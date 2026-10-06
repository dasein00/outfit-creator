package com.dasein.poryadok.system

import android.content.Context
import android.net.Uri
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Recipe
import com.dasein.poryadok.data.RecipeIngredient
import com.dasein.poryadok.data.RecipeRepo
import com.dasein.poryadok.data.RecipeStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Обмен своими рецептами между телефонами: файл .zip, внутри recipes.json и папка media с фото и видео.
 * Ссылки на видео (YouTube, VK…) и встроенные картинки блюд переносятся как есть.
 */
object RecipeShare {
    @Serializable
    data class Item(val recipe: Recipe, val ingredients: List<RecipeIngredient> = emptyList(), val steps: List<String> = emptyList())

    @Serializable
    data class Pack(val format: Int = 1, val app: String = "DASEIN", val createdAt: Long = 0, val recipes: List<Item> = emptyList())

    data class ImportResult(val added: Int, val skipped: Int, val files: Int)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val SAFE = Regex("^media/[A-Za-z0-9_.\\-]+$")
    private const val PREFIX = "media/"

    /** Локальный файл (не ссылка и не встроенная картинка). */
    private fun localFile(p: String): File? = p.takeIf { it.startsWith("/") }?.let(::File)?.takeIf { it.isFile }

    private fun videosOf(r: Recipe) = r.videos.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Сколько весят фото и видео выбранных рецептов — чтобы предупредить о размере файла. */
    suspend fun mediaSize(ids: Collection<Long>, withVideo: Boolean): Long = withContext(Dispatchers.IO) {
        Graph.extra.recipesNow().filter { it.id in ids }.sumOf { r ->
            (localFile(r.photo)?.length() ?: 0L) + if (withVideo) videosOf(r).sumOf { localFile(it)?.length() ?: 0L } else 0L
        }
    }

    /** Пишет рецепты в zip. Возвращает число рецептов. */
    suspend fun export(ids: Collection<Long>, withVideo: Boolean, out: OutputStream): Int = withContext(Dispatchers.IO) {
        val x = Graph.extra
        val recipes = x.recipesNow().filter { it.id in ids }.sortedBy { it.name }
        val files = LinkedHashMap<String, File>()
        fun pack(f: File): String {
            files.entries.firstOrNull { it.value == f }?.let { return it.key }
            val name = PREFIX + "${files.size + 1}_" + f.name.replace(Regex("[^A-Za-z0-9_.\\-]"), "_").takeLast(60)
            files[name] = f
            return name
        }
        val items = recipes.map { r ->
            val photo = localFile(r.photo)?.let(::pack) ?: r.photo.takeIf { it.startsWith("dish/") }.orEmpty()
            val videos = videosOf(r).mapNotNull { v ->
                when {
                    v.startsWith("http") -> v
                    withVideo -> localFile(v)?.let(::pack)
                    else -> null
                }
            }
            Item(
                r.copy(id = 0, photo = photo, videos = videos.joinToString("\n"), favorite = false, archived = false, seedKey = null),
                x.ingredientsOfNow(r.id).map { it.copy(id = 0, recipeId = 0, productId = null) },
                x.stepsOfNow(r.id).map { it.text },
            )
        }
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("recipes.json"))
            zip.write(json.encodeToString(Pack.serializer(), Pack(createdAt = System.currentTimeMillis(), recipes = items)).toByteArray())
            zip.closeEntry()
            files.forEach { (name, f) ->
                zip.putNextEntry(ZipEntry(name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        items.size
    }

    /** Готовит файл для отправки (Telegram, почта, диск) в кэше. */
    suspend fun exportToCache(ctx: Context, ids: Collection<Long>, withVideo: Boolean): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val name = if (ids.size == 1) Graph.extra.recipeNow(ids.first())?.name.orEmpty() else "Рецепты DASEIN (${ids.size})"
        val f = File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Рецепты DASEIN" } + ".zip")
        f.outputStream().use { export(ids, withVideo, it) }
        f
    }

    /**
     * Загружает рецепты из файла: они появятся во вкладке «Мои». Рецепт с тем же названием и теми же шагами,
     * что уже есть на телефоне, пропускается.
     */
    suspend fun import(ctx: Context, uri: Uri): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            var pack: Pack? = null
            val tmp = File(ctx.cacheDir, "recipe_import").apply { deleteRecursively(); mkdirs() }
            val input = ctx.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    when {
                        e.name == "recipes.json" -> pack = json.decodeFromString(Pack.serializer(), zip.readBytes().decodeToString())
                        !e.isDirectory && SAFE.matches(e.name) -> File(tmp, e.name.removePrefix(PREFIX)).outputStream().use { zip.copyTo(it) }
                    }
                    e = zip.nextEntry
                }
            }
            val p = pack ?: error("В файле нет рецептов DASEIN")
            val x = Graph.extra
            val existing = x.recipesNow()
            val stamp = System.currentTimeMillis()
            var added = 0
            var skipped = 0
            var files = 0
            fun place(ref: String, dir: String): String? {
                if (!ref.startsWith(PREFIX)) return ref
                val src = File(tmp, ref.removePrefix(PREFIX)).takeIf { it.isFile } ?: return null
                val target = File(File(ctx.filesDir, dir).apply { mkdirs() }, "imp_${stamp}_${src.name}")
                src.copyTo(target, overwrite = true)
                files++
                return target.absolutePath
            }
            p.recipes.forEach { item ->
                val r = item.recipe
                val dup = existing.any { e -> e.name.equals(r.name, true) && x.stepsOfNow(e.id).map { it.text } == item.steps }
                if (dup) { skipped++; return@forEach }
                val photo = place(r.photo, "recipes").orEmpty()
                val videos = r.videos.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { place(it, "recipes/video") }
                RecipeRepo.saveRecipe(
                    r.copy(id = 0, custom = true, seedKey = null, createdAt = 0, photo = photo, videos = videos.joinToString("\n")),
                    item.ingredients.map { it.copy(id = 0, productId = null) },
                    item.steps,
                )
                added++
            }
            tmp.deleteRecursively()
            ImportResult(added, skipped, files)
        }
    }
}
