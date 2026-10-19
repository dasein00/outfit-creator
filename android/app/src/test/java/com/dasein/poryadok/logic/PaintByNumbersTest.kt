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
        val codes = PaintByNumbers.PENCILS_ALL.map { it.code }.toSet()
        PaintByNumbers.SETS.forEach { (_, l) -> assertTrue(codes.containsAll(l)) }
        assertEquals(48, PaintByNumbers.CATALOG.size)
        assertEquals(264, codes.size)
        assertEquals(listOf(12, 24, 36, 48, 64, 128, 264), PaintByNumbers.SETS.map { it.second.size })
        PaintByNumbers.Medium.entries.forEach { m ->
            val mc = m.catalog.map { it.code }.toSet()
            assertEquals(m.catalog.size, mc.size)
            m.sets.forEach { (_, l) -> assertTrue(mc.containsAll(l)) }
            // Наборы: 12, 24, 36, 48 цветов, дальше — по возрастанию, последний — весь каталог.
            val sizes = m.sets.map { it.second.size }
            assertEquals("$m", listOf(12, 24, 36, 48), sizes.take(4))
            assertEquals("$m", sizes.sorted(), sizes)
            assertEquals("$m", m.catalog.size, sizes.last())
            m.sets.forEach { (_, l) -> assertEquals("$m", l.size, l.toSet().size) }
        }
        assertEquals(48, PaintByNumbers.WATERCOLOR_PENCILS.size)
        assertEquals(216, PaintByNumbers.ALCOHOL_MARKERS.size)
    }

    @Test fun sharpPhotoEdgesInsideOneColorBecomeContours() {
        val w = 300; val h = 240
        // Круг и фон близких оттенков, которые попадают в один карандаш, — граница всё равно должна обводиться.
        val px = IntArray(w * h) { i -> val x = i % w; val y = i / w; if ((x - 150) * (x - 150) + (y - 120) * (y - 120) < 70 * 70) 0xD8B49A else 0xB89478 }
        val two = listOf(PaintByNumbers.Pencil("a", "кожа", 0xC8A488), PaintByNumbers.Pencil("b", "чёрный", 0x202020))
        assertTrue(PaintByNumbers.build(px, w, h, two, 2, 2, 100).edges.size > 200)
        assertEquals(0, PaintByNumbers.build(px, w, h, two, 2, 2, 0).edges.size)
        // Шум фото без краёв контуров не даёт.
        val rnd = java.util.Random(1)
        val noise = IntArray(w * h) { val v = 0xB0 + rnd.nextInt(17) - 8; (v shl 16) or ((v - 0x20) shl 8) or (v - 0x38) }
        assertEquals(0, PaintByNumbers.build(noise, w, h, two, 2, 2, 100).edges.size)
    }
}
