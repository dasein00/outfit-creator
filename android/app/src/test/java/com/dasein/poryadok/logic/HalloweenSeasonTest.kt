package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HalloweenSeasonTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun seasonBoundaries() {
        assertFalse("23 октября — обычное оформление", HalloweenSeason.inSeason(d(2026, 10, 23)))
        assertTrue("24 октября — включается", HalloweenSeason.inSeason(d(2026, 10, 24)))
        assertTrue("31 октября", HalloweenSeason.inSeason(d(2026, 10, 31)))
        assertTrue("1 ноября включительно", HalloweenSeason.inSeason(d(2026, 11, 1)))
        assertFalse("2 ноября — снова обычное", HalloweenSeason.inSeason(d(2026, 11, 2)))
        assertFalse("новогодний сезон — не Хэллоуин", HalloweenSeason.inSeason(d(2026, 12, 31)))
    }

    @Test fun autoAndPreview() {
        assertTrue(HalloweenSeason.themeActive(auto = true, d = d(2026, 10, 30)))
        assertFalse("выключенный автоматический режим", HalloweenSeason.themeActive(auto = false, d = d(2026, 10, 30)))
        assertTrue("тестовый режим — в любой день", HalloweenSeason.themeActive(auto = false, d = d(2026, 3, 3), preview = true))
    }

    @Test fun labels() {
        assertEquals(d(2026, 10, 24), HalloweenSeason.nextStart(d(2026, 7, 1)))
        assertEquals(d(2027, 10, 24), HalloweenSeason.nextStart(d(2026, 11, 7)))
        assertEquals(d(2026, 11, 1), HalloweenSeason.seasonEnd(d(2026, 10, 28)))
        assertEquals(d(2027, 11, 1), HalloweenSeason.seasonEnd(d(2026, 11, 7)))
    }
}
