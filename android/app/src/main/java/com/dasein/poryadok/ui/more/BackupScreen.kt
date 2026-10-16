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
import androidx.compose.material3.Text
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
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var manifest by remember { mutableStateOf<BackupManifest?>(null) }
    var restored by remember { mutableStateOf(false) }
    val summary by produceState<Map<BackupSection, Pair<Int, Int>>?>(null, refresh) { value = runCatching { BackupFiles.localSummary(ctx) }.getOrNull() }
    val last = remember(refresh) { UiStateBackup.last(ctx) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = "Сохраняю копию…"
            message = BackupFiles.export(ctx, uri, picked).fold(
                { "Копия сохранена: разделов ${picked.size}, файлов $it." },
                { "Не получилось сохранить: ${it.message}" },
            )
            busy = null; refresh++
        }
    }
    val profileExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = "Сохраняю оформление…"
            message = com.dasein.poryadok.system.UiProfile.export(ctx, uri).fold(
                { "Файл оформления сохранён (иконок и служебных файлов: $it). Пришлите его в чат с Claude — он сделает эти настройки стандартными для приложения." },
                { "Не получилось сохранить: ${it.message}" },
            )
            busy = null
        }
    }
    val profileImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = "Применяю оформление…"
            message = com.dasein.poryadok.system.UiProfile.import(ctx, uri).fold(
                { restored = true; "Оформление загружено (файлов: $it). Перезапустите приложение, чтобы всё применилось." },
                { "Не получилось загрузить: ${it.message}" },
            )
            busy = null
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = "Читаю копию…"
            BackupFiles.inspect(ctx, uri).fold({ manifest = it; importUri = uri }, { message = "Не удалось прочитать файл: ${it.message}" })
            busy = null
        }
    }

    Screen("Резервное копирование", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Одна копия — точная копия профиля: записи, рецепты и тренировки с видео, карточки блюд, фильмов, сериалов и книг, свои иконки и наборы, названия кнопок, главная, тема, виджет и все настройки. Сохраните её в файл (лучше на Google Диск или в «Загрузки»), " +
                    "а на другом телефоне откройте этот же раздел и нажмите «Загрузить копию». Можно сохранить и загрузить не всё, а только отмеченные разделы.",
                fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
            )
            Gap(6.dp)
            Text(
                if (last > 0) "Последняя копия: " + fmt(last) else "Копию ещё ни разу не сохраняли",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (last == 0L || System.currentTimeMillis() - last > 7 * 86_400_000L) extra.warn else MaterialTheme.colorScheme.primary,
            )

            SectionTitle("Что сохранить")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Отмечено ${picked.size} из ${BackupSection.entries.size}", Modifier.weight(1f), fontSize = 13.sp, color = extra.dim)
                TextButton(onClick = { picked = BackupSection.entries.toSet() }) { Text(btnLabel("Все")) }
                TextButton(onClick = { picked = emptySet() }) { Text(btnLabel("Снять")) }
            }
            BackupSection.entries.forEach { s ->
                val counts = summary?.get(s)
                SectionRow(
                    s, s in picked,
                    when {
                        counts == null -> "…"
                        s == BackupSection.SETTINGS -> "настройки приложения" + if (counts.second > 0) " · файлов ${counts.second}" else ""
                        s == BackupSection.CRAFTS -> "схем ${counts.second / 2}"
                        s == BackupSection.PRESSURE -> if (counts.second > 0) "дневник давления" else "пусто"
                        s == BackupSection.TUTOR -> if (counts.second > 0) "прогресс обучения" else "пусто"
                        s == BackupSection.READER -> if (counts.second > 0) "файлов ${counts.second}" else "пусто"
                        else -> "записей ${counts.first}" + if (counts.second > 0) " · файлов ${counts.second}" else ""
                    },
                ) { on -> picked = if (on) picked + s else picked - s }
            }
            Gap(10.dp)
            Button(
                onClick = { exportLauncher.launch(fileName(picked)) }, enabled = picked.isNotEmpty() && busy == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) { Text(if (picked.size == BackupSection.entries.size) "Сохранить полную копию" else "Сохранить выбранное (${picked.size})") }

            SectionTitle("Загрузить копию")
            Text(
                "Выберите файл копии — покажу, какие разделы в нём есть. Отметьте нужные: они заменятся данными из копии, " +
                    "а остальные разделы на этом телефоне останутся как есть.",
                fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
            )
            Gap(8.dp)
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = busy == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) { Text(btnLabel("Загрузить копию из файла")) }

            SectionTitle("Файл оформления")
            Text(
                "Только вид приложения, без записей: как расставлены плашки на главной, названия разделов и кнопок, свои иконки и замены, " +
                    "тема и цвет, вид виджета, город погоды. PIN и ключи сюда не попадают. Файл можно загрузить на другом телефоне — " +
                    "или прислать в чат с Claude, чтобы эти настройки встроили в приложение и они появлялись сразу после установки.",
                fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
            )
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { profileExport.launch("DASEIN-оформление.zip") }, enabled = busy == null,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                ) { Text(btnLabel("Выгрузить оформление")) }
                OutlinedButton(
                    onClick = { profileImport.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = busy == null,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                ) { Text(btnLabel("Загрузить оформление")) }
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
                Button(
                    onClick = {
                        val act = ctx as? android.app.Activity
                        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                        if (i != null) {
                            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            ctx.startActivity(i)
                        }
                        act?.finishAffinity()
                        Runtime.getRuntime().exit(0)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) { Text(btnLabel("Перезапустить приложение")) }
            }
            HowTo("backup")
            Gap(40.dp)
        }
    }

    val m = manifest
    val uri = importUri
    if (m != null && uri != null) ImportPicker(m, onDismiss = { manifest = null; importUri = null }) { chosen ->
        manifest = null; importUri = null
        scope.launch {
            busy = "Загружаю копию…"
            message = BackupFiles.import(ctx, uri, chosen).fold(
                { restored = true; "Готово: загружено разделов ${chosen.size}, файлов $it. Перезапустите приложение — тогда иконки, названия кнопок, главная и оформление применятся полностью." },
                { "Не получилось загрузить: ${it.message}" },
            )
            busy = null; refresh++
        }
    }
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
                        if (s == BackupSection.CRAFTS) "схем ${files / 2}"
                        else if (s == BackupSection.SETTINGS) "настройки" + if (files > 0) " · файлов $files" else ""
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
