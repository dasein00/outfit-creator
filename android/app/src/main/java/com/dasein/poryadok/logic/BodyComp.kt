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
        val b = if (p.male) listOf(11.0, 17.0, 27.0) else listOf(21.0, 31.0, 36.0) // мужские границы — как у весов OKOK
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
        val skeletalKg = r.musclePct?.let { r.weight * it / 100 }
        val waterKg = r.waterPct?.let { r.weight * it / 100 }
        val list = listOf(
            BodyMetricView("weight", "Вес", r.weight, "кг", 1, weightScale(p.heightCm), "Вес — важный показатель здоровья. Резкие изменения за короткое время требуют внимания. Сравнивайте взвешивания в одно время суток — лучше утром натощак."),
            BodyMetricView("bmi", "ИМТ", bmi(r.weight, p.heightCm), "", 1, bmiScale(), "Индекс массы тела — масса, делённая на квадрат роста. Оценивает вес относительно роста, но не различает жир и мышцы: у спортсменов он бывает завышен."),
            BodyMetricView("fat", "Жир", r.fatPct, "%", 1, fatScale(p), "Процент жира — доля всех жировых тканей (подкожного и внутреннего жира) в массе тела. По сравнению с ИМТ он точнее показывает, «полный» человек или «худой», и важнее веса для оценки прогресса."),
            BodyMetricView("fatKg", "Жировая масса", fatKg, "кг", 1, null, "Вес жира в килограммах: вес × процент жира. При правильном похудении уменьшается быстрее, чем общий вес."),
            BodyMetricView("skeletal", "Скелетная мускулатура", r.musclePct, "%", 1, skeletalMuscleScale(p), "Скелетные мышцы — это произвольные мышцы туловища и конечностей, прикреплённые к костям. Чем их больше, тем выше базовый обмен: тело тратит больше энергии даже в покое. Достаточная мышечная масса поддерживает кровообращение, помогает сжигать жир и снижает риск болезней."),
            BodyMetricView("skeletalKg", "Скелетная мышечная масса", skeletalKg, "кг", 1, null, "Масса скелетных мышц в килограммах. Растёт от силовых тренировок и достаточного количества белка."),
            BodyMetricView("muscleRate", "Доля мышц", muscleKg?.let { it / r.weight * 100 }, "%", 1, null, "Доля всей мышечной массы (включая мышцы органов) в весе тела. Диапазон мышц определяет физическое здоровье и силу."),
            BodyMetricView("muscleKg", "Мышечная масса", muscleKg, "кг", 1, null, "Масса всех мышц в килограммах, включая гладкие мышцы органов и сердце."),
            BodyMetricView("water", "Вода", r.waterPct, "%", 1, waterScale(p), "Процент воды в организме напрямую связан с мышечной массой: мышцы примерно на 70% состоят из воды. Зависит от времени суток и питья — сравнивайте замеры в одно время."),
            BodyMetricView("waterKg", "Вес воды", waterKg, "кг", 1, null, "Масса воды в теле в килограммах."),
            BodyMetricView("visceral", "Висцеральный жир", r.visceral, "", 0, visceralScale(), "Жир, который скапливается в брюшной полости вокруг внутренних органов. Он опаснее подкожного: связан с диабетом, болезнями сердца и давлением. Уровень до 9 — норма."),
            BodyMetricView("bone", "Костная масса", r.boneKg, "кг", 1, boneScale(p, r.weight), "Масса костных минералов в скелете. Растёт до 20–30 лет, затем постепенно снижается. Поддерживают её силовые нагрузки, кальций, витамин D и белок; достаточная костная масса снижает риск переломов и остеопороза."),
            BodyMetricView("bmr", "Базовый обмен", r.bmr, "ккал", 0, bmrScale(p, r.weight), "Базовый обмен (BMR) — минимум калорий, который нужен телу в покое на дыхание, кровообращение, температуру и восстановление клеток. Основа для расчёта суточной нормы; растёт вместе с мышечной массой."),
            BodyMetricView("protein", "Белок", r.proteinPct, "%", 1, proteinScale(), "Доля белка в массе тела. Белок — строительный материал мышц, кожи, органов и ферментов. Поддерживайте его едой, особенно при тренировках."),
            BodyMetricView("obesity", "Тучность", obesityPct(r.weight, p), "%", 1, obesityScale(), "Степень ожирения — насколько вес выше идеального: (вес − идеальный вес) / идеальный вес × 100%. Идеальный вес для вашего роста — ${"%.1f".format(idealWeight(p)).replace('.', ',')} кг."),
            BodyMetricView("subcut", "Подкожный жир", r.subcutaneousPct, "%", 1, subcutaneousScale(p), "Жир под кожей, доля от массы тела. Менее опасен, чем висцеральный, но его избыток тоже нагружает организм."),
            BodyMetricView("metaAge", "Метаболический возраст", r.metabolicAge, "лет", 0, metabolicAgeScale(p), "Возраст, которому соответствует ваш базовый обмен. Если он ниже фактического — обмен веществ «моложе» вас; снижают его мышцы и активность."),
            BodyMetricView("lean", "Безжировая масса (LBM)", lean, "кг", 1, null, "Вес тела без жира: мышцы, кости, вода и органы. Чем больше развиты мышцы, тем лучше физическая форма."),
            BodyMetricView("age", "Фактический возраст", p.age.toDouble(), "лет", 0, null, "Хронологический возраст — сколько лет прошло с рождения. Самый объективный показатель; задаётся в профиле калькулятора."),
            BodyMetricView("height", "Рост", p.heightCm, "см", 0, null, "Рост измеряют стоя, босиком, пятки вместе, взгляд прямо. Задаётся в профиле калькулятора."),
        )
        return list
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

    /** Идеальный вес по формуле, которой пользуются весы OKOK: (рост − 80) × 0,7 у мужчин, (рост − 70) × 0,6 у женщин. */
    fun idealWeight(p: Person): Double = if (p.male) (p.heightCm - 80) * 0.7 else (p.heightCm - 70) * 0.6

    /** Тучность: насколько вес выше идеального, в процентах. */
    fun obesityPct(weight: Double, p: Person): Double = (weight - idealWeight(p)) / idealWeight(p) * 100

    fun obesityScale() = Scale(
        listOf(10.0, 20.0, 30.0, 50.0),
        listOf("Норма", "Незначительная", "Умеренная", "Значительная", "Избыточная"),
        listOf(Tone.GREEN, Tone.YELLOW, Tone.YELLOW, Tone.RED, Tone.RED),
    )

    /** Верхняя граница здорового процента жира (зелёная зона). */
    fun healthyFatTop(p: Person): Double = fatScale(p).bounds[1]

    /** Рекомендации по контролю веса: сколько изменить вес, жир и мышцы, чтобы прийти к идеальному весу со здоровым процентом жира. */
    data class Advice(val ideal: Double, val weightDelta: Double, val fatDelta: Double?, val muscleDelta: Double?)

    fun advice(r: BodyReading, p: Person): Advice {
        val ideal = idealWeight(p)
        val fatKg = r.fatPct?.let { r.weight * it / 100 }
        val targetFat = ideal * healthyFatTop(p) / 100
        val muscle = r.muscleKg ?: r.musclePct?.let { r.weight * it / 100 }
        val bone = r.boneKg ?: standardBone(p.male, ideal)
        val targetMuscle = ideal - targetFat - bone
        return Advice(ideal, ideal - r.weight, fatKg?.let { targetFat - it }, muscle?.let { targetMuscle - it })
    }

    /** Описание типа телосложения и что с ним делать. */
    fun typeAbout(row: Int, col: Int): String = when (row to col) {
        0 to 0 -> "Высокий ИМТ за счёт мышц, а не жира. Это вес спортсмена: поддерживайте силовые тренировки и достаточное количество белка."
        0 to 1 -> "Мышц много, но и жира больше нормы. Сохраните силовые тренировки и добавьте небольшой дефицит калорий, чтобы убрать жир без потери мышц."
        0 to 2 -> "Избыток и веса, и жира. Начните с регулярной ходьбы и силовых 2–3 раза в неделю, следите за рационом, меньше соли, сахара и жирного, высыпайтесь. Даже 5–10% снижения веса заметно улучшают здоровье."
        1 to 0 -> "Нормальный вес при низком проценте жира — хорошо развитые мышцы. Продолжайте в том же духе."
        1 to 1 -> "Вес и процент жира в норме. Поддерживайте активность и разнообразное питание."
        1 to 2 -> "Вес в норме, но жира больше, чем нужно. Силовые тренировки помогут заменить жир мышцами, не обязательно худеть по весу."
        2 to 0 -> "Стройное тело с хорошей долей мышц. Следите, чтобы калорий хватало."
        2 to 1 -> "Вес ниже нормы при нормальном проценте жира. Добавьте силовые тренировки и калорийности рациона, чтобы набрать мышцы."
        else -> "Вес низкий, а доля жира высокая — мышц мало. Нужны силовые тренировки и достаточно белка; худеть не стоит."
    }

    /** Строка сравнения двух замеров. goodDown — снижение показателя считается улучшением. */
    data class Diff(val key: String, val title: String, val before: Double?, val after: Double?, val decimals: Int, val goodDown: Boolean?) {
        val delta: Double? get() = if (before != null && after != null) after - before else null
        /** true — изменение в лучшую сторону, false — в худшую, null — без оценки или без изменений. */
        val better: Boolean? get() {
            val d = delta ?: return null
            if (goodDown == null || kotlin.math.abs(d) < 1e-9) return null
            return if (goodDown) d < 0 else d > 0
        }
    }

    fun compare(a: BodyReading, b: BodyReading, p: Person): List<Diff> {
        val va = metrics(a, p).associateBy { it.key }
        val vb = metrics(b, p).associateBy { it.key }
        val goodDown = mapOf(
            "weight" to true, "bmi" to true, "fat" to true, "fatKg" to true, "visceral" to true, "subcut" to true, "obesity" to true,
            "skeletal" to false, "skeletalKg" to false, "muscleKg" to false, "muscleRate" to false, "water" to false, "waterKg" to false,
            "protein" to false, "bone" to false, "lean" to false, "bmr" to false, "metaAge" to true,
        )
        return va.keys.filter { it != "age" && it != "height" }.map { k ->
            val x = va.getValue(k)
            Diff(k, x.title + if (x.unit.isNotBlank()) " (${x.unit})" else "", x.value, vb[k]?.value, x.decimals.coerceAtLeast(1), goodDown[k])
        }.filter { it.before != null || it.after != null }
    }

    data class Trend(val avg: Double, val change: Double, val max: Pair<Long, Double>, val min: Pair<Long, Double>, val count: Int)

    /** Средний, изменение, максимум и минимум за период. points — (день, значение), по возрастанию дня. */
    fun trend(points: List<Pair<Long, Double>>, from: Long, to: Long): Trend? {
        val p = points.filter { it.first in from..to }.sortedBy { it.first }
        if (p.isEmpty()) return null
        return Trend(p.map { it.second }.average(), p.last().second - p.first().second, p.maxBy { it.second }, p.minBy { it.second }, p.size)
    }
}
