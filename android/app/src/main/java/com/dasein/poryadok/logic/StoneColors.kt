package com.dasein.poryadok.logic

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Цвета страз по фото: стразы разложены на белой бумаге. Бумага — опорный белый: по ней выравнивается баланс
 * белого (жёлтая лампа, тень от телефона), потом стразы отделяются от бумаги, каждая кучка или страза даёт свой цвет
 * (без бликов и теней граней), а одинаковые цвета склеиваются.
 */
object StoneColors {
    data class Found(val rgb: Int, val share: Double, val pieces: Int)

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun rgb(r: Int, g: Int, b: Int) = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun deltaE(a: DoubleArray, b: DoubleArray): Double { val l = a[0] - b[0]; val x = a[1] - b[1]; val y = a[2] - b[2]; return sqrt(l * l + x * x + y * y) }
    private fun chroma(l: DoubleArray) = sqrt(l[1] * l[1] + l[2] * l[2])

    /** Цвет бумаги: самые светлые серо-белые пиксели. null — бумагу найти не удалось (тогда без поправки). */
    fun paper(px: IntArray): Int? {
        val labs = px.map { CraftPattern.lab(it and 0xFFFFFF) }
        val idx = labs.indices.filter { chroma(labs[it]) < 28 && labs[it][0] > 45 }
        if (idx.size < px.size / 20) return null
        val ls = idx.map { labs[it][0] }.sorted()
        val top = ls[(ls.size * 0.8).toInt().coerceAtMost(ls.size - 1)]
        val near = idx.filter { kotlin.math.abs(labs[it][0] - top) < 7 }
        if (near.isEmpty()) return null
        return rgb(near.sumOf { r(px[it]) } / near.size, near.sumOf { g(px[it]) } / near.size, near.sumOf { b(px[it]) } / near.size)
    }

    /** Поправка баланса белого: бумага становится нейтрально-белой. */
    fun whiteBalance(px: IntArray, paper: Int?): IntArray {
        paper ?: return px.copyOf()
        val gr = (242.0 / max(1, r(paper))).coerceIn(0.6, 2.2)
        val gg = (242.0 / max(1, g(paper))).coerceIn(0.6, 2.2)
        val gb = (242.0 / max(1, b(paper))).coerceIn(0.6, 2.2)
        return IntArray(px.size) { val c = px[it]; rgb((r(c) * gr).roundToInt(), (g(c) * gg).roundToInt(), (b(c) * gb).roundToInt()) }
    }

    /**
     * Найти цвета на фото [px] (w×h, ARGB). [sensitivity] — насколько разные оттенки считать разными (ΔE):
     * меньше — больше цветов (тонкие оттенки), больше — меньше цветов.
     */
    fun detect(px0: IntArray, w: Int, h: Int, sensitivity: Double = 9.0, maxColors: Int = 80): List<Found> {
        val px = whiteBalance(px0, paper(px0))
        val labs = Array(px.size) { CraftPattern.lab(px[it]) }
        val white = CraftPattern.lab(0xF2F2F2)
        // Бумага и тени на ней: светлое и почти без цвета. Белые и серебристые стразы на белой бумаге
        // отделяются хуже — их удобнее добавить касанием по фото.
        val fg = BooleanArray(px.size) { i ->
            val l = labs[i]
            val d = deltaE(l, white)
            val c = chroma(l)
            !(d < 16 || (c < 7 && l[0] > white[0] - 24))
        }
        // Сжатие маски на пиксель: края страз смешаны с бумагой.
        val core = BooleanArray(px.size) { i ->
            if (!fg[i]) false else {
                val x = i % w; val y = i / w
                x > 0 && y > 0 && x < w - 1 && y < h - 1 && fg[i - 1] && fg[i + 1] && fg[i - w] && fg[i + w]
            }
        }
        val minArea = max(5, w * h / 50_000)
        val seen = BooleanArray(px.size)
        val stack = IntArray(px.size)
        data class Blob(val lab: DoubleArray, val rgb: Int, val area: Int)
        val blobs = ArrayList<Blob>()
        for (s in core.indices) {
            if (!core[s] || seen[s]) continue
            var sp = 0
            stack[sp++] = s; seen[s] = true
            val members = ArrayList<Int>()
            while (sp > 0) {
                val i = stack[--sp]
                members += i
                val x = i % w; val y = i / w
                if (x > 0 && core[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < w - 1 && core[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && core[i - w] && !seen[i - w]) { seen[i - w] = true; stack[sp++] = i - w }
                if (y < h - 1 && core[i + w] && !seen[i + w]) { seen[i + w] = true; stack[sp++] = i + w }
            }
            if (members.size < minArea) continue
            // Цвет без бликов и чёрных теней граней: средняя часть по светлоте.
            val sorted = members.sortedBy { labs[it][0] }
            val from = (sorted.size * 0.2).toInt()
            val to = max(from + 1, (sorted.size * 0.85).toInt())
            val mid = sorted.subList(from, min(to, sorted.size))
            val c = rgb(mid.sumOf { r(px[it]) } / mid.size, mid.sumOf { g(px[it]) } / mid.size, mid.sumOf { b(px[it]) } / mid.size)
            blobs += Blob(CraftPattern.lab(c), c, members.size)
        }
        if (blobs.isEmpty()) return emptyList()
        // Склеиваем одинаковые цвета (крупные кучки — первыми, они точнее).
        class Cluster(var lab: DoubleArray, var r: Double, var g: Double, var b: Double, var area: Int, var pieces: Int)
        val clusters = ArrayList<Cluster>()
        blobs.sortedByDescending { it.area }.forEach { bl ->
            val near = clusters.minByOrNull { deltaE(it.lab, bl.lab) }
            if (near != null && deltaE(near.lab, bl.lab) < sensitivity) {
                val t = near.area + bl.area
                near.r = (near.r * near.area + r(bl.rgb) * bl.area) / t
                near.g = (near.g * near.area + g(bl.rgb) * bl.area) / t
                near.b = (near.b * near.area + b(bl.rgb) * bl.area) / t
                near.area = t; near.pieces++
                near.lab = CraftPattern.lab(rgb(near.r.roundToInt(), near.g.roundToInt(), near.b.roundToInt()))
            } else clusters += Cluster(bl.lab, r(bl.rgb).toDouble(), g(bl.rgb).toDouble(), b(bl.rgb).toDouble(), bl.area, 1)
        }
        val total = clusters.sumOf { it.area }.toDouble()
        return clusters.sortedByDescending { it.area }.take(maxColors).map {
            Found(rgb(it.r.roundToInt(), it.g.roundToInt(), it.b.roundToInt()), it.area / total, it.pieces)
        }
    }

    /** Цвет в точке фото (касание пипеткой): медиана по кружку радиусом [rad], с поправкой баланса по бумаге. */
    fun sample(px0: IntArray, w: Int, h: Int, x: Int, y: Int, rad: Int, paper: Int?): Int {
        val px = mutableListOf<Int>()
        for (dy in -rad..rad) for (dx in -rad..rad) {
            if (dx * dx + dy * dy > rad * rad) continue
            val xx = x + dx; val yy = y + dy
            if (xx in 0 until w && yy in 0 until h) px += px0[yy * w + xx]
        }
        if (px.isEmpty()) return 0x808080
        val wb = whiteBalance(px.toIntArray(), paper)
        // Медиана по светлоте среди средних 60%: без бликов граней.
        val s = wb.sortedBy { CraftPattern.lab(it)[0] }
        val mid = s.subList((s.size * 0.2).toInt(), max((s.size * 0.2).toInt() + 1, (s.size * 0.8).toInt()))
        return rgb(mid.sumOf { r(it) } / mid.size, mid.sumOf { g(it) } / mid.size, mid.sumOf { b(it) } / mid.size)
    }
}
