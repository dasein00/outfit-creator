package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconSheetTest {
    /** Белый лист, 3×2 иконки; у первой — «пар» отдельными штрихами над миской. */
    private fun sheet(transparent: Boolean): Triple<IntArray, Int, Int> {
        val w = 600; val h = 400
        val bg = if (transparent) 0x00000000 else 0xFFFFFFFF.toInt()
        val px = IntArray(w * h) { bg }
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) { for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = c }
        for (r in 0..1) for (c in 0..2) {
            val cx = 100 + c * 200; val cy = 100 + r * 200
            rect(cx - 50, cy - 20, cx + 50, cy + 50, 0xFFFF9527.toInt())
            rect(cx - 30, cy - 10, cx + 30, cy + 30, 0xFFF7E4C0.toInt()) // светлая деталь внутри
            if (r == 0 && c == 0) { rect(cx - 20, cy - 45, cx - 14, cy - 28, 0xFFFF9527.toInt()); rect(cx + 10, cy - 45, cx + 16, cy - 28, 0xFFFF9527.toInt()) }
        }
        return Triple(px, w, h)
    }

    @Test fun sixIconsInReadingOrder() {
        listOf(false, true).forEach { tr ->
            val (px, w, h) = sheet(tr)
            val boxes = IconSheet.slice(px, w, h)
            assertEquals(6, boxes.size)
            assertTrue(boxes[0].cx < boxes[1].cx && boxes[1].cx < boxes[2].cx)
            assertTrue(boxes[3].cy > boxes[0].cy)
            // Пар склеен с миской: верх первой рамки выше миски.
            assertTrue(boxes[0].y0 <= 100 - 45 + 1)
        }
    }

    @Test fun cutRemovesOnlyOuterBackground() {
        val (px, w, h) = sheet(false)
        val bg = IconSheet.background(px, w, h)
        val b = IconSheet.slice(px, w, h)[1]
        val (out, side) = IconSheet.cut(px, w, h, b, bg)
        assertEquals(0, out[0] ushr 24)
        // Центр — светлая деталь, не фон: остаётся.
        assertTrue((out[side / 2 * side + side / 2] ushr 24) > 0)
    }
}
