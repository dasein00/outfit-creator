package com.dasein.poryadok.logic

import java.time.DayOfWeek
import java.time.LocalDate

data class ParsedTask(
    val title: String,
    val dueDay: Long? = null,
    val dueMin: Int? = null,
    val priority: Int = 0,
    val project: String? = null,
    val tags: List<String> = emptyList(),
    val repeat: Repeat = Repeat.NONE,
)

/**
 * Разбор быстрого ввода задачи на русском, в духе Todoist:
 * «Позвонить маме завтра в 19:30 !2 #семья каждую неделю».
 */
object QuickAdd {
    private val months = listOf(
        "январ", "феврал", "март", "апрел", "ма[йя]", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр"
    )
    private val weekdays = listOf(
        DayOfWeek.MONDAY to "понедельник|пн",
        DayOfWeek.TUESDAY to "вторник|вт",
        DayOfWeek.WEDNESDAY to "сред[ау]|ср",
        DayOfWeek.THURSDAY to "четверг|чт",
        DayOfWeek.FRIDAY to "пятниц[ау]|пт",
        DayOfWeek.SATURDAY to "суббот[ау]|сб",
        DayOfWeek.SUNDAY to "воскресенье|вс",
    )

    private fun rx(p: String) = Regex(p, setOf(RegexOption.IGNORE_CASE))

    fun parse(input: String, today: Long): ParsedTask {
        var text = " " + input.trim() + " "
        var day: Long? = null
        var min: Int? = null
        var priority = 0
        var project: String? = null
        val tags = mutableListOf<String>()
        var repeat = Repeat.NONE
        val t = LocalDate.ofEpochDay(today)

        fun cut(r: Regex, onMatch: (MatchResult) -> Unit): Boolean {
            val m = r.find(text) ?: return false
            onMatch(m)
            text = text.removeRange(m.range).let { " " + it.trim() + " " }
            return true
        }

        cut(rx("""\s!(!{0,2}|[1-3])(?=\s)""")) { m ->
            val v = m.groupValues[1]
            priority = when {
                v.isEmpty() -> 1
                v.all { it == '!' } -> v.length + 1
                else -> v.toInt()
            }
        }
        cut(rx("""\s#([\p{L}\d_\-]+)(?=\s)""")) { project = it.groupValues[1] }
        while (cut(rx("""\s@([\p{L}\d_\-]+)(?=\s)""")) { tags += it.groupValues[1] }) Unit

        val repeats = listOf(
            Repeat.DAILY to """каждый\s+день|ежедневно""",
            Repeat.WEEKDAYS to """по\s+будням""",
            Repeat.WEEKLY to """каждую\s+неделю|еженедельно""",
            Repeat.MONTHLY to """каждый\s+месяц|ежемесячно""",
            Repeat.YEARLY to """каждый\s+год|ежегодно""",
        )
        for ((r, p) in repeats) if (cut(rx("""\s(?:$p)(?=\s)""")) { repeat = r }) break

        // время: «в 18:30», «18:30», «в 9 утра», «в 7 вечера», «в 18»
        cut(rx("""\s(?:в\s+)?(\d{1,2})[:.](\d{2})(?=\s)""")) { m ->
            val h = m.groupValues[1].toInt()
            val mm = m.groupValues[2].toInt()
            if (h in 0..23 && mm in 0..59) min = h * 60 + mm
        } || cut(rx("""\sв\s+(\d{1,2})(?:\s*(?:ч|час[аов]*))?(?:\s+(утра|дня|вечера|ночи))?(?=\s)""")) { m ->
            var h = m.groupValues[1].toInt()
            when (m.groupValues[2].lowercase()) {
                "дня", "вечера" -> if (h < 12) h += 12
                "ночи" -> if (h == 12) h = 0
            }
            if (h in 0..23) min = h * 60
        }

        // дата
        val dateRules: List<Pair<Regex, (MatchResult) -> Long?>> = listOf(
            rx("""\sпослезавтра(?=\s)""") to { _ -> today + 2 },
            rx("""\sзавтра(?=\s)""") to { _ -> today + 1 },
            rx("""\sсегодня(?=\s)""") to { _ -> today },
            rx("""\sчерез\s+(\d+)\s+(дн[яей]|день)(?=\s)""") to { m -> today + m.groupValues[1].toLong() },
            rx("""\sчерез\s+(\d+)\s+(недел[июь]|неделю)(?=\s)""") to { m -> today + 7 * m.groupValues[1].toLong() },
            rx("""\sчерез\s+недел[юь](?=\s)""") to { _ -> today + 7 },
            rx("""\sчерез\s+месяц(?=\s)""") to { _ -> t.plusMonths(1).toEpochDay() },
            rx("""\s(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?(?=\s)""") to { m ->
                val d = m.groupValues[1].toInt()
                val mo = m.groupValues[2].toInt()
                val yRaw = m.groupValues[3]
                val y = when {
                    yRaw.isEmpty() -> t.year
                    yRaw.length == 2 -> 2000 + yRaw.toInt()
                    else -> yRaw.toInt()
                }
                runCatching {
                    var ld = LocalDate.of(y, mo, d)
                    if (yRaw.isEmpty() && ld.isBefore(t)) ld = ld.plusYears(1)
                    ld.toEpochDay()
                }.getOrNull()
            },
            rx("""\s(\d{1,2})\s+(${months.joinToString("|")})[\p{L}]*(?=\s)""") to { m ->
                val d = m.groupValues[1].toInt()
                val mIdx = months.indexOfFirst { Regex("^$it", RegexOption.IGNORE_CASE).containsMatchIn(m.groupValues[2]) }
                runCatching {
                    var ld = LocalDate.of(t.year, mIdx + 1, d)
                    if (ld.isBefore(t)) ld = ld.plusYears(1)
                    ld.toEpochDay()
                }.getOrNull()
            },
        )
        for ((r, f) in dateRules) {
            var found = false
            cut(r) { m -> day = f(m); found = true }
            if (found) break
        }
        if (day == null) {
            for ((dow, names) in weekdays) {
                if (cut(rx("""\s(?:(?:в|во)\s+)?(?:$names)(?=\s)""")) { _ ->
                        var d = t.plusDays(1)
                        while (d.dayOfWeek != dow) d = d.plusDays(1)
                        day = d.toEpochDay()
                    }) break
            }
        }
        if (min != null && day == null) day = today
        if (repeat != Repeat.NONE && day == null) day = today

        val title = text.trim().replace(Regex("\\s+"), " ").trimEnd(',', '.', '-')
        return ParsedTask(title.ifEmpty { input.trim() }, day, min, priority.coerceIn(0, 3), project, tags, repeat)
    }
}
