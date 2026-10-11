package com.dasein.poryadok.system

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.dasein.poryadok.logic.IconSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Свои наборы иконок (нарезанные из картинки) и замены иконок: любую иконку приложения можно заменить
 * другой из базы — долгим нажатием на неё. Лежит в files/iconsets — попадает в резервную копию.
 */
object IconSets {
    @Serializable
    data class IconSet(val id: String, val title: String)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    /** Меняется при добавлении наборов и замене иконок — иконки на экране перерисовываются. */
    val version = mutableIntStateOf(0)

    private fun root(ctx: Context) = File(ctx.filesDir, "iconsets").apply { mkdirs() }
    private fun index(ctx: Context) = File(root(ctx), "index.json")
    private fun overridesFile(ctx: Context) = File(root(ctx), "overrides.json")

    fun sets(ctx: Context): List<IconSet> = runCatching { json.decodeFromString(ListSerializer(IconSet.serializer()), index(ctx).readText()) }.getOrDefault(emptyList())

    fun keysOf(ctx: Context, id: String): List<String> =
        File(root(ctx), id).listFiles().orEmpty().filter { it.extension == "webp" && !it.name.endsWith(".orig.webp") }.map { "u:$id/" + it.nameWithoutExtension }.sorted()

    /** Файл иконки по ключу «u:набор/номер». */
    fun file(ctx: Context, key: String): File = File(root(ctx), key.removePrefix("u:") + ".webp")

    fun deleteSet(ctx: Context, id: String) {
        File(root(ctx), id).deleteRecursively()
        writeIndex(ctx, sets(ctx).filter { it.id != id })
        version.intValue++
    }

    private fun writeIndex(ctx: Context, l: List<IconSet>) = index(ctx).writeText(json.encodeToString(ListSerializer(IconSet.serializer()), l))

    // ---------- Нарезка ----------

    /** Картинка-набор → отдельные иконки 192×192 с прозрачным фоном, в порядке чтения. */
    suspend fun slice(ctx: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.Default) {
        val src = CraftStore.decode(ctx, uri, 1800) ?: return@withContext emptyList()
        val b = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        val w = b.width; val h = b.height
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        val bg = IconSheet.background(px, w, h)
        IconSheet.slice(px, w, h).take(80).map { box ->
            val (cut, side) = IconSheet.cut(px, w, h, box, bg)
            Bitmap.createScaledBitmap(Bitmap.createBitmap(cut, side, side, Bitmap.Config.ARGB_8888), 192, 192, true)
        }
    }

    /** Сохраняет выбранные иконки новым набором. Возвращает id набора. */
    suspend fun save(ctx: Context, title: String, icons: List<Bitmap>): String = withContext(Dispatchers.IO) {
        val id = "s" + System.currentTimeMillis()
        val dir = File(root(ctx), id).apply { mkdirs() }
        icons.forEachIndexed { i, bmp ->
            File(dir, "%02d.webp".format(i)).outputStream().use {
                @Suppress("DEPRECATION")
                bmp.compress(if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP, 100, it)
            }
        }
        writeIndex(ctx, sets(ctx) + IconSet(id, title.ifBlank { "Мой набор ${sets(ctx).size + 1}" }))
        version.intValue++
        id
    }

    // ---------- Правка своих иконок ----------

    /** Правка иконки: отражение, поворот (градусы), наклон (−0,6…0,6), масштаб. Всегда считается от исходной картинки. */
    @Serializable
    data class Edit(
        val flipH: Boolean = false, val flipV: Boolean = false, val angle: Float = 0f,
        val skewX: Float = 0f, val skewY: Float = 0f, val scale: Float = 1f,
    ) {
        val isIdentity get() = this == Edit()
    }

    private fun origFile(ctx: Context, key: String) = File(file(ctx, key).path.removeSuffix(".webp") + ".orig.webp")
    private fun editFile(ctx: Context, key: String) = File(file(ctx, key).path.removeSuffix(".webp") + ".edit.json")

    fun edit(ctx: Context, key: String): Edit = runCatching { json.decodeFromString(Edit.serializer(), editFile(ctx, key).readText()) }.getOrDefault(Edit())

    /** Исходная картинка (до всех правок). */
    fun original(ctx: Context, key: String): Bitmap? {
        val o = origFile(ctx, key)
        return android.graphics.BitmapFactory.decodeFile((if (o.exists()) o else file(ctx, key)).absolutePath)
    }

    /** Иконка 192×192 с правкой [e]. */
    fun render(src: Bitmap, e: Edit): Bitmap {
        val out = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val fit = 192f / maxOf(src.width, src.height)
        val m = android.graphics.Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postScale(fit * e.scale * (if (e.flipH) -1 else 1), fit * e.scale * (if (e.flipV) -1 else 1))
            postSkew(e.skewX, e.skewY)
            postRotate(e.angle)
            postTranslate(96f, 96f)
        }
        c.drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Сохранить правку: исходник откладывается один раз, иконка перерисовывается от него. */
    suspend fun saveEdit(ctx: Context, key: String, e: Edit) = withContext(Dispatchers.IO) {
        val f = file(ctx, key); val o = origFile(ctx, key)
        if (!o.exists()) f.copyTo(o)
        val src = android.graphics.BitmapFactory.decodeFile(o.absolutePath) ?: return@withContext
        writeWebp(f, render(src, e))
        if (e.isIdentity) editFile(ctx, key).delete() else editFile(ctx, key).writeText(json.encodeToString(Edit.serializer(), e))
        changed()
    }

    /** Вернуть иконку как было до правок. */
    suspend fun revert(ctx: Context, key: String) = withContext(Dispatchers.IO) {
        val f = file(ctx, key); val o = origFile(ctx, key)
        if (o.exists()) { o.copyTo(f, overwrite = true); o.delete() }
        editFile(ctx, key).delete()
        changed()
    }

    fun deleteIcon(ctx: Context, key: String) {
        listOf(file(ctx, key), origFile(ctx, key), editFile(ctx, key)).forEach { it.delete() }
        changed()
    }

    private fun writeWebp(f: File, bmp: Bitmap) = f.outputStream().use {
        @Suppress("DEPRECATION")
        bmp.compress(if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP, 100, it)
    }

    /** Картинки поменялись — сбрасываем кэши, иконки перерисуются. */
    private fun changed() {
        com.dasein.poryadok.ui.common.Glyphs.evictAll()
        com.dasein.poryadok.ui.common.IconTint.evictAll()
        version.intValue++
    }

    // ---------- Замены иконок ----------

    private var cache: Map<String, String>? = null
    val overrides = mutableStateOf<Map<String, String>>(emptyMap())

    fun load(ctx: Context) {
        if (cache != null) return
        cache = runCatching { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), overridesFile(ctx).readText()) }.getOrDefault(emptyMap())
        overrides.value = cache!!
    }

    fun resolve(key: String): String = overrides.value[key] ?: key

    fun setOverride(ctx: Context, key: String, to: String?) {
        load(ctx)
        val m = overrides.value.toMutableMap()
        if (to == null || to == key) m.remove(key) else m[key] = to
        overrides.value = m; cache = m
        runCatching { overridesFile(ctx).writeText(json.encodeToString(MapSerializer(String.serializer(), String.serializer()), m)) }
        version.intValue++
    }
}
