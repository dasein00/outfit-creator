package com.dasein.poryadok.logic

import kotlin.random.Random

/** Факт из картотеки. id — хэш текста, поэтому новые факты в обновлениях не сбивают историю. */
data class Fact(val id: Int, val tag: String, val text: String)

/**
 * Колода фактов: «вперёд» — всегда ещё не прочитанный факт, «назад» — предыдущий из истории.
 * Когда прочитаны все, начинается новый круг в другом порядке.
 */
object FactDeck {
    data class State(val history: List<Int> = emptyList(), val pos: Int = 0, val round: Int = 0)

    fun parse(text: String): List<Fact> = text.lineSequence().mapNotNull { line ->
        val i = line.indexOf('|')
        if (i <= 0) return@mapNotNull null
        val body = line.substring(i + 1).trim()
        if (body.isEmpty()) null else Fact(body.hashCode(), line.substring(0, i).trim(), body)
    }.distinctBy { it.id }.toList()

    fun encode(history: List<Int>): String = history.joinToString(",")
    fun decode(s: String): List<Int> = s.split(',').mapNotNull { it.trim().toIntOrNull() }

    /** Убирает из истории удалённые факты и чинит позицию; если история пуста — берёт первый факт. */
    fun normalize(facts: List<Fact>, st: State, rnd: Random): State {
        if (facts.isEmpty()) return State()
        val ids = facts.map { it.id }.toSet()
        val h = st.history.filter { it in ids }
        if (h.isEmpty()) return State(listOf(pick(facts, emptySet(), rnd)), 0, st.round)
        return st.copy(history = h, pos = st.pos.coerceIn(0, h.lastIndex))
    }

    private fun pick(facts: List<Fact>, seen: Set<Int>, rnd: Random): Int {
        val fresh = facts.filter { it.id !in seen }
        return (if (fresh.isEmpty()) facts else fresh)[rnd.nextInt(if (fresh.isEmpty()) facts.size else fresh.size)].id
    }

    /** Следующий факт. Второе значение — true, если начался новый круг. */
    fun next(facts: List<Fact>, st0: State, rnd: Random): Pair<State, Boolean> {
        val st = normalize(facts, st0, rnd)
        if (st.pos < st.history.lastIndex) return st.copy(pos = st.pos + 1) to false
        val seen = st.history.toSet()
        if (seen.size >= facts.size) {
            // Все прочитаны: новый круг, текущий факт не повторяем сразу же.
            val cur = st.history[st.pos]
            val first = pick(facts, setOf(cur), rnd)
            return State(listOf(first), 0, st.round + 1) to true
        }
        val id = pick(facts, seen, rnd)
        return st.copy(history = st.history + id, pos = st.history.size) to false
    }

    fun prev(facts: List<Fact>, st0: State, rnd: Random): State {
        val st = normalize(facts, st0, rnd)
        return st.copy(pos = (st.pos - 1).coerceAtLeast(0))
    }

    fun current(facts: List<Fact>, st: State): Fact? {
        val id = st.history.getOrNull(st.pos) ?: return null
        return facts.firstOrNull { it.id == id }
    }

    /** Сколько фактов прочитано в этом круге. */
    fun read(st: State): Int = st.history.toSet().size
}
