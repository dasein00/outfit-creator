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
        val m = BodyComp.metrics(BodyReading(78.4, fatPct = 25.0, waterPct = 53.1, boneKg = 3.1, visceral = 9.0), man).associateBy { it.key }
        assertEquals("Высокий", m.getValue("bmi").label)
        assertEquals("Высокий", m.getValue("fat").label)
        assertEquals(19.6, m.getValue("fatKg").value!!, 0.01)
        assertEquals(58.8, m.getValue("lean").value!!, 0.01)
        assertEquals("Низкий", m.getValue("water").label)
        assertEquals("Здоровый", m.getValue("visceral").label)
        assertNull(m.getValue("protein").value)
    }

    @Test fun bodyTypeGrid() {
        assertEquals(0 to 2, BodyComp.bodyType(78.4, 25.0, man))  // высокий ИМТ + много жира = ожирение
        assertEquals(1 to 1, BodyComp.bodyType(68.0, 15.0, man))  // здоровый тип
        assertEquals(0 to 0, BodyComp.bodyType(85.0, 9.0, man))   // спортсмен
        assertNull(BodyComp.bodyType(70.0, null, man))
        assertEquals("Ожирение", BodyComp.BODY_TYPES[0][2])
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
