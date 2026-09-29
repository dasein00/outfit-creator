package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyCompTest {
    private val man = Person(male = true, heightCm = 173.0, age = 30)

    @Test fun weightScaleMatchesOkok() {
        // У OKOK при росте 173 см: 55.37 / 74.52 / 89.49.
        val s = BodyComp.weightScale(173.0)
        assertEquals(55.37, s.bounds[0], 0.05)
        assertEquals(74.52, s.bounds[1], 0.05)
        assertEquals(89.49, s.bounds[2], 0.05)
        assertEquals("Высокий", s.labels[s.zone(78.4)])
    }

    @Test fun bmiAndStatuses() {
        assertEquals(26.2, BodyComp.bmi(78.4, 173.0), 0.05)
        val m = BodyComp.metrics(BodyReading(78.4, fatPct = 23.0, waterPct = 53.1, boneKg = 3.1, visceral = 9.0), man).associateBy { it.key }
        assertEquals("Высокий", m.getValue("bmi").label)
        assertEquals("Избыточный", m.getValue("fat").label)
        assertEquals(18.03, m.getValue("fatKg").value!!, 0.01)
        assertEquals(60.37, m.getValue("lean").value!!, 0.01)
        assertEquals("Низкий", m.getValue("water").label)
        assertEquals("Здоровый", m.getValue("visceral").label)
        assertNull(m.getValue("protein").value)
    }

    @Test fun bodyTypeGrid() {
        assertEquals(0 to 2, BodyComp.bodyType(78.4, 25.0, man))  // высокий ИМТ + много жира = ожирение
        assertEquals(1 to 1, BodyComp.bodyType(68.0, 15.0, man))  // здоровый тип
        assertEquals(0 to 0, BodyComp.bodyType(85.0, 7.0, man))   // спортсмен
        assertNull(BodyComp.bodyType(70.0, null, man))
        assertEquals("Ожирение", BodyComp.BODY_TYPES[0][2])
    }

    @Test fun idealWeightFromBmi22AndAdvice() {
        // Рост 173 см: ориентир ИМТ 22 → 65,84 кг; здоровый жир у мужчины 31 года — до 20% (Gallagher 2000).
        val p = Person(male = true, heightCm = 173.0, age = 31)
        assertEquals(65.84, BodyComp.idealWeight(p), 0.01)
        assertEquals(27.5, BodyComp.obesityPct(83.95, p), 0.1)
        val a = BodyComp.advice(BodyReading(83.95, fatPct = 27.5, muscleKg = 57.8, boneKg = 3.0), p)
        assertEquals(-18.11, a.weightDelta, 0.01)
        assertEquals(-9.92, a.fatDelta!!, 0.05)
        val m = BodyComp.metrics(BodyReading(83.95, fatPct = 27.5, waterPct = 52.0, musclePct = 36.5), p).associateBy { it.key }
        assertEquals("Умеренная", m.getValue("obesity").label)
        assertEquals(43.65, m.getValue("waterKg").value!!, 0.01)
        assertEquals(30.64, m.getValue("skeletalKg").value!!, 0.01)
    }

    @Test fun compareMarksBetterAndWorse() {
        val before = BodyReading(83.85, fatPct = 27.4, waterPct = 52.0)
        val after = BodyReading(84.55, fatPct = 27.7, waterPct = 51.9)
        val d = BodyComp.compare(before, after, man).associateBy { it.key }
        assertEquals(0.70, d.getValue("weight").delta!!, 1e-9)
        assertEquals(false, d.getValue("weight").better)
        assertEquals(false, d.getValue("water").better)
        assertNull(d["protein"])
        assertNull(d["age"])
    }

    @Test fun trendStats() {
        val pts = listOf(1L to 80.2, 3L to 80.0, 8L to 79.7, 30L to 78.4)
        val t = BodyComp.trend(pts, 0, 31)!!
        assertEquals(-1.8, t.change, 1e-9)
        assertEquals(80.2, t.max.second, 0.0)
        assertEquals(30L, t.min.first)
        assertEquals(79.575, t.avg, 1e-9)
        assertNull(BodyComp.trend(pts, 40, 50))
    }
}

class SleepDetectTest {
    private val MIN = 60_000L
    private val H = 60 * MIN
    private val midnight = 20_000L * 86_400_000L

    private fun use(fromMin: Long, lenMin: Long) = listOf(
        SleepDetect.Event(midnight + fromMin * MIN, SleepDetect.Kind.SCREEN_ON),
        SleepDetect.Event(midnight + fromMin * MIN + 5_000, SleepDetect.Kind.UNLOCK),
        SleepDetect.Event(midnight + (fromMin + lenMin) * MIN, SleepDetect.Kind.SCREEN_OFF),
    )

    private fun glance(atMin: Long) = listOf(
        SleepDetect.Event(midnight + atMin * MIN, SleepDetect.Kind.SCREEN_ON),
        SleepDetect.Event(midnight + atMin * MIN + 8_000, SleepDetect.Kind.SCREEN_OFF),
    )

    @Test fun typicalNight() {
        // Вечером телефон в руках, последний раз в 23:40, ночью два уведомления, в 3:10 проверил 3 минуты, подъём 7:05.
        val ev = use(-6 * 60, 30) + use(-3 * 60, 50) + use(-60, 15) + use(-40, 20) +
            glance(60) + glance(150) + use(190, 3) + use(425, 15) + use(600, 30)
        val g = SleepDetect.detect(ev, midnight)!!
        assertEquals(midnight - 20 * MIN, g.lastUseAt)       // 23:40
        assertEquals(midnight - 10 * MIN, g.sleepAt)         // + 10 мин засыпания
        assertEquals(midnight + 425 * MIN, g.wakeAt)         // 7:05
        assertEquals(1, g.awakenings)
        assertEquals(2, g.glances)
        assertEquals(2, g.confidence)
    }

    @Test fun noLongPauseMeansNoSleep() {
        val ev = (0 until 20).flatMap { use(-5 * 60L + it * 60, 20) }
        assertNull(SleepDetect.detect(ev, midnight))
    }

    @Test fun calibrationUsesMedian() {
        val c = SleepDetect.calibrate(
            listOf(1400 to 1425, 1410 to 1440, 5 to 20, 1430 to 1450),
            listOf(420 to 400, 430 to 415),
        )
        assertEquals(25, c.latencyMin)
        assertEquals(0, c.wakeShiftMin)   // меньше трёх исправлений подъёма
        val g = SleepDetect.detect(use(-60, 30) + use(420, 10), midnight, c)
        assertNotNull(g)
        assertTrue(g!!.sleepAt == midnight - 30 * MIN + 25 * MIN)
    }
}
