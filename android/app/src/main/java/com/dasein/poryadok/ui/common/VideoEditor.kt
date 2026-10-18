package com.dasein.poryadok.ui.common

import android.media.MediaMetadataRetriever
import android.os.Build
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dasein.poryadok.logic.VideoCut
import com.dasein.poryadok.logic.VideoCut.Clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val Cream = Color(0xFFFBDFAB)
private val Orange = Color(0xFFFC8C1E)
private val Panel = Color(0xFF26221C)
private val Ink = Color(0xFF241C0D)
private val Bg = Color(0xFF15120E)

/** Итог монтажа: исходные видео (первое — основное, остальные добавлены в монтаже), куски, кадр и звук. */
data class VideoEdit(val sources: List<String>, val clips: List<Clip>, val aspect: Float?, val mute: Boolean) {
    /** Видео, добавленные кнопкой «+», — временные копии; после сохранения их можно удалить. */
    val extras: List<String> get() = sources.drop(1)
}

private val ASPECTS: List<Pair<String, Float?>> = listOf("Исходный" to null, "9:16" to 9f / 16f, "1:1" to 1f, "4:5" to 0.8f, "16:9" to 16f / 9f)
private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

private enum class EditPanel { NONE, SPEED, FRAME }

private data class Snap(val clips: List<Clip>, val aspect: Float?, val mute: Boolean)

/**
 * Монтаж ролика — как в мобильных видеоредакторах (VidMa, CapCut, открытые OpenShot и LosslessCut):
 *  • бегунок стоит по центру, лента с кадрами и шкалой времени прокручивается пальцем — так и перематывается;
 *    двумя пальцами лента увеличивается (или кнопками «−/+»);
 *  • касание по куску выделяет его: белые ручки по краям обрезают начало и конец, над лентой — панель
 *    «дублировать, удалить до бегунка, разрезать, удалить после бегунка, удалить кусок»;
 *  • у каждого куска своя скорость 0,25×–3×; кадр 9:16 / 1:1 / 4:5 / 16:9 с обрезкой краёв; звук можно выключить;
 *  • «+» в конце ленты добавляет ещё одно видео; куски переставляются кнопками «Влево/Вправо»;
 *  • «Отменить/Вернуть» для любого шага. Отпустили ручку конца — играют последние 0,2 с до него.
 * Склейку и сжатие делает VideoCompress.editClips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoEditor(path: String, dir: String, addMode: Boolean, onCancel: () -> Unit, onSkip: () -> Unit, onDone: (VideoEdit) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val sources = remember { mutableStateListOf(path) }
    val durations = remember { mutableStateMapOf<Int, Long>() }
    val ratios = remember { mutableStateMapOf<Int, Float>() }
    val thumbs = remember { mutableStateMapOf<Int, List<Pair<Long, ImageBitmap>>>() }
    val clips = remember { mutableStateListOf<Clip>() }
    // Сведения о каждом видео: длина, пропорции, кадры для ленты.
    LaunchedEffect(sources.size) {
        sources.toList().forEachIndexed { i, p ->
            if (i in durations) return@forEachIndexed
            val d = withContext(Dispatchers.IO) { durationOf(p) }
            ratios[i] = withContext(Dispatchers.IO) { videoAspect(p) } ?: (9f / 16f)
            durations[i] = d
            if (i == 0 && clips.isEmpty() && d > 0) clips.add(Clip(0, 0, d))
            if (d > 0) launch { thumbs[i] = withContext(Dispatchers.IO) { frames(p, d) } }
        }
    }

    var aspect by remember { mutableStateOf<Float?>(null) }
    var mute by remember { mutableStateOf(false) }
    val undo = remember { mutableStateListOf<Snap>() }
    val redo = remember { mutableStateListOf<Snap>() }
    var selected by remember { mutableStateOf<Int?>(null) }
    fun snap() = Snap(clips.toList(), aspect, mute)
    fun setClips(l: List<Clip>) { clips.clear(); clips.addAll(l); selected = selected?.takeIf { it in clips.indices } }
    fun restore(s: Snap) { setClips(s.clips); aspect = s.aspect; mute = s.mute }
    fun commit(change: () -> Unit) { undo.add(snap()); redo.clear(); change() }

    // ---------- Превью ----------
    var view by remember { mutableStateOf<VideoView?>(null) }
    var loadedSrc by remember { mutableIntStateOf(0) }
    var pendingSeek by remember { mutableLongStateOf(-1L) }
    var playing by remember { mutableStateOf(false) }
    var cur by remember { mutableIntStateOf(0) }
    var srcPos by remember { mutableLongStateOf(0L) }
    /** Остановить превью на этом месте исходника текущего куска. */
    var stopAt by remember { mutableStateOf<Long?>(null) }

    fun applyParams() {
        val v = view ?: return
        val mp = editorPlayers[v] ?: return
        runCatching {
            mp.setVolume(if (mute) 0f else 1f, if (mute) 0f else 1f)
            val sp = clips.getOrNull(cur)?.speed ?: 1f
            if (Build.VERSION.SDK_INT >= 23 && editorSpeed[mp] != sp) {
                val was = v.isPlaying
                mp.playbackParams = mp.playbackParams.setSpeed(sp)
                if (!was) mp.pause()
                editorSpeed[mp] = sp
            }
        }
    }
    fun seek(i: Int, pos: Long) {
        val c = clips.getOrNull(i) ?: return
        cur = i
        srcPos = pos.coerceIn(c.start, c.end)
        val v = view ?: return
        if (c.src != loadedSrc) {
            // Кусок из другого видео — открываем его и встаём на нужное место, когда оно загрузится.
            loadedSrc = c.src
            pendingSeek = srcPos
            v.setVideoPath(sources[c.src])
        } else {
            v.seekTo(srcPos.toInt())
            applyParams()
        }
    }
    fun play() { playing = true; if (pendingSeek < 0) view?.start(); applyParams() }
    fun pause() { view?.pause(); playing = false }
    LaunchedEffect(mute) { applyParams() }

    val total = VideoCut.total(clips)
    val playhead = VideoCut.toEdited(clips, cur, srcPos)

    // ---------- Лента ----------
    val scroll = rememberScrollState()
    var viewW by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val baseLen = durations[0]?.takeIf { it > 0 } ?: 1L
    // Масштаб — от длины основного видео: при обрезке и добавлении кусков шкала не «плывёт».
    val pxPerMs = if (viewW > 0) viewW * 0.8f * zoom / baseLen else 0f
    val px by rememberUpdatedState(pxPerMs)
    var touching by remember { mutableStateOf(false) }
    var userScroll by remember { mutableStateOf(false) }
    var trimming by remember { mutableStateOf(false) }
    fun scrollToPlayhead() = scope.launch { if (px > 0f) scroll.scrollTo((VideoCut.toEdited(clips, cur, srcPos) * px).roundToInt()) }

    // Прокрутка ленты пальцем (и докат после неё) — это перемотка: в превью то, что под бегунком.
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value }.collect { v ->
            if (userScroll && !trimming && !playing && px > 0f && clips.isNotEmpty()) {
                val (i, s) = VideoCut.fromEdited(clips, (v / px).toLong())
                seek(i, s)
            }
        }
    }
    LaunchedEffect(scroll.isScrollInProgress, touching) { if (!scroll.isScrollInProgress && !touching) userScroll = false }
    // Новый масштаб — лента подстраивается, бегунок остаётся на том же месте видео.
    LaunchedEffect(zoom, viewW) { scrollToPlayhead() }

    // Проигрывание по кускам: дошли до конца куска — следующий, после последнего — по кругу. Лента едет следом.
    LaunchedEffect(view) {
        while (true) {
            val v = view
            val c = clips.getOrNull(cur)
            if (v != null && c != null && playing && c.src == loadedSrc && pendingSeek < 0) {
                val p = v.currentPosition.toLong()
                if (p >= c.start - 300) srcPos = p.coerceIn(c.start, c.end)
                val stop = stopAt
                if (stop != null && p >= stop - 40) { pause(); stopAt = null; seek(cur, stop) }
                else if (p >= c.end - 30) {
                    val next = if (cur + 1 > clips.lastIndex) 0 else cur + 1
                    seek(next, clips[next].start)
                }
                if (px > 0f && !touching) scroll.scrollTo((VideoCut.toEdited(clips, cur, srcPos) * px).roundToInt())
            }
            delay(40)
        }
    }

    // ---------- Добавить видео ----------
    var adding by remember { mutableStateOf(false) }
    val pickMore = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        adding = true
        io {
            val p = Media.importRaw(ctx, uri, dir)?.takeIf { File(it).length() > 0 }
            val d = p?.let { durationOf(it) } ?: 0L
            withContext(Dispatchers.Main) {
                adding = false
                if (p == null || d <= 0) {
                    p?.let { File(it).delete() }
                    toast(ctx, "Не удалось добавить видео")
                    return@withContext
                }
                sources.add(p)
                val src = sources.lastIndex
                val at = (selected?.plus(1) ?: clips.size).coerceIn(0, clips.size)
                commit { setClips(clips.toMutableList().also { it.add(at, Clip(src, 0, d)) }) }
                selected = at
            }
        }
    }
    fun dropExtras() { val extra = sources.drop(1); if (extra.isNotEmpty()) io { extra.forEach { File(it).delete() } } }
    fun cancel() { pause(); dropExtras(); onCancel() }

    var panel by remember { mutableStateOf(EditPanel.NONE) }
    fun split() {
        val r = VideoCut.split(clips, playhead)
        if (r == null) toast(ctx, "Бегунок у края куска — сдвиньте его") else { commit { setClips(r.first) }; selected = r.second }
    }

    Dialog(onDismissRequest = { cancel() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Отступ от кнопок Android с запасом — панели монтажа не должны их задевать.
        Column(Modifier.fillMaxSize().background(Bg).safeBars(withIme = true, extraBottom = 24.dp)) {
            // Верх: закрыть, отменить/вернуть, готово.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TopIcon("✕", "Закрыть") { cancel() }
                TopIcon("↶", "Отменить", enabled = undo.isNotEmpty()) { redo.add(snap()); restore(undo.removeAt(undo.lastIndex)) }
                TopIcon("↷", "Вернуть", enabled = redo.isNotEmpty()) { undo.add(snap()); restore(redo.removeAt(redo.lastIndex)) }
                Spacer(Modifier.weight(1f))
                val d0 = durations[0] ?: 0L
                val changed = aspect != null || mute || clips.size != 1 || clips.firstOrNull() != Clip(0, 0, d0)
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Cream).clickable {
                        if (clips.isEmpty()) return@clickable
                        pause()
                        if (!changed) { dropExtras(); onSkip() } else onDone(VideoEdit(sources.toList(), clips.toList(), aspect, mute))
                    }.padding(horizontal = 18.dp, vertical = 10.dp),
                ) { Text(btnLabel(if (changed) "Готово" else if (addMode) "Добавить" else "Закрыть"), color = Ink, fontWeight = FontWeight.SemiBold) }
            }
            // Превью с рамкой кадра (то, что вне рамки, обрежется).
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                val r = ratios[loadedSrc] ?: (9f / 16f)
                val boxW = maxWidth
                val boxH = maxHeight
                val fw = if (boxH.value > 0f && boxW / boxH > r) boxH * r else boxW
                val fh = if (boxH.value > 0f && boxW / boxH > r) boxH else boxW / r
                Box(Modifier.size(fw, fh).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
                    AndroidView(
                        factory = { c ->
                            VideoView(c).apply {
                                setVideoPath(path)
                                setOnPreparedListener { mp ->
                                    editorPlayers[this] = mp
                                    editorSpeed.remove(mp)
                                    val at = pendingSeek.takeIf { it >= 0 } ?: srcPos
                                    pendingSeek = -1
                                    seekTo(at.toInt().coerceAtLeast(1))
                                    view = this
                                    applyParams()
                                    if (playing) start()
                                }
                            }
                        },
                        onRelease = { editorPlayers.remove(it); it.stopPlayback() },
                        modifier = Modifier.fillMaxSize(),
                    )
                    val a = aspect
                    if (a != null) Canvas(Modifier.fillMaxSize()) {
                        // Рамка кадра по центру, снаружи — затемнение.
                        val wide = size.width / size.height > a
                        val cw = if (wide) size.height * a else size.width
                        val ch = if (wide) size.height else size.width / a
                        val l = (size.width - cw) / 2
                        val t = (size.height - ch) / 2
                        val shade = Color.Black.copy(alpha = .62f)
                        drawRect(shade, Offset.Zero, Size(size.width, t))
                        drawRect(shade, Offset(0f, t + ch), Size(size.width, size.height - t - ch))
                        drawRect(shade, Offset(0f, t), Size(l, ch))
                        drawRect(shade, Offset(l + cw, t), Size(size.width - l - cw, ch))
                        drawRect(Cream, Offset(l, t), Size(cw, ch), style = Stroke(width = 2.dp.toPx()))
                    }
                    if (mute) Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .5f)).padding(4.dp)) { EditorIcon(7, 20.dp) }
                }
            }
            // Время, пуск, масштаб.
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${clock(playhead)} / ${clock(total)}", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .12f)).clickable {
                    stopAt = null
                    if (playing) pause() else {
                        // В самом конце — с начала.
                        if (playhead >= total - 50 && clips.isNotEmpty()) seek(0, clips[0].start)
                        play()
                    }
                }, contentAlignment = Alignment.Center) { EditorIcon(if (playing) 1 else 0, 26.dp) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    ZoomButton("−") { zoom = (zoom / 1.6f).coerceAtLeast(0.5f) }
                    Text(" ${zoomText(zoom)} ", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                    ZoomButton("+") { zoom = (zoom * 1.6f).coerceAtMost(40f) }
                }
            }
            // Лента: шкала времени, куски с кадрами, ручки выбранного куска, «+»; бегунок неподвижен по центру.
            Box(
                Modifier.fillMaxWidth().height(RULER_H + STRIP_H + 16.dp).onSizeChanged { viewW = it.width }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            touching = true
                            userScroll = true
                            stopAt = null
                            if (playing) pause()
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                if (ev.changes.none { it.pressed }) break
                                // Два пальца — масштаб ленты (а не прокрутка).
                                if (ev.changes.count { it.pressed } >= 2) {
                                    val z = ev.calculateZoom()
                                    if (z != 1f) zoom = (zoom * z).coerceIn(0.5f, 40f)
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                            touching = false
                        }
                    },
            ) {
                val side = viewW / 2f
                val sideDp = with(density) { side.toDp() }
                val totalPx = total * pxPerMs
                val contentW = with(density) { (side * 2 + totalPx).toDp() } + 68.dp
                val stripTop = RULER_H + 6.dp
                Box(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                    Box(Modifier.width(contentW).fillMaxHeight()) {
                        // Шкала времени.
                        Canvas(Modifier.offset(x = sideDp).width(with(density) { totalPx.toDp() }).height(RULER_H)) {
                            if (pxPerMs <= 0f) return@Canvas
                            val step = VideoCut.tickStep(pxPerMs)
                            val paint = android.graphics.Paint().apply { color = android.graphics.Color.argb(170, 255, 255, 255); textSize = 10.sp.toPx(); isAntiAlias = true }
                            var t = 0L
                            while (t <= total) {
                                val x = t * pxPerMs
                                drawLine(Color.White.copy(alpha = .45f), Offset(x, size.height - 6.dp.toPx()), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                                drawContext.canvas.nativeCanvas.drawText(clockShort(t), x + 3.dp.toPx(), size.height - 8.dp.toPx(), paint)
                                // Точка посередине между делениями.
                                if (t + step / 2 <= total) drawCircle(Color.White.copy(alpha = .3f), 1.5.dp.toPx(), Offset(x + step * pxPerMs / 2, size.height - 3.dp.toPx()))
                                t += step
                            }
                        }
                        // Куски.
                        clips.forEachIndexed { i, c ->
                            val x = side + VideoCut.offsetOf(clips, i) * pxPerMs
                            val w = c.len * pxPerMs
                            ClipTile(
                                c, thumbs[c.src].orEmpty(), pxPerMs, i == selected,
                                Modifier.offset { IntOffset(x.roundToInt(), stripTop.roundToPx()) }.width(with(density) { w.toDp() }).height(STRIP_H),
                            ) { selected = if (selected == i) null else i; panel = EditPanel.NONE }
                        }
                        // «+» — добавить ещё видео (после выбранного куска или в конец).
                        Box(
                            Modifier.offset { IntOffset((side + totalPx).roundToInt() + 10.dp.roundToPx(), (stripTop + (STRIP_H - 44.dp) / 2).roundToPx()) }
                                .size(44.dp).clip(RoundedCornerShape(10.dp)).background(Cream)
                                .clickable(enabled = !adding) { pickMore.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                            contentAlignment = Alignment.Center,
                        ) { Text(if (adding) "…" else "+", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                        // Ручки выбранного куска — внутри ленты и с запасом по ширине: за них легко взяться.
                        val s = selected
                        val sc = s?.let { clips.getOrNull(it) }
                        if (s != null && sc != null && pxPerMs > 0f) {
                            val left = side + VideoCut.offsetOf(clips, s) * pxPerMs
                            val right = left + sc.len * pxPerMs
                            val acc = remember { mutableFloatStateOf(0f) }
                            fun begin() { acc.floatValue = 0f; undo.add(snap()); redo.clear(); trimming = true; stopAt = null; pause() }
                            Handle(
                                Modifier.offset { IntOffset((left - HANDLE_W.toPx()).roundToInt(), stripTop.roundToPx()) }, left = true,
                                onStart = { begin() },
                                onEnd = {
                                    trimming = false
                                    clips.getOrNull(s)?.let { c -> seek(s, c.start); scrollToPlayhead() }
                                },
                            ) { dx ->
                                val c = clips.getOrNull(s) ?: return@Handle
                                acc.floatValue += dx / px * c.speed
                                val step = acc.floatValue.toLong(); acc.floatValue -= step
                                val ns = (c.start + step).coerceIn(0L, c.end - VideoCut.MIN_MS)
                                if (ns == c.start) return@Handle
                                clips[s] = c.copy(start = ns)
                                // Ручка идёт за пальцем, а всё правее остаётся на месте.
                                scroll.dispatchRawDelta(-((ns - c.start) / c.speed * px))
                                seek(s, ns)
                            }
                            Handle(
                                Modifier.offset { IntOffset(right.roundToInt(), stripTop.roundToPx()) }, left = false,
                                onStart = { begin() },
                                onEnd = {
                                    trimming = false
                                    // Отпустили конец — играем последние 0,2 с до него и останавливаемся на нём.
                                    clips.getOrNull(s)?.let { c ->
                                        seek(s, maxOf(c.start, c.end - (END_PREVIEW_MS * c.speed).toLong()))
                                        scrollToPlayhead()
                                        stopAt = c.end
                                        play()
                                    }
                                },
                            ) { dx ->
                                val c = clips.getOrNull(s) ?: return@Handle
                                acc.floatValue += dx / px * c.speed
                                val step = acc.floatValue.toLong(); acc.floatValue -= step
                                val ne = (c.end + step).coerceIn(c.start + VideoCut.MIN_MS, durations[c.src] ?: c.end)
                                if (ne == c.end) return@Handle
                                clips[s] = c.copy(end = ne)
                                seek(s, ne)
                            }
                        }
                    }
                }
                // Бегунок — по центру, неподвижный.
                Canvas(Modifier.fillMaxHeight().width(16.dp).align(Alignment.TopCenter)) {
                    val cx = size.width / 2
                    drawLine(Color.White, Offset(cx, 8.dp.toPx()), Offset(cx, size.height), strokeWidth = 2.5.dp.toPx())
                    drawCircle(Color.White, 6.dp.toPx(), Offset(cx, 7.dp.toPx()))
                    drawCircle(Orange, 2.5.dp.toPx(), Offset(cx, 7.dp.toPx()))
                }
            }
            // Выбранный кусок: время цифрами (ручки встанут туда сами) и плавающая панель действий.
            val sel = selected?.let { clips.getOrNull(it) }
            val si = selected ?: 0
            if (sel != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TimeField("Начало", sel.start, Modifier.weight(1f)) { ms ->
                        val c = clips.getOrNull(si) ?: return@TimeField
                        commit { clips[si] = c.copy(start = ms.coerceIn(0L, c.end - VideoCut.MIN_MS)) }
                        stopAt = null; pause(); seek(si, clips[si].start); scrollToPlayhead()
                    }
                    Spacer(Modifier.width(8.dp))
                    TimeField("Конец", sel.end, Modifier.weight(1f)) { ms ->
                        val c = clips.getOrNull(si) ?: return@TimeField
                        commit { clips[si] = c.copy(end = ms.coerceIn(c.start + VideoCut.MIN_MS, durations[c.src] ?: c.end)) }
                        val nc = clips[si]
                        seek(si, maxOf(nc.start, nc.end - (END_PREVIEW_MS * nc.speed).toLong())); scrollToPlayhead(); stopAt = nc.end; play()
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("кусок ${si + 1}/${clips.size}\n${clock(sel.len)}", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                }
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Cream).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniTool(18, "Дублировать") { commit { setClips(VideoCut.duplicate(clips, si)) }; selected = si + 1 }
                    MiniTool(22, "Удалить до бегунка") {
                        val i = VideoCut.fromEdited(clips, playhead).first
                        val l = VideoCut.trimBefore(clips, playhead)
                        if (l == null) toast(ctx, "Поставьте бегунок внутрь куска")
                        else { commit { setClips(l) }; seek(i, clips[i].start); scrollToPlayhead() }
                    }
                    MiniTool(15, "Разрезать") { split() }
                    MiniTool(23, "Удалить после бегунка") {
                        val l = VideoCut.trimAfter(clips, playhead)
                        if (l == null) toast(ctx, "Поставьте бегунок внутрь куска") else commit { setClips(l) }
                    }
                    MiniTool(21, "Удалить кусок") {
                        if (clips.size <= 1) toast(ctx, "Это единственный кусок")
                        else {
                            commit { setClips(VideoCut.delete(clips, si)) }
                            selected = null
                            val i = si.coerceAtMost(clips.lastIndex)
                            seek(i, clips[i].start); scrollToPlayhead()
                        }
                    }
                }
            }
            // Панели скорости и кадра.
            when (panel) {
                EditPanel.SPEED -> FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(if (sel != null) "Скорость куска:" else "Скорость всего видео:", color = Color.White.copy(alpha = .75f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    val curSpeed = sel?.speed ?: clips.map { it.speed }.distinct().singleOrNull()
                    SPEEDS.forEach { v ->
                        Pill(speedText(v), curSpeed == v) {
                            val only = selected
                            commit { setClips(clips.mapIndexed { i, c -> if (only == null || i == only) c.copy(speed = v) else c }) }
                            applyParams(); scrollToPlayhead()
                        }
                    }
                }
                EditPanel.FRAME -> FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Кадр:", color = Color.White.copy(alpha = .75f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    ASPECTS.forEach { (label, v) -> Pill(label, aspect == v) { if (aspect != v) commit { aspect = v } } }
                }
                EditPanel.NONE -> {}
            }
            // Нижняя панель инструментов.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Panel).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                Tool(15, "Разрезать") { split() }
                Tool(if ((sel?.speed ?: 1f) < 1f) 16 else 17, "Скорость", panel == EditPanel.SPEED) { panel = if (panel == EditPanel.SPEED) EditPanel.NONE else EditPanel.SPEED }
                Tool(24, ASPECTS.firstOrNull { it.second == aspect && aspect != null }?.first ?: "Кадр", panel == EditPanel.FRAME) { panel = if (panel == EditPanel.FRAME) EditPanel.NONE else EditPanel.FRAME }
                Tool(if (mute) 7 else 6, if (mute) "Без звука" else "Звук", mute) { commit { mute = !mute } }
                Tool(19, "Влево") {
                    val i = selected
                    if (i == null) toast(ctx, "Выберите кусок на ленте") else if (i > 0) { commit { setClips(VideoCut.move(clips, i, -1)) }; selected = i - 1 }
                }
                Tool(20, "Вправо") {
                    val i = selected
                    if (i == null) toast(ctx, "Выберите кусок на ленте") else if (i < clips.lastIndex) { commit { setClips(VideoCut.move(clips, i, 1)) }; selected = i + 1 }
                }
            }
        }
    }
}

private fun toast(ctx: android.content.Context, text: String) = android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()

private val RULER_H = 26.dp
private val STRIP_H = 60.dp
private val HANDLE_W = 22.dp

/** Сколько проиграть перед концом куска, когда отпускают его ручку. */
private const val END_PREVIEW_MS = 200L

private val editorPlayers = java.util.WeakHashMap<VideoView, android.media.MediaPlayer>()
private val editorSpeed = java.util.WeakHashMap<android.media.MediaPlayer, Float>()

private fun clock(ms: Long): String { val t = (ms / 100).coerceAtLeast(0); return "%02d:%02d.%d".format(t / 600, (t / 10) % 60, t % 10) }
private fun clockShort(ms: Long): String { val s = ms / 1000; return if (ms % 1000 == 0L) "%d:%02d".format(s / 60, s % 60) else "%d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100) }
private fun speedText(s: Float) = (if (s == Math.floor(s.toDouble()).toFloat()) "${s.toInt()}" else s.toString().replace('.', ',')) + "×"
private fun zoomText(z: Float) = (if (z < 10) String.format(java.util.Locale.US, "%.1f", z) else z.roundToInt().toString()).replace('.', ',') + "×"
private fun secText(ms: Long) = String.format(java.util.Locale.US, "%.1f", ms / 1000f).replace('.', ',') + "s"

private fun durationOf(path: String): Long = runCatching {
    val r = MediaMetadataRetriever()
    try { r.setDataSource(path); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { runCatching { r.release() } }
}.getOrDefault(0L)

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

/** Кусок на ленте: кадры, длительность (и скорость), рамка выделения. Касание — выделить. */
@Composable
private fun ClipTile(c: Clip, thumbs: List<Pair<Long, ImageBitmap>>, pxPerMs: Float, selected: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    Box(modifier.pointerInput(Unit) { detectTapGestures { tap() } }) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            val tileW = h * 0.62f
            var x = 0f
            while (x < size.width) {
                val src = c.start + ((x + tileW / 2) / pxPerMs.coerceAtLeast(1e-6f) * c.speed).toLong()
                val img = thumbs.minByOrNull { kotlin.math.abs(it.first - src) }?.second
                val tw = minOf(tileW, size.width - x)
                if (img != null && tw > 1f) {
                    val sw = (img.width * (tw / tileW)).toInt().coerceAtLeast(1)
                    drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(sw, img.height), dstOffset = IntOffset(x.toInt(), 0), dstSize = IntSize(tw.toInt().coerceAtLeast(1), h.toInt()))
                } else drawRect(Color(0xFF3A342B), Offset(x, 0f), Size(tw, h))
                x += tileW
            }
            // Стык с соседним куском.
            drawLine(Bg, Offset(0f, 0f), Offset(0f, h), strokeWidth = 2.dp.toPx())
            if (selected) drawRect(Cream, Offset.Zero, size, style = Stroke(width = 3.dp.toPx()))
        }
        Text(
            secText(c.len) + if (c.speed != 1f) " · ${speedText(c.speed)}" else "",
            color = Color.White, fontSize = 9.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(3.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 3.dp),
        )
    }
}

@Composable
private fun EditorIcon(n: Int, size: Dp) {
    val ctx = LocalContext.current
    val key = "player/%02d".format(n)
    val img by produceState<ImageBitmap?>(null, key) { value = withContext(Dispatchers.IO) { Glyphs.load(ctx, key) } }
    img?.let { androidx.compose.foundation.Image(it, null, Modifier.size(size)) } ?: Box(Modifier.size(size))
}

/** Кнопка нижней панели: иконка и подпись. [active] — включено (панель открыта, звук выключен…). */
@Composable
private fun Tool(icon: Int, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (active) Cream.copy(alpha = .25f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { EditorIcon(icon, 26.dp) }
        Text(label, fontSize = 10.sp, color = if (active) Cream else Color.White.copy(alpha = .85f), maxLines = 1)
    }
}

/** Кнопка плавающей (кремовой) панели выбранного куска. */
@Composable
private fun MiniTool(icon: Int, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Panel), contentAlignment = Alignment.Center) { EditorIcon(icon, 26.dp) }
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
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Cream.copy(alpha = .6f), RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(sym, color = Cream, fontSize = 18.sp)
    }
}

/** Ручка края куска: белая, с запасом по ширине; тянется пальцем, лента при этом не прокручивается. */
@Composable
private fun Handle(modifier: Modifier, left: Boolean, onStart: () -> Unit, onEnd: () -> Unit, onDrag: (Float) -> Unit) {
    // Жест живёт дольше одной перерисовки — берём свежие обработчики (масштаб ленты меняется).
    val start by rememberUpdatedState(onStart)
    val end by rememberUpdatedState(onEnd)
    val drag by rememberUpdatedState(onDrag)
    Box(
        modifier.width(HANDLE_W).height(STRIP_H)
            .clip(RoundedCornerShape(topStart = if (left) 8.dp else 0.dp, bottomStart = if (left) 8.dp else 0.dp, topEnd = if (left) 0.dp else 8.dp, bottomEnd = if (left) 0.dp else 8.dp))
            .background(Color.White)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragStart = { start() }, onDragEnd = { end() }, onDragCancel = { end() }) { change, dx -> change.consume(); drag(dx) }
            },
        contentAlignment = Alignment.Center,
    ) { Text(if (left) "‹" else "›", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}

/** Поле времени «м:сс.д»: впишите и нажмите «Готово» на клавиатуре — ручка встанет туда сама. */
@Composable
private fun TimeField(label: String, ms: Long, modifier: Modifier, onSet: (Long) -> Unit) {
    var text by remember(ms) { mutableStateOf(clockShort((ms / 100) * 100)) }
    androidx.compose.material3.OutlinedTextField(
        value = text, onValueChange = { text = it.filter { c -> c.isDigit() || c == ':' || c == '.' || c == ',' } },
        label = { Text(label, color = Color.White.copy(alpha = .7f)) }, singleLine = true, modifier = modifier,
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { VideoCut.parseTime(text)?.let(onSet) }),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = Cream, unfocusedBorderColor = Cream.copy(alpha = .4f), cursorColor = Cream),
    )
}
