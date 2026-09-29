package com.dasein.poryadok.logic

import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Показатели тела, которые считаются из роста, веса, возраста, пола и обхватов по опубликованным формулам.
 * У каждого показателя есть формула и источник — ничего не придумано: только клинические руководства и рецензируемые работы.
 */
data class BodyFacts(
    val male: Boolean,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val neckCm: Double? = null,
    /** Процент жира с весов (биоимпеданс), если есть. */
    val scaleFatPct: Double? = null,
    /** Коэффициент активности для суточного расхода. */
    val activity: Double = 1.375,
)

/** Один научный показатель: значение, шкала, вывод, формула и источник. needs — чего не хватает для расчёта. */
data class Evidence(
    val key: String,
    val title: String,
    val value: Double?,
    val unit: String,
    val decimals: Int,
    val scale: Scale?,
    val verdict: String,
    val about: String,
    val formula: String,
    val source: String,
    val needs: String? = null,
) {
    val zone: Int? get() = if (value != null && scale != null) scale.zone(value) else null
    val tone: Tone? get() = zone?.let { scale!!.tones[it] }
    val label: String? get() = zone?.let { scale!!.labels[it] }
}

data class Range(val low: Double, val high: Double)

object BodyScience {
    private fun m(heightCm: Double) = heightCm / 100
    private fun r1(v: Double) = (v * 10).roundToInt() / 10.0

    // ---------- ИМТ, ВОЗ ----------
    fun bmi(w: Double, hCm: Double) = w / (m(hCm) * m(hCm))

    /** Классификация ВОЗ для взрослых. */
    val BMI_SCALE = Scale(
        listOf(18.5, 25.0, 30.0, 35.0, 40.0),
        listOf("Дефицит", "Норма", "Избыток", "Ожирение I", "Ожирение II", "Ожирение III"),
        listOf(Tone.BLUE, Tone.GREEN, Tone.YELLOW, Tone.RED, Tone.RED, Tone.RED),
    )

    /** Здоровый диапазон веса для роста: ИМТ 18,5–24,9. */
    fun healthyWeight(hCm: Double) = Range(r1(18.5 * m(hCm) * m(hCm)), r1(24.9 * m(hCm) * m(hCm)))

    /** Ориентир «идеального» веса по ИМТ 22 — середина нормы (Lemmens, 2005). */
    fun referenceWeight(hCm: Double) = r1(22 * m(hCm) * m(hCm))

    /** Формула Девайна (1974) — медицинская оценка для дозировки лекарств, а не цель похудения. */
    fun devine(male: Boolean, hCm: Double): Double = r1((if (male) 50.0 else 45.5) + 2.3 * (hCm / 2.54 - 60))

    // ---------- Обхваты ----------
    /** Отношение талии к росту: граница 0,5 — «держите талию меньше половины роста» (NICE, 2022). */
    fun whtr(waist: Double, hCm: Double) = waist / hCm

    val WHTR_SCALE = Scale(
        listOf(0.4, 0.5, 0.6),
        listOf("Очень низкое", "Норма", "Повышенный риск", "Высокий риск"),
        listOf(Tone.BLUE, Tone.GREEN, Tone.YELLOW, Tone.RED),
    )

    /** Окружность талии: пороги ВОЗ (2008). */
    fun waistScale(male: Boolean) = Scale(
        if (male) listOf(94.0, 102.0) else listOf(80.0, 88.0),
        listOf("Норма", "Повышенный риск", "Высокий риск"),
        listOf(Tone.GREEN, Tone.YELLOW, Tone.RED),
    )

    /** Отношение талии к бёдрам: ВОЗ (2008) — выше 0,90 у мужчин и 0,85 у женщин риск существенно выше. */
    fun whrScale(male: Boolean) = Scale(
        listOf(if (male) 0.90 else 0.85),
        listOf("Норма", "Высокий риск"),
        listOf(Tone.GREEN, Tone.RED),
    )

    // ---------- Жир ----------
    /** Процент жира по формуле ВМС США (Hodgdon & Beckett, 1984). Сантиметры переводятся в дюймы. */
    fun navyFat(male: Boolean, hCm: Double, waist: Double, neck: Double, hip: Double?): Double? {
        val i = 2.54
        return if (male) {
            if (waist <= neck) return null
            86.010 * log10((waist - neck) / i) - 70.041 * log10(hCm / i) + 36.76
        } else {
            hip ?: return null
            if (waist + hip <= neck) return null
            163.205 * log10((waist + hip - neck) / i) - 97.684 * log10(hCm / i) - 78.387
        }
    }

    /** Относительная жировая масса (Woolcott & Bergman, 2018): точнее ИМТ по сравнению с DXA. */
    fun rfm(male: Boolean, hCm: Double, waist: Double) = 64 - 20 * hCm / waist + if (male) 0 else 12

    /** Оценка жира по ИМТ, возрасту и полу (Deurenberg et al., 1991) — если нет обхватов и весов. */
    fun deurenberg(bmi: Double, age: Int, male: Boolean) = 1.2 * bmi + 0.23 * age - 10.8 * (if (male) 1 else 0) - 5.4

    /** Здоровые диапазоны процента жира по возрасту и полу (Gallagher et al., 2000). */
    fun fatScale(male: Boolean, age: Int): Scale {
        val b = when {
            male && age < 40 -> listOf(8.0, 20.0, 25.0)
            male && age < 60 -> listOf(11.0, 22.0, 28.0)
            male -> listOf(13.0, 25.0, 30.0)
            age < 40 -> listOf(21.0, 33.0, 39.0)
            age < 60 -> listOf(23.0, 34.0, 40.0)
            else -> listOf(24.0, 36.0, 42.0)
        }
        return Scale(b, listOf("Низкий", "Здоровый", "Избыточный", "Ожирение"), listOf(Tone.BLUE, Tone.GREEN, Tone.YELLOW, Tone.RED))
    }

    /** Лучшая доступная оценка жира: весы → формула ВМС → RFM → по ИМТ. Возвращает значение и метод. */
    fun bestFat(f: BodyFacts): Pair<Double, String> {
        f.scaleFatPct?.let { return it to "весы" }
        if (f.waistCm != null && f.neckCm != null) navyFat(f.male, f.heightCm, f.waistCm, f.neckCm, f.hipCm)?.let { return it to "формула ВМС США" }
        f.waistCm?.let { return rfm(f.male, f.heightCm, it) to "RFM" }
        return deurenberg(bmi(f.weightKg, f.heightCm), f.age, f.male) to "оценка по ИМТ"
    }

    // ---------- Безжировая масса ----------
    /** Безжировая масса по формуле Боера (1984), если процент жира неизвестен. */
    fun boerLbm(male: Boolean, w: Double, hCm: Double) = if (male) 0.407 * w + 0.267 * hCm - 19.2 else 0.252 * w + 0.473 * hCm - 48.3

    /** Индекс безжировой массы (Kouri et al., 1995) и его поправка на рост 1,8 м. */
    fun ffmi(lbm: Double, hCm: Double) = lbm / (m(hCm) * m(hCm))
    fun ffmiNormalized(lbm: Double, hCm: Double) = ffmi(lbm, hCm) + 6.1 * (1.8 - m(hCm))

    fun ffmiScale(male: Boolean) = Scale(
        if (male) listOf(17.0, 20.0, 23.0) else listOf(14.0, 17.0, 20.0),
        listOf("Ниже среднего", "Средний", "Выше среднего", "Высокий"),
        listOf(Tone.BLUE, Tone.GREEN, Tone.GREEN, Tone.GREEN),
    )

    // ---------- Энергия ----------
    /** Миффлин — Сан Жеор (1990): по систематическому обзору ADA (Frankenfield, 2005) самая точная формула для взрослых. */
    fun bmrMifflin(f: BodyFacts) = 10 * f.weightKg + 6.25 * f.heightCm - 5 * f.age + if (f.male) 5 else -161

    /** Кэтч — Макардл: через безжировую массу, точнее при известном проценте жира. */
    fun bmrKatch(lbm: Double) = 370 + 21.6 * lbm

    fun tdee(bmr: Double, activity: Double) = bmr * activity

    /** Нижняя граница калорийности без наблюдения врача (NHLBI, 1998): 1200 ккал женщинам, 1500 мужчинам. */
    fun minKcal(male: Boolean) = if (male) 1500 else 1200

    // ---------- Питание и вода ----------
    /** Белок: норма для всех 0,8 г/кг (IOM, 2005); при тренировках 1,2–2,0 г/кг (ACSM/AND/DC, 2016). */
    fun proteinRda(w: Double) = (0.8 * w).roundToInt()
    fun proteinActive(w: Double) = Range((1.2 * w).roundToInt().toDouble(), (2.0 * w).roundToInt().toDouble())

    /** Вода: адекватное потребление EFSA (2010) — 2,5 л мужчинам, 2,0 л женщинам всего; ~80% из напитков. */
    fun waterTotal(male: Boolean) = if (male) 2.5 else 2.0
    fun waterDrinks(male: Boolean) = r1(waterTotal(male) * 0.8)

    // ---------- Темп ----------
    /** Безопасный темп снижения — 0,5–1 кг в неделю (NHLBI, 1998). */
    val SAFE_LOSS = Range(0.5, 1.0)

    /** Цели 5% и 10% от текущего веса: уже такое снижение улучшает давление, сахар и липиды (NHLBI; Look AHEAD). */
    fun benefitTargets(w: Double) = r1(w * 0.95) to r1(w * 0.90)

    /** Сколько недель до верхней границы здорового веса при безопасном темпе. null — вес уже в норме. */
    fun weeksToHealthy(w: Double, hCm: Double): Range? {
        val top = healthyWeight(hCm).high
        if (w <= top) return null
        val d = w - top
        return Range(kotlin.math.ceil(d / SAFE_LOSS.high), kotlin.math.ceil(d / SAFE_LOSS.low))
    }

    /** Площадь поверхности тела по Мостеллеру (1987) — используется в медицине для дозировок. */
    fun bsa(w: Double, hCm: Double) = sqrt(hCm * w / 3600)

    private fun f(v: Double, d: Int = 1) = if (d == 0) v.roundToInt().toString() else "%.${d}f".format(v).replace('.', ',')

    /** Все показатели для экрана «Научный анализ». */
    fun analyze(x: BodyFacts): List<Evidence> {
        val b = bmi(x.weightKg, x.heightCm)
        val hw = healthyWeight(x.heightCm)
        val (fat, fatMethod) = bestFat(x)
        val lbm = x.weightKg * (1 - fat / 100)
        val bmr = bmrMifflin(x)
        val list = mutableListOf<Evidence>()

        val bmiZone = BMI_SCALE.zone(b)
        list += Evidence(
            "bmi", "Индекс массы тела (ИМТ)", b, "кг/м²", 1, BMI_SCALE,
            when (bmiZone) {
                0 -> "Вес ниже нормы для роста. Худеть не нужно — стоит набрать до ${f(hw.low)} кг."
                1 -> "Вес в норме для вашего роста."
                2 -> "Вес выше нормы: снижение до ${f(hw.high)} кг уменьшает риски для сердца и обмена веществ."
                else -> "Ожирение по классификации ВОЗ. Даже 5–10% снижения веса заметно улучшают давление и сахар крови."
            },
            "ИМТ оценивает вес относительно роста. Он не различает мышцы и жир, поэтому у спортсменов бывает завышен — смотрите его вместе с талией и процентом жира.",
            "ИМТ = вес (кг) / рост² (м)",
            "ВОЗ. Obesity: preventing and managing the global epidemic. Technical Report Series 894, 2000",
        )
        list += Evidence(
            "healthy", "Здоровый вес для роста ${f(x.heightCm, 0)} см", x.weightKg, "кг", 1,
            Scale(listOf(hw.low, hw.high), listOf("Ниже", "Здоровый", "Выше"), listOf(Tone.BLUE, Tone.GREEN, Tone.YELLOW)),
            "Диапазон ${f(hw.low)}–${f(hw.high)} кг. Ориентир в середине нормы (ИМТ 22) — ${f(referenceWeight(x.heightCm))} кг.",
            "Это вес, при котором ИМТ от 18,5 до 24,9. «Идеальный» вес — не точка, а диапазон: внутри него риски для здоровья минимальны.",
            "18,5 × рост² … 24,9 × рост²; ориентир 22 × рост²",
            "ВОЗ, TRS 894 (2000); Lemmens HJ et al. Obes Surg 2005;15:1082",
        )
        if (x.waistCm != null) {
            val r = whtr(x.waistCm, x.heightCm)
            list += Evidence(
                "whtr", "Талия / рост", r, "", 2, WHTR_SCALE,
                if (r < 0.5) "Талия меньше половины роста — риск, связанный с центральным ожирением, не повышен."
                else "Талия больше половины роста: жир на животе повышает риск диабета 2 типа и болезней сердца. Цель — талия до ${f(x.heightCm / 2, 0)} см.",
                "Лучше ИМТ предсказывает риски для сердца и обмена веществ, потому что учитывает жир на животе. Правило: держите талию меньше половины роста.",
                "талия (см) / рост (см)",
                "NICE. Obesity: identification, assessment and management (NG246, 2022/2025); Ashwell M et al. Obes Rev 2012;13:275",
            )
            list += Evidence(
                "waist", "Окружность талии", x.waistCm, "см", 0, waistScale(x.male),
                when (waistScale(x.male).zone(x.waistCm)) { 0 -> "Талия в пределах нормы."; 1 -> "Риск для здоровья повышен."; else -> "Риск для здоровья существенно повышен." },
                "Измеряйте на середине между нижним ребром и гребнем подвздошной кости, на выдохе, лента горизонтально.",
                if (x.male) "повышен ≥ 94 см, высокий ≥ 102 см" else "повышен ≥ 80 см, высокий ≥ 88 см",
                "ВОЗ. Waist circumference and waist–hip ratio: report of a WHO expert consultation, 2008",
            )
        } else {
            list += Evidence("whtr", "Талия / рост", null, "", 2, WHTR_SCALE, "", "Показатель жира на животе — один из лучших предикторов рисков.", "талия / рост",
                "NICE NG246", needs = "Укажите обхват талии")
        }
        if (x.waistCm != null && x.hipCm != null) {
            val r = x.waistCm / x.hipCm
            list += Evidence(
                "whr", "Талия / бёдра", r, "", 2, whrScale(x.male),
                if (whrScale(x.male).zone(r) == 0) "Распределение жира без повышенного риска." else "Жир откладывается преимущественно на животе — риск для сердца выше.",
                "Показывает, где откладывается жир: «яблоко» (живот) опаснее, чем «груша» (бёдра).",
                "талия / бёдра",
                "ВОЗ, экспертная консультация 2008",
            )
        }
        list += Evidence(
            "fat", "Процент жира", fat, "%", 1, fatScale(x.male, x.age),
            "Метод: $fatMethod. " + when (fatScale(x.male, x.age).zone(fat)) {
                0 -> "Жира меньше здорового диапазона."
                1 -> "Жир в здоровом диапазоне для вашего возраста и пола."
                2 -> "Жира больше нормы."
                else -> "Уровень жира соответствует ожирению."
            },
            "Точность оценки: весы ±3–5%, формула ВМС США ±3–4% (нужны талия, шея, у женщин — бёдра), RFM ±4–5% (нужна талия), по ИМТ ±4–5%. Смотрите на тренд, а не на одно значение.",
            when (fatMethod) {
                "формула ВМС США" -> if (x.male) "86,010·lg(талия−шея) − 70,041·lg(рост) + 36,76 (дюймы)" else "163,205·lg(талия+бёдра−шея) − 97,684·lg(рост) − 78,387 (дюймы)"
                "RFM" -> "64 − 20 × рост / талия" + if (x.male) "" else " + 12"
                "оценка по ИМТ" -> "1,2·ИМТ + 0,23·возраст − 10,8·пол − 5,4"
                else -> "биоимпеданс весов"
            },
            "Нормы: Gallagher D et al. Am J Clin Nutr 2000;72:694. Формулы: Hodgdon & Beckett 1984 (ВМС США); Woolcott & Bergman, Sci Rep 2018 (RFM); Deurenberg P et al. Br J Nutr 1991;65:105",
            if (x.scaleFatPct == null && x.waistCm == null) "Для точности укажите талию и шею" else null,
        )
        val ffmi = ffmiNormalized(lbm, x.heightCm)
        list += Evidence(
            "ffmi", "Индекс безжировой массы (FFMI)", ffmi, "кг/м²", 1, ffmiScale(x.male),
            "Безжировая масса ${f(lbm)} кг: мышцы, кости, органы и вода.",
            "Как ИМТ, но только для безжировой массы — показывает развитость мышц независимо от жира. Растёт от силовых тренировок и белка.",
            "(вес × (1 − жир%)) / рост², поправка +6,1 × (1,8 − рост)",
            "Kouri EM et al. Clin J Sport Med 1995;5:223",
        )
        list += Evidence(
            "bmr", "Базовый обмен", bmr, "ккал", 0, null,
            "Столько энергии тело тратит в покое. С учётом активности — около ${f(tdee(bmr, x.activity), 0)} ккал в день" +
                (if (x.scaleFatPct != null || x.waistCm != null) "; по безжировой массе (Кэтч — Макардл) — ${f(bmrKatch(lbm), 0)} ккал." else "."),
            "Не ешьте меньше ${minKcal(x.male)} ккал в день без наблюдения врача — так трудно получить все нутриенты.",
            if (x.male) "10·вес + 6,25·рост − 5·возраст + 5" else "10·вес + 6,25·рост − 5·возраст − 161",
            "Mifflin MD et al. Am J Clin Nutr 1990;51:241; Frankenfield D et al. J Am Diet Assoc 2005;105:775; NHLBI Clinical Guidelines 1998",
        )
        val pa = proteinActive(x.weightKg)
        list += Evidence(
            "protein", "Белок в день", proteinRda(x.weightKg).toDouble(), "г", 0, null,
            "Минимум ${proteinRda(x.weightKg)} г; при тренировках и похудении — ${f(pa.low, 0)}–${f(pa.high, 0)} г.",
            "Достаточный белок сохраняет мышцы при дефиците калорий и помогает им расти при тренировках.",
            "0,8 г × вес; 1,2–2,0 г × вес при активности",
            "Institute of Medicine. Dietary Reference Intakes, 2005; Thomas DT et al. (ACSM/AND/DC) Med Sci Sports Exerc 2016;48:543",
        )
        list += Evidence(
            "water", "Вода в день", waterTotal(x.male), "л", 1, null,
            "Всего ${f(waterTotal(x.male))} л, из них около ${f(waterDrinks(x.male))} л — напитки, остальное даёт еда. В жару и при тренировках больше.",
            "Жажда и светлая моча — надёжные ориентиры. Правило «30 мл на кг» — упрощение, а это норма европейского агентства.",
            if (x.male) "2,5 л мужчинам" else "2,0 л женщинам",
            "EFSA Panel on Dietetic Products. Scientific Opinion on Dietary Reference Values for water. EFSA Journal 2010;8(3):1459",
        )
        val (t5, t10) = benefitTargets(x.weightKg)
        val weeks = weeksToHealthy(x.weightKg, x.heightCm)
        list += Evidence(
            "pace", "Темп и цели", null, "", 1, null,
            if (weeks == null) "Вес в здоровом диапазоне — цель поддерживать его." else
                "До ${f(hw.high)} кг при безопасном темпе 0,5–1 кг в неделю: ${f(weeks.low, 0)}–${f(weeks.high, 0)} нед. Первые ступени: −5% (${f(t5)} кг) и −10% (${f(t10)} кг).",
            "Снижение на 5–10% веса уже улучшает давление, сахар и холестерин. Быстрее 1 кг в неделю — больше потерь мышц и выше риск набрать вес обратно.",
            "дефицит ~500–1000 ккал в день ≈ 0,5–1 кг в неделю",
            "NHLBI. Clinical Guidelines on the Identification, Evaluation, and Treatment of Overweight and Obesity in Adults, 1998; Look AHEAD Research Group, Diabetes Care 2011;34:1481",
        )
        list += Evidence(
            "devine", "Вес по формуле Девайна", devine(x.male, x.heightCm), "кг", 1, null,
            "Медицинская оценка «идеального» веса для дозировки лекарств — не цель для похудения.",
            "Раньше её часто принимали за «идеальный вес», но она не учитывает телосложение. Для здоровья ориентируйтесь на диапазон ИМТ и талию.",
            if (x.male) "50 + 2,3 × (рост в дюймах − 60)" else "45,5 + 2,3 × (рост в дюймах − 60)",
            "Devine BJ. Drug Intell Clin Pharm 1974;8:650",
        )
        list += Evidence(
            "bsa", "Площадь поверхности тела", bsa(x.weightKg, x.heightCm), "м²", 2, null,
            "Используется врачами для расчёта доз и оценки функций почек.",
            "Справочный показатель, на здоровье напрямую не указывает.",
            "√(рост × вес / 3600)",
            "Mosteller RD. N Engl J Med 1987;317:1098",
        )
        return list
    }
}
