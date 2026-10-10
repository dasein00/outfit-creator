package com.dasein.poryadok.logic

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Картина по номерам из фото: цвета подбираются из карандашей, которые есть, картинка упрощается до крупных
 * областей, у каждой области — номер цвета. Потом это печатается как раскраска: тонкие контуры и номера.
 */
object PaintByNumbers {
    /** Карандаш: [code] — номер в наборе или свой «К1», [rgb] — цвет. */
    data class Pencil(val code: String, val name: String, val rgb: Int)

    data class Region(val color: Int, val area: Int, val x: Int, val y: Int, val room: Float)

    /**
     * [cells] — номер цвета (индекс в [colors]) для каждого пикселя, [regions] — области с местом для подписи:
     * ([x],[y]) — самая «глубокая» точка области, [room] — расстояние до края в пикселях (размер цифры).
     */
    class Result(val width: Int, val height: Int, val cells: IntArray, val colors: List<Pencil>, val regions: List<Region>) {
        fun counts(): IntArray = IntArray(colors.size).also { c -> regions.forEach { c[it.color] += 1 } }
    }

    /** Уровень детализации: 0 — крупно (для детей), 1 — средне, 2 — подробно. */
    data class Detail(val minArea: Int, val smooth: Int)

    fun detail(level: Int, w: Int, h: Int): Detail {
        val px = w * h
        return when (level) {
            0 -> Detail(max(60, px / 900), 3)
            1 -> Detail(max(30, px / 2200), 2)
            else -> Detail(max(14, px / 5000), 1)
        }
    }

    private fun d2(a: DoubleArray, b: DoubleArray): Double { val l = a[0] - b[0]; val x = a[1] - b[1]; val y = a[2] - b[2]; return l * l + x * x + y * y }

    /** Какие карандаши взять: главные оттенки картинки → ближайшие карандаши без повторов. */
    fun choose(px: IntArray, pencils: List<Pencil>, maxColors: Int, seed: Long = 7): List<Pencil> {
        val k = maxColors.coerceIn(2, 60)
        if (pencils.size <= k) return pencils
        val rnd = java.util.Random(seed)
        val sample = Array(min(px.size, 6000)) { CraftPattern.lab(px[rnd.nextInt(px.size)] and 0xFFFFFF) }
        val centers = ArrayList<DoubleArray>()
        centers += sample[rnd.nextInt(sample.size)].clone()
        val dist = DoubleArray(sample.size) { Double.MAX_VALUE }
        while (centers.size < k) {
            val c = centers.last()
            var sum = 0.0
            for (i in sample.indices) { dist[i] = min(dist[i], d2(sample[i], c)); sum += dist[i] }
            if (sum <= 0) break
            var r = rnd.nextDouble() * sum
            var pick = sample.size - 1
            for (i in sample.indices) { r -= dist[i]; if (r <= 0) { pick = i; break } }
            centers += sample[pick].clone()
        }
        val assign = IntArray(sample.size)
        repeat(10) {
            for (i in sample.indices) assign[i] = centers.indices.minBy { d2(sample[i], centers[it]) }
            val sums = Array(centers.size) { DoubleArray(4) }
            for (i in sample.indices) { val s = sums[assign[i]]; s[0] += sample[i][0]; s[1] += sample[i][1]; s[2] += sample[i][2]; s[3]++ }
            sums.forEachIndexed { j, s -> if (s[3] > 0) centers[j] = doubleArrayOf(s[0] / s[3], s[1] / s[3], s[2] / s[3]) }
        }
        // Центры одного оттенка (шум фото, плавный переход) склеиваем — иначе схема пестрит соседними карандашами.
        val merged = ArrayList<DoubleArray>()
        centers.forEach { c -> if (merged.none { sqrt(d2(it, c)) < 10 }) merged += c }
        val pl = pencils.map { CraftPattern.lab(it.rgb) }
        val chosen = LinkedHashSet<Int>()
        merged.forEach { c ->
            val best = pencils.indices.filter { it !in chosen }.minByOrNull { d2(c, pl[it]) }
            if (best != null) chosen += best
        }
        return chosen.map { pencils[it] }
    }

    fun build(px: IntArray, w: Int, h: Int, pencils: List<Pencil>, maxColors: Int, level: Int): Result {
        val colors0 = choose(px, pencils, maxColors)
        val pl = colors0.map { CraftPattern.lab(it.rgb) }
        val det = detail(level, w, h)
        // Лёгкое размытие убирает шум фото до подбора цвета.
        val soft = blur(px, w, h)
        var cells = IntArray(px.size) { i ->
            val l = CraftPattern.lab(soft[i])
            var best = 0; var bd = Double.MAX_VALUE
            for (j in pl.indices) { val d = d2(l, pl[j]); if (d < bd) { bd = d; best = j } }
            best
        }
        // Цвета, которых меньше 0,4% картинки, отдаём ближайшему другому цвету.
        val share = IntArray(colors0.size).also { c -> cells.forEach { c[it]++ } }
        val rare = colors0.indices.filter { share[it] in 1 until cells.size / 250 }.toSet()
        if (rare.isNotEmpty() && rare.size < colors0.indices.count { share[it] > 0 }) {
            val keep = colors0.indices.filter { share[it] > 0 && it !in rare }
            val to = IntArray(colors0.size) { j -> if (j in rare) keep.minBy { d2(pl[j], pl[it]) } else j }
            cells = IntArray(cells.size) { to[cells[it]] }
        }
        repeat(det.smooth) { cells = majority(cells, w, h, colors0.size) }
        cells = mergeSmall(cells, w, h, det.minArea)
        // Убираем неиспользованные карандаши, нумеруем по частоте.
        val count = IntArray(colors0.size).also { c -> cells.forEach { c[it]++ } }
        val order = colors0.indices.filter { count[it] > 0 }.sortedByDescending { count[it] }
        val remap = IntArray(colors0.size) { -1 }.also { m -> order.forEachIndexed { n, o -> m[o] = n } }
        val out = IntArray(cells.size) { remap[cells[it]] }
        val colors = order.map { colors0[it] }
        return Result(w, h, out, colors, regions(out, w, h))
    }

    /** Размытие 3×3 по каналам. */
    private fun blur(px: IntArray, w: Int, h: Int): IntArray = IntArray(px.size) { i ->
        val x = i % w; val y = i / w
        var r = 0; var g = 0; var b = 0; var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            val xx = x + dx; val yy = y + dy
            if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
            val c = px[yy * w + xx]; r += c shr 16 and 255; g += c shr 8 and 255; b += c and 255; n++
        }
        ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    /** Каждая клетка берёт самый частый цвет в окне 3×3 — исчезают «крапинки». */
    private fun majority(c: IntArray, w: Int, h: Int, k: Int): IntArray {
        val out = c.copyOf()
        val cnt = IntArray(k)
        for (y in 0 until h) for (x in 0 until w) {
            java.util.Arrays.fill(cnt, 0)
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                cnt[c[yy * w + xx]]++
            }
            var best = c[y * w + x]
            for (j in 0 until k) if (cnt[j] > cnt[best]) best = j
            if (cnt[best] >= 5) out[y * w + x] = best
        }
        return out
    }

    /** Связные области одного цвета: номер области для каждой клетки и их размеры. */
    fun components(c: IntArray, w: Int, h: Int): Pair<IntArray, IntArray> {
        val comp = IntArray(c.size) { -1 }
        val sizes = ArrayList<Int>()
        val stack = IntArray(c.size)
        for (s in c.indices) {
            if (comp[s] >= 0) continue
            val id = sizes.size
            var sp = 0; stack[sp++] = s; comp[s] = id
            var n = 0
            while (sp > 0) {
                val i = stack[--sp]; n++
                val x = i % w; val y = i / w; val col = c[i]
                if (x > 0 && comp[i - 1] < 0 && c[i - 1] == col) { comp[i - 1] = id; stack[sp++] = i - 1 }
                if (x < w - 1 && comp[i + 1] < 0 && c[i + 1] == col) { comp[i + 1] = id; stack[sp++] = i + 1 }
                if (y > 0 && comp[i - w] < 0 && c[i - w] == col) { comp[i - w] = id; stack[sp++] = i - w }
                if (y < h - 1 && comp[i + w] < 0 && c[i + w] == col) { comp[i + w] = id; stack[sp++] = i + w }
            }
            sizes += n
        }
        return comp to sizes.toIntArray()
    }

    /** Мелкие области (меньше [minArea]) перекрашиваются в цвет соседа, с которым у них самая длинная граница. */
    fun mergeSmall(c0: IntArray, w: Int, h: Int, minArea: Int): IntArray {
        val c = c0.copyOf()
        repeat(8) {
            val (comp, sizes) = components(c, w, h)
            val small = sizes.indices.filter { sizes[it] < minArea }
            if (small.isEmpty()) return c
            // Для каждой мелкой области — счёт соседних цветов по границе.
            val votes = HashMap<Int, HashMap<Int, Int>>()
            for (i in c.indices) {
                val id = comp[i]
                if (sizes[id] >= minArea) continue
                val x = i % w; val y = i / w
                fun vote(j: Int) { if (comp[j] != id) votes.getOrPut(id) { HashMap() }.merge(c[j], 1, Int::plus) }
                if (x > 0) vote(i - 1); if (x < w - 1) vote(i + 1); if (y > 0) vote(i - w); if (y < h - 1) vote(i + w)
            }
            val target = IntArray(sizes.size) { -1 }
            votes.forEach { (id, v) -> target[id] = v.maxBy { it.value }.key }
            var changed = false
            for (i in c.indices) { val t = target[comp[i]]; if (t >= 0 && t != c[i]) { c[i] = t; changed = true } }
            if (!changed) return c
        }
        return c
    }

    /** Где писать номер: точка области, дальше всего от её границы (расстояние — «шахматное» по 8 соседям). */
    fun regions(c: IntArray, w: Int, h: Int): List<Region> {
        val (comp, sizes) = components(c, w, h)
        val inf = Float.MAX_VALUE / 4
        val d = FloatArray(c.size) { inf }
        for (i in c.indices) {
            val x = i % w; val y = i / w
            val edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                comp[i - 1] != comp[i] || comp[i + 1] != comp[i] || comp[i - w] != comp[i] || comp[i + w] != comp[i]
            if (edge) d[i] = 1f
        }
        val diag = 1.4142f
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (x > 0 && comp[i - 1] == comp[i]) d[i] = min(d[i], d[i - 1] + 1)
            if (y > 0 && comp[i - w] == comp[i]) d[i] = min(d[i], d[i - w] + 1)
            if (x > 0 && y > 0 && comp[i - w - 1] == comp[i]) d[i] = min(d[i], d[i - w - 1] + diag)
            if (x < w - 1 && y > 0 && comp[i - w + 1] == comp[i]) d[i] = min(d[i], d[i - w + 1] + diag)
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (x < w - 1 && comp[i + 1] == comp[i]) d[i] = min(d[i], d[i + 1] + 1)
            if (y < h - 1 && comp[i + w] == comp[i]) d[i] = min(d[i], d[i + w] + 1)
            if (x < w - 1 && y < h - 1 && comp[i + w + 1] == comp[i]) d[i] = min(d[i], d[i + w + 1] + diag)
            if (x > 0 && y < h - 1 && comp[i + w - 1] == comp[i]) d[i] = min(d[i], d[i + w - 1] + diag)
        }
        val best = IntArray(sizes.size) { -1 }
        for (i in c.indices) { val id = comp[i]; if (best[id] < 0 || d[i] > d[best[id]]) best[id] = i }
        return sizes.indices.map { id ->
            val i = best[id]
            Region(c[i], sizes[id], i % w, i / w, d[i])
        }
    }

    /** Отрезки контуров (x0,y0,x1,y1) по границам областей — горизонтальные и вертикальные, склеенные в длинные. */
    fun outline(c: IntArray, w: Int, h: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        // Горизонтальные границы между строками y-1 и y.
        for (y in 1 until h) {
            var start = -1
            for (x in 0..w) {
                val diff = x < w && c[y * w + x] != c[(y - 1) * w + x]
                if (diff && start < 0) start = x
                if (!diff && start >= 0) { out += intArrayOf(start, y, x, y); start = -1 }
            }
        }
        for (x in 1 until w) {
            var start = -1
            for (y in 0..h) {
                val diff = y < h && c[y * w + x] != c[y * w + x - 1]
                if (diff && start < 0) start = y
                if (!diff && start >= 0) { out += intArrayOf(x, start, x, y); start = -1 }
            }
        }
        return out
    }

    /** Насколько цвет тёмный — подписи на цветном превью белые или чёрные. */
    fun dark(rgb: Int): Boolean = CraftPattern.lab(rgb)[0] < 55

    /** Стандартные цветные карандаши (48 цветов) — отметьте, какие есть. Номера — для удобства, не артикулы. */
    val CATALOG: List<Pencil> = listOf(
        Pencil("1", "Белый", 0xFFFFFF), Pencil("2", "Лимонный", 0xFFF44F), Pencil("3", "Жёлтый", 0xFFD600), Pencil("4", "Золотисто-жёлтый", 0xFFB300),
        Pencil("5", "Оранжевый", 0xFF8C00), Pencil("6", "Красно-оранжевый", 0xFF5A1F), Pencil("7", "Алый", 0xE52B2B), Pencil("8", "Красный", 0xC8102E),
        Pencil("9", "Тёмно-красный", 0x8B1A1A), Pencil("10", "Малиновый", 0xC2185B), Pencil("11", "Розовый", 0xF48FB1), Pencil("12", "Светло-розовый", 0xF8C8D4),
        Pencil("13", "Пурпурный", 0x9C27B0), Pencil("14", "Сиреневый", 0xB39DDB), Pencil("15", "Фиолетовый", 0x5E35B1), Pencil("16", "Тёмно-фиолетовый", 0x3B1F5C),
        Pencil("17", "Ультрамарин", 0x1A3FA8), Pencil("18", "Синий", 0x1565C0), Pencil("19", "Голубой", 0x42A5F5), Pencil("20", "Светло-голубой", 0x9FD3F5),
        Pencil("21", "Бирюзовый", 0x00A3A3), Pencil("22", "Морская волна", 0x2E8B8B), Pencil("23", "Тёмно-синий", 0x0D1B4C), Pencil("24", "Индиго", 0x283593),
        Pencil("25", "Светло-зелёный", 0xA5D66C), Pencil("26", "Салатовый", 0x7CB342), Pencil("27", "Травяной", 0x43A047), Pencil("28", "Зелёный", 0x2E7D32),
        Pencil("29", "Изумрудный", 0x00875A), Pencil("30", "Тёмно-зелёный", 0x1B4D2A), Pencil("31", "Оливковый", 0x7A7A2E), Pencil("32", "Хаки", 0x9E9A5B),
        Pencil("33", "Телесный", 0xF5CBA7), Pencil("34", "Персиковый", 0xF0A57C), Pencil("35", "Охра", 0xC88A2B), Pencil("36", "Жёлто-коричневый", 0xB5762A),
        Pencil("37", "Светло-коричневый", 0xA0673C), Pencil("38", "Коричневый", 0x6D3F1E), Pencil("39", "Тёмно-коричневый", 0x3E2415), Pencil("40", "Терракотовый", 0xB55239),
        Pencil("41", "Бежевый", 0xE6D3B3), Pencil("42", "Светло-серый", 0xC7C7C7), Pencil("43", "Серый", 0x8E8E8E), Pencil("44", "Тёмно-серый", 0x555555),
        Pencil("45", "Чёрный", 0x1A1A1A), Pencil("46", "Серо-голубой", 0x7B93A8), Pencil("47", "Бордовый", 0x6E1423), Pencil("48", "Мятный", 0x9DE0C4),
    )

    /** Наборы по количеству цветов: какие номера в них обычно есть. */
    val SETS: List<Pair<String, List<String>>> = listOf(
        "12 цветов" to listOf("1", "3", "5", "8", "11", "15", "18", "19", "27", "30", "38", "45"),
        "24 цвета" to listOf("1", "2", "3", "5", "7", "8", "9", "11", "13", "15", "17", "18", "19", "21", "25", "27", "28", "30", "33", "35", "38", "39", "43", "45"),
        "36 цветов" to listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "13", "14", "15", "17", "18", "19", "20", "21", "23", "25", "26", "27", "28", "30", "31", "33", "34", "35", "37", "38", "39", "42", "43", "44", "45"),
        "48 цветов" to CATALOG.map { it.code },
    )
}
