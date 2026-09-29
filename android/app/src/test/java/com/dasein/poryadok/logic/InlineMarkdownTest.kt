package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineMarkdownTest {
    @Test fun findsStyles() {
        val t = "Это **важно**, *очень* и ~~нет~~, `код` и ==маркер=="
        val s = InlineMarkdown.spans(t).map { it.style to t.substring(it.start, it.end) }
        assertEquals(
            listOf(
                MdStyle.BOLD to "**важно**", MdStyle.ITALIC to "*очень*", MdStyle.STRIKE to "~~нет~~",
                MdStyle.CODE to "`код`", MdStyle.HIGHLIGHT to "==маркер==",
            ),
            s,
        )
        assertEquals("Это важно, очень и нет, код и маркер", InlineMarkdown.plain(t))
    }

    @Test fun ignoresUnclosedAndNested() {
        assertEquals(0, InlineMarkdown.spans("2 * 3 = 6 и **не закрыто").size)
        val t = "**жирный *курсив* внутри**"
        val s = InlineMarkdown.spans(t)
        assertEquals(listOf(MdStyle.BOLD, MdStyle.ITALIC), s.map { it.style })
        assertEquals("жирный курсив внутри", InlineMarkdown.plain(t))
        assertEquals(1, InlineMarkdown.spans("`a *b* c`").size)
    }

    @Test fun togglesSelection() {
        val (t, a, b) = InlineMarkdown.toggle("привет мир", 7, 10, MdStyle.BOLD)
        assertEquals("привет **мир**", t)
        assertEquals(9, a); assertEquals(12, b)
        val (t2, a2, b2) = InlineMarkdown.toggle(t, a, b, MdStyle.BOLD)
        assertEquals("привет мир", t2)
        assertEquals(7, a2); assertEquals(10, b2)
    }
}
