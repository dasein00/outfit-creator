package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class EnDictTest {
    private val sample = listOf(
        "# comment",
        "run\tɹʌn\t322\tсущ.~the act of running~бег, пробежка;;гл.~to move quickly on two feet~бегать, бежать",
        "runner\t\t2900\tсущ.~one who runs~бегун",
        "give up\t\t0\tгл.~to abandon~сдаваться, бросать",
        "house\thaʊs\t213\tсущ.~human abode~дом",
        "home run\t\t0\tсущ.~baseball~хоум-ран",
    ).joinToString("\n")

    @Test fun parsesLines() {
        val all = EnDict.parse(sample)
        assertEquals(5, all.size)
        val run = all.first { it.en == "run" }
        assertEquals("ɹʌn", run.ipa)
        assertEquals(2, run.senses.size)
        assertEquals("бег, пробежка", run.shortRu)
        assertTrue(run.common)
    }

    @Test fun searchesBothWays() {
        val all = EnDict.parse(sample)
        assertEquals(listOf("run", "runner", "home run"), EnDict.search(all, "run").map { it.en })
        assertEquals("house", EnDict.search(all, "дом").first().en)
        assertEquals("run", EnDict.search(all, "бежать").first().en)
        assertTrue(EnDict.search(all, "   ").isEmpty())
    }

    @Test fun bundledDictionary() {
        val f = listOf("src/main/assets/tutor/dict.tsv", "app/src/main/assets/tutor/dict.tsv").map { File(it) }.firstOrNull { it.exists() } ?: return
        val all = EnDict.parse(f.readText())
        assertTrue(all.size > 30000)
        assertTrue(EnDict.search(all, "beautiful").first().shortRu.contains("красив"))
        assertEquals("cat", EnDict.search(all, "кошка").first { ' ' !in it.en }.en)
    }

    @Test fun floatingHolidayRules() {
        assertEquals(LocalDate.of(2026, 11, 27), HolidayRules.dateIn("nth:11:4:4:1", 2026)) // Чёрная пятница
        assertEquals(LocalDate.of(2026, 6, 20), HolidayRules.dateIn("near:06-17:6", 2026))  // день жонглирования
        assertEquals(LocalDate.of(2026, 1, 25), HolidayRules.dateIn("nth:01:7:-1", 2026))  // день без интернета
        assertEquals(LocalDate.of(2026, 5, 29), HolidayRules.dateIn("nth:05:5:-1", 2026))  // день соседей
        assertEquals("Суббота, ближайшая к 17 июня", HolidayRules.describe("near:06-17:6"))
        assertEquals(LocalDate.of(2026, 2, 16), HolidayRules.dateIn("ew:-48", 2026))  // День булочек в Исландии — понедельник
        assertEquals(LocalDate.of(2026, 2, 12), HolidayRules.dateIn("ew:-52", 2026))  // Бабий четверг
        assertEquals(LocalDate.of(2026, 6, 7), HolidayRules.dateIn("ew:63", 2026))    // Эль Колачо — воскресенье
        assertEquals(LocalDate.of(2026, 8, 26), HolidayRules.dateIn("nth:08:3:-1", 2026)) // Ла Томатина
    }
}

class CultureDayTest {
    @Test fun parsesBirthsAndEvents() {
        val wiki = """{"births":[{"text":"Брэд Питт, американский актёр","year":1963,"pages":[{"title":"Питт,_Брэд","extract":"Американский актёр и продюсер.","thumbnail":{"source":"https://x/p.jpg"}}]},
            {"text":"Иван Иванов, российский футболист","year":1990,"pages":[]}],
            "events":[{"text":"вышел фильм «Титаник»","year":1997,"pages":[]},{"text":"подписан договор","year":1800,"pages":[]}]}"""
        val l = CultureDay.parseWiki(wiki)
        assertEquals(listOf("Брэд Питт", "вышел фильм «Титаник»"), l.map { it.name })
        assertEquals("США", l[0].region)
        assertEquals(CultureDay.BIRTH, l[0].kind)
        assertEquals(CultureDay.EVENT, l[1].kind)
        assertTrue(CultureDay.isCulturePerson("советская балерина"))
        assertTrue(!CultureDay.isCulturePerson("российский футболист"))
        assertEquals("Россия", CultureDay.regionOf("советский актёр"))
    }

    @Test fun bundledCulture() {
        val f = listOf("src/main/assets/culture/culture.json", "app/src/main/assets/culture/culture.json").map { File(it) }.firstOrNull { it.exists() } ?: return
        val all = CultureDay.parseBuiltIn(f.readText())
        assertTrue(all.values.sumOf { it.size } > 150)
        assertTrue(all["12-18"].orEmpty().any { it.name == "Стивен Спилберг" })
    }
}

class PhrasesTest {
    @Test fun bundledPhrases() {
        val f = listOf("src/main/assets/tutor/phrases.json", "app/src/main/assets/tutor/phrases.json").map { File(it) }.firstOrNull { it.exists() } ?: return
        val all = Phrases.parse(f.readText())
        assertTrue(all.size >= 120)
        assertTrue(all.any { it.cat == Phrases.IDIOM } && all.any { it.cat == Phrases.PHRASE })
        assertEquals(all.size, all.map { it.key }.toSet().size)
        val p = all.first()
        val o = Phrases.options(p, all, kotlin.random.Random(1))
        assertEquals(4, o.size); assertTrue(p in o)
        assertEquals(10, Phrases.session(all, emptyMap(), 100, 10, kotlin.random.Random(1)).size)
        assertTrue(Phrases.search(all, "лёд").any { it.en == "break the ice" })
    }
}
