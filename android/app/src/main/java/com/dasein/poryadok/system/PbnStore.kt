package com.dasein.poryadok.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import com.dasein.poryadok.logic.PaintByNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/** Картина по номерам: фото и настройки. Лежит в files/crafts/pbn/ — попадает в резервную копию вместе с рукоделием. */
@Serializable
data class PbnProject(
    val id: Long,
    val name: String,
    /** Сколько цветов взять (не больше, чем карандашей). */
    val colors: Int = 16,
    /** 0 — крупно, 1 — средне, 2 — подробно. */
    val detail: Int = 1,
    /** Размер листа: 0 — A4, 1 — A3. */
    val paper: Int = 0,
    val createdAt: Long = 0,
)

/** Свой карандаш, которого нет в стандартном списке. */
@Serializable
data class MyPencil(val n: Int, val rgb: Int, val name: String = "")

object PbnStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun root(ctx: Context) = File(File(ctx.filesDir, "crafts"), "pbn").apply { mkdirs() }
    fun photo(ctx: Context, id: Long) = File(root(ctx), "p$id.jpg")
    private fun meta(ctx: Context, id: Long) = File(root(ctx), "p$id.json")

    fun list(ctx: Context): List<PbnProject> = root(ctx).listFiles().orEmpty().filter { it.name.endsWith(".json") }
        .mapNotNull { f -> runCatching { json.decodeFromString(PbnProject.serializer(), f.readText()) }.getOrNull() }
        .sortedByDescending { it.createdAt }

    fun load(ctx: Context, id: Long): PbnProject? = runCatching { json.decodeFromString(PbnProject.serializer(), meta(ctx, id).readText()) }.getOrNull()
    fun save(ctx: Context, p: PbnProject) = meta(ctx, p.id).writeText(json.encodeToString(PbnProject.serializer(), p))
    fun delete(ctx: Context, id: Long) { photo(ctx, id).delete(); meta(ctx, id).delete() }

    suspend fun create(ctx: Context, uri: Uri): PbnProject = withContext(Dispatchers.IO) {
        val id = System.currentTimeMillis()
        val bmp = CraftStore.decode(ctx, uri, 1400) ?: error("Не удалось открыть фото")
        photo(ctx, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val n = list(ctx).size + 1
        PbnProject(id, "Картина $n", createdAt = id).also { save(ctx, it) }
    }

    // ---------- Мои карандаши ----------

    /** Меняется при каждом изменении набора карандашей. */
    val version = mutableIntStateOf(0)
    private fun sp(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
    private fun customFile(ctx: Context) = File(root(ctx), "pencils.palette")

    /** Отмеченные стандартные карандаши. По умолчанию — набор из 24 цветов. */
    fun owned(ctx: Context): Set<String> = sp(ctx).getString("pbn_owned", null)?.split(',')?.filter { it.isNotBlank() }?.toSet()
        ?: PaintByNumbers.SETS[1].second.toSet()

    fun setOwned(ctx: Context, s: Set<String>) { sp(ctx).edit().putString("pbn_owned", s.joinToString(",")).apply(); version.intValue++ }

    fun custom(ctx: Context): List<MyPencil> = runCatching {
        json.decodeFromString(ListSerializer(MyPencil.serializer()), customFile(ctx).readText())
    }.getOrDefault(emptyList())

    fun setCustom(ctx: Context, l: List<MyPencil>) { runCatching { customFile(ctx).writeText(json.encodeToString(ListSerializer(MyPencil.serializer()), l)) }; version.intValue++ }

    /** Все карандаши, которые есть: отмеченные из списка + свои («К1», «К2»…). */
    fun pencils(ctx: Context): List<PaintByNumbers.Pencil> {
        val own = owned(ctx)
        return PaintByNumbers.CATALOG.filter { it.code in own } +
            custom(ctx).map { PaintByNumbers.Pencil("К${it.n}", it.name.ifBlank { "Свой цвет ${it.n}" }, it.rgb) }
    }

    // ---------- Расчёт ----------

    /** Раскраска по фото: сторона до ~1100 точек — контуры получаются плавными и на A3. */
    suspend fun build(ctx: Context, p: PbnProject): PaintByNumbers.Result? = withContext(Dispatchers.Default) {
        val bmp = BitmapFactory.decodeFile(photo(ctx, p.id).absolutePath) ?: return@withContext null
        val long = 1100
        val k = long.toFloat() / maxOf(bmp.width, bmp.height)
        val w = maxOf(40, (bmp.width * k).roundToInt()); val h = maxOf(40, (bmp.height * k).roundToInt())
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }.map { it and 0xFFFFFF }.toIntArray()
        val pencils = pencils(ctx).takeIf { it.size >= 2 } ?: PaintByNumbers.CATALOG
        PaintByNumbers.build(px, w, h, pencils, p.colors, p.detail)
    }

    /** Картинка для экрана: [colored] — раскрашенная, иначе — контуры с номерами, как на печати. */
    fun render(r: PaintByNumbers.Result, colored: Boolean, scale: Float = 1f): Bitmap {
        val w = (r.width * scale).roundToInt(); val h = (r.height * scale).roundToInt()
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        if (colored) {
            val px = IntArray(r.cells.size) { r.colors[r.cells[it]].rgb or (0xFF shl 24) }
            val src = Bitmap.createBitmap(px, r.width, r.height, Bitmap.Config.ARGB_8888)
            c.drawBitmap(Bitmap.createScaledBitmap(src, w, h, false), 0f, 0f, null)
        } else c.drawColor(Color.WHITE)
        drawOutline(c, r, scale, 0f, 0f, if (colored) 0x66000000 else 0xFF9A9A9A.toInt(), maxOf(1f, scale * 0.6f))
        drawNumbers(c, r, scale, 0f, 0f, colored, minPx = 6f * maxOf(1f, scale), maxPx = 18f * maxOf(1f, scale))
        return b
    }

    private fun drawOutline(c: Canvas, r: PaintByNumbers.Result, s: Float, ox: Float, oy: Float, color: Int, width: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = width; style = Paint.Style.STROKE; strokeCap = Paint.Cap.SQUARE }
        val segs = PaintByNumbers.outline(r.cells, r.width, r.height)
        val pts = FloatArray(segs.size * 4)
        segs.forEachIndexed { i, sg -> pts[i * 4] = ox + sg[0] * s; pts[i * 4 + 1] = oy + sg[1] * s; pts[i * 4 + 2] = ox + sg[2] * s; pts[i * 4 + 3] = oy + sg[3] * s }
        c.drawLines(pts, p)
        // Рамка вокруг картины.
        c.drawRect(ox, oy, ox + r.width * s, oy + r.height * s, p)
    }

    private fun drawNumbers(c: Canvas, r: PaintByNumbers.Result, s: Float, ox: Float, oy: Float, colored: Boolean, minPx: Float, maxPx: Float) {
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
        r.regions.forEach { g ->
            // Цифра помещается, если до края области хватает места.
            val size = (g.room * s * 1.25f).coerceIn(minPx * 0.7f, maxPx)
            if (g.room * s * 2.1f < minPx * 0.7f) return@forEach
            t.textSize = size
            t.color = if (colored) (if (PaintByNumbers.dark(r.colors[g.color].rgb)) Color.WHITE else Color.BLACK) else 0xFF8A8A8A.toInt()
            c.drawText("${g.color + 1}", ox + (g.x + 0.5f) * s, oy + (g.y + 0.5f) * s + size * 0.36f, t)
        }
    }

    const val A4_W = 595; const val A4_H = 842

    /** PDF для печати: 1-я страница — раскраска с контурами, номерами и таблицей карандашей, 2-я — цветной образец. */
    suspend fun pdf(ctx: Context, p: PbnProject, r: PaintByNumbers.Result, out: OutputStream) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val base = if (p.paper == 1) Pair(842, 1191) else Pair(A4_W, A4_H)
        val landscape = r.width > r.height
        val pw = if (landscape) base.second else base.first
        val ph = if (landscape) base.first else base.second
        val margin = 22f
        val legendRows = (r.colors.size + 7) / 8
        val legendH = legendRows * 22f + 10
        fun page(n: Int, colored: Boolean) {
            val pg = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
            val c = pg.canvas
            val availW = pw - 2 * margin
            val availH = ph - 2 * margin - legendH - 14
            val s = min(availW / r.width, availH / r.height)
            val ox = margin + (availW - r.width * s) / 2
            val oy = margin
            if (colored) {
                val px = IntArray(r.cells.size) { r.colors[r.cells[it]].rgb or (0xFF shl 24) }
                val bmp = Bitmap.createBitmap(px, r.width, r.height, Bitmap.Config.ARGB_8888)
                c.drawBitmap(bmp, null, RectF(ox, oy, ox + r.width * s, oy + r.height * s), Paint(Paint.FILTER_BITMAP_FLAG))
                drawOutline(c, r, s, ox, oy, 0x55000000, 0.4f)
                drawNumbers(c, r, s, ox, oy, true, 3.5f, 9f)
            } else {
                drawOutline(c, r, s, ox, oy, 0xFFA0A0A0.toInt(), 0.45f)
                drawNumbers(c, r, s, ox, oy, false, 3.5f, 9f)
            }
            // Таблица карандашей: квадрат цвета с номером и название.
            val ly = oy + r.height * s + 14
            val cw = availW / 8
            val box = Paint(Paint.ANTI_ALIAS_FLAG)
            val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 6.5f; color = 0xFF333333.toInt() }
            val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
            r.colors.forEachIndexed { i, pc ->
                val x = margin + (i % 8) * cw; val y = ly + (i / 8) * 22f
                box.color = pc.rgb or (0xFF shl 24); box.style = Paint.Style.FILL
                c.drawRect(x, y, x + 22, y + 14, box)
                box.color = 0xFF999999.toInt(); box.style = Paint.Style.STROKE; box.strokeWidth = 0.4f
                c.drawRect(x, y, x + 22, y + 14, box)
                num.color = if (PaintByNumbers.dark(pc.rgb)) Color.WHITE else Color.BLACK
                c.drawText("${i + 1}", x + 11, y + 10.2f, num)
                c.drawText(pc.name.take(18), x + 25, y + 6, txt)
                c.drawText(if (pc.code.startsWith("К")) "свой" else "№ ${pc.code}", x + 25, y + 13, txt)
            }
            doc.finishPage(pg)
        }
        page(1, false)
        page(2, true)
        doc.writeTo(out)
        doc.close()
    }
}
