package com.dasein.poryadok.logic

/**
 * «Что лучше?» — расстановка топа попарными сравнениями (вставка с делением пополам):
 * для 10 пунктов хватает ~20 вопросов вместо того, чтобы двигать стрелками.
 * Первый пункт [items] — самый лучший в итоге [result].
 */
class TopRank<T>(items: List<T>) {
    private val sorted = mutableListOf<T>()
    private val pending = ArrayDeque(items)
    private var cur: T? = null
    private var lo = 0
    private var hi = 0
    /** Сколько вопросов уже задано. */
    var asked = 0
        private set
    val total = items.size

    init { next() }

    private fun next() {
        cur = null
        while (pending.isNotEmpty()) {
            val c = pending.removeFirst()
            if (sorted.isEmpty()) { sorted += c; continue }
            cur = c; lo = 0; hi = sorted.size
            return
        }
    }

    /** Пара для вопроса: новый пункт и тот, с которым его сравниваем. null — всё расставлено. */
    fun question(): Pair<T, T>? = cur?.let { it to sorted[(lo + hi) / 2] }

    /** Сколько пунктов осталось расставить (включая текущий). */
    val left get() = pending.size + if (cur != null) 1 else 0

    /** Ответ: новый пункт лучше того, с которым сравнивали? */
    fun answer(newIsBetter: Boolean) {
        val c = cur ?: return
        asked++
        val mid = (lo + hi) / 2
        if (newIsBetter) hi = mid else lo = mid + 1
        if (lo >= hi) { sorted.add(lo, c); next() }
    }

    val finished get() = cur == null

    /** Итог: расставленные пункты, а нерасставленные (если прервали) — в прежнем порядке в конце. */
    fun result(): List<T> = sorted + listOfNotNull(cur) + pending
}
