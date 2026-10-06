package com.dasein.poryadok.logic

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Давление и пульс: классификация, целевые значения, «стоит ли беспокоиться», аналитика.
 *
 * Опора — действующие протоколы:
 *  • Клинические рекомендации Минздрава РФ «Артериальная гипертензия у взрослых» (2020, ред. 2024);
 *  • ESC/ESH 2018 и ESH 2023 (классификация, пороги домашнего давления, протокол домашних измерений);
 *  • ESC 2024 (целевые значения 120–129 при хорошей переносимости, осторожность у пожилых и ослабленных);
 *  • AHA/ACC 2017 — для сравнения (их «гипертония» начинается со 130/80).
 * Приложение не ставит диагноз и не заменяет врача: оно подсказывает, когда спокойно, когда стоит записаться к врачу,
 * а когда — срочно звонить 103/112.
 */
object Pressure {

    // ---------- Данные ----------

    @Serializable
    data class Person(
        val id: Long,
        val name: String,
        /** 0 — мужчина, 1 — женщина. */
        val sex: Int = 0,
        val birthYear: Int? = null,
        val heightCm: Int? = null,
        val weightKg: Double? = null,
        val hypertension: Boolean = false,
        val treated: Boolean = false,
        val diabetes: Boolean = false,
        val kidney: Boolean = false,
        /** ИБС, перенесённый инфаркт или инсульт. */
        val heart: Boolean = false,
        val pregnant: Boolean = false,
        val smoker: Boolean = false,
        /** Повышенный холестерин. */
        val cholesterol: Boolean = false,
        /** Окружность талии, см (абдоминальное ожирение: ≥102 у мужчин, ≥88 у женщин). */
        val waistCm: Int? = null,
        /** Хрупкость: падения, сильная слабость, нужна помощь в быту. */
        val frail: Boolean = false,
        val meds: String = "",
        val note: String = "",
        /** Цель, назначенная врачом (перекрывает расчётную). */
        val customSys: Int? = null,
        val customDia: Int? = null,
        val glyph: String = "pressure/27",
        val createdAt: Long = 0,
    ) {
        fun age(today: LocalDate = LocalDate.now()): Int? = birthYear?.let { today.year - it }
        val bmi: Double? get() = if (heightCm != null && weightKg != null && heightCm > 0) weightKg / (heightCm / 100.0 * heightCm / 100.0) else null
    }

    @Serializable
    data class Reading(
        val id: Long,
        val personId: Long,
        val time: Long,
        val sys: Int,
        val dia: Int,
        val pulse: Int? = null,
        /** 0 — левая рука, 1 — правая. */
        val arm: Int = 0,
        /** 0 — сидя, 1 — лёжа, 2 — стоя. */
        val position: Int = 0,
        val irregular: Boolean = false,
        val tags: List<String> = emptyList(),
        val symptoms: List<String> = emptyList(),
        val note: String = "",
    )

    val TAGS = listOf("Утро, до лекарств", "Вечер", "После лекарства", "После кофе или чая", "После нагрузки", "Стресс, волнение", "Плохо спал", "После алкоголя", "Пропуск лекарства", "Курил перед замером", "Разговаривал во время замера", "Без отдыха перед замером", "У врача")
    /** Тревожные симптомы — при высоком давлении это повод звонить 103/112. */
    val DANGER_SYMPTOMS = listOf("Боль или давление в груди", "Одышка", "Слабость или онемение руки, ноги, лица", "Нарушение речи", "Резкое ухудшение зрения", "Спутанность сознания", "Обморок")
    val MILD_SYMPTOMS = listOf("Головная боль", "Головокружение", "Шум в ушах", "Тошнота", "Сердцебиение", "Слабость", "Мушки перед глазами")

    // ---------- Классификация (ESC/ESH, Минздрав РФ) ----------

    enum class Category(val title: String, val short: String, val level: Int) {
        LOW("Пониженное", "Низкое", 1),
        OPTIMAL("Оптимальное", "Оптимальное", 0),
        NORMAL("Нормальное", "Норма", 0),
        HIGH_NORMAL("Высокое нормальное", "Выше нормы", 1),
        GRADE1("Гипертензия 1 степени", "1 степень", 2),
        GRADE2("Гипертензия 2 степени", "2 степень", 3),
        GRADE3("Гипертензия 3 степени", "3 степень", 4),
    }

    /** Категория по одному замеру (на приёме у врача пороги те же). Берётся худшее из верхнего и нижнего. */
    fun category(sys: Int, dia: Int): Category = when {
        sys >= 180 || dia >= 110 -> Category.GRADE3
        sys >= 160 || dia >= 100 -> Category.GRADE2
        sys >= 140 || dia >= 90 -> Category.GRADE1
        sys < 90 || (dia < 60 && sys < 120) -> Category.LOW
        sys >= 130 || dia >= 85 -> Category.HIGH_NORMAL
        sys >= 120 || dia >= 80 -> Category.NORMAL
        else -> Category.OPTIMAL
    }

    /** Изолированная систолическая гипертензия — частый вариант у пожилых: верхнее ≥140 при нижнем <90. */
    fun isolatedSystolic(sys: Int, dia: Int) = sys >= 140 && dia < 90

    /** Пульсовое давление: больше 60 у пожилых — признак жёстких артерий, меньше 25 — повод показать врачу. */
    fun pulsePressure(sys: Int, dia: Int) = sys - dia

    /** Среднее артериальное давление: ниже 65 — органы могут недополучать кровь. */
    fun map(sys: Int, dia: Int) = ((dia + (sys - dia) / 3.0) * 10).roundToInt() / 10.0

    // ---------- Целевые значения ----------

    data class Target(val sysLow: Int, val sysHigh: Int, val diaLow: Int, val diaHigh: Int, val why: String, val custom: Boolean = false) {
        fun contains(sys: Int, dia: Int) = sys in sysLow..sysHigh && dia in diaLow..diaHigh
        val label get() = "$sysLow–$sysHigh / $diaLow–$diaHigh"
    }

    /**
     * Какое давление считать нормой для человека.
     *  • Без гипертонии: оптимально <120/80, норма до 129/84, нижняя граница ~90/60.
     *  • С гипертонией (Минздрав РФ 2020, ESC 2018/2024): до 65 лет — 120–129 / 70–79;
     *    65 лет и старше — 130–139 / 70–79; при хрупкости и в 80+ — 130–139 (снижать осторожно, не ниже 130 без назначения врача);
     *    при болезни почек — 130–139; при диабете — 120–129 до 65 лет и 130–139 после, нижнее <80, но не ниже 70.
     *  • Беременность: ниже 140/90.
     */
    fun target(p: Person, today: LocalDate = LocalDate.now()): Target {
        if (p.customSys != null && p.customDia != null) {
            return Target(maxOf(90, p.customSys - 10), p.customSys, 60, p.customDia, "Цель, назначенная врачом", custom = true)
        }
        val age = p.age(today)
        if (p.pregnant) return Target(100, 139, 60, 89, "При беременности давление держат ниже 140/90; 160/110 и выше — срочно к врачу")
        val known = p.hypertension || p.treated
        if (!known) {
            return when {
                age != null && age >= 80 -> Target(110, 139, 60, 89, "Для 80+ без гипертонии: до 139/89 — допустимо, главное — без резких колебаний и головокружений")
                age != null && age >= 65 -> Target(100, 134, 60, 84, "Для 65+ без гипертонии: ниже 135/85 по домашним замерам")
                else -> Target(90, 129, 60, 84, "Без гипертонии: оптимально ниже 120/80, норма — до 129/84")
            }
        }
        return when {
            p.frail || (age != null && age >= 80) -> Target(130, 139, 70, 79, "80+ или ослабленный организм: 130–139 / 70–79. Ниже 130 не снижать без решения врача — риск падений и головокружений")
            age != null && age >= 65 -> Target(130, 139, 70, 79, "65 лет и старше с гипертонией: 130–139 / 70–79 (Минздрав РФ, ESC)")
            p.kidney -> Target(130, 139, 70, 79, "При болезни почек: 130–139 / 70–79")
            else -> Target(120, 129, 70, 79, "До 65 лет с гипертонией: 120–129 / 70–79, если такое давление хорошо переносится" + if (p.diabetes || p.heart) " (так же при диабете и болезнях сердца)" else "")
        }
    }

    // ---------- «Стоит ли беспокоиться» ----------

    enum class Level(val title: String, val color: Long) {
        OK("Всё в порядке", 0xFF3E9B5B),
        WATCH("Понаблюдайте", 0xFFC9A227),
        DOCTOR("Обсудите с врачом", 0xFFE08A2E),
        URGENT("Сегодня — к врачу", 0xFFD9542B),
        EMERGENCY("Срочно — скорая 103 / 112", 0xFFC0262D),
    }

    data class Verdict(val level: Level, val headline: String, val explain: String, val actions: List<String>)

    /**
     * Оценка замера (лучше — среднего из 2–3 замеров подряд) с учётом человека и симптомов.
     * Пороги неотложных состояний — по Минздраву РФ и ESC: ≥180/120 с поражением органов (боль в груди, одышка,
     * неврологические симптомы) — гипертонический криз, нужна скорая; ≥180/110 без симптомов — повторить через 5 минут
     * покоя и связаться с врачом в тот же день, резко не снижать.
     */
    fun verdict(p: Person, sys: Int, dia: Int, pulse: Int?, symptoms: List<String> = emptyList(), irregular: Boolean = false, today: LocalDate = LocalDate.now()): Verdict {
        val t = target(p, today)
        val danger = symptoms.any { it in DANGER_SYMPTOMS }
        val age = p.age(today)
        val name = p.name.ifBlank { "Человек" }
        if ((sys >= 180 || dia >= 120) && danger) return Verdict(
            Level.EMERGENCY, "Похоже на гипертонический криз",
            "$sys/$dia с симптомами (${symptoms.filter { it in DANGER_SYMPTOMS }.joinToString(", ").lowercase()}) — это может быть криз с поражением сердца или мозга.",
            listOf(
                "Вызовите скорую: 103 или 112. Скажите давление и симптомы.",
                "Сядьте или полулягте с приподнятой головой, не вставайте резко.",
                "Не принимайте лишние таблетки «для быстрого снижения» без указания врача — резкое падение давления опасно.",
                "Если есть нитроглицерин, назначенный врачом при боли в груди, — по его инструкции. Запишите время начала симптомов.",
            ),
        )
        if (danger && sys < 90) return Verdict(
            Level.EMERGENCY, "Низкое давление с тревожными симптомами",
            "$sys/$dia вместе с ${symptoms.filter { it in DANGER_SYMPTOMS }.joinToString(", ").lowercase()} — нужна срочная помощь.",
            listOf("Вызовите скорую: 103 или 112.", "Уложите, приподнимите ноги, обеспечьте приток воздуха.", "Не давайте лекарства от давления."),
        )
        if (danger) return Verdict(
            Level.EMERGENCY, "Тревожные симптомы",
            "Боль в груди, одышка, онемение, нарушение речи или зрения — повод вызвать скорую при любом давлении.",
            listOf("Вызовите скорую: 103 или 112.", "Запишите время начала симптомов — это важно для врачей (особенно при подозрении на инсульт)."),
        )
        if (sys >= 180 || dia >= 110) return Verdict(
            Level.URGENT, "Очень высокое давление",
            "$sys/$dia — 3 степень. Без тревожных симптомов это ещё не скорая, но тянуть нельзя.",
            listOf(
                "Сядьте, 5 минут спокойно посидите и перемерьте. Запишите оба значения.",
                "Если давление остаётся ≥180/110 — свяжитесь с врачом сегодня (или 103, если появятся боль в груди, одышка, слабость в руке, нарушение речи).",
                if (p.treated) "Проверьте, приняты ли сегодня лекарства по схеме. Не удваивайте дозу самостоятельно." else "Если гипертония раньше не выявлялась — нужен врач: подбирать лечение самостоятельно нельзя.",
                "Снижать давление резко не нужно: безопасно — на 20–25% за первые часы.",
            ),
        )
        if (p.pregnant && (sys >= 140 || dia >= 90)) return Verdict(
            if (sys >= 160 || dia >= 110) Level.URGENT else Level.DOCTOR, "Повышенное давление при беременности",
            "$sys/$dia. При беременности порог — 140/90, это повод сообщить врачу${if (sys >= 160 || dia >= 110) " сегодня" else " в ближайшие дни"}.",
            listOf("Сообщите акушеру-гинекологу.", "При головной боли, мушках перед глазами, боли под рёбрами справа, отёках — срочно 103."),
        )
        if (sys < 90 || (dia < 60 && sys < 110)) {
            val symptomatic = symptoms.any { it in listOf("Головокружение", "Слабость", "Мушки перед глазами", "Тошнота") }
            return Verdict(
                if (symptomatic || (age != null && age >= 65) || p.treated) Level.DOCTOR else Level.WATCH,
                "Пониженное давление",
                "$sys/$dia ниже 90/60." + when {
                    p.treated -> " На лечении это может значить, что дозировка стала великовата — особенно если кружится голова."
                    age != null && age >= 65 -> " У пожилых низкое давление опаснее высокого: риск головокружений и падений."
                    else -> " Если самочувствие хорошее, у молодых и стройных это бывает нормой."
                },
                buildList {
                    add("Вставайте медленно: сначала сядьте, посидите минуту.")
                    add("Пейте достаточно воды, особенно в жару.")
                    if (p.treated) add("Обсудите с врачом дозы лекарств от давления — не отменяйте их сами.")
                    if (symptomatic) add("Головокружение, слабость и мушки перед глазами при низком давлении — повод показаться врачу.")
                },
            )
        }
        val pulseNote = pulseVerdict(pulse, age, irregular)
        val cat = category(sys, dia)
        val inTarget = t.contains(sys, dia)
        val tips = mutableListOf<String>()
        pulseNote?.let { tips += it.second }
        val pp = pulsePressure(sys, dia)
        if (pp > 60 && age != null && age >= 60) tips += "Разница между верхним и нижним — $pp. Больше 60 у пожилых говорит о жёсткости артерий: стоит упомянуть врачу."
        if (pp < 25 && sys >= 90) tips += "Разница между верхним и нижним всего $pp — перемерьте; если повторится, покажите врачу."
        val base = when {
            inTarget -> Verdict(Level.OK, "В пределах нормы для $name", "$sys/$dia — ${cat.title.lowercase()}. Цель: ${t.label}.", emptyList())
            sys > t.sysHigh + 20 || dia > t.diaHigh + 15 || cat == Category.GRADE2 -> Verdict(
                Level.DOCTOR, "Заметно выше цели",
                "$sys/$dia — ${cat.title.lowercase()}. Цель: ${t.label}. Если такие цифры повторяются несколько дней, нужна консультация врача" +
                    if (p.treated) " — возможно, лечение нужно скорректировать." else ".",
                listOf(
                    "Перемерьте после 5 минут покоя сидя, руку на уровне сердца. Ориентируйтесь на среднее из 2–3 замеров.",
                    "Сделайте «протокол 7 дней» (утро и вечер) — с ним врачу проще принять решение.",
                ),
            )
            sys > t.sysHigh || dia > t.diaHigh -> Verdict(
                Level.WATCH, "Немного выше цели",
                "$sys/$dia, цель — ${t.label}. Одно превышение ничего не значит: давление колеблется в течение дня. Важно среднее за неделю.",
                listOf("Перемерьте через 5 минут покоя.", "Если среднее за неделю выше цели — обсудите с врачом на плановом приёме."),
            )
            else -> Verdict(
                Level.WATCH, "Ниже цели",
                "$sys/$dia — ниже целевого ${t.label}." + if (p.treated) " На лечении это повод обсудить дозировку, особенно если бывают головокружения." else " Если самочувствие хорошее — беспокоиться не о чем.",
                if (p.treated) listOf("Вставайте медленно.", "Обсудите с врачом, не великовата ли доза.") else emptyList(),
            )
        }
        val level = if (pulseNote != null && pulseNote.first.ordinal > base.level.ordinal) pulseNote.first else base.level
        return base.copy(level = level, actions = base.actions + tips)
    }

    /** Пульс в покое: 60–100 — норма (у тренированных бывает 50–60). */
    fun pulseVerdict(pulse: Int?, age: Int?, irregular: Boolean): Pair<Level, String>? {
        val parts = mutableListOf<String>()
        var lvl = Level.OK
        if (irregular) { parts += "Тонометр отметил неровный ритм. Если это повторяется — сделайте ЭКГ: так выявляют мерцательную аритмию, она повышает риск инсульта."; lvl = Level.DOCTOR }
        if (pulse != null) when {
            pulse >= 130 -> { parts += "Пульс $pulse в покое — очень частый. При слабости, одышке, боли в груди — 103."; lvl = maxOf(lvl, Level.URGENT) }
            pulse > 100 -> { parts += "Пульс $pulse — учащённый (выше 100). Перемерьте после отдыха; если держится — к врачу."; lvl = maxOf(lvl, Level.WATCH) }
            pulse < 40 -> { parts += "Пульс $pulse — очень редкий. При слабости или обмороках — срочно к врачу."; lvl = maxOf(lvl, Level.URGENT) }
            pulse < 50 -> { parts += "Пульс $pulse — редкий. У спортсменов это бывает нормой; при слабости и головокружении — к врачу."; lvl = maxOf(lvl, Level.WATCH) }
        }
        return if (parts.isEmpty()) null else lvl to parts.joinToString(" ")
    }

    // ---------- Аналитика ----------

    data class Avg(val sys: Int, val dia: Int, val pulse: Int?, val n: Int)

    fun avg(rs: List<Reading>): Avg? {
        if (rs.isEmpty()) return null
        val pulses = rs.mapNotNull { it.pulse }
        return Avg(rs.map { it.sys }.average().roundToInt(), rs.map { it.dia }.average().roundToInt(), pulses.takeIf { it.isNotEmpty() }?.average()?.roundToInt(), rs.size)
    }

    fun day(r: Reading, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(r.time).atZone(zone).toLocalDate()
    fun hour(r: Reading, zone: ZoneId = ZoneId.systemDefault()): Int = Instant.ofEpochMilli(r.time).atZone(zone).hour

    fun isMorning(r: Reading, zone: ZoneId = ZoneId.systemDefault()) = hour(r, zone) in 4..11
    fun isEvening(r: Reading, zone: ZoneId = ZoneId.systemDefault()) = hour(r, zone) in 17..23

    fun sd(xs: List<Int>): Double {
        if (xs.size < 2) return 0.0
        val m = xs.average()
        return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
    }

    data class Stats(
        val last7: Avg?, val last30: Avg?, val prev30: Avg?,
        val morning: Avg?, val evening: Avg?,
        val sysSd: Double, val inTargetShare: Double?,
        val byCategory: Map<Category, Int>,
        val max: Reading?, val min: Reading?,
        /** Изменение верхнего давления за неделю по линии тренда (мм рт. ст. в неделю). */
        val slopePerWeek: Double?,
    )

    fun stats(rs: List<Reading>, t: Target, now: Long, zone: ZoneId = ZoneId.systemDefault()): Stats {
        val day = 86_400_000L
        val l7 = rs.filter { it.time > now - 7 * day }
        val l30 = rs.filter { it.time > now - 30 * day }
        val p30 = rs.filter { it.time in (now - 60 * day)..(now - 30 * day) }
        val slope = if (l30.size >= 4) {
            val xs = l30.map { (it.time - now) / (7.0 * day) }; val ys = l30.map { it.sys.toDouble() }
            val mx = xs.average(); val my = ys.average()
            val den = xs.sumOf { (it - mx) * (it - mx) }
            if (den > 1e-9) xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den else null
        } else null
        return Stats(
            avg(l7), avg(l30), avg(p30),
            avg(l30.filter { isMorning(it, zone) }), avg(l30.filter { isEvening(it, zone) }),
            sd(l30.map { it.sys }),
            if (l30.isEmpty()) null else l30.count { t.contains(it.sys, it.dia) }.toDouble() / l30.size,
            l30.groupingBy { category(it.sys, it.dia) }.eachCount(),
            l30.maxByOrNull { it.sys * 1000 + it.dia }, l30.minByOrNull { it.sys * 1000 + it.dia },
            slope,
        )
    }

    /**
     * Протокол домашних измерений ESH: 7 дней подряд утром (до лекарств и завтрака) и вечером, по 2 замера с интервалом 1–2 минуты.
     * Первый день отбрасывается, по остальным считается среднее. Гипертония по домашним замерам — среднее ≥135/85.
     */
    data class Protocol(val days: Int, val morningDays: Int, val eveningDays: Int, val avg: Avg?, val done: Boolean, val verdict: String)

    fun protocol(rs: List<Reading>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Protocol {
        val from = today.minusDays(6)
        val week = rs.filter { day(it, zone) in from..today }
        val byDay = week.groupBy { day(it, zone) }
        val mDays = byDay.count { (_, l) -> l.any { isMorning(it, zone) } }
        val eDays = byDay.count { (_, l) -> l.any { isEvening(it, zone) } }
        val firstDay = byDay.keys.minOrNull()
        val used = if (byDay.size >= 4) week.filter { day(it, zone) != firstDay } else week
        val a = avg(used)
        val done = mDays >= 6 && eDays >= 6
        val v = when {
            a == null -> "Начните: утром и вечером по 2 замера"
            !done && byDay.size < 4 -> "Нужно минимум 4 дня, лучше 7 — сейчас ${byDay.size}"
            a.sys >= 135 || a.dia >= 85 -> "Среднее ${a.sys}/${a.dia} — выше порога 135/85 для домашних замеров: покажите результаты врачу"
            a.sys >= 130 || a.dia >= 80 -> "Среднее ${a.sys}/${a.dia} — на верхней границе нормы: стоит следить и пересмотреть образ жизни"
            else -> "Среднее ${a.sys}/${a.dia} — норма для домашних замеров (ниже 135/85)"
        }
        return Protocol(byDay.size, mDays, eDays, a, done, v)
    }

    /** Ортостатическая проба: лёжа 5 минут, потом стоя через 1 и 3 минуты. Падение ≥20 верхнего или ≥10 нижнего — ортостатическая гипотензия. */
    data class Orthostatic(val dropSys: Int, val dropDia: Int, val positive: Boolean, val text: String)

    fun orthostatic(lyingSys: Int, lyingDia: Int, standing: List<Pair<Int, Int>>): Orthostatic {
        val worstSys = standing.maxOf { lyingSys - it.first }
        val worstDia = standing.maxOf { lyingDia - it.second }
        val pos = worstSys >= 20 || worstDia >= 10
        return Orthostatic(
            worstSys, worstDia, pos,
            if (pos) "Давление при вставании падает на $worstSys/$worstDia — это ортостатическая гипотензия. Опасна падениями, особенно у пожилых: вставайте медленно и обсудите с врачом лекарства."
            else "Проба в норме: при вставании давление меняется на $worstSys/$worstDia (порог — 20/10).",
        )
    }

    /** Разница между руками: больше 10 — мерить на руке с бо́льшим давлением; больше 15 — сказать врачу. */
    fun armDifference(rs: List<Reading>): Int? {
        val l = rs.filter { it.arm == 0 }.map { it.sys }; val r = rs.filter { it.arm == 1 }.map { it.sys }
        if (l.size < 2 || r.size < 2) return null
        return abs(l.average() - r.average()).roundToInt()
    }

    /** Советы по образу жизни с ожидаемым эффектом (снижение верхнего давления, мм рт. ст.) — по данным ESC/ESH и исследованиям DASH. */
    data class Habit(val title: String, val effect: String, val how: String)

    fun habits(p: Person): List<Habit> = buildList {
        val bmi = p.bmi
        val h = p.heightCm; val w = p.weightKg
        if (bmi != null && bmi >= 25 && h != null && w != null) {
            val goal = (w - 24.9 * (h / 100.0) * (h / 100.0)).roundToInt()
            add(Habit("Снизить вес", "≈ −1 мм на каждый кг", "ИМТ ${"%.1f".format(bmi)}. Даже −5 кг дают около −5 мм. До нормального ИМТ — примерно $goal кг."))
        }
        add(Habit("Меньше соли", "−5…−6 мм", "Не больше 5 г в день (чайная ложка без горки) вместе с солью в хлебе, сыре, колбасе, соусах. Не досаливать за столом."))
        add(Habit("Питание DASH", "до −11 мм", "Овощи и фрукты 5 порций в день, цельные злаки, рыба, бобовые, нежирные молочные продукты; меньше сладкого, жирного мяса и выпечки."))
        add(Habit("Движение", "−5…−8 мм", "150 минут умеренной нагрузки в неделю: ходьба быстрым шагом по 30 минут 5 дней. Пожилым — прогулки, плавание, гимнастика на равновесие."))
        add(Habit("Калий из продуктов", "−4…−5 мм", "Бананы, курага, картофель в мундире, фасоль, шпинат. При болезни почек — только по согласованию с врачом."))
        add(Habit("Алкоголь — минимум", "−3…−4 мм", "Лучше без него. Каждый лишний бокал повышает давление."))
        if (p.smoker) add(Habit("Отказ от курения", "снижение риска инфаркта и инсульта вдвое", "Каждая сигарета поднимает давление на 15–20 минут. Отказ — самое сильное, что можно сделать для сосудов."))
        add(Habit("Сон 7–9 часов", "−2…−4 мм", "Недосып и храп с остановками дыхания (апноэ) повышают давление. Громкий храп — повод сказать врачу."))
        add(Habit("Меньше стресса", "−3…−5 мм", "Дыхание 6 вдохов в минуту по 5 минут, прогулки, медитация. Лекарства это не заменяет, но помогает."))
    }

    /** Как правильно мерить (ESH 2023) — частая причина «скачков» в неверной технике. */
    val TECHNIQUE = listOf(
        "За 30 минут — без кофе, крепкого чая, курения и нагрузки. Опорожните мочевой пузырь.",
        "5 минут посидите спокойно, спина опирается на спинку стула, ноги не скрещены, стопы на полу.",
        "Манжета — на голой руке, на 2–3 см выше локтя, на уровне сердца. Рука лежит на столе расслабленно.",
        "Не разговаривайте и не двигайтесь во время замера.",
        "Сделайте 2 замера с перерывом 1–2 минуты и запишите среднее. Если разница больше 10 — третий замер.",
        "Первый раз — на обеих руках. Дальше мерьте на той, где давление выше.",
        "Размер манжеты важен: на полной руке маленькая манжета завышает давление на 10–15 мм.",
        "Утром — после пробуждения и туалета, до лекарств и завтрака; вечером — перед сном.",
    )

    /** Быстрый ввод: «135 85 72», «135/85 72», «135-85». */
    fun parse(text: String): Triple<Int, Int, Int?>? {
        val n = Regex("\\d{2,3}").findAll(text).map { it.value.toInt() }.toList()
        if (n.size < 2) return null
        val (s, d) = n[0] to n[1]
        if (s !in 50..300 || d !in 30..200 || d >= s) return null
        return Triple(s, d, n.getOrNull(2)?.takeIf { it in 25..250 })
    }

    /** Цвет категории для графиков и бейджей. */
    fun color(c: Category): Long = when (c) {
        Category.LOW -> 0xFF4C8BD6
        Category.OPTIMAL -> 0xFF3E9B5B
        Category.NORMAL -> 0xFF6BAF4F
        Category.HIGH_NORMAL -> 0xFFC9A227
        Category.GRADE1 -> 0xFFE08A2E
        Category.GRADE2 -> 0xFFD9542B
        Category.GRADE3 -> 0xFFB0202A
    }

    /** Сводка для врача: текстом, чтобы отправить в мессенджер или распечатать. */
    fun report(p: Person, rs: List<Reading>, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = target(p)
        val s = stats(rs, t, now, zone)
        val fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm")
        return buildString {
            appendLine("Дневник давления — ${p.name}")
            listOfNotNull(
                p.age()?.let { "$it лет" }, if (p.sex == 1) "женщина" else "мужчина",
                p.heightCm?.let { "рост $it" }, p.weightKg?.let { "вес ${"%.0f".format(it)}" },
                p.bmi?.let { "ИМТ ${"%.1f".format(it)}" },
            ).joinToString(", ").let { appendLine(it) }
            val dx = listOfNotNull(
                "гипертония".takeIf { p.hypertension }, "лечится".takeIf { p.treated }, "диабет".takeIf { p.diabetes },
                "болезнь почек".takeIf { p.kidney }, "ИБС/инфаркт/инсульт".takeIf { p.heart }, "курит".takeIf { p.smoker },
            )
            if (dx.isNotEmpty()) appendLine("Состояния: ${dx.joinToString(", ")}")
            if (p.meds.isNotBlank()) appendLine("Лекарства: ${p.meds}")
            appendLine("Цель: ${t.label}")
            appendLine()
            s.last7?.let { appendLine("Среднее за 7 дней: ${it.sys}/${it.dia}" + (it.pulse?.let { pl -> ", пульс $pl" } ?: "") + " (${it.n} замеров)") }
            s.last30?.let { appendLine("Среднее за 30 дней: ${it.sys}/${it.dia}" + (it.pulse?.let { pl -> ", пульс $pl" } ?: "") + " (${it.n} замеров)") }
            s.morning?.let { appendLine("Утро: ${it.sys}/${it.dia}") }
            s.evening?.let { appendLine("Вечер: ${it.sys}/${it.dia}") }
            s.inTargetShare?.let { appendLine("В пределах цели: ${(it * 100).roundToInt()} %") }
            appendLine()
            appendLine("Замеры:")
            rs.sortedByDescending { it.time }.take(60).forEach { r ->
                append(Instant.ofEpochMilli(r.time).atZone(zone).format(fmt)).append("  ").append("${r.sys}/${r.dia}")
                r.pulse?.let { append("  п.$it") }
                if (r.irregular) append("  аритмия")
                if (r.tags.isNotEmpty()) append("  · ").append(r.tags.joinToString(", "))
                if (r.symptoms.isNotEmpty()) append("  · ").append(r.symptoms.joinToString(", "))
                appendLine()
            }
            appendLine()
            appendLine("Составлено в приложении DASEIN. Не является медицинским заключением.")
        }
    }
    // ---------- Загрузка из таблицы ----------

    data class Imported(val readings: List<Reading>, val name: String?, val skipped: Int)

    private val DATE_RX = Regex("(\\d{1,2})[./](\\d{1,2})[./](\\d{2,4})|(\\d{4})-(\\d{1,2})-(\\d{1,2})")
    private val TIME_RX = Regex("(\\d{1,2})[:\\-.](\\d{2})")

    /**
     * Таблица замеров (CSV из DASEIN, Excel или другого приложения): дата, время, верхнее, нижнее, пульс,
     * по желанию — рука, положение, аритмия, метки, симптомы, заметка. Разделитель — «;», «,» или табуляция.
     * Колонки узнаются по заголовкам; без заголовка — по порядку: дата, время, верхнее, нижнее, пульс.
     * Строка «# Имя» задаёт, чьи это замеры. personId у результата — 0, его ставит загрузка.
     */
    fun parseTable(text: String, zone: ZoneId = ZoneId.systemDefault()): Imported {
        val lines = text.removePrefix("\uFEFF").lines().map { it.trim() }.filter { it.isNotEmpty() }
        var name: String? = null
        val body = lines.filter { l -> if (l.startsWith("#")) { name = l.removePrefix("#").trim().ifBlank { null }; false } else true }
        if (body.isEmpty()) return Imported(emptyList(), name, 0)
        val sep = when { body.first().contains(';') -> ';'; body.first().contains('\t') -> '\t'; else -> ',' }
        fun split(l: String): List<String> {
            val out = mutableListOf<String>(); val cur = StringBuilder(); var q = false
            for (ch in l) when {
                ch == '"' -> q = !q
                ch == sep && !q -> { out += cur.toString().trim(); cur.clear() }
                else -> cur.append(ch)
            }
            out += cur.toString().trim(); return out
        }
        val first = split(body.first()).map { it.lowercase() }
        val hasHeader = first.none { DATE_RX.containsMatchIn(it) } && first.any { it.any(Char::isLetter) }
        fun col(vararg keys: String) = if (!hasHeader) -1 else first.indexOfFirst { h -> keys.any { it in h } }
        val cDate = col("дата", "date", "день"); val cTime = col("время", "time")
        val cSys = col("верх", "сист", "sys"); val cDia = col("ниж", "диаст", "dia")
        val cPulse = col("пульс", "pulse", "чсс", "heart"); val cArm = col("рука", "arm")
        val cPos = col("полож", "position"); val cIrr = col("аритм", "irregular", "arrhythm")
        val cTags = col("метк", "tag"); val cSym = col("симпт", "symptom"); val cNote = col("замет", "коммент", "note", "comment")
        val rows = if (hasHeader) body.drop(1) else body
        var skipped = 0
        val out = mutableListOf<Reading>()
        rows.forEachIndexed { idx, line ->
            val f = split(line)
            fun at(c: Int) = f.getOrNull(c).orEmpty()
            val dateField = if (cDate >= 0) at(cDate) else f.firstOrNull { DATE_RX.containsMatchIn(it) }.orEmpty()
            val dm = DATE_RX.find(dateField) ?: run { skipped++; return@forEachIndexed }
            val date = runCatching {
                if (dm.groupValues[4].isNotEmpty()) LocalDate.of(dm.groupValues[4].toInt(), dm.groupValues[5].toInt(), dm.groupValues[6].toInt())
                else LocalDate.of(dm.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }, dm.groupValues[2].toInt(), dm.groupValues[1].toInt())
            }.getOrNull() ?: run { skipped++; return@forEachIndexed }
            val afterDate = dateField.substring(dm.range.last + 1)
            val timeSrc = if (cTime >= 0) at(cTime) else (TIME_RX.find(afterDate)?.value ?: f.firstOrNull { it != dateField && TIME_RX.matches(it) }.orEmpty())
            val tm = TIME_RX.find(timeSrc)
            val h = tm?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..23 } ?: 12
            val mi = tm?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
            val nums: List<Int?> = if (cSys >= 0 && cDia >= 0) listOf(at(cSys).toIntOrNull(), at(cDia).toIntOrNull(), if (cPulse >= 0) at(cPulse).toIntOrNull() else null)
            else f.filter { it != dateField && it != timeSrc }.mapNotNull { it.toIntOrNull() }.let { listOf(it.getOrNull(0), it.getOrNull(1), it.getOrNull(2)) }
            val sys = nums[0]; val dia = nums[1]
            if (sys == null || dia == null || sys !in 50..300 || dia !in 25..200 || dia >= sys) { skipped++; return@forEachIndexed }
            val armText = (if (cArm >= 0) at(cArm) else f.joinToString(" ")).lowercase()
            val arm = when { "прав" in armText || "right" in armText || armText.trim() == "п" -> 1; else -> 0 }
            val posText = at(cPos).lowercase()
            val pos = when { "леж" in posText || "лёж" in posText || "lying" in posText -> 1; "сто" in posText || "stand" in posText -> 2; else -> 0 }
            val irr = at(cIrr).lowercase().let { it.isNotBlank() && (it.startsWith("да") || it == "1" || it.startsWith("yes") || "аритм" in it || it == "+") }
            fun list(c: Int) = at(c).split(',').map { it.trim() }.filter { it.isNotEmpty() }
            out += Reading(
                id = 0, personId = 0, time = date.atTime(h, mi).atZone(zone).toInstant().toEpochMilli(),
                sys = sys, dia = dia, pulse = nums[2]?.takeIf { it in 25..250 }, arm = arm, position = pos, irregular = irr,
                tags = list(cTags), symptoms = list(cSym), note = at(cNote),
            )
        }
        return Imported(out.sortedBy { it.time }, name, skipped)
    }
}
