package com.dasein.poryadok.logic

import kotlin.math.roundToInt

/** Цвет зоны на шкале: голубая (ниже нормы), зелёная (норма), жёлтая (выше), красная (сильно выше). */
enum class Tone { BLUE, GREEN, YELLOW, RED }

/** Шкала показателя: границы между зонами, подписи и цвета зон. */
data class Scale(val bounds: List<Double>, val labels: List<String>, val tones: List<Tone>) {
    init { require(labels.size == bounds.size + 1 && tones.size == labels.size) }

    fun zone(v: Double): Int = bounds.indexOfFirst { v < it }.let { if (it < 0) bounds.size else it }
}

/** Показатель тела для списка «Показатели тела». */
data class BodyMetricView(
    val key: String,
    val title: String,
    val value: Double?,
    val unit: String,
    val decimals: Int,
    val scale: Scale?,
    val about: String,
) {
    val zone: Int? get() = if (value != null && scale != null) scale.zone(value) else null
    val label: String? get() = zone?.let { scale!!.labels[it] }
    val tone: Tone? get() = zone?.let { scale!!.tones[it] }
    fun text(): String = value?.let { if (decimals == 0) it.roundToInt().toString() else "%.${decimals}f".format(it).replace('.', ',') } ?: "—"
}

/** Одно взвешивание со всеми метриками, которые дали весы или ввёл пользователь. */
data class BodyReading(
    val weight: Double,
    val fatPct: Double? = null,
    val musclePct: Double? = null,
    val muscleKg: Double? = null,
    val waterPct: Double? = null,
    val proteinPct: Double? = null,
    val boneKg: Double? = null,
    val visceral: Double? = null,
    val bmr: Double? = null,
    val metabolicAge: Double? = null,
    val subcutaneousPct: Double? = null,
    val leanKg: Double? = null,
)

data class Person(val male: Boolean, val heightCm: Double, val age: Int)

object BodyComp {
    private val WEIGHT_LABELS = listOf("Низкий", "Здоровый", "Высокий", "Ожирение")
    private val WEIGHT_TONES = listOf(Tone.BLUE, Tone.GREEN, Tone.YELLOW, Tone.RED)

    fun bmi(weight: Double, heightCm: Double): Double = weight / ((heightCm / 100) * (heightCm / 100))

    /** Границы веса считаются от ИМТ 18,5 / 24,9 / 29,9 — так же, как у весов OKOK. */
    fun weightScale(heightCm: Double): Scale {
        val h2 = (heightCm / 100) * (heightCm / 100)
        return Scale(listOf(18.5, 24.9, 29.9).map { (it * h2 * 100).roundToInt() / 100.0 }, WEIGHT_LABELS, WEIGHT_TONES)
    }

    fun bmiScale() = Scale(listOf(18.5, 24.9, 29.9), WEIGHT_LABELS, WEIGHT_TONES)

    fun fatScale(p: Person): Scale {
        val add = if (p.age >= 40) 2.0 else 0.0
        val b = if (p.male) listOf(11.0, 21.0, 26.0) else listOf(21.0, 31.0, 36.0)
        return Scale(b.map { it + add }, listOf("Низкий", "Здоровый", "Высокий", "Ожирение"), WEIGHT_TONES)
    }

    fun waterScale(p: Person) = Scale(if (p.male) listOf(55.0, 65.0) else listOf(45.0, 60.0), listOf("Низкий", "Здоровый", "Высокий"), listOf(Tone.BLUE, Tone.GREEN, Tone.GREEN))
    fun proteinScale() = Scale(listOf(16.0, 20.0), listOf("Низкий", "Здоровый", "Отличный"), listOf(Tone.BLUE, Tone.GREEN, Tone.GREEN))
    fun skeletalMuscleScale(p: Person) = Scale(if (p.male) listOf(33.3, 39.4) else listOf(24.4, 30.3), listOf("Низкий", "Здоровый", "Отличный"), listOf(Tone.BLUE, Tone.GREEN, Tone.GREEN))
    fun visceralScale() = Scale(listOf(10.0, 15.0), listOf("Здоровый", "Высокий", "Очень высокий"), listOf(Tone.GREEN, Tone.YELLOW, Tone.RED))
    fun subcutaneousScale(p: Person) = Scale(if (p.male) listOf(8.6, 16.7) else listOf(18.5, 26.7), listOf("Низкий", "Здоровый", "Высокий"), listOf(Tone.BLUE, Tone.GREEN, Tone.YELLOW))

    /** Норма костной массы зависит от пола и веса. */
    fun standardBone(male: Boolean, weight: Double): Double = if (male) when {
        weight < 60 -> 2.5; weight <= 75 -> 2.9; else -> 3.2
    } else when {
        weight < 45 -> 1.8; weight <= 60 -> 2.2; else -> 2.5
    }

    fun boneScale(p: Person, weight: Double): Scale {
        val s = standardBone(p.male, weight)
        return Scale(listOf(s - 0.1, s + 0.1), listOf("Низкий", "Здоровый", "Отличный"), listOf(Tone.BLUE, Tone.GREEN, Tone.GREEN))
    }

    /** Базовый обмен по Миффлину — Сан Жеору. */
    fun estimatedBmr(p: Person, weight: Double): Double = 10 * weight + 6.25 * p.heightCm - 5 * p.age + if (p.male) 5 else -161

    fun bmrScale(p: Person, weight: Double): Scale {
        val e = estimatedBmr(p, weight)
        return Scale(listOf((e * 0.95).roundToInt().toDouble()), listOf("Ниже нормы", "Здоровый"), listOf(Tone.BLUE, Tone.GREEN))
    }

    fun metabolicAgeScale(p: Person) = Scale(listOf(p.age + 0.5), listOf("Моложе возраста", "Старше возраста"), listOf(Tone.GREEN, Tone.YELLOW))

    /** Все показатели для экрана. Производные (ИМТ, жировая и безжировая масса) считаются из измеренных. */
    fun metrics(r: BodyReading, p: Person): List<BodyMetricView> {
        val fatKg = r.fatPct?.let { r.weight * it / 100 }
        val lean = r.leanKg ?: fatKg?.let { r.weight - it }
        val muscleKg = r.muscleKg ?: r.musclePct?.let { r.weight * it / 100 }
        return listOf(
            BodyMetricView("weight", "Вес", r.weight, "кг", 1, weightScale(p.heightCm), "Вес — важный показатель здоровья. Резкие изменения за короткое время требуют внимания."),
            BodyMetricView("bmi", "ИМТ", bmi(r.weight, p.heightCm), "", 1, bmiScale(), "Индекс массы тела — масса, делённая на квадрат роста. Оценивает вес относительно роста, но не различает жир и мышцы."),
            BodyMetricView("fat", "Жир", r.fatPct, "%", 1, fatScale(p), "Доля жировой ткани в теле. Важнее веса для оценки прогресса при похудении."),
            BodyMetricView("fatKg", "Жировая масса", fatKg, "кг", 1, null, "Вес жира в килограммах: вес × процент жира."),
            BodyMetricView("skeletal", "Скелетная мускулатура", r.musclePct, "%", 1, skeletalMuscleScale(p), "Доля скелетных мышц — тех, что двигают тело. Растёт от силовых тренировок и белка."),
            BodyMetricView("muscleKg", "Мышечная масса", muscleKg, "кг", 1, null, "Масса мышц в килограммах."),
            BodyMetricView("water", "Вода", r.waterPct, "%", 1, waterScale(p), "Доля воды в теле. Зависит от времени суток и питья — сравнивайте замеры в одно время."),
            BodyMetricView("protein", "Белок", r.proteinPct, "%", 1, proteinScale(), "Доля белка — строительного материала мышц и органов."),
            BodyMetricView("bone", "Костная масса", r.boneKg, "кг", 1, boneScale(p, r.weight), "Оценка массы минералов в костях. Меняется медленно."),
            BodyMetricView("visceral", "Висцеральный жир", r.visceral, "", 0, visceralScale(), "Жир вокруг внутренних органов. Уровень до 9 — норма; выше — повод снизить."),
            BodyMetricView("subcut", "Подкожный жир", r.subcutaneousPct, "%", 1, subcutaneousScale(p), "Жир под кожей, доля от массы тела."),
            BodyMetricView("lean", "Безжировая масса", lean, "кг", 1, null, "Всё, кроме жира: мышцы, кости, вода, органы."),
            BodyMetricView("bmr", "Базовый обмен", r.bmr, "ккал", 0, bmrScale(p, r.weight), "Сколько калорий тело тратит в покое за сутки."),
            BodyMetricView("metaAge", "Метаболический возраст", r.metabolicAge, "лет", 0, metabolicAgeScale(p), "Возраст, которому соответствует ваш обмен веществ."),
        )
    }

    /** Состав тела: вес = вода + жир + белок + кость (в килограммах), если весы дали проценты. */
    data class Composition(val water: Double?, val fat: Double?, val protein: Double?, val bone: Double?)

    fun composition(r: BodyReading) = Composition(
        r.waterPct?.let { r.weight * it / 100 }, r.fatPct?.let { r.weight * it / 100 },
        r.proteinPct?.let { r.weight * it / 100 }, r.boneKg,
    )

    val BODY_TYPES = listOf(
        listOf("Телосложение спортсмена", "Мышечное ожирение", "Ожирение"),
        listOf("Мышечный тип", "Здоровый тип", "Небольшой избыточный вес"),
        listOf("Стройный мышечный тип", "Стройный тип", "Скрытое ожирение"),
    )

    /** Тип телосложения: строка — ИМТ (выше/норма/ниже), столбец — жир (мало/норма/много). */
    fun bodyType(weight: Double, fatPct: Double?, p: Person): Pair<Int, Int>? {
        fatPct ?: return null
        val b = bmi(weight, p.heightCm)
        val row = when { b >= 25 -> 0; b >= 18.5 -> 1; else -> 2 }
        val fs = fatScale(p)
        val col = when { fatPct < fs.bounds[0] -> 0; fatPct < fs.bounds[1] -> 1; else -> 2 }
        return row to col
    }

    data class Trend(val avg: Double, val change: Double, val max: Pair<Long, Double>, val min: Pair<Long, Double>, val count: Int)

    /** Средний, изменение, максимум и минимум за период. points — (день, значение), по возрастанию дня. */
    fun trend(points: List<Pair<Long, Double>>, from: Long, to: Long): Trend? {
        val p = points.filter { it.first in from..to }.sortedBy { it.first }
        if (p.isEmpty()) return null
        return Trend(p.map { it.second }.average(), p.last().second - p.first().second, p.maxBy { it.second }, p.minBy { it.second }, p.size)
    }
}
