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

    /** Отчёт Fitdays со скриншота пользователя: кириллица распознана мусором, числа и единицы — как есть. */
    private val fitdays = listOf(
        "Бeня", "06:31 08/10/2026", "Aaнные 6anahca", "L 43.8 % 56.2 % R", "NHAMKaTOP 3HaYeHMe CTaHAapT",
        "Bec 80.8kg CnNWKOM BbICOKO", "BMI 27.3 BbICOKO", "Tenechbi XNP 26.3% BbICOKO", "Macca XNPa 21.3kg BbICOKO",
        "Macca Tena 6e3 XNPa 59.5kg", "YacToTa cepAeYHbIX 90bpm HopManbHbIN", "CepAeYHbIN NHAeKC 3.3L/min/m² HopManbHbIN",
        "MbIweYHaR Macca 56.6kg OYeHb", "NpoueHT MbIwu 70.1% OYeHb", "CKeneTHbIe MbIwubI 37.4% CTaHAapT",
        "KocTHaR Macca 3.0kg HN3KO", "Macca 6enka 14.1kg OYeHb", "6enok 17.4% OYeHb", "Bec BOAbI 42.5kg CTaHAapT",
        "BoAa 52.6% CTaHAapT", "NOAKOXHbIN XNP 18.8% CnNWKOM", "BNcuepanbHbIN XNP 13.0 CnNWKOM", "6a3OBbIN pacxoA 1705kcal",
        "Bo3pacT Tena 42 CnNWKOM", "NAeanbHbIN Bec Tena 65.1kg", "Fitdays",
    )

    @Test fun fitdaysReport() {
        val r = ScaleScreenParse.parse(fitdays)
        assertEquals(80.8, r.weight!!, 0.01)
        assertEquals(27.3, r.bmi!!, 0.01)
        assertEquals(26.3, r.fatPct!!, 0.01)
        assertEquals(59.5, r.leanKg!!, 0.01)
        assertEquals(90.0, r.heartRate!!, 0.01)
        assertEquals(56.6, r.muscleKg!!, 0.01)
        assertEquals(37.4, r.musclePct!!, 0.01)
        assertEquals(3.0, r.boneKg!!, 0.01)
        assertEquals(17.4, r.proteinPct!!, 0.01)
        assertEquals(52.6, r.waterPct!!, 0.01)
        assertEquals(18.8, r.subcutaneousPct!!, 0.01)
        assertEquals(13.0, r.visceral!!, 0.01)
        assertEquals(1705.0, r.bmr!!, 0.01)
        assertEquals(42.0, r.metabolicAge!!, 0.01)
        assertEquals(65.1, r.idealWeight!!, 0.01)
        val t = java.time.Instant.ofEpochMilli(r.at!!).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        assertEquals(java.time.LocalDateTime.of(2026, 10, 8, 6, 31), t)
    }

    @Test fun fitdaysWithoutUnits() {
        // Мелкие единицы (kg, %) могут не распознаться — порядок и проверка «жир + без жира = вес» всё равно находят вес.
        val noUnits = fitdays.map { it.replace("kg", "").replace("%", "").replace("kcal", "").replace("bpm", "") }
        val r = ScaleScreenParse.parse(noUnits)
        assertEquals(80.8, r.weight!!, 0.01)
        assertEquals(26.3, r.fatPct!!, 0.01)
        assertEquals(1705.0, r.bmr!!, 0.01)
    }
}
