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
fun rememberImage(path: String?, max: Int = 600): State<ImageBitmap?> = produceState<ImageBitmap?>(null, path, max) {
    value = if (path == null) null else withContext(Dispatchers.IO) { Images.decode(path, max)?.asImageBitmap() }
}
