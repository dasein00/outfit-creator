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
import com.dasein.poryadok.Graph
import com.dasein.poryadok.system.IconSets
import com.dasein.poryadok.ui.common.IconCell
import com.dasein.poryadok.ui.common.IconGroupList
import com.dasein.poryadok.ui.common.IconLibrary
import com.dasein.poryadok.ui.common.IconLibraryTabs
import com.dasein.poryadok.ui.common.ReorderGrid
import com.dasein.poryadok.ui.common.rememberLibraryCounts
import kotlinx.coroutines.launch
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
 * «Иконки» в три раздела: «Мои иконки» — всё, что вы добавили; «Мои наборы» — ваши папки; «Исходный» — встроенные наборы.
 * Нажатие выбирает иконку (можно несколько), удержание с перетаскиванием меняет её место.
 * Кнопка «Редактировать» отражает, поворачивает и наклоняет любую иконку, и свою, и встроенную.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun IconManagerScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val ver = IconSets.version.intValue
    var level by remember { mutableStateOf(IconLibrary.MINE) }
    var open by remember { mutableStateOf<String?>(null) }
    val allFolders = remember(ver) { IconSets.folders(ctx) }
    val folders = allFolders.filter { it.id != IconSets.MINE }
    val builtin = remember { IconLibrary.builtinGroups(false) }
    val folder = if (level == IconLibrary.SETS) folders.firstOrNull { it.id == open } else null
    // Папку могли удалить — возвращаемся к списку.
    if (level == IconLibrary.SETS && open != null && folder == null) open = null
    val keys = remember(level, open, ver) {
        when (level) {
            IconLibrary.MINE -> IconSets.mine(ctx)
            IconLibrary.SETS -> folder?.keys.orEmpty()
            else -> open?.let { IconLibrary.builtinKeys(ctx, it) }.orEmpty()
        }
    }
    val showGrid = level == IconLibrary.MINE || open != null
    val reorderable = level != IconLibrary.BUILTIN
    var selected by remember(level, open) { mutableStateOf<Set<String>>(emptySet()) }
    var dialog by remember { mutableStateOf<String?>(null) } // new, rename, delete, move, copy, deleteIcons, import, builtin, overrides
    var editIcon by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    val (mineCount, setsCount) = rememberLibraryCounts(false)
    // Куда кладутся новые иконки: открытая папка или «Мои иконки».
    val targetFolder = folder?.id ?: IconSets.MINE
    val pickOwn = rememberImagePickerWithCrop(1f, "icons", round = false, png = true, outW = 320) { path ->
        IconSets.addKeys(ctx, targetFolder, listOf(path))
    }
    fun saveOrder(l: List<String>) {
        if (level == IconLibrary.MINE) IconSets.setKeys(ctx, IconSets.MINE, l) else folder?.let { IconSets.setKeys(ctx, it.id, l) }
    }
    fun shift(k: String, d: Int) {
        val i = keys.indexOf(k); val j = (i + d).coerceIn(0, keys.lastIndex)
        if (i >= 0 && i != j) saveOrder(keys.toMutableList().apply { add(j, removeAt(i)) })
    }
    val title = when {
        folder != null -> folder.title
        level == IconLibrary.BUILTIN && open != null -> builtin.firstOrNull { it.id == open }?.title ?: "Иконки"
        else -> "Иконки"
    }

    CompositionLocalProvider(LocalIconEditable provides false) {
        Screen(title, onBack = { if (open != null && level != IconLibrary.MINE) open = null else nav.popBackStack() }) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
                IconLibraryTabs(level, mineCount, setsCount) { level = it; open = null }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { dialog = "import" }, modifier = Modifier.weight(1f)) { Text("+ Набор", fontSize = 13.sp) }
                    OutlinedButton(onClick = { pickOwn() }, modifier = Modifier.weight(1f)) { Text("+ Из галереи", fontSize = 13.sp) }
                    if (folder != null) OutlinedButton(onClick = { dialog = "builtin" }, modifier = Modifier.weight(1f)) { Text("+ Встроенная", fontSize = 13.sp) }
                    else if (level == IconLibrary.SETS) OutlinedButton(onClick = { name = ""; dialog = "new" }, modifier = Modifier.weight(1f)) { Text("+ Папка", fontSize = 13.sp) }
                }
                if (folder != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { IconSets.moveFolder(ctx, folder.id, -1) }) { Text("↑") }
                    TextButton(onClick = { IconSets.moveFolder(ctx, folder.id, 1) }) { Text("↓") }
                    TextButton(onClick = { name = folder.title; dialog = "rename" }) { Text("Имя") }
                    TextButton(onClick = { dialog = "delete" }) { Text("Удалить", color = extra.danger) }
                }
                when {
                    level == IconLibrary.SETS && open == null -> {
                        if (folders.isEmpty()) Text("Своих наборов пока нет. Добавьте набор из картинки или создайте папку.", color = extra.dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                        IconGroupList(folders.map { it.id to it.title }, { id -> folders.firstOrNull { it.id == id }?.keys.orEmpty() }, Modifier.weight(1f).padding(top = 6.dp)) { open = it }
                    }
                    level == IconLibrary.BUILTIN && open == null ->
                        IconGroupList(builtin.map { it.id to it.title }, { IconLibrary.builtinKeys(ctx, it) }, Modifier.weight(1f).padding(top = 6.dp)) { open = it }
                    else -> {
                        Text(
                            if (reorderable) "Нажмите — выбрать (можно несколько). Удерживайте и тащите — поменять место."
                            else "Нажмите — выбрать. Выбранную иконку можно отредактировать или положить в свою папку.",
                            fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp),
                        )
                        if (keys.isEmpty()) Text(
                            if (level == IconLibrary.MINE) "Своих иконок пока нет: добавьте набор из картинки или иконку из галереи."
                            else "Папка пуста. Добавьте набор из картинки, иконку из галереи или встроенную.",
                            color = extra.dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp),
                        )
                        ReorderGrid(
                            keys, 68.dp, Modifier.weight(1f),
                            reorderable = reorderable,
                            onTap = { k -> selected = if (k in selected) selected - k else selected + k },
                            onReorder = ::saveOrder,
                        ) { k, dragging ->
                            val on = k in selected
                            Box(
                                Modifier.padding(4.dp).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(extra.card)
                                    .border(if (on || dragging) 3.dp else 1.dp, if (on || dragging) MaterialTheme.colorScheme.primary else extra.line, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconCell(k, 32.dp)
                                if (on) Text("✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
                                if (IconSets.isEdited(ctx, k)) Text("✎", fontSize = 11.sp, color = extra.dim, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
                            }
                        }
                    }
                }
                // Действия с выбранными.
                if (selected.isNotEmpty()) Column(Modifier.padding(vertical = 8.dp)) {
                    Text("Выбрано: ${selected.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (selected.size == 1 && IconSets.canEdit(selected.first())) {
                            val k = selected.first()
                            Button(onClick = { editIcon = k }) { Text("Редактировать") }
                        }
                        if (folder != null) OutlinedButton(onClick = { dialog = "move" }) { Text("Переместить") }
                        OutlinedButton(onClick = { dialog = "copy" }) { Text(if (folder != null) "Копировать" else "В папку") }
                        if (selected.size == 1 && reorderable) {
                            val k = selected.first()
                            OutlinedButton(onClick = { shift(k, -1) }) { Text("←") }
                            OutlinedButton(onClick = { shift(k, 1) }) { Text("→") }
                        }
                        val edited = selected.filter { IconSets.isEdited(ctx, it) }
                        if (edited.isNotEmpty()) OutlinedButton(onClick = {
                            Graph.scope.launch { edited.forEach { IconSets.revert(ctx, it) } }
                        }) { Text("Вернуть исходный вид") }
                        if (folder != null) OutlinedButton(onClick = { IconSets.removeKeys(ctx, folder.id, selected); selected = emptySet() }) { Text("Убрать из папки") }
                        if (level != IconLibrary.BUILTIN) OutlinedButton(onClick = { dialog = "deleteIcons" }) { Text("Удалить", color = extra.danger) }
                        TextButton(onClick = { selected = if (selected.size == keys.size) emptySet() else keys.toSet() }) {
                            Text(if (selected.size == keys.size) "Снять все" else "Выбрать все")
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
                    if (dialog == "new") { level = IconLibrary.SETS; open = IconSets.createFolder(ctx, name) } else folder?.let { IconSets.renameFolder(ctx, it.id, name) }
                    dialog = null
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Удалить папку «${folder?.title.orEmpty()}»?") },
            text = { Text("Иконки из неё не пропадут — они перейдут в «Мои иконки».") },
            confirmButton = { TextButton(onClick = { folder?.let { IconSets.deleteFolder(ctx, it.id) }; open = null; dialog = null }) { Text("Удалить", color = extra.danger) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
        "move", "copy" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (dialog == "move") "Переместить в папку" else "Копировать в папку") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    allFolders.filter { it.id != folder?.id }.forEach { f ->
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
                    if (folder != null) IconSets.moveKeys(ctx, selected, id) else IconSets.addKeys(ctx, id, selected.toList())
                    selected = emptySet(); level = IconLibrary.SETS; open = id; dialog = null
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
            first?.let { k ->
                IconSets.folderOf(ctx, k)?.let { f -> if (f == IconSets.MINE) { level = IconLibrary.MINE; open = null } else { level = IconLibrary.SETS; open = f } }
            }
        }
        "builtin" -> GlyphPickerDialog("", onDismiss = { dialog = null }) { k -> IconSets.addKeys(ctx, targetFolder, listOf(k)) }
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
