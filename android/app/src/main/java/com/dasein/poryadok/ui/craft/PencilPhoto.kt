package com.dasein.poryadok.ui.craft

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dasein.poryadok.logic.CraftPattern
import com.dasein.poryadok.logic.PaintByNumbers
import com.dasein.poryadok.logic.StoneColors
import com.dasein.poryadok.system.CraftStore
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private class PencilShot(val bmp: Bitmap, val px: IntArray, val paper: Int?)

private fun sw(rgb: Int) = Color(rgb or (0xFF shl 24))

/** Ближайший карандаш каталога — подсказка, на какой номер похож цвет. */
private fun nearestPencil(rgb: Int, medium: PaintByNumbers.Medium): PaintByNumbers.Pencil? {
    val l = CraftPattern.lab(rgb)
    return medium.catalog.minByOrNull { StoneColors.deltaE(l, CraftPattern.lab(it.rgb)) }
}

/**
 * Цвет карандаша по фото: снять камерой или взять фото из галереи, затем пипеткой («пинцетом») коснуться штриха
 * или корпуса карандаша. Пипетку можно вести пальцем — она стоит чуть выше пальца, чтобы было видно, что берём.
 * [onAdd] — выбранный цвет (дальше открывается окно с названием).
 */
@Composable
internal fun PencilPhotoPicker(medium: PaintByNumbers.Medium = PaintByNumbers.Medium.PENCIL, onAdd: (Int) -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val lift = with(LocalDensity.current) { 56.dp.toPx() }
    var shot by remember { mutableStateOf<PencilShot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var color by remember { mutableStateOf<Int?>(null) }
    var balance by remember { mutableStateOf(true) }
    var radius by remember { mutableStateOf(3f) }

    fun load(uri: Uri) {
        busy = true; error = null
        scope.launch {
            val s = withContext(Dispatchers.IO) {
                runCatching {
                    val b = CraftStore.decode(ctx, uri, 1200) ?: return@runCatching null
                    val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                    PencilShot(b, px, StoneColors.paper(px))
                }.getOrNull()
            }
            busy = false
            if (s == null) error = "Не удалось открыть фото." else { shot = s; color = null }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(::load) }
    val camFile = remember { File(File(ctx.cacheDir, "share").apply { mkdirs() }, "pencils.jpg") }
    val camUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", camFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) load(camUri) }

    Column {
        Text(
            when (medium) {
                PaintByNumbers.Medium.PENCIL -> "Сфотографируйте карандаш или штрих на белой бумаге"
                PaintByNumbers.Medium.MARKER -> "Сфотографируйте выкрас маркера (закрашенный квадратик) на белой бумаге"
                PaintByNumbers.Medium.PAINT -> "Сфотографируйте мазок или выкрас краски на белой бумаге"
                PaintByNumbers.Medium.WATERCOLOR_PENCIL -> "Заштрихуйте квадратик, размойте влажной кистью и сфотографируйте на белой бумаге"
                PaintByNumbers.Medium.ALCOHOL_MARKER -> "Сфотографируйте выкрас маркера в один слой на белой бумаге"
                PaintByNumbers.Medium.WATERCOLOR_PAINT -> "Сделайте выкрас разбавленной краской на белой бумаге, дайте высохнуть и сфотографируйте"
            } + " при дневном свете без вспышки — и возьмите цвет пипеткой. Двумя пальцами фото можно увеличить.",
            fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { runCatching { camera.launch(camUri) } }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(btnLabel("📷 По камере")) }
            OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(btnLabel("🖼 Пипеткой по фото"))
            }
        }
        if (busy) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(btnLabel("  Открываю фото…"), fontSize = 13.sp)
        }
        error?.let { Text(it, color = extra.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }

        shot?.let { s ->
            val boxH = (LocalConfiguration.current.screenHeightDp / 3).dp
            var zoom by remember(s) { mutableFloatStateOf(1f) }
            var pan by remember(s) { mutableStateOf(Offset.Zero) }
            /** Точка пипетки — в долях картинки (0..1), чтобы оставаться на месте при масштабе. */
            var at by remember(s) { mutableStateOf<Offset?>(null) }
            var boxSize by remember { mutableStateOf(IntSize.Zero) }
            // Где лежит картинка внутри рамки (вписана целиком, по центру).
            fun fit(): Rect {
                val bw = boxSize.width.toFloat(); val bh = boxSize.height.toFloat()
                val k = minOf(bw / s.bmp.width, bh / s.bmp.height)
                val w = s.bmp.width * k; val h = s.bmp.height * k
                return Rect((bw - w) / 2, (bh - h) / 2, (bw + w) / 2, (bh + h) / 2)
            }
            fun center() = Offset(boxSize.width / 2f, boxSize.height / 2f)
            fun toImage(p: Offset): Offset? {
                val q = (p - center() - pan) / zoom + center()
                val r = fit()
                if (r.width <= 0f) return null
                return Offset(((q.x - r.left) / r.width).coerceIn(0f, 1f), ((q.y - r.top) / r.height).coerceIn(0f, 1f))
            }
            fun toScreen(f: Offset): Offset {
                val r = fit()
                val q = Offset(r.left + f.x * r.width, r.top + f.y * r.height)
                return (q - center()) * zoom + center() + pan
            }
            fun sampleAt(p: Offset) {
                val f = toImage(p) ?: return
                val x = (f.x * s.bmp.width).roundToInt().coerceIn(0, s.bmp.width - 1)
                val y = (f.y * s.bmp.height).roundToInt().coerceIn(0, s.bmp.height - 1)
                // Размер пипетки — в точках экрана: при увеличении берётся меньший участок фото.
                val rad = maxOf(1, (radius * 2.5f / zoom * s.bmp.width / maxOf(1f, fit().width)).roundToInt())
                at = f
                color = StoneColors.sample(s.px, s.bmp.width, s.bmp.height, x, y, rad, if (balance) s.paper else null)
            }
            Text(
                "Коснитесь нужного места или ведите пальцем — пипетка над пальцем покажет цвет. Двумя пальцами — увеличить и сдвинуть.",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            Box(
                Modifier.fillMaxWidth().height(boxH).clip(RoundedCornerShape(12.dp)).background(extra.card)
                    .onSizeChanged { boxSize = it }
                    .pointerInput(s, balance, radius) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var multi = false
                            var moved = false
                            var last = down.position
                            while (true) {
                                val ev = awaitPointerEvent()
                                val pressed = ev.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    multi = true
                                    val z = ev.calculateZoom()
                                    val c = ev.calculateCentroid(useCurrent = true)
                                    val nz = (zoom * z).coerceIn(1f, 8f)
                                    // Масштаб вокруг точки между пальцами + сдвиг.
                                    val k = nz / zoom
                                    pan = (pan + center() - c) * k + c - center() + ev.calculatePan()
                                    zoom = nz
                                    if (zoom == 1f) pan = Offset.Zero
                                    ev.changes.forEach { it.consume() }
                                } else if (!multi) {
                                    val ch = pressed.first()
                                    if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                    if (moved) { last = ch.position; sampleAt(Offset(ch.position.x, ch.position.y - lift)); ch.consume() }
                                } else ev.changes.forEach { it.consume() }
                            }
                            if (!multi && !moved) sampleAt(last)
                        }
                    },
            ) {
                Image(
                    s.bmp.asImageBitmap(), null,
                    Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
                    contentScale = ContentScale.Fit,
                )
                val f = at; val c = color
                if (f != null && c != null) Canvas(Modifier.fillMaxSize()) {
                    val p = toScreen(f)
                    drawCircle(Color.Black.copy(alpha = .55f), 17.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                    drawCircle(Color.White, 15.dp.toPx(), p, style = Stroke(3.dp.toPx()))
                    drawCircle(sw(c), 12.dp.toPx(), p)
                    drawCircle(Color.White, 1.5.dp.toPx(), p)
                }
                if (zoom > 1f) Text(
                    "×${"%.1f".format(zoom)}  ·  сбросить", fontSize = 11.sp, color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .5f))
                        .clickable { zoom = 1f; pan = Offset.Zero }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(btnLabel("Размер пипетки"), fontSize = 12.sp)
                Slider(radius, { radius = it }, valueRange = 1f..10f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(btnLabel("Поправка по белой бумаге"), fontSize = 13.sp)
                    Text(
                        if (s.paper != null) "Белый лист найден — цвета выравниваются под дневной свет" else "Белый лист не найден — цвет берётся как есть",
                        fontSize = 11.sp, color = extra.dim,
                    )
                }
                Switch(balance, { balance = it; color = null; at = null }, enabled = s.paper != null)
            }
            color?.let { c ->
                val near = remember(c, medium) { nearestPencil(c, medium) }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(sw(c)).border(1.dp, extra.line, RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("#%06X".format(c and 0xFFFFFF), fontSize = 14.sp)
                        near?.let { Text("Похож на № ${it.code} · ${it.name}", fontSize = 12.sp, color = extra.dim) }
                    }
                    TextButton(onClick = { onAdd(c) }) { Text(btnLabel("+ Добавить")) }
                }
            }
            TextButton(onClick = { shot = null; color = null }) { Text(btnLabel("Убрать фото")) }
        }
    }
}
