package com.dasein.poryadok.ui.more

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.system.IconSets
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.GlyphPickerDialog
import com.dasein.poryadok.ui.common.GlyphRaw
import com.dasein.poryadok.ui.common.IconEditorDialog
import com.dasein.poryadok.ui.common.IconSetImportDialog
import com.dasein.poryadok.ui.common.LocalIconEditable
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.rememberImagePickerWithCrop
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette

const val ICON_MANAGER_ROUTE = "iconManager"

/**
 * «Иконки»: папки своих иконок. Всё новое (наборы из картинки, иконки из галереи) попадает в «Мои иконки»,
 * отсюда их можно разложить по папкам, переместить, скопировать, упорядочить, отредактировать или удалить.
 * Папки и порядок видны в окне выбора иконки у любого раздела и кнопки.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun IconManagerScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val ver = IconSets.version.intValue
    val folders = remember(ver) { IconSets.folders(ctx) }
    var folderId by remember { mutableStateOf(IconSets.MINE) }
    val folder = folders.firstOrNull { it.id == folderId } ?: folders.first()
    var selected by remember(folderId) { mutableStateOf<Set<String>>(emptySet()) }
    var dialog by remember { mutableStateOf<String?>(null) } // new, rename, delete, move, copy, deleteIcons, import, builtin, overrides
    var editIcon by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    val pickOwn = rememberImagePickerWithCrop(1f, "icons", round = false, png = true, outW = 320) { path ->
        IconSets.addKeys(ctx, folder.id, listOf(path))
    }

    CompositionLocalProvider(LocalIconEditable provides false) {
        Screen("Иконки", onBack = { nav.popBackStack() }) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
                Text(
                    "Новые иконки попадают в «Мои иконки». Нажмите на иконку, чтобы выбрать (можно несколько), удерживайте — чтобы отредактировать свою.",
                    fontSize = 12.sp, color = extra.dim,
                )
                // Папки.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    folders.forEach { f -> Pill("${f.title} · ${f.keys.size}", f.id == folder.id) { folderId = f.id } }
                    Pill("+ Папка", false) { name = ""; dialog = "new" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { dialog = "import" }, modifier = Modifier.weight(1f)) { Text("+ Набор", fontSize = 13.sp) }
                    OutlinedButton(onClick = { pickOwn() }, modifier = Modifier.weight(1f)) { Text("+ Из галереи", fontSize = 13.sp) }
                    OutlinedButton(onClick = { dialog = "builtin" }, modifier = Modifier.weight(1f)) { Text("+ Встроенная", fontSize = 13.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(folder.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { IconSets.moveFolder(ctx, folder.id, -1) }) { Text("←") }
                    TextButton(onClick = { IconSets.moveFolder(ctx, folder.id, 1) }) { Text("→") }
                    if (folder.id != IconSets.MINE) {
                        TextButton(onClick = { name = folder.title; dialog = "rename" }) { Text("Имя") }
                        TextButton(onClick = { dialog = "delete" }) { Text("Удалить", color = extra.danger) }
                    }
                }
                if (folder.keys.isEmpty()) Text("Папка пуста. Добавьте набор из картинки, иконку из галереи или встроенную.", color = extra.dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                LazyVerticalGrid(GridCells.Adaptive(68.dp), Modifier.weight(1f)) {
                    items(folder.keys, key = { it }) { k ->
                        val on = k in selected
                        Box(
                            Modifier.padding(4.dp).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(extra.card)
                                .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else extra.line, RoundedCornerShape(14.dp))
                                .combinedClickable(
                                    onClick = { selected = if (on) selected - k else selected + k },
                                    onLongClick = { if (k.startsWith("u:")) editIcon = k else selected = selected + k },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphRaw(k, null, 32.dp)
                            if (on) Text("✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
                        }
                    }
                }
                // Действия с выбранными.
                if (selected.isNotEmpty()) Column(Modifier.padding(vertical = 8.dp)) {
                    Text("Выбрано: ${selected.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Button(onClick = { dialog = "move" }) { Text("Переместить") }
                        OutlinedButton(onClick = { dialog = "copy" }) { Text("Копировать") }
                        if (selected.size == 1) {
                            val k = selected.first()
                            OutlinedButton(onClick = { IconSets.moveKey(ctx, folder.id, k, -1) }) { Text("← Раньше") }
                            OutlinedButton(onClick = { IconSets.moveKey(ctx, folder.id, k, 1) }) { Text("Позже →") }
                            if (k.startsWith("u:")) OutlinedButton(onClick = { editIcon = k }) { Text("Править") }
                        }
                        if (folder.id != IconSets.MINE) OutlinedButton(onClick = { IconSets.removeKeys(ctx, folder.id, selected); selected = emptySet() }) { Text("Убрать из папки") }
                        OutlinedButton(onClick = { dialog = "deleteIcons" }) { Text("Удалить", color = extra.danger) }
                        TextButton(onClick = { selected = if (selected.size == folder.keys.size) emptySet() else folder.keys.toSet() }) {
                            Text(if (selected.size == folder.keys.size) "Снять все" else "Выбрать все")
                        }
                    }
                } else TextButton(onClick = { dialog = "overrides" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Заменённые иконки в приложении (${IconSets.overrides.value.size})")
                }
            }
        }
    }

    when (dialog) {
        "new", "rename" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (dialog == "new") "Новая папка" else "Название папки") },
            text = { TextInput(name, { name = it }, "Название") },
            confirmButton = {
                TextButton(onClick = {
                    if (dialog == "new") folderId = IconSets.createFolder(ctx, name) else IconSets.renameFolder(ctx, folder.id, name)
                    dialog = null
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Удалить папку «${folder.title}»?") },
            text = { Text("Иконки из неё не пропадут — они перейдут в «Мои иконки».") },
            confirmButton = { TextButton(onClick = { IconSets.deleteFolder(ctx, folder.id); folderId = IconSets.MINE; dialog = null }) { Text("Удалить", color = extra.danger) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
        "move", "copy" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (dialog == "move") "Переместить в папку" else "Копировать в папку") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    folders.filter { it.id != folder.id }.forEach { f ->
                        TextButton(onClick = {
                            if (dialog == "move") IconSets.moveKeys(ctx, selected, f.id) else IconSets.addKeys(ctx, f.id, selected.toList())
                            selected = emptySet(); dialog = null
                        }, modifier = Modifier.fillMaxWidth()) { Text("${f.title} · ${f.keys.size}") }
                    }
                    TextButton(onClick = { name = ""; dialog = "newMove" }, modifier = Modifier.fillMaxWidth()) { Text("+ В новую папку…") }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
        "newMove" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Новая папка") },
            text = { TextInput(name, { name = it }, "Название") },
            confirmButton = {
                TextButton(onClick = {
                    val id = IconSets.createFolder(ctx, name)
                    IconSets.moveKeys(ctx, selected, id); selected = emptySet(); folderId = id; dialog = null
                }) { Text("Переместить") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
        "deleteIcons" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Удалить ${selected.size} иконок?") },
            text = { Text("Свои иконки удалятся совсем; встроенные просто уберутся из ваших папок. Там, где удалённая иконка стояла вместо другой, вернётся исходная.") },
            confirmButton = { TextButton(onClick = { IconSets.deleteKeys(ctx, selected); selected = emptySet(); dialog = null }) { Text("Удалить", color = extra.danger) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
        "import" -> IconSetImportDialog(onDismiss = { dialog = null }) { first ->
            dialog = null
            first?.let { k -> IconSets.folderOf(ctx, k)?.let { folderId = it } }
        }
        "builtin" -> GlyphPickerDialog("", onDismiss = { dialog = null }) { k -> IconSets.addKeys(ctx, folder.id, listOf(k)) }
        "overrides" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Заменённые иконки") },
            text = {
                val list = IconSets.overrides.value.toList()
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text("Пока ничего не заменено. Удерживайте любую иконку в приложении, чтобы заменить её.", color = extra.dim, fontSize = 13.sp)
                    list.forEach { (from, to) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Ink2), contentAlignment = Alignment.Center) { GlyphRaw(from, null, 26.dp) }
                            Text("  →  ", color = extra.dim)
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Ink2), contentAlignment = Alignment.Center) { GlyphRaw(to, null, 26.dp) }
                            Box(Modifier.weight(1f))
                            TextButton(onClick = { IconSets.setOverride(ctx, from, null) }) { Text("Вернуть") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Закрыть") } },
        )
    }
    editIcon?.let { k -> IconEditorDialog(k) { editIcon = null } }
}
