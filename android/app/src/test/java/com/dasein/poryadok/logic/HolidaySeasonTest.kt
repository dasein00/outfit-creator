package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidaySeasonTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun seasonBoundaries() {
        assertFalse("14 декабря — обычное оформление", HolidaySeason.inSeason(d(2026, 12, 14)))
        assertTrue("15 декабря — включается", HolidaySeason.inSeason(d(2026, 12, 15)))
        assertTrue("31 декабря", HolidaySeason.inSeason(d(2026, 12, 31)))
        assertTrue("1 января", HolidaySeason.inSeason(d(2027, 1, 1)))
        assertTrue("15 января включительно", HolidaySeason.inSeason(d(2027, 1, 15)))
        assertFalse("16 января — снова обычное", HolidaySeason.inSeason(d(2027, 1, 16)))
        assertFalse("лето", HolidaySeason.inSeason(d(2026, 7, 1)))
        assertFalse("29 февраля", HolidaySeason.inSeason(d(2028, 2, 29)))
    }

    @Test fun autoSwitchControlsTheme() {
        assertTrue(HolidaySeason.themeActive(auto = true, d = d(2026, 12, 20)))
        assertFalse("выключенный автоматический режим не включает тему", HolidaySeason.themeActive(auto = false, d = d(2026, 12, 20)))
        assertFalse("вне сезона тема не включается", HolidaySeason.themeActive(auto = true, d = d(2026, 11, 3)))
    }

    @Test fun snowIsSeparateFromTheme() {
        val day = d(2026, 12, 31)
        assertTrue(HolidaySeason.snowAllowed(auto = true, snow = true, d = day))
        assertFalse("снежинки выключены", HolidaySeason.snowAllowed(auto = true, snow = false, d = day))
        // Выключенные снежинки не трогают тему.
        assertTrue(HolidaySeason.themeActive(auto = true, d = day))
        // Без новогоднего режима снежинок нет, даже если их выключатель включён.
        assertFalse(HolidaySeason.snowAllowed(auto = false, snow = true, d = day))
        assertFalse(HolidaySeason.snowAllowed(auto = true, snow = true, d = d(2026, 12, 14)))
    }

    @Test fun previewModeWorksAnyDay() {
        val nov = d(2026, 11, 3)
        assertTrue("тестовый режим включает тему вне сезона", HolidaySeason.themeActive(auto = true, d = nov, preview = true))
        assertTrue("и даже при выключенном автоматическом режиме", HolidaySeason.themeActive(auto = false, d = nov, preview = true))
        assertTrue(HolidaySeason.snowAllowed(auto = false, snow = true, d = nov, preview = true))
        assertFalse("снежинки по-прежнему выключаются отдельно", HolidaySeason.snowAllowed(auto = true, snow = false, d = nov, preview = true))
        assertFalse("без тестового режима — обычное оформление", HolidaySeason.themeActive(auto = true, d = nov, preview = false))
    }

    @Test fun newEntry() {
        assertTrue("первый запуск", HolidaySeason.isNewEntry(0L, 1_000L))
        assertFalse("переключились на минуту", HolidaySeason.isNewEntry(1_000L, 61_000L))
        assertTrue("вернулись через 5 минут", HolidaySeason.isNewEntry(1_000L, 1_000L + HolidaySeason.NEW_ENTRY_AFTER_MS))
    }

    @Test fun labels() {
        assertEquals(d(2026, 12, 15), HolidaySeason.nextStart(d(2026, 11, 3)))
        assertEquals(d(2027, 12, 15), HolidaySeason.nextStart(d(2026, 12, 20)))
        assertEquals(d(2027, 1, 15), HolidaySeason.seasonEnd(d(2026, 12, 20)))
        assertEquals(d(2027, 1, 15), HolidaySeason.seasonEnd(d(2027, 1, 3)))
    }
}
