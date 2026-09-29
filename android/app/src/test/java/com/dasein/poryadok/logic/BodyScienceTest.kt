package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BodyScienceTest {
    @Test fun bmiWhoClasses() {
        assertEquals(24.69, BodyScience.bmi(80.0, 180.0), 0.01)
        assertEquals("Норма", BodyScience.BMI_SCALE.labels[BodyScience.BMI_SCALE.zone(24.9)])
        assertEquals("Избыток", BodyScience.BMI_SCALE.labels[BodyScience.BMI_SCALE.zone(25.0)])
        assertEquals("Ожирение I", BodyScience.BMI_SCALE.labels[BodyScience.BMI_SCALE.zone(31.0)])
        assertEquals("Дефицит", BodyScience.BMI_SCALE.labels[BodyScience.BMI_SCALE.zone(18.4)])
    }

    @Test fun healthyRangeForHeight() {
        val r = BodyScience.healthyWeight(173.0)
        assertEquals(55.4, r.low, 0.05)
        assertEquals(74.5, r.high, 0.05)
        assertEquals(65.8, BodyScience.referenceWeight(173.0), 0.05)
        // Девайн: 180 см мужчина ≈ 75 кг, 165 см женщина ≈ 56,9 кг.
        assertEquals(75.0, BodyScience.devine(true, 180.0), 0.1)
        assertEquals(56.9, BodyScience.devine(false, 165.0), 0.1)
    }

    @Test fun bodyFatFormulas() {
        // ВМС США: мужчина 178 см, талия 90, шея 38 → ~20%.
        val navy = BodyScience.navyFat(true, 178.0, 90.0, 38.0, null)!!
        assertEquals(19.9, navy, 0.6)
        assertNull(BodyScience.navyFat(false, 165.0, 75.0, 33.0, null))
        // RFM: мужчина 178 см, талия 90 → 64 − 20·178/90 = 24,4.
        assertEquals(24.44, BodyScience.rfm(true, 178.0, 90.0), 0.01)
        assertEquals(36.44, BodyScience.rfm(false, 178.0, 90.0), 0.01)
        // Деуренберг: ИМТ 25, 30 лет, мужчина → 1,2·25 + 6,9 − 10,8 − 5,4 = 20,7.
        assertEquals(20.7, BodyScience.deurenberg(25.0, 30, true), 0.01)
    }

    @Test fun waistRatiosAndScales() {
        assertEquals(0.5, BodyScience.whtr(85.0, 170.0), 1e-9)
        assertEquals("Повышенный риск", BodyScience.WHTR_SCALE.labels[BodyScience.WHTR_SCALE.zone(0.52)])
        assertEquals("Норма", BodyScience.WHTR_SCALE.labels[BodyScience.WHTR_SCALE.zone(0.45)])
        val w = BodyScience.waistScale(false)
        assertEquals("Высокий риск", w.labels[w.zone(90.0)])
        assertEquals("Здоровый", BodyScience.fatScale(true, 30).let { it.labels[it.zone(15.0)] })
        assertEquals("Избыточный", BodyScience.fatScale(false, 45).let { it.labels[it.zone(36.0)] })
    }

    @Test fun energyAndPace() {
        val f = BodyFacts(male = true, age = 30, heightCm = 180.0, weightKg = 80.0)
        assertEquals(1780.0, BodyScience.bmrMifflin(f), 0.01)
        assertEquals(1450.0, BodyScience.bmrKatch(50.0), 0.01)
        val weeks = BodyScience.weeksToHealthy(90.0, 180.0)!!
        assertEquals(10.0, weeks.low, 0.0)
        assertEquals(19.0, weeks.high, 0.0)
        assertNull(BodyScience.weeksToHealthy(70.0, 180.0))
        assertEquals(1.97, BodyScience.bsa(80.0, 175.0), 0.01)
    }

    @Test fun analyzeUsesBestFatSource() {
        val base = BodyFacts(male = true, age = 30, heightCm = 178.0, weightKg = 85.0)
        val byBmi = BodyScience.analyze(base).first { it.key == "fat" }
        assertEquals("Для точности укажите талию и шею", byBmi.needs)
        val withWaist = BodyScience.analyze(base.copy(waistCm = 90.0, neckCm = 38.0)).associateBy { it.key }
        assertNotNull(withWaist.getValue("whtr").value)
        assertEquals(19.9, withWaist.getValue("fat").value!!, 0.6)
        val scale = BodyScience.analyze(base.copy(scaleFatPct = 22.0)).first { it.key == "fat" }
        assertEquals(22.0, scale.value!!, 0.0)
    }
}
