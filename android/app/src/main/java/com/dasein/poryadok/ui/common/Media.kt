package com.dasein.poryadok.ui.common

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dasein.poryadok.ui.common.Text
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.systemBarsPadding
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
    var busy by remember { mutableStateOf<String?>(null) }
    // Скопированный в приложение ролик ждёт решения: обрезать, ускорить/замедлить или оставить как есть.
    var editing by remember { mutableStateOf<String?>(null) }
    val picked by androidx.compose.runtime.rememberUpdatedState(onPicked)
    // Копирование и сжатие идут в фоне приложения, а не экрана: если уйти со страницы, ролик не потеряется.
    fun finish(path: String, segments: List<LongRange>?, speed: Float) {
        io {
            var out = path
            val before = File(path).length()
            if (segments != null || speed != 1f) {
                withContext(Dispatchers.Main) { busy = "Монтирую и сжимаю видео… Не закрывайте приложение" }
                com.dasein.poryadok.system.VideoCompress.editSegments(ctx, File(path), segments.orEmpty(), speed)?.let { f -> File(path).delete(); out = f.absolutePath }
            } else if (com.dasein.poryadok.system.VideoCompress.worth(path)) {
                // Сжимаем: 480p и около 1 Мбит/с — звук и картинка остаются, места занимает в разы меньше.
                withContext(Dispatchers.Main) { busy = "Сжимаю видео (${com.dasein.poryadok.system.VideoCompress.mb(before)})… Не закрывайте приложение" }
                out = com.dasein.poryadok.system.VideoCompress.replace(ctx, path)
            }
            val after = File(out).length()
            withContext(Dispatchers.Main) {
                busy = null
                if (out != path && after < before) android.widget.Toast.makeText(
                    ctx, "Видео готово: ${com.dasein.poryadok.system.VideoCompress.mb(before)} → ${com.dasein.poryadok.system.VideoCompress.mb(after)}", android.widget.Toast.LENGTH_LONG,
                ).show()
                picked(out)
            }
        }
    }
    fun take(uri: Uri?) {
        if (uri == null) return
        io {
            if (!Media.isVideoUri(ctx, uri)) {
                withContext(Dispatchers.Main) { android.widget.Toast.makeText(ctx, "Это не видео. Подходят mp4, mov, 3gp, webm, mkv, avi", android.widget.Toast.LENGTH_LONG).show() }
                return@io
            }
            withContext(Dispatchers.Main) { busy = "Копирую видео…" }
            // Ролик копируется в память приложения целиком: если удалить оригинал в галерее, видео останется.
            val path = Media.importRaw(ctx, uri, dir)?.takeIf { File(it).length() > 0 }
            withContext(Dispatchers.Main) {
                busy = null
                if (path == null) android.widget.Toast.makeText(ctx, "Не удалось добавить видео", android.widget.Toast.LENGTH_SHORT).show()
                else editing = path
            }
        }
    }
    editing?.let { path ->
        VideoEditor(path, addMode = true, onSkip = { editing = null; finish(path, null, 1f) }, onCancel = { editing = null; io { File(path).delete() } }) { segs, speed ->
            editing = null; finish(path, segs, speed)
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
        com.dasein.poryadok.ui.common.OutlinedButton(
            onClick = { gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            modifier = Modifier.weight(1f),
        ) { Text(btnLabel("+ Видео из галереи"), maxLines = 1) }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        com.dasein.poryadok.ui.common.OutlinedButton(
            onClick = { files.launch(arrayOf("video/*", "application/mp4", "application/octet-stream", "*/*")) },
            modifier = Modifier.weight(1f),
        ) { Text(btnLabel("+ Из файлов"), maxLines = 1) }
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
 * Видео со звуком: не стартует само, касание показывает свои кнопки — пуск/пауза и перемотка по полосе.
 * Кнопки рисуются внутри окна видео (системная плашка MediaController висела поверх экрана и не уезжала
 * при прокрутке). «⛶» открывает ролик на весь экран с того же места; маленький плеер на это время на паузе.
 * Ролик идёт по кругу. Окно повторяет пропорции видео — без чёрных полос; вертикальные ограничены по высоте.
 */
@Composable
fun VideoPlayer(path: String, modifier: Modifier = Modifier, maxHeight: androidx.compose.ui.unit.Dp = 520.dp) = androidx.compose.runtime.key(path) {
    // key(path): при удалении или замене ролика проигрыватель создаётся заново, а не показывает прежний файл.
    var full by remember { mutableStateOf(false) }
    var mini by remember { mutableStateOf<VideoView?>(null) }
    var startAt by remember { mutableStateOf(0) }
    val ratio by produceState(16f / 9f, path) { value = withContext(Dispatchers.IO) { videoRatio(path) } ?: 16f / 9f }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.heightIn(max = maxHeight).aspectRatio(ratio, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(12.dp)).background(Color.Black),
        ) {
            AndroidView(
                factory = { c -> playerView(c, path, autoplay = false, startAt = 0).also { mini = it } },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxSize(),
            )
            PlayerControls(mini, paused = full, onFull = {
                mini?.let { v -> startAt = v.currentPosition; v.pause() }
                full = true
            })
        }
    }
    if (full) {
        var big by remember { mutableStateOf<VideoView?>(null) }
        fun close() {
            val b = big
            val pos = b?.currentPosition ?: startAt
            val playing = b?.isPlaying == true
            b?.stopPlayback()
            full = false
            mini?.let { v -> v.seekTo(pos); if (playing) v.start() }
        }
        Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).safeBars(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)) {
                    AndroidView(
                        factory = { c -> playerView(c, path, autoplay = true, startAt = startAt).also { big = it } },
                        onRelease = { it.stopPlayback() },
                        modifier = Modifier.fillMaxSize(),
                    )
                    PlayerControls(big, paused = false, onFull = null)
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = .45f)).clickable { close() }.padding(8.dp),
                ) { PlayerIcon(PlayerIcons.EXIT_FULLSCREEN, 26.dp) }
            }
        }
    }
}

/**
 * Кнопки плеера поверх кадра: касание показывает/прячет их, во время воспроизведения они сами скрываются через 2,5 с.
 * [paused] — плеер сейчас не главный (открыт на весь экран), кнопки не показываем.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PlayerControls(view: VideoView?, paused: Boolean, onFull: (() -> Unit)?) {
    var shown by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0) }
    var dur by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(view) {
        while (true) {
            view?.let { v ->
                playing = v.isPlaying
                if (dragging == null) pos = v.currentPosition
                dur = v.duration.coerceAtLeast(0)
            }
            if (playing && shown && System.currentTimeMillis() - lastTouch > 2500) shown = false
            if (!playing) shown = true
            kotlinx.coroutines.delay(250)
        }
    }
    if (paused) return
    // Касание по кадру — показать или спрятать кнопки.
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
        shown = !shown; lastTouch = System.currentTimeMillis()
    })
    if (onFull != null) Box(
        Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = .45f)).clickable { onFull() }.padding(6.dp),
    ) { PlayerIcon(PlayerIcons.FULLSCREEN, 22.dp) }
    androidx.compose.animation.AnimatedVisibility(shown, Modifier.matchParentSize(), enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            // Центр: −10 с, пуск/пауза, +10 с — фирменные кнопки плеера.
            androidx.compose.foundation.layout.Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                fun skip(ms: Int) {
                    lastTouch = System.currentTimeMillis()
                    view?.let { v -> val to = (v.currentPosition + ms).coerceIn(0, maxOf(0, v.duration)); v.seekTo(to); pos = to }
                }
                RoundPlayerButton(PlayerIcons.BACK, 44.dp, 26.dp, "Назад на 10 секунд") { skip(-10_000) }
                androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
                RoundPlayerButton(if (playing) PlayerIcons.PAUSE else PlayerIcons.PLAY, 60.dp, 34.dp, if (playing) "Пауза" else "Смотреть") {
                    lastTouch = System.currentTimeMillis()
                    view?.let { v -> if (v.isPlaying) v.pause() else v.start(); playing = v.isPlaying }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
                RoundPlayerButton(PlayerIcons.FORWARD, 44.dp, 26.dp, "Вперёд на 10 секунд") { skip(10_000) }
            }
            androidx.compose.foundation.layout.Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .6f))))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(mmss(pos), color = Color.White, fontSize = 12.sp)
                androidx.compose.material3.Slider(
                    value = dragging ?: if (dur > 0) pos.toFloat() / dur else 0f,
                    onValueChange = { dragging = it; lastTouch = System.currentTimeMillis() },
                    onValueChangeFinished = { dragging?.let { f -> view?.seekTo((f * dur).toInt()); pos = (f * dur).toInt() }; dragging = null },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        // Кремовая полоса — в цвет приложения.
                        thumbColor = Color(0xFFFBDFAB), activeTrackColor = Color(0xFFFBDFAB), inactiveTrackColor = Color(0xFFFBDFAB).copy(alpha = .3f),
                    ),
                )
                Text(mmss(dur), color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/** Кнопки плеера из фирменного набора (assets/glyphs/player): рисуются как есть, без перекраски под тему. */
private object PlayerIcons {
    const val PLAY = 0
    const val PAUSE = 1
    const val BACK = 4
    const val FORWARD = 5
    const val FULLSCREEN = 8
    const val EXIT_FULLSCREEN = 9
}

@Composable
private fun PlayerIcon(n: Int, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val key = "player/%02d".format(n)
    val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, key) { value = withContext(Dispatchers.IO) { Glyphs.load(ctx, key) } }
    img?.let { Image(it, null, Modifier.size(size)) } ?: Box(Modifier.size(size))
}

@Composable
private fun RoundPlayerButton(n: Int, box: androidx.compose.ui.unit.Dp, icon: androidx.compose.ui.unit.Dp, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(box).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = .45f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { PlayerIcon(n, icon) }
}

private fun mmss(ms: Int): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

/** Пропорции кадра (ширина / высота) с учётом поворота — по ним строится окно плеера. */
private fun videoRatio(path: String): Float? = runCatching {
    val r = android.media.MediaMetadataRetriever()
    try {
        r.setDataSource(path)
        val w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: return@runCatching null
        val h = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: return@runCatching null
        val rot = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (w <= 0f || h <= 0f) null else if (rot == 90 || rot == 270) h / w else w / h
    } finally { runCatching { r.release() } }
}.getOrNull()?.coerceIn(.3f, 3f)

private fun playerView(c: Context, path: String, autoplay: Boolean, startAt: Int) = VideoView(c).apply {
    setVideoPath(path)
    setOnPreparedListener { mp ->
        mp.setVolume(1f, 1f)
        // Ролик рецепта или упражнения повторяется сам — не нужно тянуться к кнопке на каждом круге.
        mp.isLooping = true
        if (startAt > 0) seekTo(startAt) else if (!autoplay) seekTo(1)
        if (autoplay) start()
    }
}

/** Кнопка «Монтаж» для уже добавленного видео: те же обрезка, куски и скорость, что при добавлении. Файл заменяется. */
@Composable
fun EditVideoButton(path: String, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    var open by remember(path) { mutableStateOf(false) }
    var busy by remember(path) { mutableStateOf(false) }
    val done by androidx.compose.runtime.rememberUpdatedState(onDone)
    if (busy) {
        Text(btnLabel("Монтирую…"), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
        return
    }
    com.dasein.poryadok.ui.common.TextButton(onClick = { open = true }) { Text(btnLabel("Монтаж")) }
    if (open) VideoEditor(path, addMode = false, onCancel = { open = false }, onSkip = { open = false }) { segs, speed ->
        open = false
        busy = true
        // В фоне приложения: если уйти с экрана, монтаж доделается и путь сохранится.
        io {
            val out = com.dasein.poryadok.system.VideoCompress.replaceEdited(ctx, path, segs, speed)
            withContext(Dispatchers.Main) {
                busy = false
                if (out != path) done(out)
                else android.widget.Toast.makeText(ctx, "Не удалось смонтировать видео", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Кнопка «Сжать» для уже добавленного видео: перекодирует в 480p и заменяет файл. */
@Composable
fun CompressVideoButton(path: String, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(path) { mutableStateOf(false) }
    if (busy) {
        Text(btnLabel("Сжимаю…"), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
        return
    }
    com.dasein.poryadok.ui.common.TextButton(onClick = {
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
    }) { Text(btnLabel("Сжать")) }
}
