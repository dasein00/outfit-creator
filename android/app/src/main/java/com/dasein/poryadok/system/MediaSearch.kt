package com.dasein.poryadok.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.logic.MediaDiscover
import com.dasein.poryadok.logic.MediaHit
import com.dasein.poryadok.logic.MediaParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Источники поиска. «Везде» опрашивает все подходящие сразу.
 * Без ключа: Википедия, IMDb, TVMaze, iTunes, Шикимори, Google Книги, Open Library.
 * С бесплатным личным ключом: Кинопоиск (api.kinopoisk.dev) и TMDB (themoviedb.org) — в TMDB больше всего дорам с русскими описаниями.
 */
enum class MediaSource(val title: String, val kinds: Set<Int>, val needsToken: Boolean = false) {
    ALL("Везде", setOf(MediaParse.MOVIE, MediaParse.SERIES, MediaParse.BOOK)),
    KINOPOISK("Кинопоиск", setOf(MediaParse.MOVIE, MediaParse.SERIES), needsToken = true),
    TMDB("TMDB", setOf(MediaParse.MOVIE, MediaParse.SERIES), needsToken = true),
    WIKI("Википедия", setOf(MediaParse.MOVIE, MediaParse.SERIES, MediaParse.BOOK)),
    IMDB("IMDb", setOf(MediaParse.MOVIE, MediaParse.SERIES)),
    TVMAZE("TVMaze", setOf(MediaParse.SERIES)),
    ITUNES("iTunes", setOf(MediaParse.MOVIE, MediaParse.SERIES)),
    SHIKIMORI("Шикимори (аниме)", setOf(MediaParse.MOVIE, MediaParse.SERIES)),
    GOOGLE_BOOKS("Google Книги", setOf(MediaParse.BOOK)),
    OPEN_LIBRARY("Open Library", setOf(MediaParse.BOOK)),
}

object MediaSearch {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String, headers: Map<String, String> = emptyMap(), timeout: Int = 15_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = timeout
        c.setRequestProperty("User-Agent", "DASEIN/1.0 (Android)")
        c.setRequestProperty("Accept", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            val code = c.responseCode
            if (code == 401 || code == 403) error("Источник отклонил запрос (код $code). Проверьте токен.")
            if (code !in 200..299) error("Сервер ответил $code")
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            c.disconnect()
        }
    }

    fun sourcesFor(kind: Int) = MediaSource.entries.filter { kind in it.kinds }

    suspend fun search(source: MediaSource, kind: Int, query: String): List<MediaHit> = withContext(Dispatchers.IO) {
        val q = enc(query.trim())
        when (source) {
            MediaSource.KINOPOISK -> {
                val token = Graph.prefs.now().kinopoiskToken
                if (token.isBlank()) error("Нужен токен Кинопоиска")
                MediaParse.kinopoiskSearch(get("https://api.kinopoisk.dev/v1.4/movie/search?page=1&limit=20&query=$q", mapOf("X-API-KEY" to token)))
                    .filter { it.kind == kind }
            }
            MediaSource.ALL -> all(kind, query)
            MediaSource.WIKI -> wiki(kind, query.trim())
            MediaSource.TMDB -> {
                val token = Graph.prefs.now().tmdbToken.trim()
                if (token.isBlank()) error("Нужен ключ TMDB")
                val type = if (kind == MediaParse.SERIES) "tv" else "movie"
                // Ключ v4 (длинный JWT) — в заголовке, короткий v3 — параметром.
                val url = "https://api.themoviedb.org/3/search/$type?language=ru-RU&include_adult=false&query=$q" + if (token.length <= 40) "&api_key=$token" else ""
                val json = get(url, if (token.length > 40) mapOf("Authorization" to "Bearer $token") else emptyMap())
                MediaParse.tmdbSearch(json, kind, type)
            }
            MediaSource.IMDB -> {
                val term = query.trim().lowercase()
                MediaParse.imdbSuggest(get("https://v3.sg.media-imdb.com/suggestion/x/${enc(term).replace("+", "%20")}.json"), kind)
            }
            MediaSource.ITUNES -> if (kind == MediaParse.SERIES)
                MediaParse.itunesTv(get("https://itunes.apple.com/search?term=$q&media=tvShow&entity=tvSeason&country=ru&lang=ru_ru&limit=25"))
            else MediaParse.itunes(get("https://itunes.apple.com/search?term=$q&media=movie&entity=movie&country=ru&lang=ru_ru&limit=25"))
            MediaSource.SHIKIMORI -> MediaParse.shikimori(get("https://shikimori.one/api/animes?search=$q&limit=20&order=popularity"), kind)
            MediaSource.TVMAZE -> MediaParse.tvmazeSearch(get("https://api.tvmaze.com/search/shows?q=$q"))
            MediaSource.GOOGLE_BOOKS -> MediaParse.googleBooks(get("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=25&printType=books"))
            MediaSource.OPEN_LIBRARY -> MediaParse.openLibrary(get("https://openlibrary.org/search.json?q=$q&limit=25"))
        }
    }

    /** Все подходящие каталоги параллельно; Кинопоиск первым (если есть токен), без повторов. */
    private suspend fun all(kind: Int, query: String): List<MediaHit> = coroutineScope {
        val prefs = Graph.prefs.now()
        val kp = MediaSource.KINOPOISK.takeIf { prefs.kinopoiskToken.isNotBlank() }
        val tmdb = MediaSource.TMDB.takeIf { prefs.tmdbToken.isNotBlank() }
        val sources = when (kind) {
            MediaParse.BOOK -> listOf(MediaSource.GOOGLE_BOOKS, MediaSource.WIKI, MediaSource.OPEN_LIBRARY)
            MediaParse.SERIES -> listOfNotNull(kp, tmdb, MediaSource.WIKI, MediaSource.TVMAZE, MediaSource.IMDB, MediaSource.ITUNES)
            else -> listOfNotNull(kp, tmdb, MediaSource.WIKI, MediaSource.IMDB, MediaSource.ITUNES)
        }
        val results = sources.map { s -> async { runCatching { search(s, kind, query) }.getOrDefault(emptyList()) } }.awaitAll()
        MediaParse.mergeHits(results)
    }

    private const val WD = "https://www.wikidata.org/w/api.php?format=json"

    /** Русскоязычный каталог без ключа: поиск в Викиданных, описание и постер — из статьи Википедии. */
    private fun wiki(kind: Int, query: String): List<MediaHit> {
        val ids = MediaParse.wikiSearch(get("$WD&action=wbsearchentities&language=ru&uselang=ru&type=item&limit=30&search=${enc(query)}"))
        return wikiByIds(kind, ids, 15, strictKind = true)
    }

    /** Карточки по id Викиданных: названия, люди, постер и описание из Википедии. */
    private fun wikiByIds(kind: Int, ids: List<String>, limit: Int, strictKind: Boolean): List<MediaHit> {
        if (ids.isEmpty()) return emptyList()
        val ents = MediaParse.wikiEntities(
            get("$WD&action=wbgetentities&props=${enc("labels|descriptions|claims|sitelinks")}&languages=${enc("ru|en")}&sitefilter=${enc("ruwiki|enwiki")}&ids=${enc(ids.joinToString("|"))}")
        ).associateBy { it.id }
        val all = ids.mapNotNull { ents[it] }
        val matched = all.filter { MediaParse.wikiKind(it) == kind }.ifEmpty { if (strictKind) emptyList() else all }.take(limit)
        if (matched.isEmpty()) return emptyList()
        val refs = matched.flatMap { e ->
            e.directors.take(3) + e.authors.take(4) + e.creators.take(3) + e.genres.take(4) + e.countries.take(3) +
                e.cast.take(12).flatMap { listOfNotNull(it.first, it.second?.takeIf { r -> r.matches(Regex("Q\\d+")) }) }
        }.distinct()
        val labels = HashMap<String, String>()
        refs.chunked(50).forEach { part ->
            runCatching { labels += MediaParse.wikiLabels(get("$WD&action=wbgetentities&props=labels&languages=${enc("ru|en")}&ids=${enc(part.joinToString("|"))}")) }
        }
        fun pages(host: String, titles: List<String>): Map<String, Pair<String?, String?>> = if (titles.isEmpty()) emptyMap() else runCatching {
            MediaParse.wikiPages(
                get(
                    "https://$host/w/api.php?format=json&formatversion=2&action=query&redirects=1&prop=${enc("pageimages|extracts")}" +
                        "&piprop=thumbnail&pithumbsize=500&pilicense=any&exintro=1&explaintext=1&exsentences=4&exlimit=20&titles=${enc(titles.joinToString("|"))}"
                )
            )
        }.getOrDefault(emptyMap())
        val ru = pages("ru.wikipedia.org", matched.mapNotNull { it.ruTitle })
        val en = pages("en.wikipedia.org", matched.filter { it.ruTitle == null || ru[it.ruTitle]?.first == null }.mapNotNull { it.enTitle })
        return matched.map { e -> MediaParse.wikiHit(e, kind, labels, e.ruTitle?.let { ru[it] }, e.enTitle?.let { en[it] }) }
    }

    /** Какой каталог соберёт подборку по фильтрам: выбранный, если умеет, иначе TMDB → Кинопоиск → Викиданные. */
    fun discoverSource(chosen: MediaSource, f: MediaDiscover.Filter): MediaSource {
        val prefs = Graph.prefs.now()
        val tmdb = prefs.tmdbToken.isNotBlank(); val kp = prefs.kinopoiskToken.isNotBlank()
        // Поиск по людям без ключа умеют только Викиданные.
        if (f.person.isNotBlank()) return MediaSource.WIKI
        return when {
            chosen == MediaSource.TMDB && tmdb -> MediaSource.TMDB
            chosen == MediaSource.KINOPOISK && kp -> MediaSource.KINOPOISK
            chosen == MediaSource.WIKI -> MediaSource.WIKI
            tmdb -> MediaSource.TMDB
            kp -> MediaSource.KINOPOISK
            else -> MediaSource.WIKI
        }
    }

    /** Подборка без названия — только по фильтрам (страна, жанр, годы, режиссёр или актёр, рейтинг). */
    suspend fun discover(source: MediaSource, kind: Int, f: MediaDiscover.Filter): List<MediaHit> = withContext(Dispatchers.IO) {
        val series = kind == MediaParse.SERIES
        when (source) {
            MediaSource.TMDB -> {
                val token = Graph.prefs.now().tmdbToken.trim()
                val headers = if (token.length > 40) mapOf("Authorization" to "Bearer $token") else emptyMap()
                val key = if (token.length <= 40) "&api_key=$token" else ""
                val type = if (series) "tv" else "movie"
                (1..2).flatMap { page ->
                    runCatching { MediaParse.tmdbSearch(get(MediaDiscover.tmdbUrl(series, f, page) + key, headers), kind, type) }.getOrDefault(emptyList())
                }.distinctBy { it.externalId }
            }
            MediaSource.KINOPOISK -> {
                val token = Graph.prefs.now().kinopoiskToken
                MediaParse.kinopoiskSearch(get(MediaDiscover.kinopoiskUrl(series, f), mapOf("X-API-KEY" to token)))
            }
            else -> {
                val people = if (f.person.isBlank()) emptyList()
                else MediaParse.wikiSearch(get("$WD&action=wbsearchentities&language=ru&uselang=ru&type=item&limit=6&search=${enc(f.person.trim())}")).take(4)
                if (f.person.isNotBlank() && people.isEmpty()) error("Не нашли человека «${f.person.trim()}» — проверьте написание")
                val q = MediaDiscover.sparql(series, f, people)
                val text = runCatching { get("https://query.wikidata.org/sparql?format=json&query=${enc(q)}", mapOf("Accept" to "application/sparql-results+json"), timeout = 50_000) }
                    .getOrElse { error("Викиданные не успели ответить — уточните фильтры: страну, жанр или годы") }
                val ids = MediaDiscover.sparqlIds(text)
                ids.chunked(40).firstOrNull()?.let { wikiByIds(kind, it, 40, strictKind = false) }.orEmpty()
            }
        }
    }

    /** Подробности (режиссёры, актёры и роли), если источник их отдаёт отдельно. */
    suspend fun details(hit: MediaHit): MediaHit = withContext(Dispatchers.IO) {
        runCatching {
            when {
                hit.externalId.startsWith("kp:") -> {
                    val token = Graph.prefs.now().kinopoiskToken
                    MediaParse.kinopoiskDetails(get("https://api.kinopoisk.dev/v1.4/movie/${hit.externalId.removePrefix("kp:")}", mapOf("X-API-KEY" to token)))
                }
                hit.externalId.startsWith("tvmaze:") ->
                    MediaParse.tvmazeDetails(get("https://api.tvmaze.com/shows/${hit.externalId.removePrefix("tvmaze:")}?embed=cast"))
                else -> null
            }
        }.getOrNull()?.let { d ->
            hit.copy(
                creators = d.creators.ifBlank { hit.creators }, cast = d.cast.ifBlank { hit.cast },
                description = d.description.ifBlank { hit.description }, length = d.length.ifBlank { hit.length },
                countries = d.countries.ifBlank { hit.countries }, originalTitle = d.originalTitle.ifBlank { hit.originalTitle },
            )
        } ?: hit
    }

    /** Скачивает постер в личную папку приложения. */
    suspend fun downloadPoster(ctx: Context, url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        runCatching {
            val dir = File(ctx.filesDir, "posters").apply { mkdirs() }
            val f = File(dir, "p_${System.currentTimeMillis()}.jpg")
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "DASEIN/1.0 (Android)")
            c.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
            c.disconnect()
            if (f.length() < 500) { f.delete(); null } else f.absolutePath
        }.getOrNull()
    }

    fun toItem(h: MediaHit, status: Int) = MediaItem(
        kind = h.kind, title = h.title, originalTitle = h.originalTitle, year = h.year, posterUrl = h.posterUrl,
        description = h.description, genres = h.genres, creators = h.creators, cast = h.cast, countries = h.countries,
        length = h.length, status = status, source = h.source, externalId = h.externalId, externalRating = h.rating,
        url = h.url, createdAt = System.currentTimeMillis(),
    )

    /** Добавить найденное в коллекцию (без дублей) и скачать постер. Возвращает id карточки. */
    suspend fun add(ctx: Context, hit: MediaHit, status: Int): Long {
        Graph.extra.mediaByExternal(hit.externalId)?.let { return it.id }
        val full = details(hit)
        val poster = downloadPoster(ctx, full.posterUrl).orEmpty()
        return Graph.extra.upsertMedia(toItem(full, status).copy(poster = poster))
    }

    // ---------- Картинки по ссылке для результатов поиска ----------

    private val images = LruCache<String, Bitmap>(60)

    suspend fun thumb(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        images.get(url)?.let { return@withContext it }
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8_000; c.readTimeout = 12_000
            c.setRequestProperty("User-Agent", "DASEIN/1.0 (Android)")
            val bytes = c.inputStream.use { it.readBytes() }
            c.disconnect()
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }.getOrNull()?.also { images.put(url, it) }
    }
}
