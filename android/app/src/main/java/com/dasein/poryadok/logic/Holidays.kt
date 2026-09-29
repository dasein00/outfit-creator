package com.dasein.poryadok.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Праздник из встроенной базы. rule — правило даты (см. tools/holidays/generate.py), off — нерабочий день в своей стране. */
@Serializable
data class Holiday(val id: String, val name: String, val cat: String, val rule: String, val off: Boolean = false, val about: String = "")

@Serializable
data class HolidayBase(val version: Int = 1, val holidays: List<Holiday> = emptyList())

object HolidayCats {
    val ORDER = listOf("ru", "prof", "intl", "am", "orth", "arm", "folk", "my")
    val names = mapOf(
        "ru" to "Россия", "prof" to "Профессиональные", "intl" to "Международные", "am" to "Армения",
        "orth" to "Православные", "arm" to "Армянская церковь", "folk" to "Народные", "my" to "Мои",
    )
    /** Цвет категории (ARGB). */
    val colors = mapOf(
        "ru" to 0xFFD25B4BL, "prof" to 0xFF5B8DD2L, "intl" to 0xFF4BA38CL, "am" to 0xFFE08A3CL,
        "orth" to 0xFFC7A46AL, "arm" to 0xFFB06FC4L, "folk" to 0xFF8CC46EL, "my" to 0xFFE06A9AL,
    )
}

object Easter {
    /** Православная Пасха: юлианская пасхалия (алгоритм Гаусса/Меёса), дата по новому стилю. Верно для 1900–2099. */
    fun orthodox(year: Int): LocalDate {
        val a = year % 4
        val b = year % 7
        val c = year % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val month = (d + e + 114) / 31
        val day = (d + e + 114) % 31 + 1
        return LocalDate.of(year, month, day).plusDays(13)
    }

    /** Пасха по григорианской пасхалии (анонимный григорианский алгоритм) — её использует Армянская апостольская церковь. */
    fun western(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}

object HolidayRules {
    private fun md(s: String): Pair<Int, Int> = s.split('-').let { it[0].toInt() to it[1].toInt() }

    /** Дата праздника в заданном году или null, если правило не распознано. */
    fun dateIn(rule: String, year: Int): LocalDate? = runCatching {
        val parts = rule.split(':')
        when (parts[0]) {
            "fixed" -> md(parts[1]).let { (m, d) -> if (m == 2 && d == 29 && !java.time.Year.isLeap(year.toLong())) null else LocalDate.of(year, m, d) }
            "eo" -> Easter.orthodox(year).plusDays(parts[1].toLong())
            "ew" -> Easter.western(year).plusDays(parts[1].toLong())
            "nth" -> {
                val month = parts[1].toInt()
                val dow = DayOfWeek.of(parts[2].toInt())
                val n = parts[3].toInt()
                val first = LocalDate.of(year, month, 1)
                if (n > 0) first.with(TemporalAdjusters.dayOfWeekInMonth(n, dow)).takeIf { it.monthValue == month }
                else first.with(TemporalAdjusters.lastInMonth(dow))
            }
            "near" -> {
                val (m, d) = md(parts[1])
                val base = LocalDate.of(year, m, d)
                val dow = DayOfWeek.of(parts[2].toInt())
                (-3..3).map { base.plusDays(it.toLong()) }.first { it.dayOfWeek == dow }
            }
            "before" -> {
                val (m, d) = md(parts[1])
                LocalDate.of(year, m, d).with(TemporalAdjusters.previous(DayOfWeek.of(parts[2].toInt())))
            }
            "doy" -> LocalDate.ofYearDay(year, parts[1].toInt())
            else -> null
        }
    }.getOrNull()

    fun on(list: List<Holiday>, date: LocalDate): List<Holiday> = list.filter { dateIn(it.rule, date.year) == date }

    /** Человекочитаемое правило даты. */
    fun describe(rule: String): String {
        val months = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
        val dows = listOf("понедельник", "вторник", "среду", "четверг", "пятницу", "субботу", "воскресенье")
        val p = rule.split(':')
        fun mdText(s: String) = md(s).let { (m, d) -> "$d ${months[m - 1]}" }
        fun shift(n: Int, what: String) = when {
            n == 0 -> "в день $what"
            n > 0 -> "через $n дн. после $what"
            else -> "за ${-n} дн. до $what"
        }
        return when (p.getOrNull(0)) {
            "fixed" -> "Каждый год ${mdText(p[1])}"
            "eo" -> shift(p[1].toInt(), "православной Пасхи").replaceFirstChar { it.uppercase() }
            "ew" -> shift(p[1].toInt(), "Пасхи Армянской церкви").replaceFirstChar { it.uppercase() }
            "nth" -> {
                val wd = p[2].toInt()
                val n = p[3].toInt()
                val ord = when (wd) {
                    3, 5, 6 -> mapOf(1 to "первую", 2 to "вторую", 3 to "третью", 4 to "четвёртую", -1 to "последнюю")
                    7 -> mapOf(1 to "первое", 2 to "второе", 3 to "третье", 4 to "четвёртое", -1 to "последнее")
                    else -> mapOf(1 to "первый", 2 to "второй", 3 to "третий", 4 to "четвёртый", -1 to "последний")
                }
                val monthsNom = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
                "Каждый год в ${ord[n] ?: ""} ${dows[wd - 1]} ${monthsNom[p[1].toInt() - 1]}"
            }
            "near" -> "Воскресенье, ближайшее к ${mdText(p[1])}"
            "before" -> "Суббота перед ${mdText(p[1])}"
            "doy" -> "${p[1]}-й день года"
            else -> ""
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): List<Holiday> = json.decodeFromString(HolidayBase.serializer(), text).holidays
}

enum class DayKind { WORK, SHORT, WEEKEND, HOLIDAY }

/**
 * Производственный календарь России (пятидневка). Праздники — ст. 112 ТК РФ, сокращённые дни — ст. 95 ТК РФ.
 * Переносы на 2025–2027 годы — по постановлениям Правительства РФ (2025 — № 1335 от 04.10.2024,
 * 2026 — № 1466 от 24.09.2025, 2027 — № 1187 от 17.09.2026). Для остальных лет — по общим правилам ТК.
 */
object ProdCalendar {
    private val OFFICIAL = setOf(
        1 to 1, 1 to 2, 1 to 3, 1 to 4, 1 to 5, 1 to 6, 1 to 7, 1 to 8, 2 to 23, 3 to 8, 5 to 1, 5 to 9, 6 to 12, 11 to 4,
    )

    private data class YearData(val extraOff: Set<Pair<Int, Int>>, val workWeekends: Set<Pair<Int, Int>>, val short: Set<Pair<Int, Int>>)

    private val DATA = mapOf(
        2025 to YearData(
            extraOff = setOf(5 to 2, 5 to 8, 6 to 13, 11 to 3, 12 to 31),
            workWeekends = setOf(11 to 1),
            short = setOf(3 to 7, 4 to 30, 6 to 11, 11 to 1),
        ),
        2026 to YearData(
            extraOff = setOf(1 to 9, 3 to 9, 5 to 11, 12 to 31),
            workWeekends = emptySet(),
            short = setOf(4 to 30, 5 to 8, 6 to 11, 11 to 3),
        ),
        2027 to YearData(
            extraOff = setOf(2 to 22, 5 to 3, 5 to 10, 6 to 14, 11 to 5, 12 to 31),
            workWeekends = setOf(2 to 20),
            short = setOf(2 to 20, 4 to 30, 6 to 11, 11 to 3),
        ),
    )

    fun official(d: LocalDate) = (d.monthValue to d.dayOfMonth) in OFFICIAL
    fun exact(year: Int) = year in DATA
    private fun weekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    fun kind(d: LocalDate): DayKind {
        val key = d.monthValue to d.dayOfMonth
        if (official(d)) return DayKind.HOLIDAY
        val y = DATA[d.year]
        if (y != null) {
            if (key in y.extraOff) return DayKind.WEEKEND
            if (key in y.workWeekends) return if (key in y.short) DayKind.SHORT else DayKind.WORK
            if (weekend(d)) return DayKind.WEEKEND
            return if (key in y.short) DayKind.SHORT else DayKind.WORK
        }
        // Общие правила ТК: праздник в выходной (кроме январских) переносится на следующий рабочий день.
        if (weekend(d)) return DayKind.WEEKEND
        if (movedHoliday(d)) return DayKind.WEEKEND
        val next = d.plusDays(1)
        return if (official(next) || movedHoliday(next)) DayKind.SHORT else DayKind.WORK
    }

    private fun movedHoliday(d: LocalDate): Boolean {
        if (weekend(d) || official(d)) return false
        // Идём назад по подряд идущим выходным/праздникам: был ли среди них праздник, выпавший на выходной?
        var p = d.minusDays(1)
        var owed = 0
        while (weekend(p) || official(p)) {
            if (official(p) && weekend(p) && p.monthValue != 1) owed++
            p = p.minusDays(1)
        }
        return owed > 0
    }

    fun isOff(d: LocalDate) = kind(d).let { it == DayKind.WEEKEND || it == DayKind.HOLIDAY }

    data class MonthStats(val workDays: Int, val offDays: Int, val shortDays: Int, val hours40: Int)

    fun month(year: Int, month: Int): MonthStats {
        val first = LocalDate.of(year, month, 1)
        val days = (0 until first.lengthOfMonth()).map { first.plusDays(it.toLong()) }
        val kinds = days.map { kind(it) }
        val work = kinds.count { it == DayKind.WORK || it == DayKind.SHORT }
        val short = kinds.count { it == DayKind.SHORT }
        return MonthStats(work, days.size - work, short, work * 8 - short)
    }
}
