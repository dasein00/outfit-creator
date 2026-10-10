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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.dasein.poryadok.logic.CraftPattern
import com.dasein.poryadok.logic.StoneColors
import com.dasein.poryadok.system.CraftStore
import com.dasein.poryadok.system.MyStone
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private fun swatch(rgb: Int) = Color(rgb or (0xFF shl 24))
private fun hex(rgb: Int) = "#%06X".format(rgb and 0xFFFFFF)

/** Фото страз, уменьшенное для разбора: пиксели и цвет бумаги. */
private class Shot(val bmp: Bitmap, val px: IntArray, val paper: Int?)

/**
 * «Мои стразы»: фото всех страз на белой бумаге → цвета определяются сами; их можно поправить, удалить,
 * добавить вручную или касанием по фото. Потом схема собирается только из этих цветов.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CraftMineScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val ver = CraftStore.mineVersion.intValue
    val stones = remember(ver) { CraftStore.mine(ctx) }
    var shot by remember { mutableStateOf<Shot?>(null) }
    var found by remember { mutableStateOf<List<StoneColors.Found>>(emptyList()) }
    var picked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var sens by remember { mutableFloatStateOf(9f) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<MyStone?>(null) }
    var tapped by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
    var clearAll by remember { mutableStateOf(false) }

    fun detect() {
        val s = shot ?: return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.Default) { StoneColors.detect(s.px, s.bmp.width, s.bmp.height, sens.toDouble()) }
            found = r
            // Сразу отмечены новые цвета; те, что уже есть в «Моих стразах», — нет.
            picked = r.indices.filter { i ->
                val l = CraftPattern.lab(r[i].rgb)
                stones.none { StoneColors.deltaE(CraftPattern.lab(it.rgb), l) < 4 }
            }.toSet()
            busy = false
            message = if (r.isEmpty()) "Стразы не найдены. Нужен белый лист, ровный свет и стразы без упаковки." else null
        }
    }

    fun load(uri: Uri) {
        busy = true
        scope.launch {
            val s = withContext(Dispatchers.IO) {
                runCatching {
                    val b = CraftStore.decode(ctx, uri, 900) ?: return@runCatching null
                    val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                    Shot(b, px, StoneColors.paper(px))
                }.getOrNull()
            }
            busy = false
            if (s == null) { message = "Не удалось открыть фото."; return@launch }
            shot = s; tapped = null
            detect()
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(::load) }
    val camFile = remember { File(File(ctx.cacheDir, "share").apply { mkdirs() }, "stones.jpg") }
    val camUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", camFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) load(camUri) }

    Screen("Мои стразы", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Разложите все стразы на белом листе — каждый цвет кучкой или хотя бы по одной стразе, не вплотную друг к другу — и снимите сверху " +
                    "при ровном дневном свете, без вспышки. Цвета определятся сами: бумага служит эталоном белого, блики граней не учитываются.",
                fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
            )
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { runCatching { camera.launch(camUri) } }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("📷 Снять") }
                OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Из галереи") }
            }
            if (busy) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("  Определяю цвета…")
            }
            message?.let { Text(it, color = extra.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }

            shot?.let { s ->
                SectionTitle("Фото")
                Text("Нажмите на стразу, чтобы взять её цвет пипеткой — если какой-то цвет не нашёлся (белые и серебристые на белом листе).", fontSize = 12.sp, color = extra.dim)
                Gap(6.dp)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(s.bmp.width.toFloat() / s.bmp.height).clip(RoundedCornerShape(12.dp))
                        .pointerInput(s) {
                            detectTapGestures { o ->
                                val x = (o.x / size.width * s.bmp.width).roundToInt().coerceIn(0, s.bmp.width - 1)
                                val y = (o.y / size.height * s.bmp.height).roundToInt().coerceIn(0, s.bmp.height - 1)
                                val rad = maxOf(2, s.bmp.width / 120)
                                tapped = o to StoneColors.sample(s.px, s.bmp.width, s.bmp.height, x, y, rad, s.paper)
                            }
                        },
                ) {
                    Image(s.bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    tapped?.let { (o, c) ->
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Color.White, 14.dp.toPx(), o, style = Stroke(3.dp.toPx()))
                            drawCircle(swatch(c), 11.dp.toPx(), o)
                        }
                    }
                }
                tapped?.let { (_, c) ->
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(swatch(c)).border(1.dp, extra.line, RoundedCornerShape(8.dp)))
                        Text("  ${hex(c)} · ≈ DMC ${CraftPattern.DMC[CraftPattern.nearestDmc(c)].code}", Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = {
                            val n = CraftStore.addMine(ctx, listOf(c), auto = false, same = 2.0)
                            message = if (n == 0) "Такой цвет уже есть" else null
                            tapped = null
                        }) { Text("+ Добавить") }
                    }
                }

                SectionTitle("Найдено цветов: ${found.size}")
                Text("Различать оттенки: ${if (sens < 7) "тонко" else if (sens > 13) "грубо" else "обычно"}", fontSize = 13.sp)
                Slider(sens, { sens = it }, valueRange = 4f..20f, onValueChangeFinished = { detect() })
                Text("Если один цвет разбился на два похожих — сдвиньте вправо; если разные цвета слились — влево.", fontSize = 12.sp, color = extra.dim)
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    found.forEachIndexed { i, f ->
                        val on = i in picked
                        Column(
                            Modifier.width(58.dp).clip(RoundedCornerShape(10.dp))
                                .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else extra.line, RoundedCornerShape(10.dp))
                                .clickable { picked = if (on) picked - i else picked + i }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(swatch(f.rgb)), contentAlignment = Alignment.Center) {
                                if (on) Text("✓", fontWeight = FontWeight.Bold, color = if (CraftPattern.symbolDark(f.rgb)) Color.Black else Color.White)
                            }
                            Text(if (f.share >= 0.01) "${(f.share * 100).roundToInt()}%" else "<1%", fontSize = 10.sp, color = extra.dim)
                        }
                    }
                }
                if (found.isNotEmpty()) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val n = CraftStore.addMine(ctx, picked.sorted().map { found[it].rgb }, auto = true)
                            message = null
                            android.widget.Toast.makeText(ctx, "Добавлено цветов: $n", android.widget.Toast.LENGTH_SHORT).show()
                            picked = emptySet()
                        },
                        enabled = picked.isNotEmpty(), modifier = Modifier.weight(1f),
                    ) { Text("Добавить отмеченные (${picked.size})") }
                    TextButton(onClick = { picked = if (picked.size == found.size) emptySet() else found.indices.toSet() }) {
                        Text(if (picked.size == found.size) "Снять все" else "Все")
                    }
                }
            }

            SectionTitle("Мои цвета · ${stones.size}")
            Text(
                "Из этих цветов собирается картина, если в схеме включить «Только из моих страз». Нажмите на цвет, чтобы поправить оттенок, название или удалить.",
                fontSize = 12.sp, color = extra.dim,
            )
            Gap(6.dp)
            stones.forEach { st ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { edit = st }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(swatch(st.rgb)).border(1.dp, extra.line, RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Мой №${st.n} · ${st.name.ifBlank { "без названия" }}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(listOfNotNull(hex(st.rgb), st.dmc.takeIf { it.isNotBlank() }?.let { "≈ DMC $it" }, if (st.auto) "по фото" else "вручную").joinToString(" · "), fontSize = 12.sp, color = extra.dim)
                    }
                    TextButton(onClick = { CraftStore.setMine(ctx, stones.filter { it.n != st.n }) }) { Text("Удалить", color = extra.danger, fontSize = 12.sp) }
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { edit = MyStone(0, 0x9E9E9E) }, modifier = Modifier.weight(1f)) { Text("+ Цвет вручную") }
                if (stones.isNotEmpty()) TextButton(onClick = { clearAll = true }) { Text("Удалить все", color = extra.danger) }
            }
            Gap(40.dp)
        }
    }

    edit?.let { st -> StoneDialog(st, onDismiss = { edit = null }) { saved ->
        val cur = CraftStore.mine(ctx)
        val n = if (saved.n == 0) (cur.maxOfOrNull { it.n } ?: 0) + 1 else saved.n
        val d = CraftPattern.DMC[CraftPattern.nearestDmc(saved.rgb)]
        val out = saved.copy(n = n, dmc = d.code, name = saved.name.ifBlank { d.name })
        CraftStore.setMine(ctx, if (saved.n == 0) cur + out else cur.map { if (it.n == saved.n) out else it })
        edit = null
    } }
    if (clearAll) AlertDialog(
        onDismissRequest = { clearAll = false },
        title = { Text("Удалить все мои цвета?") },
        text = { Text("Схемы с «Только из моих страз» соберутся заново из каталога DMC, пока вы не добавите цвета.") },
        confirmButton = { TextButton(onClick = { CraftStore.setMine(ctx, emptyList()); clearAll = false }) { Text("Удалить", color = extra.danger) } },
        dismissButton = { TextButton(onClick = { clearAll = false }) { Text("Отмена") } },
    )
}

/** Правка цвета страз. */
@Composable
private fun StoneDialog(st: MyStone, onDismiss: () -> Unit, onSave: (MyStone) -> Unit) =
    ColorPickDialog(if (st.n == 0) "Новый цвет" else "Мой №${st.n}", st.rgb, st.name, st.n != 0, onDismiss) { rgb, name ->
        onSave(st.copy(rgb = rgb, name = name, auto = st.auto && rgb == st.rgb))
    }

/** Выбор цвета: оттенок, насыщенность, яркость, HEX или номер DMC, название. [showOld] — показать прежний цвет рядом. */
@Composable
internal fun ColorPickDialog(title: String, rgb0: Int, name0: String, showOld: Boolean, onDismiss: () -> Unit, onSave: (Int, String) -> Unit) {
    val st = remember(rgb0, name0) { MyStone(if (showOld) 1 else 0, rgb0, name0) }
    val extra = LocalExtra.current
    val hsv0 = remember(st) { FloatArray(3).also { android.graphics.Color.colorToHSV(st.rgb or (0xFF shl 24), it) } }
    var hue by remember(st) { mutableFloatStateOf(hsv0[0]) }
    var sat by remember(st) { mutableFloatStateOf(hsv0[1]) }
    var value by remember(st) { mutableFloatStateOf(hsv0[2]) }
    var name by remember(st) { mutableStateOf(st.name) }
    var code by remember(st) { mutableStateOf("") }
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)) and 0xFFFFFF
    var hexText by remember(st) { mutableStateOf(hex(st.rgb)) }
    LaunchedEffect(rgb) { hexText = hex(rgb) }
    fun setRgb(c: Int) {
        val h = FloatArray(3)
        android.graphics.Color.colorToHSV(c or (0xFF shl 24), h)
        hue = h[0]; sat = h[1]; value = h[2]
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (st.n != 0) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(swatch(st.rgb)).border(1.dp, extra.line, RoundedCornerShape(10.dp)))
                        Text("  →  ", color = extra.dim)
                    }
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(swatch(rgb)).border(1.dp, extra.line, RoundedCornerShape(10.dp)))
                    Text("  ≈ DMC ${CraftPattern.DMC[CraftPattern.nearestDmc(rgb)].code}", fontSize = 13.sp, color = extra.dim)
                }
                Gap(8.dp)
                Text("Оттенок", fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(CircleShape)) {
                        (0 until 12).forEach { i -> Box(Modifier.weight(1f).size(6.dp).background(swatch(android.graphics.Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f))))) }
                    }
                }
                Slider(hue, { hue = it }, valueRange = 0f..360f)
                Text("Насыщенность", fontSize = 12.sp)
                Slider(sat, { sat = it }, valueRange = 0f..1f)
                Text("Яркость", fontSize = 12.sp)
                Slider(value, { value = it }, valueRange = 0f..1f)
                TextInput(hexText, { t ->
                    hexText = t
                    t.trim().removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)?.let { setRgb(it) }
                }, "HEX, например #C0392B")
                Gap(6.dp)
                TextInput(code, { t ->
                    code = t
                    CraftPattern.DMC.firstOrNull { it.code.equals(t.trim(), true) }?.let { d -> setRgb(d.rgb); if (name.isBlank() || st.n == 0) name = d.name }
                }, "Или номер DMC (310, 666, blanc…)")
                Gap(6.dp)
                TextInput(name, { name = it }, "Название (по желанию)")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(rgb, name.trim()) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
