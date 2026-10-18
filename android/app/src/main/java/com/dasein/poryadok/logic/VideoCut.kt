package com.dasein.poryadok.logic

/**
 * Арифметика монтажа: ролик — это список кусков исходного файла (мс, first..last), склеенных подряд.
 * «Время монтажа» — позиция в склеенном ролике, «время исходника» — позиция в файле.
 */
object VideoCut {
    private fun len(r: LongRange) = (r.last - r.first).coerceAtLeast(0)

    /** Длина склеенного ролика. */
    fun total(segments: List<LongRange>): Long = segments.sumOf { len(it) }

    /** С какого места склейки начинается кусок [index]. */
    fun offsetOf(segments: List<LongRange>, index: Int): Long = segments.take(index.coerceIn(0, segments.size)).sumOf { len(it) }

    /** Позиция исходника внутри куска [segIndex] → время монтажа. */
    fun toEdited(segments: List<LongRange>, segIndex: Int, srcPos: Long): Long {
        val seg = segments.getOrNull(segIndex) ?: return 0L
        return offsetOf(segments, segIndex) + (srcPos - seg.first).coerceIn(0L, len(seg))
    }

    /** Время монтажа → (номер куска, позиция исходника). За концом — конец последнего куска. */
    fun fromEdited(segments: List<LongRange>, editedMs: Long): Pair<Int, Long> {
        if (segments.isEmpty()) return 0 to 0L
        var rest = editedMs.coerceAtLeast(0L)
        segments.forEachIndexed { i, s ->
            val l = len(s)
            if (rest < l || i == segments.lastIndex) return i to s.first + rest.coerceAtMost(l)
            rest -= l
        }
        return segments.lastIndex to segments.last().last
    }

    private val STEPS = longArrayOf(100, 250, 500, 1000, 2000, 5000, 10_000, 30_000, 60_000, 120_000, 300_000)

    /** Шаг делений шкалы времени: самый мелкий, при котором подписи не наезжают (≥ 60 px между делениями). */
    fun tickStep(pxPerMs: Float, minPx: Float = 60f): Long {
        if (pxPerMs <= 0f) return STEPS.last()
        return STEPS.firstOrNull { it * pxPerMs >= minPx } ?: STEPS.last()
    }

    /**
     * Время, вписанное руками: «1:05.3», «1:05», «65», «65,5», «0:07,25». Минуты необязательны.
     * null — если не разобрать.
     */
    fun parseTime(text: String): Long? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return null
        val parts = t.split(':')
        if (parts.size > 3 || parts.any { it.isEmpty() }) return null
        val secs = parts.last().toDoubleOrNull() ?: return null
        if (secs < 0) return null
        var total = 0L
        for (p in parts.dropLast(1)) total = total * 60 + (p.toLongOrNull()?.takeIf { it >= 0 } ?: return null)
        if (parts.size > 1 && secs >= 60) return null
        return total * 60_000 + Math.round(secs * 1000)
    }
}
