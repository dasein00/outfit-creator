package com.dasein.poryadok.logic

import kotlin.math.abs
import kotlin.math.roundToInt

enum class CalorieGoal(val label: String) { DEFICIT("Дефицит"), MAINTAIN("Поддержание"), SURPLUS("Профицит") }
enum class Intensity(val label: String) { SOFT("Мягко"), STANDARD("Стандартно"), HARD("Жёстко") }

val ACTIVITY_LEVELS = listOf(
    1.2 to "Сидячий образ жизни",
    1.375 to "Лёгкая активность, 1–3 тренировки в неделю",
    1.55 to "Средняя активность, 3–5 тренировок в неделю",
    1.725 to "Высокая активность, 6–7 тренировок в неделю",
    1.9 to "Очень высокая активность, физическая работа",
)

data class BodyInput(
    val male: Boolean,
    val heightCm: Double,
    val age: Int,
    val weightKg: Double,
    val activity: Double,
    val goal: CalorieGoal,
    val intensity: Intensity,
    val proteinPerKg: Double,
    val fatPerKg: Double,
)

data class NutritionPlan(
    val bmr: Double,
    val tdee: Double,
    val deltaKcal: Double,
    val targetKcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val realisticKgPer12Weeks: Double,
)

/** Та же модель, что в «Дневнике прогресса на 3 месяца»: Миффлин — Сан Жеор × активность ± % от нормы. */
object Nutrition {
    fun bmr(male: Boolean, heightCm: Double, age: Int, weightKg: Double): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + if (male) 5 else -161

    fun percent(goal: CalorieGoal, intensity: Intensity): Double = when (goal) {
        CalorieGoal.DEFICIT -> when (intensity) { Intensity.SOFT -> .15; Intensity.STANDARD -> .20; Intensity.HARD -> .25 }
        CalorieGoal.SURPLUS -> when (intensity) { Intensity.SOFT -> .08; Intensity.STANDARD -> .12; Intensity.HARD -> .16 }
        CalorieGoal.MAINTAIN -> 0.0
    }

    fun plan(i: BodyInput): NutritionPlan {
        val b = bmr(i.male, i.heightCm, i.age, i.weightKg)
        val tdee = b * i.activity
        val sign = when (i.goal) { CalorieGoal.DEFICIT -> -1; CalorieGoal.SURPLUS -> 1; CalorieGoal.MAINTAIN -> 0 }
        val delta = tdee * percent(i.goal, i.intensity) * sign
        val target = tdee + delta
        val p = (i.weightKg * i.proteinPerKg).roundToInt()
        val f = (i.weightKg * i.fatPerKg).roundToInt()
        val c = ((target - p * 4 - f * 9) / 4).roundToInt().coerceAtLeast(0)
        return NutritionPlan(b, tdee, delta, target.roundToInt(), p, f, c, abs(delta) * 84 / 7700)
    }

    /** Плановый вес на неделе [week] (0..12) при линейном движении к цели. */
    fun plannedWeight(start: Double, changeKg: Double, goal: CalorieGoal, week: Int): Double {
        val sign = when (goal) { CalorieGoal.DEFICIT -> -1; CalorieGoal.SURPLUS -> 1; CalorieGoal.MAINTAIN -> 0 }
        return start + sign * abs(changeKg) * week.coerceIn(0, 12) / 12.0
    }

    enum class Level { OK, WARN, BAD }

    /** Как в таблице: зелёный 90–110 %, жёлтый — умеренное отклонение, красный — сильное. */
    fun level(pct: Int): Level = when {
        pct in 90..110 -> Level.OK
        pct in 70..89 || pct in 111..130 -> Level.WARN
        else -> Level.BAD
    }

    fun waterGoalMl(weightKg: Double): Int = ((weightKg * 30) / 50).roundToInt() * 50
}
