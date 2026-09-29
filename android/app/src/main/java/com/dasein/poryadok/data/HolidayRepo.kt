package com.dasein.poryadok.data

import android.content.Context
import com.dasein.poryadok.logic.Holiday
import com.dasein.poryadok.logic.HolidayCats
import com.dasein.poryadok.logic.HolidayRules
import java.time.LocalDate

/** Строка списка праздников дня: встроенный праздник или свой день. */
data class DayEntry(
    val key: String,
    val name: String,
    val cat: String,
    val color: Int,
    val off: Boolean,
    val holiday: Holiday? = null,
    val custom: CustomDay? = null,
    val subtitle: String = "",
)

object HolidayRepo {
    @Volatile private var cache: List<Holiday>? = null

    fun base(ctx: Context): List<Holiday> = cache ?: runCatching {
        HolidayRules.parse(ctx.assets.open("holidays/holidays.json").bufferedReader().use { it.readText() })
    }.getOrDefault(emptyList()).also { cache = it }

    fun byId(ctx: Context, id: String) = base(ctx).firstOrNull { it.id == id }

    fun occurs(c: CustomDay, d: LocalDate): Boolean {
        if (!c.yearly) return c.year == d.year && c.month == d.monthValue && c.day == d.dayOfMonth
        if (c.month == 2 && c.day == 29 && !d.isLeapYear) return d.monthValue == 2 && d.dayOfMonth == 28
        return c.month == d.monthValue && c.day == d.dayOfMonth
    }

    private fun years(n: Int): String {
        val m10 = n % 10; val m100 = n % 100
        return "$n " + when { m10 == 1 && m100 != 11 -> "год"; m10 in 2..4 && m100 !in 12..14 -> "года"; else -> "лет" }
    }

    fun customSubtitle(c: CustomDay, d: LocalDate): String {
        val n = c.year?.let { d.year - it }?.takeIf { it > 0 && c.yearly }
        return when {
            n != null && c.kind == DayKindTag.BIRTHDAY -> "исполняется ${years(n)}"
            n != null -> "${years(n)} с ${c.year} года"
            else -> DayKindTag.names[c.kind] ?: ""
        }
    }

    fun entries(ctx: Context, d: LocalDate, custom: List<CustomDay>, marks: List<HolidayMark>, hidden: Set<String> = emptySet()): List<DayEntry> {
        val markMap = marks.associate { it.holidayId to it.color }
        val mine = custom.filter { occurs(it, d) }.map { c ->
            DayEntry("c${c.id}", c.name, "my", c.color, false, custom = c, subtitle = customSubtitle(c, d))
        }
        val built = HolidayRules.on(base(ctx), d).filter { it.cat !in hidden }.map { h ->
            DayEntry(
                h.id, h.name, h.cat, markMap[h.id] ?: (HolidayCats.colors[h.cat] ?: 0xFF888888L).toInt(), h.off, holiday = h,
                subtitle = HolidayCats.names[h.cat] ?: "",
            )
        }.sortedWith(compareBy({ !it.off }, { HolidayCats.ORDER.indexOf(it.cat) }))
        return mine + built
    }

    /** Цвет, которым выделить день в календаре: свой день или отмеченный цветом праздник важнее обычных. */
    fun highlight(ctx: Context, d: LocalDate, custom: List<CustomDay>, marks: List<HolidayMark>): Int? {
        custom.firstOrNull { occurs(it, d) }?.let { return it.color }
        val marked = marks.associate { it.holidayId to it.color }
        return HolidayRules.on(base(ctx), d).firstNotNullOfOrNull { marked[it.id] }
    }
}
