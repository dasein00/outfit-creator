package com.dasein.poryadok.logic

import com.dasein.poryadok.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLayoutTest {
    @Test fun encodeDecodeAndRows() {
        val l = listOf(HomeLayout.Entry("greeting"), HomeLayout.Entry("go:craft", 0), HomeLayout.Entry("go:notes", 0), HomeLayout.Entry("go:wardrobe", 0), HomeLayout.Entry("go:x", 1), HomeLayout.Entry("pressure", 2))
        assertEquals(l, HomeLayout.decode(HomeLayout.encode(l)))
        assertNull(HomeLayout.decode(""))
        // Неизвестные плашки отбрасываются.
        assertEquals(listOf(HomeLayout.Entry("tasks", 0)), HomeLayout.decode("nope|1;tasks|0"))
        // Три трети — один ряд, половина — новый ряд, крупная плашка — отдельно.
        assertEquals(listOf(listOf(0), listOf(1, 2, 3), listOf(4), listOf(5)), HomeLayout.rows(l))
        assertEquals(HomeLayout.DEFAULT.size, HomeLayout.DEFAULT.map { it.id }.distinct().size)
    }
}
