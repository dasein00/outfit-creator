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
        File(root(ctx), id).listFiles().orEmpty().filter { it.extension == "webp" }.map { "u:$id/" + it.nameWithoutExtension }.sorted()

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
