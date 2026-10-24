package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRefsTest {
    @Test fun linksUrlsTags() {
        val t = "Сварить [[рецепт:Борщ]], читать [[ Мастер и Маргарита ]] #кухня см. https://example.com/a?b=1."
        val r = NoteRefs.refs(t)
        assertEquals(listOf(NoteRefs.Kind.LINK, NoteRefs.Kind.LINK, NoteRefs.Kind.TAG, NoteRefs.Kind.URL), r.map { it.kind })
        assertEquals(NoteRefs.Target.RECIPE, r[0].target)
        assertEquals("Борщ", r[0].name)
        assertEquals("Борщ", t.substring(r[0].nameStart, r[0].nameEnd))
        assertEquals("Мастер и Маргарита", t.substring(r[1].nameStart, r[1].nameEnd))
        assertEquals(NoteRefs.Target.ANY, r[1].target)
        assertEquals("кухня", r[2].name)
        assertEquals("https://example.com/a?b=1", r[3].name)
        assertEquals("[[книга:Дюна]]", NoteRefs.link(NoteRefs.Target.BOOK, "Дюна"))
        assertEquals(setOf("идеи"), NoteRefs.tags("цвет #fff? нет: &#123; и #идеи"))
    }
}
