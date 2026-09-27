package com.dasein.poryadok.logic

import kotlin.math.roundToInt

/** Сожжённые за день калории: активность (шаги или данные Health Connect) плюс тренировки из журнала. */
object Energy {
    /** Около 0,04 ккал на шаг при весе 70 кг; пропорционально весу. */
    fun stepsKcal(steps: Int, weightKg: Double): Int = (steps * weightKg * 0.00057).roundToInt()

    data class Burned(val total: Int, val activity: Int, val workouts: Int, val fromHealthConnect: Boolean)

    /**
     * Если Health Connect дал активные калории — берём большее из них и оценки по шагам
     * (часы и браслеты считают точнее), затем прибавляем тренировки, записанные в приложении.
     */
    fun burned(steps: Int, weightKg: Double, workoutsKcal: Int, hcActiveKcal: Int?): Burned {
        val est = stepsKcal(steps, weightKg)
        val act = maxOf(est, hcActiveKcal ?: 0)
        return Burned(act + workoutsKcal, act, workoutsKcal, hcActiveKcal != null && hcActiveKcal >= est)
    }
}
