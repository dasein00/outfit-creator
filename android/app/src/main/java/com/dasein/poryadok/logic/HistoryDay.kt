package com.dasein.poryadok.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * «В этот день в истории»: встроенные события с подробным контекстом (работают без интернета)
 * и события из русской Википедии (раздел «В этот день» — https://ru.wikipedia.org/api/rest_v1/feed/onthisday).
 */
object HistoryDay {
    data class Event(
        val year: Int,
        val title: String,
        /** Что произошло. */
        val text: String,
        /** Подробный контекст: причины, последствия, детали. */
        val context: String,
        val image: String = "",
        val url: String = "",
        val source: String = "DASEIN",
    ) {
        val yearLabel get() = if (year < 0) "${-year} до н. э." else "$year"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray

    /** Встроенные события: {"events":[{"md":"10-06","year":1889,"title":…,"text":…,"context":…}]}. */
    fun parseBuiltIn(text: String): Map<String, List<Event>> = runCatching {
        json.parseToJsonElement(text).let { it as JsonObject }.arr("events").orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val md = o.str("md") ?: return@mapNotNull null
            md to Event((o["year"] as? JsonPrimitive)?.intOrNull ?: 0, o.str("title").orEmpty(), o.str("text").orEmpty(), o.str("context").orEmpty())
        }.groupBy({ it.first }, { it.second })
    }.getOrDefault(emptyMap())

    private val YEAR_PAGE = Regex("^\\d{1,4}( год| годы|-е| до н\\. э\\.)?$|^\\d{1,2} [а-я]+$|^[IVXLC]+ век", RegexOption.IGNORE_CASE)

    /**
     * Ответ Википедии (onthisday/all или events): события с годом, текстом и статьёй-контекстом.
     * Статьи-годы и статьи-даты пропускаются: контекст берётся из статьи о самом событии или человеке.
     */
    fun parseWiki(text: String): List<Event> = runCatching {
        val root = json.parseToJsonElement(text) as JsonObject
        val items = root.arr("selected").orEmpty() + root.arr("events").orEmpty()
        items.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val t = o.str("text")?.trim().orEmpty()
            val year = (o["year"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            if (t.isBlank()) return@mapNotNull null
            val pages = o.arr("pages").orEmpty().mapNotNull { it as? JsonObject }
            val page = bestPage(t, pages)
            val title = (page?.obj("titles")?.str("normalized") ?: page?.str("normalizedtitle") ?: page?.str("title"))?.replace('_', ' ').orEmpty()
            Event(
                year = year,
                title = title.ifBlank { t.take(80) },
                text = t.replaceFirstChar { it.uppercase() },
                context = page?.str("extract").orEmpty(),
                image = page?.obj("thumbnail")?.str("source").orEmpty(),
                url = page?.obj("content_urls")?.obj("mobile")?.str("page") ?: page?.obj("content_urls")?.obj("desktop")?.str("page").orEmpty(),
                source = "Википедия",
            )
        }.distinctBy { it.year to it.text }
    }.getOrDefault(emptyList())

    private fun titleOf(p: JsonObject) = (p.obj("titles")?.str("normalized") ?: p.str("normalizedtitle") ?: p.str("title").orEmpty()).replace('_', ' ')

    /** Статьи о местах (городах, странах, реках) — не контекст события, а фон; выбираем их в последнюю очередь. */
    private val PLACE = listOf(
        "город", "столица", "страна", "государство", "река", "остров", "область", "регион", "провинция", "штат", "деревня",
        "посёлок", "поселок", "село", "район", "департамент", "континент", "море", "озеро", "гора", "полуостров", "округ", "муниципалитет",
    )
    /** Слова, по которым видно, что статья — о самом событии. */
    private val EVENT = listOf(
        "убийство", "битва", "сражение", "война", "революция", "восстание", "переворот", "договор", "соглашение", "катастрофа",
        "крушение", "авария", "теракт", "землетрясение", "извержение", "пожар", "наводнение", "экспедиция", "полёт", "запуск",
        "открытие", "основание", "коронация", "выборы", "референдум", "инцидент", "осада", "штурм", "операция", "кризис",
        "конференция", "съезд", "премьера", "чемпионат", "олимпийск", "миссия", "реформа", "указ", "манифест", "конституция",
    )

    /**
     * Самая подходящая статья для контекста: о событии (по названию и описанию) или его участнике,
     * а не о городе или стране, где оно произошло. Статьи-годы и даты пропускаются.
     */
    internal fun bestPage(eventText: String, pages: List<JsonObject>): JsonObject? {
        val ev = eventText.lowercase()
        val evStems = stems(eventText)
        return pages.filter { p -> !YEAR_PAGE.matches(titleOf(p)) && !p.str("extract").isNullOrBlank() }
            .maxByOrNull { p ->
                val title = titleOf(p).lowercase()
                val desc = (p.str("description").orEmpty() + " " + p.str("extract").orEmpty().take(160)).lowercase()
                var score = 0
                if (EVENT.any { it in title }) score += 6
                if (EVENT.any { it in (p.str("description").orEmpty().lowercase()) }) score += 3
                if (PLACE.any { Regex("(^|[\\s,(—-])$it").containsMatchIn(desc) }) score -= 5
                // Чем больше слов названия статьи есть в тексте события, тем ближе статья к самому событию.
                score += stems(title).count { it in evStems }
                if (title.split(' ').size >= 2) score += 1
                if (title in ev) score -= 1
                score
            }
    }

    private val STOP = setOf("года", "году", "году", "были", "было", "была", "который", "которая", "после", "время", "также", "через")

    /** Основы слов (первые 4 буквы) — чтобы «Сеуле» и «сеульский», «королева» и «королевы» совпадали. */
    fun stems(text: String): Set<String> = Regex("[\\p{L}]{3,}").findAll(text.lowercase().replace('ё', 'е'))
        .map { it.value }.filter { it !in STOP }.map { it.take(4) }.toSet()

    /** Одно и то же событие, описанное по-разному: тот же год и минимум две общие основы слов (имена, места, действия). */
    fun similar(a: Event, b: Event): Boolean {
        if (a.year != b.year) return false
        val sa = stems(a.text + " " + a.title)
        val sb = stems(b.text + " " + b.title)
        val shared = (sa intersect sb).size
        return shared >= 2 && shared * 4 >= minOf(sa.size, sb.size)
    }

    /** Убираем повторы по смыслу: из похожих оставляем то, где больше подробностей. */
    fun dedupe(list: List<Event>): List<Event> {
        val out = mutableListOf<Event>()
        list.forEach { e ->
            val i = out.indexOfFirst { similar(it, e) }
            if (i < 0) out += e
            else if (e.source != "DASEIN" && out[i].source != "DASEIN" && e.context.length + e.text.length > out[i].context.length + out[i].text.length) out[i] = e
        }
        return out
    }

    /**
     * Список дня: сначала встроенные (с подробным контекстом), затем из Википедии — с контекстом статьи впереди.
     * Повторы по году отбрасываются, если встроенное событие уже про тот же год.
     */
    fun merge(builtIn: List<Event>, wiki: List<Event>): List<Event> {
        val years = builtIn.map { it.year }.toSet()
        val w = dedupe(wiki.filter { it.year !in years }).sortedWith(compareByDescending<Event> { it.context.length > 120 }.thenByDescending { it.image.isNotBlank() }.thenBy { it.year })
        return builtIn + w
    }
}
