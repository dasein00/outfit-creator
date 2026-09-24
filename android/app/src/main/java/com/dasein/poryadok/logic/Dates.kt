package com.dasein.poryadok.logic

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val RU: Locale = Locale("ru", "RU")

object Dates {
    fun today(): Long = LocalDate.now().toEpochDay()
    fun day(d: Long): LocalDate = LocalDate.ofEpochDay(d)
    fun nowMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    private val dm = DateTimeFormatter.ofPattern("d MMMM", RU)
    private val dmy = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)
    private val dmShort = DateTimeFormatter.ofPattern("d MMM", RU)

    fun label(d: Long, today: Long = today()): String = when (d - today) {
        0L -> "Сегодня"
        1L -> "Завтра"
        2L -> "Послезавтра"
        -1L -> "Вчера"
        else -> {
            val ld = day(d)
            if (ld.year == day(today).year) ld.format(dm) else ld.format(dmy)
        }
    }

    fun short(d: Long): String = day(d).format(dmShort).replace(".", "")
    fun full(d: Long): String = day(d).format(dmy)
    fun weekdayShort(d: Long): String = day(d).dayOfWeek.getDisplayName(TextStyle.SHORT, RU).replaceFirstChar { it.uppercase() }
    fun weekdayFull(d: Long): String = day(d).dayOfWeek.getDisplayName(TextStyle.FULL, RU).replaceFirstChar { it.uppercase() }
    fun monthTitle(ym: YearMonth): String =
        ym.month.getDisplayName(TextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase() } + " " + ym.year

    fun time(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    fun millis(day: Long, minutes: Int): Long =
        LocalDateTime.of(day(day), LocalTime.of(minutes / 60, minutes % 60))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun dayOf(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    fun minutesOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().let { it.hour * 60 + it.minute }

    fun monthRange(ym: YearMonth): LongRange = ym.atDay(1).toEpochDay()..ym.atEndOfMonth().toEpochDay()
    fun weekStart(d: Long): Long = day(d).with(DayOfWeek.MONDAY).toEpochDay()

    fun greeting(minutes: Int = nowMinutes()): String = when (minutes / 60) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
}

fun plural(n: Long, one: String, few: String, many: String): String {
    val a = kotlin.math.abs(n)
    val n10 = a % 10
    val n100 = a % 100
    return when {
        n10 == 1L && n100 != 11L -> one
        n10 in 2..4 && (n100 < 10 || n100 >= 20) -> few
        else -> many
    }
}

fun plural(n: Int, one: String, few: String, many: String) = plural(n.toLong(), one, few, many)
