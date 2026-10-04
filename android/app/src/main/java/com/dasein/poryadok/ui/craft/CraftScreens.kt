@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.craft

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.dasein.poryadok.logic.CraftPattern
import com.dasein.poryadok.system.CraftProject
import com.dasein.poryadok.system.CraftStore
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** «Рукоделие»: схемы для алмазной мозаики, вышивки крестом и бисера из любой фотографии. */
@Composable
fun CraftHomeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val projects = remember(refresh) { CraftStore.list(ctx) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { CraftStore.create(ctx, uri) }
                .onSuccess { nav.navigate(Routes.craft(it.id)) }
                .onFailure { error = it.message }
            busy = false; refresh++
        }
    }
    Screen(
        "Рукоделие", onBack = { nav.popBackStack() },
        fab = {
            FloatingActionButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "Новая схема")
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Выберите любое фото — приложение превратит его в схему для алмазной мозаики, вышивки крестом или бисера: " +
                    "подберёт цвета по каталогу DMC, посчитает стразы, мотки или граммы бисера и нарисует схему для печати.",
                fontSize = 14.sp, color = extra.dim, lineHeight = 20.sp,
            )
            Gap(10.dp)
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Схема из фото")
            }
            if (busy) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("  Готовлю фото…")
            }
            error?.let { Text(it, color = extra.danger, modifier = Modifier.padding(top = 8.dp)) }
            SectionTitle("Мои схемы")
            if (projects.isEmpty()) Empty(Ic.image, "Пока нет схем", "Лучше всего получаются фото с крупным объектом и чётким контрастом: портрет, питомец, цветы, пейзаж.")
            projects.forEach { p ->
                Tile(Modifier.padding(bottom = 8.dp), onClick = { nav.navigate(Routes.craft(p.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val thumb = remember(p.id) { runCatching { BitmapFactory.decodeFile(CraftStore.photo(ctx, p.id).absolutePath, BitmapFactory.Options().apply { inSampleSize = 8 }) }.getOrNull() }
                        thumb?.let { Image(it.asImageBitmap(), null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text("${p.kindEnum.title} · ${p.width} клеток в ширину · ${p.colors} цветов", fontSize = 12.sp, color = extra.dim)
                            if (p.done.isNotEmpty()) Text("готово цветов: ${p.done.size}", fontSize = 12.sp, color = extra.ok)
                        }
                    }
                }
            }
            HowTo("craft")
            Gap(96.dp)
        }
    }
}

@Composable
fun CraftEditorScreen(nav: NavHostController, id: Long) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf(CraftStore.load(ctx, id)) }
    val p0 = project ?: run { Screen("Схема", onBack = { nav.popBackStack() }) { Text("Схема не найдена", Modifier.padding(it).padding(16.dp)) }; return }
    var pattern by remember { mutableStateOf<CraftPattern.Pattern?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(true) }
    var showOriginal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Настройки, влияющие на расчёт схемы.
    val key = listOf(p0.kind, p0.width, p0.colors, p0.dither, p0.cleanup)
    LaunchedEffect(key) {
        busy = true
        val pat = CraftStore.pattern(ctx, p0)
        pattern = pat
        preview = pat?.let { withContext(Dispatchers.Default) { CraftStore.preview(it, p0.kindEnum, (1400 / maxOf(it.width, it.height)).coerceIn(4, 24)) } }
        busy = false
    }
    fun update(f: (CraftProject) -> CraftProject) { val n = f(p0); project = n; CraftStore.save(ctx, n) }
    val original = remember(id) { runCatching { BitmapFactory.decodeFile(CraftStore.photo(ctx, id).absolutePath) }.getOrNull() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val pat = pattern
        if (uri != null && pat != null) scope.launch {
            busy = true
            message = runCatching {
                withContext(Dispatchers.IO) {
                    val b = CraftStore.chart(pat, p0.kindEnum, p0.count, p0.name)
                    ctx.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    b.recycle()
                }
                "Схема сохранена — её можно распечатать или открыть на планшете."
            }.getOrElse { "Не получилось сохранить: ${it.message}" }
            busy = false
        }
    }
    fun share() {
        val pat = pattern ?: return
        scope.launch {
            busy = true
            runCatching {
                val f = withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                    val out = File(dir, "schema_${p0.id}.png")
                    val b = CraftStore.chart(pat, p0.kindEnum, p0.count, p0.name)
                    out.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    b.recycle(); out
                }
                val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Поделиться схемой"))
            }.onFailure { message = "Не получилось поделиться: ${it.message}" }
            busy = false
        }
    }

    Screen(p0.name, onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CraftPattern.Kind.entries.forEach { k -> Pill(k.title, p0.kindEnum == k) { update { it.copy(kind = k.name) } } }
            }
            Gap(10.dp)
            // Предпросмотр: можно приблизить двумя пальцами.
            var scale by remember { mutableFloatStateOf(1f) }
            var ox by remember { mutableFloatStateOf(0f) }
            var oy by remember { mutableFloatStateOf(0f) }
            val shown = if (showOriginal) original else preview
            Box(
                Modifier.fillMaxWidth().aspectRatio(((pattern?.width ?: 1).toFloat() / (pattern?.height ?: 1)).coerceIn(0.4f, 2.5f))
                    .clip(RoundedCornerShape(14.dp)).background(Color(0xFF1E1B17))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            ox = if (scale == 1f) 0f else ox + pan.x; oy = if (scale == 1f) 0f else oy + pan.y
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                shown?.let {
                    Image(
                        it.asImageBitmap(), null, Modifier.fillMaxWidth().graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy),
                        contentScale = ContentScale.Fit, filterQuality = if (showOriginal) FilterQuality.Medium else FilterQuality.None,
                    )
                }
                if (busy) CircularProgressIndicator()
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Двумя пальцами — приблизить", fontSize = 12.sp, color = extra.dim, modifier = Modifier.weight(1f))
                TextButton(onClick = { showOriginal = !showOriginal }) { Text(if (showOriginal) "Показать схему" else "Показать фото") }
            }

            val pat = pattern
            if (pat != null) {
                val kind = p0.kindEnum
                val wcm = CraftPattern.sizeCm(kind, pat.width, p0.count); val hcm = CraftPattern.sizeCm(kind, pat.height, p0.count)
                Tile {
                    Text("${pat.width} × ${pat.height} клеток · ${"%.1f".format(wcm)} × ${"%.1f".format(hcm)} см", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Всего ${pat.cells.size} ${kind.unit}, цветов ${pat.colors.size}" + when (kind) {
                            CraftPattern.Kind.DIAMOND -> " · страз 2,5 мм, квадратный"
                            CraftPattern.Kind.CROSS -> " · канва Аида ${p0.count}"
                            CraftPattern.Kind.BEADS -> " · бисер 10/0"
                        },
                        fontSize = 13.sp, color = extra.dim,
                    )
                }
            }

            SectionTitle("Настройки")
            Text("Ширина: ${p0.width} клеток" + (pattern?.let { " (≈ ${"%.0f".format(CraftPattern.sizeCm(p0.kindEnum, p0.width, p0.count))} см)" } ?: ""), fontSize = 14.sp)
            var w by remember(p0.id) { mutableFloatStateOf(p0.width.toFloat()) }
            Slider(w, { w = it }, valueRange = 20f..200f, onValueChangeFinished = { update { it.copy(width = w.toInt()) } })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(20, 30, 40, 50, 60).forEach { cm ->
                    Pill("$cm см", false) { val c = CraftPattern.cellsFor(p0.kindEnum, cm.toDouble(), p0.count).coerceIn(20, 200); w = c.toFloat(); update { it.copy(width = c) } }
                }
            }
            Gap(8.dp)
            Text("Цветов: ${p0.colors}", fontSize = 14.sp)
            var k by remember(p0.id) { mutableFloatStateOf(p0.colors.toFloat()) }
            Slider(k, { k = it }, valueRange = 4f..60f, onValueChangeFinished = { update { it.copy(colors = k.toInt()) } })
            Text("Меньше цветов — проще и дешевле работа, больше — точнее портреты и переходы.", fontSize = 12.sp, color = extra.dim)
            if (p0.kindEnum == CraftPattern.Kind.CROSS) {
                Gap(6.dp)
                Text("Канва Аида", fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(11, 14, 16, 18).forEach { c -> Pill("$c", p0.count == c) { update { it.copy(count = c) } } } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Плавные переходы", fontSize = 14.sp)
                    Text("Смешивает соседние цвета точками — для портретов и неба", fontSize = 12.sp, color = extra.dim)
                }
                Switch(p0.dither, { on -> update { it.copy(dither = on) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Убрать одиночные клетки", fontSize = 14.sp)
                    Text("Меньше «конфетти» — работать быстрее", fontSize = 12.sp, color = extra.dim)
                }
                Switch(p0.cleanup, { on -> update { it.copy(cleanup = on) } })
            }
            var name by remember(p0.id) { mutableStateOf(p0.name) }
            Gap(6.dp)
            TextInput(name, { name = it; update { pr -> pr.copy(name = it.ifBlank { pr.name }) } }, "Название")

            if (pat != null) {
                val kind = p0.kindEnum
                val counts = pat.counts()
                SectionTitle("Материалы · ${pat.colors.size} цветов")
                Text("Отмечайте цвета, которые уже выложили или вышили.", fontSize = 12.sp, color = extra.dim)
                val total = pat.colors.indices.sumOf { CraftPattern.need(kind, counts[it], p0.count).amount }
                Text(
                    when (kind) {
                        CraftPattern.Kind.DIAMOND -> "Всего стразов с запасом: ${CraftStore.fmt(total)} шт"
                        CraftPattern.Kind.CROSS -> "Всего мотков: ${CraftStore.fmt(total)}"
                        CraftPattern.Kind.BEADS -> "Всего бисера: ${CraftStore.fmt(total)} г"
                    },
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp),
                )
                val doneCells = pat.colors.indices.filter { pat.colors[it].code in p0.done }.sumOf { counts[it] }
                if (pat.cells.isNotEmpty()) com.dasein.poryadok.ui.common.Bar(doneCells.toFloat() / pat.cells.size, extra.ok, Modifier.padding(bottom = 6.dp))
                pat.colors.forEachIndexed { i, t ->
                    val n = CraftPattern.need(kind, counts[i], p0.count)
                    val done = t.code in p0.done
                    Row(Modifier.fillMaxWidth().clickable { update { it.copy(done = if (done) it.done - t.code else it.done + t.code) } }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(t.rgb or (0xFF shl 24))).border(1.dp, extra.line, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            Text(CraftPattern.SYMBOLS[i % CraftPattern.SYMBOLS.size], fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (CraftPattern.symbolDark(t.rgb)) Color.Black else Color.White)
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("DMC ${t.code} · ${t.name}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("${counts[i]} ${kind.unit} · ${CraftStore.fmt(n.amount)} ${n.unit}", fontSize = 12.sp, color = extra.dim)
                        }
                        Checkbox(done, { on -> update { it.copy(done = if (on) it.done + t.code else it.done - t.code) } })
                    }
                }
                Gap(10.dp)
                Button(onClick = { exporter.launch("${p0.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "")}.png") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Сохранить схему для печати (PNG)")
                }
                OutlinedButton(onClick = { share() }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Поделиться схемой") }
                Hint(
                    "craft_print",
                    "В схеме для печати каждая клетка подписана символом, жирные линии — через 10 клеток, по краям — номера. " +
                        "Внизу легенда: символ, номер DMC и сколько материала купить. Цвета на экране и в каталогах немного отличаются — сверяйте по номеру.",
                    title = "Как читать схему",
                )
            }
            message?.let { Tile(Modifier.padding(top = 8.dp)) { Text(it) } }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Удалить схему", color = extra.danger) }
            Gap(60.dp)
        }
    }
    if (confirmDelete) ConfirmDialog("Удалить «${p0.name}»?", "Фото и настройки схемы удалятся.", onDismiss = { confirmDelete = false }) {
        CraftStore.delete(ctx, p0.id); nav.popBackStack()
    }
}
