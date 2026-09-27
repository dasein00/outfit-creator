package com.dasein.poryadok.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.logic.MediaHit
import com.dasein.poryadok.logic.MediaParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Источники поиска. Кинопоиску нужен бесплатный личный токен (api.kinopoisk.dev), остальным — ничего. */
enum class MediaSource(val title: String, val kinds: Set<Int>, val needsToken: Boolean = false) {
    KINOPOISK("Кинопоиск", setOf(MediaParse.MOVIE, MediaParse.SERIES), needsToken = true),
    ITUNES("iTunes", setOf(MediaParse.MOVIE)),
    TVMAZE("TVMaze", setOf(MediaParse.SERIES)),
    GOOGLE_BOOKS("Google Книги", setOf(MediaParse.BOOK)),
    OPEN_LIBRARY("Open Library", setOf(MediaParse.BOOK)),
}

object MediaSearch {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
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
            MediaSource.ITUNES -> MediaParse.itunes(get("https://itunes.apple.com/search?term=$q&media=movie&entity=movie&country=RU&lang=ru_ru&limit=25"))
            MediaSource.TVMAZE -> MediaParse.tvmazeSearch(get("https://api.tvmaze.com/search/shows?q=$q"))
            MediaSource.GOOGLE_BOOKS -> MediaParse.googleBooks(get("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=25&printType=books"))
            MediaSource.OPEN_LIBRARY -> MediaParse.openLibrary(get("https://openlibrary.org/search.json?q=$q&limit=25"))
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
