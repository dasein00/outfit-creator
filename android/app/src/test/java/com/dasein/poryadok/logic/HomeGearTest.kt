package com.dasein.poryadok.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGearTest {
    private val home = setOf("Степпер", "Беговая дорожка", "Велотренажёр", "Колесо для пресса", "Гантели", "Утяжелители", "Фитнес-коврик", "Стул")

    @Test fun homeEquipmentFits() {
        assertTrue(HomeGear.fits("Без инвентаря", home))
        assertTrue(HomeGear.fits("", home))
        assertTrue(HomeGear.fits("Гантели", home))
        assertTrue(HomeGear.fits("Гантель", home))
        assertTrue(HomeGear.fits("Гантели, стул", home))
        assertTrue(HomeGear.fits("Колесо для пресса", home))
        assertTrue(HomeGear.fits("Велотренажёр", home))
    }

    @Test fun gymEquipmentHidden() {
        assertFalse(HomeGear.fits("Штанга", home))
        assertFalse(HomeGear.fits("Тренажёр", home))
        assertFalse(HomeGear.fits("Турник", home))
        assertFalse(HomeGear.fits("Брусья", home))
        assertFalse(HomeGear.fits("Гиря", home))
        assertFalse(HomeGear.fits("Гантели, штанга", home))
        assertTrue(HomeGear.fits("Блок или резина", home + "Резинка"))
        assertFalse(HomeGear.fits("Блок или резина", home))
    }
}
