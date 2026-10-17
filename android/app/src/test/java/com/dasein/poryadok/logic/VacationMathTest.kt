package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class VacationMathTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day).toEpochDay()

    @Test fun roundTrip() {
        val list = listOf(Vacation(d(2026, 7, 1), d(2026, 7, 14), "Море | юг"), Vacation(d(2026, 2, 16), d(2026, 2, 27)))
        val back = VacationMath.parse(VacationMath.format(list))
        assertEquals(2, back.size)
        assertEquals(d(2026, 2, 16), back[0].start)
        assertEquals("Море / юг", back[1].title)
    }

    @Test fun holidaysExtendVacation() {
        // 1–14 мая 2026: 1 и 9 мая — праздники, в отпуск не входят (ст. 120 ТК РФ).
        val v = Vacation(d(2026, 5, 1), d(2026, 5, 14))
        assertEquals(12, VacationMath.days(v))
        assertEquals(2, VacationMath.holidays(v))
        // Рабочие дни мая 2026 с 1 по 14: 4–8 и 12–14 (11 мая — перенос за 9 мая).
        assertEquals(8, VacationMath.workDays(v))
    }

    @Test fun lookupAndYearTotals() {
        val list = listOf(Vacation(d(2026, 12, 28), d(2027, 1, 10)))
        assertEquals(list[0], VacationMath.on(list, d(2027, 1, 3)))
        assertNull(VacationMath.on(list, d(2027, 1, 11)))
        assertEquals(4, VacationMath.daysInYear(list, 2026))
        // 1–8 января — праздники, в 2027 остаются 9 и 10 января.
        assertEquals(2, VacationMath.daysInYear(list, 2027))
        assertEquals(4, VacationMath.daysInMonth(list, 2026, 12))
    }
}
