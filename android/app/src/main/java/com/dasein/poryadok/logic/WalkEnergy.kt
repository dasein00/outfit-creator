package com.dasein.poryadok.logic

import kotlin.math.roundToInt

/**
 * Расход энергии при ходьбе по скорости — метод MET из «Компендиума физической активности»
 * (Ainsworth B.E. и соавт., Compendium of Physical Activities, 2011/2024; раздел «Ходьба»,
 * ровная твёрдая поверхность). 1 MET ≈ 1 ккал на кг массы в час — расход в покое.
 *
 * Активные калории = (MET − 1) × вес × время: считаем только то, что сверх покоя,
 * иначе обмен покоя посчитался бы дважды (он уже входит в норму калорий).
 *
 * Скорость = частота шагов × длина шага. Длина шага ≈ 0,415 × рост (стандарт шагомеров).
 * Частота шагов берётся поминутно из Health Connect; если её нет — из вашего обычного темпа.
 */
object WalkEnergy {
    /** Скорость, км/ч → MET (Компендиум). Между точками — линейно. */
    val COMPENDIUM: List<Pair<Double, Double>> = listOf(
        0.0 to 1.3,   // шаги на месте — оценка между покоем и прогулкой
        2.4 to 2.0,   // прогулка, меньше 2 миль/ч
        3.2 to 2.8,   // 2,0 мили/ч, медленно
        4.0 to 3.0,   // 2,5 мили/ч
        4.8 to 3.5,   // 2,8–3,2 мили/ч, обычный темп
        5.6 to 4.3,   // 3,5 мили/ч, бодро
        6.4 to 5.0,   // 4,0 мили/ч, очень бодро
        7.2 to 7.0,   // 4,5 мили/ч, очень быстро
        8.0 to 8.3,   // 5,0 миль/ч, скоростная ходьба
    )

    /** Темпы на выбор, км/ч. */
    val PACES = listOf(3.2 to "Медленно", 4.0 to "Спокойно", 4.8 to "Обычно", 5.6 to "Бодро", 6.4 to "Быстро", 7.2 to "Очень быстро")
    const val DEFAULT_PACE = 4.8

    fun met(kmh: Double): Double {
        if (kmh <= 0) return COMPENDIUM.first().second
        val last = COMPENDIUM.last()
        if (kmh >= last.first) return last.second
        val i = COMPENDIUM.indexOfLast { it.first <= kmh }
        val (x0, y0) = COMPENDIUM[i]
        val (x1, y1) = COMPENDIUM[i + 1]
        return y0 + (y1 - y0) * (kmh - x0) / (x1 - x0)
    }

    /** Длина шага, м: 0,415 роста; без роста — 0,72 м. */
    fun stepLength(heightCm: Double?): Double = if (heightCm != null && heightCm in 100.0..230.0) heightCm * 0.415 / 100 else 0.72

    /** Скорость при частоте [cadence] шагов в минуту, км/ч. */
    fun speedKmh(cadence: Double, heightCm: Double?): Double = cadence * stepLength(heightCm) * 60 / 1000

    /** Активные калории за [steps] шагов обычным темпом [paceKmh]. */
    fun kcalAtPace(steps: Int, weightKg: Double, heightCm: Double?, paceKmh: Double = DEFAULT_PACE): Double {
        if (steps <= 0) return 0.0
        val pace = paceKmh.coerceIn(1.5, 8.0)
        val hours = steps * stepLength(heightCm) / 1000 / pace
        return (met(pace) - 1) * weightKg * hours
    }

    /** Разбор дня по минутам. */
    data class Day(
        val kcal: Int,
        val walkMinutes: Int,
        /** Средняя скорость в минуты ходьбы, км/ч. */
        val avgKmh: Double,
        /** Минуты медленно (< 4 км/ч), умеренно (4–5,6) и бодро (> 5,6). */
        val slow: Int, val moderate: Int, val brisk: Int,
        val km: Double,
    )

    /**
     * Точный расчёт по шагам каждой минуты: скорость минуты = шаги × длина шага,
     * расход минуты = (MET(скорость) − 1) × вес / 60. Минуты с ходьбой — от 60 шагов (≈ 2,5 км/ч и выше).
     */
    fun byMinutes(minutes: IntArray, weightKg: Double, heightCm: Double?): Day {
        var kcal = 0.0; var walk = 0; var speedSum = 0.0; var slow = 0; var mod = 0; var brisk = 0; var steps = 0
        for (s in minutes) {
            if (s <= 0) continue
            steps += s
            val v = speedKmh(s.toDouble(), heightCm)
            kcal += (met(v) - 1) * weightKg / 60
            if (s >= 60) {
                walk++; speedSum += v
                when { v < 4.0 -> slow++; v <= 5.6 -> mod++; else -> brisk++ }
            }
        }
        return Day(kcal.roundToInt(), walk, if (walk > 0) speedSum / walk else 0.0, slow, mod, brisk, steps * stepLength(heightCm) / 1000)
    }

    /** Обычный темп человека по минутам ходьбы (медиана скорости), если ходьбы набралось хотя бы 20 минут. */
    fun typicalPace(minutes: IntArray, heightCm: Double?): Double? {
        val v = minutes.filter { it >= 60 }.map { speedKmh(it.toDouble(), heightCm) }.sorted()
        if (v.size < 20) return null
        return ((v[v.size / 2] * 10).roundToInt() / 10.0).coerceIn(2.5, 8.0)
    }
}
