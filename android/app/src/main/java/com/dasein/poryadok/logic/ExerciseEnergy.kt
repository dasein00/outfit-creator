package com.dasein.poryadok.logic

import kotlin.math.roundToInt

/**
 * Примерный расход калорий на сами упражнения — сверх расчёта по времени тренировки.
 *
 * Повторения: механическая работа подъёма груза (свой вес тела в доле, которую двигает упражнение, + штанга,
 * гантели или утяжелители) на высоту амплитуды, делённая на КПД мышц (~20 %); опускание груза добавляет ещё треть.
 * Упражнения на время: MET упражнения × (вес тела + утяжелители + вес в подходе или гантели) × время.
 * Профиль упражнения (доля веса тела, амплитуда, MET) угадывается по названию, иначе — по группе мышц.
 */
object ExerciseEnergy {
    const val KIND_WEIGHT = 0
    const val KIND_REPS = 1
    const val KIND_TIME = 2

    /** Что двигает упражнение: доля веса тела, амплитуда в метрах, MET для упражнений на время. */
    data class Profile(val bodyShare: Double, val rangeM: Double, val met: Double)

    /** Инвентарь тренировки: вес одной гантели и общий вес утяжелителей (кг). Спрашивается в начале тренировки. */
    data class Gear(val dumbbellKg: Double = 0.0, val wearKg: Double = 0.0)

    /** Подход: повторы, вес из записи (0 — не указан) и секунды. */
    data class SetWork(val reps: Int, val weightKg: Double, val seconds: Int)

    private const val G = 9.81
    private const val EFFICIENCY = 0.2
    private const val ECCENTRIC = 1.33
    private const val J_PER_KCAL = 4184.0

    // Порядок важен: сначала более узкие названия («отжимания на брусьях» раньше «отжиманий»).
    private val BY_NAME: List<Pair<List<String>, Profile>> = listOf(
        listOf("бёрпи", "берпи", "burpee") to Profile(1.0, 1.0, 8.0),
        listOf("прыж", "выпрыг", "jump") to Profile(1.0, 0.5, 8.0),
        listOf("скакал") to Profile(1.0, 0.1, 11.0),
        listOf("скалолаз") to Profile(0.5, 0.3, 8.0),
        listOf("бег", "бежк", "run") to Profile(1.0, 0.1, 9.0),
        listOf("вело", "байк") to Profile(0.3, 0.3, 7.0),
        listOf("гребл", "эллипс") to Profile(0.3, 0.5, 7.0),
        listOf("брус") to Profile(0.9, 0.35, 5.0),
        listOf("австрал") to Profile(0.6, 0.35, 4.0),
        listOf("подтяг", "pull-up", "pullup") to Profile(0.95, 0.45, 5.0),
        listOf("отжим", "push-up", "pushup") to Profile(0.65, 0.3, 4.0),
        listOf("жим ног", "жим платформ") to Profile(0.0, 0.4, 5.0),
        listOf("присед", "squat") to Profile(0.7, 0.5, 5.0),
        listOf("выпад", "lunge", "зашагив") to Profile(0.7, 0.5, 5.0),
        listOf("станов", "deadlift") to Profile(0.5, 0.5, 5.0),
        listOf("румын") to Profile(0.45, 0.45, 5.0),
        listOf("мост", "bridge", "thrust") to Profile(0.4, 0.25, 3.5),
        listOf("носк", "икр", "calf") to Profile(1.0, 0.08, 3.5),
        listOf("гир", "kettlebell", "swing") to Profile(0.3, 0.6, 8.0),
        listOf("скруч", "crunch", "пресс") to Profile(0.3, 0.2, 3.8),
        listOf("подъём ног", "подъемы ног", "подъёмы ног", "подъем ног") to Profile(0.35, 0.4, 3.8),
        listOf("планк", "plank") to Profile(0.0, 0.0, 3.8),
        listOf("стульчик", "у стены") to Profile(0.0, 0.0, 4.0),
        listOf("мах") to Profile(0.04, 0.5, 3.5),
        listOf("сгибан", "бицепс", "curl") to Profile(0.03, 0.35, 3.0),
        listOf("разгибан", "трицепс", "француз") to Profile(0.03, 0.3, 3.0),
        listOf("тяга") to Profile(0.05, 0.4, 4.0),
        listOf("жим") to Profile(0.05, 0.4, 4.0),
        listOf("растяж", "йог", "stretch") to Profile(0.0, 0.0, 2.5),
    )

    private val BY_MUSCLE: Map<String, Profile> = mapOf(
        "ноги" to Profile(0.6, 0.45, 5.0),
        "ягодицы" to Profile(0.4, 0.3, 4.0),
        "спина" to Profile(0.3, 0.4, 4.0),
        "грудь" to Profile(0.4, 0.3, 4.0),
        "плечи" to Profile(0.05, 0.45, 3.5),
        "руки" to Profile(0.03, 0.35, 3.0),
        "пресс" to Profile(0.3, 0.25, 3.8),
        "всё тело" to Profile(0.7, 0.6, 8.0),
        "все тело" to Profile(0.7, 0.6, 8.0),
        "кардио" to Profile(0.5, 0.3, 8.0),
    )

    private val DEFAULT = Profile(0.3, 0.4, 4.0)

    fun profile(name: String, muscle: String): Profile {
        val n = name.lowercase()
        BY_NAME.firstOrNull { (keys, _) -> keys.any { it in n } }?.let { return it.second }
        return BY_MUSCLE[muscle.trim().lowercase()] ?: DEFAULT
    }

    /** Сколько гантелей в упражнении: «с гантелями» — две, «гантели одной рукой», «с гантелью» — одна, без гантелей — 0. */
    fun dumbbells(name: String, equipment: String): Int {
        val n = name.lowercase()
        val e = equipment.lowercase()
        if ("гантел" !in n && "гантел" !in e) return 0
        val words = n.split(Regex("[^\\p{L}]+")).toSet()
        val single = "одной" in words || "гантелью" in words || "гантель" in words || e.trim() == "гантель"
        return if (single) 1 else 2
    }

    /** Ккал за один выполненный подход. */
    fun setKcal(
        name: String, muscle: String, equipment: String, kind: Int, set: SetWork, bodyKg: Double, gear: Gear,
    ): Double {
        val p = profile(name, muscle)
        val body = bodyKg.coerceIn(30.0, 250.0)
        val wear = gear.wearKg.coerceIn(0.0, 50.0)
        // Вес из подхода важнее; если не указан, а упражнение с гантелями — вес гантелей из начала тренировки.
        val load = when {
            set.weightKg > 0 -> set.weightKg.coerceAtMost(500.0)
            else -> gear.dumbbellKg.coerceIn(0.0, 100.0) * dumbbells(name, equipment)
        }
        if (kind == KIND_TIME) {
            // Удержание (планка, стульчик, вис): расход растёт со временем и со всем весом, который держит тело,
            // — свой вес, утяжелители, блин на спине или гантели в руках.
            val sec = set.seconds.coerceIn(0, 4 * 3600)
            return p.met * (body + wear + load) * sec / 3600.0
        }
        val reps = set.reps.coerceIn(0, 500)
        if (reps == 0) return 0.0
        val range = if (p.rangeM > 0) p.rangeM else 0.3
        val mass = p.bodyShare * body + load + wear
        val joules = mass * G * range * reps * ECCENTRIC / EFFICIENCY
        return joules / J_PER_KCAL
    }

    /** Итог по тренировке, округлённый до целых ккал. */
    fun total(items: List<Double>): Int = items.sum().roundToInt()
}
