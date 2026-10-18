package com.dasein.poryadok.ui.common

import android.media.MediaMetadataRetriever
import android.os.Build
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dasein.poryadok.logic.VideoCut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val Cream = Color(0xFFFBDFAB)
private val Orange = Color(0xFFFC8C1E)
private val Panel = Color(0xFF26221C)

/**
 * Монтаж ролика перед добавлением (или уже добавленного): лента с кадрами и шкалой времени, которую можно
 * увеличивать (кнопки «−/+» или щипком двумя пальцами), куски можно разрезать по бегунку, дублировать и удалять,
 * края выбранного куска тянуть ползунками или вписать время цифрами. Скорость 0,5×–2×. «Отменить/Вернуть».
 * Когда тянете конец куска, превью проигрывает последние 2 секунды до него — видно, где обрезается.
 * Результат — список кусков исходного файла (мс) и скорость; склейку и сжатие делает VideoCompress.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoEditor(path: String, addMode: Boolean, onCancel: () -> Unit, onSkip: () -> Unit, onDone: (List<LongRange>, Float) -> Unit) {
    val duration by produceState(0L, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                try { r.setDataSource(path); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { runCatching { r.release() } }
            }.getOrDefault(0L)
        }
    }
    val ratio by produceState(9f / 16f, path) { value = withContext(Dispatchers.IO) { videoAspect(path) } ?: 9f / 16f }
    val thumbs by produceState<List<Pair<Long, ImageBitmap>>>(emptyList(), path, duration) {
        if (duration > 0) value = withContext(Dispatchers.IO) { frames(path, duration) }
    }
    val segments = remember { mutableStateListOf<LongRange>() }
    LaunchedEffect(duration) { if (duration > 0 && segments.isEmpty()) segments.add(0L..duration) }
    val undo = remember { mutableStateListOf<List<LongRange>>() }
    val redo = remember { mutableStateListOf<List<LongRange>>() }
    fun commit(change: (MutableList<LongRange>) -> Unit) {
        undo.add(segments.toList()); redo.clear()
        val l = segments.toMutableList(); change(l); segments.clear(); segments.addAll(l)
    }
    var selected by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var view by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(false) }
    var srcPos by remember { mutableLongStateOf(0L) }
    var curSeg by remember { mutableIntStateOf(0) }
    /** Остановить превью на этом месте исходника (проигрывание «последних 2 секунд» до конца куска). */
    var stopAt by remember { mutableStateOf<Long?>(null) }
    var showSpeed by remember { mutableStateOf(false) }

    fun seekSrc(ms: Long, seg: Int) { curSeg = seg; srcPos = ms; view?.seekTo(ms.toInt()) }
    fun play() { view?.start(); playing = true }
    fun pause() { view?.pause(); playing = false }

    // Проигрывание по кускам: дошли до конца куска — прыгаем на следующий, после последнего — по кругу.
    LaunchedEffect(view) {
        while (true) {
            val v = view
            if (v != null && segments.isNotEmpty()) {
                val p = v.currentPosition.toLong()
                if (playing) srcPos = p
                val seg = segments.getOrNull(curSeg) ?: segments.first().also { curSeg = 0 }
                val stop = stopAt
                if (playing && stop != null && p >= stop - 40) { pause(); stopAt = null }
                else if (playing && p >= seg.last - 30) {
                    val next = (curSeg + 1) % segments.size
                    seekSrc(segments[next].first, next)
                }
            }
            delay(40)
        }
    }
    LaunchedEffect(view, speed) {
        val v = view ?: return@LaunchedEffect
        runCatching { if (Build.VERSION.SDK_INT >= 23) editorPlayers[v]?.let { mp -> val was = v.isPlaying; mp.playbackParams = mp.playbackParams.setSpeed(speed); if (!was) mp.pause() } }
    }

    val total = VideoCut.total(segments)
    val playhead = VideoCut.toEdited(segments, curSeg, srcPos)

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF15120E)).systemBarsPadding()) {
            // Верх: закрыть, отменить/вернуть, готово.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TopIcon("✕", "Закрыть") { onCancel() }
                TopIcon("↶", "Отменить", enabled = undo.isNotEmpty()) {
                    redo.add(segments.toList()); val prev = undo.removeAt(undo.lastIndex); segments.clear(); segments.addAll(prev); selected = selected.coerceIn(0, segments.lastIndex)
                }
                TopIcon("↷", "Вернуть", enabled = redo.isNotEmpty()) {
                    undo.add(segments.toList()); val nxt = redo.removeAt(redo.lastIndex); segments.clear(); segments.addAll(nxt); selected = selected.coerceIn(0, segments.lastIndex)
                }
                Spacer(Modifier.weight(1f))
                val changed = speed != 1f || segments.size != 1 || segments.firstOrNull()?.let { it.first > 0 || it.last < duration } == true
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Cream).clickable {
                        if (duration <= 0) return@clickable
                        if (!changed) onSkip() else onDone(segments.toList(), speed)
                    }.padding(horizontal = 18.dp, vertical = 10.dp),
                ) { Text(btnLabel(if (changed) "Готово" else if (addMode) "Добавить" else "Закрыть"), color = Color(0xFF241C0D), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
            }
            // Превью.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxHeight().aspectRatio(ratio, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
                    AndroidView(
                        factory = { c ->
                            VideoView(c).apply {
                                setVideoPath(path)
                                setOnPreparedListener { mp -> mp.setVolume(1f, 1f); editorPlayers[this] = mp; seekTo(1); view = this }
                            }
                        },
                        onRelease = { editorPlayers.remove(it); it.stopPlayback() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // Пуск и время.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${clock(playhead)} / ${clock(total)}", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .12f)).clickable {
                    stopAt = null
                    if (playing) pause() else play()
                }, contentAlignment = Alignment.Center) { EditorIcon(if (playing) 1 else 0, 28.dp) }
                Spacer(Modifier.weight(1f))
                ZoomButton("−") { zoom = (zoom / 1.6f).coerceAtLeast(1f) }
                Text("  ${if (zoom < 10) "%.1f".format(zoom) else zoom.roundToInt().toString()}×  ".replace('.', ','), color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                ZoomButton("+") { zoom = (zoom * 1.6f).coerceAtMost(40f) }
            }
            // Лента: шкала времени, кадры по кускам, ползунки выбранного куска, бегунок.
            BoxWithConstraints(Modifier.fillMaxWidth().height(118.dp)) {
                val density = LocalDensity.current
                val viewW = with(density) { maxWidth.toPx() }
                val side = viewW / 2f
                val padPx = with(density) { 22.dp.toPx() }
                // Масштаб от длины исходника: при обрезке кусок укорачивается, а шкала не «плывёт».
                val pxPerMs = if (duration > 0) (viewW - 2 * padPx) * zoom / duration else 0f
                val stripW = total * pxPerMs
                val scroll = rememberScrollState()
                // Бегунок держим в поле зрения при проигрывании.
                LaunchedEffect(playhead, playing, zoom) {
                    if (playing && pxPerMs > 0) {
                        val x = (playhead * pxPerMs).roundToInt()
                        if (x < scroll.value || x > scroll.value + viewW.roundToInt() - (side / 2).roundToInt()) scroll.scrollTo((x - side.roundToInt() / 2).coerceAtLeast(0))
                    }
                }
                Box(
                    Modifier.fillMaxSize().horizontalScroll(scroll).pointerInput(Unit) {
                        // Щипок двумя пальцами — масштаб ленты; одним пальцем лента прокручивается.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.none { it.pressed }) break
                                if (ev.changes.size >= 2) {
                                    val z = ev.calculateZoom()
                                    if (z != 1f) zoom = (zoom * z).coerceIn(1f, 40f)
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
                ) {
                    val widthDp = with(density) { (maxOf(stripW, viewW - 2 * padPx) + 2 * padPx).toDp() }
                    Box(Modifier.width(widthDp).fillMaxHeight().padding(horizontal = 22.dp)) {
                        // Шкала времени.
                        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                            if (pxPerMs <= 0f) return@Canvas
                            val step = VideoCut.tickStep(pxPerMs)
                            var t = 0L
                            val paint = android.graphics.Paint().apply { color = android.graphics.Color.argb(170, 255, 255, 255); textSize = 10.sp.toPx(); isAntiAlias = true }
                            while (t <= total) {
                                val x = t * pxPerMs
                                drawLine(Color.White.copy(alpha = .45f), Offset(x, size.height - 6.dp.toPx()), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                                drawContext.canvas.nativeCanvas.drawText(clockShort(t), x + 3.dp.toPx(), size.height - 8.dp.toPx(), paint)
                                t += step
                            }
                        }
                        // Кадры кусков.
                        Canvas(
                            Modifier.padding(top = 26.dp).fillMaxWidth().height(62.dp).pointerInput(segments.toList(), pxPerMs) {
                                detectTapGestures { o ->
                                    if (pxPerMs <= 0f) return@detectTapGestures
                                    val (i, src) = VideoCut.fromEdited(segments, (o.x / pxPerMs).toLong())
                                    selected = i; stopAt = null; seekSrc(src, i)
                                }
                            },
                        ) {
                            var x0 = 0f
                            val h = size.height
                            val tileW = h * 0.62f
                            segments.forEachIndexed { i, seg ->
                                val w = (seg.last - seg.first) * pxPerMs
                                // Кадры: по одному на ширину плитки, берём ближайший готовый кадр исходника.
                                var x = x0
                                while (x < x0 + w) {
                                    val src = seg.first + ((x - x0 + tileW / 2) / pxPerMs).toLong()
                                    val img = thumbs.minByOrNull { kotlin.math.abs(it.first - src) }?.second
                                    val tw = minOf(tileW, x0 + w - x)
                                    if (img != null && tw > 1f) {
                                        val sw = (img.width * (tw / tileW)).toInt().coerceAtLeast(1)
                                        drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(sw, img.height), dstOffset = IntOffset(x.toInt(), 0), dstSize = IntSize(tw.toInt().coerceAtLeast(1), h.toInt()))
                                    } else drawRect(Color(0xFF3A342B), Offset(x, 0f), Size(tw, h))
                                    x += tileW
                                }
                                // Разделитель кусков и рамка выбранного.
                                drawLine(Color.Black, Offset(x0, 0f), Offset(x0, h), strokeWidth = 2.dp.toPx())
                                if (i == selected) drawRect(Cream, Offset(x0, 0f), Size(w, h), style = Stroke(width = 3.dp.toPx()))
                                x0 += w
                            }
                            // Бегунок.
                            val px = playhead * pxPerMs
                            drawLine(Color.White, Offset(px, -4.dp.toPx()), Offset(px, h + 4.dp.toPx()), strokeWidth = 2.5.dp.toPx())
                        }
                        // Ползунки начала и конца выбранного куска.
                        val sel = segments.getOrNull(selected)
                        if (sel != null && pxPerMs > 0f) {
                            val segStart = VideoCut.offsetOf(segments, selected) * pxPerMs
                            val segW = (sel.last - sel.first) * pxPerMs
                            val handleW = 18.dp
                            val hw = with(density) { handleW.toPx() }
                            val drag = remember { mutableFloatStateOf(0f) }
                            val startDrag = { drag.floatValue = 0f; undo.add(segments.toList()); redo.clear(); stopAt = null; pause() }
                            Handle(Modifier.offset { IntOffset((segStart - hw).roundToInt(), with(density) { 26.dp.roundToPx() }) }, handleW, left = true, onStart = startDrag, onEnd = {}) { dx ->
                                val s = segments.getOrNull(selected) ?: return@Handle
                                // Доли миллисекунды копим, чтобы медленное движение пальца тоже сдвигало край.
                                drag.floatValue += dx / pxPerMs
                                val step = drag.floatValue.toLong(); drag.floatValue -= step
                                val ns = (s.first + step).coerceIn(0L, s.last - 300)
                                segments[selected] = ns..s.last
                                seekSrc(ns, selected)
                            }
                            Handle(
                                Modifier.offset { IntOffset((segStart + segW).roundToInt(), with(density) { 26.dp.roundToPx() }) }, handleW, left = false, onStart = startDrag,
                                onEnd = {
                                    // Отпустили конец — играем последние 2 секунды до него и останавливаемся на нём.
                                    segments.getOrNull(selected)?.let { s -> seekSrc(maxOf(s.first, s.last - 2000), selected); stopAt = s.last; play() }
                                },
                            ) { dx ->
                                val s = segments.getOrNull(selected) ?: return@Handle
                                drag.floatValue += dx / pxPerMs
                                val step = drag.floatValue.toLong(); drag.floatValue -= step
                                val ne = (s.last + step).coerceIn(s.first + 300, duration)
                                segments[selected] = s.first..ne
                                seekSrc(ne, selected)
                            }
                        }
                    }
                }
            }
            // Время выбранного куска цифрами.
            val sel = segments.getOrNull(selected)
            if (sel != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeField("Начало", sel.first, Modifier.weight(1f)) { ms ->
                    val s = segments.getOrNull(selected) ?: return@TimeField
                    commit { it[selected] = ms.coerceIn(0L, s.last - 300)..s.last }
                    stopAt = null; pause(); seekSrc(segments[selected].first, selected)
                }
                Spacer(Modifier.width(10.dp))
                TimeField("Конец", sel.last, Modifier.weight(1f)) { ms ->
                    val s = segments.getOrNull(selected) ?: return@TimeField
                    commit { it[selected] = s.first..ms.coerceIn(s.first + 300, duration) }
                    val ne = segments[selected].last
                    seekSrc(maxOf(s.first, ne - 2000), selected); stopAt = ne; play()
                }
                Spacer(Modifier.width(10.dp))
                Text("кусок ${selected + 1}/${segments.size}\n${clock(sel.last - sel.first)}", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
            }
            if (showSpeed) FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp -> Pill(speedText(sp), speed == sp) { speed = sp } }
            }
            // Панель инструментов (кремовая, как в редакторах видео).
            Row(
                Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(Cream).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                Tool(15, "Разрезать") {
                    val (i, src) = VideoCut.fromEdited(segments, playhead)
                    val s = segments.getOrNull(i) ?: return@Tool
                    if (src - s.first > 300 && s.last - src > 300) { commit { it[i] = s.first..src; it.add(i + 1, src..s.last) }; selected = i + 1 }
                }
                Tool(18, "Дублировать") {
                    val s = segments.getOrNull(selected) ?: return@Tool
                    commit { it.add(selected + 1, s) }; selected += 1
                }
                Tool(19, "Влево") {
                    if (selected > 0) { val i = selected; commit { val t = it[i]; it[i] = it[i - 1]; it[i - 1] = t }; selected = i - 1 }
                }
                Tool(20, "Вправо") {
                    if (selected < segments.lastIndex) { val i = selected; commit { val t = it[i]; it[i] = it[i + 1]; it[i + 1] = t }; selected = i + 1 }
                }
                Tool(if (speed < 1f) 16 else 17, "Скорость ${speedText(speed)}") { showSpeed = !showSpeed }
                Tool(21, "Удалить") {
                    if (segments.size > 1) { val i = selected; commit { it.removeAt(i) }; selected = i.coerceAtMost(segments.lastIndex); curSeg = 0; seekSrc(segments[0].first, 0) }
                }
            }
        }
    }
}

private val editorPlayers = java.util.WeakHashMap<VideoView, android.media.MediaPlayer>()

private fun clock(ms: Long): String { val t = (ms / 100).coerceAtLeast(0); return "%02d:%02d.%d".format(t / 600, (t / 10) % 60, t % 10) }
private fun clockShort(ms: Long): String { val s = ms / 1000; return if (ms % 1000 == 0L) "%d:%02d".format(s / 60, s % 60) else "%d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100) }
private fun speedText(s: Float) = (if (s == Math.floor(s.toDouble()).toFloat()) "${s.toInt()}" else s.toString().replace('.', ',')) + "×"

private fun videoAspect(path: String): Float? = runCatching {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(path)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: return@runCatching null
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: return@runCatching null
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) h / w else w / h
    } finally { runCatching { r.release() } }
}.getOrNull()?.coerceIn(.3f, 3f)

/** Кадры для ленты: примерно 40 штук по всей длине, маленькие. */
private fun frames(path: String, duration: Long): List<Pair<Long, ImageBitmap>> {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(path)
        val n = 40
        (0 until n).mapNotNull { i ->
            val t = duration * i / n
            val bmp = runCatching {
                if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 96, 160)
                else r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull() ?: return@mapNotNull null
            t to bmp.asImageBitmap()
        }
    } catch (_: Throwable) { emptyList() } finally { runCatching { r.release() } }
}

@Composable
private fun EditorIcon(n: Int, size: androidx.compose.ui.unit.Dp) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val key = "player/%02d".format(n)
    val img by produceState<ImageBitmap?>(null, key) { value = withContext(Dispatchers.IO) { Glyphs.load(ctx, key) } }
    img?.let { androidx.compose.foundation.Image(it, null, Modifier.size(size)) } ?: Box(Modifier.size(size))
}

@Composable
private fun Tool(icon: Int, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Panel), contentAlignment = Alignment.Center) { EditorIcon(icon, 26.dp) }
        Text(label, fontSize = 10.sp, color = Color(0xFF241C0D), maxLines = 1)
    }
}

@Composable
private fun TopIcon(sym: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(sym, color = if (enabled) Color.White else Color.White.copy(alpha = .3f), fontSize = 22.sp) }
}

@Composable
private fun ZoomButton(sym: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Cream.copy(alpha = .6f), RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(sym, color = Cream, fontSize = 18.sp)
    }
}

/** Ползунок края куска: тянется пальцем, жест не прокручивает ленту. */
@Composable
private fun Handle(modifier: Modifier, width: androidx.compose.ui.unit.Dp, left: Boolean, onStart: () -> Unit, onEnd: () -> Unit, onDrag: (Float) -> Unit) {
    // Жест живёт дольше одной перерисовки — берём свежие обработчики (масштаб ленты меняется).
    val start = androidx.compose.runtime.rememberUpdatedState(onStart)
    val end = androidx.compose.runtime.rememberUpdatedState(onEnd)
    val drag = androidx.compose.runtime.rememberUpdatedState(onDrag)
    Box(
        modifier.width(width).height(62.dp)
            .clip(RoundedCornerShape(topStart = if (left) 8.dp else 0.dp, bottomStart = if (left) 8.dp else 0.dp, topEnd = if (left) 0.dp else 8.dp, bottomEnd = if (left) 0.dp else 8.dp))
            .background(Cream)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragStart = { start.value() }, onDragEnd = { end.value() }, onDragCancel = { end.value() }) { change, dx -> change.consume(); drag.value(dx) }
            },
        contentAlignment = Alignment.Center,
    ) { Text(if (left) "‹" else "›", color = Color(0xFF241C0D), fontSize = 18.sp) }
}

/** Поле времени «м:сс.д»: впишите и нажмите «Готово» на клавиатуре — ползунок встанет туда сам. */
@Composable
private fun TimeField(label: String, ms: Long, modifier: Modifier, onSet: (Long) -> Unit) {
    var text by remember(ms) { mutableStateOf(clockShort((ms / 100) * 100)) }
    androidx.compose.material3.OutlinedTextField(
        value = text, onValueChange = { text = it.filter { c -> c.isDigit() || c == ':' || c == '.' || c == ',' } },
        label = { Text(label, color = Color.White.copy(alpha = .7f)) }, singleLine = true, modifier = modifier,
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { VideoCut.parseTime(text)?.let(onSet) }),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = Cream, unfocusedBorderColor = Cream.copy(alpha = .4f), cursorColor = Cream),
    )
}
