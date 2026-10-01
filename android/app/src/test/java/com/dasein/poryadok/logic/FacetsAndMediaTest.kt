package com.dasein.poryadok.logic

import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.data.MediaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacetsAndMediaTest {
    @Test fun recipeBasesFromIngredients() {
        val plov = listOf("Свинина (шея)", "Рис", "Морковь", "Соль морская")
        assertEquals(setOf("pork"), RecipeFacets.bases(plov))
        assertTrue(RecipeFacets.matchesBase("meat", plov))
        assertFalse(RecipeFacets.matchesBase("veg", plov))
        val salad = listOf("Огурец", "Помидор", "Сметана 20%")
        assertTrue(RecipeFacets.isVegetarian(salad))
        assertFalse(RecipeFacets.isVegan(salad))
        assertTrue(RecipeFacets.isVegan(listOf("Нут", "Лук", "Масло растительное")))
        assertTrue(RecipeFacets.matchesBase("fish", listOf("Минтай", "Мука")))
    }

    @Test fun proteinGroups() {
        assertTrue(RecipeFacets.matchesProtein("high", 30.0))
        assertTrue(RecipeFacets.matchesProtein("mid", 12.0))
        assertTrue(RecipeFacets.matchesProtein("low", 4.0))
        assertFalse(RecipeFacets.matchesProtein("low", 25.0))
    }

    @Test fun mediaLengthParsing() {
        assertEquals(135, MediaCatalog.minutes("2 ч 15 мин"))
        assertEquals(115, MediaCatalog.minutes("1:55"))
        assertEquals(98, MediaCatalog.minutes("98 мин."))
        assertEquals(24, MediaCatalog.count("3 сезона, 24 серии", MediaKind.SERIES))
        assertEquals(320, MediaCatalog.count("320 стр.", MediaKind.BOOK))
        assertNull(MediaCatalog.count("3 сезона", MediaKind.SERIES))
        assertEquals("1990-е", MediaCatalog.decade(1994))
        assertEquals(listOf("Драма", "Комедия", "Триллер"), MediaCatalog.split("драма, комедия / триллер"))
    }

    @Test fun progressStepFinishesSeries() {
        val s = MediaItem(kind = MediaKind.SERIES, title = "Сериал", status = MediaStatus.PLANNED, progressTotal = 2)
        val one = com.dasein.poryadok.ui.media.stepProgress(s, 1)
        assertEquals(MediaStatus.IN_PROGRESS, one.status)
        val two = com.dasein.poryadok.ui.media.stepProgress(one, 1)
        assertEquals(MediaStatus.DONE, two.status)
        assertTrue(two.finishedDay != null)
        assertEquals(2, com.dasein.poryadok.ui.media.stepProgress(two, 5).progress)
    }

    @Test fun statsAndSimilar() {
        val a = MediaItem(id = 1, title = "A", genres = "драма, триллер", creators = "Финчер", myRating = 9, status = MediaStatus.DONE, length = "2 ч")
        val b = MediaItem(id = 2, title = "B", genres = "триллер", creators = "Финчер", myRating = 7)
        val c = MediaItem(id = 3, title = "C", genres = "мультфильм")
        val st = MediaCatalog.stats(listOf(a, b, c))
        assertEquals(3, st.total)
        assertEquals(8.0, st.avgRating, 0.01)
        assertEquals("Триллер" to 2, st.topGenres.first())
        assertEquals(120, st.doneMinutes)
        assertEquals(listOf(b), MediaCatalog.similar(a, listOf(a, b, c)))
    }

    @Test fun rainOnlyForRestOfDay() {
        val h = listOf(
            WeatherHour("2026-09-30T08:00", 10.0, 80, 61),
            WeatherHour("2026-09-30T15:00", 12.0, 10, 2),
        )
        assertTrue(WeatherLogic.precipToday(h, "2026-09-30").startsWith("Дождь 08:00"))
        assertTrue(WeatherLogic.precipToday(h, "2026-09-30", fromHour = 12).startsWith("Без осадков"))
    }
}
