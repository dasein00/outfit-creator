package com.dasein.poryadok.logic

import kotlin.math.abs

/**
 * Определение сна по использованию телефона.
 *
 * Идея: ночью вы не пользуетесь телефоном. Экран может загораться от уведомлений или будильника —
 * это не считается. «Использование» — экран включён и телефон разблокирован (или открыто приложение)
 * хотя бы минуту. Сон — самая длинная пауза в использовании в окне 19:00–13:00; короткие ночные
 * проверки телефона (до 10 минут между паузами от 45 минут) считаются пробуждениями, а не подъёмом.
 * Отбой = конец последнего использования + время засыпания; подъём = первое использование утром.
 * Время засыпания и сдвиг подъёма подстраиваются под человека по его исправлениям.
 */
object SleepDetect {
    enum class Kind { SCREEN_ON, SCREEN_OFF, UNLOCK, APP }

    data class Event(val at: Long, val kind: Kind)

    /** Сеанс: экран включён с from по to; used — телефон реально использовали. */
    data class Session(val from: Long, val to: Long, val used: Boolean)

    data class Guess(
        val lastUseAt: Long,
        val firstUseAt: Long,
        val sleepAt: Long,
        val wakeAt: Long,
        val awakenings: Int,
        val glances: Int,
        val confidence: Int,
    ) {
        val minutes: Int get() = ((wakeAt - sleepAt) / 60_000).toInt()
    }

    data class Calibration(val latencyMin: Int = 10, val wakeShiftMin: Int = 0)

    private const val MIN = 60_000L

    fun sessions(events: List<Event>, maxOpenMs: Long = 30 * MIN): List<Session> {
        val out = mutableListOf<Session>()
        var start: Long? = null
        var used = false
        var lastApp: Long? = null
        for (e in events.sortedBy { it.at }) {
            when (e.kind) {
                Kind.SCREEN_ON -> { if (start == null) { start = e.at; used = false } }
                Kind.UNLOCK -> { if (start == null) start = e.at; used = true }
                Kind.APP -> {
                    if (start == null) {
                        // Нет событий экрана (старый Android): считаем минуту использования вокруг события приложения.
                        if (lastApp == null || e.at - lastApp > 2 * MIN) out += Session(e.at, e.at + MIN, true)
                        lastApp = e.at
                    } else used = true
                }
                Kind.SCREEN_OFF -> {
                    val s = start
                    if (s != null) out += Session(s, e.at, used)
                    start = null; used = false
                }
            }
        }
        start?.let { s -> out += Session(s, s + maxOpenMs, used) }
        return out.sortedBy { it.from }
    }

    /** Реальное использование: разблокирован и включён хотя бы минуту, либо экран горел дольше 3 минут. */
    private fun isUse(s: Session) = (s.used && s.to - s.from >= MIN) || s.to - s.from >= 3 * MIN

    /**
     * Ночь, которая заканчивается утром дня [morningStart] (полночь этого дня в мс).
     * Окно: с 19:00 предыдущего дня до 13:00 этого дня.
     */
    fun detect(events: List<Event>, morningStart: Long, cal: Calibration = Calibration()): Guess? {
        val winFrom = morningStart - 5 * 60 * MIN
        val winTo = morningStart + 13 * 60 * MIN
        val all = sessions(events).filter { it.to > winFrom - 6 * 60 * MIN && it.from < winTo + 60 * MIN }
        val uses = all.filter(::isUse)
        if (uses.isEmpty()) return null
        // Паузы между использованиями, обрезанные окном.
        data class Gap(val from: Long, val to: Long) { val len get() = to - from }
        val gaps = mutableListOf<Gap>()
        for (i in 0 until uses.size - 1) {
            val a = maxOf(uses[i].to, winFrom)
            val b = minOf(uses[i + 1].from, winTo)
            if (b > a) gaps += Gap(a, b)
        }
        if (gaps.isEmpty()) return null
        val best = gaps.indices.maxBy { gaps[it].len }
        var from = gaps[best].from
        var to = gaps[best].to
        if (to - from < 3 * 60 * MIN) return null
        var awakenings = 0
        // Склеиваем соседние паузы через короткие ночные проверки телефона.
        var l = best - 1
        while (l >= 0 && gaps[l].len >= 45 * MIN && from - gaps[l].to <= 10 * MIN && gaps[l].from >= morningStart - 4 * 60 * MIN) {
            from = gaps[l].from; awakenings++; l--
        }
        var r = best + 1
        while (r < gaps.size && gaps[r].len >= 45 * MIN && gaps[r].from - to <= 10 * MIN && to <= morningStart + 7 * 60 * MIN) {
            to = gaps[r].to; awakenings++; r++
        }
        val glances = all.count { !isUse(it) && it.from in from..to }
        val len = to - from
        val startHour = ((from - morningStart) / (60 * MIN)).toInt() // от −5 (19:00) до 13
        val confidence = when {
            len >= 5 * 60 * MIN && startHour in -3..3 -> 2
            len >= 4 * 60 * MIN -> 1
            else -> 0
        }
        val sleepAt = from + cal.latencyMin * MIN
        val wakeAt = to + cal.wakeShiftMin * MIN
        return Guess(from, to, sleepAt, maxOf(wakeAt, sleepAt + 60 * MIN), awakenings, glances, confidence)
    }

    /**
     * Подстройка под человека по его исправлениям, в минутах от полуночи:
     * пары (последнее использование, когда на самом деле лёг) и (первое использование, когда встал).
     * Берётся медиана; пока исправлений меньше трёх — значения по умолчанию.
     */
    fun calibrate(bedPairs: List<Pair<Int, Int>>, wakePairs: List<Pair<Int, Int>>, base: Calibration = Calibration()): Calibration {
        fun diff(a: Int, b: Int): Int { var d = b - a; if (d > 720) d -= 1440; if (d < -720) d += 1440; return d }
        fun median(xs: List<Int>) = xs.sorted().let { it[it.size / 2] }
        val bed = bedPairs.map { (det, user) -> diff(det, user) }.filter { abs(it) <= 180 }
        val wake = wakePairs.map { (det, user) -> diff(det, user) }.filter { abs(it) <= 180 }
        return Calibration(
            latencyMin = if (bed.size >= 3) median(bed).coerceIn(0, 90) else base.latencyMin,
            wakeShiftMin = if (wake.size >= 3) median(wake).coerceIn(-90, 30) else base.wakeShiftMin,
        )
    }
}
