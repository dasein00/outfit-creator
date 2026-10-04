package com.dasein.poryadok.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * Расширенный поиск, как на Кинопоиске и в TMDB: страна, жанр, годы, режиссёр или актёр, рейтинг, сортировка.
 * Два режима: фильтр уже найденного по названию и «подборка» — поиск без названия, только по фильтрам
 * (TMDB и Кинопоиск — с ключом, Викиданные — без ключа).
 */
object MediaDiscover {
    /** Страна: подпись, Викиданные, код ISO для TMDB, название на Кинопоиске и другие написания в каталогах. */
    data class Country(val title: String, val wd: String, val iso: String, val kp: String, val aliases: List<String> = emptyList())

    val COUNTRIES = listOf(
        Country("Россия", "Q159", "RU", "Россия", listOf("russia", "рф")),
        Country("СССР", "Q15180", "SU", "СССР", listOf("soviet", "ussr")),
        Country("США", "Q30", "US", "США", listOf("united states", "usa", "соединённые штаты", "соединенные штаты")),
        Country("Великобритания", "Q145", "GB", "Великобритания", listOf("united kingdom", "uk", "англия", "england")),
        Country("Южная Корея", "Q884", "KR", "Корея Южная", listOf("корея", "south korea", "korea")),
        Country("Япония", "Q17", "JP", "Япония", listOf("japan")),
        Country("Китай", "Q148", "CN", "Китай", listOf("china", "кнр")),
        Country("Франция", "Q142", "FR", "Франция", listOf("france")),
        Country("Германия", "Q183", "DE", "Германия", listOf("germany", "фрг")),
        Country("Италия", "Q38", "IT", "Италия", listOf("italy")),
        Country("Испания", "Q29", "ES", "Испания", listOf("spain")),
        Country("Индия", "Q668", "IN", "Индия", listOf("india")),
        Country("Турция", "Q43", "TR", "Турция", listOf("turkey", "türkiye")),
        Country("Тайвань", "Q865", "TW", "Тайвань", listOf("taiwan")),
        Country("Таиланд", "Q869", "TH", "Таиланд", listOf("thailand")),
        Country("Гонконг", "Q8646", "HK", "Гонконг", listOf("hong kong")),
        Country("Канада", "Q16", "CA", "Канада", listOf("canada")),
        Country("Австралия", "Q408", "AU", "Австралия", listOf("australia")),
        Country("Швеция", "Q34", "SE", "Швеция", listOf("sweden")),
        Country("Дания", "Q35", "DK", "Дания", listOf("denmark")),
        Country("Норвегия", "Q20", "NO", "Норвегия", listOf("norway")),
        Country("Польша", "Q36", "PL", "Польша", listOf("poland")),
        Country("Мексика", "Q96", "MX", "Мексика", listOf("mexico")),
        Country("Бразилия", "Q155", "BR", "Бразилия", listOf("brazil")),
        Country("Аргентина", "Q414", "AR", "Аргентина", listOf("argentina")),
        Country("Иран", "Q794", "IR", "Иран", listOf("iran")),
    )

    /**
     * Жанр: подпись, корни слов для фильтра найденного, шаблон для английских названий жанров в Викиданных,
     * номера жанров TMDB (для фильмов и для сериалов) и название на Кинопоиске.
     */
    data class Genre(val title: String, val stems: List<String>, val wdRegex: String, val tmdbMovie: Int?, val tmdbTv: Int?, val kp: String)

    val GENRES = listOf(
        Genre("Драма", listOf("драм", "drama"), "drama", 18, 18, "драма"),
        Genre("Комедия", listOf("комед", "comedy", "ситком", "sitcom"), "comed|sitcom", 35, 35, "комедия"),
        Genre("Мелодрама", listOf("мелодрам", "романт", "romance", "romantic"), "romance|romantic", 10749, null, "мелодрама"),
        Genre("Триллер", listOf("триллер", "thriller"), "thriller", 53, null, "триллер"),
        Genre("Детектив", listOf("детектив", "mystery", "detective"), "mystery|detective", 9648, 9648, "детектив"),
        Genre("Криминал", listOf("криминал", "crime"), "crime", 80, 80, "криминал"),
        Genre("Ужасы", listOf("ужас", "хоррор", "horror"), "horror", 27, null, "ужасы"),
        Genre("Фантастика", listOf("фантастик", "science fiction", "sci-fi"), "science fiction", 878, 10765, "фантастика"),
        Genre("Фэнтези", listOf("фэнтези", "fantasy"), "fantasy", 14, 10765, "фэнтези"),
        Genre("Боевик", listOf("боевик", "action"), "action", 28, 10759, "боевик"),
        Genre("Приключения", listOf("приключ", "adventure"), "adventure", 12, 10759, "приключения"),
        Genre("Мультфильм", listOf("мульт", "анимац", "animat", "anime", "аниме"), "animat|anime", 16, 16, "мультфильм"),
        Genre("Семейный", listOf("семейн", "family"), "family", 10751, 10751, "семейный"),
        Genre("Исторический", listOf("истори", "histor"), "histor", 36, null, "история"),
        Genre("Военный", listOf("военн", "война", "war"), "(^| )war( |$)", 10752, 10768, "военный"),
        Genre("Биография", listOf("биограф", "biograph"), "biograph", null, null, "биография"),
        Genre("Документальный", listOf("документ", "documentary"), "documentary", 99, 99, "документальный"),
        Genre("Мюзикл", listOf("мюзикл", "musical"), "musical", 10402, null, "мюзикл"),
    )

    enum class Role(val title: String) { ANY("Любая роль"), DIRECTOR("Режиссёр"), ACTOR("Актёр") }

    enum class Sort(val title: String) { RELEVANCE("Сначала популярные"), RATING("По рейтингу"), NEWEST("Сначала новые"), OLDEST("Сначала старые"), TITLE("По алфавиту") }

    data class Filter(
        val yearFrom: Int? = null,
        val yearTo: Int? = null,
        val country: String? = null,
        val genre: String? = null,
        val person: String = "",
        val role: Role = Role.ANY,
        val minRating: Double? = null,
        val sort: Sort = Sort.RELEVANCE,
        val hideOwned: Boolean = false,
    ) {
        val countryObj get() = COUNTRIES.firstOrNull { it.title == country }
        val genreObj get() = GENRES.firstOrNull { it.title == genre }
        /** Сколько фильтров выбрано (без сортировки). */
        val count get() = listOf(yearFrom != null || yearTo != null, country != null, genre != null, person.isNotBlank(), minRating != null, hideOwned).count { it }
        /** Можно искать без названия: есть хоть что-то, кроме рейтинга и сортировки. */
        val canDiscover get() = yearFrom != null || yearTo != null || country != null || genre != null || person.isNotBlank()
    }

    private fun low(s: String) = s.lowercase().replace('ё', 'е')

    fun matchesCountry(hit: MediaHit, c: Country): Boolean {
        val t = low(hit.countries)
        if (t.isBlank()) return false
        return (listOf(c.title, c.kp) + c.aliases).any { low(it) in t }
    }

    fun matchesGenre(hit: MediaHit, g: Genre): Boolean {
        val t = low(hit.genres)
        return t.isNotBlank() && g.stems.any { it in t }
    }

    fun matchesPerson(hit: MediaHit, name: String, role: Role): Boolean {
        val words = low(name).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val text = low(
            when (role) {
                Role.DIRECTOR -> hit.creators
                Role.ACTOR -> hit.cast
                Role.ANY -> hit.creators + " " + hit.cast
            },
        )
        return words.all { it in text }
    }

    /** Результат фильтра найденного: что осталось и сколько скрыто из-за того, что в каталоге нет этих данных. */
    data class Filtered(val hits: List<MediaHit>, val unknown: Int)

    /**
     * Фильтр найденного по названию. Если у результата нет данных (страны, жанра, рейтинга), он не проходит фильтр,
     * но считается отдельно, чтобы честно сказать «столько-то скрыто — каталог не знает страну».
     */
    fun apply(hits: List<MediaHit>, f: Filter, owned: (MediaHit) -> Boolean = { false }): Filtered {
        var unknown = 0
        val c = f.countryObj; val g = f.genreObj
        val out = hits.filter { h ->
            if (f.hideOwned && owned(h)) return@filter false
            if (f.yearFrom != null || f.yearTo != null) {
                val y = h.year ?: run { unknown++; return@filter false }
                if (f.yearFrom != null && y < f.yearFrom) return@filter false
                if (f.yearTo != null && y > f.yearTo) return@filter false
            }
            if (c != null) {
                if (h.countries.isBlank()) { unknown++; return@filter false }
                if (!matchesCountry(h, c)) return@filter false
            }
            if (g != null) {
                if (h.genres.isBlank()) { unknown++; return@filter false }
                if (!matchesGenre(h, g)) return@filter false
            }
            if (f.person.isNotBlank()) {
                if (h.creators.isBlank() && h.cast.isBlank()) { unknown++; return@filter false }
                if (!matchesPerson(h, f.person, f.role)) return@filter false
            }
            if (f.minRating != null) {
                val r = h.rating ?: run { unknown++; return@filter false }
                if (r < f.minRating) return@filter false
            }
            true
        }
        return Filtered(sort(out, f.sort), unknown)
    }

    fun sort(hits: List<MediaHit>, s: Sort): List<MediaHit> = when (s) {
        Sort.RELEVANCE -> hits
        Sort.RATING -> hits.sortedByDescending { it.rating ?: -1.0 }
        Sort.NEWEST -> hits.sortedByDescending { it.year ?: Int.MIN_VALUE }
        Sort.OLDEST -> hits.sortedBy { it.year ?: Int.MAX_VALUE }
        Sort.TITLE -> hits.sortedBy { low(it.title) }
    }

    /** Чего больше всего среди найденного — быстрые фильтры над результатами, как «уточнить» в магазинах. */
    fun facets(hits: List<MediaHit>): Pair<List<Pair<String, Int>>, List<Pair<String, Int>>> {
        val countries = COUNTRIES.map { c -> c.title to hits.count { matchesCountry(it, c) } }.filter { it.second > 0 }.sortedByDescending { it.second }
        val genres = GENRES.map { g -> g.title to hits.count { matchesGenre(it, g) } }.filter { it.second > 0 }.sortedByDescending { it.second }
        return countries to genres
    }

    // ---------- Подборка без названия ----------

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** TMDB discover. Пусто — если выбранный жанр в TMDB для этого типа не существует. */
    fun tmdbUrl(series: Boolean, f: Filter, page: Int = 1): String {
        val type = if (series) "tv" else "movie"
        val date = if (series) "first_air_date" else "primary_release_date"
        val sort = when (f.sort) {
            Sort.RELEVANCE, Sort.TITLE -> "popularity.desc"
            Sort.RATING -> "vote_average.desc"
            Sort.NEWEST -> "$date.desc"
            Sort.OLDEST -> "$date.asc"
        }
        val p = mutableListOf("language=ru-RU", "include_adult=false", "page=$page", "sort_by=$sort")
        f.countryObj?.let { p += "with_origin_country=${it.iso}" }
        f.genreObj?.let { g -> (if (series) g.tmdbTv else g.tmdbMovie)?.let { p += "with_genres=$it" } }
        f.yearFrom?.let { p += "$date.gte=$it-01-01" }
        f.yearTo?.let { p += "$date.lte=$it-12-31" }
        f.minRating?.let { p += "vote_average.gte=$it" }
        // Чтобы наверх не попадали фильмы с одной оценкой 10.
        if (f.sort == Sort.RATING || f.minRating != null) p += "vote_count.gte=150" else p += "vote_count.gte=10"
        return "https://api.themoviedb.org/3/discover/$type?" + p.joinToString("&")
    }

    /** Кинопоиск (api.kinopoisk.dev): поиск по полям. */
    fun kinopoiskUrl(series: Boolean, f: Filter): String {
        val g = f.genreObj
        val type = when {
            series -> "tv-series"
            g?.kp == "мультфильм" -> "cartoon"
            else -> "movie"
        }
        val p = mutableListOf("page=1", "limit=40", "type=$type", "notNullFields=name")
        if (f.yearFrom != null || f.yearTo != null) p += "year=${f.yearFrom ?: 1900}-${f.yearTo ?: 2100}"
        f.countryObj?.let { p += "countries.name=${enc(it.kp)}" }
        if (g != null && !(type == "cartoon")) p += "genres.name=${enc(g.kp)}"
        f.minRating?.let { p += "rating.kp=${it.toInt()}-10" }
        when (f.sort) {
            Sort.RATING -> { p += "sortField=rating.kp"; p += "sortType=-1"; p += "votes.kp=${enc("1000-10000000")}" }
            Sort.NEWEST -> { p += "sortField=year"; p += "sortType=-1" }
            Sort.OLDEST -> { p += "sortField=year"; p += "sortType=1" }
            else -> { p += "sortField=votes.kp"; p += "sortType=-1" }
        }
        return "https://api.kinopoisk.dev/v1.4/movie?" + p.joinToString("&")
    }

    private val FILM_TYPES = listOf("Q11424", "Q24869", "Q202866", "Q29168811")
    private val SERIES_TYPES = listOf("Q5398426", "Q581714", "Q1259759")

    /**
     * Запрос к Викиданным (без ключа): фильмы или сериалы по стране, жанру, годам и людям [people] (id Викиданных).
     * Популярность — по числу статей в Википедиях на разных языках.
     */
    fun sparql(series: Boolean, f: Filter, people: List<String> = emptyList(), limit: Int = 40): String {
        val sb = StringBuilder("SELECT ?item (MAX(?sl) AS ?pop) (MIN(?yr) AS ?y) WHERE {\n")
        if (people.isNotEmpty()) {
            sb.append("  VALUES ?p { ${people.joinToString(" ") { "wd:$it" }} }\n")
            val props = when (f.role) {
                Role.DIRECTOR -> listOf("P57", "P170")
                Role.ACTOR -> listOf("P161")
                Role.ANY -> listOf("P57", "P161", "P58", "P170")
            }
            sb.append("  VALUES ?prop { ${props.joinToString(" ") { "wdt:$it" }} }\n  ?item ?prop ?p .\n")
        }
        f.countryObj?.let { sb.append("  ?item wdt:P495 wd:${it.wd} .\n") }
        sb.append("  VALUES ?type { ${(if (series) SERIES_TYPES else FILM_TYPES).joinToString(" ") { "wd:$it" }} }\n  ?item wdt:P31 ?type .\n")
        f.genreObj?.let { g ->
            sb.append("  ?item wdt:P136 ?g . ?g rdfs:label ?gl . FILTER(LANG(?gl) = \"en\" && REGEX(?gl, \"${g.wdRegex}\", \"i\"))\n")
        }
        if (f.yearFrom != null || f.yearTo != null) {
            sb.append("  ?item wdt:P577|wdt:P580 ?date . BIND(YEAR(?date) AS ?yr)\n")
            val c = listOfNotNull(f.yearFrom?.let { "?yr >= $it" }, f.yearTo?.let { "?yr <= $it" })
            sb.append("  FILTER(${c.joinToString(" && ")})\n")
        } else {
            sb.append("  OPTIONAL { ?item wdt:P577|wdt:P580 ?date . BIND(YEAR(?date) AS ?yr) }\n")
        }
        sb.append("  ?item wikibase:sitelinks ?sl .\n}\nGROUP BY ?item\n")
        sb.append(
            when (f.sort) {
                Sort.NEWEST -> "ORDER BY DESC(?y)"
                Sort.OLDEST -> "ORDER BY ASC(?y)"
                else -> "ORDER BY DESC(?pop)"
            },
        )
        sb.append("\nLIMIT $limit")
        return sb.toString()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Ответ SPARQL → id Викиданных по порядку. */
    fun sparqlIds(text: String): List<String> = runCatching {
        json.parseToJsonElement(text).jsonObject["results"]!!.jsonObject["bindings"]!!.jsonArray.mapNotNull { b ->
            ((b as? JsonObject)?.get("item") as? JsonObject)?.get("value")?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?.substringAfterLast('/')?.takeIf { it.startsWith("Q") }
        }.distinct()
    }.getOrDefault(emptyList())

    /** Подпись подборки: «Фильмы · мелодрама · Южная Корея · 2015–2024». */
    fun describe(f: Filter, series: Boolean): String = listOfNotNull(
        if (series) "Сериалы" else "Фильмы",
        f.genreObj?.title?.lowercase()?.let { "· $it" },
        f.countryObj?.let { "· ${it.title}" },
        when {
            f.yearFrom != null && f.yearTo != null -> if (f.yearFrom == f.yearTo) "· ${f.yearFrom}" else "· ${f.yearFrom}–${f.yearTo}"
            f.yearFrom != null -> "· с ${f.yearFrom}"
            f.yearTo != null -> "· до ${f.yearTo}"
            else -> null
        },
        f.person.takeIf { it.isNotBlank() }?.let { "· ${if (f.role == Role.ACTOR) "с участием" else if (f.role == Role.DIRECTOR) "режиссёр" else "с"} ${it.trim()}" },
        f.minRating?.let { "· рейтинг от ${it.toInt()}" },
    ).joinToString(" ")

    /** Быстрые периоды, как на Кинопоиске. */
    val DECADES = listOf("2020-е" to (2020 to null), "2010-е" to (2010 to 2019), "2000-е" to (2000 to 2009), "90-е" to (1990 to 1999), "80-е" to (1980 to 1989), "Классика" to (null to 1979))
}
