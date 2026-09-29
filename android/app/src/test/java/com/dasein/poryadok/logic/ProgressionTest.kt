package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private val squat = Target(kind = 0, sets = 3, reps = 8, weight = 60.0, seconds = 0, repMin = 8, repMax = 12, weightStep = 2.5, maxSets = 5)

    private fun sets(vararg reps: Int, w: Double = 60.0) = reps.map { DoneSet(it, w, 0, true) }

    @Test fun addsRepThenWeight() {
        val a = Progression.next(squat, sets(8, 8, 8))
        assertEquals(9, a.target.reps)
        assertEquals(60.0, a.target.weight, 0.0)
        val b = Progression.next(squat.copy(reps = 12), sets(12, 12, 12))
        assertEquals(62.5, b.target.weight, 0.0)
        assertEquals(8, b.target.reps)
    }

    @Test fun keepsWhenMissedAndDeloadsAfterTwoFails() {
        val miss = Progression.next(squat.copy(reps = 10), sets(10, 9, 8))
        assertEquals(10, miss.target.reps)
        assertTrue(Progression.failed(squat, sets(8, 7, 6)))
        val deload = Progression.next(squat, sets(8, 7, 6), failedBefore = true)
        assertEquals(55.0, deload.target.weight, 0.0)
    }

    @Test fun bodyweightAddsSetsAtTop() {
        val push = Target(kind = 1, sets = 3, reps = 15, weight = 0.0, seconds = 0, repMin = 10, repMax = 15, weightStep = 0.0, maxSets = 5)
        val n = Progression.next(push, sets(15, 15, 16, w = 0.0))
        assertEquals(4, n.target.sets)
        assertEquals(10, n.target.reps)
        val up = Progression.next(push.copy(reps = 11), sets(11, 11, 12, w = 0.0))
        assertEquals(12, up.target.reps)
        assertEquals(3, up.target.sets)
    }

    @Test fun timedAddsSeconds() {
        val plank = Target(kind = 2, sets = 3, reps = 0, weight = 0.0, seconds = 40, repMin = 0, repMax = 0, weightStep = 0.0, maxSets = 5)
        val n = Progression.next(plank, List(3) { DoneSet(0, 0.0, 40, true) })
        assertEquals(45, n.target.seconds)
    }

    @Test fun epleyVolumeKcal() {
        assertEquals(80.0, Progression.oneRepMax(60.0, 10), 0.01)
        assertEquals(60.0, Progression.oneRepMax(60.0, 1), 0.0)
        assertEquals(1440.0, Progression.volume(sets(8, 8, 8)), 0.0)
        assertEquals(245, Progression.kcal(60, 70.0, false))
    }
}
