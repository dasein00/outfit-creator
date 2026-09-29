package com.dasein.poryadok.ui.common

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Картинки, GIF и видео, которые пользователь прикрепляет к заметкам и упражнениям. */
object Media {
    fun isVideo(path: String) = path.substringAfterLast('.', "").lowercase() in setOf("mp4", "webm", "3gp", "mkv", "mov")
    fun isGif(path: String) = path.substringAfterLast('.', "").lowercase() == "gif"

    /** Копирует файл как есть (без пережатия — GIF остаётся анимированным) в папку приложения. */
    suspend fun importRaw(ctx: Context, uri: Uri, dir: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val mime = ctx.contentResolver.getType(uri) ?: ""
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 2..4 }
                ?: if (mime.startsWith("video")) "mp4" else "jpg"
            val folder = File(ctx.filesDir, dir).apply { mkdirs() }
            val f = File(folder, "m_${System.currentTimeMillis()}.$ext")
            ctx.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
            f.absolutePath
        }.getOrNull()
    }
}

/** Выбор картинки, GIF (и видео, если [video]) из галереи. Возвращает путь к копии в папке [dir]. */
@Composable
fun rememberMediaPicker(dir: String, video: Boolean = false, onPicked: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { Media.importRaw(ctx, uri, dir)?.let(onPicked) }
    }
    return { launcher.launch(if (video) arrayOf("image/*", "video/*") else arrayOf("image/*")) }
}

/** Картинка по пути; GIF проигрывается (Android 9+), видео — зациклено без звука. */
@Composable
fun MediaView(path: String, modifier: Modifier = Modifier, crop: Boolean = false) {
    when {
        Media.isVideo(path) -> AndroidView(
            factory = { c ->
                VideoView(c).apply {
                    setVideoPath(path)
                    setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); start() }
                }
            },
            modifier = modifier,
        )
        Media.isGif(path) && Build.VERSION.SDK_INT >= 28 -> {
            val drawable by produceState<android.graphics.drawable.Drawable?>(null, path) {
                value = withContext(Dispatchers.IO) { runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path))) }.getOrNull() }
            }
            AndroidView(
                factory = { c -> ImageView(c).apply { adjustViewBounds = true; scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER } },
                update = { v ->
                    val d = drawable
                    if (v.drawable !== d) {
                        v.setImageDrawable(d)
                        (d as? AnimatedImageDrawable)?.start()
                    }
                },
                modifier = modifier,
            )
        }
        else -> {
            val img by rememberImage(path, 1400)
            Box(modifier) {
                img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit) }
            }
        }
    }
}
