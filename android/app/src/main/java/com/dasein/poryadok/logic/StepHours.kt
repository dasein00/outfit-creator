package com.dasein.poryadok.logic

/**
 * Шаги по часам с датчика телефона: датчик опрашивается раз в 15 минут, прирост записывается в текущий час.
 * Хранится строкой «день:24 числа через запятую;…» за последние [KEEP] дней.
 */
object StepHours {
    const val KEEP = 14

    fun decode(s: String): Map<Long, IntArray> = s.split(';').mapNotNull { rec ->
        val i = rec.indexOf(':')
        if (i <= 0) return@mapNotNull null
        val day = rec.substring(0, i).toLongOrNull() ?: return@mapNotNull null
        val v = rec.substring(i + 1).split(',').map { it.toIntOrNull() ?: 0 }
        if (v.size != 24) null else day to v.toIntArray()
    }.toMap()

    fun encode(m: Map<Long, IntArray>): String =
        m.entries.sortedBy { it.key }.takeLast(KEEP).joinToString(";") { (d, v) -> "$d:" + v.joinToString(",") }

    /** Прибавляет [steps] к часу [hour] дня [day]. */
    fun add(s: String, day: Long, hour: Int, steps: Int): String {
        if (steps <= 0 || hour !in 0..23) return s
        val m = decode(s).toMutableMap()
        val arr = m[day]?.copyOf() ?: IntArray(24)
        arr[hour] += steps
        m[day] = arr
        return encode(m.filterKeys { it > day - KEEP })
    }

    /** Почасовые значения из двух источников: берётся большее в каждом часе. */
    fun merge(a: IntArray?, b: IntArray?): IntArray = IntArray(24) { maxOf(a?.getOrNull(it) ?: 0, b?.getOrNull(it) ?: 0) }

    data class Summary(val total: Int, val peakHour: Int, val activeHours: Int, val activeMinutes: Int, val km: Double)

    /**
     * Итоги дня: пик, часы с заметной активностью (от 250 шагов), время в движении (≈100 шагов в минуту)
     * и расстояние по длине шага (≈0,415 роста, без роста — 0,75 м).
     */
    fun summary(h: IntArray, heightCm: Double?): Summary {
        val total = h.sum()
        val peak = if (total == 0) -1 else h.indices.maxBy { h[it] }
        val stride = if (heightCm != null && heightCm > 100) heightCm * 0.415 / 100 else 0.75
        return Summary(total, peak, h.count { it >= 250 }, Math.round(total / 100.0).toInt(), total * stride / 1000)
    }
}
