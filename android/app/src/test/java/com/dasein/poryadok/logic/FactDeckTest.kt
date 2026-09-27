package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class FactDeckTest {
    private val facts = (1..20).map { Fact(it, "Тело", "Факт $it") }

    @Test
    fun nextNeverRepeatsUntilAllRead() {
        val rnd = Random(7)
        var st = FactDeck.normalize(facts, FactDeck.State(), rnd)
        val seen = mutableListOf(FactDeck.current(facts, st)!!.id)
        repeat(facts.size - 1) {
            val (n, newRound) = FactDeck.next(facts, st, rnd)
            assertFalse(newRound)
            st = n
            seen += FactDeck.current(facts, st)!!.id
        }
        assertEquals(facts.size, seen.toSet().size)
        assertEquals(facts.size, FactDeck.read(st))
        val (n, newRound) = FactDeck.next(facts, st, rnd)
        assertTrue(newRound)
        assertEquals(1, n.round)
        assertTrue(FactDeck.current(facts, n)!!.id != seen.last())
    }

    @Test
    fun backThenForwardReturnsSameFacts() {
        val rnd = Random(1)
        var st = FactDeck.normalize(facts, FactDeck.State(), rnd)
        repeat(4) { st = FactDeck.next(facts, st, rnd).first }
        val order = st.history
        st = FactDeck.prev(facts, st, rnd)
        st = FactDeck.prev(facts, st, rnd)
        assertEquals(order[2], FactDeck.current(facts, st)!!.id)
        st = FactDeck.next(facts, st, rnd).first
        assertEquals(order[3], FactDeck.current(facts, st)!!.id)
        st = FactDeck.next(facts, st, rnd).first
        assertEquals(order[4], FactDeck.current(facts, st)!!.id)
        // Назад с первого — остаёмся на первом.
        repeat(10) { st = FactDeck.prev(facts, st, rnd) }
        assertEquals(order[0], FactDeck.current(facts, st)!!.id)
    }

    @Test
    fun removedFactsAreDroppedFromHistory() {
        val st = FactDeck.State(listOf(999, 3, 5), pos = 2)
        val n = FactDeck.normalize(facts, st, Random(0))
        assertEquals(listOf(3, 5), n.history)
        assertEquals(1, n.pos)
        assertEquals(listOf(3, 5), FactDeck.decode(FactDeck.encode(n.history)))
    }

    @Test
    fun bundledFactsAreWellFormed() {
        val dir = listOf(File("src/main/assets/facts"), File("app/src/main/assets/facts")).first { it.isDirectory }
        val lines = dir.listFiles()!!.filter { it.name.endsWith(".txt") }.flatMap { it.readLines() }.filter { it.isNotBlank() }
        val parsed = FactDeck.parse(lines.joinToString("\n"))
        assertEquals("каждая строка — «Тема|Текст» без повторов", lines.size, parsed.size)
        assertTrue("фактов должно быть много: ${parsed.size}", parsed.size >= 500)
        parsed.forEach { f ->
            assertTrue(f.text, f.text.length in 40..400)
            assertTrue(f.text, f.tag.isNotBlank() && f.tag.length < 20)
        }
    }
}
