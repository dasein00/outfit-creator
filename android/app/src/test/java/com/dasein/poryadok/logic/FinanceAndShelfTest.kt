package com.dasein.poryadok.logic

import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceAndShelfTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day).toEpochDay()
    private val ops = listOf(
        FinanceStats.Op(d(2026, 8, 1), 50_000.0, true, "Зарплата"),
        FinanceStats.Op(d(2026, 8, 3), 10_000.0, false, "Продукты"),
        FinanceStats.Op(d(2026, 8, 10), 5_000.0, false, "Кафе и рестораны"),
        FinanceStats.Op(d(2026, 9, 1), 50_000.0, true, "Зарплата"),
        FinanceStats.Op(d(2026, 9, 2), 12_000.0, false, "Продукты"),
        FinanceStats.Op(d(2026, 9, 5), 20_000.0, false, "Кафе и рестораны"),
    )

    @Test fun monthAndSavingsRate() {
        val m = FinanceStats.month(ops, YearMonth.of(2026, 9))
        assertEquals(50_000.0, m.income, 0.01)
        assertEquals(32_000.0, m.expense, 0.01)
        assertEquals(0.36, m.savingsRate, 0.001)
    }

    @Test fun paceComparesSameDayOfPreviousMonth() {
        // К 5 сентября потрачено 32 000, к 5 августа — 10 000 → +220 %.
        val p = FinanceStats.pace(ops, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 5))!!
        assertEquals(2.2, p, 0.001)
        assertEquals(192_000.0, FinanceStats.forecast(32_000.0, 5, 30), 0.01)
    }

    @Test fun trendsAndSplit() {
        val t = FinanceStats.categoryTrends(ops, YearMonth.of(2026, 9))
        val cafe = t.first { it.category == "Кафе и рестораны" }
        assertEquals(3.0, cafe.change!!, 0.001)
        val split = FinanceStats.split503020(ops, YearMonth.of(2026, 9))
        assertEquals(0.24, split[FinanceStats.Need.NEEDS]!!, 0.001)
        assertEquals(0.40, split[FinanceStats.Need.WANTS]!!, 0.001)
        assertEquals(0.36, split[FinanceStats.Need.SAVE]!!, 0.001)
    }

    @Test fun healthAndTips() {
        val h = FinanceStats.health(0.2, 6.0, 0.9, -0.1)
        assertEquals(100, h.score)
        assertTrue(FinanceStats.health(-0.1, 0.5, 1.3, 0.5).score < 30)
        val m = FinanceStats.month(ops, YearMonth.of(2026, 9))
        val tips = FinanceStats.tips(m, FinanceStats.averages(ops, YearMonth.of(2026, 9)), 0.5, 2.2,
            FinanceStats.categoryTrends(ops, YearMonth.of(2026, 9)), FinanceStats.split503020(ops, YearMonth.of(2026, 9)), false) { "%.0f".format(it) }
        assertTrue(tips.any { it.title.startsWith("Тратите быстрее") })
        assertTrue(tips.any { it.title.contains("подушку") })
    }

    @Test fun calculators() {
        val (pay, total, over) = FinanceStats.loan(1_000_000.0, 12.0, 12)
        assertEquals(88_848.79, pay, 0.5)
        assertEquals(total - 1_000_000.0, over, 0.01)
        val (sum, put, _) = FinanceStats.deposit(0.0, 10_000.0, 0.0, 12)
        assertEquals(120_000.0, sum, 0.01); assertEquals(120_000.0, put, 0.01)
        assertEquals(10_000.0, FinanceStats.saveFor(120_000.0, 0.0, 0.0, 12), 0.01)
        assertTrue(FinanceStats.saveFor(120_000.0, 0.0, 12.0, 12) < 10_000.0)
        assertEquals(50_000.0, FinanceStats.inflation(100_000.0, 100.0, 1.0), 0.01)
        assertEquals(10, FinanceStats.payoffMonths(100_000.0, 0.0, 10_000.0))
        assertNull(FinanceStats.payoffMonths(100_000.0, 24.0, 1_000.0))
    }

    private fun m(id: Long, title: String, countries: String = "", genres: String = "", status: Int = MediaStatus.DONE, rating: Int = 0, year: Int? = null) =
        MediaItem(id = id, title = title, countries = countries, genres = genres, status = status, myRating = rating, year = year, createdAt = System.currentTimeMillis())

    @Test fun smartFoldersAndGroups() {
        val list = listOf(
            m(1, "Аварийная посадка любви", "Южная Корея", "Мелодрама", rating = 10, year = 2019),
            m(2, "Интерстеллар", "США", "Фантастика", rating = 9, year = 2014),
            m(3, "Дюна", "США", "Фантастика", status = MediaStatus.PLANNED, year = 2021),
        )
        val now = System.currentTimeMillis()
        assertEquals(listOf(1L), list.filter { MediaShelf.smart("smart:asia")!!.test(it, now) }.map { it.id })
        assertEquals(listOf(1L, 2L), list.filter { MediaShelf.smart("smart:top")!!.test(it, now) }.map { it.id })
        val names = MediaStatus.names(0)
        val byStatus = MediaShelf.group(list, MediaShelf.Group.STATUS, names)
        assertEquals(names[MediaStatus.PLANNED], byStatus.first().first)
        val byDecade = MediaShelf.group(list, MediaShelf.Group.DECADE, names)
        assertEquals(listOf("2020-е", "2010-е"), byDecade.map { it.first })
        val byFolder = MediaShelf.group(list, MediaShelf.Group.FOLDER, names) { if (it.id == 1L) listOf("Дорамы", "С мамой") else emptyList() }
        assertEquals(3, byFolder.size)
    }

    @Test fun newCatalogParsers() {
        val imdb = """{"d":[{"i":{"imageUrl":"https://m.media-amazon.com/images/M/abc._V1_.jpg"},"id":"tt10850932","l":"Crash Landing on You","q":"TV series","qid":"tvSeries","s":"Hyun Bin, Son Ye-jin","y":2019,"yr":"2019-2020"},
            {"id":"nm1","l":"Hyun Bin","s":"Actor"},{"id":"tt1","l":"Some Film","qid":"movie","y":2020}]}"""
        val series = MediaParse.imdbSuggest(imdb, MediaParse.SERIES)
        assertEquals(1, series.size)
        assertEquals("Crash Landing on You", series[0].title)
        assertEquals("https://m.media-amazon.com/images/M/abc._V1_SX600.jpg", series[0].posterUrl)
        assertEquals("Hyun Bin\nSon Ye-jin", series[0].cast)
        assertEquals(1, MediaParse.imdbSuggest(imdb, MediaParse.MOVIE).size)

        val tmdb = """{"results":[{"id":94796,"name":"Аварийная посадка любви","original_name":"사랑의 불시착","first_air_date":"2019-12-14","poster_path":"/p.jpg","overview":"Наследница…","vote_average":8.6,"origin_country":["KR"],"genre_ids":[18,35]}]}"""
        val t = MediaParse.tmdbSearch(tmdb, MediaParse.SERIES, "tv").single()
        assertEquals("사랑의 불시착", t.originalTitle)
        assertEquals("Южная Корея", t.countries)
        assertEquals("драма, комедия", t.genres)
        assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", t.posterUrl)
        assertEquals(2019, t.year)

        val shiki = """[{"id":5114,"name":"Fullmetal Alchemist: Brotherhood","russian":"Стальной алхимик: Братство","image":{"original":"/system/animes/original/5114.jpg"},"url":"/animes/5114","kind":"tv","score":"9.1","episodes":64,"aired_on":"2009-04-05"}]"""
        val s = MediaParse.shikimori(shiki, MediaParse.SERIES).single()
        assertEquals("Стальной алхимик: Братство", s.title)
        assertEquals("64 эп.", s.length)
        assertNotNull(s.rating)
    }

    @Test fun watchTopDiffAndDefaultNames() {
        // Пункт 10 → карточка 1 (ещё в планах), пункт 11 → карточка 2 (уже посмотрели); карточка 3 — новая.
        val (add, remove) = MediaShelf.watchDiff(listOf(1L, 3L), mapOf(10L to 1L, 11L to 2L))
        assertEquals(listOf(3L), add)
        assertEquals(listOf(11L), remove)
        // Без переименования — стандартные названия статусов.
        assertEquals("Хочу посмотреть", MediaStatus.names(0)[0])
        assertEquals("Прочитано", MediaStatus.names(2)[2])
    }
}
