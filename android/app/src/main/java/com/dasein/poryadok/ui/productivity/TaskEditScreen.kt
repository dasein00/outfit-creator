@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.productivity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.Repo
import com.dasein.poryadok.data.Subtask
import com.dasein.poryadok.data.TaskItem
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Repeat
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.CheckDot
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.PRIORITY_NAMES
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TimePickDialog
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.priorityColor
import com.dasein.poryadok.ui.theme.LocalExtra

@Composable
fun TaskEditScreen(nav: NavHostController, id: Long, day: Long, goal: Long) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    var t by remember {
        mutableStateOf(
            TaskItem(
                title = "", dueDay = day.takeIf { it >= 0 }, goalId = goal.takeIf { it > 0 },
                createdAt = System.currentTimeMillis(),
            )
        )
    }
    val subs = remember { mutableStateListOf<Subtask>() }
    val removed = remember { mutableStateListOf<Subtask>() }
    var loaded by remember { mutableStateOf(id == 0L) }
    LaunchedEffect(id) {
        if (id != 0L) {
            dao.taskNow(id)?.let { t = it }
            subs.clear(); subs.addAll(dao.subtasksOfNow(id))
            loaded = true
        }
    }
    val projects by observe(emptyList()) { dao.projects() }
    val goals by observe(emptyList()) { dao.goals() }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    var goalMenu by remember { mutableStateOf(false) }
    var newSub by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save(then: () -> Unit = { nav.popBackStack() }) {
        val cur = t
        if (cur.title.isBlank() || !loaded) { then(); return }
        val subsNow = subs.toList()
        val removedNow = removed.toList()
        io {
            val newId = dao.upsertTask(cur.copy(title = cur.title.trim()))
            val tid = if (cur.id == 0L) newId else cur.id
            removedNow.forEach { dao.deleteSubtask(it) }
            subsNow.forEachIndexed { i, s -> dao.upsertSubtask(s.copy(taskId = tid, sort = i)) }
        }
        then()
    }
    BackHandler { save() }

    Screen(
        title = if (id == 0L) "Новая задача" else "Задача",
        onBack = { save() },
        actions = {
            if (id != 0L) {
                IconButton(onClick = {
                    io { Graph.prefs.update { it.copy(focusTaskId = id) } }
                    save { nav.navigate(Routes.FOCUS) }
                }) { Icon(Icons.Default.Timer, "Фокус на задаче") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Удалить") }
            }
            IconButton(onClick = { save() }) { Icon(Icons.Default.Check, "Сохранить") }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckDot(t.done, priorityColor(t.priority), onClick = {
                    if (t.id != 0L) { io { Repo.toggleTask(t) }; nav.popBackStack() }
                })
                TextField(
                    value = t.title, onValueChange = { t = t.copy(title = it) },
                    placeholder = { Text("Название задачи") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
            OutlinedTextField(
                value = t.note, onValueChange = { t = t.copy(note = it) }, label = { Text("Заметка") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp),
            )

            SectionTitle("Когда")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldButton("Дата", t.dueDay?.let { Dates.label(it) } ?: "Без даты", Modifier.weight(1f)) { pickDate = true }
                FieldButton("Время", t.dueMin?.let { Dates.time(it) } ?: "—", Modifier.weight(1f)) { pickTime = true }
            }
            Gap(8.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = Dates.today()
                Pill("Сегодня", t.dueDay == today) { t = t.copy(dueDay = today) }
                Pill("Завтра", t.dueDay == today + 1) { t = t.copy(dueDay = today + 1) }
                Pill("Через неделю", t.dueDay == today + 7) { t = t.copy(dueDay = today + 7) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Напомнить")
                    Text(
                        if (t.dueDay == null) "Нужна дата" else "В ${Dates.time(t.dueMin ?: 540)} в день задачи",
                        fontSize = 12.sp, color = extra.dim,
                    )
                }
                Switch(checked = t.remind, onCheckedChange = { t = t.copy(remind = it, dueDay = t.dueDay ?: Dates.today()) })
            }

            SectionTitle("Повтор")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Repeat.entries.forEach { r ->
                    Pill(r.label, t.repeat == r.code) {
                        t = t.copy(repeat = r.code, dueDay = if (r != Repeat.NONE) t.dueDay ?: Dates.today() else t.dueDay)
                    }
                }
            }

            SectionTitle("Приоритет")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRIORITY_NAMES.forEachIndexed { i, n -> Pill(if (i > 0) "⚑ $n" else n, t.priority == i) { t = t.copy(priority = i) } }
            }

            SectionTitle("Список и цель")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    val p = projects.firstOrNull { it.id == t.projectId }
                    FieldButton("Список", p?.let { "${it.emoji} ${it.name}" } ?: "📥 Входящие", Modifier.fillMaxWidth()) { projectMenu = true }
                    DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                        DropdownMenuItem(text = { Text("📥 Входящие") }, onClick = { t = t.copy(projectId = null); projectMenu = false })
                        projects.forEach { pr ->
                            DropdownMenuItem(text = { Text("${pr.emoji} ${pr.name}") }, onClick = { t = t.copy(projectId = pr.id); projectMenu = false })
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    val g = goals.firstOrNull { it.id == t.goalId }
                    FieldButton("Цель", g?.let { "${it.emoji} ${it.title}" } ?: "—", Modifier.fillMaxWidth()) { goalMenu = true }
                    DropdownMenu(expanded = goalMenu, onDismissRequest = { goalMenu = false }) {
                        DropdownMenuItem(text = { Text("Без цели") }, onClick = { t = t.copy(goalId = null); goalMenu = false })
                        goals.filter { !it.done }.forEach { gl ->
                            DropdownMenuItem(text = { Text("${gl.emoji} ${gl.title}") }, onClick = { t = t.copy(goalId = gl.id); goalMenu = false })
                        }
                    }
                }
            }
            Gap(10.dp)
            OutlinedTextField(
                value = t.tags, onValueChange = { t = t.copy(tags = it.replace(" ", ",").replace(",,", ",")) },
                label = { Text("Теги через запятую") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )

            SectionTitle("Подзадачи${if (subs.isNotEmpty()) "  ${subs.count { it.done }}/${subs.size}" else ""}")
            subs.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CheckDot(s.done, extra.dim, onClick = { subs[i] = s.copy(done = !s.done) }, size = 22.dp)
                    Text(
                        s.title, Modifier.weight(1f).padding(start = 10.dp),
                        textDecoration = if (s.done) TextDecoration.LineThrough else null,
                        color = if (s.done) extra.dim else MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { subs.removeAt(i); if (s.id != 0L) removed.add(s) }) {
                        Icon(Icons.Default.Close, "Удалить", tint = extra.dim)
                    }
                }
            }
            fun addSub() {
                if (newSub.isNotBlank()) { subs.add(Subtask(taskId = t.id, title = newSub.trim())); newSub = "" }
            }
            OutlinedTextField(
                value = newSub, onValueChange = { newSub = it },
                placeholder = { Text("+ Добавить подзадачу") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addSub() }),
                trailingIcon = { IconButton(onClick = { addSub() }) { Icon(Icons.Default.Check, "Добавить") } },
            )
            if (t.createdAt > 0 && id != 0L) Text(
                "Создана ${Dates.label(Dates.dayOf(t.createdAt))}",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 16.dp),
            )
            Gap(40.dp)
        }
    }

    if (pickDate) DatePickDialog(t.dueDay, onDismiss = { pickDate = false }, onPick = { t = t.copy(dueDay = it, remind = if (it == null) false else t.remind) })
    if (pickTime) TimePickDialog(t.dueMin, onDismiss = { pickTime = false }, onPick = {
        t = t.copy(dueMin = it, dueDay = if (it != null) t.dueDay ?: Dates.today() else t.dueDay, remind = it != null || t.remind)
    })
    if (confirmDelete) ConfirmDialog("Удалить задачу?", "«${t.title}» будет удалена вместе с подзадачами.", onDismiss = { confirmDelete = false }) {
        val cur = t
        io { Repo.deleteTask(cur) }
        nav.popBackStack()
    }
}
