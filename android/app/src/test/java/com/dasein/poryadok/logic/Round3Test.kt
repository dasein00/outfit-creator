package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyTest {
    @Test fun stepsEstimate() {
        assertEquals(279, Energy.stepsKcal(7000, 70.0))
        val b = Energy.burned(7387, 78.4, 150, null)
        assertEquals(330 + 150, b.total)
        assertFalse(b.fromHealthConnect)
    }

    @Test fun healthConnectWinsWhenHigher() {
        val b = Energy.burned(5000, 70.0, 0, 450)
        assertEquals(450, b.total)
        assertTrue(b.fromHealthConnect)
        assertEquals(200, Energy.burned(5000, 70.0, 0, 50).total)
    }
}

class GramsCalculatorTest {
    private val chicken = Ingr("Куриное филе", 400.0, "г", 400.0, Macros(113.0, 23.6, 1.9, 0.4))
    private val eggs = Ingr("Яйцо", 2.0, "шт", 110.0, Macros(157.0, 12.7, 11.5, 0.7))

    @Test fun factorFromGramsAndPieces() {
        assertEquals(1.5, Cooking.factorFor(chicken, 600.0)!!, 1e-9)
        assertEquals(2.0, Cooking.factorFor(eggs, 4.0)!!, 1e-9)
        assertNull(Cooking.factorFor(chicken, 0.0))
        val scaled = Cooking.scale(listOf(chicken, eggs), Cooking.factorFor(chicken, 600.0)!!)
        assertEquals(3.0, scaled[1].amount, 1e-9)
    }

    @Test fun platePortions() {
        assertEquals(400, Plate.recipeGrams("Обеды"))
        assertEquals(350, Plate.recipeGrams("Супы"))
        assertEquals(120, Plate.productGrams("Мясо"))
        val per100 = Plate.per100(listOf(chicken))!!
        assertEquals(113.0, per100.kcal, 1e-9)
        assertNull(Plate.per100(emptyList()))
    }
}

class MediaParseTest {
    @Test fun itunes() {
        val r = MediaParse.itunes("""{"resultCount":1,"results":[{"trackId":42,"trackName":"Интерстеллар","artistName":"Кристофер Нолан","releaseDate":"2014-11-07T08:00:00Z","primaryGenreName":"Фантастика","artworkUrl100":"https://x/100x100bb.jpg","longDescription":"Про космос","trackTimeMillis":10140000}]}""")
        assertEquals(1, r.size)
        assertEquals("Интерстеллар", r[0].title)
        assertEquals(2014, r[0].year)
        assertEquals("https://x/600x600bb.jpg", r[0].posterUrl)
        assertEquals("Кристофер Нолан", r[0].creators)
        assertEquals("2 ч 49 мин", r[0].length)
        assertEquals("itunes:42", r[0].externalId)
    }

    @Test fun tvmazeWithCast() {
        val r = MediaParse.tvmazeDetails("""{"id":1,"name":"Breaking Bad","premiered":"2008-01-20","genres":["Drama","Crime"],"summary":"<p>A teacher</p>","image":{"original":"https://i/1.jpg"},"rating":{"average":9.2},"status":"Ended","network":{"name":"AMC","country":{"name":"United States"}},"_embedded":{"cast":[{"person":{"name":"Bryan Cranston"},"character":{"name":"Walter White"}}]}}""")!!
        assertEquals(2008, r.year)
        assertEquals("A teacher", r.description)
        assertEquals("Bryan Cranston — Walter White", r.cast)
        assertEquals("Drama, Crime", r.genres)
        assertEquals(9.2, r.rating!!, 0.0)
    }

    @Test fun kinopoiskPersons() {
        val r = MediaParse.kinopoiskDetails("""{"id":258687,"name":"Интерстеллар","alternativeName":"Interstellar","type":"movie","year":2014,"movieLength":169,"rating":{"kp":8.6},"poster":{"url":"https://p/1.jpg"},"genres":[{"name":"фантастика"}],"countries":[{"name":"США"}],"persons":[{"name":"Кристофер Нолан","enProfession":"director"},{"name":"Мэттью Макконахи","description":"Купер","enProfession":"actor"}]}""")!!
        assertEquals("Interstellar", r.originalTitle)
        assertEquals("Кристофер Нолан", r.creators)
        assertEquals("Мэттью Макконахи — Купер", r.cast)
        assertEquals("2 ч 49 мин", r.length)
        assertEquals("kp:258687", r.externalId)
        assertEquals(MediaParse.MOVIE, r.kind)
    }

    @Test fun books() {
        val g = MediaParse.googleBooks("""{"items":[{"id":"abc","volumeInfo":{"title":"Мастер и Маргарита","authors":["Михаил Булгаков"],"publishedDate":"1967","pageCount":480,"imageLinks":{"thumbnail":"http://books/1&edge=curl"}}}]}""")
        assertEquals("Михаил Булгаков", g[0].creators)
        assertEquals("https://books/1", g[0].posterUrl)
        assertEquals("480 стр.", g[0].length)
        val o = MediaParse.openLibrary("""{"docs":[{"key":"/works/OL1W","title":"1984","author_name":["George Orwell"],"first_publish_year":1949,"cover_i":123}]}""")
        assertEquals("https://covers.openlibrary.org/b/id/123-L.jpg", o[0].posterUrl)
        assertEquals(1949, o[0].year)
    }

    @Test fun listLines() {
        assertEquals("Интерстеллар" to 2014, MediaParse.parseListLine("1. Интерстеллар (2014)"))
        assertEquals("Брат" to 1997, MediaParse.parseListLine("- Брат, 1997"))
        assertEquals("Амели" to null, MediaParse.parseListLine("Амели"))
        assertNull(MediaParse.parseListLine("   "))
    }
}
