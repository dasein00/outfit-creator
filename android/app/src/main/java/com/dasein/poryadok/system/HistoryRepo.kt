package com.dasein.poryadok.system

import android.content.Context
import com.dasein.poryadok.logic.HistoryDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** События дня: встроенные + русская Википедия (кэшируется на телефоне, чтобы работать и без сети). */
object HistoryRepo {
    private var builtIn: Map<String, List<HistoryDay.Event>>? = null

    fun md(d: LocalDate) = "%02d-%02d".format(d.monthValue, d.dayOfMonth)

    fun builtIn(ctx: Context): Map<String, List<HistoryDay.Event>> = builtIn ?: runCatching {
        HistoryDay.parseBuiltIn(ctx.assets.open("history/events.json").bufferedReader().use { it.readText() })
    }.getOrDefault(emptyMap()).also { builtIn = it }

    /**
     * Хранилище «Википедии на день» — в files, а не в cache: кэш Android чистит сам при нехватке места,
     * а здесь загруженное на месяц вперёд должно лежать, пока нет интернета. Сжато gzip (в 5–8 раз меньше).
     */
    private fun storeFile(ctx: Context, d: LocalDate) = File(File(ctx.filesDir, "offline/history").apply { mkdirs() }, "${md(d)}.json.gz")
    private fun oldCacheFile(ctx: Context, d: LocalDate) = File(File(ctx.cacheDir, "history"), "${md(d)}.json")

    /** Сколько считать загруженное свежим. События «В этот день» почти не меняются — обновляем раз в 2 месяца. */
    private const val FRESH_MS = 60L * 86_400_000

    private fun read(ctx: Context, d: LocalDate): String? {
        val f = storeFile(ctx, d)
        if (f.exists()) runCatching { return java.util.zip.GZIPInputStream(f.inputStream()).use { it.readBytes().decodeToString() } }
        // Старая версия хранила в cache: переносим, чтобы не пропало.
        val old = oldCacheFile(ctx, d)
        if (!old.exists()) return null
        return runCatching { old.readText() }.getOrNull()?.also { write(ctx, d, it, old.lastModified()); old.delete() }
    }

    private fun write(ctx: Context, d: LocalDate, text: String, at: Long = System.currentTimeMillis()) {
        val f = storeFile(ctx, d)
        val tmp = File(f.path + ".tmp")
        runCatching {
            java.util.zip.GZIPOutputStream(tmp.outputStream()).use { it.write(text.encodeToByteArray()) }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            f.setLastModified(at)
        }.onFailure { tmp.delete() }
    }

    /** Загружено ли на день и не устарело ли. */
    fun isStored(ctx: Context, d: LocalDate, fresh: Boolean = true): Boolean {
        val f = storeFile(ctx, d)
        if (!f.exists()) return oldCacheFile(ctx, d).exists() && !fresh
        return !fresh || System.currentTimeMillis() - f.lastModified() < FRESH_MS
    }

    /** Загрузить день из сети и сохранить. true — получилось. */
    suspend fun download(ctx: Context, d: LocalDate): Boolean = withContext(Dispatchers.IO) {
        val text = runCatching { fetch(d) }.getOrNull() ?: return@withContext false
        if (text.length < 200) return@withContext false
        write(ctx, d, text)
        true
    }

    /** Сначала то, что есть без сети; [online] — дополнить из Википедии (кэш на месяц). */
    private val memo = java.util.concurrent.ConcurrentHashMap<LocalDate, List<HistoryDay.Event>>()

    /** Уже загруженное за день — чтобы плашка при прокрутке сразу была нужной высоты, без рывков. */
    fun cached(d: LocalDate): List<HistoryDay.Event>? = memo[d]

    suspend fun day(ctx: Context, d: LocalDate, online: Boolean = true): List<HistoryDay.Event> = withContext(Dispatchers.IO) {
        val own = builtIn(ctx)[md(d)].orEmpty()
        HistoryDay.merge(own, wikiText(ctx, d, online)?.let { HistoryDay.parseWiki(it) }.orEmpty()).also { r ->
            if (r.size >= (memo[d]?.size ?: 0)) memo[d] = r
        }
    }

    /** Ответ Википедии «В этот день» за дату (события, рождения): из кэша или из сети. Нужен и «Истории», и «Культуре». */
    suspend fun wikiText(ctx: Context, d: LocalDate, online: Boolean): String? = withContext(Dispatchers.IO) {
        if (online && !isStored(ctx, d) && Offline.hasNetwork(ctx)) {
            runCatching { fetch(d) }.getOrNull()?.takeIf { it.length >= 200 }?.let { write(ctx, d, it); return@withContext it }
        }
        // Без сети или сеть не ответила — то, что сохранено раньше, даже если давно.
        read(ctx, d)
    }

    /** Есть ли что-то без сети: если на этот день встроенных событий нет — ближайшие встроенные. */
    fun nearestBuiltIn(ctx: Context, d: LocalDate): Pair<LocalDate, List<HistoryDay.Event>>? {
        val all = builtIn(ctx)
        for (k in 1..30) {
            val n = d.plusDays(k.toLong())
            all[md(n)]?.let { return n to it }
        }
        return null
    }

    private fun fetch(d: LocalDate): String {
        val c = URL("https://ru.wikipedia.org/api/rest_v1/feed/onthisday/all/%02d/%02d".format(d.monthValue, d.dayOfMonth)).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "DASEIN/1.0 (Android; personal app)")
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally { c.disconnect() }
    }
}

/** «В этот день в культуре»: встроенная подборка + рождения и культурные события из Википедии (тот же кэш, что у «Истории»). */
object CultureRepo {
    private var builtIn: Map<String, List<com.dasein.poryadok.logic.CultureDay.Item>>? = null

    fun builtIn(ctx: Context): Map<String, List<com.dasein.poryadok.logic.CultureDay.Item>> = builtIn ?: runCatching {
        com.dasein.poryadok.logic.CultureDay.parseBuiltIn(ctx.assets.open("culture/culture.json").bufferedReader().use { it.readText() })
    }.getOrDefault(emptyMap()).also { builtIn = it }

    private val memo = java.util.concurrent.ConcurrentHashMap<LocalDate, List<com.dasein.poryadok.logic.CultureDay.Item>>()

    fun cached(d: LocalDate): List<com.dasein.poryadok.logic.CultureDay.Item>? = memo[d]

    suspend fun day(ctx: Context, d: LocalDate, online: Boolean = true): List<com.dasein.poryadok.logic.CultureDay.Item> = withContext(Dispatchers.IO) {
        com.dasein.poryadok.logic.CultureDay.merge(
            builtIn(ctx)[HistoryRepo.md(d)].orEmpty(),
            HistoryRepo.wikiText(ctx, d, online)?.let { com.dasein.poryadok.logic.CultureDay.parseWiki(it) }.orEmpty(),
        ).also { r -> if (r.size >= (memo[d]?.size ?: 0)) memo[d] = r }
    }
}
