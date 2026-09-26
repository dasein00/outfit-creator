@file:OptIn(ExperimentalFoundationApi::class)

package com.dasein.poryadok.ui.productivity

import androidx.compose.foundation.ExperimentalFoundationApi
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Ic
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Project
import com.dasein.poryadok.data.Subtask
import com.dasein.poryadok.data.TaskItem
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.ColorPicker
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.EmojiPicker
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette

private const val L_TODAY = "today"
private const val L_WEEK = "week"
private const val L_INBOX = "inbox"
private const val L_ALL = "all"
private const val L_MATRIX = "matrix"
private const val L_DONE = "done"

@Composable
fun TasksScreen(nav: NavHostController) {
    val dao = Graph.dao
    val tasks by observe(emptyList()) { dao.tasks() }
    val projects by observe(emptyList()) { dao.projects() }
    val subtasks by observe(emptyList()) { dao.subtasks() }
    var list by rememberSaveable { mutableStateOf(L_TODAY) }
    var quickAdd by remember { mutableStateOf(false) }
    var editProject by remember { mutableStateOf<Project?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val today = Dates.today()
    val open = tasks.filter { !it.done }
    val selectedProject = list.removePrefix("p").toLongOrNull()?.let { id -> projects.firstOrNull { it.id == id } }

    Screen(
        title = "Задачи",
        actions = {
            IconButton(onClick = { nav.navigate(Routes.GOALS) }) { Icon(Icons.Default.TrackChanges, "Цели") }
            IconButton(onClick = { nav.navigate(Routes.FOCUS) }) { Icon(Icons.Default.Timer, "Фокус") }
            if (list == L_DONE) IconAction(Ic.trash, "Очистить") { confirmClear = true }
        },
        fab = {
            if (list != L_DONE) FloatingActionButton(onClick = { quickAdd = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "Добавить")
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            val chips = buildList {
                add(Triple(L_TODAY, "☀️ Сегодня", open.count { it.dueDay != null && it.dueDay <= today }))
                add(Triple(L_WEEK, "📅 7 дней", open.count { it.dueDay != null && it.dueDay in today..today + 6 }))
                add(Triple(L_INBOX, "📥 Входящие", open.count { it.projectId == null }))
                add(Triple(L_ALL, "🗂 Все", open.size))
                add(Triple(L_MATRIX, "⊞ Матрица", -1))
                projects.forEach { p -> add(Triple("p${p.id}", "${p.emoji} ${p.name}", open.count { it.projectId == p.id })) }
                add(Triple(L_DONE, "✓ Выполнено", -1))
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                items(chips, key = { it.first }) { (id, label, count) ->
                    val selected = id == list
                    val project = id.removePrefix("p").toLongOrNull()?.let { pid -> projects.firstOrNull { it.id == pid } }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) MaterialTheme.colorScheme.onSurface else LocalExtra.current.card,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).combinedClickable(
                            onClick = { list = id },
                            onLongClick = { if (project != null) editProject = project },
                        ),
                    ) {
                        Text(
                            if (count > 0) "$label  $count" else label,
                            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                item {
                    Surface(
                        onClick = { editProject = Project(id = 0, name = "") },
                        shape = RoundedCornerShape(20.dp), color = Color.Transparent,
                    ) {
                        Text("+ Список", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }

            when (list) {
                L_MATRIX -> Matrix(open, today, nav)
                else -> {
                    val groups: List<Pair<String, List<TaskItem>>> = when (list) {
                        L_TODAY -> listOf(
                            "Просрочено" to open.filter { it.dueDay != null && it.dueDay < today },
                            "Сегодня" to open.filter { it.dueDay == today },
                        )
                        L_WEEK -> (0..6).map { d -> Dates.label(today + d, today) + " · " + Dates.weekdayShort(today + d) to open.filter { it.dueDay == today + d } } +
                            listOf("Позже" to open.filter { it.dueDay != null && it.dueDay > today + 6 })
                        L_INBOX -> listOf("" to open.filter { it.projectId == null })
                        L_ALL -> listOf("Входящие" to open.filter { it.projectId == null }) +
                            projects.map { p -> "${p.emoji} ${p.name}" to open.filter { it.projectId == p.id } }
                        L_DONE -> listOf("" to tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0 })
                        else -> listOf(
                            "" to open.filter { it.projectId == selectedProject?.id },
                            "Выполнено" to tasks.filter { it.done && it.projectId == selectedProject?.id },
                        )
                    }.filter { it.second.isNotEmpty() }

                    if (groups.isEmpty()) {
                        Empty(
                            if (list == L_DONE) "🗒" else "🌿",
                            if (list == L_DONE) "Пока ничего не выполнено" else "Здесь чисто",
                            if (list == L_DONE) "Отмечайте задачи — они появятся здесь." else "Добавьте задачу кнопкой «+». Можно писать «завтра в 10 !2 #работа».",
                        )
                    } else LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
                        groups.forEach { (title, group) ->
                            if (title.isNotEmpty()) item(key = "h$title") {
                                SectionTitle("$title  ${group.size}")
                            }
                            item(key = "g$title") {
                                Tile(padding = 6.dp) {
                                    group.forEach { t ->
                                        val subs = subtasks.filter { s -> s.taskId == t.id }
                                        TaskRow(
                                            t, projects.firstOrNull { it.id == t.projectId }, subs.count(Subtask::done), subs.size,
                                            onOpen = { nav.navigate(Routes.task(t.id)) }, showProject = list != "p${t.projectId}",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (quickAdd) QuickAddSheet(
        onDismiss = { quickAdd = false },
        defaultDay = if (list == L_TODAY) today else null,
        defaultProject = selectedProject?.id,
    )
    editProject?.let { p -> ProjectDialog(p, onDismiss = { editProject = null }) }
    if (confirmClear) ConfirmDialog(
        "Очистить выполненные?", "Все выполненные задачи будут удалены навсегда.",
        onDismiss = { confirmClear = false },
    ) { io { dao.deleteDoneTasks() } }
}

@Composable
private fun Matrix(open: List<TaskItem>, today: Long, nav: NavHostController) {
    val extra = LocalExtra.current
    fun important(t: TaskItem) = t.priority >= 2
    fun urgent(t: TaskItem) = t.dueDay != null && t.dueDay <= today + 2
    val quads = listOf(
        Triple("Срочно и важно", "Сделать сейчас", extra.danger) to open.filter { important(it) && urgent(it) },
        Triple("Важно, не срочно", "Запланировать", MaterialTheme.colorScheme.primary) to open.filter { important(it) && !urgent(it) },
        Triple("Срочно, не важно", "Делегировать", Palette.item(2)) to open.filter { !important(it) && urgent(it) },
        Triple("Не срочно и не важно", "Отложить или удалить", extra.dim) to open.filter { !important(it) && !urgent(it) },
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).verticalScroll(rememberScrollState())) {
        Text(
            "Важность — приоритет «средний» и выше. Срочность — срок в ближайшие 2 дня.",
            fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(4.dp),
        )
        quads.chunked(2).forEach { rowQuads ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                rowQuads.forEach { (meta, items) ->
                    val (title, hint, color) = meta
                    Tile(Modifier.weight(1f).height(250.dp), padding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = color, shape = RoundedCornerShape(4.dp), modifier = Modifier.height(14.dp).padding(end = 6.dp)) { Text(" ") }
                            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Text(hint, fontSize = 11.sp, color = extra.dim)
                        Gap(6.dp)
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            if (items.isEmpty()) Text("—", color = extra.dim)
                            items.forEach { t ->
                                Text(
                                    "• " + t.title, fontSize = 13.sp, maxLines = 2,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                        .combinedClickable(onClick = { nav.navigate(Routes.task(t.id)) }).padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Gap(96.dp)
    }
}

@Composable
fun ProjectDialog(p: Project, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var emoji by remember { mutableStateOf(p.emoji) }
    var color by remember { mutableStateOf(p.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p.id == 0L) "Новый список" else "Список") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextInput(name, { name = it }, "Название")
                Gap()
                ColorPicker(color) { color = it }
                Gap()
                EmojiPicker(emoji) { emoji = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) io { Graph.dao.upsertProject(p.copy(name = name.trim(), emoji = emoji, color = color)) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (p.id != 0L) TextButton(onClick = { confirmDelete = true }) { Text("Удалить", color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (confirmDelete) ConfirmDialog(
        "Удалить список «${p.name}»?", "Задачи из него переедут во «Входящие».",
        onDismiss = { confirmDelete = false },
    ) {
        io { Graph.dao.detachProject(p.id); Graph.dao.deleteProject(p) }
        onDismiss()
    }
}
