package com.dasein.poryadok.logic

/**
 * Разбор текста со скриншота приложения весов (Fitdays и похожих): строки, распознанные на телефоне,
 * → показатели взвешивания. Подписи ищутся на английском и русском; русские буквы, похожие на латинские
 * (В, Е, К, М, Н, О, Р, С, Т, Х, А), распознаются как латинские — поэтому ключи сравниваются в «похожем» виде.
 * Если подписи не нашлись, показатели угадываются по единицам и правдоподобным диапазонам.
 * Результат всегда показывается в форме для проверки перед сохранением.
 */
object ScaleScreenParse {
    data class Result(
        val weight: Double? = null,
        val bmi: Double? = null,
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
        /** Время замера из шапки отчёта (мс), если распознано. */
        val at: Long? = null,
        val heartRate: Double? = null,
        val idealWeight: Double? = null,
    ) {
        val found get() = listOf(weight, fatPct, musclePct, muscleKg, waterPct, proteinPct, boneKg, visceral, bmr, metabolicAge, subcutaneousPct).count { it != null }
    }

    private val LOOK = mapOf(
        'а' to 'a', 'в' to 'b', 'е' to 'e', 'ё' to 'e', 'к' to 'k', 'м' to 'm', 'н' to 'h', 'о' to 'o', 'р' to 'p', 'с' to 'c',
        'т' to 't', 'у' to 'y', 'х' to 'x', 'и' to 'u', 'й' to 'u', 'з' to '3', 'п' to 'n', 'л' to 'n', 'ь' to 'b', 'ы' to 'b', 'г' to 'r',
    )

    /** Нижний регистр и «похожие» латинские буквы вместо кириллицы — чтобы «Вес» совпал и с «Вес», и с «Bec». */
    fun look(s: String): String = s.lowercase().map { LOOK[it] ?: it }.joinToString("")

    private enum class Unit { KG, PCT, KCAL, BPM, NONE }

    private data class Num(val value: Double, val unit: Unit, val label: String)

    /** Отдельно стоящее число (не внутри слова: «6e3», «Bo3pacT» — это буквы, распознанные цифрами) и единица после него. */
    private val NUM = Regex("""(?<![\p{L}\d.,])(\d{1,4}(?:[.,]\d{1,2})?)\s*(kg|кг|kr|%|kcal|ккал|kkan|kkal|кал|cal|bpm)?(?![\p{L}\d]|[.,]\d)""", RegexOption.IGNORE_CASE)

    private fun unitOf(u: String?): Unit = when (u?.lowercase()) {
        null, "" -> Unit.NONE
        "%" -> Unit.PCT
        "kg", "кг", "kr" -> Unit.KG
        "bpm" -> Unit.BPM
        else -> Unit.KCAL
    }

    /** Ключи подписей. Английские — как в Fitdays на английском, русские — как в русском интерфейсе. */
    private val KEYS = mapOf(
        "weight" to listOf("weight", "вес"),
        "bmi" to listOf("bmi", "имт", "индекс массы"),
        "subcut" to listOf("subcutaneous", "подкож"),
        "visceral" to listOf("visceral", "висцер", "внутренн"),
        "fat" to listOf("body fat", "fat rate", "fat %", "fat", "жир"),
        "lean" to listOf("fat-free", "fat free", "lean", "без жира", "безжир"),
        "musclePct" to listOf("skeletal", "скелет"),
        "muscleKg" to listOf("muscle mass", "muscle", "мышеч", "мышц"),
        "water" to listOf("water", "moisture", "вода", "воды", "влаг"),
        "protein" to listOf("protein", "белок", "белк", "протеин"),
        "bone" to listOf("bone", "кост"),
        "bmr" to listOf("bmr", "basal", "metabolic rate", "обмен", "метаболизм"),
        "age" to listOf("body age", "metabolic age", "age", "возраст"),
    )

    private fun keyOf(label: String): String? {
        val l = look(label)
        // Порядок важен: «подкожный жир» и «висцеральный жир» раньше просто «жира», «метаболический возраст» — раньше «обмена».
        val order = listOf("subcut", "visceral", "lean", "musclePct", "age", "bmr", "fat", "muscleKg", "water", "protein", "bone", "bmi", "weight")
        return order.firstOrNull { k -> KEYS.getValue(k).any { look(it) in l } }
    }

    private fun parseNum(s: String) = s.replace(',', '.').toDoubleOrNull()

    /** Числа с подписями: подпись — текст в той же строке перед числом, а если его нет — предыдущая строка. */
    private fun numbers(lines: List<String>): List<Num> {
        val out = mutableListOf<Num>()
        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            NUM.findAll(line).forEach { m ->
                val v = parseNum(m.groupValues[1]) ?: return@forEach
                val before = line.substring(0, m.range.first).trim()
                val label = before.ifBlank { lines.getOrNull(i - 1)?.takeIf { p -> NUM.find(p) == null }.orEmpty() }
                out += Num(v, unitOf(m.groupValues[2]), label)
            }
        }
        // Время и даты (12:30, 07.10.2026) не показатели.
        return out.filter { !(it.unit == Unit.NONE && it.value > 2100) }
    }

    /** Строка отчёта Fitdays: показатель, единица, допустимые значения. */
    private class Slot(val key: String, val unit: Unit, val range: ClosedFloatingPointRange<Double>)

    /** Порядок строк в отчёте Fitdays («Поделиться» результатом взвешивания). */
    private val FITDAYS = listOf(
        Slot("weight", Unit.KG, 20.0..300.0), Slot("bmi", Unit.NONE, 10.0..70.0), Slot("fat", Unit.PCT, 2.0..70.0),
        Slot("fatKg", Unit.KG, 1.0..200.0), Slot("lean", Unit.KG, 15.0..200.0), Slot("hr", Unit.BPM, 30.0..220.0),
        Slot("cardiac", Unit.NONE, 0.5..10.0), Slot("muscleKg", Unit.KG, 10.0..150.0), Slot("muscleRate", Unit.PCT, 20.0..95.0),
        Slot("musclePct", Unit.PCT, 15.0..75.0), Slot("bone", Unit.KG, 0.5..8.0), Slot("proteinKg", Unit.KG, 2.0..40.0),
        Slot("protein", Unit.PCT, 5.0..35.0), Slot("waterKg", Unit.KG, 10.0..120.0), Slot("water", Unit.PCT, 20.0..80.0),
        Slot("subcut", Unit.PCT, 2.0..60.0), Slot("visceral", Unit.NONE, 1.0..40.0), Slot("bmr", Unit.KCAL, 700.0..4000.0),
        Slot("age", Unit.NONE, 10.0..110.0), Slot("ideal", Unit.KG, 20.0..200.0),
    )

    private fun fits(n: Num, s: Slot) = n.value in s.range && (n.unit == s.unit || n.unit == Unit.NONE)

    /**
     * Отчёт Fitdays: русские подписи распознаются плохо, зато строки всегда в одном порядке, а числа — с единицами.
     * Раскладываем числа по строкам по порядку (пропуская лишнее и отсутствующие строки) и выбираем вариант,
     * где сходится «вес = масса жира + масса без жира» и «жир % = масса жира / вес».
     */
    fun parseFitdays(lines: List<String>): Map<String, Double> {
        val nums = numbers(lines.filter { !Regex("""\d{1,2}:\d{2}""").containsMatchIn(it) && !Regex("""\d{1,2}[./-]\d{1,2}[./-]\d{2,4}""").containsMatchIn(it) })
        var best: Map<String, Double> = emptyMap()
        var bestScore = -1.0
        var bestConsistent = false
        nums.indices.filter { fits(nums[it], FITDAYS[0]) }.forEach { start ->
            val out = LinkedHashMap<String, Double>()
            var slot = 0
            for (i in start until nums.size) {
                if (slot >= FITDAYS.size) break
                // Число встаёт в ближайшую подходящую строку (строки «пульс», «сердечный индекс» и др. могут отсутствовать).
                val j = (slot until minOf(FITDAYS.size, slot + 4)).firstOrNull { fits(nums[i], FITDAYS[it]) } ?: continue
                out[FITDAYS[j].key] = nums[i].value
                slot = j + 1
            }
            var score = out.size.toDouble()
            val w = out["weight"]; val fk = out["fatKg"]; val lean = out["lean"]; val fp = out["fat"]
            val consistent = w != null && fk != null && lean != null && kotlin.math.abs(fk + lean - w) <= 0.4
            if (consistent) score += 5
            if (w != null && fk != null && fp != null && kotlin.math.abs(fk / w * 100 - fp) <= 1.0) score += 3
            if (score > bestScore) { bestScore = score; best = out; bestConsistent = consistent }
        }
        // Это отчёт Fitdays, только если сошлось «масса жира + масса без жира = вес» — иначе разбираем по подписям.
        return if (bestConsistent && best.size >= 6) best else emptyMap()
    }

    /** Время из шапки отчёта: «06:31 08/10/2026» (день/месяц/год). */
    fun timeOf(lines: List<String>): Long? {
        val m = lines.firstNotNullOfOrNull { Regex("""(\d{1,2}):(\d{2})\D{0,3}(\d{1,2})[./-](\d{1,2})[./-](\d{4})""").find(it) } ?: return null
        val (h, mi, d, mo, y) = m.destructured
        return runCatching {
            java.time.LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    fun parse(lines: List<String>): Result {
        val f = parseFitdays(lines)
        val generic = parseGeneric(lines)
        if (f.isEmpty()) return generic.copy(at = timeOf(lines))
        return Result(
            weight = f["weight"] ?: generic.weight, bmi = f["bmi"] ?: generic.bmi, fatPct = f["fat"] ?: generic.fatPct,
            musclePct = f["musclePct"], muscleKg = f["muscleKg"], waterPct = f["water"], proteinPct = f["protein"],
            boneKg = f["bone"], visceral = f["visceral"], bmr = f["bmr"], metabolicAge = f["age"], subcutaneousPct = f["subcut"],
            leanKg = f["lean"], at = timeOf(lines), heartRate = f["hr"], idealWeight = f["ideal"],
        )
    }

    private fun parseGeneric(lines: List<String>): Result {
        val nums = numbers(lines.filter { !Regex("""\d{1,2}:\d{2}""").containsMatchIn(it) && !Regex("""\d{2}[./-]\d{2}[./-]\d{2,4}""").containsMatchIn(it) })
        val byKey = LinkedHashMap<String, Num>()
        nums.forEach { n -> keyOf(n.label)?.let { k -> if (k !in byKey) byKey[k] = n } }

        fun v(k: String, ok: (Num) -> Boolean): Double? = byKey[k]?.takeIf(ok)?.value
        var weight = v("weight") { it.value in 20.0..300.0 && it.unit != Unit.PCT }
        var fat = v("fat") { it.value in 2.0..70.0 && it.unit != Unit.KG }
        var water = v("water") { it.value in 20.0..80.0 && it.unit != Unit.KG }
        var protein = v("protein") { it.value in 5.0..35.0 && it.unit != Unit.KG }
        var bone = v("bone") { it.value in 0.5..8.0 }
        var bmr = v("bmr") { it.value in 700.0..4000.0 }
        val muscleKg = v("muscleKg") { it.unit != Unit.PCT && it.value in 10.0..150.0 }
        val musclePct = v("musclePct") { it.unit != Unit.KG && it.value in 15.0..75.0 }
        val visceral = v("visceral") { it.value in 1.0..40.0 && it.unit == Unit.NONE }
        val age = v("age") { it.value in 10.0..110.0 && it.unit == Unit.NONE }
        val subcut = v("subcut") { it.value in 2.0..60.0 && it.unit != Unit.KG }
        val lean = v("lean") { it.unit != Unit.PCT && it.value in 15.0..200.0 }
        val bmi = v("bmi") { it.value in 10.0..70.0 && it.unit == Unit.NONE }

        // Без подписей: по единицам и диапазонам.
        val used = byKey.values.toSet()
        val free = nums.filter { it !in used }
        if (weight == null) weight = free.filter { it.unit == Unit.KG && it.value in 20.0..300.0 }.maxByOrNull { it.value }?.value
        if (bmr == null) bmr = free.firstOrNull { it.unit == Unit.KCAL && it.value in 700.0..4000.0 }?.value
        if (bone == null) bone = free.firstOrNull { it.unit == Unit.KG && it.value in 0.5..8.0 }?.value
        val pcts = free.filter { it.unit == Unit.PCT }.map { it.value }
        if (fat == null) fat = pcts.firstOrNull { it in 3.0..60.0 }
        if (water == null) water = pcts.firstOrNull { it in 35.0..80.0 && it != fat }
        if (protein == null) protein = pcts.firstOrNull { it in 8.0..30.0 && it != fat }
        return Result(weight, bmi, fat, musclePct, muscleKg, water, protein, bone, visceral, bmr, age, subcut, lean)
    }
}
