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
            val page = pages.firstOrNull { p -> val title = p.obj("titles")?.str("normalized") ?: p.str("normalizedtitle") ?: p.str("title").orEmpty(); !YEAR_PAGE.matches(title.replace('_', ' ')) && !p.str("extract").isNullOrBlank() }
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

    /**
     * Список дня: сначала встроенные (с подробным контекстом), затем из Википедии — с контекстом статьи впереди.
     * Повторы по году отбрасываются, если встроенное событие уже про тот же год.
     */
    fun merge(builtIn: List<Event>, wiki: List<Event>): List<Event> {
        val years = builtIn.map { it.year }.toSet()
        val w = wiki.filter { it.year !in years }.sortedWith(compareByDescending<Event> { it.context.length > 120 }.thenByDescending { it.image.isNotBlank() }.thenBy { it.year })
        return builtIn + w
    }
}
