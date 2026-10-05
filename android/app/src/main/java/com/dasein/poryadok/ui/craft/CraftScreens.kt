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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.CropDialog
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.NumberField
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

/** Квадратик цвета с символом — как в таблице цветов набора. */
@Composable
fun ColorChip(t: CraftPattern.Thread, index: Int, size: Int = 28) {
    val extra = LocalExtra.current
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(6.dp)).background(Color(t.rgb or (0xFF shl 24))).border(1.dp, extra.line, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(CraftPattern.symbol(index), fontSize = (size * .5f).sp, fontWeight = FontWeight.Bold, color = if (CraftPattern.symbolDark(t.rgb)) Color.Black else Color.White)
    }
}

/** «Рукоделие»: схемы для алмазной мозаики, вышивки крестом и бисера из любой фотографии. */
@Composable
fun CraftHomeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<CraftProject?>(null) }
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
                    "подберёт цвета по самой картинке и каталогу DMC, даст каждому цвету свой символ (1, 2, 3… A, B, C…), посчитает материалы " +
                    "и сделает схему для печати на A4–A0 или в натуральную величину.",
                fontSize = 14.sp, color = extra.dim, lineHeight = 20.sp,
            )
            Gap(10.dp)
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Схема из фото")
            }
            OutlinedButton(onClick = { nav.navigate(Routes.CRAFT_STASH) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Мои запасы (${CraftStore.stash(ctx).size} цветов)")
            }
            if (busy) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("  Готовлю фото…")
            }
            error?.let { Text(it, color = extra.danger, modifier = Modifier.padding(top = 8.dp)) }
            SectionTitle("Мои схемы")
            if (projects.isEmpty()) Empty(Ic.image, "Пока нет схем", "Лучше всего получаются фото с крупным объектом и чётким контрастом: портрет, питомец, цветы, пейзаж.")
            projects.forEach { p ->
                Tile(Modifier.padding(bottom = 8.dp), onClick = { nav.navigate(Routes.craft(p.id)) }, onLongClick = { toDelete = p }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val thumb = remember(p.id) { runCatching { BitmapFactory.decodeFile(CraftStore.photo(ctx, p.id).absolutePath, BitmapFactory.Options().apply { inSampleSize = 8 }) }.getOrNull() }
                        thumb?.let { Image(it.asImageBitmap(), null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text("${p.kindEnum.title} · ${p.width} клеток в ширину · до ${p.colors} цветов", fontSize = 12.sp, color = extra.dim)
                        }
                        IconAction(Ic.trash, "Удалить") { toDelete = p }
                    }
                }
            }
            HowTo("craft")
            Gap(96.dp)
        }
    }
    toDelete?.let { p ->
        ConfirmDialog("Удалить «${p.name}»?", "Фото, схема, настройки и отметки удалятся.", onDismiss = { toDelete = null }) {
            CraftStore.delete(ctx, p.id); toDelete = null; refresh++
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
    /** Что показывать: 0 — готовая работа, 1 — схема с символами, 2 — исходное фото. */
    var view by remember { mutableIntStateOf(1) }
    var symbols by remember { mutableStateOf<Bitmap?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var photoVersion by remember { mutableIntStateOf(0) }
    var cropAspect by remember { mutableStateOf<Float?>(null) }
    var pending by remember { mutableStateOf<CraftProject?>(null) }
    var colorEdit by remember { mutableStateOf<Int?>(null) }
    var printDialog by remember { mutableStateOf(false) }
    var stashDialog by remember { mutableStateOf(false) }
    // Настройки, влияющие на расчёт схемы.
    val key = listOf(p0.kind, p0.width, p0.colors, p0.dither, p0.cleanup, p0.brightness, p0.contrast, p0.saturation, p0.removeBg, p0.onlyStash, p0.replace, photoVersion)
    LaunchedEffect(key, p0.round) {
        busy = true
        val pat = CraftStore.pattern(ctx, p0)
        pattern = pat
        preview = pat?.let { withContext(Dispatchers.Default) { CraftStore.preview(it, p0.kindEnum, (1400 / maxOf(it.width, it.height)).coerceIn(4, 24), p0.round) } }
        symbols = pat?.let { withContext(Dispatchers.Default) { CraftStore.symbolChart(it, p0.chartStyle) } }
        busy = false
    }
    LaunchedEffect(p0.chartStyle) { pattern?.let { pt -> symbols = withContext(Dispatchers.Default) { CraftStore.symbolChart(pt, p0.chartStyle) } } }
    fun save(n: CraftProject) { project = n; CraftStore.save(ctx, n) }
    /** Изменение, которое пересчитает схему: если уже есть отметки выложенных клеток — сначала спросить. */
    fun update(f: (CraftProject) -> CraftProject) {
        val n = f(p0)
        val changesPattern = listOf(n.kind, n.width, n.colors, n.dither, n.cleanup, n.brightness, n.contrast, n.saturation, n.removeBg, n.onlyStash, n.replace) !=
            listOf(p0.kind, p0.width, p0.colors, p0.dither, p0.cleanup, p0.brightness, p0.contrast, p0.saturation, p0.removeBg, p0.onlyStash, p0.replace)
        if (changesPattern && CraftStore.hasProgress(ctx, p0)) pending = n else save(n)
    }
    val original = remember(id, photoVersion) { runCatching { BitmapFactory.decodeFile(CraftStore.photo(ctx, id).absolutePath) }.getOrNull() }

    val fileName = p0.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").ifBlank { "Схема" }
    val legendExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val pat = pattern
        if (uri != null && pat != null) scope.launch {
            busy = true
            message = runCatching {
                withContext(Dispatchers.IO) {
                    val b = CraftStore.legendCard(pat, p0)
                    ctx.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    b.recycle()
                }
                "Готово: схема (PDF) и таблица цветов с миниатюрой (PNG) сохранены."
            }.getOrElse { "Не получилось сохранить таблицу: ${it.message}" }
            busy = false
        }
    }
    var exportPaper by remember { mutableStateOf(210 to 297) }
    var exportMode by remember { mutableStateOf(CraftStore.PRINT_FIT) }
    val pdfExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val pat = pattern
        if (uri != null && pat != null) scope.launch {
            busy = true
            message = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { CraftStore.pdf(ctx, pat, p0, it, exportPaper.first, exportPaper.second, exportMode) }
                "PDF сохранён: бумага ${exportPaper.first}×${exportPaper.second} мм" +
                    when (exportMode) { CraftStore.PRINT_FIT -> ", вся схема на первом листе"; CraftStore.PRINT_REAL -> ", натуральная величина"; else -> ", по листам" } +
                    ". Теперь сохраните таблицу цветов."
            }.getOrElse { "Не получилось сохранить: ${it.message}" }
            busy = false
            // Второй файл — таблица цветов с миниатюрой, как карточка в наборе.
            legendExporter.launch("$fileName — таблица цветов.png")
        }
    }
    val pngExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val pat = pattern
        if (uri != null && pat != null) scope.launch {
            busy = true
            message = runCatching {
                withContext(Dispatchers.IO) {
                    val b = CraftStore.chart(pat, p0)
                    ctx.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    b.recycle()
                }
                "Схема сохранена картинкой."
            }.getOrElse { "Не получилось сохранить: ${it.message}" }
            busy = false
        }
    }
    fun share(text: String? = null) {
        val pat = pattern ?: return
        scope.launch {
            busy = true
            runCatching {
                val send: Intent
                if (text != null) send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                else {
                    // Два файла: схема PDF и таблица цветов с миниатюрой.
                    val files = withContext(Dispatchers.IO) {
                        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                        val pdf = File(dir, "$fileName.pdf")
                        pdf.outputStream().use { CraftStore.pdf(ctx, pat, p0, it) }
                        val png = File(dir, "$fileName — таблица цветов.png")
                        val b = CraftStore.legendCard(pat, p0)
                        png.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        b.recycle()
                        listOf(pdf, png)
                    }
                    val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) as Uri })
                    send = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(send, "Поделиться"))
            }.onFailure { message = "Не получилось поделиться: ${it.message}" }
            busy = false
        }
    }
    Screen(p0.name, onBack = { nav.popBackStack() }, actions = { IconAction(Ic.trash, "Удалить схему") { confirmDelete = true } }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CraftPattern.Kind.entries.forEach { k -> Pill(k.title, p0.kindEnum == k) { update { it.copy(kind = k.name) } } }
            }
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Схема с символами", "Готовая работа", "Фото").forEachIndexed { i, s -> val v = listOf(1, 0, 2)[i]; Pill(s, view == v) { view = v } }
            }
            Gap(8.dp)
            // Предпросмотр: можно приблизить двумя пальцами.
            var scale by remember { mutableFloatStateOf(1f) }
            var ox by remember { mutableFloatStateOf(0f) }
            var oy by remember { mutableFloatStateOf(0f) }
            val shown = when (view) { 2 -> original; 1 -> symbols ?: preview; else -> preview }
            Box(
                Modifier.fillMaxWidth().height(340.dp)
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
                        it.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy),
                        contentScale = ContentScale.Fit, filterQuality = if (view == 0) FilterQuality.None else FilterQuality.Medium,
                    )
                }
                if (busy) CircularProgressIndicator()
            }
            Text("Двумя пальцами — приблизить и рассмотреть символы", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            // Цвета с символами — сразу под схемой, как таблица в наборе.
            pattern?.let { pt ->
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pt.colors.forEachIndexed { i, t ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { colorEdit = i }) {
                            ColorChip(t, i, 30)
                            Text(t.code, fontSize = 10.sp, color = extra.dim)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.navigate(Routes.craftWork(p0.id)) }, enabled = !busy && pattern != null, modifier = Modifier.weight(1f)) {
                    Text(if (p0.kindEnum == CraftPattern.Kind.CROSS) "Вышивать" else "Выкладывать")
                }
                OutlinedButton(onClick = { printDialog = true }, enabled = !busy && pattern != null, modifier = Modifier.weight(1f)) { Text("PDF / печать") }
            }
            Text("Ниже — размер холста, цвета, таблица с заменой цветов, печать и список покупок ↓", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))

            val pat = pattern
            if (pat != null) {
                val kind = p0.kindEnum
                val wcm = CraftPattern.sizeCm(kind, pat.width, p0.count, p0.round); val hcm = CraftPattern.sizeCm(kind, pat.height, p0.count, p0.round)
                Tile {
                    Text("${pat.width} × ${pat.height} клеток · ${"%.1f".format(wcm)} × ${"%.1f".format(hcm)} см", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Выкладывать ${pat.filled} ${kind.unit}, цветов ${pat.colors.size}" + when (kind) {
                            CraftPattern.Kind.DIAMOND -> if (p0.round) " · круглые стразы 2,8 мм" else " · квадратные стразы 2,5 мм"
                            CraftPattern.Kind.CROSS -> " · канва Аида ${p0.count}"
                            CraftPattern.Kind.BEADS -> " · бисер 10/0"
                        },
                        fontSize = 13.sp, color = extra.dim,
                    )
                }
            }

            SectionTitle("Фото и холст")
            Text("Размер холста — обрежет фото под пропорции и пересчитает клетки:", fontSize = 13.sp, color = extra.dim)
            val portrait = (original?.let { it.height >= it.width }) ?: true
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                CraftPattern.CANVASES.forEach { (a, b) ->
                    val (w, h) = if (portrait) a to b else b to a
                    Pill("$w×$h см", false) {
                        update { it.copy(width = CraftPattern.cellsFor(it.kindEnum, w.toDouble(), it.count, it.round).coerceIn(20, 250)) }
                        cropAspect = w.toFloat() / h
                    }
                }
                Pill("Обрезать вручную", false) { cropAspect = original?.let { it.width.toFloat() / it.height } ?: 0.75f }
                Pill("Квадрат", false) { cropAspect = 1f }
                Pill("Вернуть всё фото", false) { CraftStore.resetCrop(ctx, p0.id); photoVersion++ }
            }
            Text("Ширина: ${p0.width} клеток (≈ ${"%.0f".format(CraftPattern.sizeCm(p0.kindEnum, p0.width, p0.count, p0.round))} см)", fontSize = 14.sp)
            var w by remember(p0.id) { mutableFloatStateOf(p0.width.toFloat()) }
            Slider(w, { w = it }, valueRange = 20f..250f, onValueChangeFinished = { update { it.copy(width = w.toInt()) } })
            AdjustSlider("Яркость", p0.brightness) { v -> update { it.copy(brightness = v) } }
            AdjustSlider("Контраст", p0.contrast) { v -> update { it.copy(contrast = v) } }
            AdjustSlider("Насыщенность", p0.saturation) { v -> update { it.copy(saturation = v) } }

            SectionTitle("Цвета")
            Text("Цветов: до ${p0.colors}", fontSize = 14.sp)
            var k by remember(p0.id) { mutableFloatStateOf(p0.colors.toFloat()) }
            Slider(k, { k = it }, valueRange = 4f..80f, onValueChangeFinished = { update { it.copy(colors = k.toInt()) } })
            Text("Цвета подбираются по самой картинке: сначала находятся её главные оттенки, потом — ближайшие нитки и стразы DMC. Меньше цветов — проще работа, больше — точнее портреты.", fontSize = 12.sp, color = extra.dim)
            if (p0.kindEnum == CraftPattern.Kind.CROSS) {
                Gap(6.dp)
                Text("Канва Аида", fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(11, 14, 16, 18).forEach { c -> Pill("$c", p0.count == c) { save(p0.copy(count = c)) } } }
            }
            if (p0.kindEnum == CraftPattern.Kind.DIAMOND) Toggle("Круглые стразы", "2,8 мм вместо квадратных 2,5 мм — холст получится крупнее", p0.round) { on -> save(p0.copy(round = on)) }
            Toggle("Плавные переходы", "Смешивает соседние цвета точками — для портретов и неба", p0.dither) { on -> update { it.copy(dither = on) } }
            Toggle("Убрать одиночные клетки", "Меньше «конфетти» — работать быстрее", p0.cleanup) { on -> update { it.copy(cleanup = on) } }
            Toggle("Не заполнять фон", "Однотонный фон у краёв остаётся пустым — как в вышивке без фона", p0.removeBg) { on -> update { it.copy(removeBg = on) } }
            val stashSize = CraftStore.stash(ctx).size
            Toggle("Только из моих запасов", if (stashSize < 2) "Сначала отметьте цвета в «Мои запасы»" else "Подбирать из $stashSize цветов, которые у вас уже есть", p0.onlyStash) { on ->
                if (on && stashSize < 2) nav.navigate(Routes.CRAFT_STASH) else update { it.copy(onlyStash = on) }
            }
            var name by remember(p0.id) { mutableStateOf(p0.name) }
            Gap(6.dp)
            TextInput(name, { name = it; save(p0.copy(name = it.ifBlank { p0.name })) }, "Название")

            if (pat != null) {
                val kind = p0.kindEnum
                val counts = pat.counts()
                val stash = CraftStore.stash(ctx)
                SectionTitle("Таблица цветов · ${pat.colors.size}")
                Text("У каждого цвета свой символ — им подписаны клетки схемы. Нажмите на цвет, чтобы заменить его другим оттенком или слить с соседним.", fontSize = 12.sp, color = extra.dim)
                val total = pat.colors.indices.sumOf { CraftPattern.need(kind, counts[it], p0.count).amount }
                Text(
                    when (kind) {
                        CraftPattern.Kind.DIAMOND -> "Всего стразов с запасом: ${CraftStore.fmt(total)} шт"
                        CraftPattern.Kind.CROSS -> "Всего мотков: ${CraftStore.fmt(total)}"
                        CraftPattern.Kind.BEADS -> "Всего бисера: ${CraftStore.fmt(total)} г"
                    },
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp),
                )
                pat.colors.forEachIndexed { i, t ->
                    val n = CraftPattern.need(kind, counts[i], p0.count)
                    Row(Modifier.fillMaxWidth().clickable { colorEdit = i }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", Modifier.width(26.dp), fontSize = 12.sp, color = extra.dim)
                        ColorChip(t, i)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("DMC ${t.code} · ${t.name}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${counts[i]} ${kind.unit} · ${CraftStore.fmt(n.amount)} ${n.unit}" + if (t.code in stash) " · есть в запасах" else "",
                                fontSize = 12.sp, color = if (t.code in stash) extra.ok else extra.dim,
                            )
                        }
                    }
                }
                val own = pat.colors.count { it.code in stash }
                OutlinedButton(onClick = { if (stash.isEmpty()) nav.navigate(Routes.CRAFT_STASH) else stashDialog = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(if (stash.isEmpty()) "Собрать из моих страз — сначала отметьте запасы" else "Собрать из моих страз (своих уже $own из ${pat.colors.size})")
                }
                if (p0.replace.isNotEmpty()) TextButton(onClick = { update { it.copy(replace = emptyMap()) } }) { Text("Вернуть исходные цвета (замен: ${p0.replace.size})") }

                SectionTitle("Печать и выгрузка")
                Text("Вид клеток схемы", fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf(0 to "Цветная, как в наборе", 1 to "Светлая", 2 to "Ч/б символы").forEach { (i, s) -> Pill(s, p0.chartStyle == i) { save(p0.copy(chartStyle = i)) } }
                }
                Button(onClick = { printDialog = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Выгрузить: схема PDF (A4, A3, A2…) + таблица цветов") }
                OutlinedButton(onClick = { pngExporter.launch("$fileName.png") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Схема одной картинкой (PNG)") }
                OutlinedButton(onClick = { legendExporter.launch("$fileName — таблица цветов.png") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Таблица цветов с миниатюрой (PNG)") }
                OutlinedButton(onClick = { share() }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Поделиться: схема PDF + таблица цветов") }
                val toBuy = pat.colors.indices.filter { pat.colors[it].code !in stash }
                OutlinedButton(
                    onClick = {
                        share(
                            "Купить для «${p0.name}» (${kind.title}):\n" + toBuy.joinToString("\n") { i ->
                                val n = CraftPattern.need(kind, counts[i], p0.count)
                                "DMC ${pat.colors[i].code} — ${pat.colors[i].name}: ${CraftStore.fmt(n.amount)} ${n.unit}"
                            },
                        )
                    },
                    enabled = !busy && toBuy.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text("Список покупок (${toBuy.size} цветов, без того что есть)") }
                Hint(
                    "craft_print",
                    "«Схема по листам» — удобно работать: крупные клетки, номера рядов и столбцов, сетка через 10. «Натуральная величина» — клетка ровно под страз или крестик: " +
                        "распечатайте на нужном формате (A4–A0 или свой), и если холст больше листа, он разделится на части с метками совмещения. Цвета на экране приблизительные — покупайте по номеру DMC.",
                    title = "Как печатать",
                )
            }
            message?.let { Tile(Modifier.padding(top = 8.dp)) { Text(it) } }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Удалить схему", color = extra.danger) }
            Gap(60.dp)
        }
    }
    cropAspect?.let { a ->
        CropDialog(CraftStore.originalPath(ctx, p0.id), a, "crafts", outW = 1600, title = "Кадр для схемы", onDismiss = { cropAspect = null }) { path ->
            CraftStore.applyCrop(ctx, p0.id, path); cropAspect = null; photoVersion++
        }
    }
    pending?.let { n ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Схема изменится") },
            text = { Text("Отметки выложенных клеток относятся к текущей схеме и сбросятся. Продолжить?") },
            confirmButton = { TextButton(onClick = { save(n); pending = null }) { Text("Изменить") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Оставить как есть") } },
        )
    }
    colorEdit?.let { i ->
        val pat = pattern
        if (pat != null && i < pat.colors.size) {
            val t = pat.colors[i]
            AlertDialog(
                onDismissRequest = { colorEdit = null },
                title = { Row(verticalAlignment = Alignment.CenterVertically) { ColorChip(t, i); Text("  DMC ${t.code}") } },
                text = {
                    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                        Text(t.name, color = extra.dim)
                        Text("Заменить на соседний оттенок:", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        CraftPattern.alternatives(t).forEach { a ->
                            Row(Modifier.fillMaxWidth().clickable { update { it.copy(replace = it.replace + (t.code to a.code)) }; colorEdit = null }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).background(Color(a.rgb or (0xFF shl 24))))
                                Text("  DMC ${a.code} · ${a.name}", fontSize = 14.sp)
                            }
                        }
                        Text("Слить с цветом схемы:", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        pat.colors.forEachIndexed { j, o ->
                            if (j != i) Row(Modifier.fillMaxWidth().clickable { update { it.copy(replace = it.replace + (t.code to o.code)) }; colorEdit = null }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                ColorChip(o, j, 24)
                                Text("  DMC ${o.code} · ${o.name}", fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { colorEdit = null }) { Text("Закрыть") } },
            )
        }
    }
    if (stashDialog) pattern?.let { pat ->
        val matches = remember(pat) { CraftPattern.matchStash(pat.colors, CraftStore.stash(ctx)) }
        val merged = pat.colors.size - matches.map { it.to.code }.distinct().size
        AlertDialog(
            onDismissRequest = { stashDialog = false },
            title = { Text("Собрать из моих страз") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Каждый цвет схемы заменится самым похожим из ваших запасов. Символы и клетки останутся на месте." +
                            if (merged > 0) " $merged цв. сольются с соседними — схема станет проще." else "",
                        fontSize = 13.sp, color = extra.dim,
                    )
                    matches.forEachIndexed { i, m ->
                        val same = m.from.code == m.to.code
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            ColorChip(m.from, i, 26)
                            Text(" →  ", color = extra.dim)
                            Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Color(m.to.rgb or (0xFF shl 24))))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(if (same) "DMC ${m.to.code} — уже есть" else "DMC ${m.from.code} → ${m.to.code} · ${m.to.name}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                if (!same) Text(
                                    m.quality, fontSize = 11.sp,
                                    color = when { m.deltaE < 14 -> extra.ok; m.deltaE < 28 -> extra.warn; else -> extra.danger },
                                )
                            }
                        }
                    }
                    Text(
                        "Совет: если какой-то цвет «другой», добавьте в запасы похожий оттенок или включите «Только из моих запасов» — тогда схема пересчитается целиком из ваших цветов.",
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = CraftPattern.stashReplace(p0.replace, matches)
                    update { it.copy(replace = r) }; stashDialog = false
                    message = "Схема собрана из ваших страз: заменено ${matches.count { it.from.code != it.to.code }} цв."
                }) { Text("Заменить") }
            },
            dismissButton = { TextButton(onClick = { stashDialog = false }) { Text("Отмена") } },
        )
    }
    if (printDialog) {
        var paper by remember { mutableStateOf("A4") }
        var land by remember { mutableStateOf(false) }
        var cw by remember { mutableStateOf("500") }
        var ch by remember { mutableStateOf("700") }
        var mode by remember { mutableStateOf(CraftStore.PRINT_FIT) }
        AlertDialog(
            onDismissRequest = { printDialog = false },
            title = { Text("PDF для печати") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Формат бумаги", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        (CraftStore.PAPERS.map { it.first } + "Свой").forEach { n -> Pill(n, paper == n) { paper = n } }
                    }
                    if (paper == "Свой") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { NumberField(cw, { cw = it }, "Ширина", suffix = "мм", decimal = false) }
                        Box(Modifier.weight(1f)) { NumberField(ch, { ch = it }, "Высота", suffix = "мм", decimal = false) }
                    } else if (mode != CraftStore.PRINT_FIT) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(land, { land = it }); Text("Альбомная ориентация")
                    }
                    Text("Что печатать", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    listOf(
                        CraftStore.PRINT_FIT to "Вся схема на одном листе",
                        CraftStore.PRINT_SHEETS to "По листам — крупные клетки, части склеиваются",
                        CraftStore.PRINT_REAL to "Холст в натуральную величину",
                    ).forEach { (k, t) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { mode = k }) {
                            androidx.compose.material3.RadioButton(mode == k, { mode = k }); Text(t, fontSize = 14.sp)
                        }
                    }
                    if (mode == CraftStore.PRINT_FIT) pattern?.let { pat ->
                        val size = if (paper == "Свой") (cw.toIntOrNull() ?: 210).coerceIn(50, 3000) to (ch.toIntOrNull() ?: 297).coerceIn(50, 3000)
                        else CraftStore.PAPERS.first { it.first == paper }.second
                        val cell = CraftStore.fitCellMm(pat.width, pat.height, size.first, size.second)
                        // Какой формат даёт удобную клетку (от 2,5 мм) — подсказка.
                        val better = CraftStore.PAPERS.firstOrNull { CraftStore.fitCellMm(pat.width, pat.height, it.second.first, it.second.second) >= 2.5 }?.first
                        Text(
                            "Вся схема ${pat.width}×${pat.height} клеток встанет на один лист, клетка ≈ ${CraftStore.fmt(cell)} мм. Ориентация выбирается сама." +
                                when {
                                    cell >= 2.5 -> " Символы хорошо читаются."
                                    better != null -> " Символы мелкие — для работы удобнее $better и больше."
                                    else -> " Символы будут мелкими: для работы удобнее режим «По листам»."
                                },
                            fontSize = 12.sp, color = if (cell >= 2.5) extra.ok else extra.warn, modifier = Modifier.padding(top = 4.dp),
                        )
                        Text("При печати выберите масштаб 100% или «по размеру страницы». На следующих страницах — таблица цветов.", fontSize = 12.sp, color = extra.dim)
                    }
                    pattern?.let { pat ->
                        val wcm = CraftPattern.sizeCm(p0.kindEnum, pat.width, p0.count, p0.round); val hcm = CraftPattern.sizeCm(p0.kindEnum, pat.height, p0.count, p0.round)
                        Text("Работа: ${"%.1f".format(wcm)} × ${"%.1f".format(hcm)} см", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val size = if (paper == "Свой") (cw.toIntOrNull() ?: 210).coerceIn(50, 3000) to (ch.toIntOrNull() ?: 297).coerceIn(50, 3000)
                    else CraftStore.PAPERS.first { it.first == paper }.second.let { if (land) it.second to it.first else it }
                    exportPaper = size; exportMode = mode; printDialog = false
                    pdfExporter.launch("$fileName ${if (paper == "Свой") "${size.first}x${size.second}" else paper}.pdf")
                }) { Text("Сохранить PDF") }
            },
            dismissButton = { TextButton(onClick = { printDialog = false }) { Text("Отмена") } },
        )
    }
    if (confirmDelete) ConfirmDialog("Удалить «${p0.name}»?", "Фото, настройки и отметки удалятся.", onDismiss = { confirmDelete = false }) {
        CraftStore.delete(ctx, p0.id); nav.popBackStack()
    }
}

@Composable
private fun AdjustSlider(label: String, value: Int, onSet: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${if (v.toInt() > 0) "+" else ""}${v.toInt()}", fontSize = 13.sp, modifier = Modifier.width(130.dp))
        Slider(v, { v = it }, valueRange = -50f..50f, onValueChangeFinished = { onSet(v.toInt()) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(sub, fontSize = 12.sp, color = LocalExtra.current.dim)
        }
        Switch(on, onChange)
    }
}

/** «Мои запасы»: какие цвета DMC уже есть — чтобы подбирать схему из них и не покупать лишнее. */
@Composable
fun CraftStashScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var stash by remember { mutableStateOf(CraftStore.stash(ctx)) }
    var q by remember { mutableStateOf("") }
    var bulk by remember { mutableStateOf("") }
    fun set(s: Set<String>) { stash = s; CraftStore.setStash(ctx, s) }
    Screen("Мои запасы", onBack = { nav.popBackStack() }) { pad ->
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                Text("Отметьте цвета DMC, которые у вас есть (стразы, нитки, бисер). Схему можно подобрать только из них, а в списке покупок они не появятся.", fontSize = 13.sp, color = extra.dim)
                Gap(8.dp)
                TextInput(bulk, { bulk = it }, "Добавить номерами: 310, 321, 666, B5200")
                Row {
                    TextButton(onClick = {
                        val codes = CraftPattern.DMC.map { it.code }.toSet()
                        val add = bulk.split(',', ' ', ';', '\n').map { it.trim() }.filter { c -> c in codes || c.lowercase() in codes }
                        set(stash + add); bulk = ""
                    }, enabled = bulk.isNotBlank()) { Text("Добавить") }
                    TextButton(onClick = { set(emptySet()) }, enabled = stash.isNotEmpty()) { Text("Очистить всё", color = extra.danger) }
                }
                TextInput(q, { q = it }, "Поиск: номер или название цвета")
                Text("Отмечено: ${stash.size} из ${CraftPattern.DMC.size}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 6.dp))
            }
            val list = CraftPattern.DMC.filter { q.isBlank() || it.code.contains(q.trim(), true) || it.name.contains(q.trim(), true) }
            items(list.size) { n ->
                val t = list[n]
                val on = t.code in stash
                Row(Modifier.fillMaxWidth().clickable { set(if (on) stash - t.code else stash + t.code) }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Color(t.rgb or (0xFF shl 24))).border(1.dp, extra.line, RoundedCornerShape(6.dp)))
                    Text("  DMC ${t.code} · ${t.name}", Modifier.weight(1f), fontSize = 14.sp)
                    Checkbox(on, { set(if (it) stash + t.code else stash - t.code) })
                }
            }
        }
    }
}
