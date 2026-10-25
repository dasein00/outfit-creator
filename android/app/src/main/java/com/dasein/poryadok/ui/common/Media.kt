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
fun VideoAddButtons(dir: String, galleryLabel: String = "+ Видео из галереи", filesLabel: String = "+ Из файлов", onPicked: (String) -> Unit) {
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf<String?>(null) }
    // Скопированный в приложение ролик ждёт решения: обрезать, ускорить/замедлить или оставить как есть.
    var editing by remember { mutableStateOf<String?>(null) }
    val picked by androidx.compose.runtime.rememberUpdatedState(onPicked)
    // Копирование и сжатие идут в фоне приложения, а не экрана: если уйти со страницы, ролик не потеряется.
    fun finish(path: String, edit: VideoEdit?) {
        io {
            var out = path
            val before = File(path).length()
            if (edit != null) {
                withContext(Dispatchers.Main) { busy = "Монтирую и сжимаю видео… Не закрывайте приложение" }
                com.dasein.poryadok.system.VideoCompress.editClips(ctx, edit.sources.map { File(it) }, edit.clips, edit.aspect, edit.mute)?.let { f -> File(path).delete(); out = f.absolutePath }
                // Видео, добавленные в монтаже, уже вклеены — копии больше не нужны.
                edit.extras.forEach { File(it).delete() }
            } else if (com.dasein.poryadok.system.VideoCompress.worth(path)) {
                // Сжимаем как можно компактнее: 360p, 24 кадра/с, H.265 около 250 кбит/с, звук моно — минута около 2 МБ.
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
        VideoEditor(path, dir, addMode = true, onSkip = { editing = null; finish(path, null) }, onCancel = { editing = null; io { File(path).delete() } }) { e ->
            editing = null; finish(path, e)
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
        ) { Text(btnLabel(galleryLabel), maxLines = 1) }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        com.dasein.poryadok.ui.common.OutlinedButton(
            onClick = { files.launch(arrayOf("video/*", "application/mp4", "application/octet-stream", "*/*")) },
            modifier = Modifier.weight(1f),
        ) { Text(btnLabel(filesLabel), maxLines = 1) }
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
 * Скорость 0,5×–2× — кнопкой слева вверху (звук без «мультяшного» голоса); на весь экран переходит та же скорость.
 */
@Composable
fun VideoPlayer(path: String, modifier: Modifier = Modifier, maxHeight: androidx.compose.ui.unit.Dp = 520.dp) = androidx.compose.runtime.key(path) {
    // key(path): при удалении или замене ролика проигрыватель создаётся заново, а не показывает прежний файл.
    var full by remember { mutableStateOf(false) }
    var mini by remember { mutableStateOf<VideoView?>(null) }
    var startAt by remember { mutableStateOf(0) }
    var speed by remember { mutableStateOf(1f) }
    val ratio by produceState(16f / 9f, path) { value = withContext(Dispatchers.IO) { videoRatio(path) } ?: 16f / 9f }
    // Видео рисуется на отдельном слое (SurfaceView), который не гаснет вместе с экраном: при выходе из карточки
    // кадр «висел» поверх списка, пока шла анимация. Уходим с экрана — плеер сразу прячется и встаёт на паузу,
    // возвращаемся — показывается с того же места.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle, mini) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            val v = mini ?: return@LifecycleEventObserver
            when (ev) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> if (v.visibility == android.view.View.VISIBLE) {
                    v.tag = v.currentPosition.coerceAtLeast(1)
                    v.pause()
                    v.visibility = android.view.View.INVISIBLE
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> if (v.visibility != android.view.View.VISIBLE) v.visibility = android.view.View.VISIBLE
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); mini?.visibility = android.view.View.INVISIBLE }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.heightIn(max = maxHeight).aspectRatio(ratio, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(12.dp)).background(Color.Black),
        ) {
            AndroidView(
                factory = { c -> playerView(c, path, autoplay = false, startAt = 0).also { mini = it } },
                onRelease = { it.visibility = android.view.View.INVISIBLE; it.stopPlayback() },
                modifier = Modifier.fillMaxSize(),
            )
            PlayerControls(mini, paused = full, speed = speed, onSpeed = { speed = it }, onFull = {
                mini?.let { v -> startAt = v.currentPosition; v.pause() }
                full = true
            })
        }
    }
    if (full) {
        var big by remember { mutableStateOf<VideoView?>(null) }
        var bigUi by remember { mutableStateOf(true) }
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
                    PlayerControls(big, paused = false, speed = speed, onSpeed = { speed = it }, onFull = null, onShown = { bigUi = it })
                }
                if (bigUi) Box(
                    Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = .45f)).clickable { close() }.padding(8.dp),
                ) { PlayerIcon(PlayerIcons.EXIT_FULLSCREEN, 26.dp) }
            }
        }
    }
}

/**
 * Кнопки плеера поверх кадра: касание показывает/прячет их, во время воспроизведения они сами скрываются через 2,5 с.
 * На паузе касание по кадру (не по кнопке) тоже прячет всё — чистый кадр для скриншота; следующее касание возвращает кнопки.
 * [paused] — плеер сейчас не главный (открыт на весь экран), кнопки не показываем. [onShown] — видны ли кнопки.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PlayerControls(
    view: VideoView?, paused: Boolean, speed: Float, onSpeed: (Float) -> Unit, onFull: (() -> Unit)?, onShown: (Boolean) -> Unit = {},
) {
    val wantSpeed by androidx.compose.runtime.rememberUpdatedState(speed)
    var shown by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0) }
    var dur by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    var wasPlaying by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(shown) { onShown(shown) }
    androidx.compose.runtime.LaunchedEffect(view) {
        while (true) {
            view?.let { v ->
                playing = v.isPlaying
                if (dragging == null) pos = v.currentPosition
                dur = v.duration.coerceAtLeast(0)
                applySpeed(v, wantSpeed)
            }
            if (playing && shown && System.currentTimeMillis() - lastTouch > 2500) shown = false
            // Видео встало на паузу (само или кнопкой) — кнопки показываются; дальше касание по кадру их прячет.
            if (!playing && wasPlaying) shown = true
            wasPlaying = playing
            kotlinx.coroutines.delay(250)
        }
    }
    if (paused) return
    // Касание по кадру — показать или спрятать кнопки.
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
        shown = !shown; lastTouch = System.currentTimeMillis()
    })
    androidx.compose.animation.AnimatedVisibility(shown, Modifier.matchParentSize(), enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            if (onFull != null) Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = .45f)).clickable { onFull() }.padding(6.dp),
            ) { PlayerIcon(PlayerIcons.FULLSCREEN, 22.dp) }
            // Слева вверху — скорость: список от 0,1× (очень медленно, рассмотреть движение) до 2×.
            if (Build.VERSION.SDK_INT >= 23) Box(Modifier.align(Alignment.TopStart).padding(6.dp)) {
                var speedMenu by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .45f))
                        .clickable(onClickLabel = "Скорость") { lastTouch = System.currentTimeMillis(); speedMenu = true }.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIcon(PlayerIcons.SPEED, 20.dp)
                    Text(" " + speedText(speed) + " ▾", color = if (speed == 1f) Color.White else Color(0xFFFBDFAB), fontSize = 13.sp, maxLines = 1)
                }
                androidx.compose.material3.DropdownMenu(speedMenu, { speedMenu = false }) {
                    PLAYER_SPEEDS.sorted().forEach { v ->
                        androidx.compose.material3.DropdownMenuItem(
                            { Text(speedText(v) + if (v == 1f) " (обычная)" else if (v <= 0.25f) " (медленно)" else "", fontWeight = if (v == speed) androidx.compose.ui.text.font.FontWeight.Bold else null) },
                            { speedMenu = false; lastTouch = System.currentTimeMillis(); onSpeed(v) },
                        )
                    }
                }
            }
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
    const val SPEED = 11
}

// По кругу: обычная, быстрее, потом медленнее — до 0,1× (покадрово рассмотреть движение).
private val PLAYER_SPEEDS = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.1f, 0.25f, 0.5f, 0.75f)
private fun speedText(s: Float) = (if (s == Math.floor(s.toDouble()).toFloat()) "${s.toInt()}" else s.toString().replace('.', ',')) + "×"

/** MediaPlayer каждого плеера (VideoView скорость менять не умеет) и скорость, которая на нём уже стоит. */
private val playerMp = java.util.WeakHashMap<VideoView, android.media.MediaPlayer>()
private val playerSpeed = java.util.WeakHashMap<android.media.MediaPlayer, Float>()

private fun applySpeed(v: VideoView, speed: Float) {
    if (Build.VERSION.SDK_INT < 23) return
    val mp = playerMp[v] ?: return
    if ((playerSpeed[mp] ?: 1f) == speed) return
    runCatching {
        val was = v.isPlaying
        mp.playbackParams = mp.playbackParams.setSpeed(speed)
        // Смена скорости на паузе запускает ролик — возвращаем паузу.
        if (!was) mp.pause()
        playerSpeed[mp] = speed
    }
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
        playerMp[this] = mp
        // Ролик рецепта или упражнения повторяется сам — не нужно тянуться к кнопке на каждом круге.
        mp.isLooping = true
        // Вернулись на экран — плеер открылся заново; продолжаем с того места, где ушли.
        val resume = tag as? Int
        tag = null
        if (resume != null) seekTo(resume) else if (startAt > 0) seekTo(startAt) else if (!autoplay) seekTo(1)
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
    // Добавленные в монтаже видео копируются в ту же папку, что и этот ролик.
    val dir = remember(path) { File(path).parentFile?.absolutePath.orEmpty().removePrefix(ctx.filesDir.absolutePath).trimStart('/').ifBlank { "video" } }
    if (open) VideoEditor(path, dir, addMode = false, onCancel = { open = false }, onSkip = { open = false }) { e ->
        open = false
        busy = true
        // В фоне приложения: если уйти с экрана, монтаж доделается и путь сохранится.
        io {
            val out = com.dasein.poryadok.system.VideoCompress.replaceEdited(ctx, path, e.sources, e.clips, e.aspect, e.mute)
            e.extras.forEach { File(it).delete() }
            withContext(Dispatchers.Main) {
                busy = false
                if (out != path) done(out)
                else android.widget.Toast.makeText(ctx, "Не удалось смонтировать видео", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Кнопка «Сжать» для уже добавленного видео: видна, только если ролик крупнее нынешних настроек; заменяет файл. */
@Composable
fun CompressVideoButton(path: String, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(path) { mutableStateOf(false) }
    val worth by androidx.compose.runtime.produceState(false, path) { value = withContext(Dispatchers.IO) { com.dasein.poryadok.system.VideoCompress.worth(path) } }
    if (!worth && !busy) return
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
