package com.dasein.poryadok.logic

import java.time.DayOfWeek
import java.time.LocalDate

enum class Repeat(val code: String, val label: String) {
    NONE("none", "Не повторять"),
    DAILY("daily", "Каждый день"),
    WEEKDAYS("weekdays", "По будням"),
    WEEKLY("weekly", "Каждую неделю"),
    MONTHLY("monthly", "Каждый месяц"),
    YEARLY("yearly", "Каждый год");

    companion object {
        fun of(code: String?): Repeat = entries.firstOrNull { it.code == code } ?: NONE
    }
}

private fun isWeekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

/** Следующая дата после [day] для повторяющегося элемента, привязанного к [anchor] (исходной дате). */
fun nextOccurrence(day: Long, repeat: Repeat, anchor: Long = day): Long? {
    val d = LocalDate.ofEpochDay(day)
    return when (repeat) {
        Repeat.NONE -> null
        Repeat.DAILY -> day + 1
        Repeat.WEEKDAYS -> {
            var n = d.plusDays(1)
            while (isWeekend(n)) n = n.plusDays(1)
            n.toEpochDay()
        }
        Repeat.WEEKLY -> day + 7
        Repeat.MONTHLY -> {
            val a = LocalDate.ofEpochDay(anchor)
            val next = d.plusMonths(1)
            next.withDayOfMonth(minOf(a.dayOfMonth, next.lengthOfMonth())).toEpochDay()
        }
        Repeat.YEARLY -> d.plusYears(1).toEpochDay()
    }
}

/** Попадает ли повторяющийся элемент, начатый в [start], на день [day]. */
fun occursOn(start: Long, repeat: Repeat, day: Long): Boolean {
    if (day < start) return false
    val s = LocalDate.ofEpochDay(start)
    val d = LocalDate.ofEpochDay(day)
    return when (repeat) {
        Repeat.NONE -> day == start
        Repeat.DAILY -> true
        Repeat.WEEKDAYS -> !isWeekend(d)
        Repeat.WEEKLY -> (day - start) % 7 == 0L
        Repeat.MONTHLY -> {
            val dom = minOf(s.dayOfMonth, d.lengthOfMonth())
            d.dayOfMonth == dom
        }
        Repeat.YEARLY -> d.month == s.month && d.dayOfMonth == minOf(s.dayOfMonth, d.lengthOfMonth())
    }
}

/** Ближайшее вхождение не раньше [from]. */
fun upcomingOccurrence(start: Long, repeat: Repeat, from: Long): Long? {
    if (start >= from) return start
    if (repeat == Repeat.NONE) return null
    var d = from
    repeat(400) {
        if (occursOn(start, repeat, d)) return d
        d++
    }
    return null
}
