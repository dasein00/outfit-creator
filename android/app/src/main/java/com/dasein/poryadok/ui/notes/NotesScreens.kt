@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Note
import com.dasein.poryadok.data.TopItem
import com.dasein.poryadok.data.TopList
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.CheckDot
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.EmojiPicker
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import kotlinx.coroutines.flow.first

@Composable
private fun noteColor(i: Int): Color {
    val list = if (LocalExtra.current.dark) Palette.notes else Palette.notesLight
    val c = list.getOrElse(i) { Color.Transparent }
    return if (c == Color.Transparent) LocalExtra.current.card else c
}

/** Чек-лист хранится в тексте строками «[ ] пункт» / «[x] пункт». */
private fun parseChecklist(body: String): List<Pair<Boolean, String>> =
    body.lines().filter { it.isNotBlank() }.map { l ->
        when {
            l.startsWith("[x] ") || l.startsWith("[X] ") -> true to l.drop(4)
            l.startsWith("[ ] ") -> false to l.drop(4)
            else -> false to l
        }
    }

private fun formatChecklist(items: List<Pair<Boolean, String>>) = items.joinToString("\n") { (d, t) -> (if (d) "[x] " else "[ ] ") + t }

@Composable
fun NotesScreen(nav: NavHostController) {
    val notes by observe(emptyList()) { Graph.dao.notes() }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val extra = LocalExtra.current
    val shown = notes.filter { !it.archived && (query.isBlank() || (it.title + " " + it.body).contains(query, ignoreCase = true)) }
    Screen(
        title = "Заметки",
        onBack = { nav.popBackStack() },
        actions = { IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Default.Search, "Поиск") } },
        fab = {
            FloatingActionButton(onClick = { nav.navigate(Routes.note(0)) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "Новая заметка")
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (searching) OutlinedTextField(
                value = query, onValueChange = { query = it }, placeholder = { Text("Искать в заметках…") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(14.dp),
            )
            if (shown.isEmpty()) Empty("📝", if (query.isBlank()) "Заметок нет" else "Ничего не нашлось", "Мысли, списки покупок, идеи — всё сюда.")
            else LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
            ) {
                val pinned = shown.filter { it.pinned }
                if (pinned.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                    Text("ЗАКРЕПЛЁННЫЕ", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(4.dp))
                }
                items(pinned, key = { it.id }) { n -> NoteCard(n) { nav.navigate(Routes.note(n.id)) } }
                if (pinned.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                    Text("ОСТАЛЬНЫЕ", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(4.dp))
                }
                items(shown.filter { !it.pinned }, key = { it.id }) { n -> NoteCard(n) { nav.navigate(Routes.note(n.id)) } }
            }
        }
    }
}

@Composable
private fun NoteCard(n: Note, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Surface(
        onClick = onClick, shape = RoundedCornerShape(16.dp), color = noteColor(n.color),
        modifier = Modifier.border(1.dp, extra.line, RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(12.dp)) {
            if (n.title.isNotBlank()) Text(n.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (n.checklist) {
                val items = parseChecklist(n.body)
                items.take(6).forEach { (d, t) ->
                    Text(
                        (if (d) "☑ " else "☐ ") + t, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (d) TextDecoration.LineThrough else null, color = if (d) extra.dim else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (items.size > 6) Text("…ещё ${items.size - 6}", fontSize = 12.sp, color = extra.dim)
            } else if (n.body.isNotBlank()) Text(n.body, fontSize = 13.sp, maxLines = 8, overflow = TextOverflow.Ellipsis, color = extra.dim)
            Text(Dates.label(Dates.dayOf(n.updatedAt)), fontSize = 10.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun NoteEditScreen(nav: NavHostController, id: Long) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    var n by remember { mutableStateOf(Note(updatedAt = System.currentTimeMillis())) }
    var loaded by remember { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<Note?>(null) }
    var newItem by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id != 0L) {
            Graph.dao.notes().first().firstOrNull { it.id == id }?.let { n = it; original = it }
            loaded = true
        }
    }
    fun save() {
        val changed = original == null || original != n
        if (loaded && (n.title.isNotBlank() || n.body.isNotBlank()) && changed) {
            val cur = n.copy(updatedAt = System.currentTimeMillis())
            io { Graph.dao.upsertNote(cur) }
        }
        nav.popBackStack()
    }
    BackHandler { save() }
    Screen(
        title = "",
        onBack = { save() },
        actions = {
            IconButton(onClick = { n = n.copy(pinned = !n.pinned) }) {
                Icon(Icons.Default.PushPin, "Закрепить", tint = if (n.pinned) MaterialTheme.colorScheme.primary else extra.dim)
            }
            IconButton(onClick = {
                n = if (n.checklist) n.copy(checklist = false, body = parseChecklist(n.body).joinToString("\n") { it.second })
                else n.copy(checklist = true, body = formatChecklist(n.body.lines().filter { it.isNotBlank() }.map { false to it }))
            }) { Icon(Icons.Default.Checklist, "Чек-лист", tint = if (n.checklist) MaterialTheme.colorScheme.primary else extra.dim) }
            IconButton(onClick = {
                val text = listOf(n.title, n.body.replace("[x] ", "✔ ").replace("[ ] ", "• ")).filter { it.isNotBlank() }.joinToString("\n\n")
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
                ctx.startActivity(android.content.Intent.createChooser(send, "Поделиться заметкой"))
            }) { Icon(Icons.Default.Share, "Поделиться") }
            if (id != 0L) IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, "Удалить") }
        },
    ) { pad ->
        val fieldColors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        )
        Column(
            Modifier.padding(pad).fillMaxSize().background(noteColor(n.color)).imePadding().verticalScroll(rememberScrollState())
        ) {
            TextField(
                n.title, { n = n.copy(title = it) }, placeholder = { Text("Заголовок") },
                textStyle = MaterialTheme.typography.titleLarge, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            )
            if (n.checklist) {
                val items = parseChecklist(n.body)
                items.forEachIndexed { i, (done, text) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        CheckDot(done, extra.ok, onClick = {
                            n = n.copy(body = formatChecklist(items.mapIndexed { j, p -> if (j == i) (!p.first to p.second) else p }))
                        }, size = 22.dp)
                        Text(
                            text, Modifier.weight(1f).padding(start = 10.dp),
                            textDecoration = if (done) TextDecoration.LineThrough else null, color = if (done) extra.dim else MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { n = n.copy(body = formatChecklist(items.filterIndexed { j, _ -> j != i })) }) {
                            Icon(Icons.Default.Close, null, tint = extra.dim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                fun add() {
                    if (newItem.isNotBlank()) {
                        n = n.copy(body = formatChecklist(items + (false to newItem.trim())))
                        newItem = ""
                    }
                }
                TextField(
                    newItem, { newItem = it }, placeholder = { Text("+ Пункт") }, singleLine = true, colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (items.any { it.first }) TextButton(onClick = { n = n.copy(body = formatChecklist(items.filter { !it.first })) }, modifier = Modifier.padding(start = 4.dp)) {
                    Text("Убрать отмеченные")
                }
            } else {
                TextField(
                    n.body, { n = n.copy(body = it) }, placeholder = { Text("Заметка…") },
                    colors = fieldColors, modifier = Modifier.fillMaxWidth(), minLines = 12,
                )
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.notes.indices.forEach { i ->
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(noteColor(i))
                            .border(if (n.color == i) 2.dp else 1.dp, if (n.color == i) MaterialTheme.colorScheme.primary else extra.line, CircleShape)
                            .clickable { n = n.copy(color = i) }
                    )
                }
            }
        }
    }
    if (confirm) ConfirmDialog("Удалить заметку?", "", onDismiss = { confirm = false }) {
        val cur = n
        io { Graph.dao.deleteNote(cur) }
        nav.popBackStack()
    }
}

/* ---------- топы ---------- */

val TOP_IDEAS = listOf(
    "🎬" to "Топ фильмов", "📚" to "Топ книг", "🎵" to "Любимые песни", "🍽" to "Лучшие рестораны",
    "✈️" to "Места, где хочу побывать", "📺" to "Сериалы", "🎮" to "Игры", "🎁" to "Список желаний",
)

@Composable
fun TopsScreen(nav: NavHostController) {
    val lists by observe(emptyList()) { Graph.dao.topLists() }
    val allItems by observe(emptyList()) { Graph.dao.topItems() }
    var create by remember { mutableStateOf(false) }
    val extra = LocalExtra.current
    Screen(
        title = "Мои топы",
        onBack = { nav.popBackStack() },
        fab = { FloatingActionButton(onClick = { create = true }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, "Новый топ") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            if (lists.isEmpty()) item {
                Empty("🏆", "Ваши рейтинги", "Фильмы, книги, места, желания — соберите свои топы и расставьте места.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TOP_IDEAS.forEach { (e, t) -> Pill("$e $t", false) { io { Graph.dao.upsertTopList(TopList(title = t, emoji = e, sort = lists.size)) } } }
                }
            }
            items(lists, key = { it.id }) { l ->
                val mine = allItems.filter { it.listId == l.id }
                Tile(Modifier.padding(bottom = 10.dp), onClick = { nav.navigate(Routes.top(l.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.emoji, fontSize = 30.sp)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(l.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${mine.size} ${plural(mine.size, "пункт", "пункта", "пунктов")}" +
                                    (mine.firstOrNull()?.let { " · №1 ${it.title}" } ?: ""),
                                fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    if (create) TopListDialog(TopList(title = "", sort = lists.size)) { create = false }
}

@Composable
private fun TopListDialog(l: TopList, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(l.title) }
    var emoji by remember { mutableStateOf(l.emoji) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (l.id == 0L) "Новый топ" else "Топ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextInput(title, { title = it }, "Название")
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("🏆", "🎬", "📚", "🎵", "🍽", "✈️", "📺", "🎮", "🎁", "⭐", "❤️", "🌍").forEach { e -> Pill(e, emoji == e) { emoji = e } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) io { Graph.dao.upsertTopList(l.copy(title = title.trim(), emoji = emoji)) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun TopScreen(nav: NavHostController, id: Long) {
    val extra = LocalExtra.current
    val lists by observe(emptyList()) { Graph.dao.topLists() }
    val all by observe(emptyList()) { Graph.dao.topItems() }
    val l = lists.firstOrNull { it.id == id } ?: return
    val entries = all.filter { it.listId == id }
    var newText by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<TopItem?>(null) }
    var editList by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    fun move(i: Int, d: Int) {
        val j = i + d
        if (j !in entries.indices) return
        val m = entries.toMutableList()
        val tmp = m[i]; m[i] = m[j]; m[j] = tmp
        io { Graph.dao.upsertTopItems(m.mapIndexed { k, e -> e.copy(sort = k) }) }
    }
    fun add() {
        if (newText.isBlank()) return
        val t = newText.trim(); newText = ""
        io { Graph.dao.upsertTopItem(TopItem(listId = id, title = t, sort = entries.size)) }
    }
    Screen(
        title = "${l.emoji} ${l.title}",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { editList = true }) { Icon(Icons.Default.Checklist, "Переименовать") }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, "Удалить") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).imePadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                OutlinedTextField(
                    newText, { newText = it }, placeholder = { Text("Добавить в топ…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }),
                    trailingIcon = { IconButton(onClick = { add() }) { Icon(Icons.Default.Add, "Добавить") } },
                )
                Gap(8.dp)
            }
            if (entries.isEmpty()) item { Text("Пока пусто — добавьте первый пункт.", color = extra.dim) }
            items(entries.size, key = { entries[it].id }) { i ->
                val item = entries[i]
                val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(extra.card)
                        .clickable { edit = item }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(medal, fontSize = if (i < 3) 22.sp else 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(width = 34.dp, height = 28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(if (item.rating > 0) "★ ${item.rating}/10" else null, item.note.takeIf { n -> n.isNotBlank() }).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = extra.dim, maxLines = 1)
                    }
                    IconButton(onClick = { move(i, -1) }, enabled = i > 0) { Icon(Icons.Default.ArrowUpward, "Выше", Modifier.size(18.dp)) }
                    IconButton(onClick = { move(i, 1) }, enabled = i < entries.size - 1) { Icon(Icons.Default.ArrowDownward, "Ниже", Modifier.size(18.dp)) }
                }
            }
            if (entries.count { it.rating > 0 } >= 2) item {
                TextButton(onClick = {
                    io { Graph.dao.upsertTopItems(entries.sortedByDescending { it.rating }.mapIndexed { k, e -> e.copy(sort = k) }) }
                }) { Text("Упорядочить по оценке") }
            }
        }
    }
    edit?.let { it0 ->
        var title by remember(it0) { mutableStateOf(it0.title) }
        var note by remember(it0) { mutableStateOf(it0.note) }
        var rating by remember(it0) { mutableStateOf(it0.rating) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("Пункт") },
            text = {
                Column {
                    TextInput(title, { title = it }, "Название")
                    Gap(8.dp)
                    TextInput(note, { note = it }, "Комментарий")
                    Gap(8.dp)
                    Text("Оценка: ${if (rating > 0) "$rating/10" else "нет"}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..10).forEach { r -> Pill("$r", rating == r) { rating = if (rating == r) 0 else r } }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { io { Graph.dao.upsertTopItem(it0.copy(title = title.trim(), note = note, rating = rating)) }; edit = null }) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { io { Graph.dao.deleteTopItem(it0) }; edit = null }) { Text("Удалить", color = extra.danger) }
                    TextButton(onClick = { edit = null }) { Text("Отмена") }
                }
            },
        )
    }
    if (editList) TopListDialog(l) { editList = false }
    if (confirm) ConfirmDialog("Удалить топ «${l.title}»?", "Все пункты тоже удалятся.", onDismiss = { confirm = false }) {
        io { Graph.dao.deleteTopItemsOf(l.id); Graph.dao.deleteTopList(l) }
        nav.popBackStack()
    }
}
