package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StoneColorsTest {
    /** Белый лист с жёлтым оттенком лампы, на нём кучки страз трёх цветов с бликами и шумом. */
    private fun photo(): Triple<IntArray, Int, Int> {
        val w = 300; val h = 200
        val rnd = Random(1)
        fun tint(c: Int): Int { // тёплый свет: синего меньше
            val r = (c shr 16 and 255); val g = (c shr 8 and 255); val b = (c and 255)
            return ((r * 0.98).toInt() shl 16) or ((g * 0.94).toInt() shl 8) or (b * 0.80).toInt()
        }
        val px = IntArray(w * h) { tint(0xF4F4F4) }
        val colors = listOf(0xC0392B, 0x2E86C1, 0x27AE60)
        colors.forEachIndexed { k, c ->
            // Две кучки каждого цвета.
            repeat(2) { j ->
                val cx = 40 + k * 90 + j * 30; val cy = 50 + j * 90
                for (y in cy - 14..cy + 14) for (x in cx - 14..cx + 14) {
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > 196) continue
                    val n = rnd.nextInt(-12, 12)
                    val glint = rnd.nextInt(100) < 6
                    val base = if (glint) 0xFFFFFF else c
                    val r = ((base shr 16 and 255) + n).coerceIn(0, 255)
                    val g = ((base shr 8 and 255) + n).coerceIn(0, 255)
                    val b = ((base and 255) + n).coerceIn(0, 255)
                    px[y * w + x] = tint((r shl 16) or (g shl 8) or b)
                }
            }
        }
        return Triple(px, w, h)
    }

    @Test fun findsThreeColorsUnderWarmLight() {
        val (px, w, h) = photo()
        val found = StoneColors.detect(px, w, h)
        assertEquals(found.joinToString { "%06X".format(it.rgb) }, 3, found.size)
        val targets = listOf(0xC0392B, 0x2E86C1, 0x27AE60)
        targets.forEach { t ->
            val best = found.minOf { StoneColors.deltaE(CraftPattern.lab(it.rgb), CraftPattern.lab(t)) }
            assertTrue("цвет %06X найден с ΔE=$best".format(t), best < 12)
        }
        assertTrue(found.sumOf { it.pieces } >= 6)
    }

    @Test fun paperIsWhiteBalanced() {
        val (px, _, _) = photo()
        val paper = StoneColors.paper(px)!!
        val wb = StoneColors.whiteBalance(intArrayOf(paper), paper)[0]
        val l = CraftPattern.lab(wb)
        assertTrue(kotlin.math.abs(l[1]) < 2 && kotlin.math.abs(l[2]) < 2)
    }

    @Test fun customPaletteOnly() {
        val mine = listOf(CraftPattern.Thread("М1", "Красный", 0xC0392B), CraftPattern.Thread("М2", "Синий", 0x2E86C1))
        val px = IntArray(100) { if (it < 50) 0xD04030 else 0x3080D0 }
        val p = CraftPattern.build(px, 10, 10, 24, false, custom = mine)
        assertTrue(p.colors.all { it.custom })
        assertEquals(setOf("М1", "М2"), p.colors.map { it.code }.toSet())
        assertEquals("Мой №1", mine[0].label)
    }
}
