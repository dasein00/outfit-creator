package com.dasein.poryadok.system

import android.content.Context
import android.util.Log
import com.dasein.poryadok.logic.SmallTalk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Новые факты для Small Talks из сети. Сохраняются на телефоне (files/smalltalk/online.txt в формате «Сфера|Текст»)
 * и дальше показываются вместе со встроенными — в том числе без интернета.
 *
 * Источники:
 *  - рубрика Википедии «Знаете ли вы» — проверенные редакторами факты, по месяцу архива за раз;
 *  - бесплатный ИИ (если в настройках есть ключ) — раз в день подборка по сферам.
 */
object TalkOnline {
    const val SPHERE_WIKI = "Знаете ли вы"
    private const val MONTHS_BACK = 36
    private val json = Json { ignoreUnknownKeys = true }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("talk_online", Context.MODE_PRIVATE)
    fun file(ctx: Context) = File(ctx.filesDir, "smalltalk/online.txt")

    /** Сохранённые факты из сети. */
    fun facts(ctx: Context): List<com.dasein.poryadok.logic.Fact> = runCatching { SmallTalk.parseFacts(file(ctx).readText()) }.getOrDefault(emptyList())

    fun count(ctx: Context): Int = runCatching { file(ctx).readLines().count { '|' in it } }.getOrDefault(0)

    private fun append(ctx: Context, lines: List<Pair<String, String>>): Int {
        if (lines.isEmpty()) return 0
        val f = file(ctx).apply { parentFile?.mkdirs() }
        val have = runCatching { f.readLines().map { it.substringAfter('|').trim() }.toHashSet() }.getOrDefault(hashSetOf())
        val fresh = lines.filter { (_, t) -> t !in have }.distinctBy { it.second }
        if (fresh.isEmpty()) return 0
        f.appendText(fresh.joinToString("\n", postfix = "\n") { (s, t) -> "$s|${t.replace('\n', ' ').replace('|', '/')}" })
        com.dasein.poryadok.ui.talk.TalkRepo.invalidate()
        return fresh.size
    }

    /** Сохранить свои факты (например, найденные ИИ по теме) в каталог. Возвращает, сколько новых. */
    fun addFacts(ctx: Context, sphere: String, facts: List<String>): Int = append(ctx, facts.map { sphere to it })

    /** Факты, с чего начать разговор и что спросить — по любой теме, которую вы напишете (ИИ). */
    data class Topic(val facts: List<String>, val openers: List<String>, val questions: List<String>)

    suspend fun topic(ctx: Context, topic: String): Topic = withContext(Dispatchers.IO) {
        val system = "Ты помогаешь вести лёгкую светскую беседу. По теме пользователя (праздник, событие, место, увлечение, что угодно) " +
            "дай по-русски: 8 интересных малоизвестных, но достоверных фактов (1–2 предложения каждый, без выдумок); " +
            "3 естественные фразы, чтобы завести разговор на эту тему; 4 открытых вопроса собеседнику. " +
            "Ответ строго JSON: {\"facts\":[\"…\"],\"openers\":[\"…\"],\"questions\":[\"…\"]}"
        val o = Ai.extractJson(Ai.ask(ctx, system, topic)) ?: throw Ai.AiError("ИИ ответил не по формату — попробуйте ещё раз.")
        fun list(k: String) = (o[k] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
        Topic(list("facts"), list("openers"), list("questions"))
    }

    /** Догрузить новое: свежий месяц «Знаете ли вы» (раз в день), один старый месяц архива и подборку от ИИ (раз в день). */
    suspend fun sync(ctx: Context): Int = withContext(Dispatchers.IO) {
        var got = 0
        val p = sp(ctx)
        val today = LocalDate.now().toEpochDay()
        runCatching {
            if (p.getLong("wiki_day", -1) != today) {
                val now = LocalDate.now()
                got += append(ctx, wikiMonth(now.year, now.monthValue))
                // Архив — по одному месяцу за раз, от недавних к старым.
                val back = p.getInt("wiki_back", 1)
                if (back <= MONTHS_BACK) {
                    val m = now.minusMonths(back.toLong())
                    got += append(ctx, wikiMonth(m.year, m.monthValue))
                    p.edit().putInt("wiki_back", back + 1).apply()
                }
                p.edit().putLong("wiki_day", today).apply()
            }
        }.onFailure { Log.w("DASEIN", "talk wiki", it) }
        runCatching {
            if (Ai.enabled(ctx) && p.getLong("ai_day", -1) != today) {
                got += append(ctx, aiFacts(ctx))
                p.edit().putLong("ai_day", today).apply()
            }
        }.onFailure { Log.w("DASEIN", "talk ai", it) }
        if (got > 0) p.edit().putLong("last_at", System.currentTimeMillis()).apply()
        got
    }

    fun lastAt(ctx: Context) = sp(ctx).getLong("last_at", 0)

    // ---------- Википедия «Знаете ли вы» ----------

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 25_000
        c.setRequestProperty("User-Agent", "DASEIN/1.0 (Android; personal app)")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally { c.disconnect() }
    }

    private fun wikiMonth(year: Int, month: Int): List<Pair<String, String>> {
        val page = "Проект:Знаете ли вы/Архив рубрики/%d-%02d".format(year, month)
        val url = "https://ru.wikipedia.org/w/api.php?action=parse&format=json&formatversion=2&redirects=1&prop=text&page=" +
            URLEncoder.encode(page, "UTF-8")
        val body = runCatching { get(url) }.getOrNull() ?: return emptyList()
        val html = (json.parseToJsonElement(body).jsonObject["parse"] as? JsonObject)?.get("text")?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: return emptyList()
        return dykFacts(html).map { classify(it) to it }
    }

    /** Строки рубрики «…что …?» → утверждения. */
    internal fun dykFacts(html: String): List<String> {
        val text = html
            .replace(Regex("(?i)<(br|/li|/p|/dd|/div)[^>]*>"), "\n")
            .replace(Regex("<sup[^>]*>.*?</sup>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&#160;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&#91;", "[").replace("&#93;", "]")
        return text.lines().map { it.trim() }
            .filter { it.startsWith("…") || it.startsWith("...") }
            .map { it.removePrefix("…").removePrefix("...").trim().replace(Regex("\\s+"), " ").replace(Regex("\\[\\d+]"), "") }
            .filter { it.length in 25..420 }
            .map { s ->
                if (s.startsWith("что ")) {
                    val b = s.removePrefix("что ").trim().trimEnd('?', ' ')
                    b.replaceFirstChar { it.uppercase() } + "."
                } else "Знаете ли вы, " + s.trimEnd(' ') + if (s.endsWith("?")) "" else "?"
            }
    }

    /** Сфера по ключевым словам; если не понятно — «Знаете ли вы». */
    private val KEYS = listOf(
        "Кино" to listOf("фильм", "кино", "режисс", "актёр", "актрис", "сериал", "мультфильм"),
        "Музыка" to listOf("песн", "композитор", "музык", "опер", "альбом", "группа ", "певиц", "певец", "симфон"),
        "Литература" to listOf("роман", "писател", "поэт", "стихотвор", "книг", "повест", "рассказ"),
        "Искусство" to listOf("картин", "художник", "скульпт", "музе", "живопис"),
        "Космос" to listOf("планет", "звезд", "звёзд", "космос", "космическ", "астероид", "галактик", "луны", "марс"),
        "Животные" to listOf("птиц", "животн", "насеком", "рыб", "вид ", "млекопит", "ящериц", "жук", "паук", "змей"),
        "Спорт" to listOf("футбол", "хоккей", "чемпион", "олимпи", "спортсмен", "теннис", "матч"),
        "Еда" to listOf("блюд", "кухн", "напит", "пиво", "вино", "рецепт", "сыр"),
        "Архитектура" to listOf("собор", "церк", "храм", "здани", "дворец", "архитект", "мост", "башн"),
        "Транспорт" to listOf("корабл", "судно", "поезд", "самолёт", "самолет", "автомоб", "железн", "метро"),
        "Технологии" to listOf("компьютер", "программ", "изобрет", "робот", "интернет", "двигател"),
        "Наука" to listOf("учён", "учен", "физик", "химик", "биолог", "открыт", "математик", "теорем"),
        "География" to listOf("остров", "река", "озер", "озёр", "гора ", "город", "деревн", "посёлок", "посел", "стран"),
        "История" to listOf("век", "император", "царь", "король", "войн", "битв", "князь"),
        "Язык" to listOf("слово", "язык", "назван", "фамили"),
    )

    private fun classify(t: String): String {
        val l = t.lowercase()
        return KEYS.firstOrNull { (_, ks) -> ks.any { it in l } }?.first ?: SPHERE_WIKI
    }

    // ---------- ИИ ----------

    private suspend fun aiFacts(ctx: Context): List<Pair<String, String>> {
        val spheres = SmallTalk.SPHERES.map { it.first }
        val pick = spheres.shuffled().take(6)
        val system = "Ты автор рубрики интересных фактов для светской беседы. Пиши по-русски, только достоверные, проверяемые факты " +
            "(без выдумок и сомнительных цифр), коротко — 1–2 предложения, так, чтобы было интересно рассказать собеседнику. " +
            "Ответ строго JSON: {\"facts\":[{\"sphere\":\"…\",\"text\":\"…\"}]}"
        val user = "Дай 15 малоизвестных интересных фактов, примерно поровну по сферам: ${pick.joinToString(", ")}. " +
            "Поле sphere — ровно одно из этих названий."
        val obj = Ai.extractJson(Ai.ask(ctx, system, user)) ?: return emptyList()
        val arr = runCatching { obj["facts"]!!.jsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val s = (o["sphere"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val t = (o["text"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (t.length < 20) null else (if (s in spheres) s else SPHERE_WIKI) to t
        }
    }
}
