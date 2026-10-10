package com.dasein.poryadok.ui.craft

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.dasein.poryadok.logic.PaintByNumbers
import com.dasein.poryadok.system.MyPencil
import com.dasein.poryadok.system.PbnProject
import com.dasein.poryadok.system.PbnStore
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun sw(rgb: Int) = Color(rgb or (0xFF shl 24))

/** «Картины по номерам»: свои фото → раскраска с контурами и номерами для цветных карандашей. */
@Composable
fun PbnHomeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<PbnProject?>(null) }
    val list = remember(refresh) { PbnStore.list(ctx) }
    val ver = PbnStore.version.intValue
    val pencils = remember(ver) { PbnStore.pencils(ctx) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { PbnStore.create(ctx, uri) }.onSuccess { nav.navigate(Routes.pbn(it.id)) }
            busy = false; refresh++
        }
    }
    Screen("Картины по номерам", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Загрузите любую картинку — приложение превратит её в раскраску: тонкие контуры и номера цветов, а внизу листа — какой карандаш брать " +
                    "для каждого номера. Цвета подбираются только из ваших карандашей. Распечатайте PDF и раскрашивайте.",
                fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
            )
            Gap(8.dp)
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Открываю…" else "Новая раскраска из фото")
            }
            OutlinedButton(onClick = { nav.navigate(Routes.CRAFT_PENCILS) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("✏️ Мои карандаши (${pencils.size})")
            }
            SectionTitle("Мои раскраски")
            if (list.isEmpty()) Text("Пока нет раскрасок. Лучше всего получаются картинки с крупными предметами: пейзажи, цветы, животные.", color = extra.dim, fontSize = 13.sp)
            list.forEach { p ->
                Tile(Modifier.padding(bottom = 8.dp), onClick = { nav.navigate(Routes.pbn(p.id)) }, onLongClick = { toDelete = p }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val thumb = remember(p.id) { runCatching { BitmapFactory.decodeFile(PbnStore.photo(ctx, p.id).absolutePath, BitmapFactory.Options().apply { inSampleSize = 8 }) }.getOrNull() }
                        if (thumb != null) Image(thumb.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text("до ${p.colors} цветов · ${listOf("крупно", "средне", "подробно")[p.detail.coerceIn(0, 2)]}", fontSize = 12.sp, color = extra.dim)
                        }
                        TextButton(onClick = { toDelete = p }) { Text("Удалить", color = extra.danger, fontSize = 12.sp) }
                    }
                }
            }
            Gap(40.dp)
        }
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить «${p.name}»?") },
            confirmButton = { TextButton(onClick = { PbnStore.delete(ctx, p.id); toDelete = null; refresh++ }) { Text("Удалить", color = extra.danger) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PbnScreen(nav: NavHostController, id: Long) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf(PbnStore.load(ctx, id)) }
    val p0 = project ?: run {
        Screen("Раскраска", onBack = { nav.popBackStack() }) { pad -> Text("Раскраска не найдена", Modifier.padding(pad).padding(16.dp)) }
        return
    }
    val ver = PbnStore.version.intValue
    val pencils = remember(ver) { PbnStore.pencils(ctx) }
    var result by remember { mutableStateOf<PaintByNumbers.Result?>(null) }
    var outline by remember { mutableStateOf<Bitmap?>(null) }
    var colored by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(true) }
    var view by rememberSaveable { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    fun save(n: PbnProject) { project = n; PbnStore.save(ctx, n) }

    LaunchedEffect(p0.colors, p0.detail, ver) {
        busy = true
        val r = PbnStore.build(ctx, p0)
        result = r
        if (r != null) {
            val sc = (1400f / maxOf(r.width, r.height)).coerceAtLeast(1f)
            outline = withContext(Dispatchers.Default) { PbnStore.render(r, false, sc) }
            colored = withContext(Dispatchers.Default) { PbnStore.render(r, true, sc) }
        }
        busy = false
    }
    val name = p0.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").ifBlank { "Раскраска" }
    val pdfExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val r = result
        if (uri != null && r != null) scope.launch {
            busy = true
            message = runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { PbnStore.pdf(ctx, p0, r, it) }; "PDF сохранён — откройте и распечатайте." }
                .getOrElse { "Не удалось сохранить: ${it.message}" }
            busy = false
        }
    }
    fun share() {
        val r = result ?: return
        scope.launch {
            busy = true
            runCatching {
                val f = withContext(Dispatchers.IO) {
                    File(File(ctx.cacheDir, "share").apply { mkdirs() }, "$name.pdf").also { file -> file.outputStream().use { PbnStore.pdf(ctx, p0, r, it) } }
                }
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Раскраска"))
            }.onFailure { message = "Не удалось: ${it.message}" }
            busy = false
        }
    }

    Screen(p0.name, onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Segments(listOf(0 to "Раскраска", 1 to "Как будет", 2 to "Фото"), view, { view = it })
            Gap(8.dp)
            val bmp = when (view) {
                0 -> outline
                1 -> colored
                else -> remember(p0.id) { runCatching { BitmapFactory.decodeFile(PbnStore.photo(ctx, p0.id).absolutePath) }.getOrNull() }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height), contentScale = ContentScale.Fit)
                else Box(Modifier.fillMaxWidth().aspectRatio(1.4f))
                if (busy) CircularProgressIndicator()
            }
            Text("Раскраску можно увеличить в PDF — контуры и цифры векторные, печатаются чётко.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))

            SectionTitle("Настройки")
            Text("Детализация", fontSize = 14.sp)
            Segments(listOf(0 to "Крупно", 1 to "Средне", 2 to "Подробно"), p0.detail, { save(p0.copy(detail = it)) })
            Text("Крупно — большие области, удобно детям; подробно — мелкие детали, дольше раскрашивать.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            Gap(8.dp)
            Text("Цветов: до ${p0.colors} (карандашей у вас: ${pencils.size})", fontSize = 14.sp)
            var k by remember(p0.id) { mutableFloatStateOf(p0.colors.toFloat()) }
            Slider(k, { k = it }, valueRange = 4f..maxOf(5f, minOf(48f, pencils.size.toFloat())), onValueChangeFinished = { save(p0.copy(colors = k.toInt())) })
            Text("Лист для печати", fontSize = 14.sp)
            Segments(listOf(0 to "A4", 1 to "A3"), p0.paper, { save(p0.copy(paper = it)) })
            var title by remember(p0.id) { mutableStateOf(p0.name) }
            Gap(6.dp)
            TextInput(title, { title = it; save(p0.copy(name = it.ifBlank { p0.name })) }, "Название")
            if (pencils.size < 2) Text("Отметьте свои карандаши — пока подбираю из стандартного набора.", color = extra.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

            result?.let { r ->
                SectionTitle("Карандаши для этой картины · ${r.colors.size}")
                val counts = r.counts()
                r.colors.forEachIndexed { i, pc ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(sw(pc.rgb)).border(1.dp, extra.line, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (PaintByNumbers.dark(pc.rgb)) Color.White else Color.Black)
                        }
                        Text("  ${pc.name}", Modifier.weight(1f), fontSize = 14.sp)
                        Text(if (pc.code.startsWith("К")) "свой" else "№ ${pc.code}", fontSize = 12.sp, color = extra.dim)
                        Text("  · ${counts[i]} обл.", fontSize = 12.sp, color = extra.dim)
                    }
                }
                SectionTitle("Печать")
                Button(onClick = { pdfExport.launch("$name.pdf") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Сохранить PDF для печати") }
                OutlinedButton(onClick = { share() }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Поделиться PDF / распечатать") }
                Text("В PDF две страницы: раскраска с номерами и таблицей карандашей и цветной образец — подсказка, как должно получиться.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            }
            message?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            Gap(40.dp)
        }
    }
}

/** «Мои карандаши»: отметить, какие цвета есть (готовые наборы 12–48), и добавить свои. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PencilsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val ver = PbnStore.version.intValue
    val owned = remember(ver) { PbnStore.owned(ctx) }
    val custom = remember(ver) { PbnStore.custom(ctx) }
    var edit by remember { mutableStateOf<MyPencil?>(null) }
    Screen("Мои карандаши", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Отметьте карандаши, которые у вас есть. Раскраски подбирают цвета только из них.", fontSize = 13.sp, color = extra.dim)
            Gap(6.dp)
            Text("Быстро выбрать набор:", fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                PaintByNumbers.SETS.forEach { (t, codes) -> Pill(t, owned == codes.toSet()) { PbnStore.setOwned(ctx, codes.toSet()) } }
                Pill("Снять все", false) { PbnStore.setOwned(ctx, emptySet()) }
            }
            Text("Отмечено: ${owned.size} из ${PaintByNumbers.CATALOG.size} + своих ${custom.size}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 4.dp))
            PaintByNumbers.CATALOG.forEach { pc ->
                val on = pc.code in owned
                Row(Modifier.fillMaxWidth().clickable { PbnStore.setOwned(ctx, if (on) owned - pc.code else owned + pc.code) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(sw(pc.rgb)).border(1.dp, extra.line, RoundedCornerShape(6.dp)))
                    Text("  № ${pc.code} · ${pc.name}", Modifier.weight(1f), fontSize = 14.sp)
                    Checkbox(on, { PbnStore.setOwned(ctx, if (it) owned + pc.code else owned - pc.code) })
                }
            }
            SectionTitle("Свои цвета")
            Text("Если оттенка нет в списке — добавьте его: ползунками, HEX-кодом или касанием «Подобрать по номеру DMC».", fontSize = 12.sp, color = extra.dim)
            custom.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { edit = c }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(sw(c.rgb)).border(1.dp, extra.line, RoundedCornerShape(6.dp)))
                    Text("  ${c.name.ifBlank { "Свой цвет ${c.n}" }}", Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { PbnStore.setCustom(ctx, custom.filter { it.n != c.n }) }) { Text("Удалить", color = extra.danger, fontSize = 12.sp) }
                }
            }
            OutlinedButton(onClick = { edit = MyPencil(0, 0x9E9E9E) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("+ Добавить свой цвет") }
            Gap(40.dp)
        }
    }
    edit?.let { c ->
        ColorPickDialog(if (c.n == 0) "Новый карандаш" else c.name.ifBlank { "Свой цвет" }, c.rgb, c.name, c.n != 0, onDismiss = { edit = null }) { rgb, nm ->
            val cur = PbnStore.custom(ctx)
            val n = if (c.n == 0) (cur.maxOfOrNull { it.n } ?: 0) + 1 else c.n
            val out = MyPencil(n, rgb, nm)
            PbnStore.setCustom(ctx, if (c.n == 0) cur + out else cur.map { if (it.n == c.n) out else it })
            edit = null
        }
    }
}
