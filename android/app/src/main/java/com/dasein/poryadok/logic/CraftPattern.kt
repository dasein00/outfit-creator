package com.dasein.poryadok.logic

import kotlin.math.ceil
import kotlin.math.cbrt
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Схема для рукоделия из фотографии: алмазная мозаика, вышивка крестом, бисер.
 * Фото уменьшается до сетки (одна клетка — один страз, крестик или бусина), цвета сводятся к нужному числу
 * (k-средних в пространстве Lab, где расстояние ≈ разница на глаз) и подбираются по палитре DMC.
 * Чистые функции над массивом ARGB — проверяются тестами без Android.
 */
object CraftPattern {
    enum class Kind(val title: String, val unit: String) {
        DIAMOND("Алмазная мозаика", "стразов"), CROSS("Вышивка крестом", "крестиков"), BEADS("Бисер", "бусин"),
    }

    /** Цвет палитры: номер DMC (по нему продаются нитки и стразы), название, RGB. Свои стразы — код «М1», «М2»… */
    data class Thread(val code: String, val name: String, val rgb: Int) {
        /** Свой цвет из «Мои стразы» (определён по фото или добавлен вручную), а не номер каталога. */
        val custom get() = code.startsWith(MINE)
        /** Подпись цвета: «DMC 321» или «Мой №3». */
        val label get() = if (custom) "Мой №" + code.removePrefix(MINE) else "DMC $code"
    }

    /** Приставка кода своих страз. */
    const val MINE = "М"

    data class Pattern(
        val width: Int,
        val height: Int,
        /** Индекс цвета в [colors] для каждой клетки, построчно; -1 — пустая клетка (фон, который не выкладывают). */
        val cells: IntArray,
        val colors: List<Thread>,
    ) {
        fun counts(): IntArray = IntArray(colors.size).also { c -> cells.forEach { if (it >= 0) c[it]++ } }
        val filled get() = cells.count { it >= 0 }
        /** Подпись, по которой видно, что схема не изменилась (иначе отметки прогресса сбрасываются). */
        fun signature(): String = "$width×$height:" + colors.joinToString(",") { it.code } + ":" + cells.contentHashCode()
    }

    // ---------- Цвет ----------

    private fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
    private fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116

    /** sRGB → CIE Lab (D65). */
    fun lab(rgb: Int): DoubleArray {
        val r = lin(rgb shr 16 and 255); val g = lin(rgb shr 8 and 255); val b = lin(rgb and 255)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun d2(a: DoubleArray, b: DoubleArray): Double { val l = a[0] - b[0]; val x = a[1] - b[1]; val y = a[2] - b[2]; return l * l + x * x + y * y }

    private val dmcLab by lazy { DMC.map { lab(it.rgb) } }

    /** Ближайший цвет DMC. */
    fun nearestDmc(rgb: Int, exclude: Set<Int> = emptySet()): Int {
        val l = lab(rgb)
        var best = -1; var bd = Double.MAX_VALUE
        dmcLab.forEachIndexed { i, c -> if (i !in exclude) { val d = d2(l, c); if (d < bd) { bd = d; best = i } } }
        return best
    }

    // ---------- Уменьшение фото до сетки ----------

    /** Среднее по блокам: каждая клетка — средний цвет своего участка фото. Прозрачное считается белым. */
    fun downscale(src: IntArray, sw: Int, sh: Int, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = y * sh / h; val y1 = maxOf(y0 + 1, (y + 1) * sh / h)
            for (x in 0 until w) {
                val x0 = x * sw / w; val x1 = maxOf(x0 + 1, (x + 1) * sw / w)
                var r = 0L; var g = 0L; var b = 0L; var n = 0L
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val p = src[yy * sw + xx]
                    val a = p ushr 24
                    r += ((p shr 16 and 255) * a + 255 * (255 - a)) / 255
                    g += ((p shr 8 and 255) * a + 255 * (255 - a)) / 255
                    b += ((p and 255) * a + 255 * (255 - a)) / 255
                    n++
                }
                out[y * w + x] = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
            }
        }
        return out
    }

    /** Высота сетки по ширине и пропорциям фото. */
    fun heightFor(width: Int, sw: Int, sh: Int): Int = maxOf(1, (width.toDouble() * sh / sw).roundToInt())

    // ---------- Подбор цветов ----------

    /**
     * Схема из уже уменьшенной картинки [px] (w×h): не больше [maxColors] цветов DMC.
     * [dither] — сглаживание Флойда — Стейнберга: плавные переходы на портретах и небе, но «шумнее» на плоских участках.
     */
    fun build(
        px: IntArray, w: Int, h: Int, maxColors: Int, dither: Boolean, seed: Long = 42,
        /** Только эти цвета DMC (индексы в [DMC]) — например, «из моих запасов». null — весь каталог. */
        allowed: Set<Int>? = null,
        /** Клетки, которые остаются пустыми (фон). */
        skip: BooleanArray? = null,
        /** Свои стразы («Мои стразы»): схема только из них вместо каталога DMC. */
        custom: List<Thread>? = null,
    ): Pattern {
        val cand: List<Thread> = custom?.takeIf { it.size >= 2 } ?: DMC
        val candLab = if (cand === DMC) dmcLab else cand.map { lab(it.rgb) }
        val pool = if (cand !== DMC) cand.indices.toSet() else (allowed?.takeIf { it.size >= 2 } ?: DMC.indices.toSet())
        val all = Array(px.size) { lab(px[it]) }
        val labs = if (skip == null) all else all.filterIndexed { i, _ -> !skip[i] }.toTypedArray().ifEmpty { all }
        val k = maxColors.coerceIn(2, 80)
        // k-средних с инициализацией k-means++ (детерминированно).
        val rnd = java.util.Random(seed)
        val centers = ArrayList<DoubleArray>()
        centers += labs[rnd.nextInt(labs.size)].clone()
        val dist = DoubleArray(labs.size) { Double.MAX_VALUE }
        while (centers.size < k) {
            val c = centers.last()
            var sum = 0.0
            for (i in labs.indices) { dist[i] = minOf(dist[i], d2(labs[i], c)); sum += dist[i] }
            if (sum <= 0) break
            var r = rnd.nextDouble() * sum
            var pick = labs.size - 1
            for (i in labs.indices) { r -= dist[i]; if (r <= 0) { pick = i; break } }
            centers += labs[pick].clone()
        }
        val assign = IntArray(labs.size)
        repeat(12) {
            for (i in labs.indices) {
                var best = 0; var bd = Double.MAX_VALUE
                centers.forEachIndexed { j, c -> val d = d2(labs[i], c); if (d < bd) { bd = d; best = j } }
                assign[i] = best
            }
            val sums = Array(centers.size) { DoubleArray(4) }
            for (i in labs.indices) { val s = sums[assign[i]]; s[0] += labs[i][0]; s[1] += labs[i][1]; s[2] += labs[i][2]; s[3]++ }
            sums.forEachIndexed { j, s -> if (s[3] > 0) centers[j] = doubleArrayOf(s[0] / s[3], s[1] / s[3], s[2] / s[3]) }
        }
        // Центры → ближайшие нитки DMC без повторов. Своих страз не больше лимита — тогда берём их все.
        val chosen = LinkedHashSet<Int>()
        if (cand !== DMC && cand.size <= k) chosen += cand.indices
        else centers.forEach { c ->
            var best = -1; var bd = Double.MAX_VALUE
            candLab.forEachIndexed { i, d -> if (i in pool && i !in chosen) { val dd = d2(c, d); if (dd < bd) { bd = dd; best = i } } }
            if (best >= 0) chosen += best
        }
        val pal = chosen.toList()
        val palLab = pal.map { candLab[it] }
        val cells = IntArray(px.size)
        if (!dither) {
            for (i in all.indices) cells[i] = if (skip?.get(i) == true) -1 else nearestIn(all[i], palLab)
        } else {
            val err = Array(all.size) { all[it].clone() }
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (skip?.get(i) == true) { cells[i] = -1; continue }
                val p = nearestIn(err[i], palLab)
                cells[i] = p
                val e = DoubleArray(3) { err[i][it] - palLab[p][it] }
                fun spread(dx: Int, dy: Int, f: Double) {
                    val xx = x + dx; val yy = y + dy
                    if (xx in 0 until w && yy < h) { val t = err[yy * w + xx]; for (c in 0..2) t[c] += e[c] * f }
                }
                spread(1, 0, 7 / 16.0); spread(-1, 1, 3 / 16.0); spread(0, 1, 5 / 16.0); spread(1, 1, 1 / 16.0)
            }
        }
        // Убираем цвета, которые в итоге не встретились, и сортируем по частоте.
        return compact(Pattern(w, h, cells, pal.map { cand[it] }))
    }

    /** Убирает неиспользуемые цвета, склеивает повторы одного номера DMC и сортирует по частоте (символы 1, 2, 3… — самым частым). */
    fun compact(p: Pattern): Pattern {
        val byCode = LinkedHashMap<String, Int>()
        val uniq = ArrayList<Thread>()
        val first = IntArray(p.colors.size) { i -> byCode.getOrPut(p.colors[i].code) { uniq += p.colors[i]; uniq.size - 1 } }
        val cells0 = IntArray(p.cells.size) { val c = p.cells[it]; if (c < 0) -1 else first[c] }
        val count = IntArray(uniq.size).also { c -> cells0.forEach { if (it >= 0) c[it]++ } }
        val order = uniq.indices.filter { count[it] > 0 }.sortedByDescending { count[it] }
        val remap = IntArray(uniq.size) { -1 }.also { m -> order.forEachIndexed { n, o -> m[o] = n } }
        return Pattern(p.width, p.height, IntArray(cells0.size) { val c = cells0[it]; if (c < 0) -1 else remap[c] }, order.map { uniq[it] })
    }

    /** Замены цветов пользователем: номер DMC → другой номер (можно слить два цвета в один или взять соседний оттенок). */
    fun replace(p: Pattern, map: Map<String, String>, mine: List<Thread> = emptyList()): Pattern {
        if (map.isEmpty()) return p
        val byCode = DMC.associateBy { it.code } + mine.associateBy { it.code }
        return compact(p.copy(colors = p.colors.map { t -> map[t.code]?.let { byCode[it] } ?: t }))
    }

    /** Замена цвета схемы на ближайший из запасов: что было, чем заменить и насколько похоже (ΔE в Lab). */
    data class StashMatch(val from: Thread, val to: Thread, val deltaE: Double) {
        val quality: String get() = when {
            deltaE < 6 -> "почти не отличить"
            deltaE < 14 -> "похожий оттенок"
            deltaE < 28 -> "заметно отличается"
            else -> "другой цвет"
        }
    }

    /** Для каждого цвета схемы — самый похожий цвет из запасов [stash] (номера DMC). Пусто, если запасов нет. */
    fun matchStash(colors: List<Thread>, stash: Set<String>): List<StashMatch> {
        val own = DMC.indices.filter { DMC[it].code in stash }
        if (own.isEmpty()) return emptyList()
        return colors.map { t ->
            val l = lab(t.rgb)
            val best = own.minBy { d2(l, dmcLab[it]) }
            StashMatch(t, DMC[best], sqrt(d2(l, dmcLab[best])))
        }
    }

    /**
     * Новая таблица замен, чтобы схема собиралась только из запасов: учитывает уже сделанные замены [old]
     * (исходный цвет → выбранный), чтобы цепочки не терялись.
     */
    fun stashReplace(old: Map<String, String>, matches: List<StashMatch>): Map<String, String> {
        val to = matches.associate { it.from.code to it.to.code }
        val out = LinkedHashMap<String, String>()
        matches.forEach { m -> out[m.from.code] = m.to.code }
        old.forEach { (k, v) -> out[k] = to[v] ?: v }
        return out.filter { it.key != it.value }
    }

    /** Ближайшие оттенки каталога к цвету — варианты замены. */
    fun alternatives(t: Thread, n: Int = 8, mine: List<Thread>? = null): List<Thread> {
        val l = lab(t.rgb)
        if (mine != null) return mine.filter { it.code != t.code }.sortedBy { d2(l, lab(it.rgb)) }.take(n)
        return DMC.indices.filter { DMC[it].code != t.code }.sortedBy { d2(l, dmcLab[it]) }.take(n).map { DMC[it] }
    }

    // ---------- Подготовка фото ----------

    /** Яркость, контраст и насыщенность: от −50 до +50, 0 — без изменений. */
    fun adjust(px: IntArray, brightness: Int, contrast: Int, saturation: Int): IntArray {
        if (brightness == 0 && contrast == 0 && saturation == 0) return px
        val b = brightness * 2.0
        val c = (100.0 + contrast * 1.6) / 100.0
        val s = 1.0 + saturation / 50.0
        return IntArray(px.size) { i ->
            val p = px[i]
            var r = (p shr 16 and 255).toDouble(); var g = (p shr 8 and 255).toDouble(); var bl = (p and 255).toDouble()
            val lum = 0.299 * r + 0.587 * g + 0.114 * bl
            r = lum + (r - lum) * s; g = lum + (g - lum) * s; bl = lum + (bl - lum) * s
            r = (r - 128) * c + 128 + b; g = (g - 128) * c + 128 + b; bl = (bl - 128) * c + 128 + b
            fun cl(v: Double) = v.roundToInt().coerceIn(0, 255)
            (p and 0xFF000000.toInt()) or (cl(r) shl 16) or (cl(g) shl 8) or cl(bl)
        }
    }

    /**
     * Фон: клетки, связанные с краем картинки и похожие по цвету на преобладающий цвет рамки ([tolerance] — разница на глаз в Lab).
     * Их можно не выкладывать — как в схемах вышивки с незаполненным фоном.
     */
    fun backgroundMask(px: IntArray, w: Int, h: Int, tolerance: Double = 14.0): BooleanArray {
        val border = buildList { for (x in 0 until w) { add(x); add((h - 1) * w + x) }; for (y in 0 until h) { add(y * w); add(y * w + w - 1) } }
        // Преобладающий цвет рамки — по грубым корзинам.
        val bucket = border.groupingBy { val p = px[it]; (p shr 19 and 31) * 1024 + (p shr 11 and 31) * 32 + (p shr 3 and 31) }.eachCount()
        val top = bucket.maxByOrNull { it.value }?.key ?: return BooleanArray(px.size)
        val ref = lab(border.first { val p = px[it]; (p shr 19 and 31) * 1024 + (p shr 11 and 31) * 32 + (p shr 3 and 31) == top }.let { px[it] })
        val tol = tolerance * tolerance
        val mask = BooleanArray(px.size)
        val queue = ArrayDeque<Int>()
        border.forEach { if (!mask[it] && d2(lab(px[it]), ref) <= tol) { mask[it] = true; queue += it } }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst(); val x = i % w; val y = i / w
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val xx = x + dx; val yy = y + dy
                if (xx !in 0 until w || yy !in 0 until h) continue
                val j = yy * w + xx
                if (!mask[j] && d2(lab(px[j]), ref) <= tol) { mask[j] = true; queue += j }
            }
        }
        return mask
    }

    private fun nearestIn(l: DoubleArray, pal: List<DoubleArray>): Int {
        var best = 0; var bd = Double.MAX_VALUE
        pal.forEachIndexed { j, c -> val d = d2(l, c); if (d < bd) { bd = d; best = j } }
        return best
    }

    /** Одиночные клетки, окружённые другим цветом, перекрашиваются в цвет соседей — меньше мелкой смены цветов при работе. */
    fun cleanup(p: Pattern): Pattern {
        val w = p.width; val h = p.height
        val out = p.cells.clone()
        for (y in 0 until h) for (x in 0 until w) {
            val me = p.cells[y * w + x]
            if (me < 0) continue
            val around = buildList {
                if (x > 0) add(p.cells[y * w + x - 1]); if (x < w - 1) add(p.cells[y * w + x + 1])
                if (y > 0) add(p.cells[(y - 1) * w + x]); if (y < h - 1) add(p.cells[(y + 1) * w + x])
            }
            if (around.size >= 3 && around.none { it == me }) {
                val top = around.filter { it >= 0 }.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
                if (top.value >= 3) out[y * w + x] = top.key
            }
        }
        return compact(p.copy(cells = out))
    }

    // ---------- Материалы ----------

    /** Размер готовой работы, см. Алмазная мозаика — страз 2,5 мм; вышивка — канва [count] крестиков на дюйм; бисер — 10/0 (~2,2 мм в ряду). */
    fun sizeCm(kind: Kind, cells: Int, count: Int = 14, round: Boolean = false): Double = when (kind) {
        Kind.DIAMOND -> cells * if (round) 0.28 else 0.25
        Kind.CROSS -> cells * 2.54 / count
        Kind.BEADS -> cells * 0.22
    }

    /** Сколько клеток в ширину нужно для работы шириной [cm]. */
    fun cellsFor(kind: Kind, cm: Double, count: Int = 14, round: Boolean = false): Int = (cm / sizeCm(kind, 1, count, round)).roundToInt().coerceAtLeast(10)

    /** Популярные размеры холстов для алмазной мозаики, см (ширина × высота). */
    val CANVASES = listOf(20 to 30, 30 to 40, 40 to 50, 50 to 65, 60 to 80)

    data class Need(val amount: Double, val unit: String)

    /**
     * Сколько материала на цвет:
     *  - стразы: штук с запасом 10 % (в пакетиках по 200);
     *  - нитки: мотков DMC (8 м, 6 сложений) — при вышивке в 2 нити один моток ≈ на 1800 крестиков на канве 14 (крупнее канва — меньше крестиков);
     *  - бисер 10/0: граммов (≈ 110 бусин в грамме) с запасом 10 %.
     */
    fun need(kind: Kind, cells: Int, count: Int = 14): Need = when (kind) {
        Kind.DIAMOND -> Need(ceil(cells * 1.1), "шт")
        Kind.CROSS -> Need(ceil(cells / (1800.0 * count / 14) * 10) / 10, "мот.")
        Kind.BEADS -> Need(ceil(cells * 1.1 / 110 * 10) / 10, "г")
    }

    /**
     * Символы цветов — как в наборах: сначала 1–9, потом буквы (без похожих на цифры I, O, Q, W), потом значки.
     * Самым частым цветам достаются самые простые символы. У каждого цвета схемы символ свой, повторов нет.
     */
    val SYMBOLS: List<String> = ("123456789".map { "$it" } + "ABCDEFGHJKLMNPRSTUVXYZ".map { "$it" } +
        listOf("#", "%", "↑", "★", "⊥", "+", "▷", "∀", "Ω", "Σ", "Ж", "Ф", "Ш", "Ю", "Я", "Д", "Л", "П", "Ц", "Б", "Г", "Э",
            "@", "&", "§", "♥", "♣", "♠", "♦", "●", "▲", "■", "◆", "✓", "×", "÷", "=", "?", "!", "<", ">", "~", "∞", "≈",
            "π", "λ", "δ", "β", "α", "μ", "ψ", "Ψ", "Φ", "Θ", "Λ", "a", "b", "d", "e", "f", "g", "h", "k", "m", "n", "q", "r", "t")).distinct()

    /** Символ цвета с номером [i]; если цветов больше, чем символов, — двузначные номера. */
    fun symbol(i: Int): String = SYMBOLS.getOrNull(i) ?: "${i + 1}"

    /** Чёрный или белый символ поверх цвета — что читается лучше. */
    fun symbolDark(rgb: Int): Boolean = lab(rgb)[0] > 55

    /** Цвета DMC (номера по каталогу; RGB — приблизительные экранные значения). */
    val DMC: List<Thread> = listOf(
        Thread("B5200", "Белоснежный", 0xFFFFFF), Thread("blanc", "Белый", 0xFCFBF8), Thread("ecru", "Экрю", 0xF0EADA), Thread("310", "Чёрный", 0x000000),
        Thread("150", "Пыльная роза ультратёмная", 0xAB0249), Thread("151", "Пыльная роза очень светлая", 0xF0CED4), Thread("152", "Розовая ракушка", 0xE2A099),
        Thread("153", "Фиалка очень светлая", 0xE6CCD9), Thread("154", "Виноград тёмный", 0x572433), Thread("155", "Сине-фиолетовый", 0x9891B6),
        Thread("156", "Сине-фиолетовый светлый", 0xA3AED1), Thread("157", "Васильковый очень светлый", 0xBBC3D9), Thread("158", "Васильковый тёмный", 0x4C526E),
        Thread("159", "Серо-голубой светлый", 0xC7CAD7), Thread("160", "Серо-голубой", 0x999FB7), Thread("161", "Серо-голубой тёмный", 0x7880A4),
        Thread("162", "Голубой ультрасветлый", 0xDBECF5), Thread("163", "Селадон", 0x4D8361), Thread("164", "Лесной зелёный светлый", 0xC8D8B8),
        Thread("165", "Мох очень светлый", 0xEFF4A4), Thread("166", "Мох", 0xC0C840), Thread("167", "Жёлто-бежевый тёмный", 0xA77C49),
        Thread("168", "Олово очень светлое", 0xD1D1D1), Thread("169", "Олово светлое", 0x848484), Thread("208", "Лаванда очень тёмная", 0x835B8B),
        Thread("209", "Лаванда тёмная", 0xA37BA7), Thread("210", "Лаванда", 0xC39FC3), Thread("211", "Лаванда светлая", 0xE3CBE3),
        Thread("221", "Розовая ракушка очень тёмная", 0x883E43), Thread("223", "Розовая ракушка светлая", 0xCC847C), Thread("224", "Розовая ракушка очень светлая", 0xEBB7AF),
        Thread("225", "Розовая ракушка ультрасветлая", 0xFFDFD5), Thread("300", "Красное дерево очень тёмное", 0x6F2F00), Thread("301", "Красное дерево", 0xB35F2B),
        Thread("304", "Красный", 0xB71F33), Thread("307", "Лимонный", 0xFDED54), Thread("309", "Роза тёмная", 0xBA4A4A),
        Thread("311", "Веджвуд ультратёмный", 0x1C5066), Thread("312", "Детский голубой очень тёмный", 0x35668B), Thread("315", "Антик лиловый тёмный", 0x814952),
        Thread("316", "Антик лиловый", 0xB7737F), Thread("317", "Оловянный серый", 0x6C6C6C), Thread("318", "Стальной серый светлый", 0xABABAB),
        Thread("319", "Фисташковый очень тёмный", 0x205F2E), Thread("320", "Фисташковый", 0x69885A), Thread("321", "Красный", 0xC72B3B),
        Thread("322", "Детский голубой тёмный", 0x5A8FB8), Thread("326", "Роза очень тёмная", 0xB33B4B), Thread("327", "Фиолетовый тёмный", 0x633666),
        Thread("333", "Сине-фиолетовый очень тёмный", 0x5C5478), Thread("334", "Детский голубой", 0x739FC1), Thread("335", "Роза", 0xEE546E),
        Thread("336", "Тёмно-синий", 0x253B73), Thread("340", "Сине-фиолетовый средний", 0xADA7C7), Thread("341", "Сине-фиолетовый светлый", 0xB7BFDD),
        Thread("347", "Лосось очень тёмный", 0xBF2D2D), Thread("349", "Коралловый тёмный", 0xD21035), Thread("350", "Коралловый", 0xE04848),
        Thread("351", "Коралловый светлый", 0xE96A67), Thread("352", "Коралловый бледный", 0xFD9C97), Thread("353", "Персиковый", 0xFED7CC),
        Thread("355", "Терракота тёмная", 0x984436), Thread("356", "Терракота", 0xC56A5B), Thread("367", "Фисташковый тёмный", 0x617A52),
        Thread("368", "Фисташковый светлый", 0xA6C298), Thread("369", "Фисташковый очень светлый", 0xD7EDCC), Thread("370", "Горчичный", 0xB89D64),
        Thread("372", "Горчичный светлый", 0xCCB784), Thread("400", "Красное дерево тёмное", 0x8F430F), Thread("402", "Красное дерево очень светлое", 0xF7A777),
        Thread("407", "Песочный тёмный", 0xBB8161), Thread("413", "Оловянный серый тёмный", 0x565656), Thread("414", "Стальной серый тёмный", 0x8C8C8C),
        Thread("415", "Жемчужно-серый", 0xD3D3D6), Thread("420", "Лесной орех тёмный", 0xA07042), Thread("422", "Лесной орех светлый", 0xC69F7B),
        Thread("433", "Коричневый", 0x7A451F), Thread("434", "Коричневый светлый", 0x985E33), Thread("435", "Коричневый очень светлый", 0xB87748),
        Thread("436", "Загар", 0xCB9051), Thread("437", "Загар светлый", 0xE4BB8E), Thread("444", "Лимонный тёмный", 0xFFD600),
        Thread("445", "Лимонный светлый", 0xFFFB8B), Thread("451", "Ракушечный серый тёмный", 0x917B73), Thread("452", "Ракушечный серый", 0xC0B3AE),
        Thread("453", "Ракушечный серый светлый", 0xD7CECB), Thread("469", "Авокадо", 0x72843C), Thread("470", "Авокадо светлый", 0x94AB4F),
        Thread("471", "Авокадо очень светлый", 0xAEBF79), Thread("472", "Авокадо ультрасветлый", 0xD8E498), Thread("498", "Красный тёмный", 0xA7132B),
        Thread("500", "Сине-зелёный очень тёмный", 0x044D33), Thread("501", "Сине-зелёный тёмный", 0x396F52), Thread("502", "Сине-зелёный", 0x5B9071),
        Thread("503", "Сине-зелёный средний", 0x7BAC94), Thread("504", "Сине-зелёный очень светлый", 0xC4DECC), Thread("505", "Нефритовый", 0x338362),
        Thread("517", "Веджвуд тёмный", 0x3B768F), Thread("518", "Веджвуд светлый", 0x4F93A7), Thread("519", "Небесно-голубой", 0x7EB1C8),
        Thread("520", "Папоротник тёмный", 0x666D4F), Thread("522", "Папоротник", 0x969E7E), Thread("524", "Папоротник очень светлый", 0xC4CDAC),
        Thread("535", "Пепельно-серый", 0x636458), Thread("543", "Бежево-коричневый ультрасветлый", 0xF2E3CE), Thread("550", "Фиолетовый очень тёмный", 0x5C184E),
        Thread("552", "Фиолетовый", 0x803A6B), Thread("553", "Фиолетовый светлый", 0xA3638B), Thread("554", "Фиолетовый бледный", 0xDBB3CB),
        Thread("561", "Селадон очень тёмный", 0x2C6A45), Thread("562", "Нефрит", 0x53976A), Thread("563", "Нефрит светлый", 0x8FC098),
        Thread("564", "Нефрит очень светлый", 0xA7CDAF), Thread("598", "Бирюзовый светлый", 0x90C3CC), Thread("600", "Клюквенный очень тёмный", 0xCD2F63),
        Thread("601", "Клюквенный тёмный", 0xD1286A), Thread("602", "Клюквенный", 0xE24874), Thread("603", "Клюквенный светлый", 0xFFA4BE),
        Thread("605", "Клюквенный очень светлый", 0xFFC0CD), Thread("606", "Ярко-оранжево-красный", 0xFA3203), Thread("608", "Ярко-оранжевый", 0xFD5D35),
        Thread("610", "Серо-коричневый тёмный", 0x796047), Thread("611", "Серо-коричневый", 0x967656), Thread("612", "Серо-коричневый светлый", 0xBC9A78),
        Thread("613", "Серо-коричневый очень светлый", 0xDCC4AA), Thread("632", "Песочный ультратёмный", 0x875539), Thread("640", "Бежево-серый очень тёмный", 0x857B61),
        Thread("642", "Бежево-серый тёмный", 0xA49878), Thread("644", "Бежево-серый", 0xDDD8CB), Thread("645", "Бобровый серый очень тёмный", 0x6E655C),
        Thread("646", "Бобровый серый тёмный", 0x877D73), Thread("647", "Бобровый серый", 0xB0A69C), Thread("648", "Бобровый серый светлый", 0xBCB4AC),
        Thread("666", "Ярко-красный", 0xE31D42), Thread("676", "Старое золото светлое", 0xE5CE97), Thread("677", "Старое золото очень светлое", 0xF5ECCB),
        Thread("680", "Старое золото тёмное", 0xBC8D0E), Thread("699", "Зелёный", 0x056517), Thread("700", "Зелёный яркий", 0x07731B),
        Thread("701", "Зелёный светлый", 0x3F8F29), Thread("702", "Зелёный келли", 0x47A72F), Thread("703", "Шартрез", 0x7BB547),
        Thread("704", "Шартрез яркий", 0x9ECF34), Thread("712", "Кремовый", 0xFFFBEF), Thread("718", "Сливовый", 0x9C2462),
        Thread("720", "Пряный оранжевый тёмный", 0xE55C1F), Thread("721", "Пряный оранжевый", 0xF27842), Thread("722", "Пряный оранжевый светлый", 0xF7976F),
        Thread("725", "Топаз", 0xFFC840), Thread("726", "Топаз светлый", 0xFDD755), Thread("727", "Топаз очень светлый", 0xFFF1AF),
        Thread("728", "Золотистый", 0xE4B468), Thread("729", "Старое золото", 0xD0A53E), Thread("730", "Оливковый очень тёмный", 0x827B30),
        Thread("732", "Оливковый", 0x948C36), Thread("733", "Оливковый средний", 0xBCB34C), Thread("734", "Оливковый светлый", 0xC7C077),
        Thread("738", "Загар очень светлый", 0xECCC9E), Thread("739", "Загар ультрасветлый", 0xF8E4C8), Thread("740", "Мандариновый", 0xFF8B00),
        Thread("741", "Мандариновый средний", 0xFFA32B), Thread("742", "Мандариновый светлый", 0xFFBF57), Thread("743", "Жёлтый", 0xFED376),
        Thread("744", "Жёлтый бледный", 0xFFE793), Thread("745", "Жёлтый очень бледный", 0xFFE9AD), Thread("746", "Молочный", 0xFCFCEE),
        Thread("754", "Персиковый светлый", 0xF7CBBF), Thread("758", "Терракота очень светлая", 0xEEAA9B), Thread("760", "Лосось", 0xF5ADAD),
        Thread("761", "Лосось светлый", 0xFFC9C9), Thread("762", "Жемчужно-серый очень светлый", 0xECECEC), Thread("772", "Жёлто-зелёный очень светлый", 0xE4ECD4),
        Thread("775", "Детский голубой очень светлый", 0xD9EBF1), Thread("776", "Розовый", 0xFCB0B9), Thread("777", "Малиновый очень тёмный", 0x913546),
        Thread("778", "Антик лиловый очень светлый", 0xDFB3BB), Thread("779", "Какао тёмный", 0x624B45), Thread("780", "Топаз ультратёмный", 0x94631A),
        Thread("782", "Топаз тёмный", 0xAE7720), Thread("783", "Топаз средний", 0xCE9124), Thread("791", "Васильковый очень тёмный", 0x464563),
        Thread("792", "Васильковый тёмный", 0x555B7B), Thread("793", "Васильковый", 0x707DA2), Thread("794", "Васильковый светлый", 0x8F9CC1),
        Thread("796", "Королевский синий тёмный", 0x11416E), Thread("797", "Королевский синий", 0x13477D), Thread("798", "Дельфтский синий тёмный", 0x466A8E),
        Thread("799", "Дельфтский синий", 0x748EB6), Thread("800", "Дельфтский синий бледный", 0xC0CCDE), Thread("801", "Кофейный тёмный", 0x653919),
        Thread("806", "Павлиний синий тёмный", 0x3D95A5), Thread("807", "Павлиний синий", 0x64ABBA), Thread("809", "Дельфтский синий светлый", 0x94A8C6),
        Thread("813", "Голубой светлый", 0xA1C2D7), Thread("814", "Гранатовый тёмный", 0x7B001B), Thread("815", "Гранатовый", 0x87071F),
        Thread("816", "Гранатовый яркий", 0x970B23), Thread("817", "Коралловый красный очень тёмный", 0xBB051F), Thread("818", "Детский розовый", 0xFFDFD9),
        Thread("819", "Детский розовый светлый", 0xFFEEEB), Thread("820", "Королевский синий очень тёмный", 0x0E365C), Thread("822", "Бежево-серый светлый", 0xE7E2D3),
        Thread("823", "Тёмно-синий тёмный", 0x213063), Thread("825", "Синий тёмный", 0x476B99), Thread("826", "Синий", 0x6B9EBF),
        Thread("827", "Синий очень светлый", 0xBDDDED), Thread("828", "Синий ультрасветлый", 0xC5E8ED), Thread("829", "Оливковый ультратёмный", 0x7E6A3F),
        Thread("832", "Золотисто-оливковый", 0xBD9B51), Thread("834", "Золотисто-оливковый светлый", 0xDBBE7F), Thread("838", "Бежево-коричневый очень тёмный", 0x594933),
        Thread("839", "Бежево-коричневый тёмный", 0x675541), Thread("840", "Бежево-коричневый", 0x9A7C5C), Thread("841", "Бежево-коричневый светлый", 0xB69B7E),
        Thread("842", "Бежево-коричневый очень светлый", 0xD1BAA1), Thread("844", "Пепельно-серый ультратёмный", 0x484848), Thread("869", "Лесной орех очень тёмный", 0x83633A),
        Thread("890", "Фисташковый ультратёмный", 0x174923), Thread("891", "Гвоздика тёмная", 0xFF5755), Thread("892", "Гвоздика", 0xFF7D73),
        Thread("893", "Гвоздика светлая", 0xFC9081), Thread("894", "Гвоздика очень светлая", 0xFFB2BB), Thread("898", "Кофейный очень тёмный", 0x492A13),
        Thread("899", "Роза", 0xF27688), Thread("900", "Жжёный оранжевый тёмный", 0xD15807), Thread("902", "Гранатовый очень тёмный", 0x822637),
        Thread("904", "Попугай зелёный очень тёмный", 0x557822), Thread("905", "Попугай зелёный тёмный", 0x628A28), Thread("906", "Попугай зелёный", 0x7FB335),
        Thread("907", "Попугай зелёный светлый", 0xC7E666), Thread("909", "Изумрудный очень тёмный", 0x156F49), Thread("910", "Изумрудный тёмный", 0x187E56),
        Thread("911", "Изумрудный", 0x189065), Thread("912", "Изумрудный светлый", 0x1BA36F), Thread("913", "Нильский зелёный", 0x6DAB77),
        Thread("915", "Сливовый тёмный", 0x820043), Thread("917", "Сливовый средний", 0x9B1366), Thread("918", "Красная медь тёмная", 0x82341A),
        Thread("919", "Красная медь", 0xA64521), Thread("920", "Медь", 0xAC5C3E), Thread("922", "Медь светлая", 0xE27323),
        Thread("924", "Серо-зелёный очень тёмный", 0x566A6A), Thread("926", "Серо-зелёный", 0x98AEAE), Thread("927", "Серо-зелёный светлый", 0xBDCBCB),
        Thread("928", "Серо-зелёный очень светлый", 0xDDE3E3), Thread("930", "Антик синий тёмный", 0x455C71), Thread("931", "Антик синий", 0x6A859E),
        Thread("932", "Антик синий светлый", 0xA2B5C6), Thread("934", "Чёрно-зелёный авокадо", 0x313919), Thread("935", "Авокадо тёмный", 0x424D21),
        Thread("936", "Авокадо очень тёмный", 0x4C5826), Thread("937", "Авокадо средний", 0x627133), Thread("938", "Кофейный ультратёмный", 0x361F0E),
        Thread("939", "Тёмно-синий очень тёмный", 0x1B2853), Thread("943", "Аквамарин", 0x3D9384), Thread("945", "Загар", 0xFBD5BB),
        Thread("946", "Жжёный оранжевый", 0xEB6307), Thread("947", "Жжёный оранжевый яркий", 0xFF7B4D), Thread("948", "Персиковый очень светлый", 0xFEE7DA),
        Thread("950", "Песочный светлый", 0xEED3C4), Thread("951", "Телесный светлый", 0xFFE2CF), Thread("954", "Нильский зелёный светлый", 0x88BA91),
        Thread("955", "Нильский зелёный бледный", 0xA2D6AD), Thread("956", "Гераниевый", 0xFF9191), Thread("957", "Гераниевый бледный", 0xFDB5B5),
        Thread("958", "Морская волна тёмная", 0x3EB6A1), Thread("959", "Морская волна", 0x59C7B4), Thread("961", "Пыльная роза тёмная", 0xCF7373),
        Thread("962", "Пыльная роза", 0xE68A8A), Thread("963", "Пыльная роза ультрасветлая", 0xFFD7D7), Thread("964", "Морская волна светлая", 0xA9E2D8),
        Thread("966", "Детский зелёный", 0xB9D7C0), Thread("970", "Тыквенный светлый", 0xF78B13), Thread("971", "Тыквенный", 0xF67F00),
        Thread("972", "Тёмно-жёлтый", 0xFFB515), Thread("973", "Ярко-канареечный", 0xFFE300), Thread("975", "Золотисто-коричневый тёмный", 0x753F10),
        Thread("976", "Золотисто-коричневый", 0xC28142), Thread("977", "Золотисто-коричневый светлый", 0xDC9C56), Thread("986", "Лесной зелёный очень тёмный", 0x405230),
        Thread("987", "Лесной зелёный тёмный", 0x587141), Thread("988", "Лесной зелёный", 0x738B5B), Thread("989", "Лесной зелёный светлый", 0x8DA675),
        Thread("991", "Аквамарин тёмный", 0x477B6E), Thread("992", "Аквамарин светлый", 0x6FAE9F), Thread("993", "Аквамарин очень светлый", 0x90C0B4),
        Thread("995", "Электрик тёмный", 0x2696B6), Thread("996", "Электрик", 0x30C2EC), Thread("3011", "Хаки тёмный", 0x897B56),
        Thread("3012", "Хаки", 0xA6884A), Thread("3013", "Хаки светлый", 0xB9B982), Thread("3021", "Коричнево-серый очень тёмный", 0x4F4B41),
        Thread("3022", "Коричнево-серый", 0x8E9078), Thread("3023", "Коричнево-серый светлый", 0xB1AA97), Thread("3024", "Коричнево-серый очень светлый", 0xEBEAE7),
        Thread("3031", "Мокко очень тёмный", 0x4B3C2A), Thread("3032", "Мокко", 0xB39F8B), Thread("3033", "Мокко очень светлый", 0xE3D8CC),
        Thread("3045", "Жёлто-бежевый тёмный", 0xBC966A), Thread("3046", "Жёлто-бежевый", 0xD8BC9A), Thread("3047", "Жёлто-бежевый светлый", 0xE7D6C1),
        Thread("3064", "Пустынный песок", 0xC48E70), Thread("3072", "Бобровый серый очень светлый", 0xE6E8E8), Thread("3078", "Золотисто-жёлтый очень светлый", 0xFDF9CD),
        Thread("3326", "Роза светлая", 0xFBADB4), Thread("3328", "Лосось тёмный", 0xE36D6D), Thread("3340", "Абрикосовый", 0xFF836F),
        Thread("3341", "Абрикосовый светлый", 0xFCAB98), Thread("3345", "Хвойный тёмный", 0x1B5915), Thread("3346", "Хвойный", 0x406A3A),
        Thread("3347", "Хвойный светлый", 0x71935C), Thread("3348", "Жёлто-зелёный светлый", 0xCCD9B1), Thread("3350", "Пыльная роза ультратёмная", 0xBC4365),
        Thread("3354", "Пыльная роза светлая", 0xE4A6AC), Thread("3362", "Сосновый тёмный", 0x5E6B4C), Thread("3363", "Сосновый", 0x728256),
        Thread("3364", "Сосновый светлый", 0x83975F), Thread("3371", "Чёрно-коричневый", 0x1E1108), Thread("3685", "Розово-лиловый очень тёмный", 0x881531),
        Thread("3687", "Розово-лиловый", 0xC9607A), Thread("3688", "Розово-лиловый светлый", 0xE79CAD), Thread("3689", "Розово-лиловый бледный", 0xFBBFC2),
        Thread("3705", "Дыня тёмная", 0xFF7992), Thread("3706", "Дыня", 0xFFADBC), Thread("3708", "Дыня светлая", 0xFFCBD5),
        Thread("3712", "Лосось", 0xF18787), Thread("3713", "Лосось очень светлый", 0xFFE2E2), Thread("3716", "Пыльная роза очень светлая", 0xFFBDBD),
        Thread("3721", "Розовая ракушка тёмная", 0xA14B51), Thread("3722", "Розовая ракушка", 0xBC6C64), Thread("3726", "Антик лиловый тёмный", 0x9B5B66),
        Thread("3731", "Пыльная роза очень тёмная", 0xDA6783), Thread("3733", "Пыльная роза", 0xE8879B), Thread("3740", "Антик фиолетовый тёмный", 0x785762),
        Thread("3743", "Антик фиолетовый очень светлый", 0xD7CBD3), Thread("3746", "Сине-фиолетовый тёмный", 0x776B98), Thread("3747", "Сине-фиолетовый очень светлый", 0xD3D7ED),
        Thread("3750", "Антик синий очень тёмный", 0x384C5E), Thread("3752", "Антик синий очень светлый", 0xC7D1DB), Thread("3753", "Антик синий ультрасветлый", 0xDBE2E9),
        Thread("3755", "Детский голубой", 0x93B4CE), Thread("3756", "Детский голубой ультрасветлый", 0xEEFCFC), Thread("3760", "Веджвуд", 0x3E85A2),
        Thread("3761", "Небесно-голубой светлый", 0xACD8E2), Thread("3765", "Павлиний синий очень тёмный", 0x347F8C), Thread("3766", "Павлиний синий светлый", 0x99CFD9),
        Thread("3768", "Серо-зелёный тёмный", 0x657F7F), Thread("3770", "Телесный очень светлый", 0xFFEEE3), Thread("3772", "Телесный тёмный", 0xA06C50),
        Thread("3773", "Телесный", 0xB67552), Thread("3774", "Телесный бледный", 0xF3E1D7), Thread("3776", "Красное дерево светлое", 0xCF7939),
        Thread("3778", "Терракота светлая", 0xD98978), Thread("3779", "Терракота ультрасветлая", 0xF8CAC8), Thread("3781", "Мокко тёмный", 0x6B5743),
        Thread("3782", "Мокко светлый", 0x9A7C5C), Thread("3787", "Коричнево-серый тёмный", 0x625D50), Thread("3790", "Бежево-серый ультратёмный", 0x7F6A55),
        Thread("3799", "Оловянный серый очень тёмный", 0x424242), Thread("3801", "Мелоновый тёмный", 0xE74967), Thread("3807", "Васильковый", 0x60678C),
        Thread("3808", "Бирюзовый ультратёмный", 0x366970), Thread("3809", "Бирюзовый очень тёмный", 0x3F7C85), Thread("3810", "Бирюзовый тёмный", 0x488E9A),
        Thread("3811", "Бирюзовый очень светлый", 0xBCE3E6), Thread("3812", "Морская волна очень тёмная", 0x2F8C84), Thread("3813", "Голубовато-зелёный светлый", 0xB2D4BD),
        Thread("3814", "Аквамарин", 0x508B7D), Thread("3815", "Селадон тёмный", 0x477759), Thread("3816", "Селадон", 0x65A57D),
        Thread("3817", "Селадон светлый", 0x99C3AA), Thread("3818", "Изумрудный ультратёмный", 0x115A3B), Thread("3819", "Мох светлый", 0xE0E868),
        Thread("3820", "Соломенный тёмный", 0xDFB65F), Thread("3821", "Соломенный", 0xF3CE75), Thread("3822", "Соломенный светлый", 0xF6DC98),
        Thread("3823", "Жёлтый ультрабледный", 0xFFFDE3), Thread("3824", "Абрикосовый очень светлый", 0xFECDC2), Thread("3825", "Тыквенный бледный", 0xFDBD96),
        Thread("3826", "Золотисто-коричневый", 0xAD7239), Thread("3827", "Золотисто-коричневый бледный", 0xF7BB77), Thread("3828", "Лесной орех", 0xB78B61),
        Thread("3829", "Старое золото очень тёмное", 0xA98204), Thread("3830", "Терракота", 0xB95544), Thread("3831", "Малиновый тёмный", 0xB32F48),
        Thread("3832", "Малиновый", 0xDB556E), Thread("3833", "Малиновый светлый", 0xEA8699), Thread("3834", "Виноград тёмный", 0x72375D),
        Thread("3835", "Виноград", 0x946083), Thread("3836", "Виноград светлый", 0xBA91AA), Thread("3837", "Лаванда ультратёмная", 0x6C3A6E),
        Thread("3838", "Лавандово-синий тёмный", 0x5C7294), Thread("3839", "Лавандово-синий", 0x7B8EAB), Thread("3840", "Лавандово-синий светлый", 0xB0C0DA),
        Thread("3841", "Детский голубой бледный", 0xCDDFED), Thread("3842", "Веджвуд очень тёмный", 0x32667C), Thread("3843", "Электрик", 0x14AAD0),
        Thread("3844", "Ярко-бирюзовый тёмный", 0x12AEBA), Thread("3845", "Ярко-бирюзовый", 0x04C4CA), Thread("3846", "Ярко-бирюзовый светлый", 0x06E3E6),
        Thread("3847", "Тёмная бирюза", 0x347D75), Thread("3848", "Бирюза", 0x559392), Thread("3849", "Бирюза светлая", 0x52B3A4),
        Thread("3850", "Ярко-зелёный тёмный", 0x378477), Thread("3851", "Ярко-зелёный светлый", 0x5CB09E), Thread("3852", "Соломенный очень тёмный", 0xCDA94A),
        Thread("3853", "Осенний золотой тёмный", 0xF29746), Thread("3854", "Осенний золотой", 0xF2AF68), Thread("3855", "Осенний золотой светлый", 0xFAD396),
        Thread("3856", "Мандариновый ультрасветлый", 0xFFD3B5), Thread("3857", "Розовое дерево тёмное", 0x68251A), Thread("3858", "Розовое дерево", 0x96493E),
        Thread("3859", "Розовое дерево светлое", 0xBA8B7C), Thread("3860", "Какао", 0x7D5D57), Thread("3861", "Какао светлый", 0xA68881),
        Thread("3862", "Мокко бежевый тёмный", 0x8A6E4E), Thread("3863", "Мокко бежевый", 0xA4835C), Thread("3864", "Мокко бежевый светлый", 0xCBB08A),
        Thread("3865", "Зимний белый", 0xF9F7F1), Thread("3866", "Мокко коричневый ультрасветлый", 0xFAF6F0),
    )
}
