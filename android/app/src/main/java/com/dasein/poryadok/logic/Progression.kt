package com.dasein.poryadok.logic

import kotlin.math.roundToInt

/** Цель упражнения на тренировку (чистая модель, без базы). kind: 0 — вес×повторы, 1 — повторы, 2 — время. */
data class Target(
    val kind: Int,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val seconds: Int,
    val repMin: Int,
    val repMax: Int,
    val weightStep: Double,
    val maxSets: Int,
)

/** Фактический подход. */
data class DoneSet(val reps: Int, val weight: Double, val seconds: Int, val done: Boolean)

/** Новая цель и объяснение, почему она такая. */
data class NextTarget(val target: Target, val reason: String)

/**
 * Прогрессия нагрузки. Правило «двойной прогрессии» из позиции ACSM (Progression Models in Resistance Training, 2009):
 * работать в диапазоне повторений; когда все подходы сделаны на верхней границе — добавить вес (2–10%) и вернуться к нижней.
 * Для упражнений без веса вместо веса добавляется подход, для упражнений на время — 5 секунд.
 * Если дважды подряд не удалось выполнить нижнюю границу — снижение веса на 10% (разгрузка).
 */
object Progression {
    fun next(t: Target, sets: List<DoneSet>, failedBefore: Boolean = false): NextTarget {
        val done = sets.filter { it.done }
        if (done.isEmpty()) return NextTarget(t, "Подходы не отмечены — цель без изменений")
        val allSets = done.size >= t.sets
        return when (t.kind) {
            2 -> {
                val ok = allSets && done.all { it.seconds >= t.seconds }
                if (ok) NextTarget(t.copy(seconds = t.seconds + 5), "Все подходы выполнены — +5 секунд")
                else NextTarget(t, "Не все подходы выполнены — повторите цель")
            }
            1 -> {
                val minReps = done.minOf { it.reps }
                when {
                    allSets && minReps >= t.repMax && t.sets < t.maxSets ->
                        NextTarget(t.copy(sets = t.sets + 1, reps = t.repMin), "Верхняя граница во всех подходах — +1 подход, повторы с ${t.repMin}")
                    allSets && minReps >= t.repMax ->
                        NextTarget(t.copy(reps = t.reps + 1, repMax = t.repMax + 1), "Максимум подходов — растим повторы")
                    allSets && minReps >= t.reps ->
                        NextTarget(t.copy(reps = (t.reps + 1).coerceAtMost(t.repMax)), "Цель выполнена — +1 повтор")
                    else -> NextTarget(t, "Цель не выполнена — повторите её")
                }
            }
            else -> {
                val minReps = done.minOf { it.reps }
                val w = done.maxOf { it.weight }
                when {
                    allSets && minReps >= t.repMax ->
                        NextTarget(t.copy(weight = roundTo(w + t.weightStep, t.weightStep), reps = t.repMin), "Верх диапазона во всех подходах — +${fmt(t.weightStep)} кг, повторы с ${t.repMin}")
                    allSets && minReps >= t.reps ->
                        NextTarget(t.copy(weight = w, reps = (t.reps + 1).coerceAtMost(t.repMax)), "Цель выполнена — +1 повтор")
                    minReps < t.repMin && failedBefore && w > 0 ->
                        NextTarget(t.copy(weight = roundTo(w * 0.9, t.weightStep), reps = t.repMin), "Дважды ниже диапазона — разгрузка −10%")
                    else -> NextTarget(t.copy(weight = w), "Цель не выполнена — повторите её")
                }
            }
        }
    }

    /** Не дотянули до нижней границы повторов — нужно для правила разгрузки. */
    fun failed(t: Target, sets: List<DoneSet>): Boolean {
        val done = sets.filter { it.done }
        return t.kind != 2 && done.isNotEmpty() && done.minOf { it.reps } < t.repMin
    }

    private fun roundTo(v: Double, step: Double): Double = if (step <= 0) v else (v / step).roundToInt() * step

    /** Оценка максимума на одно повторение по Эпли (1985): вес × (1 + повторы / 30). */
    fun oneRepMax(weight: Double, reps: Int): Double = if (reps <= 1) weight else weight * (1 + reps / 30.0)

    /** Тоннаж: сумма вес × повторы по выполненным подходам. */
    fun volume(sets: List<DoneSet>): Double = sets.filter { it.done }.sumOf { it.weight * it.reps }

    /**
     * Расход энергии по MET из Компендиума физической активности (Ainsworth et al., 2011):
     * силовая тренировка умеренной интенсивности — 3,5 MET, интенсивная — 6 MET. ккал = MET × вес × часы.
     */
    fun kcal(minutes: Int, weightKg: Double, vigorous: Boolean): Int = ((if (vigorous) 6.0 else 3.5) * weightKg * minutes / 60).roundToInt()

    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else v.toString().replace('.', ',')
}
