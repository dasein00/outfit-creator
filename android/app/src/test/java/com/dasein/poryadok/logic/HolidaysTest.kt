package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class HolidaysTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun easterDates() {
        assertEquals(d(2025, 4, 20), Easter.orthodox(2025))
        assertEquals(d(2026, 4, 12), Easter.orthodox(2026))
        assertEquals(d(2027, 5, 2), Easter.orthodox(2027))
        assertEquals(d(2025, 4, 20), Easter.western(2025))
        assertEquals(d(2026, 4, 5), Easter.western(2026))
        assertEquals(d(2027, 3, 28), Easter.western(2027))
    }

    @Test fun armenianMovableFeasts2026() {
        assertEquals(d(2026, 7, 12), HolidayRules.dateIn("ew:98", 2026))   // Вардавар
        assertEquals(d(2026, 2, 12), HolidayRules.dateIn("ew:-52", 2026))  // Вардананц
        assertEquals(d(2026, 1, 31), HolidayRules.dateIn("ew:-64", 2026))  // Сурб Саркис
        assertEquals(d(2026, 8, 16), HolidayRules.dateIn("near:08-15:7", 2026))
        assertEquals(d(2026, 9, 13), HolidayRules.dateIn("near:09-14:7", 2026))
    }

    @Test fun weekdayRules() {
        assertEquals(d(2026, 11, 29), HolidayRules.dateIn("nth:11:7:-1", 2026)) // День матери
        assertEquals(d(2026, 6, 21), HolidayRules.dateIn("nth:06:7:3", 2026))   // День медика
        assertEquals(d(2026, 9, 13), HolidayRules.dateIn("doy:256", 2026))
        assertEquals(d(2024, 9, 12), HolidayRules.dateIn("doy:256", 2024))
        assertEquals(d(2026, 11, 7), HolidayRules.dateIn("before:11-08:6", 2026))
        assertEquals(d(2026, 2, 22), HolidayRules.dateIn("eo:-49", 2026))       // Прощёное воскресенье
    }

    @Test fun bundledBaseParses() {
        val f = listOf("src/main/assets/holidays/holidays.json", "app/src/main/assets/holidays/holidays.json",
            "/home/user/outfit-creator/android/app/src/main/assets/holidays/holidays.json").map { File(it) }.firstOrNull { it.exists() } ?: return
        val list = HolidayRules.parse(f.readText())
        assertTrue(list.size > 150)
        list.forEach { h -> assertTrue(h.id, HolidayRules.dateIn(h.rule, 2026) != null && h.about.isNotBlank() && h.how.isNotBlank() && h.facts.isNotBlank()) }
        val jan7 = HolidayRules.on(list, d(2026, 1, 7)).map { it.id }
        assertTrue("or_christmas" in jan7)
    }

    @Test fun productionCalendar() {
        val p = ProdCalendar
        assertEquals(DayKind.WEEKEND, p.kind(d(2026, 1, 9)))   // перенос с 3 января
        assertEquals(DayKind.HOLIDAY, p.kind(d(2026, 1, 7)))
        assertEquals(DayKind.WEEKEND, p.kind(d(2026, 3, 9)))   // 8 марта в воскресенье
        assertEquals(DayKind.SHORT, p.kind(d(2026, 4, 30)))
        assertEquals(DayKind.WEEKEND, p.kind(d(2026, 12, 31)))
        assertEquals(DayKind.WORK, p.kind(d(2026, 12, 30)))
        assertEquals(DayKind.SHORT, p.kind(d(2025, 11, 1)))    // рабочая суббота
        assertEquals(DayKind.SHORT, p.kind(d(2027, 2, 20)))
        assertEquals(DayKind.WEEKEND, p.kind(d(2027, 2, 22)))
        // 2026 год: 247 рабочих дней, 4 сокращённых (постановление № 1466).
        val y = (1..12).map { p.month(2026, it) }
        assertEquals(247, y.sumOf { it.workDays })
        assertEquals(4, y.sumOf { it.shortDays })
        assertEquals(247, (1..12).sumOf { p.month(2027, it).workDays })
        // Год без постановления: праздник в субботу переносится на понедельник.
        assertEquals(DayKind.WEEKEND, p.kind(d(2028, 11, 6)))  // 4 ноября 2028 — суббота
        assertEquals(DayKind.SHORT, p.kind(d(2028, 11, 3)))
    }
}
