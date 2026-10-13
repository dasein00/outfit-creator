package com.dasein.poryadok.system

import android.content.Context
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.logic.CultureDay
import com.dasein.poryadok.system.Ai.arr
import com.dasein.poryadok.system.Ai.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Интересные факты о фильмах, сериалах и книгах из вашей коллекции:
 *  - Кинопоиск (раздел «Знаете ли вы» и киноляпы) — если в настройках есть токен;
 *  - русская Википедия — разделы «Интересные факты», «Съёмки», «Создание», «Производство»…;
 *  - ИИ по кнопке — в духе IMDb Trivia.
 * Сохраняются на телефоне. Факты без спойлеров дублируются в Small Talks, а годовщина премьеры
 * появляется в «В этот день в культуре».
 */
object MediaFacts {
    @Serializable
    data class Fact(val text: String, val source: String, val spoiler: Boolean = false)

    @Serializable
    data class Saved(val facts: List<Fact> = emptyList(), val premiere: String? = null, val fetchedAt: Long = 0, val title: String = "", val year: Int? = null, val kind: Int = 0)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    val version = androidx.compose.runtime.mutableIntStateOf(0)
    private fun dir(ctx: Context) = File(ctx.filesDir, "media_facts").apply { mkdirs() }
    private fun file(ctx: Context, id: Long) = File(dir(ctx), "$id.json")

    fun saved(ctx: Context, id: Long): Saved? = runCatching { json.decodeFromString(Saved.serializer(), file(ctx, id).readText()) }.getOrNull()

    private fun save(ctx: Context, id: Long, s: Saved) {
        runCatching { file(ctx, id).writeText(json.encodeToString(Saved.serializer(), s)) }
        culture = null
        version.intValue++
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 25_000
        c.setRequestProperty("User-Agent", "DASEIN/1.0 (Android; personal app)")
        c.setRequestProperty("Accept", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally { c.disconnect() }
    }

    private fun plain(html: String) = html
        .replace(Regex("(?is)<sup[^>]*>.*?</sup>"), "")
        .replace(Regex("(?i)<br\\s*/?>"), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&#160;", " ").replace("&laquo;", "«").replace("&raquo;", "»").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&mdash;", "—").replace("&ndash;", "–").replace("&amp;", "&")
        .replace(Regex("\\[\\d+]|\\[источник не указан[^]]*]"), "")
        .replace(Regex("\\s+"), " ").trim()

    /** Загрузить факты из Кинопоиска и Википедии (без ИИ). Уже сохранённые факты ИИ остаются. */
    suspend fun fetch(ctx: Context, m: MediaItem): Saved = withContext(Dispatchers.IO) {
        val old = saved(ctx, m.id)
        val facts = ArrayList<Fact>()
        var premiere: String? = old?.premiere
        if (m.kind != MediaKind.BOOK) runCatching { kinopoisk(m) }.getOrNull()?.let { (f, p) -> facts += f; if (p != null) premiere = p }
        runCatching { wikipedia(m) }.getOrNull()?.let { (f, p) -> facts += f; if (premiere == null) premiere = p }
        val ai = old?.facts.orEmpty().filter { it.source == "ИИ" }
        val all = (facts + ai).distinctBy { it.text.lowercase().take(80) }
        val s = Saved(all, premiere, System.currentTimeMillis(), m.title, m.year, m.kind)
        save(ctx, m.id, s)
        toTalks(ctx, m, facts)
        s
    }

    /** Ещё факты от ИИ (в духе IMDb Trivia и «Знаете ли вы»). */
    suspend fun askAi(ctx: Context, m: MediaItem): Saved = withContext(Dispatchers.IO) {
        val old = saved(ctx, m.id) ?: Saved(title = m.title, year = m.year, kind = m.kind)
        val what = when (m.kind) { MediaKind.BOOK -> "книге"; MediaKind.SERIES -> "сериале"; else -> "фильме" }
        val system = "Ты киновед и литературный редактор. Дай 8 интересных, малоизвестных и достоверных фактов о $what — как в разделах IMDb Trivia и «Знаете ли вы» Кинопоиска: " +
            "съёмки и создание, кастинг, кто чуть не сыграл роль, импровизации актёров, бюджет и рекорды, отсылки и пасхалки, судьба после выхода. " +
            "Не выдумывай: если не уверен — не пиши. Не повторяй уже известные факты из списка. Отметь факты, раскрывающие сюжет, как spoiler=true. " +
            "Пиши по-русски, каждый факт 1–2 предложения. Ответ строго JSON: {\"facts\":[{\"text\":\"…\",\"spoiler\":false}]}"
        val user = buildString {
            append("${m.title}")
            if (m.originalTitle.isNotBlank()) append(" / ${m.originalTitle}")
            m.year?.let { append(" ($it)") }
            if (m.creators.isNotBlank()) append(", ${m.creators}")
            if (old.facts.isNotEmpty()) append("\nУже известно:\n" + old.facts.take(15).joinToString("\n") { "- " + it.text.take(140) })
        }
        val o = Ai.extractJson(Ai.ask(ctx, system, user)) ?: throw Ai.AiError("ИИ ответил не по формату — попробуйте ещё раз.")
        val fresh = o.arr("facts").orEmpty().mapNotNull { e ->
            val f = e as? JsonObject ?: return@mapNotNull null
            val t = f.str("text")?.trim().orEmpty()
            if (t.length < 20) null else Fact(t, "ИИ", (f["spoiler"] as? JsonPrimitive)?.booleanOrNull == true)
        }
        val s = old.copy(facts = (old.facts + fresh).distinctBy { it.text.lowercase().take(80) }, title = m.title, year = m.year, kind = m.kind)
        save(ctx, m.id, s)
        toTalks(ctx, m, fresh)
        s
    }

    fun remove(ctx: Context, id: Long, f: Fact) {
        val s = saved(ctx, id) ?: return
        save(ctx, id, s.copy(facts = s.facts - f))
    }

    /** Факты без спойлеров — в каталог Small Talks: «Кино» или «Литература», с названием, чтобы было понятно, о чём речь. */
    private fun toTalks(ctx: Context, m: MediaItem, facts: List<Fact>) {
        val clean = facts.filter { !it.spoiler && it.text.length in 30..400 }
        if (clean.isEmpty()) return
        val sphere = if (m.kind == MediaKind.BOOK) "Литература" else "Кино"
        val name = "«${m.title}»" + (m.year?.let { " ($it)" } ?: "")
        runCatching { TalkOnline.addFacts(ctx, sphere, clean.map { "$name: ${it.text}" }) }
    }

    // ---------- Кинопоиск ----------

    private suspend fun kinopoisk(m: MediaItem): Pair<List<Fact>, String?>? {
        val token = Graph.prefs.now().kinopoiskToken.trim()
        if (token.isBlank()) return null
        val h = mapOf("X-API-KEY" to token)
        val id = m.externalId.takeIf { it.startsWith("kp:") }?.removePrefix("kp:") ?: run {
            val r = json.parseToJsonElement(get("https://api.kinopoisk.dev/v1.4/movie/search?page=1&limit=5&query=${enc(m.title)}", h)).jsonObject
            val docs = r["docs"] as? JsonArray ?: return null
            docs.mapNotNull { it as? JsonObject }.firstOrNull { d -> m.year == null || (d["year"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() == m.year }
                ?.get("id")?.let { (it as? JsonPrimitive)?.contentOrNull }
        } ?: return null
        val o = json.parseToJsonElement(get("https://api.kinopoisk.dev/v1.4/movie/$id", h)).jsonObject
        val facts = (o["facts"] as? JsonArray).orEmpty().mapNotNull { e ->
            val f = e as? JsonObject ?: return@mapNotNull null
            val t = plain((f["value"] as? JsonPrimitive)?.contentOrNull.orEmpty())
            if (t.length < 20) return@mapNotNull null
            val blooper = (f["type"] as? JsonPrimitive)?.contentOrNull == "BLOOPER"
            Fact(if (blooper) "Киноляп: $t" else t, "Кинопоиск", (f["spoiler"] as? JsonPrimitive)?.booleanOrNull == true)
        }.take(30)
        val prem = (o["premiere"] as? JsonObject)?.let { p ->
            listOf("world", "russia", "digital").firstNotNullOfOrNull { k -> (p[k] as? JsonPrimitive)?.contentOrNull?.take(10) }
        }
        return facts to prem
    }

    // ---------- Википедия ----------

    private val SECTIONS = listOf("интересные факты", "факты", "любопытные факты", "съёмки", "создание", "производство", "история создания", "кастинг", "подбор актёров", "работа над", "предыстория", "музыка", "сборы", "награды")

    private fun wikipedia(m: MediaItem): Pair<List<Fact>, String?>? {
        val base = "https://ru.wikipedia.org/w/api.php?format=json&formatversion=2"
        val kindWord = when (m.kind) { MediaKind.BOOK -> "роман книга"; MediaKind.SERIES -> "телесериал"; else -> "фильм" }
        val q = listOfNotNull(m.title, m.year?.toString(), kindWord).joinToString(" ")
        val search = json.parseToJsonElement(get("$base&action=query&list=search&srlimit=3&srsearch=${enc(q)}")).jsonObject
        val title = ((search["query"] as? JsonObject)?.get("search") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("title") }
            ?.firstOrNull { t -> t.lowercase().contains(m.title.lowercase().take(12)) } ?: return null
        val parsed = json.parseToJsonElement(get("$base&action=parse&redirects=1&prop=sections%7Cproperties&page=${enc(title)}")).jsonObject["parse"] as? JsonObject ?: return null
        val sections = (parsed["sections"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val picked = sections.filter { s -> val l = s.str("line")?.lowercase().orEmpty(); SECTIONS.any { it in l } }.take(4)
        val facts = ArrayList<Fact>()
        for (s in picked) {
            val idx = s.str("index") ?: continue
            val line = s.str("line")?.lowercase().orEmpty()
            val html = runCatching {
                (json.parseToJsonElement(get("$base&action=parse&redirects=1&prop=text&section=$idx&page=${enc(title)}")).jsonObject["parse"] as? JsonObject)?.str("text")
            }.getOrNull() ?: continue
            val listy = "факт" in line
            // Пункты списка — отдельными фактами; абзацы — по 1–2 предложения.
            val items = Regex("(?is)<li[^>]*>(.*?)</li>").findAll(html).map { plain(it.groupValues[1]) }.toList()
            val paras = Regex("(?is)<p[^>]*>(.*?)</p>").findAll(html).map { plain(it.groupValues[1]) }.toList()
            val chunks = if (listy && items.isNotEmpty()) items else paras.flatMap { p ->
                p.split(Regex("(?<=[.!?])\\s+(?=[А-ЯЁA-Z«])")).chunked(2).map { it.joinToString(" ") }
            }
            chunks.filter { it.length in 40..420 && !it.startsWith("Основная статья") }.take(if (listy) 12 else 3).forEach { facts += Fact(it, "Википедия") }
        }
        // Дата премьеры — из Викиданных (P577), чтобы показать годовщину в «В этот день в культуре».
        val qid = ((parsed["properties"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull { it.str("name") == "wikibase_item" })?.str("*")
            ?: (parsed["properties"] as? JsonObject)?.str("wikibase_item")
        val prem = qid?.let { id ->
            runCatching {
                val c = json.parseToJsonElement(get("https://www.wikidata.org/w/api.php?format=json&action=wbgetclaims&property=P577&entity=$id")).jsonObject
                ((c["claims"] as? JsonObject)?.get("P577") as? JsonArray).orEmpty().mapNotNull { cl ->
                    val v = (((cl as? JsonObject)?.get("mainsnak") as? JsonObject)?.get("datavalue") as? JsonObject)?.get("value") as? JsonObject
                    val t = v?.str("time"); val prec = (v?.get("precision") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                    if (t != null && prec == 11) t.removePrefix("+").take(10) else null
                }.minOrNull()
            }.getOrNull()
        }
        return facts.distinctBy { it.text } to prem
    }

    // ---------- «В этот день в культуре» ----------

    @Volatile private var culture: List<Saved>? = null

    /** Годовщины премьер фильмов и книг из вашей коллекции на этот день — с интересным фактом. */
    fun cultureItems(ctx: Context, d: LocalDate): List<CultureDay.Item> {
        val all = culture ?: dir(ctx).listFiles().orEmpty().mapNotNull { f -> runCatching { json.decodeFromString(Saved.serializer(), f.readText()) }.getOrNull() }.also { culture = it }
        val md = "%02d-%02d".format(d.monthValue, d.dayOfMonth)
        return all.filter { it.premiere?.length == 10 && it.premiere?.substring(5) == md }.mapNotNull { s ->
            val y = s.premiere!!.take(4).toIntOrNull() ?: return@mapNotNull null
            val fact = s.facts.filter { !it.spoiler }.let { l -> if (l.isEmpty()) null else l[(d.toEpochDay() % l.size).toInt()] }
            val what = when (s.kind) { MediaKind.BOOK -> "Выход книги"; MediaKind.SERIES -> "Премьера сериала"; else -> "Премьера фильма" }
            CultureDay.Item(
                CultureDay.EVENT, y, "«${s.title}»",
                "$what из вашей коллекции." + (fact?.let { " ${it.text}" } ?: ""),
                "Мир", source = "Моя коллекция",
            )
        }
    }
}
