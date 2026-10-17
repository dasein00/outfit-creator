package com.dasein.poryadok.logic

import java.time.LocalDate

/** Отпуск: первый и последний день включительно (номер дня от 1970-01-01). */
data class Vacation(val start: Long, val end: Long, val title: String = "")

/**
 * Отпуска в календаре. Хранятся строкой «начало|конец|название» по строке на отпуск.
 * Длина считается по ст. 120 ТК РФ: нерабочие праздничные дни внутри отпуска в его дни не входят.
 */
object VacationMath {
    fun parse(text: String): List<Vacation> = text.lineSequence().mapNotNull { line ->
        val p = line.split('|', limit = 3)
        val s = p.getOrNull(0)?.trim()?.toLongOrNull() ?: return@mapNotNull null
        val e = p.getOrNull(1)?.trim()?.toLongOrNull() ?: return@mapNotNull null
        Vacation(minOf(s, e), maxOf(s, e), p.getOrNull(2)?.replace('\n', ' ')?.trim().orEmpty())
    }.sortedBy { it.start }.toList()

    fun format(list: List<Vacation>): String =
        list.sortedBy { it.start }.joinToString("\n") { "${it.start}|${it.end}|${it.title.replace('|', '/').replace('\n', ' ')}" }

    fun on(list: List<Vacation>, day: Long): Vacation? = list.firstOrNull { day in it.start..it.end }

    /** Календарные дни отпуска без нерабочих праздничных (их отпуск «перешагивает»). */
    fun days(v: Vacation): Int = (v.start..v.end).count { !ProdCalendar.official(LocalDate.ofEpochDay(it)) }

    /** Праздничные дни, попавшие в отпуск (продлевают его). */
    fun holidays(v: Vacation): Int = (v.end - v.start + 1).toInt() - days(v)

    /** Рабочие дни, которые закрывает отпуск (по производственному календарю). */
    fun workDays(v: Vacation): Int = (v.start..v.end).count { !ProdCalendar.isOff(LocalDate.ofEpochDay(it)) }

    /** Дней отпуска (по ст. 120) в указанном году — для подсчёта, сколько осталось из 28. */
    fun daysInYear(list: List<Vacation>, year: Int): Int {
        val from = LocalDate.of(year, 1, 1).toEpochDay()
        val to = LocalDate.of(year, 12, 31).toEpochDay()
        return list.sumOf { v ->
            val s = maxOf(v.start, from)
            val e = minOf(v.end, to)
            if (s > e) 0 else days(Vacation(s, e))
        }
    }

    /** Дней отпуска в месяце (календарных, вместе с выходными). */
    fun daysInMonth(list: List<Vacation>, year: Int, month: Int): Int {
        val first = LocalDate.of(year, month, 1)
        val from = first.toEpochDay()
        val to = first.plusDays(first.lengthOfMonth() - 1L).toEpochDay()
        return (from..to).count { d -> on(list, d) != null }
    }
}
