package com.dasein.poryadok.logic

import com.dasein.poryadok.data.BackupData
import com.dasein.poryadok.data.BackupSection
import com.dasein.poryadok.data.ExtraBackup
import com.dasein.poryadok.data.Habit
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.Recipe
import com.dasein.poryadok.data.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupAndWalkTest {
    private fun data(tag: String) = BackupData(
        habits = listOf(Habit(id = 1, name = "Habit $tag")),
        weightEntrys = listOf(WeightEntry(1, if (tag == "phone") 80.0 else 75.0)),
        extra = ExtraBackup(recipes = listOf(Recipe(id = 1, name = "Recipe $tag")), media = listOf(MediaItem(id = 1, title = "Film $tag"))),
    )

    @Test fun partialExportKeepsOnlyChosenSections() {
        val only = BackupSection.only(data("phone"), setOf(BackupSection.RECIPES, BackupSection.HABITS))
        assertEquals("Habit phone", only.habits.single().name)
        assertEquals("Recipe phone", only.extra!!.recipes.single().name)
        assertTrue(only.weightEntrys.isEmpty())
        assertTrue(only.extra!!.media.isEmpty())
    }

    @Test fun partialImportReplacesOnlyChosenSections() {
        val merged = BackupSection.merge(data("phone"), data("file"), setOf(BackupSection.MEDIA, BackupSection.HEALTH))
        assertEquals("Film file", merged.extra!!.media.single().title)
        assertEquals(75.0, merged.weightEntrys.single().kg, 0.01)
        // Не выбранные разделы остаются с телефона.
        assertEquals("Habit phone", merged.habits.single().name)
        assertEquals("Recipe phone", merged.extra!!.recipes.single().name)
    }

    @Test fun everyDirHasASection() {
        assertEquals(BackupSection.NOTES, BackupSection.forDir("pages"))
        assertEquals(BackupSection.WARDROBE, BackupSection.forDir("outfits"))
        assertEquals(BackupSection.SETTINGS, BackupSection.forDir("something_new"))
    }

    @Test fun compendiumMetsBySpeed() {
        assertEquals(3.5, WalkEnergy.met(4.8), 0.001)
        assertEquals(5.0, WalkEnergy.met(6.4), 0.001)
        assertEquals(3.9, WalkEnergy.met(5.2), 0.001)
        assertEquals(8.3, WalkEnergy.met(9.0), 0.001)
    }

    @Test fun kcalDependOnSpeed() {
        // 10 000 шагов, 70 кг, 175 см: ≈ 7,3 км.
        val slow = WalkEnergy.kcalAtPace(10_000, 70.0, 175.0, 4.0)
        val normal = WalkEnergy.kcalAtPace(10_000, 70.0, 175.0, 4.8)
        val brisk = WalkEnergy.kcalAtPace(10_000, 70.0, 175.0, 6.4)
        assertTrue("normal $normal", normal in 240.0..290.0)
        assertTrue(brisk > normal)
        assertTrue(slow > 0)
        assertEquals(0.0, WalkEnergy.kcalAtPace(0, 70.0, 175.0), 0.0)
    }

    @Test fun minutesGiveSpeedZones() {
        // 30 минут по 110 шагов (≈ 4,8 км/ч при 175 см) и 10 минут по 140 (≈ 6,1 км/ч).
        val m = IntArray(1440)
        for (i in 0 until 30) m[600 + i] = 110
        for (i in 0 until 10) m[700 + i] = 140
        val d = WalkEnergy.byMinutes(m, 70.0, 175.0)
        assertEquals(40, d.walkMinutes)
        assertEquals(30, d.moderate)
        assertEquals(10, d.brisk)
        assertTrue(d.kcal in 80..140)
        assertEquals(4.8, WalkEnergy.typicalPace(m, 175.0)!!, 0.11)
    }
}
