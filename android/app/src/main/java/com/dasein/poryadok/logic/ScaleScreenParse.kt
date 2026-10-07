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
    ) {
        val found get() = listOf(weight, fatPct, musclePct, muscleKg, waterPct, proteinPct, boneKg, visceral, bmr, metabolicAge, subcutaneousPct).count { it != null }
    }

    private val LOOK = mapOf(
        'а' to 'a', 'в' to 'b', 'е' to 'e', 'ё' to 'e', 'к' to 'k', 'м' to 'm', 'н' to 'h', 'о' to 'o', 'р' to 'p', 'с' to 'c',
        'т' to 't', 'у' to 'y', 'х' to 'x', 'и' to 'u', 'й' to 'u', 'з' to '3', 'п' to 'n', 'л' to 'n', 'ь' to 'b', 'ы' to 'b', 'г' to 'r',
    )

    /** Нижний регистр и «похожие» латинские буквы вместо кириллицы — чтобы «Вес» совпал и с «Вес», и с «Bec». */
    fun look(s: String): String = s.lowercase().map { LOOK[it] ?: it }.joinToString("")

    private enum class Unit { KG, PCT, KCAL, NONE }

    private data class Num(val value: Double, val unit: Unit, val label: String)

    private val NUM = Regex("""(\d{1,4}(?:[.,]\d{1,2})?)\s*(kg|кг|kr|%|kcal|ккал|kkan|kkal|кал|cal)?""", RegexOption.IGNORE_CASE)

    private fun unitOf(u: String?): Unit = when (u?.lowercase()) {
        null, "" -> Unit.NONE
        "%" -> Unit.PCT
        "kg", "кг", "kr" -> Unit.KG
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

    fun parse(lines: List<String>): Result {
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
