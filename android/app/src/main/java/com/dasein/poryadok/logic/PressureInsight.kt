package com.dasein.poryadok.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Клинически ориентированная оценка дневника давления: что происходит, меняется ли ситуация и что разумно сделать.
 * Опирается на ESC 2024 (риск растёт с уровнем АД непрерывно; домашний порог 135/85), ESH 2023 (протокол домашних
 * измерений), AHA/ACC 2025 (риск-ориентированный подход, домашний мониторинг). Диагноз не ставится —
 * только интерпретация данных и подсказка, когда обсудить их с врачом.
 */
object PressureInsight {
    private const val DAY = 86_400_000L
    val SOURCES = "ESC 2024 (повышенное АД и гипертензия), ESH 2023 (домашнее измерение), AHA/ACC 2025, клинические рекомендации Минздрава РФ «Артериальная гипертензия у взрослых»"

    // ---------- Состояние по данным дневника ----------

    enum class Status(val title: String, val color: Long, val emoji: String) {
        NO_DATA("Мало данных", 0xFF8A8F98, "⚪"),
        STABLE("Стабильное", 0xFF3E9B5B, "🟢"),
        WATCH("Требует наблюдения", 0xFFC9A227, "🟡"),
        ELEVATED("Устойчиво повышенное", 0xFFE08A2E, "🟠"),
        MEDICAL("Требует медицинской оценки", 0xFFD9542B, "🔴"),
    }

    data class Assessment(
        val status: Status,
        /** Коротко, простыми словами — режим пользователя. */
        val simple: String,
        /** Вывод по данным. */
        val conclusion: String,
        /** Основание: на каких данных построена оценка («Почему?»). */
        val basis: List<String>,
    )

    /** Пороговое домашнее давление: 135/85 (ESC/ESH). У человека с целевым уровнем ниже — его цель. */
    private fun homeLimit(p: Pressure.Person, t: Pressure.Target) = if (p.customSys != null) t.sysHigh to t.diaHigh else 135 to 85

    private fun highRisk(p: Pressure.Person) = p.diabetes || p.kidney || p.heart

    fun window(rs: List<Pressure.Reading>, from: Long, to: Long) = rs.filter { it.time > from && it.time <= to }

    fun assess(p: Pressure.Person, rs: List<Pressure.Reading>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Assessment {
        val t = Pressure.target(p)
        val w7 = window(rs, now - 7 * DAY, now); val p7 = window(rs, now - 14 * DAY, now - 7 * DAY)
        val w14 = window(rs, now - 14 * DAY, now)
        val a7 = Pressure.avg(w7); val b7 = Pressure.avg(p7); val a14 = Pressure.avg(w14)
        val days7 = w7.map { Pressure.day(it, zone) }.distinct().size
        val (ls, ld) = homeLimit(p, t)
        val basis = mutableListOf<String>()
        if (a7 == null) {
            val last = rs.maxByOrNull { it.time }
            return Assessment(
                Status.NO_DATA, "За последнюю неделю замеров нет.",
                "Для оценки нужны хотя бы 3 дня измерений за последние 7 дней — лучше утром и вечером по 2 замера.",
                listOfNotNull("Замеров за 7 дней: 0", last?.let { "Последний замер: ${Pressure.day(it, zone)}" }),
            )
        }
        basis += "${w7.size} ${plural(w7.size, "измерение", "измерения", "измерений")} за 7 дней (дней с замерами: $days7)"
        basis += "Среднее за 7 дней: ${a7.sys}/${a7.dia}" + (a7.pulse?.let { ", пульс $it" } ?: "")
        b7?.let { basis += "Предыдущие 7 дней: ${it.sys}/${it.dia} → изменение ${sign(a7.sys - it.sys)}/${sign(a7.dia - it.dia)}" }
        a14?.let { basis += "Среднее за 14 дней: ${it.sys}/${it.dia}" }
        basis += "Порог для домашних измерений: $ls/$ld" + if (p.customSys != null) " (цель от врача)" else " (ESC/ESH)"
        val risk = listOfNotNull("диабет".takeIf { p.diabetes }, "болезнь почек".takeIf { p.kidney }, "болезни сердца/сосудов".takeIf { p.heart }, "курение".takeIf { p.smoker }, "холестерин".takeIf { p.cholesterol })
        if (risk.isNotEmpty()) basis += "Факторы риска из профиля: ${risk.joinToString(", ")}"
        val crises = w7.count { it.sys >= 180 || it.dia >= 110 }
        if (crises > 0) basis += "Значений ≥180/110 за 7 дней: $crises"
        val up = b7?.let { a7.sys - it.sys } ?: 0
        if (days7 < 3 || w7.size < 4) {
            return Assessment(
                Status.NO_DATA, "Пока мало замеров, чтобы делать выводы.",
                "Последние значения: среднее ${a7.sys}/${a7.dia}. Для надёжной оценки измеряйте 7 дней подряд утром и вечером.",
                basis,
            )
        }
        val elevated = a7.sys >= ls || a7.dia >= ld
        val (status, simple, concl) = when {
            crises >= 2 || a7.sys >= 160 || a7.dia >= 100 -> Triple(
                Status.MEDICAL, "Давление заметно выше нормы — покажите дневник врачу.",
                "Среднее домашнее давление за 7 дней ${a7.sys}/${a7.dia} значительно выше порога $ls/$ld" + (if (crises > 0) ", были значения ≥180/110" else "") + ". Рекомендуется медицинская оценка в ближайшее время.",
            )
            elevated && (highRisk(p) || (a14 != null && (a14.sys >= 150 || a14.dia >= 95))) -> Triple(
                Status.MEDICAL, "Давление повышено, и есть факторы риска — обсудите с врачом.",
                "Среднее домашнее давление ${a7.sys}/${a7.dia} выше порога $ls/$ld. С учётом факторов риска (${risk.joinToString(", ").ifBlank { "высокое среднее за 14 дней" }}) рекомендуется обсудить результаты с врачом.",
            )
            elevated && a14 != null && (a14.sys >= ls || a14.dia >= ld) -> Triple(
                Status.ELEVATED, "Давление держится выше нормы уже две недели.",
                "Среднее за 7 дней ${a7.sys}/${a7.dia} и за 14 дней ${a14.sys}/${a14.dia} — выше порога $ls/$ld. Повышение устойчивое; стоит показать дневник врачу на плановом приёме.",
            )
            elevated -> Triple(
                Status.WATCH, "Давление на этой неделе выше обычного.",
                "Среднее за 7 дней ${a7.sys}/${a7.dia} выше порога $ls/$ld, но пока без устойчивости. Продолжайте измерения в одинаковых условиях.",
            )
            up >= 5 || a7.sys >= ls - 5 || a7.dia >= ld - 5 -> Triple(
                Status.WATCH, if (up >= 5) "Давление понемногу растёт." else "Давление на верхней границе нормы.",
                if (up >= 5) "Среднее за 7 дней ${a7.sys}/${a7.dia} выше предыдущей недели на $up мм рт. ст. Наблюдается тенденция к повышению."
                else "Среднее ${a7.sys}/${a7.dia} близко к порогу $ls/$ld. Риск растёт с уровнем давления непрерывно, поэтому полезно следить за привычками.",
            )
            else -> Triple(
                Status.STABLE, "Давление в пределах нормы и стабильно.",
                "Среднее домашнее давление ${a7.sys}/${a7.dia} ниже порога $ls/$ld" + (if (b7 != null) ", без заметного роста к прошлой неделе" else "") + ".",
            )
        }
        return Assessment(status, simple, concl, basis)
    }

    private fun sign(v: Int) = if (v > 0) "+$v" else "$v"

    // ---------- Динамика, профиль суток, стабильность, отклонение ----------

    data class Dynamics(val days: Int, val now: Pressure.Avg, val before: Pressure.Avg, val dSys: Int, val dDia: Int, val dPulse: Int?, val text: String, val better: Boolean?)

    fun dynamics(rs: List<Pressure.Reading>, days: Int, now: Long): Dynamics? {
        val a = Pressure.avg(window(rs, now - days * DAY, now)) ?: return null
        val b = Pressure.avg(window(rs, now - 2 * days * DAY, now - days * DAY)) ?: return null
        val ds = a.sys - b.sys; val dd = a.dia - b.dia
        val dp = if (a.pulse != null && b.pulse != null) a.pulse - b.pulse else null
        val (text, better) = when {
            ds <= -3 && dd <= 1 -> "Положительная динамика. Среднее давление за последние $days дней ниже, чем в предыдущем периоде." to true
            ds >= 3 || dd >= 3 -> "Отрицательная динамика. Среднее давление за последние $days дней выше, чем в предыдущем периоде." to false
            else -> "Без существенных изменений: колебания до 3 мм рт. ст. — в пределах обычной вариабельности." to null
        }
        return Dynamics(days, a, b, ds, dd, dp, text, better)
    }

    data class DayProfile(val morning: Pressure.Avg?, val day: Pressure.Avg?, val evening: Pressure.Avg?, val pattern: String?)

    fun dayProfile(rs: List<Pressure.Reading>, zone: ZoneId = ZoneId.systemDefault()): DayProfile {
        val m = Pressure.avg(rs.filter { Pressure.hour(it, zone) in 4..11 })
        val d = Pressure.avg(rs.filter { Pressure.hour(it, zone) in 12..16 })
        val e = Pressure.avg(rs.filter { Pressure.hour(it, zone) in 17..23 })
        val pattern = if (m != null && e != null && m.n >= 3 && e.n >= 3) {
            val ds = e.sys - m.sys; val dd = e.dia - m.dia
            when {
                ds >= 5 -> "Наблюдается паттерн: вечернее давление в среднем выше утреннего на $ds/$dd мм рт. ст."
                ds <= -5 -> "Наблюдается паттерн: утреннее давление в среднем выше вечернего на ${-ds}/${-dd} мм рт. ст."
                else -> "Утро и вечер почти не отличаются."
            }
        } else null
        return DayProfile(m, d, e, pattern)
    }

    /** Стабильность: доля измерений в пределах ±10 мм от личного среднего (верхнее) и разброс (SD). */
    data class Stability(val score: Int, val sdSys: Double, val sdDia: Double, val n: Int, val text: String, val high: Boolean)

    fun stability(rs: List<Pressure.Reading>, now: Long, days: Int = 14): Stability? {
        val w = window(rs, now - days * DAY, now)
        if (w.size < 5) return null
        val mean = w.map { it.sys }.average()
        val near = w.count { abs(it.sys - mean) <= 10 }
        val score = (near * 100.0 / w.size).roundToInt()
        val sdS = Pressure.sd(w.map { it.sys }); val sdD = Pressure.sd(w.map { it.dia })
        val high = sdS >= 12 || score < 60
        val text = if (high) "За последние $days дней наблюдается повышенная вариабельность давления. Проверьте, что измеряете в одинаковых условиях (покой 5 минут, одна рука, манжета по размеру), и обсудите результаты с врачом, если тенденция сохраняется."
        else "Давление относительно стабильно: большинство измерений близко к вашему среднему уровню."
        return Stability(score, sdS, sdD, w.size, text, high)
    }

    data class Deviation(val usual: Pressure.Avg, val dSys: Int, val dDia: Int, val text: String, val notable: Boolean)

    /** Отклонение замера от личного среднего за 30 дней (без него самого). */
    fun deviation(rs: List<Pressure.Reading>, r: Pressure.Reading): Deviation? {
        val base = Pressure.avg(window(rs, r.time - 30 * DAY, r.time - 1).filter { it.id != r.id }) ?: return null
        if (base.n < 5) return null
        val ds = r.sys - base.sys; val dd = r.dia - base.dia
        val notable = ds >= 15 || dd >= 10 || ds <= -15
        val text = when {
            ds >= 15 || dd >= 10 -> "Значение заметно выше вашего среднего уровня за 30 дней. Повторите измерение после 5 минут покоя и оцените результат в контексте остальных измерений."
            ds <= -15 -> "Значение заметно ниже вашего обычного уровня. Если есть слабость или головокружение — сядьте и перемерьте."
            abs(ds) <= 7 && abs(dd) <= 5 -> "Близко к вашему обычному уровню."
            else -> "Небольшое отклонение от обычного уровня — в пределах естественных колебаний."
        }
        return Deviation(base, ds, dd, text, notable)
    }

    // ---------- Лента недели и календарь ----------

    data class DayCell(val date: LocalDate, val avg: Pressure.Avg?, val color: Long)

    fun dayColor(a: Pressure.Avg?, p: Pressure.Person): Long {
        if (a == null) return 0
        val t = Pressure.target(p)
        val (ls, ld) = homeLimit(p, t)
        return when {
            a.sys >= 160 || a.dia >= 100 -> 0xFFD9542B
            a.sys >= ls || a.dia >= ld -> 0xFFE08A2E
            a.sys >= ls - 5 || a.dia >= ld - 5 -> 0xFFC9A227
            a.sys < 95 || a.dia < 55 -> 0xFF4C8BD6
            else -> 0xFF3E9B5B
        }
    }

    fun days(rs: List<Pressure.Reading>, p: Pressure.Person, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<DayCell> {
        val by = rs.groupBy { Pressure.day(it, zone) }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { d ->
            val a = Pressure.avg(by[d].orEmpty()); DayCell(d, a, dayColor(a, p))
        }.toList()
    }

    // ---------- Закономерности ----------

    /** Простой поиск закономерностей в дневнике: только наблюдения, без причинных выводов. */
    fun patterns(rs: List<Pressure.Reading>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<String> = buildList {
        val w = window(rs, now - 28 * DAY, now)
        if (w.size >= 8) {
            val xs = w.map { (it.time - now) / (7.0 * DAY) }; val ys = w.map { it.sys.toDouble() }
            val mx = xs.average(); val my = ys.average()
            val den = xs.sumOf { (it - mx) * (it - mx) }
            if (den > 1e-9) {
                val k = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
                if (k >= 2) add("За 28 дней обнаружено постепенное повышение среднего верхнего давления (≈ +${"%.1f".format(k).replace('.', ',')} мм рт. ст. в неделю).")
                if (k <= -2) add("За 28 дней верхнее давление постепенно снижается (≈ ${"%.1f".format(k).replace('.', ',')} мм рт. ст. в неделю).")
            }
            val m = w.filter { Pressure.hour(it, zone) in 4..11 }; val e = w.filter { Pressure.hour(it, zone) in 17..23 }
            if (m.size >= 4 && e.size >= 4) {
                val hiM = m.count { it.sys >= 135 } * 100 / m.size; val hiE = e.count { it.sys >= 135 } * 100 / e.size
                if (hiE - hiM >= 20) add("Повышенные значения чаще наблюдались вечером ($hiE% вечерних замеров против $hiM% утренних).")
                if (hiM - hiE >= 20) add("Повышенные значения чаще наблюдались утром ($hiM% утренних замеров против $hiE% вечерних).")
            }
        }
        // Метки: после каких обстоятельств давление выше.
        listOf("Плохо спал" to "после дней с плохим сном", "После кофе или чая" to "после кофе или чая", "Стресс, волнение" to "при стрессе",
            "После алкоголя" to "после алкоголя", "Пропуск лекарства" to "при пропуске лекарства").forEach { (tag, label) ->
            val with = rs.filter { tag in it.tags }; val without = rs.filter { tag !in it.tags }
            if (with.size >= 3 && without.size >= 5) {
                val d = with.map { it.sys }.average() - without.map { it.sys }.average()
                if (d >= 5) add("Замеры $label в среднем выше остальных на ${d.roundToInt()} мм рт. ст. (${with.size} замеров).")
            }
        }
        val weekend = rs.filter { Pressure.day(it, zone).dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
        val weekday = rs.filter { Pressure.day(it, zone).dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
        if (weekend.size >= 6 && weekday.size >= 10) {
            val d = weekday.map { it.sys }.average() - weekend.map { it.sys }.average()
            if (d >= 5) add("В будни давление в среднем выше, чем в выходные, на ${d.roundToInt()} мм рт. ст.")
            if (d <= -5) add("В выходные давление в среднем выше, чем в будни, на ${(-d).roundToInt()} мм рт. ст.")
        }
        Pressure.armDifference(w)?.let { if (it >= 10) add("Разница между руками в среднем $it мм рт. ст. — измеряйте на руке с бо́льшим давлением.") }
        val lowPulse = w.count { (it.pulse ?: 99) < 50 }
        if (lowPulse >= 3) add("Пульс ниже 50 уд/мин отмечен $lowPulse раз за 28 дней.")
    }

    // ---------- Качество измерений ----------

    data class Quality(val score: Int, val label: String, val issues: List<String>)

    fun quality(rs: List<Pressure.Reading>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Quality {
        val w = window(rs, now - 14 * DAY, now)
        val issues = mutableListOf<String>()
        var score = 100
        if (w.isEmpty()) return Quality(0, "нет данных", listOf("За 14 дней нет измерений."))
        val days = w.map { Pressure.day(it, zone) }.distinct().size
        if (days < 7) { score -= 25; issues += "Измерения в $days из 14 дней — для надёжной картины лучше каждый день." }
        // Серии: подряд в течение 10 минут, разница больше 10 — признак нарушенной техники.
        val sorted = w.sortedBy { it.time }
        val jumps = sorted.zipWithNext().count { (a, b) -> b.time - a.time <= 10 * 60_000 && a.arm == b.arm && abs(a.sys - b.sys) > 15 }
        if (jumps >= 2) { score -= 20; issues += "Повторные замеры подряд часто отличаются больше чем на 15 мм — проверьте технику: покой 5 минут, не разговаривать, рука на уровне сердца." }
        val bad = w.count { r -> r.tags.any { it in listOf("После кофе или чая", "После нагрузки", "Курил перед замером", "Разговаривал во время замера", "Без отдыха перед замером") } }
        if (bad > 0) { score -= minOf(30, bad * 5); issues += "$bad ${plural(bad, "замер сделан", "замера сделаны", "замеров сделаны")} в неподходящих условиях (кофе, нагрузка, курение, разговор) — такие цифры завышены." }
        val singles = sorted.groupBy { Pressure.day(it, zone) to (Pressure.hour(it, zone) < 14) }.count { it.value.size == 1 }
        if (singles > days) { score -= 10; issues += "Часто записан один замер — делайте 2 с перерывом 1–2 минуты и смотрите среднее." }
        score = score.coerceIn(0, 100)
        return Quality(score, when { score >= 80 -> "хорошее"; score >= 55 -> "удовлетворительное"; else -> "низкое" }, issues)
    }

    // ---------- Что сейчас важно ----------

    data class Tip(val kind: String, val title: String, val text: String)

    fun important(p: Pressure.Person, rs: List<Pressure.Reading>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Tip> = buildList {
        val a = assess(p, rs, now, zone)
        val q = quality(rs, now, zone)
        val st = stability(rs, now)
        val dyn7 = dynamics(rs, 7, now)
        when (a.status) {
            Status.MEDICAL -> add(Tip("alert", "Обсудите с врачом", "${a.conclusion} Сформируйте отчёт для врача на вкладке «Аналитика». Не меняйте дозы лекарств самостоятельно."))
            Status.ELEVATED -> add(Tip("alert", "Обратите внимание", "Среднее давление устойчиво выше нормы. Продолжайте регулярные измерения и покажите дневник врачу."))
            Status.WATCH -> if (dyn7?.better == false) add(Tip("watch", "Обратите внимание", "Среднее давление за последние 7 дней выше предыдущей недели. Продолжайте регулярные измерения и обсудите устойчивое повышение с врачом."))
                else add(Tip("watch", "Понаблюдайте", a.conclusion))
            Status.NO_DATA -> add(Tip("info", "Нужно больше измерений", "Измеряйте 7 дней подряд утром и вечером по 2 замера — по такому протоколу врачи оценивают домашнее давление."))
            Status.STABLE -> add(Tip("ok", "Совет дня", "Ваши показатели стабильны. Продолжайте измерять давление примерно в одно и то же время и записывать результаты."))
        }
        if (q.score < 80 && q.issues.isNotEmpty()) add(Tip("quality", "Качество измерения", q.issues.first()))
        if (st?.high == true) add(Tip("watch", "Колебания", st.text))
        if (rs.any { "Пропуск лекарства" in it.tags && it.time > now - 14 * DAY }) add(Tip("med", "Лекарства", "Отмечены пропуски лекарства. Регулярный приём важнее точного времени: поставьте напоминание. Не удваивайте дозу без указания врача."))
        val low = window(rs, now - 14 * DAY, now).count { (it.pulse ?: 99) < 50 }
        if (low >= 2) add(Tip("watch", "Пульс", "Пульс ниже 50 уд/мин был $low раз за 2 недели. Если есть слабость или головокружение — сообщите врачу."))
    }

    /** Персональные изменения образа жизни: сначала то, что важнее именно для этого человека. */
    fun changes(p: Pressure.Person, rs: List<Pressure.Reading>): List<Pair<String, String>> {
        val list = mutableListOf<Triple<Int, String, String>>()
        list += Triple(100, "Измерять в одинаковых условиях", "Утром до лекарств и вечером, сидя, после 5 минут покоя, 2 замера с перерывом 1–2 минуты.")
        p.bmi?.let { if (it >= 25) list += Triple(90, "Следить за массой тела", "ИМТ ${"%.1f".format(it).replace('.', ',')}. Снижение веса даёт около −1 мм рт. ст. на каждый килограмм.") }
        p.waistCm?.let { w -> if ((p.sex == 0 && w >= 102) || (p.sex == 1 && w >= 88)) list += Triple(85, "Уменьшить окружность талии", "Талия $w см — признак абдоминального ожирения, связанного с давлением.") }
        if (p.smoker) list += Triple(88, "Отказаться от курения", "Курение резко повышает сердечно-сосудистый риск при любом давлении.")
        val tagShare = { tag: String -> rs.count { tag in it.tags } }
        if (tagShare("После алкоголя") >= 2) list += Triple(80, "Ограничить алкоголь", "Алкоголь отмечен в дневнике несколько раз; его снижение даёт −3…−4 мм рт. ст.")
        if (tagShare("Плохо спал") >= 2) list += Triple(78, "Нормализовать сон", "Плохой сон отмечен несколько раз. 7–9 часов сна; громкий храп с паузами дыхания — повод сказать врачу.")
        if (tagShare("Стресс, волнение") >= 2) list += Triple(70, "Управлять стрессом", "Стресс отмечен несколько раз. Медленное дыхание, прогулки, режим помогают снизить давление.")
        if (tagShare("Пропуск лекарства") >= 1) list += Triple(95, "Принимать лекарства регулярно", "Есть пропуски. Не меняйте самостоятельно дозировку назначенных препаратов.")
        list += Triple(75, "Следить за потреблением натрия", "Не больше 5 г соли в день вместе со скрытой солью в хлебе, сыре, колбасе: −5…−6 мм рт. ст.")
        list += Triple(72, "Регулярная физическая активность", "150 минут умеренной нагрузки в неделю: −5…−8 мм рт. ст.")
        if (p.treated) list += Triple(65, "Не менять дозы самостоятельно", "Изменения схемы лечения — только вместе с врачом.")
        return list.sortedByDescending { it.first }.map { it.second to it.third }
    }

    // ---------- Отчёт за неделю и режим врача ----------

    data class Weekly(val daysCovered: Int, val n: Int, val avg: Pressure.Avg?, val prev: Pressure.Avg?, val trend: String, val note: String?, val quality: String, val advice: String)

    fun weekly(p: Pressure.Person, rs: List<Pressure.Reading>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Weekly {
        val w = window(rs, now - 7 * DAY, now)
        val a = Pressure.avg(w); val b = Pressure.avg(window(rs, now - 14 * DAY, now - 7 * DAY))
        val trend = if (a != null && b != null) when {
            a.sys - b.sys <= -3 -> "↓ улучшение"; a.sys - b.sys >= 3 -> "↑ ухудшение"; else -> "→ без изменений"
        } else "—"
        val prof = dayProfile(w, zone)
        val q = quality(rs, now, zone)
        val st = assess(p, rs, now, zone).status
        val advice = when (st) {
            Status.STABLE -> "Продолжить наблюдение в аналогичном режиме."
            Status.WATCH -> "Продолжить регулярные измерения; при сохранении тенденции обсудить с врачом."
            Status.ELEVATED -> "Показать дневник врачу на плановом приёме."
            Status.MEDICAL -> "Обсудить результаты с врачом в ближайшее время."
            Status.NO_DATA -> "Измерять 7 дней подряд утром и вечером."
        }
        return Weekly(w.map { Pressure.day(it, zone) }.distinct().size, w.size, a, b, trend, prof.pattern?.takeIf { "паттерн" in it }, q.label, advice)
    }

    data class Doctor(
        val n: Int, val mean: Pressure.Avg?, val sdSys: Double, val sdDia: Double,
        val morning: Pressure.Avg?, val evening: Pressure.Avg?,
        val maxR: Pressure.Reading?, val minR: Pressure.Reading?,
        val elevatedShare: Int, val slopePerWeek: Double?,
        val pulseMin: Int?, val pulseMax: Int?, val prev: Pressure.Avg?,
    )

    fun doctor(rs: List<Pressure.Reading>, days: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Doctor {
        val w = window(rs, now - days * DAY, now)
        val slope = if (w.size >= 4) {
            val xs = w.map { (it.time - now) / (7.0 * DAY) }; val ys = w.map { it.sys.toDouble() }
            val mx = xs.average(); val my = ys.average(); val den = xs.sumOf { (it - mx) * (it - mx) }
            if (den > 1e-9) xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den else null
        } else null
        val pulses = w.mapNotNull { it.pulse }
        return Doctor(
            w.size, Pressure.avg(w), Pressure.sd(w.map { it.sys }), Pressure.sd(w.map { it.dia }),
            Pressure.avg(w.filter { Pressure.hour(it, zone) in 4..11 }), Pressure.avg(w.filter { Pressure.hour(it, zone) in 17..23 }),
            w.maxByOrNull { it.sys }, w.minByOrNull { it.sys },
            if (w.isEmpty()) 0 else w.count { it.sys >= 135 || it.dia >= 85 } * 100 / w.size, slope,
            pulses.minOrNull(), pulses.maxOrNull(), Pressure.avg(window(rs, now - 2 * days * DAY, now - days * DAY)),
        )
    }

    /** Точки графика: все замеры или средние за день, в окне [from, to]. */
    data class Point(val time: Long, val sys: Float, val dia: Float, val pulse: Float?)

    fun points(rs: List<Pressure.Reading>, from: Long, to: Long, daily: Boolean, zone: ZoneId = ZoneId.systemDefault()): List<Point> {
        val w = rs.filter { it.time in from..to }.sortedBy { it.time }
        if (!daily) return w.map { Point(it.time, it.sys.toFloat(), it.dia.toFloat(), it.pulse?.toFloat()) }
        return w.groupBy { Pressure.day(it, zone) }.toSortedMap().map { (d, l) ->
            val ps = l.mapNotNull { it.pulse }
            Point(d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), l.map { it.sys }.average().toFloat(), l.map { it.dia }.average().toFloat(), ps.takeIf { it.isNotEmpty() }?.average()?.toFloat())
        }
    }
}
