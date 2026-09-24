package com.dasein.poryadok.logic

import java.time.LocalDate

/** Расписание привычки: битовая маска дней (бит 0 = пн … бит 6 = вс) или N раз в неделю. */
data class HabitSchedule(val daysMask: Int = ALL, val timesPerWeek: Int = 0) {
    fun isScheduled(day: Long): Boolean {
        if (timesPerWeek > 0) return true
        val dow = LocalDate.ofEpochDay(day).dayOfWeek.value - 1
        return daysMask and (1 shl dow) != 0
    }

    fun describe(): String = when {
        timesPerWeek > 0 -> "$timesPerWeek ${plural(timesPerWeek, "раз", "раза", "раз")} в неделю"
        daysMask == ALL -> "Каждый день"
        daysMask == WEEKDAYS -> "По будням"
        daysMask == WEEKENDS -> "По выходным"
        else -> DAY_NAMES.filterIndexed { i, _ -> daysMask and (1 shl i) != 0 }.joinToString(", ")
    }

    companion object {
        const val ALL = 0b1111111
        const val WEEKDAYS = 0b0011111
        const val WEEKENDS = 0b1100000
        val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    }
}

data class HabitSummary(
    val streak: Int,
    val best: Int,
    val streakUnit: String,
    val rate30: Int,
    val totalDone: Int,
)

object HabitStats {
    /**
     * [doneDays] — дни, когда цель привычки выполнена.
     * Сегодняшний невыполненный день серию не рвёт: ещё есть время.
     */
    fun summary(schedule: HabitSchedule, doneDays: Set<Long>, today: Long, createdDay: Long): HabitSummary {
        return if (schedule.timesPerWeek > 0) weekly(schedule.timesPerWeek, doneDays, today, createdDay)
        else daily(schedule, doneDays, today, createdDay)
    }

    private fun daily(s: HabitSchedule, done: Set<Long>, today: Long, created: Long): HabitSummary {
        val start = minOf(created, done.minOrNull() ?: created)
        var streak = 0
        var d = today
        if (s.isScheduled(d) && d !in done) d--
        while (d >= start) {
            if (s.isScheduled(d)) {
                if (d in done) streak++ else break
            }
            d--
        }
        var best = 0
        var run = 0
        for (x in start..today) {
            if (!s.isScheduled(x)) continue
            if (x in done) {
                run++; best = maxOf(best, run)
            } else if (x != today) run = 0
        }
        best = maxOf(best, streak)
        var sched = 0
        var hit = 0
        for (x in maxOf(start, today - 29)..today) {
            if (!s.isScheduled(x)) continue
            if (x == today && x !in done) continue
            sched++
            if (x in done) hit++
        }
        val rate = if (sched == 0) 0 else hit * 100 / sched
        return HabitSummary(streak, best, "дн.", rate, done.count { it <= today })
    }

    private fun weekly(times: Int, done: Set<Long>, today: Long, created: Long): HabitSummary {
        val thisWeek = Dates.weekStart(today)
        fun countIn(ws: Long) = done.count { it in ws..(ws + 6) }
        val start = Dates.weekStart(minOf(created, done.minOrNull() ?: created))
        var streak = 0
        var w = thisWeek
        if (countIn(w) < times) w -= 7
        while (w >= start && countIn(w) >= times) {
            streak++; w -= 7
        }
        var best = 0
        var run = 0
        var ws = start
        while (ws <= thisWeek) {
            if (countIn(ws) >= times) {
                run++; best = maxOf(best, run)
            } else if (ws != thisWeek) run = 0
            ws += 7
        }
        best = maxOf(best, streak)
        var hitWeeks = 0
        var weeks = 0
        var x = thisWeek - 7 * 3
        while (x <= thisWeek) {
            if (x >= start) {
                weeks++
                if (countIn(x) >= times) hitWeeks++
            }
            x += 7
        }
        val rate = if (weeks == 0) 0 else hitWeeks * 100 / weeks
        return HabitSummary(streak, best, "нед.", rate, done.count { it <= today })
    }
}
