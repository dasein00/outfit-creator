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
    }
}
