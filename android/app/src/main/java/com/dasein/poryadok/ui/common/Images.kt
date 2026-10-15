package com.dasein.poryadok.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Images {
    /** Декодирует картинку с уменьшением, чтобы сторона была не больше [max], и учитывает поворот из EXIF. */
    fun decode(path: String, max: Int): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= max || opts.outHeight / (sample * 2) >= max) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rot = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rot == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
    }.getOrNull()

    /** Копирует выбранное пользователем изображение в личную папку приложения. */
    suspend fun importUri(ctx: Context, uri: Uri, dir: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val folder = File(ctx.filesDir, dir).apply { mkdirs() }
            val f = File(folder, "img_${System.currentTimeMillis()}.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
            f.absolutePath
        }.getOrNull()
    }
}

@Composable
fun rememberImage(path: String?, max: Int = 600): State<ImageBitmap?> {
    // Уже декодированное фото берём из памяти: при прокрутке списков картинки не мигают и не декодируются заново.
    val key = androidx.compose.runtime.remember(path, max) { path?.let { "$it|$max|${java.io.File(it).lastModified()}" } }
    return produceState(key?.let { ImageMemory.get(it) }, key) {
        value = if (path == null || key == null) null else ImageMemory.get(key) ?: withContext(Dispatchers.IO) {
            Images.decode(path, max)?.asImageBitmap()?.also { ImageMemory.put(key, it) }
        }
    }
}

/** Кэш фото в памяти: до 1/8 доступной приложению памяти. */
object ImageMemory {
    private val cache = object : android.util.LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt().coerceAtLeast(8 * 1024)) {
        override fun sizeOf(key: String, value: ImageBitmap) = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }
    fun get(k: String): ImageBitmap? = cache.get(k)
    fun put(k: String, v: ImageBitmap) { cache.put(k, v) }
    fun clear() = cache.evictAll()
}
