@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.dasein.poryadok.ui.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaList
import com.dasein.poryadok.data.MediaListItem
import com.dasein.poryadok.data.MediaStatus
import com.dasein.poryadok.logic.MediaCatalog
import com.dasein.poryadok.logic.MediaShelf
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra

/**
 * Полка папок над коллекцией: «Все», свои папки (нажатие — открыть, долгое — переименовать или удалить),
 * умные папки, которые собираются сами, и «+ Папка».
 */
@Composable
fun FolderShelf(
    base: List<MediaItem>,
    folders: List<MediaList>,
    items: List<MediaListItem>,
    selected: String,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (MediaList) -> Unit,
) {
    val now = System.currentTimeMillis()
    val ids = base.map { it.id }.toSet()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        FolderChip("ui:grid", "Все", base.size, selected.isEmpty(), onClick = { onSelect("") })
        folders.forEach { f ->
            val n = items.count { it.listId == f.id && it.mediaId in ids }
            FolderChip(f.glyph.ifBlank { "ui:folder" }, f.name, n, selected == "${f.id}", onClick = { onSelect(if (selected == "${f.id}") "" else "${f.id}") }, onLong = { onEdit(f) })
        }
        MediaShelf.SMART.forEach { s ->
            val n = base.count { s.test(it, now) }
            if (n > 0 || selected == s.key) FolderChip(s.glyph, s.title, n, selected == s.key, smart = true, onClick = { onSelect(if (selected == s.key) "" else s.key) })
        }
        FolderChip("ui:folder", "+ Папка", -1, false, onClick = onNew)
    }
}

@Composable
private fun FolderChip(glyph: String, title: String, count: Int, on: Boolean, smart: Boolean = false, onClick: () -> Unit, onLong: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (on) scheme.onSurface else extra.card,
        border = if (smart && !on) BorderStroke(1.dp, extra.line) else null,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLong),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(glyph, 18.dp)
            Text(
                " $title", fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (on) scheme.surface else scheme.onSurface,
            )
            if (count >= 0) Text(" $count", fontSize = 12.sp, color = if (on) scheme.surface.copy(alpha = 0.7f) else extra.dim)
        }
    }
}

/** Действия с выбранными карточками: в папку, статус, любимое, метка, удалить. */
@Composable
fun BulkActionsDialog(
    kind: Int,
    chosen: List<MediaItem>,
    folders: List<MediaList>,
    currentFolder: Long,
    onNewFolder: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extra = LocalExtra.current
    var step by remember { mutableStateOf("menu") }
    var tag by remember { mutableStateOf("") }
    var pickFolders by remember { mutableStateOf(setOf<Long>()) }
    val now = System.currentTimeMillis()
    when (step) {
        "folder" -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("В какие папки") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (folders.isEmpty()) Text("Папок пока нет — создайте первую.", color = extra.dim)
                    folders.forEach { f ->
                        Row(Modifier.fillMaxWidth().clickable { pickFolders = if (f.id in pickFolders) pickFolders - f.id else pickFolders + f.id }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(f.id in pickFolders, { on -> pickFolders = if (on) pickFolders + f.id else pickFolders - f.id })
                            Glyph(f.glyph.ifBlank { "ui:folder" }, 20.dp)
                            Text("  ${f.name}")
                        }
                    }
                    TextButton(onClick = onNewFolder) { Text("+ Новая папка") }
                }
            },
            confirmButton = {
                TextButton(enabled = pickFolders.isNotEmpty(), onClick = {
                    io { pickFolders.forEach { lid -> chosen.forEach { m -> Graph.extra.addToMediaList(MediaListItem(lid, m.id, addedAt = now)) } } }
                    onDone()
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { step = "menu" }) { Text("Назад") } },
        )
        "status" -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Статус для ${chosen.size}") },
            text = {
                Column {
                    MediaStatus.names(kind).forEachIndexed { i, n ->
                        Text(n, Modifier.fillMaxWidth().clickable {
                            io {
                                chosen.forEach { m ->
                                    Graph.extra.upsertMedia(
                                        m.copy(status = i, finishedDay = if (i == MediaStatus.DONE) m.finishedDay ?: java.time.LocalDate.now().toEpochDay() else m.finishedDay),
                                    )
                                }
                            }
                            onDone()
                        }.padding(vertical = 12.dp), fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { step = "menu" }) { Text("Назад") } },
        )
        "tag" -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Добавить метку") },
            text = {
                Column {
                    TextInput(tag, { tag = it }, "Например: с мамой, пересмотреть, корейское")
                    Text("Метки видны в фильтре «Метки» и в поиске.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = tag.isNotBlank(), onClick = {
                    val t = tag.trim()
                    io { chosen.forEach { m -> Graph.extra.upsertMedia(m.copy(tags = (MediaCatalog.split(m.tags) + t).distinct().joinToString(", "))) } }
                    onDone()
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { step = "menu" }) { Text("Назад") } },
        )
        "delete" -> ConfirmDialog(
            "Удалить ${chosen.size} из коллекции?", "Карточки, оценки и отзывы будут удалены. Это нельзя отменить.",
            onDismiss = { step = "menu" },
        ) {
            io { chosen.forEach { m -> Graph.extra.removeMediaFromLists(m.id); Graph.extra.deleteMedia(m) } }
            onDone()
        }
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Выбрано: ${chosen.size}") },
            text = {
                Column {
                    MenuRow("ui:folder", "В папку…") { step = "folder" }
                    if (currentFolder > 0) MenuRow("ui:folder", "Убрать из этой папки") {
                        io { chosen.forEach { Graph.extra.removeFromMediaList(currentFolder, it.id) } }
                        onDone()
                    }
                    MenuRow("ui:check", "Сменить статус…") { step = "status" }
                    val allFav = chosen.all { it.favorite }
                    MenuRow("habit/28", if (allFav) "Убрать из любимого" else "В любимое") {
                        io { chosen.forEach { Graph.extra.upsertMedia(it.copy(favorite = !allFav)) } }
                        onDone()
                    }
                    MenuRow("ui:pin", "Добавить метку…") { step = "tag" }
                    MenuRow("ui:trash", "Удалить из коллекции", danger = true) { step = "delete" }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun MenuRow(glyph: String, text: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(glyph, 20.dp)
        Text("   $text", fontSize = 16.sp, color = if (danger) LocalExtra.current.danger else MaterialTheme.colorScheme.onSurface)
    }
}
