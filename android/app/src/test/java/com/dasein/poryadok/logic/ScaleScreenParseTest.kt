package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScaleScreenParseTest {
    @Test fun englishFitdays() {
        val r = ScaleScreenParse.parse(
            listOf(
                "12:41", "Weight 78.4kg", "BMI 24.2", "Body Fat 22.5%", "Fat-free body weight 60.8kg", "Subcutaneous fat 19.8%",
                "Visceral fat 9", "Body water 54.3%", "Skeletal muscle 47.1%", "Muscle mass 57.6kg", "Bone mass 3.2kg",
                "Protein 17.9%", "BMR 1650kcal", "Metabolic age 34", "07/10/2026",
            ),
        )
        assertEquals(78.4, r.weight!!, 0.01)
        assertEquals(24.2, r.bmi!!, 0.01)
        assertEquals(22.5, r.fatPct!!, 0.01)
        assertEquals(19.8, r.subcutaneousPct!!, 0.01)
        assertEquals(9.0, r.visceral!!, 0.01)
        assertEquals(54.3, r.waterPct!!, 0.01)
        assertEquals(47.1, r.musclePct!!, 0.01)
        assertEquals(57.6, r.muscleKg!!, 0.01)
        assertEquals(3.2, r.boneKg!!, 0.01)
        assertEquals(17.9, r.proteinPct!!, 0.01)
        assertEquals(1650.0, r.bmr!!, 0.01)
        assertEquals(34.0, r.metabolicAge!!, 0.01)
        assertEquals(60.8, r.leanKg!!, 0.01)
    }

    @Test fun russianAndLookalikes() {
        // «Вес» и «Вода», распознанные латиницей: Bec, Boдa.
        val r = ScaleScreenParse.parse(listOf("Bec", "81,2 кг", "Жир 25,1 %", "Вода 52,0%", "Костная масса 3,4 кг", "Базовый обмен 1720 ккал", "Висцеральный жир 11"))
        assertEquals(81.2, r.weight!!, 0.01)
        assertEquals(25.1, r.fatPct!!, 0.01)
        assertEquals(52.0, r.waterPct!!, 0.01)
        assertEquals(3.4, r.boneKg!!, 0.01)
        assertEquals(1720.0, r.bmr!!, 0.01)
        assertEquals(11.0, r.visceral!!, 0.01)
    }

    @Test fun unitsOnlyFallback() {
        val r = ScaleScreenParse.parse(listOf("xxx 79.9kg", "yyy 23.0%", "zzz 2.9kg", "qqq 1600kcal"))
        assertEquals(79.9, r.weight!!, 0.01)
        assertEquals(23.0, r.fatPct!!, 0.01)
        assertEquals(2.9, r.boneKg!!, 0.01)
        assertEquals(1600.0, r.bmr!!, 0.01)
        assertNull(r.visceral)
    }
}
