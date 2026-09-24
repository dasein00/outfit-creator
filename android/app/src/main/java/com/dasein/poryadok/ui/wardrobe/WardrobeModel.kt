package com.dasein.poryadok.ui.wardrobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.LruCache
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.ItemFit
import com.dasein.poryadok.data.WardrobeItem
import com.dasein.poryadok.ui.common.Images
import kotlinx.coroutines.Dispatchers
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

/* Мир 500×1000, манекен шириной 311.4 по центру — как в веб-версии. */
const val WORLD_W = 500f
const val WORLD_H = 1000f
const val MW = 311.4f
const val MX0 = (WORLD_W - MW) / 2
const val MODEL_ID = "__model__"

val CATEGORIES = listOf("верх", "низ", "обувь", "аксессуары")
val Z_ORDER = mapOf("обувь" to 1, "низ" to 2, "верх" to 3, "аксессуары" to 4)
val SEASONS = listOf("все" to "👕 Все", "зима" to "❄️ Зима", "осень" to "🍂 Осень", "весна" to "🌸 Весна", "лето" to "☀️ Лето")

@Serializable
data class Stroke(val r: Float, val pts: List<Float>)

/** Слой: вещь и её четырёхугольник TL, TR, BR, BL (8 чисел) в мировых координатах плюс мазки ластика в долях 0..1. */
@Serializable
data class LayerData(val id: String, val q: List<Float>, val strokes: List<Stroke> = emptyList())

@Serializable
private data class ManifestItem(
    val id: String,
    val name: String,
    val category: String,
    val season: String,
    val pct: List<Double>,
    val file: String,
    val sketch: Boolean = false,
)

val wjson = Json { ignoreUnknownKeys = true }
val layersSer = ListSerializer(LayerData.serializer())
val strokesSer = ListSerializer(Stroke.serializer())

fun rectQ(x: Float, y: Float, w: Float, h: Float) = listOf(x, y, x + w, y, x + w, y + h, x, y + h)
fun modelQuad() = rectQ(MX0, 0f, MW, WORLD_H)

fun parsePct(s: String): List<Float> = s.split(",").mapNotNull { it.trim().toFloatOrNull() }

fun defaultQuad(item: WardrobeItem, bmp: Bitmap?): List<Float> {
    val p = parsePct(item.pct).let { if (it.size < 3) listOf(80f, 50f, 40f) else it }
    val w = p[0] * MW / 100
    val h = if (p.size > 3) p[3] * 10 else w * ((bmp?.height ?: 12).toFloat() / (bmp?.width ?: 10))
    return rectQ(MX0 + p[1] * MW / 100 - w / 2, p[2] * 10, w, h)
}

object WardrobeSeed {
    suspend fun run() {
        val s = Graph.prefs.now()
        if (s.wardrobeSeed >= 1) return
        val items = withContext(Dispatchers.IO) {
            val text = Graph.app.assets.open("wardrobe/items.json").bufferedReader().use { it.readText() }
            wjson.decodeFromString(ListSerializer(ManifestItem.serializer()), text)
        }
        Graph.dao.insertWardrobeItems(items.map {
            WardrobeItem(
                id = it.id, name = it.name, category = it.category, season = it.season,
                src = "asset:wardrobe/${it.file}", pct = it.pct.joinToString(","), builtin = true, sketch = it.sketch,
            )
        })
        Graph.prefs.update { it.copy(wardrobeSeed = 1) }
    }
}

/** Кэш исходных картинок и картинок с применённым ластиком. */
object Bitmaps {
    private val originals = LruCache<String, Bitmap>(80)
    private val erased = LruCache<String, Bitmap>(30)

    fun load(ctx: Context, src: String): Bitmap? {
        originals.get(src)?.let { return it }
        val bmp = runCatching {
            if (src.startsWith("asset:")) ctx.assets.open(src.removePrefix("asset:")).use { BitmapFactory.decodeStream(it) }
            else BitmapFactory.decodeFile(src)
        }.getOrNull() ?: return null
        originals.put(src, bmp)
        return bmp
    }

    fun withStrokes(key: String, bmp: Bitmap, strokes: List<Stroke>): Bitmap {
        if (strokes.isEmpty()) return bmp
        val k = key + "#" + strokes.hashCode()
        erased.get(k)?.let { return it }
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dot = Paint(paint).apply { style = Paint.Style.FILL }
        strokes.forEach { s ->
            val r = s.r * out.width
            paint.strokeWidth = r * 2
            val pts = s.pts
            if (pts.size < 2) return@forEach
            if (pts.size == 2) {
                c.drawCircle(pts[0] * out.width, pts[1] * out.height, r, dot)
            } else {
                val path = Path()
                path.moveTo(pts[0] * out.width, pts[1] * out.height)
                var i = 2
                while (i + 1 < pts.size) { path.lineTo(pts[i] * out.width, pts[i + 1] * out.height); i += 2 }
                c.drawPath(path, paint)
            }
        }
        erased.put(k, out)
        return out
    }

    fun invalidate(src: String) { originals.remove(src) }
}

/** Матрица, переводящая прямоугольник картинки w×h в четырёхугольник q (перспектива через setPolyToPoly). */
fun quadMatrix(w: Float, h: Float, q: List<Float>, scale: Float = 1f, ox: Float = 0f, oy: Float = 0f): Matrix {
    val dst = FloatArray(8) { i -> if (i % 2 == 0) ox + q[i] * scale else oy + q[i] * scale }
    return Matrix().apply { setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, dst, 0, 4) }
}

/** Мировая точка → локальные доли (u, v) внутри четырёхугольника. */
fun toLocal(q: List<Float>, x: Float, y: Float): Pair<Float, Float>? {
    val m = quadMatrix(1f, 1f, q)
    val inv = Matrix()
    if (!m.invert(inv)) return null
    val p = floatArrayOf(x, y)
    inv.mapPoints(p)
    return p[0] to p[1]
}

fun quadWidth(q: List<Float>) = hypot(q[2] - q[0], q[3] - q[1])

/** Рисует всю сцену обычным android-холстом — и на экране, и для превью образа. */
fun drawScene(
    ctx: Context,
    canvas: Canvas,
    scale: Float,
    ox: Float,
    oy: Float,
    layers: List<LayerData>,
    items: Map<String, WardrobeItem>,
    modelStrokes: List<Stroke>,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    Bitmaps.load(ctx, "asset:wardrobe/model.webp")?.let { m ->
        val b = Bitmaps.withStrokes(MODEL_ID, m, modelStrokes)
        canvas.drawBitmap(b, quadMatrix(b.width.toFloat(), b.height.toFloat(), modelQuad(), scale, ox, oy), paint)
    }
    layers.sortedBy { Z_ORDER[items[it.id]?.category] ?: 5 }.forEach { l ->
        val item = items[l.id] ?: return@forEach
        val bmp = Bitmaps.load(ctx, item.src) ?: return@forEach
        val b = Bitmaps.withStrokes(l.id, bmp, l.strokes)
        canvas.drawBitmap(b, quadMatrix(b.width.toFloat(), b.height.toFloat(), l.q, scale, ox, oy), paint)
    }
}

fun renderPreview(ctx: Context, layers: List<LayerData>, items: Map<String, WardrobeItem>, modelStrokes: List<Stroke>): String? {
    val w = 300
    val h = 600
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.rgb(0xE6, 0xE1, 0xD5))
    drawScene(ctx, c, w / WORLD_W, 0f, 0f, layers, items, modelStrokes)
    return runCatching {
        val dir = File(ctx.filesDir, "outfits").apply { mkdirs() }
        val f = File(dir, "look_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        f.absolutePath
    }.getOrNull()
}

suspend fun saveFit(l: LayerData) {
    Graph.dao.upsertFit(ItemFit(l.id, l.q.joinToString(",") { "%.2f".format(java.util.Locale.US, it) }, wjson.encodeToString(strokesSer, l.strokes)))
}

fun fitToLayer(f: ItemFit): LayerData? {
    val q = f.quad.split(",").mapNotNull { it.toFloatOrNull() }
    if (q.size != 8) return null
    val s = runCatching { wjson.decodeFromString(strokesSer, f.strokes) }.getOrDefault(emptyList())
    return LayerData(f.itemId, q, s)
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
    val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
    if (tot > 0 && clear.toFloat() / tot > .5f) return out
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

/** Подгонка по умолчанию для своей вещи: как autoPct в веб-версии. */
fun autoPct(b: Bitmap, cat: String): List<Float> {
    when (cat) {
        "верх" -> return listOf(106f, 50f, 12.8f)
        "обувь" -> return listOf(88f, 50f, 90f)
        "аксессуары" -> return listOf(40f, 48f, 2f)
    }
    val w = b.width
    val h = b.height
    val y = (h * .05f).roundToInt().coerceIn(0, h - 1)
    var mn = -1
    var mx = -1
    for (i in 0 until w) if ((b.getPixel(i, y) ushr 24) > 60) { if (mn < 0) mn = i; mx = i }
    val waist = if (mx < 0) .7f else (mx - mn + 1f) / w
    val wp = (57.5f / waist).coerceIn(55f, 105f)
    return if (h.toFloat() / w > 1.6f) listOf(wp, 50f, 42f, 54f) else listOf(wp, 50f, 42f)
}

data class Prepared(val path: String, val pct: List<Float>)

suspend fun prepareUserItem(ctx: Context, uri: android.net.Uri, cat: String): Prepared? = withContext(Dispatchers.Default) {
    runCatching {
        val tmp = Images.importUri(ctx, uri, "tmp") ?: return@runCatching null
        val decoded = Images.decode(tmp, 1100) ?: return@runCatching null
        File(tmp).delete()
        val cleaned = stripBackground(decoded)
        val bb = alphaBox(cleaned) ?: return@runCatching null
        val cw = bb[2] - bb[0]
        val chh = bb[3] - bb[1]
        val s = min(1f, 460f / cw)
        val cropped = Bitmap.createBitmap(cleaned, bb[0], bb[1], cw, chh)
        val scaled = Bitmap.createScaledBitmap(cropped, max(1, (cw * s).roundToInt()), max(1, (chh * s).roundToInt()), true)
        val dir = File(ctx.filesDir, "wardrobe").apply { mkdirs() }
        val f = File(dir, "user_${System.currentTimeMillis()}.png")
        f.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Prepared(f.absolutePath, autoPct(scaled, cat))
    }.getOrNull()
}
