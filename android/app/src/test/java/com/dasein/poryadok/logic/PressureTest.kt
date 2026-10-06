package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PressureTest {
    private val today = LocalDate.of(2026, 10, 5)
    private fun person(age: Int, ht: Boolean = false, frail: Boolean = false) =
        Pressure.Person(id = 1, name = "Бабушка", birthYear = 2026 - age, hypertension = ht, treated = ht, frail = frail)

    @Test fun categories() {
        assertEquals(Pressure.Category.OPTIMAL, Pressure.category(115, 75))
        assertEquals(Pressure.Category.NORMAL, Pressure.category(125, 82))
        assertEquals(Pressure.Category.HIGH_NORMAL, Pressure.category(135, 80))
        assertEquals(Pressure.Category.GRADE1, Pressure.category(145, 85))
        assertEquals(Pressure.Category.GRADE2, Pressure.category(150, 102))
        assertEquals(Pressure.Category.GRADE3, Pressure.category(182, 95))
        assertEquals(Pressure.Category.LOW, Pressure.category(88, 58))
        // У пожилых 150/55 — это не «низкое», а изолированная систолическая гипертензия.
        assertEquals(Pressure.Category.GRADE1, Pressure.category(150, 55))
        assertTrue(Pressure.isolatedSystolic(150, 55))
    }

    @Test fun targetsByAge() {
        assertEquals(129, Pressure.target(person(50, ht = true), today).sysHigh)
        assertEquals(120, Pressure.target(person(50, ht = true), today).sysLow)
        assertEquals(139, Pressure.target(person(70, ht = true), today).sysHigh)
        assertEquals(130, Pressure.target(person(84, ht = true), today).sysLow)
        assertEquals(130, Pressure.target(person(60, ht = true, frail = true), today).sysLow)
        val custom = person(70, ht = true).copy(customSys = 140, customDia = 90)
        assertTrue(Pressure.target(custom, today).custom)
    }

    @Test fun verdicts() {
        val gran = person(78, ht = true)
        assertEquals(Pressure.Level.OK, Pressure.verdict(gran, 135, 75, 70, today = today).level)
        assertEquals(Pressure.Level.EMERGENCY, Pressure.verdict(gran, 190, 115, 90, listOf("Боль или давление в груди"), today = today).level)
        assertEquals(Pressure.Level.URGENT, Pressure.verdict(gran, 185, 100, 80, today = today).level)
        assertEquals(Pressure.Level.DOCTOR, Pressure.verdict(gran, 165, 90, 75, today = today).level)
        assertEquals(Pressure.Level.WATCH, Pressure.verdict(gran, 145, 78, 72, today = today).level)
        // Пожилой на лечении с низким давлением — к врачу (риск падений).
        assertEquals(Pressure.Level.DOCTOR, Pressure.verdict(gran, 88, 55, 70, today = today).level)
        // Аритмия поднимает уровень даже при нормальном давлении.
        assertEquals(Pressure.Level.DOCTOR, Pressure.verdict(gran, 132, 74, 70, irregular = true, today = today).level)
        assertNull(Pressure.pulseVerdict(72, 40, false))
        assertEquals(Pressure.Level.URGENT, Pressure.pulseVerdict(135, 40, false)!!.first)
    }

    @Test fun protocolAndStats() {
        val zone = ZoneOffset.UTC
        val rs = (0..6).flatMap { d ->
            val day = today.minusDays(d.toLong())
            listOf(
                Pressure.Reading(d * 10L + 1, 1, day.atTime(8, 0).toInstant(zone).toEpochMilli(), 140, 88, 70),
                Pressure.Reading(d * 10L + 2, 1, day.atTime(21, 0).toInstant(zone).toEpochMilli(), 130, 82, 66),
            )
        }
        val p = Pressure.protocol(rs, today, zone)
        assertTrue(p.done)
        assertEquals(135, p.avg!!.sys)
        assertTrue(p.verdict, "выше порога" in p.verdict)
        val t = Pressure.target(person(50, ht = true), today)
        val now = today.atTime(23, 0).toInstant(zone).toEpochMilli()
        val s = Pressure.stats(rs, t, now, zone)
        assertEquals(140, s.morning!!.sys)
        assertEquals(130, s.evening!!.sys)
        assertEquals(0.0, s.inTargetShare!!, 0.001)
    }

    @Test fun orthostaticAndParse() {
        assertTrue(Pressure.orthostatic(130, 80, listOf(108 to 76, 112 to 78)).positive)
        assertFalse(Pressure.orthostatic(130, 80, listOf(125 to 78)).positive)
        assertEquals(Triple(135, 85, 72), Pressure.parse("135/85 72"))
        assertEquals(Triple(120, 80, null), Pressure.parse("120 80"))
        assertNull(Pressure.parse("80 120"))
        assertEquals(93.3, Pressure.map(120, 80), 0.05)
    }

    @Test fun importTable() {
        val zone = ZoneOffset.UTC
        val csv = "\uFEFFДата;Время;Верхнее;Нижнее;Пульс;Рука;Положение;Аритмия;Метки;Симптомы;Заметка\n" +
            "02.10.2026;08:20;145;75;63;правая;сидя;;;;из тетради\n" +
            "02.10.2026;19:08;123;59;66;левая;сидя;да;\"Вечер, Стресс\";;\n" +
            "мусор;;;\n# Папа\n"
        val r = Pressure.parseTable(csv, zone)
        assertEquals("Папа", r.name)
        assertEquals(2, r.readings.size)
        assertEquals(1, r.skipped)
        val a = r.readings[0]
        assertEquals(145, a.sys); assertEquals(75, a.dia); assertEquals(63, a.pulse); assertEquals(1, a.arm)
        assertEquals(LocalDate.of(2026, 10, 2).atTime(8, 20).toInstant(zone).toEpochMilli(), a.time)
        assertTrue(r.readings[1].irregular)
        assertEquals(listOf("Вечер", "Стресс"), r.readings[1].tags)
        // Без заголовка, через запятую и с датой в другом виде.
        val plain = Pressure.parseTable("2026-09-01 07:39,156,85,76\n1.9.26,19:24,135,64", zone)
        assertEquals(2, plain.readings.size)
        assertEquals(156, plain.readings[0].sys); assertEquals(76, plain.readings[0].pulse)
        assertNull(plain.readings[1].pulse)
    }

    private fun series(days: Int, sys: (Int) -> Int, dia: (Int) -> Int, now: Long): List<Pressure.Reading> = (0 until days).flatMap { d ->
        listOf(8, 20).mapIndexed { k, h ->
            val t = now - d * 86_400_000L - (if (h == 8) 12 else 0) * 3_600_000L
            Pressure.Reading(d * 10L + k + 1, 1, t, sys(d), dia(d), 70)
        }
    }

    @Test fun insightLevels() {
        val now = LocalDate.of(2026, 10, 5).atTime(22, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val me = Pressure.Person(id = 1, name = "Я", birthYear = 1980)
        assertEquals(PressureInsight.Status.STABLE, PressureInsight.assess(me, series(14, { 120 }, { 76 }, now), now, ZoneOffset.UTC).status)
        assertEquals(PressureInsight.Status.ELEVATED, PressureInsight.assess(me, series(14, { 142 }, { 86 }, now), now, ZoneOffset.UTC).status)
        assertEquals(PressureInsight.Status.MEDICAL, PressureInsight.assess(me, series(14, { 165 }, { 95 }, now), now, ZoneOffset.UTC).status)
        assertEquals(PressureInsight.Status.MEDICAL, PressureInsight.assess(me.copy(diabetes = true), series(14, { 140 }, { 84 }, now), now, ZoneOffset.UTC).status)
        // Рост к прошлой неделе на 8 мм при нормальном уровне — наблюдение.
        val rising = series(14, { d -> if (d < 7) 128 else 120 }, { 78 }, now)
        assertEquals(PressureInsight.Status.WATCH, PressureInsight.assess(me, rising, now, ZoneOffset.UTC).status)
        assertEquals(PressureInsight.Status.NO_DATA, PressureInsight.assess(me, series(1, { 130 }, { 80 }, now), now, ZoneOffset.UTC).status)
        assertTrue(PressureInsight.assess(me, rising, now, ZoneOffset.UTC).basis.any { "Предыдущие 7 дней" in it })
    }

    @Test fun insightDynamicsStabilityDeviation() {
        val now = LocalDate.of(2026, 10, 5).atTime(22, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val rs = series(60, { d -> if (d < 30) 124 else 132 }, { d -> if (d < 30) 78 else 82 }, now)
        val dyn = PressureInsight.dynamics(rs, 30, now)!!
        assertEquals(-8, dyn.dSys); assertEquals(true, dyn.better)
        val st = PressureInsight.stability(rs, now)!!
        assertEquals(100, st.score); assertFalse(st.high)
        val noisy = series(14, { d -> if (d % 2 == 0) 110 else 150 }, { 80 }, now)
        assertTrue(PressureInsight.stability(noisy, now)!!.high)
        val spike = Pressure.Reading(999, 1, now + 1, 145, 92, 70)
        val dev = PressureInsight.deviation(rs + spike, spike)!!
        assertEquals(21, dev.dSys); assertTrue(dev.notable)
        val prof = PressureInsight.dayProfile(series(10, { 130 }, { 80 }, now).map { if (Pressure.hour(it, ZoneOffset.UTC) >= 17) it.copy(sys = 140, dia = 87) else it }, ZoneOffset.UTC)
        assertTrue(prof.pattern!!, "вечернее давление в среднем выше утреннего на 10/7" in prof.pattern!!)
        assertTrue(PressureInsight.quality(series(14, { 130 }, { 80 }, now), now, ZoneOffset.UTC).score >= 80)
        assertEquals(30, PressureInsight.points(rs, now - 30 * 86_400_000L + 1, now, true, ZoneOffset.UTC).size)
    }
}
