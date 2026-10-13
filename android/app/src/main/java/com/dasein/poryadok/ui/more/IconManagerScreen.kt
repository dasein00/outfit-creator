package com.dasein.poryadok.ui.more

import androidx.compose.foundation.ExperimentalFoundationApi
import com.dasein.poryadok.ui.common.btnLabel
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
import com.dasein.poryadok.ui.common.Button
import androidx.compose.material3.MaterialTheme
import com.dasein.poryadok.ui.common.OutlinedButton
import androidx.compose.material3.Text
import com.dasein.poryadok.ui.common.TextButton
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
    val builtin = remember(ver) { IconLibrary.groups(ctx, false) }
    val group = if (level == IconLibrary.BUILTIN) builtin.firstOrNull { it.id == open } else null
    val folder = if (level == IconLibrary.SETS) folders.firstOrNull { it.id == open } else null
    // Папку могли удалить — возвращаемся к списку.
    if (level == IconLibrary.SETS && open != null && folder == null) open = null
    if (level == IconLibrary.BUILTIN && open != null && group == null) open = null
    val keys = remember(level, open, ver) {
        when (level) {
            IconLibrary.MINE -> IconSets.mine(ctx)
            IconLibrary.SETS -> folder?.keys.orEmpty()
            else -> open?.let { IconLibrary.builtinKeys(ctx, it) }.orEmpty()
        }
    }
    val showGrid = level == IconLibrary.MINE || open != null
    val reorderable = true
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
        when {
            level == IconLibrary.MINE -> IconSets.setKeys(ctx, IconSets.MINE, l)
            folder != null -> IconSets.setKeys(ctx, folder.id, l)
            group != null -> IconLibrary.setGroupKeys(ctx, group.id, l)
        }
    }
    fun shift(k: String, d: Int) {
        val i = keys.indexOf(k); val j = (i + d).coerceIn(0, keys.lastIndex)
        if (i >= 0 && i != j) saveOrder(keys.toMutableList().apply { add(j, removeAt(i)) })
    }
    /** Переложить или скопировать выбранные в свою папку или во встроенный набор. */
    fun sendTo(toFolder: String?, toGroup: String?, move: Boolean) {
        val sel = selected.toList()
        if (move && folder != null && toFolder != null) IconSets.moveKeys(ctx, sel, toFolder)
        else {
            if (move) {
                if (folder != null) IconSets.removeKeys(ctx, folder.id, sel)
                if (group != null) IconLibrary.removeFromGroup(ctx, group.id, sel)
            }
            toFolder?.let { IconSets.addKeys(ctx, it, sel) }
            toGroup?.let { IconLibrary.addToGroup(ctx, it, sel) }
        }
        selected = emptySet()
    }
    val title = when {
        folder != null -> folder.title
        group != null -> group.title
        else -> "Иконки"
    }

    CompositionLocalProvider(LocalIconEditable provides false) {
        Screen(title, onBack = { if (open != null && level != IconLibrary.MINE) open = null else nav.popBackStack() }) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
                IconLibraryTabs(level, mineCount, setsCount) { level = it; open = null }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { dialog = "import" }, modifier = Modifier.weight(1f)) { Text(btnLabel("+ Набор"), fontSize = 13.sp) }
                    OutlinedButton(onClick = { pickOwn() }, modifier = Modifier.weight(1f)) { Text(btnLabel("+ Из галереи"), fontSize = 13.sp) }
                    if (folder != null) OutlinedButton(onClick = { dialog = "builtin" }, modifier = Modifier.weight(1f)) { Text(btnLabel("+ Встроенная"), fontSize = 13.sp) }
                    else if (level == IconLibrary.SETS) OutlinedButton(onClick = { name = ""; dialog = "new" }, modifier = Modifier.weight(1f)) { Text(btnLabel("+ Папка"), fontSize = 13.sp) }
                }
                if (folder != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { IconSets.moveFolder(ctx, folder.id, -1) }) { Text(btnLabel("↑")) }
                    TextButton(onClick = { IconSets.moveFolder(ctx, folder.id, 1) }) { Text(btnLabel("↓")) }
                    TextButton(onClick = { name = folder.title; dialog = "rename" }) { Text(btnLabel("Имя")) }
                    TextButton(onClick = { dialog = "delete" }) { Text(btnLabel("Удалить"), color = extra.danger) }
                }
                if (group != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { IconLibrary.moveGroup(ctx, group.id, -1) }) { Text(btnLabel("↑")) }
                    TextButton(onClick = { IconLibrary.moveGroup(ctx, group.id, 1) }) { Text(btnLabel("↓")) }
                    if (IconLibrary.layout(ctx).keys.containsKey(group.id)) TextButton(onClick = { IconLibrary.resetGroup(ctx, group.id) }) { Text(btnLabel("Как было")) }
                    TextButton(onClick = { dialog = "deleteGroup" }) { Text(btnLabel("Удалить"), color = extra.danger) }
                }
                when {
                    level == IconLibrary.SETS && open == null -> {
                        if (folders.isEmpty()) Text("Своих наборов пока нет. Добавьте набор из картинки или создайте папку.", color = extra.dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                        IconGroupList(folders.map { it.id to it.title }, { id -> folders.firstOrNull { it.id == id }?.keys.orEmpty() }, Modifier.weight(1f).padding(top = 6.dp)) { open = it }
                    }
                    level == IconLibrary.BUILTIN && open == null -> {
                        val hidden = remember(ver) { IconLibrary.hiddenGroups(ctx) }
                        IconGroupList(
                            builtin.map { it.id to it.title }, { IconLibrary.builtinKeys(ctx, it) }, Modifier.weight(1f).padding(top = 6.dp),
                            trailing = { id ->
                                TextButton(onClick = { IconLibrary.moveGroup(ctx, id, -1) }) { Text(btnLabel("↑")) }
                                TextButton(onClick = { IconLibrary.moveGroup(ctx, id, 1) }) { Text(btnLabel("↓")) }
                            },
                        ) { open = it }
                        if (hidden.isNotEmpty()) TextButton(onClick = { IconLibrary.restoreGroups(ctx) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Вернуть удалённые наборы (${hidden.size})")
                        }
                    }
                    else -> {
                        Text(
                            "Нажмите — выбрать (можно несколько). Удерживайте и тащите — поменять место.",
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
                                if (on) Text(btnLabel("✓"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
                                if (IconSets.isEdited(ctx, k)) Text(btnLabel("✎"), fontSize = 11.sp, color = extra.dim, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
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
                            Button(onClick = { editIcon = k }) { Text(btnLabel("Редактировать")) }
                        }
                        if (folder != null || group != null) OutlinedButton(onClick = { dialog = "move" }) { Text(btnLabel("Переместить")) }
                        OutlinedButton(onClick = { dialog = "copy" }) { Text(if (folder != null || group != null) "Копировать" else "В папку") }
                        if (selected.size == 1 && reorderable) {
                            val k = selected.first()
                            OutlinedButton(onClick = { shift(k, -1) }) { Text(btnLabel("←")) }
                            OutlinedButton(onClick = { shift(k, 1) }) { Text(btnLabel("→")) }
                        }
                        val edited = selected.filter { IconSets.isEdited(ctx, it) }
                        if (edited.isNotEmpty()) OutlinedButton(onClick = {
                            Graph.scope.launch { edited.forEach { IconSets.revert(ctx, it) } }
                        }) { Text(btnLabel("Вернуть исходный вид")) }
                        if (folder != null) OutlinedButton(onClick = { IconSets.removeKeys(ctx, folder.id, selected); selected = emptySet() }) { Text(btnLabel("Убрать из папки")) }
                        if (level != IconLibrary.BUILTIN) OutlinedButton(onClick = { dialog = "deleteIcons" }) { Text(btnLabel("Удалить"), color = extra.danger) }
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
                }) { Text(btnLabel("Готово")) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(btnLabel("Отмена")) } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Удалить папку «${folder?.title.orEmpty()}»?") },
            text = { Text("Иконки из неё не пропадут — они перейдут в «Мои иконки».") },
            confirmButton = { TextButton(onClick = { folder?.let { IconSets.deleteFolder(ctx, it.id) }; open = null; dialog = null }) { Text(btnLabel("Удалить"), color = extra.danger) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(btnLabel("Отмена")) } },
        )
        "move", "copy" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (dialog == "move") "Переместить в…" else "Копировать в…") },
            text = {
                val move = dialog == "move"
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(btnLabel("Мои наборы"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    allFolders.filter { it.id != folder?.id }.forEach { f ->
                        TextButton(onClick = { sendTo(f.id, null, move); dialog = null }, modifier = Modifier.fillMaxWidth()) { Text("${f.title} · ${f.keys.size}") }
                    }
                    TextButton(onClick = { name = ""; dialog = "newMove" }, modifier = Modifier.fillMaxWidth()) { Text(btnLabel("+ В новую папку…")) }
                    Text(btnLabel("Исходные наборы"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    builtin.filter { it.id != group?.id }.forEach { g ->
                        TextButton(onClick = { sendTo(null, g.id, move); dialog = null }, modifier = Modifier.fillMaxWidth()) { Text(g.title) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(btnLabel("Отмена")) } },
        )
        "newMove" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(btnLabel("Новая папка")) },
            text = { TextInput(name, { name = it }, "Название") },
            confirmButton = {
                TextButton(onClick = {
                    val id = IconSets.createFolder(ctx, name)
                    sendTo(id, null, folder != null || group != null)
                    level = IconLibrary.SETS; open = id; dialog = null
                }) { Text(btnLabel("Переместить")) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(btnLabel("Отмена")) } },
        )
        "deleteIcons" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Удалить ${selected.size} иконок?") },
            text = { Text("Свои иконки удалятся совсем; встроенные просто уберутся из ваших папок. Там, где удалённая иконка стояла вместо другой, вернётся исходная.") },
            confirmButton = { TextButton(onClick = { IconSets.deleteKeys(ctx, selected); selected = emptySet(); dialog = null }) { Text(btnLabel("Удалить"), color = extra.danger) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(btnLabel("Отмена")) } },
        )
        "import" -> IconSetImportDialog(onDismiss = { dialog = null }) { first ->
            dialog = null
            first?.let { k ->
                IconSets.folderOf(ctx, k)?.let { f -> if (f == IconSets.MINE) { level = IconLibrary.MINE; open = null } else { level = IconLibrary.SETS; open = f } }
            }
        }
        "builtin" -> GlyphPickerDialog("", onDismiss = { dialog = null }) { k -> IconSets.addKeys(ctx, targetFolder, listOf(k)) }
        "deleteGroup" -> group?.let { g ->
            // Набор можно удалить, только если ни одна его иконка не стоит в приложении.
            val used by androidx.compose.runtime.produceState<List<String>?>(null, g.id) {
                val all = IconLibrary.usedKeys(ctx)
                value = IconLibrary.builtinKeys(ctx, g.id).filter { it in all }
            }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Удалить набор «${g.title}»?") },
                text = {
                    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                        val u = used
                        when {
                            u == null -> Text(btnLabel("Проверяю, где стоят иконки этого набора…"), color = extra.dim)
                            u.isEmpty() -> Text("Ни одна иконка из набора не используется. Набор пропадёт из списка; вернуть его можно кнопкой «Вернуть удалённые наборы».")
                            else -> {
                                Text("Нельзя удалить: ${u.size} иконок из набора стоят в приложении (в разделах, записях, настройках или заменах). Перенесите их в другой набор или замените — тогда набор можно будет удалить.")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    u.forEach { k ->
                                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Ink2), contentAlignment = Alignment.Center) { IconCell(k, 24.dp) }
                                    }
                                }
                                TextButton(onClick = { selected = u.toSet(); dialog = null }) { Text(btnLabel("Выбрать их в наборе")) }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (used?.isEmpty() == true) TextButton(onClick = { IconLibrary.hideGroup(ctx, g.id); open = null; dialog = null }) { Text(btnLabel("Удалить"), color = extra.danger) }
                    else TextButton(onClick = { dialog = null }) { Text(btnLabel("Закрыть")) }
                },
                dismissButton = { if (used?.isEmpty() == true) TextButton(onClick = { dialog = null }) { Text(btnLabel("Отмена")) } },
            )
        }
        "overrides" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(btnLabel("Заменённые иконки")) },
            text = {
                val list = IconSets.overrides.value.toList()
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text("Пока ничего не заменено. Удерживайте любую иконку в приложении, чтобы заменить её.", color = extra.dim, fontSize = 13.sp)
                    list.forEach { (from, to) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Ink2), contentAlignment = Alignment.Center) { GlyphRaw(from, null, 26.dp) }
                            Text(btnLabel("  →  "), color = extra.dim)
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Ink2), contentAlignment = Alignment.Center) { GlyphRaw(to, null, 26.dp) }
                            Box(Modifier.weight(1f))
                            TextButton(onClick = { IconSets.setOverride(ctx, from, null) }) { Text(btnLabel("Вернуть")) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(btnLabel("Закрыть")) } },
        )
    }
    editIcon?.let { k -> IconEditorDialog(k) { editIcon = null } }
}
