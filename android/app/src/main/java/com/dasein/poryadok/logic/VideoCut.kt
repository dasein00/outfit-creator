package com.dasein.poryadok.logic

import kotlin.math.roundToLong

/**
 * Арифметика монтажа: ролик — это куски ([Clip]) одного или нескольких исходных файлов, склеенные подряд.
 * У каждого куска своя скорость. «Время ленты» — позиция в склеенном ролике (с учётом скорости),
 * «время исходника» — позиция в файле куска.
 */
object VideoCut {
    /** Кусок: исходник №[src], отрезок [start]..[end] (мс исходника), скорость [speed]. */
    data class Clip(val src: Int, val start: Long, val end: Long, val speed: Float = 1f) {
        /** Длина куска в исходнике. */
        val srcLen: Long get() = (end - start).coerceAtLeast(0)

        /** Длина куска на ленте — с учётом скорости. */
        val len: Long get() = (srcLen / speed.coerceAtLeast(0.1f)).roundToLong()
    }

    /** Самый короткий кусок исходника, мс. */
    const val MIN_MS = 200L

    /** Длина склеенного ролика. */
    fun total(clips: List<Clip>): Long = clips.sumOf { it.len }

    /** С какого места ленты начинается кусок [index]. */
    fun offsetOf(clips: List<Clip>, index: Int): Long = clips.take(index.coerceIn(0, clips.size)).sumOf { it.len }

    /** Место исходника внутри куска [index] → время ленты. */
    fun toEdited(clips: List<Clip>, index: Int, srcPos: Long): Long {
        val c = clips.getOrNull(index) ?: return 0L
        return offsetOf(clips, index) + ((srcPos - c.start).coerceIn(0L, c.srcLen) / c.speed.coerceAtLeast(0.1f)).roundToLong().coerceAtMost(c.len)
    }

    /** Время ленты → (номер куска, место исходника). За концом — конец последнего куска. */
    fun fromEdited(clips: List<Clip>, editedMs: Long): Pair<Int, Long> {
        if (clips.isEmpty()) return 0 to 0L
        var rest = editedMs.coerceAtLeast(0L)
        clips.forEachIndexed { i, c ->
            if (rest < c.len || i == clips.lastIndex) {
                val src = c.start + (rest.coerceAtMost(c.len) * c.speed).roundToLong()
                return i to src.coerceIn(c.start, c.end)
            }
            rest -= c.len
        }
        return clips.lastIndex to clips.last().end
    }

    /** Разрезать кусок под бегунком на два. null — бегунок у самого края куска. Второе — номер правой части. */
    fun split(clips: List<Clip>, editedMs: Long): Pair<List<Clip>, Int>? {
        val (i, src) = fromEdited(clips, editedMs)
        val c = clips.getOrNull(i) ?: return null
        if (src - c.start < MIN_MS || c.end - src < MIN_MS) return null
        val l = clips.toMutableList()
        l[i] = c.copy(end = src)
        l.add(i + 1, c.copy(start = src))
        return l to i + 1
    }

    /** Удалить начало куска под бегунком — всё до бегунка. */
    fun trimBefore(clips: List<Clip>, editedMs: Long): List<Clip>? {
        val (i, src) = fromEdited(clips, editedMs)
        val c = clips.getOrNull(i) ?: return null
        if (src - c.start < 1 || c.end - src < MIN_MS) return null
        return clips.toMutableList().also { it[i] = c.copy(start = src) }
    }

    /** Удалить конец куска под бегунком — всё после бегунка. */
    fun trimAfter(clips: List<Clip>, editedMs: Long): List<Clip>? {
        val (i, src) = fromEdited(clips, editedMs)
        val c = clips.getOrNull(i) ?: return null
        if (c.end - src < 1 || src - c.start < MIN_MS) return null
        return clips.toMutableList().also { it[i] = c.copy(end = src) }
    }

    fun duplicate(clips: List<Clip>, index: Int): List<Clip> =
        clips.getOrNull(index)?.let { c -> clips.toMutableList().also { it.add(index + 1, c) } } ?: clips

    /** Удалить кусок; последний оставшийся не удаляется. */
    fun delete(clips: List<Clip>, index: Int): List<Clip> =
        if (clips.size <= 1 || index !in clips.indices) clips else clips.toMutableList().also { it.removeAt(index) }

    /** Переставить кусок на [delta] позиций. */
    fun move(clips: List<Clip>, index: Int, delta: Int): List<Clip> {
        val j = index + delta
        if (index !in clips.indices || j !in clips.indices) return clips
        return clips.toMutableList().also { val c = it.removeAt(index); it.add(j, c) }
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
