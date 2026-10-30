package com.dasein.poryadok.ui.wardrobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.WardrobeItem
import com.dasein.poryadok.ui.common.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* Флэт-лэй: вещи разложены на доске 900×1200 (3:4), как в каталоге — без манекена. */
const val BOARD_W = 900f
const val BOARD_H = 1200f

/** Цвет «студийной» поверхности, на которой лежат вещи. */
const val BOARD_RGB = 0xFFF3F0EA.toInt()

val CATEGORIES = listOf("верх", "верхняя одежда", "низ", "обувь", "аксессуары")
val CATEGORY_LABELS = mapOf("верх" to "Верх", "верхняя одежда" to "Верхняя", "низ" to "Низ", "обувь" to "Обувь", "аксессуары" to "Аксессуары")

/** Категории, где в образе может быть только одна вещь (аксессуаров — сколько угодно). */
val SINGLE = setOf("верх", "верхняя одежда", "низ", "обувь")
val Z_ORDER = mapOf("обувь" to 1, "низ" to 2, "верх" to 3, "верхняя одежда" to 4, "аксессуары" to 5)
val SEASONS = listOf("все" to "Все", "зима" to "Зима", "осень" to "Осень", "весна" to "Весна", "лето" to "Лето")

/** Иконки сезонов для фильтров гардероба. */
val SEASON_GLYPHS = mapOf("все" to "ui:hanger", "зима" to "fest/08", "осень" to "ui:leaves", "весна" to "fest/12", "лето" to "ui:sun")

@Serializable
data class Stroke(val r: Float, val pts: List<Float>)

/** Слой: вещь и её четырёхугольник TL, TR, BR, BL (8 чисел) в координатах доски. */
@Serializable
data class LayerData(val id: String, val q: List<Float>, val strokes: List<Stroke> = emptyList())

@Serializable
private data class ManifestItem(val id: String, val name: String, val category: String, val season: String, val file: String, val kind: String = "")

val wjson = Json { ignoreUnknownKeys = true }
val layersSer = ListSerializer(LayerData.serializer())

/** Образы флэт-лэя сохраняются с префиксом: старые (на манекене) раскладываются заново. */
private const val V2 = "v2:"
fun encodeLayers(ls: List<LayerData>) = V2 + wjson.encodeToString(layersSer, ls)
fun decodeLayers(s: String): Pair<List<LayerData>, Boolean> {
    val v2 = s.startsWith(V2)
    val ls = runCatching { wjson.decodeFromString(layersSer, s.removePrefix(V2)) }.getOrDefault(emptyList())
    return ls to v2
}

fun rectQ(x: Float, y: Float, w: Float, h: Float) = listOf(x, y, x + w, y, x + w, y + h, x, y + h)

/** Место категории на доске (доли ширины/высоты): композиция как у каталожного флэт-лэя. */
private fun slot(cat: String, accIndex: Int): RectF = when (cat) {
    "верхняя одежда" -> RectF(.02f, .03f, .38f, .54f)
    "верх" -> RectF(.32f, .03f, .74f, .41f)
    "низ" -> RectF(.38f, .38f, .70f, .97f)
    "обувь" -> RectF(.04f, .57f, .36f, .97f)
    else -> {
        val top = .06f + (accIndex % 4) * .23f
        RectF(.75f, top, .98f, top + .2f)
    }
}

/** Вписать картинку w×h в место категории с сохранением пропорций. */
fun slotQuad(cat: String, accIndex: Int, w: Int, h: Int, boardH: Float = BOARD_H): List<Float> {
    val r = slot(cat, accIndex)
    val bw = r.width() * BOARD_W
    val bh = r.height() * boardH
    val s = min(bw / max(1, w), bh / max(1, h))
    val dw = w * s
    val dh = h * s
    return rectQ(r.left * BOARD_W + (bw - dw) / 2, r.top * boardH + (bh - dh) / 2, dw, dh)
}

/* ---------- реальные размеры ---------- */

/** Тип вещи → её высота в разложенном виде, см (по ней вещи соотносятся: пальто длиннее рубашки, рубашка — футболки). */
val KIND_CM = linkedMapOf(
    "tee" to 70f, "longsleeve" to 71f, "polo" to 70f, "shirt" to 77f, "overshirt" to 76f, "sweatshirt" to 68f, "hoodie" to 70f, "sweater" to 66f,
    "jacket" to 66f, "blazer" to 77f, "parka" to 90f, "coat" to 108f,
    "trousers" to 104f, "joggers" to 98f, "shorts" to 50f,
    "shoes" to 32f, "bag" to 30f, "hat" to 20f, "acc" to 24f,
)

val KIND_LABELS = mapOf(
    "tee" to "Футболка", "longsleeve" to "Лонгслив", "polo" to "Поло", "shirt" to "Рубашка", "overshirt" to "Рубашка-куртка",
    "sweatshirt" to "Свитшот", "hoodie" to "Худи", "sweater" to "Свитер", "jacket" to "Куртка", "blazer" to "Пиджак", "parka" to "Парка",
    "coat" to "Пальто, плащ", "trousers" to "Брюки, джинсы", "joggers" to "Джоггеры", "shorts" to "Шорты", "shoes" to "Обувь",
    "bag" to "Сумка", "hat" to "Головной убор", "acc" to "Аксессуар",
)

/** Типы, подходящие категории. */
fun kindsOf(category: String): List<String> = when (category) {
    "верх" -> listOf("tee", "longsleeve", "polo", "shirt", "overshirt", "sweatshirt", "hoodie", "sweater")
    "верхняя одежда" -> listOf("jacket", "blazer", "parka", "coat")
    "низ" -> listOf("trousers", "joggers", "shorts")
    "обувь" -> listOf("shoes")
    else -> listOf("bag", "hat", "acc")
}

private val KIND_RULES = listOf(
    "рубашка-куртк|куртка-рубашк" to "overshirt", "футболк" to "tee", "лонгслив" to "longsleeve", "поло(?![а-яё])" to "polo", "рубашк|сорочк" to "shirt",
    "свитшот" to "sweatshirt", "худи" to "hoodie", "свитер|джемпер|кардиган" to "sweater", "пиджак|блейзер" to "blazer", "парк[аи]" to "parka",
    "пальто|плащ|тренч" to "coat", "бомбер|куртк|ветровк" to "jacket", "шорт" to "shorts", "джоггер|спортивн|штаны" to "joggers",
    "джинс|брюк|чинос" to "trousers", "кроссов|кед|ботин|туфл|лофер|сапог|сандал" to "shoes", "сумк|рюкзак" to "bag", "кепк|шапк|панам" to "hat",
)

/** Тип по названию, а если не узнали — по категории. */
fun guessKind(name: String, category: String): String {
    val l = name.lowercase()
    val byName = KIND_RULES.firstOrNull { Regex(it.first).containsMatchIn(l) }?.second
    return byName?.takeIf { it in kindsOf(category) } ?: kindsOf(category).let {
        when (category) { "верх" -> "shirt"; "верхняя одежда" -> "jacket"; "низ" -> "trousers"; else -> it.first() }
    }
}

/** Тип вещи: сохранённый (в поле pct) или угаданный. */
fun WardrobeItem.kind(): String = pct.takeIf { it in KIND_CM && it in kindsOf(category) } ?: guessKind(name, category)
fun WardrobeItem.realCm(): Float = KIND_CM[kind()] ?: 60f

/**
 * Коллаж как в Fits — вещи «надеты»: верх от плеч, низ от талии (≈45 см ниже плеч, верх навыпуск закрывает пояс),
 * обувь у низа брюк; все в одном масштабе по реальной длине, так что пальто длиннее рубашки. Аксессуары справа.
 */
fun collageLayout(ctx: Context, ids: List<String>, items: Map<String, WardrobeItem>, boardH: Float = BOARD_H): List<LayerData> {
    val list = ids.mapNotNull { items[it] }
    if (list.isEmpty()) return emptyList()
    fun row(c: String) = when (c) { "верх", "верхняя одежда" -> 0; "низ" -> 1; "обувь" -> 2; else -> -1 }
    data class P(val item: WardrobeItem, val top: Float, val h: Float, val w: Float)
    fun aspect(it: WardrobeItem) = Bitmaps.load(ctx, it.src)?.let { b -> b.width.toFloat() / b.height } ?: .8f
    val upper = list.filter { row(it.category) == 0 }
    val lower = list.filter { row(it.category) == 1 }
    val shoes = list.filter { row(it.category) == 2 }
    val acc = list.filter { row(it.category) < 0 }
    val placed = mutableListOf<P>()
    upper.forEach { placed += P(it, 0f, it.realCm(), it.realCm() * aspect(it)) }
    val waist = if (upper.isEmpty()) 0f else 45f
    lower.forEach { placed += P(it, waist, it.realCm(), it.realCm() * aspect(it)) }
    val lowerEnd = lower.maxOfOrNull { waist + it.realCm() } ?: (if (upper.isEmpty()) 0f else 148f)
    shoes.forEach { val h = it.realCm(); placed += P(it, (lowerEnd - h * .45f).coerceAtLeast(0f), h, h * aspect(it)) }
    val totalH = placed.maxOfOrNull { it.top + it.h } ?: 1f
    val maxW = placed.maxOfOrNull { it.w } ?: 1f
    val left = .06f * BOARD_W
    val right = (if (acc.isEmpty()) .94f else .76f) * BOARD_W
    val topPad = .04f * boardH
    val k = min((boardH * .92f) / totalH, (right - left) / maxW)
    val cx = (left + right) / 2
    val oy = topPad + (boardH * .92f - totalH * k) / 2
    val out = placed.map { p -> LayerData(p.item.id, rectQ(cx - p.w * k / 2, oy + p.top * k, p.w * k, p.h * k)) }.toMutableList()
    acc.forEachIndexed { i, it0 ->
        val h = (it0.realCm() * k).coerceAtMost(boardH * .17f)
        val w = h * aspect(it0)
        out += LayerData(it0.id, rectQ(.87f * BOARD_W - w / 2, .06f * boardH + (i % 5) * .19f * boardH, w, h))
    }
    return out.sortedBy { l -> Z_ORDER[items[l.id]?.category] ?: 6 }
}

/** Заменить вещь на другую, сохранив место, поворот и масштаб «см → точки». */
fun swapLayer(ctx: Context, old: LayerData, oldItem: WardrobeItem, item: WardrobeItem): LayerData {
    val b = Bitmaps.load(ctx, item.src)
    val aspect = b?.let { it.width.toFloat() / it.height } ?: .8f
    val q = old.q
    val cx = (q[0] + q[2] + q[4] + q[6]) / 4
    val oldH = hypot(q[6] - q[0], q[7] - q[1])
    val h = oldH * item.realCm() / oldItem.realCm()
    val w = h * aspect
    // Для одежды верх держим на том же уровне (плечи/пояс), для прочего — центр.
    val top = if (oldItem.category in SINGLE && item.category in SINGLE) min(q[1], q[3]) else (q[1] + q[3] + q[5] + q[7]) / 4 - h / 2
    return LayerData(item.id, rectQ(cx - w / 2, top, w, h))
}

/** Главные цвета вещи по фото (до трёх), #RRGGBB через запятую. */
fun dominantColors(b: Bitmap): String {
    val s = Bitmap.createScaledBitmap(b, 48, max(1, 48 * b.height / max(1, b.width)), true)
    val counts = HashMap<Int, Int>()
    val sums = HashMap<Int, LongArray>()
    for (y in 0 until s.height) for (x in 0 until s.width) {
        val p = s.getPixel(x, y)
        if ((p ushr 24) < 200) continue
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val bl = p and 0xFF
        val key = (r / 48) * 100 + (g / 48) * 10 + bl / 48
        counts[key] = (counts[key] ?: 0) + 1
        sums.getOrPut(key) { LongArray(3) }.let { it[0] += r.toLong(); it[1] += g.toLong(); it[2] += bl.toLong() }
    }
    val total = counts.values.sum().coerceAtLeast(1)
    return counts.entries.sortedByDescending { it.value }.filter { it.value * 100 / total >= 8 }.take(3).joinToString(",") { e ->
        val sm = sums.getValue(e.key)
        "#%02X%02X%02X".format(sm[0] / e.value, sm[1] / e.value, sm[2] / e.value)
    }
}

/** Флэт-лэй: вещи по местам категорий, размеры — по реальной длине в одном масштабе. */
fun autoLayout(ctx: Context, ids: List<String>, items: Map<String, WardrobeItem>, boardH: Float = BOARD_H): List<LayerData> {
    var acc = 0
    val k = 6.4f // точек доски на сантиметр
    return ids.mapNotNull { items[it] }.sortedBy { Z_ORDER[it.category] ?: 6 }.map { item ->
        val b = Bitmaps.load(ctx, item.src)
        val w = b?.width ?: 10
        val h = b?.height ?: 10
        val i = if (item.category in SINGLE) 0 else acc++
        val slotQ = slotQuad(item.category, i, w, h, boardH)
        // Высота по реальному размеру, но не больше места категории с небольшим запасом.
        val slotH = slotQ[7] - slotQ[1]
        val realH = item.realCm() * k
        val dh = min(realH, slotH * 1.12f)
        val dw = dh * w / h
        val cx = (slotQ[0] + slotQ[2]) / 2
        val cy = (slotQ[1] + slotQ[7]) / 2
        LayerData(item.id, rectQ(cx - dw / 2, cy - dh / 2, dw, dh))
    }
}

object WardrobeSeed {
    private const val VERSION = 4

    suspend fun run() {
        val s = Graph.prefs.now()
        if (s.wardrobeSeed >= VERSION) return
        val dao = Graph.dao
        if (s.wardrobeSeed < 2) {
            // Переход на флэт-лэй: старые встроенные вещи, их подгонка и луки с ними удаляются.
            val old = dao.builtinWardrobeIds().toSet()
            if (old.isNotEmpty()) {
                dao.outfits().first().filter { o -> decodeLayers(o.layers).first.any { it.id in old } }.forEach { o ->
                    if (o.preview.isNotBlank()) File(o.preview).delete()
                    dao.deleteOutfit(o)
                }
                dao.deleteAllFits()
                dao.deleteBuiltinWardrobe()
            }
        }
        val items = withContext(Dispatchers.IO) {
            val text = Graph.app.assets.open("wardrobe/items.json").bufferedReader().use { it.readText() }
            wjson.decodeFromString(ListSerializer(ManifestItem.serializer()), text)
        }
        // v3–v4: коллекция из архивов; встроенные вещи, которых нет в каталоге (дубли), убираются, остальным проставляется тип.
        dao.deleteBuiltinExcept(items.map { it.id })
        val now = System.currentTimeMillis()
        dao.insertWardrobeItems(items.mapIndexed { i, it ->
            WardrobeItem(id = it.id, name = it.name, category = it.category, season = it.season, src = "asset:wardrobe/${it.file}", pct = it.kind, builtin = true, addedAt = now - i)
        })
        val kinds = items.associate { it.id to it.kind }
        dao.wardrobeAll().filter { it.builtin && it.pct.isBlank() && kinds[it.id] != null }.forEach { dao.upsertWardrobeItem(it.copy(pct = kinds.getValue(it.id))) }
        Graph.prefs.update { it.copy(wardrobeSeed = VERSION) }
    }
}

/** Кэш картинок вещей и их мягких теней. */
object Bitmaps {
    private val originals = LruCache<String, Bitmap>(80)
    private val shadows = LruCache<String, Bitmap>(80)
    private const val SHADOW_K = 4
    private const val SHADOW_PAD = 10

    fun load(ctx: Context, src: String): Bitmap? {
        originals.get(src)?.let { return it }
        val decoded = runCatching {
            if (src.startsWith("asset:")) ctx.assets.open(src.removePrefix("asset:")).use { BitmapFactory.decodeStream(it) }
            else BitmapFactory.decodeFile(src)
        }.getOrNull() ?: return null
        val bmp = stripBackground(decoded)
        originals.put(src, bmp)
        return bmp
    }

    /** Размытый силуэт вещи в 1/4 размера с полями [SHADOW_PAD] — тень рисует приложение, а не генерация. */
    fun shadow(src: String, bmp: Bitmap): Bitmap {
        shadows.get(src)?.let { return it }
        val sw = max(1, bmp.width / SHADOW_K)
        val sh = max(1, bmp.height / SHADOW_K)
        val alpha = Bitmap.createScaledBitmap(bmp, sw, sh, true).extractAlpha()
        val out = Bitmap.createBitmap(sw + 2 * SHADOW_PAD, sh + 2 * SHADOW_PAD, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 60, 45, 30)
            maskFilter = BlurMaskFilter(SHADOW_PAD * .7f, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(out).drawBitmap(alpha, SHADOW_PAD.toFloat(), SHADOW_PAD.toFloat(), p)
        shadows.put(src, out)
        return out
    }

    /** Матрица тени к матрице вещи [m]: пиксели тени → пиксели вещи (учёт масштаба и полей). */
    fun shadowMatrix(m: Matrix, bmp: Bitmap, sh: Bitmap): Matrix {
        val sw = (sh.width - 2 * SHADOW_PAD).toFloat()
        val shh = (sh.height - 2 * SHADOW_PAD).toFloat()
        return Matrix(m).apply {
            preScale(bmp.width / sw, bmp.height / shh)
            preTranslate(-SHADOW_PAD.toFloat(), -SHADOW_PAD.toFloat())
        }
    }

    fun invalidate(src: String) { originals.remove(src); shadows.remove(src) }
}

/** Матрица, переводящая прямоугольник картинки w×h в четырёхугольник q. */
fun quadMatrix(w: Float, h: Float, q: List<Float>, scale: Float = 1f, ox: Float = 0f, oy: Float = 0f): Matrix {
    val dst = FloatArray(8) { i -> if (i % 2 == 0) ox + q[i] * scale else oy + q[i] * scale }
    return Matrix().apply { setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, dst, 0, 4) }
}

/** Точка доски → локальные доли (u, v) внутри четырёхугольника. */
fun toLocal(q: List<Float>, x: Float, y: Float): Pair<Float, Float>? {
    val m = quadMatrix(1f, 1f, q)
    val inv = Matrix()
    if (!m.invert(inv)) return null
    val p = floatArrayOf(x, y)
    inv.mapPoints(p)
    return p[0] to p[1]
}

fun quadWidth(q: List<Float>) = hypot(q[2] - q[0], q[3] - q[1])

/** Вещь с мягкой тенью (тень чуть ниже и правее — свет сверху-слева). */
fun drawItem(canvas: Canvas, src: String, bmp: Bitmap, m: Matrix, shadowShift: Float, paint: Paint) {
    val sh = Bitmaps.shadow(src, bmp)
    val sm = Bitmaps.shadowMatrix(m, bmp, sh).apply { postTranslate(shadowShift * .5f, shadowShift) }
    canvas.drawBitmap(sh, sm, paint)
    canvas.drawBitmap(bmp, m, paint)
}

/** Рисует доску обычным android-холстом — и на экране, и для превью образа. Порядок слоёв = порядок списка. */
fun drawBoard(ctx: Context, canvas: Canvas, scale: Float, ox: Float, oy: Float, layers: List<LayerData>, items: Map<String, WardrobeItem>) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    layers.forEach { l ->
        val item = items[l.id] ?: return@forEach
        val bmp = Bitmaps.load(ctx, item.src) ?: return@forEach
        drawItem(canvas, item.src, bmp, quadMatrix(bmp.width.toFloat(), bmp.height.toFloat(), l.q, scale, ox, oy), 9f * scale, paint)
    }
}

fun renderPreview(ctx: Context, layers: List<LayerData>, items: Map<String, WardrobeItem>, bg: Int = 0, boardH: Float = BOARD_H): String? {
    val w = 600
    val h = (w * boardH / BOARD_W).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(if (bg != 0) bg else BOARD_RGB)
    drawBoard(ctx, c, w / BOARD_W, 0f, 0f, layers, items)
    return runCatching {
        val dir = File(ctx.filesDir, "outfits").apply { mkdirs() }
        val f = File(dir, "look_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        f.absolutePath
    }.getOrNull()
}

/* ---------- свои вещи: обработка фото ---------- */

/** Порт stripBg: заливка от краёв по цвету фона, если фон однородный, плюс снятие светлой каймы. */
fun stripBackground(bmp: Bitmap): Bitmap {
    val w = bmp.width
    val h = bmp.height
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    fun alpha(p: Int) = px[p] ushr 24
    var clear = 0
    var tot = 0
    for (x in 0 until w) { tot += 2; if (alpha(x) < 16) clear++; if (alpha((h - 1) * w + x) < 16) clear++ }
    for (y in 0 until h) { tot += 2; if (alpha(y * w) < 16) clear++; if (alpha(y * w + w - 1) < 16) clear++ }
    if (tot > 0 && clear.toFloat() / tot > .5f) return bmp
    val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
    val corners = listOf(0, w - 1, (h - 1) * w, (h - 1) * w + w - 1).map { px[it] }
    fun ch(c: Int, j: Int) = (c shr (16 - 8 * j)) and 0xFF
    val bg = IntArray(3) { j -> corners.sumOf { ch(it, j) } / 4 }
    val spread = (0..2).maxOf { j -> corners.maxOf { abs(ch(it, j) - bg[j]) } }
    if (spread > 30) return out
    val tol = 26
    fun near(p: Int, k: Int) = (0..2).all { j -> abs(ch(px[p], j) - bg[j]) <= k }
    val seen = BooleanArray(w * h)
    val stack = IntArray(w * h)
    var sp = 0
    fun push(p: Int) {
        if (seen[p]) return
        if (alpha(p) < 16 || near(p, tol)) { seen[p] = true; stack[sp++] = p }
    }
    for (x in 0 until w) { push(x); push((h - 1) * w + x) }
    for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
    while (sp > 0) {
        val p = stack[--sp]
        val x = p % w
        val y = p / w
        px[p] = px[p] and 0x00FFFFFF
        if (x > 0) push(p - 1)
        if (x < w - 1) push(p + 1)
        if (y > 0) push(p - w)
        if (y < h - 1) push(p + w)
    }
    val edge = BooleanArray(w * h)
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val p = y * w + x
        if (alpha(p) < 16) continue
        var n = 0
        if (alpha(p - 1) < 16) n++
        if (alpha(p + 1) < 16) n++
        if (alpha(p - w) < 16) n++
        if (alpha(p + w) < 16) n++
        if (n >= 2 && near(p, (tol * 1.7).toInt())) edge[p] = true
    }
    for (p in 0 until w * h) if (edge[p]) px[p] = px[p] and 0x00FFFFFF
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
}

private fun alphaBox(b: Bitmap): IntArray? {
    val w = b.width
    val h = b.height
    val px = IntArray(w * h)
    b.getPixels(px, 0, w, 0, 0, w, h)
    var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
    for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 8) {
        x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
    }
    return if (x1 < 0) null else intArrayOf(x0, y0, x1 + 1, y1 + 1)
}

/** Есть ли у картинки свой прозрачный фон (больше половины краёв прозрачные). */
private fun hasAlphaBackground(b: Bitmap): Boolean {
    val w = b.width; val h = b.height
    var clear = 0; var tot = 0
    for (x in 0 until w step 4) { tot += 2; if ((b.getPixel(x, 0) ushr 24) < 16) clear++; if ((b.getPixel(x, h - 1) ushr 24) < 16) clear++ }
    for (y in 0 until h step 4) { tot += 2; if ((b.getPixel(0, y) ushr 24) < 16) clear++; if ((b.getPixel(w - 1, y) ushr 24) < 16) clear++ }
    return tot > 0 && clear * 2 > tot
}

/** Вырезать вещь нейросетью ML Kit (на телефоне); null — если модель недоступна или ничего не нашла. */
private suspend fun segmentSubject(b: Bitmap): Bitmap? = runCatching {
    kotlinx.coroutines.withTimeout(20_000) {
        kotlinx.coroutines.suspendCancellableCoroutine<Bitmap?> { cont ->
            val opts = com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
            val client = com.google.mlkit.vision.segmentation.subject.SubjectSegmentation.getClient(opts)
            client.process(com.google.mlkit.vision.common.InputImage.fromBitmap(b, 0))
                .addOnSuccessListener { r -> client.close(); if (cont.isActive) cont.resumeWith(Result.success(r.foregroundBitmap)) }
                .addOnFailureListener { client.close(); if (cont.isActive) cont.resumeWith(Result.success(null)) }
        }
    }
}.getOrNull()

/**
 * Своё фото вещи: если фон уже прозрачный — оставляем как есть; иначе вырезаем вещь нейросетью,
 * а если она недоступна — заливкой однородного фона от краёв. Края обрезаются, сохраняется PNG с прозрачностью.
 */
suspend fun prepareUserItem(ctx: Context, uri: android.net.Uri): String? = withContext(Dispatchers.Default) {
    runCatching {
        val tmp = Images.importUri(ctx, uri, "tmp") ?: return@runCatching null
        val decoded = Images.decode(tmp, 1400) ?: return@runCatching null
        File(tmp).delete()
        val argb = if (decoded.config == Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888, false)
        val cleaned = when {
            hasAlphaBackground(argb) -> argb
            else -> segmentSubject(argb)?.takeIf { alphaBox(it) != null } ?: stripBackground(argb)
        }
        val bb = alphaBox(cleaned) ?: return@runCatching null
        val cw = bb[2] - bb[0]
        val chh = bb[3] - bb[1]
        val s = min(1f, 900f / max(cw, chh))
        val cropped = Bitmap.createBitmap(cleaned, bb[0], bb[1], cw, chh)
        val scaled = Bitmap.createScaledBitmap(cropped, max(1, (cw * s).roundToInt()), max(1, (chh * s).roundToInt()), true)
        val dir = File(ctx.filesDir, "wardrobe").apply { mkdirs() }
        val f = File(dir, "user_${System.currentTimeMillis()}.png")
        f.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        f.absolutePath
    }.getOrNull()
}
