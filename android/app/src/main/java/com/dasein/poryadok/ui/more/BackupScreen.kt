package com.dasein.poryadok.ui.more

import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                "Одна копия — всё ваше: записи, настройки, вид виджета, фото, видео и GIF. Сохраните её в файл (лучше на Google Диск или в «Загрузки»), " +
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
                TextButton(onClick = { picked = BackupSection.entries.toSet() }) { Text("Все") }
                TextButton(onClick = { picked = emptySet() }) { Text("Снять") }
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
            ) { Text("Загрузить копию из файла") }

            busy?.let {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  $it", fontSize = 14.sp)
                }
            }
            message?.let { Tile(Modifier.padding(top = 12.dp)) { Text(it, fontSize = 14.sp) } }
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
                { "Готово: загружено разделов ${chosen.size}, файлов $it." },
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
            title = { Text("Заменить выбранные разделы?") },
            text = {
                Text(
                    "На этом телефоне заменятся: " + chosen.joinToString(", ") { it.title.lowercase() } +
                        ". Остальные разделы не изменятся. Если сомневаетесь — сначала сохраните копию текущих данных.",
                )
            },
            confirmButton = { TextButton(onClick = { confirm = false; onImport(chosen) }) { Text("Загрузить") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что загрузить из копии") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    (if (m.createdAt > 0) "Копия от ${fmt(m.createdAt)}" else "Копия") + (if (m.app.isNotBlank()) " · версия ${m.app}" else ""),
                    fontSize = 13.sp, color = extra.dim,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { chosen = inFile.toSet() }) { Text("Все") }
                    TextButton(onClick = { chosen = emptySet() }) { Text("Снять") }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
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
