package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDiscoverTest {
    private fun hit(t: String, y: Int?, c: String = "", g: String = "", d: String = "", cast: String = "", r: Double? = null) =
        MediaHit(kind = 0, title = t, year = y, countries = c, genres = g, creators = d, cast = cast, rating = r, source = "x", externalId = t)

    private val hits = listOf(
        hit("Паразиты", 2019, "Южная Корея", "драма, триллер", "Пон Джун-хо", "Сон Ган Хо — Ки-тхэк", 8.5),
        hit("Олдбой", 2003, "Корея Южная", "триллер, детектив", "Пак Чхан-ук", "Чхве Мин-сик", 8.0),
        hit("Интерстеллар", 2014, "США, Великобритания", "фантастика, драма", "Кристофер Нолан", "Мэттью Макконахи", 8.7),
        hit("Без данных", 2010),
    )

    @Test fun filtersByCountryGenreYearsPersonRating() {
        val kr = MediaDiscover.apply(hits, MediaDiscover.Filter(country = "Южная Корея"))
        assertEquals(listOf("Паразиты", "Олдбой"), kr.hits.map { it.title })
        assertEquals(1, kr.unknown)
        assertEquals(listOf("Паразиты", "Интерстеллар"), MediaDiscover.apply(hits, MediaDiscover.Filter(genre = "Драма")).hits.map { it.title })
        assertEquals(listOf("Паразиты", "Интерстеллар", "Без данных"), MediaDiscover.apply(hits, MediaDiscover.Filter(yearFrom = 2010)).hits.map { it.title })
        assertEquals(listOf("Интерстеллар"), MediaDiscover.apply(hits, MediaDiscover.Filter(person = "нолан", role = MediaDiscover.Role.DIRECTOR)).hits.map { it.title })
        assertTrue(MediaDiscover.apply(hits, MediaDiscover.Filter(person = "Сон Ган Хо", role = MediaDiscover.Role.DIRECTOR)).hits.isEmpty())
        assertEquals(listOf("Паразиты"), MediaDiscover.apply(hits, MediaDiscover.Filter(person = "Сон Ган Хо", role = MediaDiscover.Role.ACTOR)).hits.map { it.title })
        assertEquals(listOf("Паразиты", "Интерстеллар"), MediaDiscover.apply(hits, MediaDiscover.Filter(minRating = 8.5)).hits.map { it.title })
        assertEquals("Интерстеллар", MediaDiscover.apply(hits, MediaDiscover.Filter(sort = MediaDiscover.Sort.RATING)).hits.first().title)
        assertEquals("Олдбой", MediaDiscover.apply(hits, MediaDiscover.Filter(sort = MediaDiscover.Sort.OLDEST)).hits.first().title)
        assertEquals(listOf("Олдбой"), MediaDiscover.apply(hits, MediaDiscover.Filter(country = "Южная Корея", hideOwned = true)) { it.title == "Паразиты" }.hits.map { it.title })
    }

    @Test fun facetsAndCounts() {
        val (c, g) = MediaDiscover.facets(hits)
        assertEquals("Южная Корея" to 2, c.first())
        assertTrue(g.any { it.first == "Триллер" && it.second == 2 })
        assertFalse(MediaDiscover.Filter(minRating = 7.0).canDiscover)
        assertTrue(MediaDiscover.Filter(country = "Япония").canDiscover)
        assertEquals(2, MediaDiscover.Filter(country = "Япония", genre = "Драма").count)
    }

    @Test fun discoverQueries() {
        val f = MediaDiscover.Filter(country = "Южная Корея", genre = "Мелодрама", yearFrom = 2015, yearTo = 2024, minRating = 7.0)
        val tv = MediaDiscover.tmdbUrl(true, f)
        assertTrue(tv, "discover/tv" in tv && "with_origin_country=KR" in tv && "first_air_date.gte=2015-01-01" in tv && "vote_average.gte=7.0" in tv)
        assertFalse("with_genres" in tv) // у сериалов в TMDB нет «мелодрамы»
        assertTrue("with_genres=10749" in MediaDiscover.tmdbUrl(false, f))
        val kp = MediaDiscover.kinopoiskUrl(false, f)
        assertTrue(kp, "year=2015-2024" in kp && "type=movie" in kp && "rating.kp=7-10" in kp && "countries.name=" in kp)
        val q = MediaDiscover.sparql(false, f, listOf("Q123"))
        assertTrue(q, "wd:Q884" in q && "wd:Q11424" in q && "?yr >= 2015" in q && "wd:Q123" in q && "LIMIT 40" in q)
        val ids = MediaDiscover.sparqlIds("""{"results":{"bindings":[{"item":{"value":"http://www.wikidata.org/entity/Q42"}},{"item":{"value":"http://www.wikidata.org/entity/Q7"}}]}}""")
        assertEquals(listOf("Q42", "Q7"), ids)
        assertEquals("Фильмы · мелодрама · Южная Корея · 2015–2024 · рейтинг от 7", MediaDiscover.describe(f, false))
    }

    @Test fun rankByPairs() {
        // Истинный порядок — по убыванию числа; отвечаем честно.
        val r = TopRank(listOf(3, 9, 1, 7, 5, 8, 2))
        while (true) {
            val (a, b) = r.question() ?: break
            r.answer(a > b)
        }
        assertTrue(r.finished)
        assertEquals(listOf(9, 8, 7, 5, 3, 2, 1), r.result())
        assertTrue(r.asked <= 14)
        val part = TopRank(listOf("a", "b", "c"))
        part.answer(true)
        assertEquals(3, part.result().size)
    }
}
