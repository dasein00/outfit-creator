package com.dasein.poryadok.logic

import kotlin.math.roundToInt

/** Сожжённые за день калории: активность (шаги или данные Health Connect) плюс тренировки из журнала. */
object Energy {
    /**
     * Активные калории за шаги по скорости ходьбы — метод MET (Компендиум физической активности), см. [WalkEnergy].
     * [paceKmh] — обычный темп: определяется сам по минутам из Health Connect или задаётся в разделе «Шаги».
     */
    fun stepsKcal(steps: Int, weightKg: Double, heightCm: Double? = null, paceKmh: Double = WalkEnergy.DEFAULT_PACE): Int =
        WalkEnergy.kcalAtPace(steps, weightKg, heightCm, paceKmh).roundToInt()

    data class Burned(val total: Int, val activity: Int, val workouts: Int, val fromHealthConnect: Boolean)

    /**
     * Если Health Connect дал активные калории — берём большее из них и оценки по шагам
     * (часы и браслеты считают точнее), затем прибавляем тренировки, записанные в приложении.
     */
    fun burned(
        steps: Int, weightKg: Double, workoutsKcal: Int, hcActiveKcal: Int?,
        heightCm: Double? = null, paceKmh: Double = WalkEnergy.DEFAULT_PACE,
    ): Burned {
        val est = stepsKcal(steps, weightKg, heightCm, paceKmh)
        val act = maxOf(est, hcActiveKcal ?: 0)
        return Burned(act + workoutsKcal, act, workoutsKcal, hcActiveKcal != null && hcActiveKcal >= est)
    }
}
