package com.dasein.poryadok.logic

import com.dasein.poryadok.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class TutorTest {
    private fun file(rel: String) = listOf("src/main/assets/$rel", "app/src/main/assets/$rel", "/home/user/outfit-creator/android/app/src/main/assets/$rel").map { File(it) }.firstOrNull { it.exists() }

    private val words by lazy { Tutor.parse(file("tutor/words.json")!!.readText()) }

    @Test fun dictionaryIsValid() {
        assertTrue(words.size >= 300)
        words.forEach { w ->
            assertTrue(w.en, w.ru.isNotBlank() && w.ex.isNotBlank() && w.exRu.isNotBlank())
            assertTrue(w.en, w.level in Tutor.LEVELS && w.topic in Tutor.TOPICS && w.freq in 1..3)
        }
        assertEquals(words.size, words.map { it.id }.distinct().size)
        // В большинстве примеров слово можно найти для упражнения «Вставь слово».
        assertTrue(words.count { Tutor.cloze(it) != null } > words.size * 0.7)
    }

    @Test fun leitnerIntervals() {
        var p: Tutor.Progress? = null
        val today = 1000L
        p = Tutor.answer(p, true, today); assertEquals(1, p.box); assertEquals(today + 1, p.due)
        p = Tutor.answer(p, true, today); assertEquals(2, p.box); assertEquals(today + 2, p.due)
        p = Tutor.answer(p, true, today); assertEquals(today + 4, p.due)
        p = Tutor.answer(p, false, today); assertEquals(1, p.box); assertEquals(today + 1, p.due); assertEquals(1, p.wrong)
        assertTrue(Tutor.known(null, today).box >= Tutor.LEARNED_BOX)
    }

    @Test fun dailySetRespectsSettings() {
        val s = Tutor.Settings(levels = listOf("B1"), freq = 1, topics = emptyList(), perDay = 10, mode = 0)
        val set = Tutor.dailyNew(words, emptyMap(), s, 20000)
        assertEquals(10, set.size)
        assertTrue(set.all { it.level == "B1" && it.freq == 1 })
        // Один и тот же день — тот же набор; другой день — другая тема.
        assertEquals(set, Tutor.dailyNew(words, emptyMap(), s, 20000))
        val topic = Tutor.topicOfDay(s, 20000, words)
        val avail = words.count { it.topic == topic && Tutor.matches(it, s) }
        assertEquals(minOf(avail, 10), set.count { it.topic == topic })
        // Уже начатые слова не попадают в новые.
        val started = set.associate { it.id to Tutor.Progress(box = 1, due = 20001) }
        assertTrue(Tutor.dailyNew(words, started, s, 20000).none { it.id in started })
        val onlyIt = s.copy(topics = listOf("it"), levels = Tutor.LEVELS, freq = 3, mode = 1)
        assertTrue(Tutor.dailyNew(words, emptyMap(), onlyIt, 5).all { it.topic == "it" })
    }

    @Test fun exercises() {
        val w = words.first { it.en == "deadline" }
        assertTrue(Tutor.typedOk(" Deadline ", w))
        assertTrue(Tutor.typedOk("dedline", w))
        assertFalse(Tutor.typedOk("time", w))
        assertEquals("The ___ is next Friday.", Tutor.cloze(w))
        val opts = Tutor.options(w, words, 4, Random(1), ruAnswers = true)
        assertEquals(4, opts.size); assertTrue(w in opts); assertEquals(4, opts.map { it.ru }.distinct().size)
        assertEquals(3, Tutor.streak(setOf(10L, 11L, 12L), 12)); assertEquals(3, Tutor.streak(setOf(10L, 11L, 12L), 13)); assertEquals(0, Tutor.streak(setOf(10L), 13))
    }

    @Test fun historyAndLayout() {
        val built = HistoryDay.parseBuiltIn(file("history/events.json")!!.readText())
        assertTrue(built.values.sumOf { it.size } >= 80)
        assertTrue(built.values.flatten().all { it.text.isNotBlank() && it.context.isNotBlank() })
        val wiki = """{"events":[{"text":"открыто кабаре","year":1889,"pages":[{"title":"1889_год","extract":"Год"},{"title":"Мулен_Руж","titles":{"normalized":"Мулен Руж"},"extract":"Мулен Руж — кабаре в Париже, открытое в 1889 году на бульваре Клиши.","thumbnail":{"source":"https://x/y.jpg"},"content_urls":{"mobile":{"page":"https://ru.m.wikipedia.org/wiki/Мулен_Руж"}}}]},{"text":"родился кто-то","year":1950,"pages":[]}]}"""
        val ev = HistoryDay.parseWiki(wiki)
        assertEquals(2, ev.size)
        assertEquals("Мулен Руж", ev[0].title); assertTrue(ev[0].context.startsWith("Мулен Руж —")); assertEquals("https://x/y.jpg", ev[0].image)
        assertEquals("Открыто кабаре", ev[0].text)
        val merged = HistoryDay.merge(built["10-06"].orEmpty(), ev)
        assertEquals(1889, merged.first().year)
        assertEquals(2, merged.size) // встроенное 1889 + 1950 из Википедии (1889 из Википедии — повтор)
        val old = listOf(HomeLayout.Entry("greeting"), HomeLayout.Entry("holiday"), HomeLayout.Entry("tasks"))
        assertEquals(listOf("greeting", "holiday", "tutor", "history", "culture", "tasks"), HomeLayout.migrate(old, 1).map { it.id })
        assertEquals(listOf("greeting", "holiday", "culture", "tasks"), HomeLayout.migrate(old, 2).map { it.id })
        assertEquals(old, HomeLayout.migrate(old, 3))
        assertNull(HomeLayout.decode(""))
    }
}
