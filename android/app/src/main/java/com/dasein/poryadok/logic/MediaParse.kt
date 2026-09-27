package com.dasein.poryadok.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Найденный в интернете фильм, сериал или книга — до сохранения в коллекцию. */
data class MediaHit(
    val kind: Int,
    val title: String,
    val originalTitle: String = "",
    val year: Int? = null,
    val posterUrl: String = "",
    val description: String = "",
    val genres: String = "",
    val creators: String = "",
    val cast: String = "",
    val countries: String = "",
    val length: String = "",
    val rating: Double? = null,
    val source: String,
    val externalId: String,
    val url: String = "",
)

/** Разбор ответов открытых каталогов. Сеть — отдельно (system/MediaSearch). */
object MediaParse {
    const val MOVIE = 0
    const val SERIES = 1
    const val BOOK = 2

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonElement?.arr() = this as? JsonArray
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.intOrNull ?: it.contentOrNull?.take(4)?.toIntOrNull() }
    private fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

    fun stripHtml(s: String) = s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim()

    fun minutes(m: Int?): String = when {
        m == null || m <= 0 -> ""
        m >= 60 -> "${m / 60} ч ${m % 60} мин"
        else -> "$m мин"
    }

    /** iTunes Search API (фильмы, без ключа). */
    fun itunes(text: String): List<MediaHit> {
        val root = json.parseToJsonElement(text).obj() ?: return emptyList()
        return root["results"].arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val title = o["trackName"].str() ?: return@mapNotNull null
            MediaHit(
                kind = MOVIE, title = title, year = o["releaseDate"].str()?.take(4)?.toIntOrNull(),
                posterUrl = o["artworkUrl100"].str()?.replace("100x100bb", "600x600bb").orEmpty(),
                description = o["longDescription"].str() ?: o["shortDescription"].str().orEmpty(),
                genres = o["primaryGenreName"].str().orEmpty(), creators = o["artistName"].str().orEmpty(),
                length = minutes(o["trackTimeMillis"].dbl()?.let { (it / 60000).toInt() }),
                source = "iTunes", externalId = "itunes:" + (o["trackId"].int() ?: title.hashCode()), url = o["trackViewUrl"].str().orEmpty(),
            )
        }
    }

    /** TVMaze (сериалы, без ключа): поиск шоу. */
    fun tvmazeSearch(text: String): List<MediaHit> =
        json.parseToJsonElement(text).arr().orEmpty().mapNotNull { e -> e.obj()?.get("show").obj()?.let { tvmazeShow(it) } }

    fun tvmazeShow(o: JsonObject): MediaHit? {
        val title = o["name"].str() ?: return null
        val cast = o["_embedded"].obj()?.get("cast").arr().orEmpty().mapNotNull { c ->
            val p = c.obj()?.get("person").obj()?.get("name").str() ?: return@mapNotNull null
            val ch = c.obj()?.get("character").obj()?.get("name").str()
            if (ch != null) "$p — $ch" else p
        }
        val network = o["network"].obj() ?: o["webChannel"].obj()
        return MediaHit(
            kind = SERIES, title = title, year = o["premiered"].str()?.take(4)?.toIntOrNull(),
            posterUrl = o["image"].obj()?.let { it["original"].str() ?: it["medium"].str() }.orEmpty(),
            description = stripHtml(o["summary"].str().orEmpty()),
            genres = o["genres"].arr().orEmpty().mapNotNull { it.str() }.joinToString(", "),
            cast = cast.take(15).joinToString("\n"),
            countries = network?.get("country").obj()?.get("name").str().orEmpty(),
            length = listOfNotNull(network?.get("name").str(), o["status"].str()?.let { if (it == "Ended") "завершён" else "идёт" }).joinToString(" · "),
            rating = o["rating"].obj()?.get("average").dbl(),
            source = "TVMaze", externalId = "tvmaze:" + o["id"].int(), url = o["url"].str().orEmpty(),
        )
    }

    /** Google Книги (без ключа). */
    fun googleBooks(text: String): List<MediaHit> {
        val root = json.parseToJsonElement(text).obj() ?: return emptyList()
        return root["items"].arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val v = o["volumeInfo"].obj() ?: return@mapNotNull null
            val title = v["title"].str() ?: return@mapNotNull null
            MediaHit(
                kind = BOOK, title = title + (v["subtitle"].str()?.let { ". $it" } ?: ""),
                year = v["publishedDate"].str()?.take(4)?.toIntOrNull(),
                posterUrl = v["imageLinks"].obj()?.let { it["thumbnail"].str() ?: it["smallThumbnail"].str() }?.replace("http://", "https://")?.replace("&edge=curl", "").orEmpty(),
                description = stripHtml(v["description"].str().orEmpty()),
                genres = v["categories"].arr().orEmpty().mapNotNull { it.str() }.joinToString(", "),
                creators = v["authors"].arr().orEmpty().mapNotNull { it.str() }.joinToString(", "),
                length = v["pageCount"].int()?.let { "$it стр." }.orEmpty(),
                rating = v["averageRating"].dbl()?.let { it * 2 },
                source = "Google Книги", externalId = "gbooks:" + o["id"].str(), url = v["infoLink"].str().orEmpty(),
            )
        }
    }

    /** Open Library (книги, без ключа). */
    fun openLibrary(text: String): List<MediaHit> {
        val root = json.parseToJsonElement(text).obj() ?: return emptyList()
        return root["docs"].arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val title = o["title"].str() ?: return@mapNotNull null
            val cover = o["cover_i"].int()
            MediaHit(
                kind = BOOK, title = title, year = o["first_publish_year"].int(),
                posterUrl = cover?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" }.orEmpty(),
                genres = o["subject"].arr().orEmpty().take(4).mapNotNull { it.str() }.joinToString(", "),
                creators = o["author_name"].arr().orEmpty().mapNotNull { it.str() }.joinToString(", "),
                length = o["number_of_pages_median"].int()?.let { "$it стр." }.orEmpty(),
                source = "Open Library", externalId = "ol:" + o["key"].str(), url = o["key"].str()?.let { "https://openlibrary.org$it" }.orEmpty(),
            )
        }
    }

    private fun kpKind(type: String?): Int = when (type) {
        "tv-series", "animated-series", "anime", "mini-series", "tv-show" -> SERIES
        else -> MOVIE
    }

    /** Кинопоиск (api.kinopoisk.dev, нужен личный токен): поиск. */
    fun kinopoiskSearch(text: String): List<MediaHit> {
        val root = json.parseToJsonElement(text).obj() ?: return emptyList()
        return root["docs"].arr().orEmpty().mapNotNull { e -> e.obj()?.let { kinopoiskMovie(it) } }
    }

    /** Кинопоиск: карточка фильма (с режиссёрами и актёрами, если есть). */
    fun kinopoiskMovie(o: JsonObject): MediaHit? {
        val title = o["name"].str() ?: o["alternativeName"].str() ?: o["enName"].str() ?: return null
        val persons = o["persons"].arr().orEmpty().mapNotNull { it.obj() }
        fun names(prof: String) = persons.filter { it["enProfession"].str() == prof }.mapNotNull { it["name"].str() ?: it["enName"].str() }
        val actors = persons.filter { it["enProfession"].str() == "actor" }.take(15).mapNotNull { p ->
            val n = p["name"].str() ?: p["enName"].str() ?: return@mapNotNull null
            p["description"].str()?.let { "$n — $it" } ?: n
        }
        val kind = kpKind(o["type"].str())
        val seasons = o["seasonsInfo"].arr()?.size
        return MediaHit(
            kind = kind, title = title, originalTitle = (o["alternativeName"].str() ?: o["enName"].str()).takeIf { it != title }.orEmpty(),
            year = o["year"].int(),
            posterUrl = o["poster"].obj()?.let { it["url"].str() ?: it["previewUrl"].str() }.orEmpty(),
            description = o["description"].str() ?: o["shortDescription"].str().orEmpty(),
            genres = o["genres"].arr().orEmpty().mapNotNull { it.obj()?.get("name").str() }.joinToString(", "),
            creators = names("director").joinToString(", "),
            cast = actors.joinToString("\n"),
            countries = o["countries"].arr().orEmpty().mapNotNull { it.obj()?.get("name").str() }.joinToString(", "),
            length = if (kind == SERIES && seasons != null) "$seasons сез." else minutes(o["movieLength"].int() ?: o["seriesLength"].int()),
            rating = o["rating"].obj()?.get("kp").dbl()?.takeIf { it > 0 },
            source = "Кинопоиск", externalId = "kp:" + o["id"].int(), url = o["id"].int()?.let { "https://www.kinopoisk.ru/film/$it/" }.orEmpty(),
        )
    }

    fun kinopoiskDetails(text: String): MediaHit? = json.parseToJsonElement(text).obj()?.let { kinopoiskMovie(it) }

    fun tvmazeDetails(text: String): MediaHit? = json.parseToJsonElement(text).obj()?.let { tvmazeShow(it) }

    // ---------- Год в запросе и объединение результатов ----------

    /** «Дюна 2021» → («Дюна», 2021). Год — только в конце и только 1900–2099; «2012» само по себе — название. */
    fun splitYear(query: String): Pair<String, Int?> {
        val t = query.trim()
        val m = Regex("""^(.*\S)[\s,(]+((?:19|20)\d{2})\)?$""").find(t) ?: return t to null
        val y = m.groupValues[2].toInt()
        // «Бегущий по лезвию 2049» — число из будущего это часть названия, а не год.
        if (y > java.time.LocalDate.now().year + 2) return t to null
        return m.groupValues[1].trim().trimEnd(',', '(').trim() to y
    }

    /** Только вышедшие в [year]. Если таких нет — пусто (экран покажет подсказку). */
    fun filterYear(hits: List<MediaHit>, year: Int?): List<MediaHit> = if (year == null) hits else hits.filter { it.year == year }

    private fun norm(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Склеивает выдачу нескольких каталогов без повторов: один и тот же фильм (название + год) показывается один раз. */
    fun mergeHits(lists: List<List<MediaHit>>): List<MediaHit> {
        val seen = HashSet<String>()
        val out = ArrayList<MediaHit>()
        lists.flatten().forEach { h ->
            val keys = listOf(norm(h.title), norm(h.originalTitle)).filter { it.isNotEmpty() }.map { "$it|${h.year ?: ""}" }
            if (keys.none { it in seen } && h.externalId !in seen) {
                out += h
                seen += keys
                seen += h.externalId
            }
        }
        return out
    }

    // ---------- Википедия и Викиданные (русскоязычный каталог без ключа) ----------

    data class WikiEntity(
        val id: String,
        val label: String,
        val description: String,
        val origTitle: String,
        val year: Int?,
        val types: Set<String>,
        val directors: List<String>,
        val authors: List<String>,
        val creators: List<String>,
        val cast: List<Pair<String, String?>>,
        val genres: List<String>,
        val countries: List<String>,
        val minutes: Int?,
        val pages: Int?,
        val seasons: Int?,
        val kpId: String?,
        val ruTitle: String?,
        val enTitle: String?,
    )

    private val FILM_TYPES = setOf("Q11424", "Q202866", "Q24869", "Q506240", "Q93204", "Q226730", "Q17123180", "Q229390", "Q24862", "Q20650540", "Q18011172", "Q112158242")
    private val SERIES_TYPES = setOf("Q5398426", "Q581714", "Q63952888", "Q1259759", "Q117467246", "Q15416", "Q526877")
    private val BOOK_TYPES = setOf("Q7725634", "Q8261", "Q571", "Q47461344", "Q49084", "Q1667921", "Q12308638", "Q277759", "Q17518461")

    fun wikiSearch(text: String): List<String> =
        json.parseToJsonElement(text).obj()?.get("search").arr().orEmpty().mapNotNull { it.obj()?.get("id").str() }

    private fun JsonObject.claims(p: String): List<JsonObject> =
        this["claims"].obj()?.get(p).arr().orEmpty().mapNotNull { it.obj() }.filter { it["rank"].str() != "deprecated" }

    private fun JsonObject.value(): JsonElement? = this["mainsnak"].obj()?.get("datavalue").obj()?.get("value")
    private fun JsonObject.ids(p: String) = claims(p).mapNotNull { it.value().obj()?.get("id").str() }
    private fun JsonObject.amount(p: String) = claims(p).firstNotNullOfOrNull { it.value().obj()?.get("amount").str()?.toDoubleOrNull()?.toInt() }
    private fun JsonObject.label(): String? = this["labels"].obj()?.let { l -> l["ru"].obj()?.get("value").str() ?: l["en"].obj()?.get("value").str() }

    fun wikiEntities(text: String): List<WikiEntity> {
        val root = json.parseToJsonElement(text).obj()?.get("entities").obj() ?: return emptyList()
        return root.values.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o["id"].str() ?: return@mapNotNull null
            val sl = o["sitelinks"].obj()
            val ru = sl?.get("ruwiki").obj()?.get("title").str()
            val en = sl?.get("enwiki").obj()?.get("title").str()
            val label = o.label() ?: ru ?: en ?: return@mapNotNull null
            val year = (o.claims("P577") + o.claims("P580")).mapNotNull { c ->
                c.value().obj()?.get("time").str()?.let { Regex("^[+-](\\d{4})").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            }.minOrNull()
            val cast = o.claims("P161").mapNotNull { c ->
                val pid = c.value().obj()?.get("id").str() ?: return@mapNotNull null
                val q = c["qualifiers"].obj()
                val role = q?.get("P453").arr()?.firstOrNull().obj()?.get("datavalue").obj()?.get("value").obj()?.get("id").str()
                    ?: q?.get("P4633").arr()?.firstOrNull().obj()?.get("datavalue").obj()?.get("value").str()
                pid to role
            }
            WikiEntity(
                id = id, label = label,
                description = o["descriptions"].obj()?.let { d -> d["ru"].obj()?.get("value").str() ?: d["en"].obj()?.get("value").str() }.orEmpty(),
                origTitle = o.claims("P1476").firstNotNullOfOrNull { it.value().obj()?.get("text").str() }.orEmpty(),
                year = year, types = o.ids("P31").toSet(),
                directors = o.ids("P57"), authors = o.ids("P50"), creators = o.ids("P170"), cast = cast,
                genres = o.ids("P136"), countries = o.ids("P495"),
                minutes = o.amount("P2047"), pages = o.amount("P1104"), seasons = o.amount("P2437"),
                kpId = o.claims("P2603").firstNotNullOfOrNull { it.value().str() },
                ruTitle = ru, enTitle = en,
            )
        }
    }

    /** Вид записи Викиданных: по «это частный случай» (P31), а если его нет — по описанию. */
    fun wikiKind(e: WikiEntity): Int? {
        if (e.types.any { it in SERIES_TYPES }) return SERIES
        if (e.types.any { it in FILM_TYPES }) return MOVIE
        if (e.types.any { it in BOOK_TYPES }) return BOOK
        val d = e.description.lowercase()
        return when {
            "сериал" in d || "series" in d || "аниме" in d -> SERIES
            "фильм" in d || "film" in d || "movie" in d -> MOVIE
            listOf("роман", "книга", "повесть", "рассказ", "поэма", "novel", "book", "сборник").any { it in d } -> BOOK
            else -> null
        }
    }

    fun wikiLabels(text: String): Map<String, String> {
        val root = json.parseToJsonElement(text).obj()?.get("entities").obj() ?: return emptyMap()
        return root.values.mapNotNull { e -> val o = e.obj() ?: return@mapNotNull null; val id = o["id"].str() ?: return@mapNotNull null; o.label()?.let { id to it } }.toMap()
    }

    /** Ответ query (formatversion=2): картинка и первые предложения статьи по названию, с учётом перенаправлений. */
    fun wikiPages(text: String): Map<String, Pair<String?, String?>> {
        val q = json.parseToJsonElement(text).obj()?.get("query").obj() ?: return emptyMap()
        val alias = HashMap<String, String>()
        (q["normalized"].arr().orEmpty() + q["redirects"].arr().orEmpty()).forEach { r ->
            val from = r.obj()?.get("from").str(); val to = r.obj()?.get("to").str()
            if (from != null && to != null) alias[from] = to
        }
        val pages = q["pages"].arr().orEmpty().mapNotNull { p ->
            val o = p.obj() ?: return@mapNotNull null
            val t = o["title"].str() ?: return@mapNotNull null
            t to (o["thumbnail"].obj()?.get("source").str() to o["extract"].str())
        }.toMap()
        val out = HashMap<String, Pair<String?, String?>>(pages)
        alias.keys.forEach { from ->
            var t = from; var guard = 0
            while (alias[t] != null && guard++ < 4) t = alias.getValue(t)
            pages[t]?.let { out[from] = it }
        }
        return out
    }

    fun wikiHit(e: WikiEntity, kind: Int, labels: Map<String, String>, ru: Pair<String?, String?>?, en: Pair<String?, String?>?): MediaHit {
        fun names(ids: List<String>, n: Int) = ids.take(n).mapNotNull { labels[it] }.joinToString(", ")
        val title = e.label
        val creators = when (kind) {
            BOOK -> names(e.authors, 4)
            else -> names(e.directors, 3).ifBlank { names(e.creators, 3) }
        }
        val cast = e.cast.take(12).mapNotNull { (p, r) ->
            val n = labels[p] ?: return@mapNotNull null
            val role = r?.let { labels[it] ?: it.takeIf { x -> !x.matches(Regex("Q\\d+")) } }
            if (role != null) "$n — $role" else n
        }.joinToString("\n")
        return MediaHit(
            kind = kind, title = title,
            originalTitle = e.origTitle.takeIf { it.isNotBlank() && !it.equals(title, true) }.orEmpty(),
            year = e.year,
            posterUrl = ru?.first ?: en?.first ?: "",
            description = ru?.second ?: e.description,
            genres = names(e.genres, 4),
            creators = creators, cast = cast,
            countries = names(e.countries, 3),
            length = when {
                kind == BOOK -> e.pages?.let { "$it стр." }.orEmpty()
                kind == SERIES && e.seasons != null -> "${e.seasons} сез."
                else -> minutes(e.minutes)
            },
            source = "Википедия", externalId = "wd:" + e.id,
            url = e.ruTitle?.let { "https://ru.wikipedia.org/wiki/" + it.replace(' ', '_') } ?: "https://www.wikidata.org/wiki/${e.id}",
        )
    }

    // ---------- Импорт со страниц Кинопоиска (оценки, папки, «Буду смотреть») ----------

    data class KpItem(val id: String, val series: Boolean, val title: String, val year: Int?, val vote: Int?, val posterUrl: String)

    /** Картинка Кинопоиска в большем размере и с протоколом. */
    fun kpPoster(src: String): String {
        if (src.isBlank() || src.startsWith("data:")) return ""
        val u = if (src.startsWith("//")) "https:$src" else src
        return u.replace(Regex("/\\d{2,3}x\\d{2,3}$"), "/300x450")
    }

    /** Разбор того, что собрал скрипт со страницы: [{id, type, title, text, img, vote}]. */
    fun kpItems(text: String): List<KpItem> = json.parseToJsonElement(text).arr().orEmpty().mapNotNull { e ->
        val o = e.obj() ?: return@mapNotNull null
        val id = o["id"].str() ?: return@mapNotNull null
        val raw = o["title"].str().orEmpty().lines().first().trim()
        val body = o["text"].str().orEmpty()
        val (t, y) = parseListLine(raw) ?: return@mapNotNull null
        if (t.isBlank() || t.matches(Regex("[\\d.,\\s]+"))) return@mapNotNull null
        val year = y ?: Regex("\\b((?:19|20)\\d{2})\\b").find(body)?.groupValues?.get(1)?.toInt()
        val vote = o["vote"].str()?.trim()?.toIntOrNull()?.takeIf { it in 1..10 }
            ?: Regex("(?:[Мм]оя оценка|[Вв]аша оценка)[:\\s]*(10|[1-9])\\b").find(body)?.groupValues?.get(1)?.toInt()
        KpItem(id, o["type"].str() == "series", t, year, vote, kpPoster(o["img"].str().orEmpty()))
    }

    /** Строка списка «Название (2010)» или «Название, 2010» → название и год. */
    fun parseListLine(line: String): Pair<String, Int?>? {
        val t = line.trim().trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim()
        if (t.isEmpty()) return null
        val m = Regex("""^(.*?)[\s,(\[]+((?:19|20)\d{2})[)\]]?\s*$""").find(t)
        return if (m != null && m.groupValues[1].isNotBlank()) m.groupValues[1].trim().trimEnd(',', '-', '—').trim() to m.groupValues[2].toInt() else t to null
    }
}
