package com.dasein.poryadok.logic

import com.dasein.poryadok.logic.ExerciseEnergy.Gear
import com.dasein.poryadok.logic.ExerciseEnergy.SetWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseEnergyTest {
    private val none = Gear()

    private fun kcal(name: String, muscle: String = "", equipment: String = "", kind: Int = ExerciseEnergy.KIND_REPS, set: SetWork, gear: Gear = none, body: Double = 70.0) =
        ExerciseEnergy.setKcal(name, muscle, equipment, kind, set, body, gear)

    @Test fun profileByNameBeatsMuscle() {
        assertEquals(0.9, ExerciseEnergy.profile("Отжимания на брусьях", "Грудь").bodyShare, 1e-9)
        assertEquals(0.65, ExerciseEnergy.profile("Отжимания", "Грудь").bodyShare, 1e-9)
        assertEquals(0.0, ExerciseEnergy.profile("Жим ногами", "Ноги").bodyShare, 1e-9)
        assertEquals(0.6, ExerciseEnergy.profile("Моё упражнение", "Ноги").bodyShare, 1e-9)
    }

    @Test fun dumbbellCount() {
        assertEquals(2, ExerciseEnergy.dumbbells("Жим гантелей сидя", "Гантели"))
        assertEquals(1, ExerciseEnergy.dumbbells("Тяга гантели одной рукой", "Гантель"))
        assertEquals(1, ExerciseEnergy.dumbbells("Гоблет-присед с гантелью", ""))
        assertEquals(0, ExerciseEnergy.dumbbells("Приседания", "Без инвентаря"))
    }

    @Test fun squatsAreRealistic() {
        // 15 приседаний без веса для 70 кг — около 5–6 ккал (примерно 0,3–0,4 ккал за повтор).
        val k = kcal("Приседания без веса", set = SetWork(15, 0.0, 0))
        assertTrue("$k", k in 4.0..8.0)
    }

    @Test fun moreWeightAndRepsBurnMore() {
        val light = kcal("Приседания со штангой", kind = ExerciseEnergy.KIND_WEIGHT, set = SetWork(8, 20.0, 0))
        val heavy = kcal("Приседания со штангой", kind = ExerciseEnergy.KIND_WEIGHT, set = SetWork(8, 60.0, 0))
        val more = kcal("Приседания со штангой", kind = ExerciseEnergy.KIND_WEIGHT, set = SetWork(12, 20.0, 0))
        assertTrue(heavy > light && more > light)
    }

    @Test fun dumbbellsFromGearWhenSetHasNoWeight() {
        val bare = kcal("Сгибания рук с гантелями", "Руки", "Гантели", ExerciseEnergy.KIND_REPS, SetWork(12, 0.0, 0))
        val withGear = kcal("Сгибания рук с гантелями", "Руки", "Гантели", ExerciseEnergy.KIND_REPS, SetWork(12, 0.0, 0), Gear(dumbbellKg = 8.0))
        val explicit = kcal("Сгибания рук с гантелями", "Руки", "Гантели", ExerciseEnergy.KIND_WEIGHT, SetWork(12, 16.0, 0), Gear(dumbbellKg = 8.0))
        assertTrue(withGear > bare)
        // Две гантели по 8 кг — то же, что 16 кг в записи подхода.
        assertEquals(explicit, withGear, 1e-9)
    }

    @Test fun wearAddsToTimedAndReps() {
        val plank = kcal("Планка", "Пресс", kind = ExerciseEnergy.KIND_TIME, set = SetWork(0, 0.0, 60))
        val plankW = kcal("Планка", "Пресс", kind = ExerciseEnergy.KIND_TIME, set = SetWork(0, 0.0, 60), gear = Gear(wearKg = 4.0))
        assertEquals(3.8 * 70 / 60, plank, 1e-9)
        assertTrue(plankW > plank)
        val lunges = kcal("Выпады", set = SetWork(10, 0.0, 0))
        val lungesW = kcal("Выпады", set = SetWork(10, 0.0, 0), gear = Gear(wearKg = 4.0))
        assertTrue(lungesW > lunges)
    }

    @Test fun emptySetBurnsNothing() {
        assertEquals(0.0, kcal("Отжимания", set = SetWork(0, 0.0, 0)), 1e-9)
    }
}
