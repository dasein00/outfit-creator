package com.dasein.poryadok.system

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.dasein.poryadok.logic.IconSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
/** Цвет оформления для иконок виджета (виджет рисуется вне экрана приложения). */
fun accentOf(ctx: Context): Int? =
    ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE).getInt("accent_rgb", -1).takeIf { it >= 0 }

object IconSets {
    /** Запомнить цвет оформления для виджета. */
    fun rememberAccent(ctx: Context, rgb: Int) {
        val sp = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
        if (sp.getInt("accent_rgb", -1) != rgb) sp.edit().putInt("accent_rgb", rgb and 0xFFFFFF).apply()
    }

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

    /** Файл иконки по ключу «u:набор/номер»; для встроенной — файл её правки (files/iconsets/edited). */
    fun file(ctx: Context, key: String): File =
        if (key.startsWith("u:")) File(root(ctx), key.removePrefix("u:") + ".webp")
        else File(root(ctx), "edited/" + key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".webp")

    /** Встроенная иконка (из приложения), а не своя. */
    fun isBuiltin(key: String) = !key.startsWith("u:") && !key.startsWith("/")

    /** Иконку можно править: свою из набора или любую встроенную. Фото из галереи — нет. */
    fun canEdit(key: String) = !key.startsWith("/")

    private val editedCache = HashMap<String, Boolean>()

    /** Встроенная иконка изменена (отражена, повёрнута…) — рисуется из файла правки. */
    fun isEdited(ctx: Context, key: String): Boolean =
        isBuiltin(key) && synchronized(editedCache) { editedCache.getOrPut(key) { file(ctx, key).exists() } }

    /** Встроенная картинка как в приложении, без правок. */
    fun builtinSource(ctx: Context, key: String): Bitmap? = runCatching {
        if (key.startsWith("ui:")) android.graphics.BitmapFactory.decodeResource(ctx.resources, com.dasein.poryadok.ui.common.Ic.byName(key.removePrefix("ui:")))
        else ctx.assets.open(com.dasein.poryadok.ui.common.Glyphs.assetPath(key)).use { android.graphics.BitmapFactory.decodeStream(it) }
    }.getOrNull()

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

    /**
     * Сохраняет выбранные иконки. Они попадают в папку [folderId] (по умолчанию «Мои иконки»);
     * если задан [newFolder] — в новую папку с этим названием. Возвращает id набора файлов.
     */
    suspend fun save(ctx: Context, title: String, icons: List<Bitmap>, folderId: String = MINE, newFolder: String? = null): String = withContext(Dispatchers.IO) {
        val id = "s" + System.currentTimeMillis()
        val dir = File(root(ctx), id).apply { mkdirs() }
        icons.forEachIndexed { i, bmp ->
            File(dir, "%02d.webp".format(i)).outputStream().use {
                @Suppress("DEPRECATION")
                bmp.compress(if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP, 100, it)
            }
        }
        writeIndex(ctx, sets(ctx) + IconSet(id, title.ifBlank { "Мой набор ${sets(ctx).size + 1}" }))
        val keys = keysOf(ctx, id)
        val target = newFolder?.takeIf { it.isNotBlank() }?.let { createFolder(ctx, it) } ?: folderId
        addKeys(ctx, target, keys)
        version.intValue++
        id
    }

    // ---------- Папки иконок ----------

    /** Папка иконок: свои иконки и любые встроенные, разложенные как удобно. Порядок ключей — порядок показа. */
    @Serializable
    data class Folder(val id: String, val title: String, val keys: List<String> = emptyList())

    /** «Мои иконки» — сюда попадает всё новое, удалить её нельзя. */
    const val MINE = "mine"

    private fun foldersFile(ctx: Context) = File(root(ctx), "folders.json")

    /** Свои картинки-иконки, загруженные из галереи (files/icons). */
    private fun ownImages(ctx: Context): List<String> = File(ctx.filesDir, "icons").listFiles().orEmpty()
        .filter { it.isFile && !it.name.contains("_orig") && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
        .sortedBy { it.lastModified() }.map { it.absolutePath }

    fun folders(ctx: Context): List<Folder> {
        val f = foldersFile(ctx)
        val list = if (f.exists()) runCatching { json.decodeFromString(ListSerializer(Folder.serializer()), f.readText()) }.getOrNull() else null
        if (list != null && list.any { it.id == MINE }) return list
        // Первый запуск: «Мои иконки» — картинки из галереи; прежние наборы — отдельными папками.
        val migrated = listOf(Folder(MINE, "Мои иконки", ownImages(ctx))) + sets(ctx).map { Folder(it.id, it.title, keysOf(ctx, it.id)) }
        writeFolders(ctx, migrated)
        return migrated
    }

    private fun writeFolders(ctx: Context, l: List<Folder>) =
        runCatching { foldersFile(ctx).writeText(json.encodeToString(ListSerializer(Folder.serializer()), l)) }

    fun saveFolders(ctx: Context, l: List<Folder>) { writeFolders(ctx, l); version.intValue++ }

    /** Что-то в библиотеке иконок поменялось — экраны перерисуются. */
    fun bump() { version.intValue++ }

    private fun update(ctx: Context, f: (List<Folder>) -> List<Folder>) = saveFolders(ctx, f(folders(ctx)))

    fun createFolder(ctx: Context, title: String): String {
        val id = "f" + System.currentTimeMillis()
        update(ctx) { it + Folder(id, title.ifBlank { "Новая папка" }) }
        return id
    }

    fun renameFolder(ctx: Context, id: String, title: String) = update(ctx) { l -> l.map { if (it.id == id) it.copy(title = title.ifBlank { it.title }) else it } }

    /** Удалить папку: иконки из неё переходят в «Мои иконки». */
    fun deleteFolder(ctx: Context, id: String) {
        if (id == MINE) return
        update(ctx) { l ->
            val moved = l.firstOrNull { it.id == id }?.keys.orEmpty()
            l.filter { it.id != id }.map { if (it.id == MINE) it.copy(keys = (it.keys + moved).distinct()) else it }
        }
    }

    fun moveFolder(ctx: Context, id: String, delta: Int) = update(ctx) { l ->
        val i = l.indexOfFirst { it.id == id }
        val j = (i + delta).coerceIn(0, l.lastIndex)
        if (i < 0 || i == j) l else l.toMutableList().also { m -> val x = m.removeAt(i); m.add(j, x) }
    }

    fun addKeys(ctx: Context, folderId: String, keys: List<String>) = update(ctx) { l ->
        l.map { if (it.id == folderId) it.copy(keys = (it.keys + keys).distinct()) else it }
    }

    /** Переложить иконки в другую папку (из всех остальных они убираются). */
    fun moveKeys(ctx: Context, keys: Collection<String>, to: String) = update(ctx) { l ->
        l.map { f -> if (f.id == to) f.copy(keys = (f.keys + keys).distinct()) else f.copy(keys = f.keys - keys.toSet()) }
    }

    /** Убрать из папки, не удаляя файл. Свои иконки, не оставшиеся ни в одной папке, возвращаются в «Мои иконки». */
    fun removeKeys(ctx: Context, folderId: String, keys: Collection<String>) = update(ctx) { l ->
        val after = l.map { if (it.id == folderId) it.copy(keys = it.keys - keys.toSet()) else it }
        val orphans = keys.filter { k -> (k.startsWith("u:") || k.startsWith("/")) && after.none { k in it.keys } }
        after.map { if (it.id == MINE) it.copy(keys = (it.keys + orphans).distinct()) else it }
    }

    /** Новый порядок иконок папки (после перетаскивания). Ключи не из папки добавляются. */
    fun setKeys(ctx: Context, folderId: String, keys: List<String>) = update(ctx) { l ->
        l.map { if (it.id == folderId) it.copy(keys = keys.distinct()) else it }
    }

    /**
     * «Мои иконки»: всё, что вы добавили сами, — иконки из папки «Мои иконки» в её порядке,
     * затем свои иконки из остальных папок и наборов и картинки из галереи.
     */
    fun mine(ctx: Context): List<String> {
        val fs = folders(ctx)
        val own = fs.flatMap { it.keys }.filter { !isBuiltin(it) }
        val loose = sets(ctx).flatMap { keysOf(ctx, it.id) } + ownImages(ctx)
        return (fs.firstOrNull { it.id == MINE }?.keys.orEmpty() + own + loose).distinct()
            .filter { k -> !k.startsWith("/") || File(k).exists() }
    }

    /** Сдвинуть иконку в папке на [delta] позиций (упорядочивание). */
    fun moveKey(ctx: Context, folderId: String, key: String, delta: Int) = update(ctx) { l ->
        l.map { f ->
            if (f.id != folderId) f else {
                val i = f.keys.indexOf(key); val j = (i + delta).coerceIn(0, f.keys.lastIndex)
                if (i < 0 || i == j) f else f.copy(keys = f.keys.toMutableList().also { m -> val x = m.removeAt(i); m.add(j, x) })
            }
        }
    }

    /** Удалить иконки совсем: свои файлы стираются, замены на них сбрасываются. Встроенные просто убираются из папок. */
    fun deleteKeys(ctx: Context, keys: Collection<String>) {
        keys.forEach { k ->
            when {
                k.startsWith("u:") -> listOf(file(ctx, k), origFile(ctx, k), editFile(ctx, k)).forEach { it.delete() }
                k.startsWith("/") && k.startsWith(File(ctx.filesDir, "icons").absolutePath) -> File(k).delete()
            }
            if (!isBuiltin(k)) overrides.value.filterValues { it == k }.keys.forEach { setOverride(ctx, it, null) }
        }
        update(ctx) { l -> l.map { it.copy(keys = it.keys - keys.toSet()) } }
        changed()
    }

    /** В какой папке иконка (для открытия нужной вкладки). */
    fun folderOf(ctx: Context, key: String): String? = folders(ctx).firstOrNull { key in it.keys }?.id

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
        if (isBuiltin(key)) return builtinSource(ctx, key)
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
        if (isBuiltin(key)) {
            // Встроенная: исходник всегда в приложении, сохраняем только результат правки.
            val f = file(ctx, key)
            if (e.isIdentity) { f.delete(); editFile(ctx, key).delete() } else {
                val src = builtinSource(ctx, key) ?: return@withContext
                f.parentFile?.mkdirs()
                writeWebp(f, render(src, e))
                editFile(ctx, key).writeText(json.encodeToString(Edit.serializer(), e))
            }
            changed(); return@withContext
        }
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
        if (isBuiltin(key)) f.delete()
        else if (o.exists()) { o.copyTo(f, overwrite = true); o.delete() }
        editFile(ctx, key).delete()
        changed()
    }

    fun deleteIcon(ctx: Context, key: String) = deleteKeys(ctx, listOf(key))

    private fun writeWebp(f: File, bmp: Bitmap) = f.outputStream().use {
        @Suppress("DEPRECATION")
        bmp.compress(if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP, 100, it)
    }

    /** Картинки поменялись — сбрасываем кэши, иконки перерисуются. */
    private fun changed() {
        synchronized(editedCache) { editedCache.clear() }
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

    /**
     * Картинка иконки для виджета (вне экрана приложения): с учётом замены долгим нажатием и своих наборов,
     * в цвете выбранного оформления.
     */
    fun widgetBitmap(ctx: Context, key0: String, accent: Int? = null): Bitmap? {
        load(ctx)
        val key = resolve(com.dasein.poryadok.ui.common.Glyphs.normalize(key0))
        val raw = runCatching {
            when {
                key.startsWith("/") -> android.graphics.BitmapFactory.decodeFile(key)
                else -> com.dasein.poryadok.ui.common.Glyphs.bitmap(ctx, key)
            }
        }.getOrNull() ?: return null
        if (accent == null || key.startsWith("dish/") || key.startsWith("/")) return raw
        return com.dasein.poryadok.ui.common.IconTint.apply(raw, com.dasein.poryadok.ui.common.IconTint.tone(accent, light = false))
    }

    fun setOverride(ctx: Context, key: String, to: String?) {
        load(ctx)
        val m = overrides.value.toMutableMap()
        if (to == null || to == key) m.remove(key) else m[key] = to
        overrides.value = m; cache = m
        runCatching { overridesFile(ctx).writeText(json.encodeToString(MapSerializer(String.serializer(), String.serializer()), m)) }
        version.intValue++
        // Виджет рисуется отдельно — обновляем его иконки.
        com.dasein.poryadok.Graph.scope.launch { runCatching { Widgets.refresh(ctx.applicationContext) } }
    }
}
