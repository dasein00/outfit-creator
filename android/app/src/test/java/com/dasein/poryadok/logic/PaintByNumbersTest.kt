package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PaintByNumbersTest {
    /** Небо, трава и солнце с шумом фото: три области, номера внутри своих областей. */
    @Test fun skyGrassSun() {
        val w = 120; val h = 80
        val rnd = Random(3)
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val base = when {
                (x - 90) * (x - 90) + (y - 20) * (y - 20) < 100 -> 0xFFD600
                y < 50 -> 0x42A5F5
                else -> 0x43A047
            }
            val n = rnd.nextInt(-10, 10)
            fun c(v: Int) = (v + n).coerceIn(0, 255)
            (c(base shr 16 and 255) shl 16) or (c(base shr 8 and 255) shl 8) or c(base and 255)
        }
        val r = PaintByNumbers.build(px, w, h, PaintByNumbers.CATALOG, 12, 1)
        assertEquals(r.colors.map { it.name }.toString(), 3, r.colors.size)
        assertEquals(setOf("Голубой", "Травяной", "Жёлтый"), r.colors.map { it.name }.toSet())
        assertTrue(r.regions.size in 3..5)
        r.regions.forEach { g -> assertEquals(g.color, r.cells[g.y * w + g.x]) }
        assertTrue(PaintByNumbers.outline(r.cells, w, h).isNotEmpty())
    }

    @Test fun smallSpotsMerge() {
        val w = 40; val h = 40
        val c = IntArray(w * h) { if (it == 820) 1 else 0 }
        val m = PaintByNumbers.mergeSmall(c, w, h, 5)
        assertTrue(m.all { it == 0 })
    }

    @Test fun setsUseCatalogCodes() {
        val codes = PaintByNumbers.CATALOG.map { it.code }.toSet()
        PaintByNumbers.SETS.forEach { (_, l) -> assertTrue(codes.containsAll(l)) }
        assertEquals(48, PaintByNumbers.CATALOG.size)
    }
}
