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
import android.media.ExifInterface
import android.net.Uri
import com.dasein.poryadok.logic.CraftPattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Работа в разделе «Рукоделие»: фото и настройки схемы. Лежит в files/crafts/<id>/ (фото + meta.json) и попадает в резервную копию. */
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
) {
    val kindEnum get() = runCatching { CraftPattern.Kind.valueOf(kind) }.getOrDefault(CraftPattern.Kind.DIAMOND)
}

object CraftStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun root(ctx: Context) = File(ctx.filesDir, "crafts").apply { mkdirs() }
    fun photo(ctx: Context, id: Long) = File(root(ctx), "p$id.jpg")
    private fun meta(ctx: Context, id: Long) = File(root(ctx), "p$id.json")

    fun list(ctx: Context): List<CraftProject> = root(ctx).listFiles().orEmpty().filter { it.name.endsWith(".json") }
        .mapNotNull { f -> runCatching { json.decodeFromString(CraftProject.serializer(), f.readText()) }.getOrNull() }
        .sortedByDescending { it.createdAt }

    fun load(ctx: Context, id: Long): CraftProject? = runCatching { json.decodeFromString(CraftProject.serializer(), meta(ctx, id).readText()) }.getOrNull()

    fun save(ctx: Context, p: CraftProject) = meta(ctx, p.id).writeText(json.encodeToString(CraftProject.serializer(), p))

    fun delete(ctx: Context, id: Long) { photo(ctx, id).delete(); meta(ctx, id).delete() }

    /** Копирует фото к себе (не больше 1600 px по длинной стороне, с учётом поворота из EXIF) и заводит работу. */
    suspend fun create(ctx: Context, uri: Uri): CraftProject = withContext(Dispatchers.IO) {
        val bmp = decode(ctx, uri, 1600) ?: error("Не удалось открыть фото")
        val id = System.currentTimeMillis()
        photo(ctx, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val p = CraftProject(id = id, name = "Схема ${java.text.SimpleDateFormat("d.MM HH:mm", java.util.Locale("ru")).format(java.util.Date(id))}", createdAt = id)
        save(ctx, p)
        p
    }

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

    /** Схема по настройкам работы. */
    suspend fun pattern(ctx: Context, p: CraftProject): CraftPattern.Pattern? = withContext(Dispatchers.Default) {
        val bmp = BitmapFactory.decodeFile(photo(ctx, p.id).absolutePath) ?: return@withContext null
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        val w = p.width.coerceIn(10, 250)
        val h = CraftPattern.heightFor(w, bmp.width, bmp.height).coerceIn(5, 350)
        val small = CraftPattern.downscale(px, bmp.width, bmp.height, w, h)
        val pat = CraftPattern.build(small, w, h, p.colors, p.dither)
        if (p.cleanup) CraftPattern.cleanup(pat) else pat
    }

    /** Картинка «как будет выглядеть»: стразы — кружки с бликом, вышивка — крестики, бисер — бусины в шахматном порядке. */
    fun preview(p: CraftPattern.Pattern, kind: CraftPattern.Kind, cell: Int): Bitmap {
        val bmp = Bitmap.createBitmap(p.width * cell, p.height * cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(if (kind == CraftPattern.Kind.CROSS) 0xFFF4EFE4.toInt() else 0xFF2A2621.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
        for (y in 0 until p.height) for (x in 0 until p.width) {
            val rgb = p.colors[p.cells[y * p.width + x]].rgb or (0xFF shl 24)
            paint.color = rgb
            val l = x * cell.toFloat(); val t = y * cell.toFloat()
            when (kind) {
                CraftPattern.Kind.DIAMOND -> {
                    c.drawRoundRect(RectF(l + .5f, t + .5f, l + cell - .5f, t + cell - .5f), cell * .25f, cell * .25f, paint)
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

    /**
     * Печатная схема: цветные клетки с символами, сетка (жирная линия каждые 10 клеток), номера по краям
     * и легенда — символ, номер DMC, название, сколько клеток и материала.
     */
    fun chart(p: CraftPattern.Pattern, kind: CraftPattern.Kind, count: Int, title: String): Bitmap {
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
        val fill = Paint(); val sym = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = cell * .68f; typeface = Typeface.DEFAULT_BOLD }
        val thin = Paint().apply { color = 0x55000000; strokeWidth = 1f }
        val thick = Paint().apply { color = 0xFF000000.toInt(); strokeWidth = 2.5f }
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt(); textSize = cell * .7f; textAlign = Paint.Align.CENTER }
        val ox = margin.toFloat(); val oy = margin.toFloat()
        for (y in 0 until p.height) for (x in 0 until p.width) {
            val i = p.cells[y * p.width + x]
            val rgb = p.colors[i].rgb or (0xFF shl 24)
            fill.color = rgb
            val l = ox + x * cell; val t = oy + y * cell
            c.drawRect(l, t, l + cell, t + cell, fill)
            sym.color = if (CraftPattern.symbolDark(p.colors[i].rgb)) Color.BLACK else Color.WHITE
            c.drawText(CraftPattern.SYMBOLS[i % CraftPattern.SYMBOLS.size], l + cell / 2f, t + cell * .74f, sym)
        }
        for (x in 0..p.width) c.drawLine(ox + x * cell, oy, ox + x * cell, oy + gridH, if (x % 10 == 0) thick else thin)
        for (y in 0..p.height) c.drawLine(ox, oy + y * cell, ox + gridW, oy + y * cell, if (y % 10 == 0) thick else thin)
        for (x in 10..p.width step 10) { c.drawText("$x", ox + x * cell, oy - cell * .4f, num); c.drawText("$x", ox + x * cell, oy + gridH + cell * 1.1f, num) }
        num.textAlign = Paint.Align.RIGHT
        for (y in 10..p.height step 10) c.drawText("$y", ox - cell * .3f, oy + y * cell + cell * .25f, num)
        // Легенда
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 26f }
        val head = Paint(txt).apply { textSize = 34f; typeface = Typeface.DEFAULT_BOLD }
        var ly = oy + gridH + margin + 30f
        val sizeW = CraftPattern.sizeCm(kind, p.width, count); val sizeH = CraftPattern.sizeCm(kind, p.height, count)
        c.drawText("$title · ${kind.title} · ${p.width}×${p.height} (${"%.1f".format(sizeW)}×${"%.1f".format(sizeH)} см) · цветов ${p.colors.size}", ox, ly, head)
        ly += 50
        val counts = p.counts()
        val colW = (W - ox * 2) / cols
        p.colors.forEachIndexed { i, t ->
            val cx = ox + (i / legendRows) * colW; val cy = ly + (i % legendRows) * rowH
            fill.color = t.rgb or (0xFF shl 24)
            c.drawRect(cx, cy - 24, cx + 30, cy + 4, fill)
            sym.textSize = 22f; sym.color = if (CraftPattern.symbolDark(t.rgb)) Color.BLACK else Color.WHITE
            c.drawText(CraftPattern.SYMBOLS[i % CraftPattern.SYMBOLS.size], cx + 15, cy - 3, sym)
            val n = CraftPattern.need(kind, counts[i], count)
            c.drawText("DMC ${t.code}  ${t.name} — ${counts[i]} ${kind.unit}, ${fmt(n.amount)} ${n.unit}", cx + 42, cy, txt)
        }
        return bmp
    }

    fun fmt(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(v).replace('.', ',')
}
