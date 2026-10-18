package com.dasein.poryadok.ui.recipes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dasein.poryadok.Graph
import com.dasein.poryadok.ui.common.Crop
import java.io.File

/**
 * Свои иконки рецептов с прозрачным фоном. Раньше картинка после кадрирования сохранялась в JPEG — прозрачность
 * заменялась белым, и иконка стояла белым квадратом. Теперь прозрачные картинки остаются PNG и рисуются
 * как иконки из набора: на подложке карточки, целиком, без обрезки. Фото без прозрачности — как раньше, JPEG.
 */
object RecipeIcons {
    /** Своя иконка (PNG с прозрачностью), а не фото. */
    fun isIcon(photo: String): Boolean = photo.endsWith(".png", ignoreCase = true) && !photo.startsWith("dish/") && !photo.startsWith("u:")

    private fun transparentShare(b: Bitmap): Float {
        if (!b.hasAlpha()) return 0f
        val w = b.width
        val h = b.height
        if (w < 2 || h < 2) return 0f
        var clear = 0
        var tot = 0
        val step = maxOf(1, minOf(w, h) / 64)
        for (x in 0 until w step step) { tot += 2; if (b.getPixel(x, 0) ushr 24 < 16) clear++; if (b.getPixel(x, h - 1) ushr 24 < 16) clear++ }
        for (y in 0 until h step step) { tot += 2; if (b.getPixel(0, y) ushr 24 < 16) clear++; if (b.getPixel(w - 1, y) ushr 24 < 16) clear++ }
        return if (tot == 0) 0f else clear.toFloat() / tot
    }

    /**
     * После кадрирования (сохранено в PNG): если по краям прозрачно — это иконка, оставляем PNG;
     * если прозрачности нет — обычное фото, пересохраняем в JPEG, чтобы не занимало лишнего места.
     */
    fun finish(path: String): String = runCatching {
        if (!path.endsWith(".png", true)) return path
        val b = BitmapFactory.decodeFile(path) ?: return path
        if (transparentShare(b) > .3f) return path
        val jpg = File(path.substringBeforeLast('.') + ".jpg")
        val flat = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(flat).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(b, 0f, 0f, null) }
        jpg.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        // Исходник для перекадрирования переезжает вслед за файлом.
        File(Crop.originalOf(path)).takeIf { it.exists() }?.renameTo(File(Crop.originalOf(jpg.absolutePath)))
        File(path).delete()
        jpg.absolutePath
    }.getOrDefault(path)

    /**
     * Уже добавленные иконки, у которых прозрачный фон стал белым: исходник лежит рядом (…_orig), и если в нём
     * есть прозрачность — делаем из него PNG-иконку и ставим её рецепту. Фото без прозрачности не трогаем.
     */
    /** Новая иконка или фото моего блюда — в папку «Мои рецепты» библиотеки иконок. */
    fun addToFolder(path: String) = runCatching { com.dasein.poryadok.system.IconSets.addRecipeIcons(Graph.app, listOf(path)) }

    /** Один раз: иконки и фото уже добавленных моих рецептов — тоже в «Мои рецепты». */
    suspend fun collectMine() {
        val files = Graph.app.filesDir.absolutePath
        val paths = Graph.extra.recipesNow().filter { it.custom && it.photo.startsWith(files) }.map { it.photo }
        runCatching { com.dasein.poryadok.system.IconSets.addRecipeIcons(Graph.app, paths) }
    }

    suspend fun repairWhiteBackgrounds() {
        val files = Graph.app.filesDir.absolutePath
        Graph.extra.recipesNow().forEach { r ->
            val p = r.photo
            if (!p.startsWith(files) || isIcon(p)) return@forEach
            val orig = File(Crop.originalOf(p)).takeIf { it.exists() } ?: return@forEach
            val src = runCatching { com.dasein.poryadok.ui.common.Images.decode(orig.absolutePath, 600) }.getOrNull() ?: return@forEach
            if (transparentShare(src) <= .3f) return@forEach
            val out = File(p.substringBeforeLast('.') + "_icon.png")
            val ok = runCatching { out.outputStream().use { src.compress(Bitmap.CompressFormat.PNG, 100, it) } }.isSuccess
            if (!ok || out.length() == 0L) return@forEach
            orig.copyTo(File(Crop.originalOf(out.absolutePath)), overwrite = true)
            Graph.extra.upsertRecipe(r.copy(photo = out.absolutePath))
            // Белую копию удаляем, только если она больше ни у кого не стоит.
            if (Graph.extra.recipesNow().none { it.photo == p }) { File(p).delete(); orig.delete() }
        }
    }
}
