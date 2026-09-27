package com.dasein.poryadok.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Кадрирование картинок: исходник хранится рядом, чтобы кадр можно было поменять позже без потери краёв. */
object Crop {
    /** Путь исходника для кадрированного файла «…/img_1.jpg» → «…/img_1_orig.jpg». */
    fun originalOf(path: String): String = path.substringBeforeLast('.') + "_orig." + path.substringAfterLast('.', "jpg")

    fun sourceFor(path: String): String = originalOf(path).takeIf { File(it).exists() } ?: path

    /**
     * Вырезает область [rect] (в пикселях исходника) и сохраняет квадрат/прямоугольник шириной [outW].
     * PNG — для иконок (сохраняется прозрачность), JPEG — для фото.
     */
    fun save(ctx: Context, src: Bitmap, rect: Rect, outW: Int, aspect: Float, dir: String, png: Boolean, original: String?): String? = runCatching {
        val outH = (outW / aspect).toInt()
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (!png) c.drawColor(android.graphics.Color.WHITE)
        val m = Matrix().apply {
            postTranslate(-rect.left, -rect.top)
            postScale(outW / rect.width, outH / rect.height)
        }
        c.drawBitmap(src, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val folder = File(ctx.filesDir, dir).apply { mkdirs() }
        val f = File(folder, "img_${System.currentTimeMillis()}." + if (png) "png" else "jpg")
        f.outputStream().use { out.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it) }
        // Исходник сохраняем рядом, чтобы потом можно было перекадрировать.
        if (original != null) File(original).takeIf { it.exists() }?.let { o ->
            o.copyTo(File(originalOf(f.absolutePath)), overwrite = true)
            if (o.parentFile?.name == "src") o.delete()
        }
        f.absolutePath
    }.getOrNull()
}

/**
 * Редактор превью: двигайте и масштабируйте картинку пальцами внутри рамки.
 * [source] — путь к файлу-исходнику. [aspect] — ширина / высота рамки. [round] — круглая рамка для иконок.
 */
@Composable
fun CropDialog(
    source: String,
    aspect: Float,
    dir: String,
    round: Boolean = false,
    png: Boolean = false,
    outW: Int = 900,
    title: String = "Кадр превью",
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source) { bmp = withContext(Dispatchers.IO) { Images.decode(source, 2048) } }
    var scale by remember { mutableFloatStateOf(0f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    var saving by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xF0101010)).systemBarsPadding().padding(16.dp)) {
            Text(title, color = Color.White, fontSize = 18.sp)
            Text("Двигайте и увеличивайте картинку двумя пальцами — в превью попадёт то, что внутри рамки.", color = Color(0xFFBBBBBB), fontSize = 12.sp)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
                val density = LocalDensity.current
                val areaW = with(density) { maxWidth.toPx() }
                val areaH = with(density) { maxHeight.toPx() }
                // Рамка: как можно больше, с отступом, нужной пропорции.
                val usableH = areaH - with(density) { 72.dp.toPx() }
                val fw = min(areaW * .92f, usableH * .92f * aspect)
                val fh = fw / aspect
                val frame = Rect(Offset((areaW - fw) / 2, (usableH - fh) / 2), Size(fw, fh))
                val b = bmp
                if (b == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val minScale = max(fw / b.width, fh / b.height)
                    fun clamp() {
                        scale = scale.coerceIn(minScale, minScale * 6)
                        ox = ox.coerceIn(frame.right - b.width * scale, frame.left)
                        oy = oy.coerceIn(frame.bottom - b.height * scale, frame.top)
                    }
                    fun center() {
                        scale = minScale
                        ox = frame.center.x - b.width * scale / 2
                        oy = frame.center.y - b.height * scale / 2
                    }
                    LaunchedEffect(b, fw, fh) { if (scale == 0f) center() else clamp() }
                    val image = remember(b) { b.asImageBitmap() }
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(b, frame) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(minScale, minScale * 6)
                                val k = newScale / scale
                                ox = centroid.x - (centroid.x - ox) * k + pan.x
                                oy = centroid.y - (centroid.y - oy) * k + pan.y
                                scale = newScale
                                clamp()
                            }
                        },
                    ) {
                        drawImage(
                            image, dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                            dstSize = IntSize((b.width * scale).toInt(), (b.height * scale).toInt()),
                        )
                        // Затемнение вне рамки.
                        val hole = Path().apply {
                            if (round) addOval(frame) else addRoundRect(androidx.compose.ui.geometry.RoundRect(frame, CornerRadius(12.dp.toPx())))
                        }
                        clipPath(hole, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = .6f)) }
                        if (round) drawOval(Color.White, frame.topLeft, frame.size, style = Stroke(2.dp.toPx()))
                        else drawRoundRect(Color.White, frame.topLeft, frame.size, CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx()))
                        // Сетка третей помогает поставить объект по центру.
                        for (k in 1..2) {
                            val x = frame.left + frame.width * k / 3
                            val y = frame.top + frame.height * k / 3
                            drawLine(Color.White.copy(alpha = .35f), Offset(x, frame.top), Offset(x, frame.bottom), 1f)
                            drawLine(Color.White.copy(alpha = .35f), Offset(frame.left, y), Offset(frame.right, y), 1f)
                        }
                    }
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss, Modifier.weight(1f)) { Text("Отмена", color = Color.White) }
                        OutlinedButton(onClick = { center() }, Modifier.weight(1f)) { Text("По центру", color = Color.White) }
                        Button(
                            onClick = {
                                if (saving) return@Button
                                saving = true
                                val rect = Rect((frame.left - ox) / scale, (frame.top - oy) / scale, (frame.right - ox) / scale, (frame.bottom - oy) / scale)
                                scope.launch {
                                    val path = withContext(Dispatchers.IO) { Crop.save(ctx, b, rect, outW, aspect, dir, png, source) }
                                    saving = false
                                    if (path != null) onDone(path)
                                }
                            },
                            Modifier.weight(1f),
                        ) { Text(if (saving) "…" else "Готово") }
                    }
                }
            }
        }
    }
}

/** Выбор картинки из галереи и сразу — кадрирование. Возвращает путь к готовому превью. */
@Composable
fun rememberImagePickerWithCrop(
    aspect: Float,
    dir: String,
    round: Boolean = false,
    png: Boolean = false,
    onDone: (String) -> Unit,
): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch { source = Images.importUri(ctx, uri, "$dir/src") }
    }
    source?.let { src ->
        CropDialog(src, aspect, dir, round, png, onDismiss = { source = null }) { path -> source = null; onDone(path) }
    }
    return { launcher.launch("image/*") }
}
