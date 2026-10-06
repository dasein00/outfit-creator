package com.dasein.poryadok.ui.recipes

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dasein.poryadok.data.Recipe
import com.dasein.poryadok.system.RecipeShare
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch

/** Кнопки «Поделиться» и «Загрузить» на вкладке «Мои»: перенос своих рецептов на другой телефон. */
@Composable
fun RecipeShareButtons(mine: List<Recipe>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var share by remember { mutableStateOf(false) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            Toast.makeText(ctx, "Загружаю рецепты…", Toast.LENGTH_SHORT).show()
            RecipeShare.import(ctx, uri).onSuccess { r ->
                val msg = buildString {
                    append(if (r.added > 0) "Добавлено рецептов: ${r.added}" else "Новых рецептов нет")
                    if (r.files > 0) append(", фото и видео: ${r.files}")
                    if (r.skipped > 0) append(". Уже были: ${r.skipped}")
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(ctx, "Не удалось загрузить: ${it.message ?: "это не файл рецептов DASEIN"}", Toast.LENGTH_LONG).show() }
        }
    }
    Row {
        OutlinedButton(onClick = { share = true }, enabled = mine.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Поделиться", maxLines = 1) }
        HGap(8.dp)
        OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) {
            Text("Загрузить", maxLines = 1)
        }
    }
    Text(
        "Свои рецепты с фото и видео можно отправить файлом (Telegram, почта, диск) и загрузить на другом телефоне.",
        fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 4.dp),
    )
    if (share) ShareRecipesDialog(mine, mine.map { it.id }.toSet()) { share = false }
}

/** Выбор рецептов для отправки: с видео или без, отправить или сохранить в файл. */
@Composable
fun ShareRecipesDialog(all: List<Recipe>, preselected: Set<Long>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(preselected) }
    var withVideo by remember { mutableStateOf(true) }
    var size by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(chosen, withVideo) { size = RecipeShare.mediaSize(chosen, withVideo) }
    val hasVideo = all.any { it.id in chosen && it.videos.lines().any { v -> v.startsWith("/") } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { RecipeShare.export(chosen, withVideo, it) } }
                .onSuccess { Toast.makeText(ctx, "Сохранено рецептов: ${it ?: 0}", Toast.LENGTH_SHORT).show(); onDismiss() }
                .onFailure { Toast.makeText(ctx, "Не удалось сохранить", Toast.LENGTH_SHORT).show() }
            busy = false
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Поделиться рецептами") },
        text = {
            Column {
                if (all.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { chosen = all.map { it.id }.toSet() }) { Text("Все") }
                    TextButton(onClick = { chosen = emptySet() }) { Text("Снять все") }
                }
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    all.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { chosen = if (r.id in chosen) chosen - r.id else chosen + r.id },
                        ) {
                            Checkbox(r.id in chosen, { chosen = if (r.id in chosen) chosen - r.id else chosen + r.id })
                            Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (hasVideo) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("С видеороликами", modifier = Modifier.weight(1f))
                    Switch(withVideo, { withVideo = it })
                }
                Text(
                    "Выбрано: ${chosen.size}" + if (size > 0) " · фото и видео ≈ ${mb(size)}" else "",
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                )
                if (size > 45L * 1024 * 1024) Text(
                    "Файл большой: в почту может не пройти. Отправьте через Telegram или сохраните на диск.",
                    fontSize = 12.sp, color = extra.warn,
                )
                if (busy) Text("Собираю файл…", fontSize = 12.sp, color = extra.dim)
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty() && !busy, onClick = {
                scope.launch {
                    busy = true
                    runCatching {
                        val f = RecipeShare.exportToCache(ctx, chosen, withVideo)
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                            .putExtra(Intent.EXTRA_SUBJECT, f.nameWithoutExtension).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        ctx.startActivity(Intent.createChooser(send, "Отправить рецепты"))
                    }.onSuccess { onDismiss() }.onFailure { Toast.makeText(ctx, "Не удалось подготовить файл", Toast.LENGTH_SHORT).show() }
                    busy = false
                }
            }) { Text("Отправить") }
        },
        dismissButton = {
            Row {
                TextButton(enabled = chosen.isNotEmpty() && !busy, onClick = { save.launch("Рецепты DASEIN.zip") }) { Text("В файл") }
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") }
            }
        },
    )
}

private fun mb(b: Long) = if (b < 1024 * 1024) "${b / 1024} КБ" else String.format(java.util.Locale.US, "%.1f МБ", b / 1048576.0)
