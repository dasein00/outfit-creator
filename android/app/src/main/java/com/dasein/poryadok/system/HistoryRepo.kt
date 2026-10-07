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

    private fun cacheFile(ctx: Context, d: LocalDate) = File(File(ctx.cacheDir, "history").apply { mkdirs() }, "${md(d)}.json")

    /** Сначала то, что есть без сети; [online] — дополнить из Википедии (кэш на месяц). */
    suspend fun day(ctx: Context, d: LocalDate, online: Boolean = true): List<HistoryDay.Event> = withContext(Dispatchers.IO) {
        val own = builtIn(ctx)[md(d)].orEmpty()
        HistoryDay.merge(own, wikiText(ctx, d, online)?.let { HistoryDay.parseWiki(it) }.orEmpty())
    }

    /** Ответ Википедии «В этот день» за дату (события, рождения): из кэша или из сети. Нужен и «Истории», и «Культуре». */
    suspend fun wikiText(ctx: Context, d: LocalDate, online: Boolean): String? = withContext(Dispatchers.IO) {
        val f = cacheFile(ctx, d)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 30L * 86_400_000
        when {
            fresh -> runCatching { f.readText() }.getOrNull()
            online -> runCatching { fetch(d) }.getOrNull()?.also { runCatching { f.writeText(it) } } ?: runCatching { f.readText() }.getOrNull()
            else -> runCatching { f.readText() }.getOrNull()
        }
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

    suspend fun day(ctx: Context, d: LocalDate, online: Boolean = true): List<com.dasein.poryadok.logic.CultureDay.Item> = withContext(Dispatchers.IO) {
        com.dasein.poryadok.logic.CultureDay.merge(
            builtIn(ctx)[HistoryRepo.md(d)].orEmpty(),
            HistoryRepo.wikiText(ctx, d, online)?.let { com.dasein.poryadok.logic.CultureDay.parseWiki(it) }.orEmpty(),
        )
    }
}
