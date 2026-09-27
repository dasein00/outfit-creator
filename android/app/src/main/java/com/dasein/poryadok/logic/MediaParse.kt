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

    /** Строка списка «Название (2010)» или «Название, 2010» → название и год. */
    fun parseListLine(line: String): Pair<String, Int?>? {
        val t = line.trim().trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim()
        if (t.isEmpty()) return null
        val m = Regex("""^(.*?)[\s,(\[]+((?:19|20)\d{2})[)\]]?\s*$""").find(t)
        return if (m != null && m.groupValues[1].isNotBlank()) m.groupValues[1].trim().trimEnd(',', '-', '—').trim() to m.groupValues[2].toInt() else t to null
    }
}
