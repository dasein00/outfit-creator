package com.dasein.poryadok.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import com.dasein.poryadok.logic.CraftPattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream

/** Работа в разделе «Рукоделие»: фото и настройки схемы. Лежит в files/crafts/ (фото, исходник, meta.json, отметки) и попадает в резервную копию. */
@Serializable
data class CraftProject(
    val id: Long,
    val name: String,
    val kind: String = CraftPattern.Kind.DIAMOND.name,
    /** Ширина в клетках (стразах, крестиках, бусинах). */
    val width: Int = 60,
    val colors: Int = 24,
    val dither: Boolean = false,
    val cleanup: Boolean = true,
    /** Канва для вышивки: крестиков на дюйм (Аида 11, 14, 16, 18). */
    val count: Int = 14,
    /** Отмеченные готовыми цвета (номера DMC). */
    val done: List<String> = emptyList(),
    val createdAt: Long = 0,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    /** Не выкладывать фон (клетки, связанные с краем и похожие на цвет рамки). */
    val removeBg: Boolean = false,
    /** Круглые стразы 2,8 мм вместо квадратных 2,5 мм. */
    val round: Boolean = false,
    /** Подбирать только из цветов «Мои запасы». */
    val onlyStash: Boolean = false,
    /** Замены цветов: номер DMC → номер DMC. */
    val replace: Map<String, String> = emptyMap(),
    /** Вид схемы: 0 — как на холсте (светлый оттенок + символ), 1 — цветная, 2 — только символы (ч/б печать). */
    val chartStyle: Int = 0,
    /** Подпись схемы, к которой относятся отметки клеток. */
    val progressSig: String = "",
) {
    val kindEnum get() = runCatching { CraftPattern.Kind.valueOf(kind) }.getOrDefault(CraftPattern.Kind.DIAMOND)
}

object CraftStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun root(ctx: Context) = File(ctx.filesDir, "crafts").apply { mkdirs() }
    fun photo(ctx: Context, id: Long) = File(root(ctx), "p$id.jpg")
    /** Исходное фото без обрезки — чтобы кадр можно было поменять. */
    fun original(ctx: Context, id: Long) = File(root(ctx), "p${id}_orig.jpg")
    private fun meta(ctx: Context, id: Long) = File(root(ctx), "p$id.json")
    private fun cellsFile(ctx: Context, id: Long) = File(root(ctx), "p$id.cells")

    fun list(ctx: Context): List<CraftProject> = root(ctx).listFiles().orEmpty().filter { it.name.endsWith(".json") }
        .mapNotNull { f -> runCatching { json.decodeFromString(CraftProject.serializer(), f.readText()) }.getOrNull() }
        .sortedByDescending { it.createdAt }

    fun load(ctx: Context, id: Long): CraftProject? = runCatching { json.decodeFromString(CraftProject.serializer(), meta(ctx, id).readText()) }.getOrNull()

    fun save(ctx: Context, p: CraftProject) = meta(ctx, p.id).writeText(json.encodeToString(CraftProject.serializer(), p))

    fun delete(ctx: Context, id: Long) { listOf(photo(ctx, id), original(ctx, id), meta(ctx, id), cellsFile(ctx, id)).forEach { it.delete() } }

    /** Копирует фото к себе (не больше 2000 px, с учётом поворота из EXIF) и заводит работу. */
    suspend fun create(ctx: Context, uri: Uri): CraftProject = withContext(Dispatchers.IO) {
        val bmp = decode(ctx, uri, 2000) ?: error("Не удалось открыть фото")
        val id = System.currentTimeMillis()
        original(ctx, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        original(ctx, id).copyTo(photo(ctx, id), overwrite = true)
        val p = CraftProject(id = id, name = "Схема ${java.text.SimpleDateFormat("d.MM HH:mm", java.util.Locale("ru")).format(java.util.Date(id))}", createdAt = id)
        save(ctx, p)
        p
    }

    /** Для старых работ без исходника — исходником становится текущее фото. */
    fun originalPath(ctx: Context, id: Long): String {
        val o = original(ctx, id)
        if (!o.exists()) photo(ctx, id).copyTo(o, overwrite = true)
        return o.absolutePath
    }

    /** Кадр выбран в CropDialog: файл [cropped] становится фото работы, лишние копии удаляются. */
    fun applyCrop(ctx: Context, id: Long, cropped: String) {
        val f = File(cropped)
        f.copyTo(photo(ctx, id), overwrite = true)
        f.delete()
        File(com.dasein.poryadok.ui.common.Crop.originalOf(cropped)).delete()
    }

    fun resetCrop(ctx: Context, id: Long) { val o = original(ctx, id); if (o.exists()) o.copyTo(photo(ctx, id), overwrite = true) }

    private fun decode(ctx: Context, uri: Uri, max: Int): Bitmap? {
        val cr = ctx.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) return null
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return null
        val rot = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        var b = raw
        val k = max.toFloat() / maxOf(b.width, b.height)
        if (k < 1f) b = Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
        if (rot != 0f) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot) }, true)
        return b
    }

    // ---------- Мои запасы ----------

    private fun sp(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
    fun stash(ctx: Context): Set<String> = sp(ctx).getString("craft_stash", "").orEmpty().split(',').filter { it.isNotBlank() }.toSet()
    fun setStash(ctx: Context, codes: Set<String>) = sp(ctx).edit().putString("craft_stash", codes.joinToString(",")).apply()

    // ---------- Схема ----------

    /** Фото с поправками, уменьшенное до сетки (для превью «до/после» и расчёта). */
    private fun grid(ctx: Context, p: CraftProject): Triple<IntArray, Int, Int>? {
        val bmp = BitmapFactory.decodeFile(photo(ctx, p.id).absolutePath) ?: return null
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        val w = p.width.coerceIn(10, 250)
        val h = CraftPattern.heightFor(w, bmp.width, bmp.height).coerceIn(5, 350)
        val adj = CraftPattern.adjust(px, p.brightness, p.contrast, p.saturation)
        return Triple(CraftPattern.downscale(adj, bmp.width, bmp.height, w, h), w, h)
    }

    /** Схема по настройкам работы. */
    suspend fun pattern(ctx: Context, p: CraftProject): CraftPattern.Pattern? = withContext(Dispatchers.Default) {
        val (small, w, h) = grid(ctx, p) ?: return@withContext null
        val allowed = if (p.onlyStash) stash(ctx).let { s -> CraftPattern.DMC.indices.filter { CraftPattern.DMC[it].code in s }.toSet() } else null
        val skip = if (p.removeBg) CraftPattern.backgroundMask(small, w, h) else null
        val pat = CraftPattern.build(small, w, h, p.colors, p.dither, allowed = allowed, skip = skip)
        CraftPattern.replace(if (p.cleanup) CraftPattern.cleanup(pat) else pat, p.replace)
    }

    // ---------- Отметки выложенных клеток ----------

    fun loadDone(ctx: Context, p: CraftProject, pat: CraftPattern.Pattern): BooleanArray {
        val f = cellsFile(ctx, p.id)
        if (p.progressSig != pat.signature() || !f.exists()) return BooleanArray(pat.cells.size)
        val bytes = f.readBytes()
        return BooleanArray(pat.cells.size) { i -> (bytes.getOrNull(i / 8)?.toInt() ?: 0) shr (i % 8) and 1 == 1 }
    }

    fun saveDone(ctx: Context, p: CraftProject, pat: CraftPattern.Pattern, done: BooleanArray) {
        val bytes = ByteArray((done.size + 7) / 8)
        done.forEachIndexed { i, on -> if (on) bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte() }
        cellsFile(ctx, p.id).writeBytes(bytes)
        if (p.progressSig != pat.signature()) save(ctx, p.copy(progressSig = pat.signature()))
    }

    /** Есть ли отметки, которые сбросятся при изменении схемы. */
    fun hasProgress(ctx: Context, p: CraftProject): Boolean = p.progressSig.isNotEmpty() && cellsFile(ctx, p.id).let { f -> f.exists() && f.readBytes().any { it.toInt() != 0 } }

    // ---------- Картинки ----------

    private const val CANVAS = 0xFFF4EFE4.toInt()

    /** Светлый оттенок цвета — как печатают на холсте наборов. */
    fun tint(rgb: Int, k: Float = .45f): Int {
        val r = (rgb shr 16 and 255); val g = (rgb shr 8 and 255); val b = (rgb and 255)
        fun m(c: Int) = (c * k + 255 * (1 - k)).toInt()
        return (0xFF shl 24) or (m(r) shl 16) or (m(g) shl 8) or m(b)
    }

    /** Картинка «как будет выглядеть»: стразы — с бликом, вышивка — крестики, бисер — бусины в шахматном порядке. */
    fun preview(p: CraftPattern.Pattern, kind: CraftPattern.Kind, cell: Int, round: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(p.width * cell, p.height * cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(if (kind == CraftPattern.Kind.DIAMOND) 0xFF2A2621.toInt() else CANVAS)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
        val empty = Paint().apply { color = CANVAS }
        for (y in 0 until p.height) for (x in 0 until p.width) {
            val idx = p.cells[y * p.width + x]
            val l = x * cell.toFloat(); val t = y * cell.toFloat()
            if (idx < 0) { c.drawRect(l, t, l + cell, t + cell, empty); continue }
            paint.color = p.colors[idx].rgb or (0xFF shl 24)
            when (kind) {
                CraftPattern.Kind.DIAMOND -> {
                    if (round) c.drawCircle(l + cell / 2f, t + cell / 2f, cell * .47f, paint)
                    else c.drawRoundRect(RectF(l + .5f, t + .5f, l + cell - .5f, t + cell - .5f), cell * .25f, cell * .25f, paint)
                    if (cell >= 6) c.drawCircle(l + cell * .32f, t + cell * .32f, cell * .14f, shine)
                }
                CraftPattern.Kind.CROSS -> {
                    paint.strokeWidth = cell * .28f; paint.strokeCap = Paint.Cap.ROUND
                    val m = cell * .18f
                    c.drawLine(l + m, t + m, l + cell - m, t + cell - m, paint)
                    c.drawLine(l + cell - m, t + m, l + m, t + cell - m, paint)
                }
                CraftPattern.Kind.BEADS -> {
                    val off = if (y % 2 == 1) cell * .5f else 0f
                    c.drawOval(RectF(l + off + .5f, t + cell * .08f, l + off + cell - .5f, t + cell * .92f), paint)
                    if (cell >= 6) c.drawCircle(l + off + cell * .35f, t + cell * .35f, cell * .12f, shine)
                }
            }
        }
        return bmp
    }

    /** Клетка схемы: фон по стилю и символ цвета. */
    fun drawCell(c: Canvas, l: Float, t: Float, cell: Float, idx: Int, p: CraftPattern.Pattern, style: Int, fill: Paint, sym: Paint) {
        if (idx < 0) { fill.color = Color.WHITE; c.drawRect(l, t, l + cell, t + cell, fill); return }
        val rgb = p.colors[idx].rgb
        val bg = when (style) { 1 -> rgb or (0xFF shl 24); 2 -> Color.WHITE; else -> tint(rgb) }
        fill.color = bg
        c.drawRect(l, t, l + cell, t + cell, fill)
        sym.color = if (style == 1 && !CraftPattern.symbolDark(rgb)) Color.WHITE else Color.BLACK
        sym.textSize = cell * .7f
        c.drawText(CraftPattern.symbol(idx), l + cell / 2f, t + cell * .75f, sym)
    }

    /** Схема с символами для экрана: клетки в выбранном виде, символы, сетка через 10 (размер ограничен, чтобы хватило памяти). */
    fun symbolChart(p: CraftPattern.Pattern, style: Int): Bitmap {
        val cell = (2400 / maxOf(p.width, p.height)).coerceIn(8, 28)
        val bmp = Bitmap.createBitmap(p.width * cell, p.height * cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val fill = Paint(); val sym = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        for (y in 0 until p.height) for (x in 0 until p.width) drawCell(c, x * cell.toFloat(), y * cell.toFloat(), cell.toFloat(), p.cells[y * p.width + x], p, style, fill, sym)
        gridLines(c, 0f, 0f, cell.toFloat(), 0, 0, p.width, p.height)
        return bmp
    }

    private fun gridLines(c: Canvas, ox: Float, oy: Float, cell: Float, x0: Int, y0: Int, cols: Int, rows: Int) {
        val thin = Paint().apply { color = 0x66000000; strokeWidth = cell * .04f }
        val thick = Paint().apply { color = 0xFF000000.toInt(); strokeWidth = cell * .1f }
        for (x in 0..cols) c.drawLine(ox + x * cell, oy, ox + x * cell, oy + rows * cell, if ((x0 + x) % 10 == 0) thick else thin)
        for (y in 0..rows) c.drawLine(ox, oy + y * cell, ox + cols * cell, oy + y * cell, if ((y0 + y) % 10 == 0) thick else thin)
    }

    /** Печатная схема одним PNG: символы, сетка через 10, номера по краям и легенда. */
    fun chart(p: CraftPattern.Pattern, pr: CraftProject): Bitmap {
        val kind = pr.kindEnum
        val cell = (3000 / maxOf(p.width, p.height)).coerceIn(12, 30)
        val margin = cell * 2
        val gridW = p.width * cell; val gridH = p.height * cell
        val rowH = 34
        val cols = if (p.colors.size > 20) 2 else 1
        val legendRows = (p.colors.size + cols - 1) / cols
        val legendH = 120 + legendRows * rowH
        val W = maxOf(gridW + margin * 2, 1100); val H = gridH + margin * 2 + legendH
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val fill = Paint(); val sym = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        val ox = margin.toFloat(); val oy = margin.toFloat()
        for (y in 0 until p.height) for (x in 0 until p.width) drawCell(c, ox + x * cell, oy + y * cell, cell.toFloat(), p.cells[y * p.width + x], p, pr.chartStyle, fill, sym)
        gridLines(c, ox, oy, cell.toFloat(), 0, 0, p.width, p.height)
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt(); textSize = cell * .7f; textAlign = Paint.Align.CENTER }
        for (x in 10..p.width step 10) { c.drawText("$x", ox + x * cell, oy - cell * .4f, num); c.drawText("$x", ox + x * cell, oy + gridH + cell * 1.1f, num) }
        num.textAlign = Paint.Align.RIGHT
        for (y in 10..p.height step 10) c.drawText("$y", ox - cell * .3f, oy + y * cell + cell * .25f, num)
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 26f }
        val head = Paint(txt).apply { textSize = 34f; typeface = Typeface.DEFAULT_BOLD }
        var ly = oy + gridH + margin + 30f
        c.drawText(title(p, pr), ox, ly, head)
        ly += 50
        val counts = p.counts()
        val colW = (W - ox * 2) / cols
        p.colors.forEachIndexed { i, t ->
            val cx = ox + (i / legendRows) * colW; val cy = ly + (i % legendRows) * rowH
            drawCell(c, cx, cy - 26, 30f, i, p, pr.chartStyle, fill, sym)
            val n = CraftPattern.need(kind, counts[i], pr.count)
            c.drawText("DMC ${t.code}  ${t.name} — ${counts[i]} ${kind.unit}, ${fmt(n.amount)} ${n.unit}", cx + 42, cy, txt)
        }
        return bmp
    }

    fun title(p: CraftPattern.Pattern, pr: CraftProject): String {
        val k = pr.kindEnum
        val w = CraftPattern.sizeCm(k, p.width, pr.count, pr.round); val h = CraftPattern.sizeCm(k, p.height, pr.count, pr.round)
        return "${pr.name} · ${k.title} · ${p.width}×${p.height} (${"%.1f".format(w)}×${"%.1f".format(h)} см) · цветов ${p.colors.size}"
    }

    /** Форматы бумаги, мм (ширина × высота в книжной ориентации). */
    val PAPERS = listOf("A4" to (210 to 297), "A3" to (297 to 420), "A2" to (420 to 594), "A1" to (594 to 841), "A0" to (841 to 1189))

    /** Миллиметры → точки PDF. */
    private fun pt(mm: Double) = (mm * 72 / 25.4).toFloat()

    /**
     * PDF для печати на бумаге [paperW]×[paperH] мм.
     *  - [realSize] = false — «схема по листам»: клетка 3,2 мм, обложка с картинкой и таблицей цветов, дальше листы с номерами рядов и столбцов;
     *  - [realSize] = true — «холст в натуральную величину»: клетка ровно размером страза/крестика/бусины, чтобы распечатать и выкладывать прямо поверх
     *    (если холст больше листа — делится на части с номерами частей и метками совмещения).
     */
    suspend fun pdf(ctx: Context, p: CraftPattern.Pattern, pr: CraftProject, out: OutputStream, paperW: Int = 210, paperH: Int = 297, realSize: Boolean = false) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val pw = pt(paperW.toDouble()).toInt(); val ph = pt(paperH.toDouble()).toInt()
        val m = pt(8.0)
        val kind = pr.kindEnum
        val counts = p.counts()
        val scale = (pw / 595f).coerceIn(1f, 4f)
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f * scale }
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f * scale; typeface = Typeface.DEFAULT_BOLD }
        val fill = Paint(); val sym = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        var pageNo = 1
        val cell = if (realSize) pt(CraftPattern.sizeCm(kind, 1, pr.count, pr.round) * 10) else pt(3.2)
        val top = m + 14 * scale + 8
        val perX = ((pw - 2 * m - 16 * scale) / cell).toInt().coerceAtLeast(1)
        val perY = ((ph - top - m - 6 * scale) / cell).toInt().coerceAtLeast(1)
        val sheetsX = (p.width + perX - 1) / perX; val sheetsY = (p.height + perY - 1) / perY
        // 1. Обложка и таблица цветов.
        val rowH = 14f * scale
        val firstRows = ((ph - m - (m + 340 * scale)) / rowH).toInt().coerceAtLeast(5)
        val nextRows = ((ph - 2 * m - 30 * scale) / rowH).toInt().coerceAtLeast(5)
        var i = 0; var first = true
        while (first || i < p.colors.size) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo++).create())
            val c = page.canvas
            var y = m + 14 * scale
            if (first) {
                c.drawText(title(p, pr), m, y, head); y += 10 * scale
                val prev = preview(p, kind, maxOf(1, (600 / maxOf(p.width, p.height))), pr.round)
                val k = minOf((pw - 2 * m) / prev.width, 300f * scale / prev.height)
                c.drawBitmap(prev, null, RectF(m, y, m + prev.width * k, y + prev.height * k), Paint(Paint.FILTER_BITMAP_FLAG))
                prev.recycle()
                y += 300 * scale + 14 * scale
                c.drawText(
                    (if (realSize) "Натуральная величина: клетка ${fmt(CraftPattern.sizeCm(kind, 1, pr.count, pr.round) * 10)} мм" else "Схема по листам") +
                        " · бумага ${paperW}×${paperH} мм · листов схемы: ${sheetsX * sheetsY} ($sheetsX × $sheetsY)", m, y, txt,
                )
                y += rowH * 1.5f
            }
            val rows = if (first) firstRows else nextRows
            val headTxt = Paint(txt).apply { typeface = Typeface.DEFAULT_BOLD }
            c.drawText("Символ · номер DMC · название · клеток · сколько купить", m, y, headTxt); y += rowH
            repeat(rows) {
                if (i >= p.colors.size) return@repeat
                val t = p.colors[i]
                drawCell(c, m, y - 10 * scale, 12f * scale, i, p, pr.chartStyle, fill, sym)
                val n = CraftPattern.need(kind, counts[i], pr.count)
                c.drawText("DMC ${t.code}   ${t.name}   ${counts[i]}   ${fmt(n.amount)} ${n.unit}", m + 20 * scale, y, txt)
                y += rowH; i++
            }
            doc.finishPage(page)
            first = false
        }
        // 2. Схема по листам.
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt(); textSize = maxOf(6f, cell * .6f).coerceAtMost(10f * scale); textAlign = Paint.Align.CENTER }
        val mark = Paint().apply { color = Color.BLACK; strokeWidth = 0.6f }
        for (sy in 0 until sheetsY) for (sx in 0 until sheetsX) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo++).create())
            val c = page.canvas
            val x0 = sx * perX; val y0 = sy * perY
            val cols = minOf(perX, p.width - x0); val rows = minOf(perY, p.height - y0)
            c.drawText("${pr.name} · часть ${sy * sheetsX + sx + 1} из ${sheetsX * sheetsY} (ряд листов ${sy + 1}, колонка ${sx + 1}) · столбцы ${x0 + 1}–${x0 + cols}, ряды ${y0 + 1}–${y0 + rows}", m, m + 10 * scale, txt)
            val ox = m + 14 * scale; val oy = top
            for (y in 0 until rows) for (x in 0 until cols) drawCell(c, ox + x * cell, oy + y * cell, cell, p.cells[(y0 + y) * p.width + x0 + x], p, pr.chartStyle, fill, sym)
            gridLines(c, ox, oy, cell, x0, y0, cols, rows)
            for (x in 0 until cols) if ((x0 + x + 1) % 10 == 0) c.drawText("${x0 + x + 1}", ox + (x + .5f) * cell, oy - 2, num)
            num.textAlign = Paint.Align.RIGHT
            for (y in 0 until rows) if ((y0 + y + 1) % 10 == 0) c.drawText("${y0 + y + 1}", ox - 2, oy + (y + .8f) * cell, num)
            num.textAlign = Paint.Align.CENTER
            // Метки совмещения по углам — чтобы склеить части холста встык.
            val r = ox + cols * cell; val b = oy + rows * cell; val k = 6f
            listOf(ox to oy, r to oy, ox to b, r to b).forEach { (x, y) -> c.drawLine(x - k, y, x + k, y, mark); c.drawLine(x, y - k, x, y + k, mark) }
            doc.finishPage(page)
        }
        doc.writeTo(out)
        doc.close()
    }

    fun fmt(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(v).replace('.', ',')
}
