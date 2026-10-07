package com.dasein.poryadok.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.random.Random

/** Идиомы и устойчивые разговорные фразы английского: значение, пример и перевод примера. */
object Phrases {
    const val IDIOM = "idiom"
    const val PHRASE = "phrase"
    val CATS = linkedMapOf(IDIOM to "Идиомы", PHRASE to "Устойчивые фразы")

    data class Phrase(val en: String, val ru: String, val ex: String, val exRu: String, val cat: String) {
        val key get() = en.lowercase()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** {"phrases":[[en, ru, example, exampleRu, cat], …]} */
    fun parse(text: String): List<Phrase> = runCatching {
        ((json.parseToJsonElement(text) as JsonObject)["phrases"] as JsonArray).mapNotNull { e ->
            val a = e as? JsonArray ?: return@mapNotNull null
            fun s(i: Int) = (a.getOrNull(i) as? JsonPrimitive)?.contentOrNull.orEmpty()
            Phrase(s(0), s(1), s(2), s(3), s(4).ifBlank { IDIOM })
        }
    }.getOrDefault(emptyList())

    fun search(all: List<Phrase>, q: String): List<Phrase> {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return all
        return all.filter { t in it.en.lowercase() || t in it.ru.lowercase() }
    }

    /** На сегодня: сначала те, что пора повторить, потом новые. */
    fun session(all: List<Phrase>, progress: Map<String, Tutor.Progress>, today: Long, n: Int, rnd: Random): List<Phrase> {
        val due = all.filter { p -> progress[p.key]?.let { it.due <= today } == true }.shuffled(rnd)
        val fresh = all.filter { it.key !in progress }.shuffled(rnd)
        return (due + fresh).take(n)
    }

    /** Варианты значения: верное + три других из той же категории. */
    fun options(p: Phrase, all: List<Phrase>, rnd: Random): List<Phrase> =
        (all.filter { it.cat == p.cat && it.key != p.key }.shuffled(rnd).take(3) + p).shuffled(rnd)
}
