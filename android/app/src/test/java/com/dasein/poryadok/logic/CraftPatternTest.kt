package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CraftPatternTest {
    /** Картинка 40×20: левая половина красная, правая синяя, по центру белая полоса. */
    private fun sample(): IntArray = IntArray(40 * 20) { i ->
        val x = i % 40
        when {
            x in 19..20 -> 0xFFFFFFFF.toInt()
            x < 19 -> 0xFFC72B3B.toInt()
            else -> 0xFF253B73.toInt()
        }
    }

    @Test fun downscaleAveragesBlocks() {
        val small = CraftPattern.downscale(sample(), 40, 20, 4, 2)
        assertEquals(8, small.size)
        assertEquals(0xFFC72B3B.toInt(), small[0])
        assertEquals(0xFF253B73.toInt(), small[3])
        assertEquals(2, CraftPattern.heightFor(4, 40, 20))
    }

    @Test fun patternPicksDmcColors() {
        val p = CraftPattern.build(sample(), 40, 20, 3, dither = false)
        assertEquals(40 * 20, p.cells.size)
        val codes = p.colors.map { it.code }.toSet()
        assertTrue("красный 321 в $codes", "321" in codes)
        assertTrue("тёмно-синий 336 в $codes", "336" in codes)
        assertTrue(codes.any { it == "B5200" || it == "blanc" || it == "3865" || it == "746" })
        // Цвета отсортированы по частоте: красного и синего больше, чем белого.
        val counts = p.counts()
        assertEquals(counts.toList().sortedDescending(), counts.toList())
    }

    @Test fun ditherKeepsSizeAndPalette() {
        val grad = IntArray(30 * 10) { i -> val v = (i % 30) * 255 / 29; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val p = CraftPattern.build(grad, 30, 10, 4, dither = true)
        assertTrue(p.colors.size <= 4)
        assertTrue(p.cells.all { it in p.colors.indices })
    }

    @Test fun cleanupRemovesSingleCells() {
        val cells = IntArray(9) { 0 }.also { it[4] = 1 }
        val p = CraftPattern.Pattern(3, 3, cells, listOf(CraftPattern.DMC[0], CraftPattern.DMC[3]))
        assertEquals(0, CraftPattern.cleanup(p).cells[4])
    }

    @Test fun materialsAndSize() {
        assertEquals(1100.0, CraftPattern.need(CraftPattern.Kind.DIAMOND, 1000).amount, 0.01)
        assertEquals(1.0, CraftPattern.need(CraftPattern.Kind.CROSS, 1800).amount, 0.01)
        assertEquals(10.0, CraftPattern.need(CraftPattern.Kind.BEADS, 1000).amount, 0.01)
        assertEquals(25.0, CraftPattern.sizeCm(CraftPattern.Kind.DIAMOND, 100), 0.01)
        assertEquals(18.14, CraftPattern.sizeCm(CraftPattern.Kind.CROSS, 100, 14), 0.01)
        assertEquals(160, CraftPattern.cellsFor(CraftPattern.Kind.DIAMOND, 40.0))
        assertEquals(CraftPattern.DMC.size, CraftPattern.DMC.map { it.code }.toSet().size)
    }
}
