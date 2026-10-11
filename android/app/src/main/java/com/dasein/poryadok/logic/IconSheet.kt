package com.dasein.poryadok.logic

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Нарезка картинки с набором иконок (сетка от ChatGPT и т. п.) на отдельные иконки: фон определяется сам
 * (прозрачный или однотонный по краям), части одной иконки (пар, искры) склеиваются, порядок — по строкам.
 */
object IconSheet {
    data class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val w get() = x1 - x0
        val h get() = y1 - y0
        val cx get() = (x0 + x1) / 2
        val cy get() = (y0 + y1) / 2
        fun near(o: Box, gap: Int) = x0 - gap < o.x1 && o.x0 - gap < x1 && y0 - gap < o.y1 && o.y0 - gap < y1
        fun union(o: Box) = Box(min(x0, o.x0), min(y0, o.y0), max(x1, o.x1), max(y1, o.y1))
    }

    /** Фон: null — прозрачный, иначе цвет по краям картинки. */
    fun background(px: IntArray, w: Int, h: Int): Int? {
        val border = ArrayList<Int>()
        for (x in 0 until w step max(1, w / 200)) { border += px[x]; border += px[(h - 1) * w + x] }
        for (y in 0 until h step max(1, h / 200)) { border += px[y * w]; border += px[y * w + w - 1] }
        val transparent = border.count { (it ushr 24) < 40 }
        if (transparent > border.size / 2) return null
        val r = border.map { it shr 16 and 255 }.sorted()[border.size / 2]
        val g = border.map { it shr 8 and 255 }.sorted()[border.size / 2]
        val b = border.map { it and 255 }.sorted()[border.size / 2]
        return (r shl 16) or (g shl 8) or b
    }

    private fun dist(c: Int, bg: Int): Double {
        val r = (c shr 16 and 255) - (bg shr 16 and 255); val g = (c shr 8 and 255) - (bg shr 8 and 255); val b = (c and 255) - (bg and 255)
        return sqrt((r * r + g * g + b * b).toDouble())
    }

    fun foreground(px: IntArray, bg: Int?): BooleanArray =
        BooleanArray(px.size) { i -> if (bg == null) (px[i] ushr 24) > 60 else (px[i] ushr 24) > 60 && dist(px[i], bg) > 38 }

    /** Рамки иконок в порядке чтения (сверху вниз, слева направо). */
    fun slice(px: IntArray, w: Int, h: Int): List<Box> {
        val bg = background(px, w, h)
        val fg = foreground(px, bg)
        // Расширяем маску, чтобы части одной иконки стали одной областью.
        val r = max(2, min(w, h) / 90)
        val rowGrow = BooleanArray(fg.size)
        for (y in 0 until h) {
            var last = -10_000
            for (x in 0 until w) { if (fg[y * w + x]) last = x; if (x - last <= r) rowGrow[y * w + x] = true }
            last = 10_000
            for (x in w - 1 downTo 0) { if (fg[y * w + x]) last = x; if (last - x <= r) rowGrow[y * w + x] = true }
        }
        val grown = BooleanArray(fg.size)
        for (x in 0 until w) {
            var last = -10_000
            for (y in 0 until h) { if (rowGrow[y * w + x]) last = y; if (y - last <= r) grown[y * w + x] = true }
            last = 10_000
            for (y in h - 1 downTo 0) { if (rowGrow[y * w + x]) last = y; if (last - y <= r) grown[y * w + x] = true }
        }
        val seen = BooleanArray(fg.size)
        val stack = IntArray(fg.size)
        var boxes = ArrayList<Box>()
        for (s in grown.indices) {
            if (!grown[s] || seen[s]) continue
            var sp = 0; stack[sp++] = s; seen[s] = true
            var x0 = w; var y0 = h; var x1 = 0; var y1 = 0; var n = 0
            while (sp > 0) {
                val i = stack[--sp]
                val x = i % w; val y = i / w
                if (fg[i]) { x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x + 1); y1 = max(y1, y + 1); n++ }
                if (x > 0 && grown[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < w - 1 && grown[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && grown[i - w] && !seen[i - w]) { seen[i - w] = true; stack[sp++] = i - w }
                if (y < h - 1 && grown[i + w] && !seen[i + w]) { seen[i + w] = true; stack[sp++] = i + w }
            }
            if (n > 0) boxes += Box(x0, y0, x1, y1)
        }
        // Склеиваем рамки, которые почти касаются (иконка из нескольких частей).
        val gap = r * 2
        var merged = true
        while (merged) {
            merged = false
            val out = ArrayList<Box>()
            for (b in boxes) {
                val j = out.indexOfFirst { it.near(b, gap) }
                if (j >= 0) { out[j] = out[j].union(b); merged = true } else out += b
            }
            boxes = out
        }
        // Мелочь (крошки, шум) — не иконки.
        val big = boxes.maxOfOrNull { max(it.w, it.h) } ?: return emptyList()
        val keep = boxes.filter { max(it.w, it.h) >= big * 0.28 && min(it.w, it.h) >= 6 }
        // Порядок чтения: строки по центру.
        val sorted = keep.sortedBy { it.cy }
        val rows = ArrayList<MutableList<Box>>()
        sorted.forEach { b ->
            val row = rows.lastOrNull()
            if (row != null && abs(row.map { it.cy }.average() - b.cy) < row.map { it.h }.average() * 0.5) row += b else rows += mutableListOf(b)
        }
        return rows.flatMap { row -> row.sortedBy { it.cx } }
    }

    /**
     * Квадратная иконка из рамки [b]: фон, связанный с краем, становится прозрачным; поля — [pad] от размера.
     * Возвращает пиксели side×side.
     */
    fun cut(px: IntArray, w: Int, h: Int, b: Box, bg: Int?, pad: Double = 0.06): Pair<IntArray, Int> {
        val side = (max(b.w, b.h) * (1 + 2 * pad)).toInt().coerceAtLeast(1)
        val ox = b.x0 - (side - b.w) / 2
        val oy = b.y0 - (side - b.h) / 2
        val out = IntArray(side * side)
        for (y in 0 until side) for (x in 0 until side) {
            val sx = ox + x; val sy = oy + y
            // Соседние иконки в квадрат не попадают — берём только пиксели внутри рамки.
            if (sx < b.x0 || sy < b.y0 || sx >= b.x1 || sy >= b.y1 || sx >= w || sy >= h || sx < 0 || sy < 0) continue
            out[y * side + x] = px[sy * w + sx]
        }
        if (bg != null) {
            // Заливка от краёв: однотонный фон вокруг иконки → прозрачный, светлые детали внутри остаются.
            val stack = IntArray(out.size)
            val seen = BooleanArray(out.size)
            var sp = 0
            fun push(i: Int) { if (!seen[i]) { seen[i] = true; stack[sp++] = i } }
            for (x in 0 until side) { push(x); push((side - 1) * side + x) }
            for (y in 0 until side) { push(y * side); push(y * side + side - 1) }
            while (sp > 0) {
                val i = stack[--sp]
                val c = out[i]
                if ((c ushr 24) != 0 && dist(c, bg) > 38) continue
                out[i] = 0
                val x = i % side; val y = i / side
                if (x > 0) push(i - 1); if (x < side - 1) push(i + 1); if (y > 0) push(i - side); if (y < side - 1) push(i + side)
            }
        }
        return out to side
    }
}
