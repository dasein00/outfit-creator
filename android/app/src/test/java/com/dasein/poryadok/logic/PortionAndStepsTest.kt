package com.dasein.poryadok.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PortionTest {
    // 500 г курицы (110 ккал/100 г) + 200 г риса (130 ккал/100 г) = 810 ккал, 700 г.
    private val list = listOf(
        Ingr("Курица", 500.0, "г", 500.0, Macros(110.0, 23.0, 1.5, 0.0)),
        Ingr("Рис", 200.0, "г", 200.0, Macros(130.0, 2.7, 0.3, 28.0)),
    )

    @Test fun portionByWeightOfIngredients() {
        assertEquals(700.0 / 350.0, Cooking.exactServings(list, 1, 350.0, 0.0), 1e-9)
        assertEquals(405.0, Cooking.perPortion(list, 1, 350.0, 0.0).kcal, 1e-6)
    }

    @Test fun portionByCookedWeight() {
        // Рис разварился: готовое блюдо 900 г, порция 300 г — треть всего.
        assertEquals(3.0, Cooking.exactServings(list, 1, 300.0, 900.0), 1e-9)
        assertEquals(270.0, Cooking.perPortion(list, 1, 300.0, 900.0).kcal, 1e-6)
        assertEquals(90.0, Cooking.per100(list, 900.0).kcal, 1e-6)
    }

    @Test fun withoutPortionWeightUsesServings() {
        assertEquals(405.0, Cooking.perPortion(list, 2, 0.0, 0.0).kcal, 1e-6)
    }
}

class StepHoursTest {
    @Test fun addAndMerge() {
        var s = ""
        s = StepHours.add(s, 100, 9, 500)
        s = StepHours.add(s, 100, 9, 250)
        s = StepHours.add(s, 100, 17, 1542)
        val h = StepHours.decode(s)[100]!!
        assertEquals(750, h[9]); assertEquals(1542, h[17]); assertEquals(2292, h.sum())
        val other = IntArray(24).also { it[9] = 900 }
        assertEquals(900, StepHours.merge(h, other)[9])
        assertEquals(1542, StepHours.merge(h, null)[17])
    }

    @Test fun keepsOnlyRecentDays() {
        var s = ""
        for (d in 1L..30L) s = StepHours.add(s, d, 12, 100)
        assertEquals((30L - StepHours.KEEP + 1..30L).toSet(), StepHours.decode(s).keys)
    }

    @Test fun summary() {
        val h = IntArray(24).also { it[8] = 1700; it[17] = 1542; it[18] = 100 }
        val sum = StepHours.summary(h, 180.0)
        assertEquals(3342, sum.total)
        assertEquals(8, sum.peakHour)
        assertEquals(2, sum.activeHours)
        assertEquals(33, sum.activeMinutes)
        assertEquals(3342 * 1.8 * 0.415 / 1000, sum.km, 1e-9)
        assertArrayEquals(IntArray(24), StepHours.merge(null, null))
    }
}
