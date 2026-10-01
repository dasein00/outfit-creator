package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextQueryTest {
    @Test fun wordsIgnoreEndingsAndYo() {
        assertTrue(TextQuery.matches("Пчела приносит мёд", "пчёлы мед"))
        assertTrue(TextQuery.matches("Полёт на Луну", "луна"))
        assertFalse(TextQuery.matches("Полёт на Марс", "луна"))
    }

    @Test fun phraseExcludeAndOr() {
        assertTrue(TextQuery.matches("Чёрная дыра в центре галактики", "\"черная дыра\""))
        assertFalse(TextQuery.matches("Чёрный кот и дыра в заборе", "\"чёрная дыра\""))
        assertFalse(TextQuery.matches("Кофе и чай", "чай -кофе"))
        assertTrue(TextQuery.matches("Собаки лают", "кошки|собаки"))
        assertTrue(TextQuery.matches("Кошки мурлычут", "кошки или собаки"))
        assertFalse(TextQuery.matches("Птицы поют", "кошки или собаки"))
    }

    @Test fun emptyQueryMatchesAll() {
        assertTrue(TextQuery.matches("что угодно", ""))
        assertTrue(TextQuery.parse("  ").isEmpty)
    }

    @Test fun rangesCoverWholeWord() {
        val text = "Пчёлы танцуют"
        val r = TextQuery.ranges(text, TextQuery.parse("пчела"))
        assertEquals(listOf(0 until 5), r)
    }

    @Test fun paging() {
        assertEquals(1, Paging.pages(0, 20))
        assertEquals(3, Paging.pages(41, 20))
        assertEquals(listOf(40), Paging.slice((0 until 41).toList(), 9, 20))
        assertEquals(listOf(0, 1, 2, 3, -1, 99), Paging.window(0, 100))
        assertEquals(listOf(0, -1, 49, 50, 51, -1, 99), Paging.window(50, 100))
        assertEquals((0 until 5).toList(), Paging.window(2, 5))
    }
}
