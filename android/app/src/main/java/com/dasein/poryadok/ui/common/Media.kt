package com.dasein.poryadok.ui.common

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val VIDEO_EXT = setOf("mp4", "m4v", "webm", "3gp", "3g2", "mkv", "mov", "avi", "mpg", "mpeg", "ts", "mts", "m2ts", "wmv", "flv", "ogv")
    fun isVideo(path: String) = path.substringAfterLast('.', "").lowercase() in VIDEO_EXT
    fun isGif(path: String) = path.substringAfterLast('.', "").lowercase() == "gif"

    /** Имя файла, как его видит пользователь (в нём настоящее расширение: .mp4, .mov…). */
    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Расширение файла: из имени, из типа или по умолчанию (видео — mp4, иначе jpg). */
    fun extensionOf(ctx: Context, uri: Uri): String {
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        val fromName = (displayName(ctx, uri) ?: uri.lastPathSegment)?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
        return fromName
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.takeIf { it != "bin" }
            ?: if (mime.startsWith("video") || mime == "application/mp4") "mp4" else "jpg"
    }

    /** Это видео? По типу файла или по расширению. */
    fun isVideoUri(ctx: Context, uri: Uri): Boolean {
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        return mime.startsWith("video") || mime == "application/mp4" || extensionOf(ctx, uri) in VIDEO_EXT
    }

    /** Копирует файл как есть (без пережатия — GIF остаётся анимированным) в папку приложения. */
    suspend fun importRaw(ctx: Context, uri: Uri, dir: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val ext = extensionOf(ctx, uri)
            val folder = File(ctx.filesDir, dir).apply { mkdirs() }
            val f = File(folder, "m_${System.currentTimeMillis()}.$ext")
            ctx.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
            f.absolutePath
        }.getOrNull()
    }
}

/**
 * Добавление видео: «Из галереи» — системный выбор фото и видео (видит все ролики телефона: mp4, mov, 3gp…),
 * «Из файлов» — любой файл из «Загрузок», диска, Telegram. Файл копируется в папку [dir].
 */
@Composable
fun VideoAddButtons(dir: String, onPicked: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    fun take(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            if (!Media.isVideoUri(ctx, uri)) {
                android.widget.Toast.makeText(ctx, "Это не видео. Подходят mp4, mov, 3gp, webm, mkv, avi", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            busy = "Копирую видео…"
            var path = Media.importRaw(ctx, uri, dir)
            if (path == null) {
                busy = null
                android.widget.Toast.makeText(ctx, "Не удалось добавить видео", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            // Сжимаем: 480p и около 1 Мбит/с — звук и картинка остаются, места занимает в разы меньше.
            if (withContext(Dispatchers.IO) { com.dasein.poryadok.system.VideoCompress.worth(path!!) }) {
                val before = File(path!!).length()
                busy = "Сжимаю видео (${com.dasein.poryadok.system.VideoCompress.mb(before)})… Не закрывайте экран"
                path = com.dasein.poryadok.system.VideoCompress.replace(ctx, path!!)
                val after = File(path!!).length()
                if (after < before) android.widget.Toast.makeText(
                    ctx, "Видео сжато: ${com.dasein.poryadok.system.VideoCompress.mb(before)} → ${com.dasein.poryadok.system.VideoCompress.mb(after)}", android.widget.Toast.LENGTH_LONG,
                ).show()
            }
            busy = null
            onPicked(path!!)
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { take(it) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { take(it) }
    busy?.let { msg ->
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.padding(end = 10.dp).size(20.dp), strokeWidth = 2.dp)
            Text(msg, fontSize = 13.sp)
        }
        return
    }
    androidx.compose.foundation.layout.Row {
        androidx.compose.material3.OutlinedButton(
            onClick = { gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            modifier = Modifier.weight(1f),
        ) { Text("+ Видео из галереи", maxLines = 1) }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = { files.launch(arrayOf("video/*", "application/mp4", "application/octet-stream", "*/*")) },
            modifier = Modifier.weight(1f),
        ) { Text("+ Из файлов", maxLines = 1) }
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
    return { launcher.launch(if (video) arrayOf("image/*", "video/*", "application/mp4") else arrayOf("image/*")) }
}

/** Картинка по пути; GIF проигрывается (Android 9+), видео — зациклено без звука. */
@Composable
fun MediaView(path: String, modifier: Modifier = Modifier, crop: Boolean = false) = androidx.compose.runtime.key(path) {
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

/**
 * Видео со звуком: не стартует само, касание показывает кнопки плеера (пуск, пауза, перемотка),
 * «⛶» открывает ролик на весь экран.
 */
@Composable
fun VideoPlayer(path: String, modifier: Modifier = Modifier) = androidx.compose.runtime.key(path) {
    // key(path): при удалении или замене ролика проигрыватель создаётся заново, а не показывает прежний файл.
    var full by remember { mutableStateOf(false) }
    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { c -> playerView(c, path, autoplay = false) },
            onRelease = { it.stopPlayback() },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "⛶", color = Color.White, fontSize = 18.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = .55f)).clickable { full = true }.padding(horizontal = 9.dp, vertical = 2.dp),
        )
    }
    if (full) Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { c -> playerView(c, path, autoplay = true) },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            )
            Text(
                "✕", color = Color.White, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = .55f)).clickable { full = false }.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

private fun playerView(c: Context, path: String, autoplay: Boolean) = VideoView(c).apply {
    val mc = MediaController(c)
    mc.setAnchorView(this)
    setMediaController(mc)
    setVideoPath(path)
    setOnPreparedListener { mp -> mp.setVolume(1f, 1f); if (autoplay) { start(); mc.show(2500) } else seekTo(1) }
}

/** Кнопка «Сжать» для уже добавленного видео: перекодирует в 480p и заменяет файл. */
@Composable
fun CompressVideoButton(path: String, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(path) { mutableStateOf(false) }
    if (busy) {
        Text("Сжимаю…", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
        return
    }
    androidx.compose.material3.TextButton(onClick = {
        busy = true
        scope.launch {
            val before = File(path).length()
            val out = com.dasein.poryadok.system.VideoCompress.replace(ctx, path)
            busy = false
            if (out != path) {
                onDone(out)
                android.widget.Toast.makeText(ctx, "Видео сжато: ${com.dasein.poryadok.system.VideoCompress.mb(before)} → ${com.dasein.poryadok.system.VideoCompress.mb(File(out).length())}", android.widget.Toast.LENGTH_LONG).show()
            } else android.widget.Toast.makeText(ctx, "Это видео уже компактное или не сжимается", android.widget.Toast.LENGTH_SHORT).show()
        }
    }) { Text("Сжать") }
}
