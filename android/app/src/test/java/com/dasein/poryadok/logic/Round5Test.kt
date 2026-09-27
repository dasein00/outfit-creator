package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchYearTest {
    @Test fun splitsTrailingYear() {
        assertEquals("Дюна" to 2021, MediaParse.splitYear("Дюна 2021"))
        assertEquals("Дюна" to 1984, MediaParse.splitYear("  Дюна (1984) "))
        assertEquals("Мастер и Маргарита" to 2024, MediaParse.splitYear("Мастер и Маргарита, 2024"))
        // Число без названия и «будущий» год — это часть названия.
        assertEquals("2012" to null, MediaParse.splitYear("2012"))
        assertEquals("Бегущий по лезвию 2049" to null, MediaParse.splitYear("Бегущий по лезвию 2049"))
        assertEquals("Интерстеллар" to null, MediaParse.splitYear("Интерстеллар"))
    }

    @Test fun filtersAndMerges() {
        val a = MediaHit(0, "Дюна", year = 2021, source = "Кинопоиск", externalId = "kp:1")
        val b = MediaHit(0, "Дюна", year = 1984, source = "Кинопоиск", externalId = "kp:2")
        val c = MediaHit(0, "Дюна", originalTitle = "Dune", year = 2021, source = "Википедия", externalId = "wd:Q1")
        val d = MediaHit(0, "Дюна: Часть вторая", year = 2024, source = "Википедия", externalId = "wd:Q2")
        assertEquals(listOf(a), MediaParse.filterYear(listOf(a, b), 2021))
        assertEquals(2, MediaParse.filterYear(listOf(a, b), null).size)
        val merged = MediaParse.mergeHits(listOf(listOf(a, b), listOf(c, d)))
        assertEquals(listOf("kp:1", "kp:2", "wd:Q2"), merged.map { it.externalId })
    }
}

class WikiParseTest {
    private val entities = """
        {"entities":{
          "Q13417189":{"id":"Q13417189",
            "labels":{"ru":{"value":"Интерстеллар"},"en":{"value":"Interstellar"}},
            "descriptions":{"ru":{"value":"фильм 2014 года"}},
            "claims":{
              "P31":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q11424"}}}}],
              "P577":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"time":"+2014-11-06T00:00:00Z"}}}},
                      {"rank":"normal","mainsnak":{"datavalue":{"value":{"time":"+2014-10-26T00:00:00Z"}}}}],
              "P57":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q25191"}}}}],
              "P161":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q188955"}}},"qualifiers":{"P453":[{"datavalue":{"value":{"id":"Q18127434"}}}]}},
                      {"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q37876"}}}}],
              "P2047":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"amount":"+169","unit":"x"}}}}],
              "P2603":[{"rank":"normal","mainsnak":{"datavalue":{"value":"258687"}}}],
              "P1476":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"text":"Interstellar","language":"en"}}}}]
            },
            "sitelinks":{"ruwiki":{"title":"Интерстеллар"},"enwiki":{"title":"Interstellar (film)"}}},
          "Q1":{"id":"Q1","labels":{"ru":{"value":"Вселенная"}},"descriptions":{"ru":{"value":"всё сущее"}},"claims":{}}
        }}
    """.trimIndent()

    @Test fun parsesFilmEntity() {
        val list = MediaParse.wikiEntities(entities)
        val e = list.first { it.id == "Q13417189" }
        assertEquals(2014, e.year)
        assertEquals(169, e.minutes)
        assertEquals("258687", e.kpId)
        assertEquals(MediaParse.MOVIE, MediaParse.wikiKind(e))
        assertNull(MediaParse.wikiKind(list.first { it.id == "Q1" }))
        val labels = mapOf("Q25191" to "Кристофер Нолан", "Q188955" to "Мэттью Макконахи", "Q18127434" to "Купер", "Q37876" to "Энн Хэтэуэй")
        val h = MediaParse.wikiHit(e, MediaParse.MOVIE, labels, "https://img/poster.jpg" to "Фантастический фильм.", null)
        assertEquals("Интерстеллар", h.title)
        assertEquals("Interstellar", h.originalTitle)
        assertEquals("Кристофер Нолан", h.creators)
        assertEquals("Мэттью Макконахи — Купер\nЭнн Хэтэуэй", h.cast)
        assertEquals("https://img/poster.jpg", h.posterUrl)
        assertEquals("2 ч 49 мин", h.length)
        assertEquals("wd:Q13417189", h.externalId)
    }

    @Test fun pagesFollowRedirects() {
        val q = """{"query":{"normalized":[{"from":"интерстеллар","to":"Интерстеллар"}],
            "pages":[{"title":"Интерстеллар","thumbnail":{"source":"https://img/p.jpg"},"extract":"Текст"}]}}"""
        val p = MediaParse.wikiPages(q)
        assertEquals("https://img/p.jpg", p["Интерстеллар"]?.first)
        assertEquals("Текст", p["интерстеллар"]?.second)
        assertEquals(listOf("Q1", "Q2"), MediaParse.wikiSearch("""{"search":[{"id":"Q1"},{"id":"Q2"}]}"""))
    }
}

class KpImportParseTest {
    @Test fun parsesCollectedItems() {
        val js = """[
          {"id":"258687","type":"film","title":"Интерстеллар (2014)","text":"Интерстеллар (2014)\nInterstellar, 169 мин.","img":"//avatars.mds.yandex.net/get-kinopoisk-image/1/abc/68x102","vote":"10"},
          {"id":"77044","type":"series","title":"Друзья","text":"Друзья\nFriends, 1994 – 2004\nМоя оценка: 9","img":"","vote":""},
          {"id":"1","type":"film","title":"8.6","text":"","img":"","vote":""}
        ]"""
        val items = MediaParse.kpItems(js)
        assertEquals(2, items.size)
        val a = items[0]
        assertEquals("Интерстеллар", a.title)
        assertEquals(2014, a.year)
        assertEquals(10, a.vote)
        assertEquals("https://avatars.mds.yandex.net/get-kinopoisk-image/1/abc/300x450", a.posterUrl)
        val b = items[1]
        assertTrue(b.series)
        assertEquals(1994, b.year)
        assertEquals(9, b.vote)
    }
}
