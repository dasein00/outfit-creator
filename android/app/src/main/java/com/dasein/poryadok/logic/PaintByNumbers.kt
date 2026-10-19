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
    class Result(
        val width: Int, val height: Int, val cells: IntArray, val colors: List<Pencil>, val regions: List<Region>,
        /** Дополнительные контуры (x0,y0,x1,y1) по резким краям фото внутри областей одного цвета. */
        val edges: List<IntArray> = emptyList(),
    ) {
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
    fun choose(px: IntArray, pencils: List<Pencil>, maxColors: Int, seed: Long = 7, mergeDe: Double = 10.0): List<Pencil> {
        val k = maxColors.coerceIn(2, 264)
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
        centers.forEach { c -> if (merged.none { sqrt(d2(it, c)) < mergeDe }) merged += c }
        val pl = pencils.map { CraftPattern.lab(it.rgb) }
        val chosen = LinkedHashSet<Int>()
        merged.forEach { c ->
            val best = pencils.indices.filter { it !in chosen }.minByOrNull { d2(c, pl[it]) }
            if (best != null) chosen += best
        }
        return chosen.map { pencils[it] }
    }

    /**
     * [sensitivity] 0..100 — чувствительность контуров: чем выше, тем больше различаются близкие оттенки, тем меньше
     * сглаживание и мельче сохраняемые детали, и тем больше резких краёв фото становятся контурами внутри одного цвета.
     */
    fun build(px: IntArray, w: Int, h: Int, pencils: List<Pencil>, maxColors: Int, level: Int, sensitivity: Int = 50): Result {
        val sv = sensitivity.coerceIn(0, 100) / 100.0
        val colors0 = choose(px, pencils, maxColors, mergeDe = 12 - 9 * sv)
        val pl = colors0.map { CraftPattern.lab(it.rgb) }
        val det = detail(level, w, h)
        // Лёгкое размытие убирает шум фото до подбора цвета.
        val soft = blur(px, w, h)
        // Lab всех точек одним плоским массивом (L,a,b подряд) — без миллиона мелких объектов.
        val labs = FloatArray(px.size * 3)
        for (i in px.indices) { val l = CraftPattern.lab(soft[i]); labs[i * 3] = l[0].toFloat(); labs[i * 3 + 1] = l[1].toFloat(); labs[i * 3 + 2] = l[2].toFloat() }
        val pf = FloatArray(pl.size * 3).also { a -> pl.forEachIndexed { j, l -> a[j * 3] = l[0].toFloat(); a[j * 3 + 1] = l[1].toFloat(); a[j * 3 + 2] = l[2].toFloat() } }
        var cells = IntArray(px.size) { i ->
            val L = labs[i * 3]; val A = labs[i * 3 + 1]; val B = labs[i * 3 + 2]
            var best = 0; var bd = Float.MAX_VALUE
            for (j in pl.indices) {
                val dl = L - pf[j * 3]; val da = A - pf[j * 3 + 1]; val db = B - pf[j * 3 + 2]
                val d = dl * dl + da * da + db * db
                if (d < bd) { bd = d; best = j }
            }
            best
        }
        // Редкие цвета отдаём ближайшему другому: при низкой чувствительности — меньше 0,4% картинки, при высокой — почти не трогаем.
        val share = IntArray(colors0.size).also { c -> cells.forEach { c[it]++ } }
        val rareMax = (cells.size / (250 + 4000 * sv)).toInt()
        val rare = colors0.indices.filter { share[it] in 1 until rareMax }.toSet()
        if (rare.isNotEmpty() && rare.size < colors0.indices.count { share[it] > 0 }) {
            val keep = colors0.indices.filter { share[it] > 0 && it !in rare }
            val to = IntArray(colors0.size) { j -> if (j in rare) keep.minBy { d2(pl[j], pl[it]) } else j }
            cells = IntArray(cells.size) { to[cells[it]] }
        }
        val passes = (det.smooth - (sv * 2.2).toInt()).coerceAtLeast(0)
        repeat(passes) { cells = majority(cells, w, h) }
        cells = mergeSmall(cells, w, h, maxOf(4, (det.minArea * (1.5 - 1.2 * sv)).toInt()))
        // Убираем неиспользованные карандаши, нумеруем по частоте.
        val count = IntArray(colors0.size).also { c -> cells.forEach { c[it]++ } }
        val order = colors0.indices.filter { count[it] > 0 }.sortedByDescending { count[it] }
        val remap = IntArray(colors0.size) { -1 }.also { m -> order.forEachIndexed { n, o -> m[o] = n } }
        val out = IntArray(cells.size) { remap[cells[it]] }
        val colors = order.map { colors0[it] }
        return Result(w, h, out, colors, regions(out, w, h), photoEdges(labs, out, w, h, sv, det.minArea))
    }

    /**
     * Контуры по резким краям фото там, где по обе стороны оказался один цвет (лицо на фоне похожего оттенка, складки,
     * границы предметов). Шум отсекается: край должен быть резким ([sv] задаёт порог) и тянуться линией, а не точкой.
     */
    fun photoEdges(labs: FloatArray, c: IntArray, w: Int, h: Int, sv: Double, minArea: Int): List<IntArray> {
        if (sv <= 0.05) return emptyList()
        val t2 = (14 - 11 * sv).let { it * it }.toFloat()
        fun dd(i: Int, j: Int): Float {
            val dl = labs[i * 3] - labs[j * 3]; val da = labs[i * 3 + 1] - labs[j * 3 + 1]; val db = labs[i * 3 + 2] - labs[j * 3 + 2]
            return dl * dl + da * da + db * db
        }
        val minLen = maxOf(14, (minArea / 3.0 * (1.2 - sv)).toInt())
        // Рядом с уже существующей границей цветов края не ищем — иначе контур удвоится по размытому переходу.
        val near = BooleanArray(c.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if ((x < w - 1 && c[i] != c[i + 1]) || (y < h - 1 && c[i] != c[i + w])) {
                for (dy in -2..2) for (dx in -2..2) {
                    val xx = x + dx; val yy = y + dy
                    if (xx in 0 until w && yy in 0 until h) near[yy * w + xx] = true
                }
            }
        }
        // Пиксель края: резкий перепад с соседом справа или снизу при том же цвете на схеме.
        val right = BooleanArray(c.size); val down = BooleanArray(c.size); val mark = BooleanArray(c.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (near[i]) continue
            // Перепад меряем через точку (i и i+2): размытие растягивает край на два пикселя.
            if (x < w - 2 && c[i] == c[i + 1] && c[i] == c[i + 2] && dd(i, i + 2) > t2 && dd(i, i + 2) >= maxOf(dd(i - minOf(x, 1), i + 1), dd(i + 1, minOf(i + 3, y * w + w - 1)))) {
                right[i] = true; mark[i] = true; mark[i + 1] = true
            }
            if (y < h - 2 && c[i] == c[i + w] && c[i] == c[i + 2 * w] && dd(i, i + 2 * w) > t2 && dd(i, i + 2 * w) >= maxOf(dd(i - (if (y > 0) w else 0), i + w), dd(i + w, if (y < h - 3) i + 3 * w else i + 2 * w))) {
                down[i] = true; mark[i] = true; mark[i + w] = true
            }
        }
        // Оставляем только линии: связные (по 8 соседям) группы отмеченных пикселей длиной от minLen.
        val comp = IntArray(c.size) { -1 }
        val keep = BooleanArray(c.size)
        val stack = IntArray(c.size)
        val members = ArrayList<Int>()
        for (s in c.indices) {
            if (!mark[s] || comp[s] >= 0) continue
            members.clear()
            var sp = 0; stack[sp++] = s; comp[s] = s
            while (sp > 0) {
                val i = stack[--sp]; members += i
                val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                    val j = yy * w + xx
                    if (mark[j] && comp[j] < 0) { comp[j] = s; stack[sp++] = j }
                }
            }
            if (members.size >= minLen) members.forEach { keep[it] = true }
        }
        val out = ArrayList<IntArray>()
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (right[i] && (keep[i] || keep[i + 1])) out += intArrayOf(x + 1, y, x + 1, y + 1)
            if (down[i] && (keep[i] || keep[i + w])) out += intArrayOf(x, y + 1, x + 1, y + 1)
        }
        return out
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

    /** Каждая клетка берёт самый частый цвет в окне 3×3 — исчезают «крапинки». Считаем только 9 соседей, не все цвета. */
    private fun majority(c: IntArray, w: Int, h: Int): IntArray {
        val out = c.copyOf()
        val vals = IntArray(9); val cnt = IntArray(9)
        for (y in 0 until h) for (x in 0 until w) {
            var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                val v = c[yy * w + xx]
                var k = 0
                while (k < n && vals[k] != v) k++
                if (k == n) { vals[n] = v; cnt[n] = 1; n++ } else cnt[k]++
            }
            var best = -1; var bc = 0
            for (k in 0 until n) if (cnt[k] > bc) { bc = cnt[k]; best = vals[k] }
            if (bc >= 5) out[y * w + x] = best
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

    /** Все карандаши: 48 базовых + расширенная линейка — всего 264. */
    val PENCILS_ALL: List<Pencil> by lazy { CATALOG + PbnCatalogs.PENCIL_EXTRA }
    val MARKERS_ALL: List<Pencil> by lazy { MARKERS + PbnCatalogs.MARKER_EXTRA }
    val PAINTS_ALL: List<Pencil> by lazy { PAINTS + PbnCatalogs.PAINT_EXTRA }

    /** Большие наборы — первые 64 и 128 цветов линейки (самые различимые) и вся линейка. */
    private fun big(all: List<Pencil>) = listOf(
        "64 цвета" to all.take(64).map { it.code }, "128 цветов" to all.take(128).map { it.code }, "${all.size} цвета" to all.map { it.code },
    )

    /** Наборы по количеству цветов: какие номера в них обычно есть. */
    val SETS: List<Pair<String, List<String>>> = listOf(
        "12 цветов" to listOf("1", "3", "5", "8", "11", "15", "18", "19", "27", "30", "38", "45"),
        "24 цвета" to listOf("1", "2", "3", "5", "7", "8", "9", "11", "13", "15", "17", "18", "19", "21", "25", "27", "28", "30", "33", "35", "38", "39", "43", "45"),
        "36 цветов" to listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "13", "14", "15", "17", "18", "19", "20", "21", "23", "25", "26", "27", "28", "30", "31", "33", "34", "35", "37", "38", "39", "42", "43", "44", "45"),
        "48 цветов" to CATALOG.map { it.code },
    ) + big(PENCILS_ALL)

    /** Чем раскрашивать: у каждого материала свой каталог, наборы и свои цвета. */
    // Новые материалы — только в конец: номер материала хранится в сохранённых картинах.
    enum class Medium(val title: String, val one: String, val many: String) {
        PENCIL("Карандаши", "Карандаш", "карандашей"), MARKER("Маркеры", "Маркер", "маркеров"), PAINT("Краски", "Краска", "красок"),
        WATERCOLOR_PENCIL("Акварельные карандаши", "Акварельный карандаш", "акварельных карандашей"),
        ALCOHOL_MARKER("Спиртовые маркеры", "Спиртовой маркер", "спиртовых маркеров");
        val catalog: List<Pencil> get() = when (this) {
            PENCIL -> PENCILS_ALL; MARKER -> MARKERS_ALL; PAINT -> PAINTS_ALL; WATERCOLOR_PENCIL -> WATERCOLOR_PENCILS; ALCOHOL_MARKER -> ALCOHOL_MARKERS
        }
        val sets: List<Pair<String, List<String>>> get() = when (this) {
            PENCIL -> SETS; MARKER -> MARKER_SETS; PAINT -> PAINT_SETS; WATERCOLOR_PENCIL -> WATERCOLOR_SETS; ALCOHOL_MARKER -> ALCOHOL_SETS
        }
        companion object { fun of(i: Int) = entries.getOrElse(i) { PENCIL } }
    }

    /**
     * Маркеры для скетчинга (на водной основе и универсальные) — 60 ходовых оттенков, как в наборах на 12–60 цветов.
     * Номера «М…» — для удобства, не артикулы конкретной марки: если оттенок отличается, добавьте его пипеткой по фото.
     */
    val MARKERS: List<Pencil> = listOf(
        "Белый блендер" to 0xF7F7F2, "Лимонный" to 0xFFF35C, "Жёлтый" to 0xFFE01A, "Канареечный" to 0xFFD23F, "Горчичный" to 0xE0B02E,
        "Абрикосовый" to 0xFFB86B, "Оранжевый" to 0xFF8A1F, "Мандариновый" to 0xFF6A2B, "Коралловый" to 0xFF6F61, "Алый" to 0xE8343A,
        "Красный" to 0xD0202E, "Вишнёвый" to 0x9E1B32, "Винный" to 0x6E1E2D, "Малиновый" to 0xD81B60, "Фуксия" to 0xE6399B,
        "Розовый" to 0xF59AC0, "Пудровый" to 0xF5C6CF, "Лавандовый" to 0xC3A9E0, "Сиреневый" to 0xA77CCF, "Фиолетовый" to 0x6A3FA0,
        "Баклажан" to 0x40214F, "Небесный" to 0xA8D8F0, "Голубой" to 0x5BB4E5, "Лазурный" to 0x1E88D2, "Синий" to 0x1F5BB5,
        "Ультрамарин" to 0x2A3AA0, "Тёмно-синий" to 0x15214F, "Бирюзовый" to 0x18B2B2, "Морская волна" to 0x16827E, "Мятный" to 0xA3E4CF,
        "Салатовый" to 0xB5DD5A, "Травяной" to 0x5DB54A, "Зелёный" to 0x2E9E4F, "Изумрудный" to 0x0F7F5C, "Хвойный" to 0x1F5137,
        "Оливковый" to 0x7F8236, "Хаки" to 0xA6A06A, "Телесный светлый" to 0xFBE0CC, "Телесный" to 0xF2C4A2, "Персиковый" to 0xEFA987,
        "Загар" to 0xD9956B, "Охра" to 0xC98F3A, "Карамельный" to 0xB8763E, "Коричневый" to 0x7A4A2A, "Шоколадный" to 0x4A2C1C,
        "Терракота" to 0xB8553B, "Бежевый" to 0xE8D6B8, "Песочный" to 0xD8C08F, "Тёплый серый 1" to 0xE3DED6, "Тёплый серый 3" to 0xBDB5AA,
        "Тёплый серый 5" to 0x8F877D, "Холодный серый 1" to 0xDEE2E6, "Холодный серый 3" to 0xB3BAC2, "Холодный серый 5" to 0x838B94,
        "Холодный серый 7" to 0x5A626B, "Нейтральный серый" to 0x9A9A9A, "Тёмно-серый" to 0x4D4D4D, "Графит" to 0x333538, "Чёрный" to 0x161616,
        "Серо-голубой" to 0x8EA3B5,
    ).mapIndexed { i, (n, c) -> Pencil("М${i + 1}", n, c) }

    /**
     * Краски (акрил и гуашь): классические пигменты, как в наборах на 12–36 цветов. Цвет — каким он ложится из тюбика,
     * без смешивания; номера «К…» — для удобства, не артикулы.
     */
    val PAINTS: List<Pencil> = listOf(
        "Белила титановые" to 0xFAFAF7, "Лимонная" to 0xFFF04A, "Кадмий жёлтый светлый" to 0xFFDD1F, "Кадмий жёлтый средний" to 0xFFC20E,
        "Охра светлая" to 0xD9A441, "Неаполитанская жёлтая" to 0xF3D9A0, "Кадмий оранжевый" to 0xFF7A12, "Кадмий красный светлый" to 0xE8392B,
        "Кадмий красный тёмный" to 0xB51F24, "Алая" to 0xE2252E, "Краплак красный" to 0x9E1631, "Пурпурная" to 0xB3196B,
        "Розовая" to 0xF29BB8, "Фиолетовая" to 0x5B2C83, "Ультрамарин" to 0x23339B, "Кобальт синий" to 0x1F4FA8,
        "Церулеум (лазурь)" to 0x2A86C8, "Фталоцианин синий" to 0x0F3A7A, "Голубая" to 0x7EC4EA, "Бирюзовая" to 0x00A6A6,
        "Изумрудная" to 0x00805A, "Фталоцианин зелёный" to 0x0B5E46, "Зелёная светлая" to 0x6CBF3F, "Травяная" to 0x3E8E2F,
        "Оливковая" to 0x6F7330, "Сиена натуральная" to 0xC27A3A, "Сиена жжёная" to 0x8E3B1E, "Умбра натуральная" to 0x6E5A3A,
        "Умбра жжёная" to 0x4E3021, "Марс коричневый" to 0x5E2A1C, "Телесная" to 0xF3C9A8, "Персиковая" to 0xF2A57E,
        "Серая нейтральная" to 0x8C8C8C, "Пейнс серый" to 0x3B4250, "Сажа газовая" to 0x151515, "Золотая охра" to 0xC98A1E,
    ).mapIndexed { i, (n, c) -> Pencil("К${i + 1}", n, c) }

    val MARKER_SETS: List<Pair<String, List<String>>> = listOf(
        "12 цветов" to listOf(2, 3, 7, 11, 16, 20, 24, 28, 33, 44, 56, 59),
        "24 цвета" to listOf(1, 2, 3, 6, 7, 9, 11, 12, 15, 16, 19, 20, 23, 25, 28, 31, 33, 35, 39, 42, 44, 53, 56, 59),
        "36 цветов" to listOf(1, 2, 3, 4, 6, 7, 9, 10, 11, 12, 14, 15, 16, 18, 19, 20, 22, 23, 25, 27, 28, 30, 31, 32, 33, 35, 36, 38, 39, 42, 44, 49, 52, 54, 56, 59),
        "48 цветов" to (1..60).filter { it !in setOf(5, 8, 13, 17, 21, 26, 29, 34, 37, 41, 47, 60) },
    ).map { (t, l) -> t to l.map { "М$it" } } + big(MARKERS_ALL)

    val PAINT_SETS: List<Pair<String, List<String>>> = listOf(
        "12 цветов" to listOf(1, 3, 7, 10, 12, 14, 15, 17, 23, 26, 29, 35),
        "24 цвета" to listOf(1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17, 19, 20, 21, 23, 24, 26, 27, 29, 35),
        "36 цветов" to (1..36).toList(),
        "48 цветов" to (1..48).toList(),
    ).map { (t, l) -> t to l.map { "К$it" } } + big(PAINTS_ALL)

    /**
     * Акварельные карандаши — 48 пигментных цветов, как в наборах на 12–48. Цвет — каким он становится после
     * размывания влажной кистью (ярче и прозрачнее сухого штриха). Номера «А…» — для удобства, не артикулы.
     */
    val WATERCOLOR_PENCILS: List<Pencil> = listOf(
        "Белый" to 0xF8F8F4, "Лимонный" to 0xFFF04A, "Кадмий жёлтый" to 0xFFD11A, "Неаполитанский жёлтый" to 0xF6DC9C,
        "Жёлтая охра" to 0xD9A33C, "Оранжевый светлый" to 0xFFA23A, "Кадмий оранжевый" to 0xFF7418, "Красный светлый" to 0xF0453A,
        "Кармин" to 0xC4142F, "Ализарин тёмный" to 0x8E1730, "Розовый" to 0xF49AB9, "Маджента" to 0xC3247A,
        "Пурпурный" to 0x8E2A7E, "Фиолетовый светлый" to 0xA27BC9, "Фиолетовый" to 0x5C3492, "Ультрамарин" to 0x2C3DA6,
        "Кобальт синий" to 0x2652AE, "Лазурь (церулеум)" to 0x2E8CCB, "Небесно-голубой" to 0x8ACCF0, "Прусская синь" to 0x123A66,
        "Индиго" to 0x23305E, "Бирюзовый" to 0x13A6AE, "Кобальт бирюзовый" to 0x0F8C88, "Изумрудный" to 0x0C8060,
        "Фталоцианин зелёный" to 0x0E5D4A, "Зелёный светлый" to 0x7EC64B, "Майский зелёный" to 0x9AD14C, "Травяной" to 0x45953A,
        "Сок-зелёный" to 0x5C8A2B, "Хвойный" to 0x234B32, "Оливковый" to 0x77772F, "Земля зелёная" to 0x7E8655,
        "Телесный" to 0xF5CBAA, "Персиковый" to 0xF0A47F, "Сиена натуральная" to 0xC47B3B, "Сиена жжёная" to 0x8D3C21,
        "Умбра натуральная" to 0x725C3C, "Умбра жжёная" to 0x4F3122, "Ван Дик коричневый" to 0x3E2A1E, "Венецианский красный" to 0xA9402D,
        "Терракота" to 0xB8573D, "Бежевый" to 0xE6D4B4, "Тёплый серый" to 0xB1A99C, "Холодный серый" to 0x9AA2AB,
        "Пейнс серый" to 0x404857, "Графит" to 0x3A3B3E, "Чёрный" to 0x181818, "Серо-голубой" to 0x7F95A9,
    ).mapIndexed { i, (n, c) -> Pencil("А${i + 1}", n, c) }

    /**
     * Спиртовые маркеры — 216 оттенков: 76 основных (у каждого цвета светлый, средний и тёмный тон, телесные,
     * тёплые и холодные серые) и 140 дополнительных — 28 оттенков по 5 тонов, как в больших наборах для скетчинга. Цвет — один ровный слой; второй слой темнее.
     * Номера «СМ…» — для удобства, не артикулы конкретной марки.
     */
    val ALCOHOL_MARKERS: List<Pencil> = listOf(
        "Пастельно-жёлтый" to 0xFFF6B0, "Лимонный" to 0xFFF04D, "Жёлтый" to 0xFFDD22, "Тёмно-жёлтый" to 0xFFC61A, "Горчичный" to 0xDDAA2C,
        "Светлый абрикос" to 0xFFD3A3, "Абрикос" to 0xFFB066, "Оранжевый" to 0xFF8524, "Тыквенный" to 0xF26B1D,
        "Лососевый" to 0xFF9B85, "Коралл" to 0xFF6B5E, "Алый" to 0xEC3B3B, "Красный" to 0xD42232, "Кармин" to 0xB0182E, "Бордо" to 0x7A1A2C,
        "Бледно-розовый" to 0xFBD3DE, "Розовый" to 0xF7A1C1, "Ярко-розовый" to 0xF0629E, "Маджента" to 0xD02A80, "Сливовый" to 0x8E2D6A,
        "Бледная лаванда" to 0xE2D6F2, "Лаванда" to 0xC2A9E3, "Сирень" to 0xA27CD0, "Фиолетовый" to 0x6B42A8, "Тёмный фиолет" to 0x3F2466,
        "Бледно-голубой" to 0xD6ECF8, "Небесный" to 0xA6D5F2, "Голубой" to 0x5DB2E6, "Лазурь" to 0x2589D4, "Синий" to 0x1E5CB8,
        "Кобальт" to 0x2A47A6, "Ультрамарин" to 0x2C34A0, "Тёмно-синий" to 0x18234F, "Серо-синий" to 0x5C7392,
        "Бледная мята" to 0xD3F1E6, "Мята" to 0x9EE0C9, "Бирюза" to 0x1FB5B0, "Морская волна" to 0x168681, "Тёмная бирюза" to 0x0E5C5A,
        "Бледно-салатовый" to 0xE6F4B8, "Салатовый" to 0xBADF63, "Лайм" to 0x8CCB3E, "Травяной" to 0x55AE48, "Зелёный" to 0x2C9A50,
        "Изумруд" to 0x107E5D, "Хвоя" to 0x1E4F37, "Оливковый" to 0x7F8036, "Хаки" to 0xA7A16D, "Мох" to 0x5E6A35,
        "Светлая кожа" to 0xFCE6D6, "Кожа светлая 2" to 0xF8D6BF, "Телесный" to 0xF2C2A0, "Бежево-розовый" to 0xEDB497, "Загар" to 0xD8976D,
        "Карамель" to 0xB9773F, "Медный" to 0x9A5A35, "Кожа тёмная" to 0x7A4A2E, "Кожа очень тёмная" to 0x5A3524,
        "Песочный" to 0xDCC393, "Бежевый" to 0xEBDCC0, "Охра" to 0xC9913E, "Коричневый" to 0x7B4B2C, "Шоколад" to 0x4B2D1D, "Кофейный" to 0x3A241A,
        "Тёплый серый 1" to 0xE6E1DA, "Тёплый серый 3" to 0xC7C0B6, "Тёплый серый 5" to 0xA39B90, "Тёплый серый 7" to 0x7A736A, "Тёплый серый 9" to 0x4F4A44,
        "Холодный серый 1" to 0xE1E5E9, "Холодный серый 3" to 0xBCC3CA, "Холодный серый 5" to 0x949CA5, "Холодный серый 7" to 0x6B737C, "Холодный серый 9" to 0x454B53,
        "Нейтральный серый" to 0x9A9A9A, "Чёрный" to 0x151515,
        // Расширенная палитра (СМ77–СМ216): 28 оттенков по 5 тонов, 1 — самый светлый, 5 — самый глубокий.
        "Ванильный 1" to 0xF1EDDA, "Ванильный 2" to 0xE6D9A8, "Ванильный 3" to 0xD5C06D, "Ванильный 4" to 0xBEA337, "Ванильный 5" to 0x7E6D25,
        "Шафран 1" to 0xF8ECD3, "Шафран 2" to 0xF7D797, "Шафран 3" to 0xF1BB50, "Шафран 4" to 0xE29D12, "Шафран 5" to 0x97690C,
        "Мёд 1" to 0xF5E8D6, "Мёд 2" to 0xEECCA0, "Мёд 3" to 0xE3A95F, "Мёд 4" to 0xD08625, "Мёд 5" to 0x8B5918,
        "Корица 1" to 0xF1E2DA, "Корица 2" to 0xE6BFA8, "Корица 3" to 0xD5936D, "Корица 4" to 0xBE6837, "Корица 5" to 0x7E4625,
        "Ржавчина 1" to 0xF4DFD7, "Ржавчина 2" to 0xEBB6A2, "Ржавчина 3" to 0xDE8463, "Ржавчина 4" to 0xCA552B, "Ржавчина 5" to 0x87391D,
        "Кирпич 1" to 0xF1DDDA, "Кирпич 2" to 0xE6B0A8, "Кирпич 3" to 0xD57B6D, "Кирпич 4" to 0xBE4937, "Кирпич 5" to 0x7E3125,
        "Гранат 1" to 0xF4D7DB, "Гранат 2" to 0xEBA2AC, "Гранат 3" to 0xDE6374, "Гранат 4" to 0xCA2B40, "Гранат 5" to 0x871D2B,
        "Пыльная роза 1" to 0xECDFE2, "Пыльная роза 2" to 0xD8B6BE, "Пыльная роза 3" to 0xBD8492, "Пыльная роза 4" to 0x9F5668, "Пыльная роза 5" to 0x6A3945,
        "Пион 1" to 0xF3D8E3, "Пион 2" to 0xE9A5C1, "Пион 3" to 0xD96897, "Пион 4" to 0xC4316E, "Пион 5" to 0x832149,
        "Бургунди 1" to 0xF0DBE2, "Бургунди 2" to 0xE3ABBE, "Бургунди 3" to 0xD07191, "Бургунди 4" to 0xB83D66, "Бургунди 5" to 0x7A2944,
        "Слива 1" to 0xEDDEE8, "Слива 2" to 0xDBB3CD, "Слива 3" to 0xC280AC, "Слива 4" to 0xA55089, "Слива 5" to 0x6E355B,
        "Орхидея 1" to 0xEEDDEE, "Орхидея 2" to 0xDDB0DD, "Орхидея 3" to 0xC67BC6, "Орхидея 4" to 0xAB49AB, "Орхидея 5" to 0x723172,
        "Аметист 1" to 0xE7DCEF, "Аметист 2" to 0xCBAEE0, "Аметист 3" to 0xA876CB, "Аметист 4" to 0x8443B1, "Аметист 5" to 0x582D76,
        "Барвинок 1" to 0xDCDDEF, "Барвинок 2" to 0xAEB2E0, "Барвинок 3" to 0x767DCB, "Барвинок 4" to 0x434CB1, "Барвинок 5" to 0x2D3376,
        "Васильковый 1" to 0xD7E1F4, "Васильковый 2" to 0xA2BBEB, "Васильковый 3" to 0x638CDE, "Васильковый 4" to 0x2B60CA, "Васильковый 5" to 0x1D4087,
        "Джинсовый 1" to 0xDEE5ED, "Джинсовый 2" to 0xB3C6DB, "Джинсовый 3" to 0x809EC2, "Джинсовый 4" to 0x5078A5, "Джинсовый 5" to 0x35506E,
        "Ледяной 1" to 0xDBEBF0, "Ледяной 2" to 0xABD5E3, "Ледяной 3" to 0x71B8D0, "Ледяной 4" to 0x3D99B8, "Ледяной 5" to 0x29667A,
        "Петроль 1" to 0xDAEEF1, "Петроль 2" to 0xA8DEE6, "Петроль 3" to 0x6DC7D5, "Петроль 4" to 0x37ACBE, "Петроль 5" to 0x25737E,
        "Нефрит 1" to 0xDCEFE9, "Нефрит 2" to 0xAEE0CF, "Нефрит 3" to 0x76CBAF, "Нефрит 4" to 0x43B18D, "Нефрит 5" to 0x2D765E,
        "Эвкалипт 1" to 0xE0EBE6, "Эвкалипт 2" to 0xB9D5C7, "Эвкалипт 3" to 0x89B8A1, "Эвкалипт 4" to 0x5C997A, "Эвкалипт 5" to 0x3D6652,
        "Шалфей 1" to 0xE3EAE1, "Шалфей 2" to 0xBFD2BC, "Шалфей 3" to 0x94B48E, "Шалфей 4" to 0x6A9362, "Шалфей 5" to 0x476241,
        "Зелёное яблоко 1" to 0xE3F3D8, "Зелёное яблоко 2" to 0xC1E9A5, "Зелёное яблоко 3" to 0x97D968, "Зелёное яблоко 4" to 0x6EC431, "Зелёное яблоко 5" to 0x498321,
        "Шартрёз 1" to 0xEEF6D5, "Шартрёз 2" to 0xDCF19D, "Шартрёз 3" to 0xC4E75A, "Шартрёз 4" to 0xA8D61F, "Шартрёз 5" to 0x708F14,
        "Болотный 1" to 0xECEDDE, "Болотный 2" to 0xD7DBB3, "Болотный 3" to 0xBCC280, "Болотный 4" to 0x9EA550, "Болотный 5" to 0x696E35,
        "Полынь 1" to 0xE7E9E2, "Полынь 2" to 0xCACFBE, "Полынь 3" to 0xA5AF92, "Полынь 4" to 0x818D68, "Полынь 5" to 0x565E45,
        "Какао 1" to 0xECE4DF, "Какао 2" to 0xD8C4B6, "Какао 3" to 0xBD9C84, "Какао 4" to 0x9F7456, "Какао 5" to 0x6A4E39,
        "Тауп 1" to 0xE8E6E3, "Тауп 2" to 0xCEC7C0, "Тауп 3" to 0xACA195, "Тауп 4" to 0x897A6C, "Тауп 5" to 0x5B5248,
        "Сланец 1" to 0xE3E6E8, "Сланец 2" to 0xC0C7CE, "Сланец 3" to 0x95A1AC, "Сланец 4" to 0x6C7A89, "Сланец 5" to 0x48525B,
    ).mapIndexed { i, (n, c) -> Pencil("СМ${i + 1}", n, c) }

    /** Основные 76 спиртовых маркеров — из них собираются небольшие наборы. */
    private val ALCOHOL_BASE: List<Pencil> get() = ALCOHOL_MARKERS.take(76)

    /** Равномерная выборка из каталога — набор на [n] цветов с самыми разными оттенками. */
    private fun spread(all: List<Pencil>, n: Int): List<String> =
        if (n >= all.size) all.map { it.code } else (0 until n).map { all[(it * all.size) / n].code }.distinct()

    val WATERCOLOR_SETS: List<Pair<String, List<String>>> = listOf(
        "12 цветов" to listOf(2, 3, 7, 9, 12, 16, 18, 24, 28, 36, 38, 47).map { "А$it" },
        "24 цвета" to listOf(1, 2, 3, 5, 7, 8, 9, 11, 12, 15, 16, 18, 19, 22, 24, 26, 28, 30, 33, 35, 36, 38, 45, 47).map { "А$it" },
        "36 цветов" to spread(WATERCOLOR_PENCILS, 36),
        "48 цветов" to WATERCOLOR_PENCILS.map { it.code },
    )

    val ALCOHOL_SETS: List<Pair<String, List<String>>> = listOf(
        "12 цветов" to listOf(3, 8, 13, 18, 24, 29, 37, 44, 53, 63, 72, 76).map { "СМ$it" },
        "24 цвета" to spread(ALCOHOL_BASE, 24),
        "36 цветов" to spread(ALCOHOL_BASE, 36),
        "48 цветов" to spread(ALCOHOL_BASE, 48),
        "60 цветов" to spread(ALCOHOL_BASE, 60),
        "76 цветов" to ALCOHOL_BASE.map { it.code },
        "120 цветов" to ALCOHOL_BASE.map { it.code } + spread(ALCOHOL_MARKERS.drop(76), 44),
        "168 цветов" to ALCOHOL_BASE.map { it.code } + spread(ALCOHOL_MARKERS.drop(76), 92),
        "${ALCOHOL_MARKERS.size} цветов" to ALCOHOL_MARKERS.map { it.code },
    )
}
