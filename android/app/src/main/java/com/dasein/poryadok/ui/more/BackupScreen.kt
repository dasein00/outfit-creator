package com.dasein.poryadok.ui.more

import android.net.Uri
import com.dasein.poryadok.ui.common.btnLabel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.dasein.poryadok.ui.common.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.data.BackupSection
import com.dasein.poryadok.system.BackupFiles
import com.dasein.poryadok.system.BackupManifest
import com.dasein.poryadok.system.UiStateBackup
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * «Резервное копирование»: сохранить всё или отмеченные разделы в один файл (данные, настройки, фото, видео, GIF)
 * и загрузить на этом или другом телефоне — тоже целиком или только выбранные разделы.
 */
@Composable
fun BackupScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(BackupSection.entries.toSet()) }
    var everything by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var manifest by remember { mutableStateOf<BackupManifest?>(null) }
    var profileUri by remember { mutableStateOf<Uri?>(null) }
    var restored by remember { mutableStateOf(false) }
    val summary by produceState<Map<BackupSection, Pair<Int, Int>>?>(null, refresh) { value = runCatching { BackupFiles.localSummary(ctx) }.getOrNull() }
    val last = remember(refresh) { UiStateBackup.last(ctx) }
    val chosen = if (everything) BackupSection.entries.toSet() else picked

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = "Сохраняю копию…"
            message = BackupFiles.export(ctx, uri, chosen).fold(
                { "Копия сохранена: разделов ${chosen.size}, файлов $it." },
                { "Не удалось сохранить: ${it.message}" },
            )
            busy = null; refresh++
        }
    }
    val profileExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = "Сохраняю оформление…"
            message = com.dasein.poryadok.system.UiProfile.export(ctx, uri).fold(
                { "Файл оформления сохранён (иконок и служебных файлов: $it)." },
                { "Не удалось сохранить: ${it.message}" },
            )
            busy = null
        }
    }
    // Один вход для любого файла: полная копия — выбор разделов, файл оформления — применить оформление.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = "Читаю файл…"
            if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.dasein.poryadok.system.UiProfile.isProfile(ctx, uri) }) profileUri = uri
            else BackupFiles.inspect(ctx, uri).fold({ manifest = it; importUri = uri }, { message = "Не удалось прочитать файл: ${it.message}" })
            busy = null
        }
    }
    fun applyProfile(uri: Uri) = scope.launch {
        busy = "Применяю оформление…"
        message = com.dasein.poryadok.system.UiProfile.import(ctx, uri).fold(
            { restored = true; "Оформление загружено (файлов: $it). Перезапустите приложение, чтобы всё применилось." },
            { "Не удалось загрузить: ${it.message}" },
        )
        busy = null
    }

    Screen("Резервная копия", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Состояние: когда была копия и сколько всего данных.
            val stale = last == 0L || System.currentTimeMillis() - last > 7 * 86_400_000L
            Tile(color = (if (stale) extra.warn else MaterialTheme.colorScheme.primary).copy(alpha = .12f)) {
                Text(if (last > 0) "Последняя копия" else "Копии ещё нет", fontSize = 13.sp, color = extra.dim)
                Text(
                    if (last > 0) fmt(last) else "Сохраните первую",
                    fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                    color = if (stale) extra.warn else MaterialTheme.colorScheme.primary,
                )
                summary?.let { m ->
                    val rec = m.values.sumOf { it.first }; val files = m.values.sumOf { it.second }
                    Text("На телефоне: записей $rec · файлов $files (фото, видео, книги, иконки)", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
                }
                if (stale) Text("Делайте копию раз в неделю — так ничего не потеряется при смене телефона.", fontSize = 12.sp, color = extra.dim)
            }

            BackupCard(Modifier.padding(top = 16.dp), "Сохранить копию", "Все разделы с фото, видео и файлами, избранное и настройки оформления — в один файл.") {
                Segments(listOf(true to "Всё", false to "Выбрать разделы"), everything, { everything = it })
                if (!everything) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Отмечено ${picked.size} из ${BackupSection.entries.size}", Modifier.weight(1f), fontSize = 13.sp, color = extra.dim)
                        TextButton(onClick = { picked = BackupSection.entries.toSet() }) { Text(btnLabel("Все")) }
                        TextButton(onClick = { picked = emptySet() }) { Text(btnLabel("Снять")) }
                    }
                    BackupSection.entries.forEach { s ->
                        SectionRow(s, s in picked, localSub(s, summary?.get(s))) { on -> picked = if (on) picked + s else picked - s }
                    }
                } else summary?.let { m ->
                    Text(
                        BackupSection.entries.filter { (m[it]?.first ?: 0) + (m[it]?.second ?: 0) > 0 || it == BackupSection.SETTINGS }.joinToString(" · ") { it.title },
                        fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { exportLauncher.launch(fileName(chosen)) }, enabled = chosen.isNotEmpty() && busy == null,
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    ) { Text(btnLabel("Сохранить в файл")) }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = "Готовлю копию…"
                                shareFile(ctx, fileName(chosen), "Копия DASEIN") { BackupFiles.export(ctx, it, chosen) }.onFailure { message = "Не удалось: ${it.message}" }
                                busy = null; refresh++
                            }
                        },
                        enabled = chosen.isNotEmpty() && busy == null, modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    ) { Text(btnLabel("Отправить")) }
                }
                Text("«Отправить» — сразу в Telegram, на Google Диск или почтой.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            }

            BackupCard(Modifier.padding(top = 12.dp), "Импортировать данные", "Выберите файл копии или файл оформления — покажу, что в нём есть, и вы отметите, что загрузить. Остальное на телефоне не изменится.") {
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = busy == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) { Text(btnLabel("Выбрать файл")) }
            }

            BackupCard(
                Modifier.padding(top = 12.dp), "Сжать видео",
                "Видео рецептов и тренировок, добавленные раньше, пережимаются как можно компактнее: 360p, 24 кадра/с, звук моно — минута около 2 МБ. Новые сжимаются так сами.",
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = "Ищу видео…"
                            val (n, saved) = runCatching { com.dasein.poryadok.system.VideoCompress.recompressAll(ctx) { busy = it } }.getOrDefault(0 to 0L)
                            busy = null; refresh++
                            message = if (n == 0) "Все видео уже компактные." else "Пережато видео: $n · освобождено ${com.dasein.poryadok.system.VideoCompress.mb(saved)}"
                        }
                    },
                    enabled = busy == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(btnLabel("Сжать все видео")) }
            }

            BackupCard(
                Modifier.padding(top = 12.dp), "Оформление",
                "Только вид приложения: тема и цвета, шрифт, тексты и названия кнопок, свои иконки, главная и виджет. Записей, PIN и ключей нет. " +
                    "«Отправить в чат» — пришлите файл Claude, и ваше оформление станет стандартным при установке.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { profileExport.launch("DASEIN-оформление.zip") }, enabled = busy == null, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) {
                        Text(btnLabel("Сохранить"))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                busy = "Готовлю оформление…"
                                shareFile(ctx, "DASEIN-оформление.zip", "Оформление DASEIN") { com.dasein.poryadok.system.UiProfile.export(ctx, it) }.onFailure { message = "Не удалось: ${it.message}" }
                                busy = null
                            }
                        },
                        enabled = busy == null, modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    ) { Text(btnLabel("Отправить в чат")) }
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = busy == null,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp),
                ) { Text(btnLabel("Импортировать оформление")) }
            }

            busy?.let {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  $it", fontSize = 14.sp)
                }
            }
            message?.let { Tile(Modifier.padding(top = 12.dp)) { Text(it, fontSize = 14.sp) } }
            if (restored) {
                Gap(8.dp)
                Button(onClick = { restartApp(ctx) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(btnLabel("Перезапустить приложение")) }
            }
            HowTo("backup")
            Gap(40.dp)
        }
    }

    val m = manifest
    val uri = importUri
    if (m != null && uri != null) ImportPicker(m, onDismiss = { manifest = null; importUri = null }) { secs ->
        manifest = null; importUri = null
        scope.launch {
            busy = "Загружаю копию…"
            message = BackupFiles.import(ctx, uri, secs).fold(
                { restored = true; "Готово: загружено разделов ${secs.size}, файлов $it. Перезапустите приложение — тогда иконки, названия кнопок, главная и оформление применятся полностью." },
                { "Не удалось загрузить: ${it.message}" },
            )
            busy = null; refresh++
        }
    }
    profileUri?.let { pu ->
        AlertDialog(
            onDismissRequest = { profileUri = null },
            title = { Text(btnLabel("Применить оформление?")) },
            text = { Text("Это файл оформления: тема, цвета, шрифт, тексты кнопок, иконки, главная и виджет. Записи на телефоне не изменятся.") },
            confirmButton = { TextButton(onClick = { profileUri = null; applyProfile(pu) }) { Text(btnLabel("Применить")) } },
            dismissButton = { TextButton(onClick = { profileUri = null }) { Text(btnLabel("Отмена")) } },
        )
    }
}

/** Карточка-блок экрана копии: заголовок, пояснение и действия. */
@Composable
private fun BackupCard(modifier: Modifier, title: String, about: String, content: @Composable () -> Unit) {
    val extra = LocalExtra.current
    Tile(modifier) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(about, fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        content()
    }
}

private fun localSub(s: BackupSection, counts: Pair<Int, Int>?): String = when {
    counts == null -> "…"
    s == BackupSection.SETTINGS -> "настройки и оформление" + if (counts.second > 0) " · файлов ${counts.second}" else ""
    s == BackupSection.CRAFTS -> "файлов ${counts.second}"
    s == BackupSection.PRESSURE -> if (counts.second > 0) "дневник давления" else "пусто"
    s == BackupSection.TUTOR -> if (counts.second > 0) "прогресс обучения" else "пусто"
    s == BackupSection.READER || s == BackupSection.LIKES -> if (counts.second > 0) "файлов ${counts.second}" else "пусто"
    else -> "записей ${counts.first}" + if (counts.second > 0) " · файлов ${counts.second}" else ""
}

/** Сохранить файл во временную папку и сразу открыть «Поделиться» (мессенджер, диск, почта, чат с Claude). */
internal suspend fun shareFile(ctx: android.content.Context, name: String, title: String, write: suspend (Uri) -> Result<Int>): Result<Unit> = runCatching {
    val f = java.io.File(java.io.File(ctx.cacheDir, "share").apply { mkdirs() }, name)
    write(Uri.fromFile(f)).getOrThrow()
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/zip")
        .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(android.content.Intent.createChooser(send, title).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun restartApp(ctx: android.content.Context) {
    val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
    if (i != null) {
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(i)
    }
    (ctx as? android.app.Activity)?.finishAffinity()
    Runtime.getRuntime().exit(0)
}

@Composable
private fun SectionRow(s: BackupSection, on: Boolean, sub: String, onChange: (Boolean) -> Unit) {
    val extra = LocalExtra.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, onChange)
        Glyph(s.glyph, 26.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(s.title, fontWeight = FontWeight.Medium)
            Text(s.about, fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Выбор разделов из файла копии перед загрузкой. */
@Composable
private fun ImportPicker(m: BackupManifest, onDismiss: () -> Unit, onImport: (Set<BackupSection>) -> Unit) {
    val extra = LocalExtra.current
    val inFile = m.sections.mapNotNull { n -> BackupSection.entries.firstOrNull { it.name == n } }
    var chosen by remember { mutableStateOf(inFile.toSet()) }
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(btnLabel("Заменить выбранные разделы?")) },
            text = {
                Text(
                    "На этом телефоне заменятся: " + chosen.joinToString(", ") { it.title.lowercase() } +
                        ". Остальные разделы не изменятся. Если сомневаетесь — сначала сохраните копию текущих данных.",
                )
            },
            confirmButton = { TextButton(onClick = { confirm = false; onImport(chosen) }) { Text(btnLabel("Загрузить")) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(btnLabel("Отмена")) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Что загрузить из копии")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    (if (m.createdAt > 0) "Копия от ${fmt(m.createdAt)}" else "Копия") + (if (m.app.isNotBlank()) " · версия ${m.app}" else ""),
                    fontSize = 13.sp, color = extra.dim,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { chosen = inFile.toSet() }) { Text(btnLabel("Все")) }
                    TextButton(onClick = { chosen = emptySet() }) { Text(btnLabel("Снять")) }
                }
                if (inFile.isEmpty()) Text("В файле нет разделов, которые можно загрузить.")
                inFile.forEach { s ->
                    val rec = m.records[s.name] ?: 0
                    val files = m.files[s.name] ?: 0
                    SectionRow(
                        s, s in chosen,
                        if (s == BackupSection.CRAFTS) "файлов $files"
                        else if (s == BackupSection.SETTINGS) "настройки и оформление" + if (files > 0) " · файлов $files" else ""
                        else if (s == BackupSection.LIKES) "файлов $files"
                        else "записей $rec" + if (files > 0) " · файлов $files" else "",
                    ) { on -> chosen = if (on) chosen + s else chosen - s }
                }
            }
        },
        confirmButton = { TextButton(onClick = { confirm = true }, enabled = chosen.isNotEmpty()) { Text(if (chosen.size == inFile.size) "Загрузить всё" else "Загрузить (${chosen.size})") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

private fun fmt(ms: Long): String =
    DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** «DASEIN-копия-2026-10-02.zip» или «DASEIN-рецепты-2026-10-02.zip» для одного раздела. */
private fun fileName(sections: Set<BackupSection>): String {
    val day = java.time.LocalDate.now().toString()
    val what = when {
        sections.size == BackupSection.entries.size -> "копия"
        sections.size == 1 -> sections.first().title.lowercase().substringBefore(' ').replace(',', ' ').trim()
        else -> "выборочно"
    }
    return "DASEIN-$what-$day.zip"
}
