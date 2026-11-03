@file:OptIn(ExperimentalFoundationApi::class)

package com.dasein.poryadok.ui.productivity

import com.dasein.poryadok.ui.common.cardViewport
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.HowTo
import androidx.compose.foundation.ExperimentalFoundationApi
import com.dasein.poryadok.ui.common.Glyphs
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Ic
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import com.dasein.poryadok.ui.common.safeBottom
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
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
private const val L_HABITS = "habits"
private const val L_DONE = "done"

/** Порядок внутри группы — как в Todoist: сначала со временем (по часам), потом по приоритету, потом по дате создания. */
private val TASK_ORDER = compareBy<TaskItem>({ it.dueMin ?: Int.MAX_VALUE }, { -it.priority }, { it.createdAt })

/**
 * «Задачи» по образцу Todoist, TickTick и Things: умные списки сверху, в «Сегодня» — итог дня кольцом,
 * привычки на сегодня, просроченное с переносом на сегодня одной кнопкой и свёрнутое «Выполнено сегодня».
 * Удержание задачи — быстрое меню: срок, приоритет, удаление. «Привычки» — отдельный список здесь же.
 */
@Composable
fun TasksScreen(nav: NavHostController, onBack: (() -> Unit)? = null, showGoals: Boolean = true) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    val tasks by observe(emptyList()) { dao.tasks() }
    val projects by observe(emptyList()) { dao.projects() }
    val subtasks by observe(emptyList()) { dao.subtasks() }
    var list by rememberSaveable { mutableStateOf(L_TODAY) }
    var quickAdd by remember { mutableStateOf(false) }
    var editProject by remember { mutableStateOf<Project?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<TaskItem?>(null) }
    var showDoneToday by rememberSaveable { mutableStateOf(false) }
    val today = Dates.today()
    val open = tasks.filter { !it.done }
    val selectedProject = list.removePrefix("p").toLongOrNull()?.let { id -> projects.firstOrNull { it.id == id } }

    Screen(
        title = "Задачи",
        onBack = onBack,
        actions = {
            if (showGoals) IconButton(onClick = { nav.navigate(Routes.GOALS) }) { Icon(Icons.Default.TrackChanges, "Цели") }
            if (list == L_DONE) IconAction(Ic.trash, "Очистить") { confirmClear = true }
        },
        fab = {
            if (list != L_DONE) FloatingActionButton(
                elevation = com.dasein.poryadok.ui.common.fabElevation(),
                onClick = { if (list == L_HABITS) nav.navigate(Routes.habitEdit(0)) else quickAdd = true },
                containerColor = com.dasein.poryadok.ui.common.fabColor(),
            ) { Icon(Icons.Default.Add, if (list == L_HABITS) "Новая привычка" else "Добавить") }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            val chips = buildList {
                add(Triple(L_TODAY, "Сегодня", open.count { it.dueDay != null && it.dueDay <= today }))
                add(Triple(L_WEEK, "7 дней", open.count { it.dueDay != null && it.dueDay in today..today + 6 }))
                add(Triple(L_INBOX, "Входящие", open.count { it.projectId == null }))
                add(Triple(L_HABITS, "Привычки", -1))
                add(Triple(L_ALL, "Все", open.size))
                add(Triple(L_MATRIX, "Матрица", -1))
                projects.forEach { p -> add(Triple("p${p.id}", p.name, open.count { it.projectId == p.id })) }
                add(Triple(L_DONE, "Выполнено", -1))
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
                        color = if (selected) MaterialTheme.colorScheme.onSurface else extra.card,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).combinedClickable(
                            onClick = { list = id },
                            onLongClick = { if (project != null) editProject = project },
                        ),
                    ) {
                        Row(Modifier.padding(start = 8.dp, end = if (count > 0) 6.dp else 14.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph(
                                project?.emoji ?: when (id) {
                                    L_TODAY -> "ui:sun"; L_WEEK -> "cal/10"; L_INBOX -> Glyphs.INBOX; L_HABITS -> "d04/00"; L_MATRIX -> "cal/40"; L_DONE -> "ui:check"; else -> "ui:grid"
                                },
                                18.dp,
                            )
                            Text(
                                "  $label",
                                color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            // Счётчик — бейджем, как в Todoist и TickTick.
                            if (count > 0) Text(
                                "$count", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else extra.dim,
                                modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.surface else extra.line).padding(horizontal = 7.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { editProject = Project(id = 0, name = "") },
                        shape = RoundedCornerShape(20.dp), color = Color.Transparent,
                    ) {
                        Text(btnLabel("+ Список"), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }

            when (list) {
                L_MATRIX -> Matrix(open, today, nav)
                L_HABITS -> HabitsContent(nav, Modifier.weight(1f))
                else -> {
                    val doneToday = tasks.filter { it.done && it.doneAt != null && Dates.dayOf(it.doneAt) == today }.sortedByDescending { it.doneAt }
                    val overdue = open.filter { it.overdue(today) && it.dueDay != null && it.dueDay < today }.sortedWith(compareBy<TaskItem> { it.dueDay }.then(TASK_ORDER))
                    val groups: List<Pair<String, List<TaskItem>>> = when (list) {
                        L_TODAY -> listOf(
                            "Просрочено" to overdue,
                            "Сегодня" to open.filter { it.dueDay == today }.sortedWith(TASK_ORDER),
                        )
                        L_WEEK -> (0..6).map { d -> Dates.label(today + d, today) + " · " + Dates.weekdayShort(today + d) to open.filter { it.dueDay == today + d }.sortedWith(TASK_ORDER) } +
                            listOf("Позже" to open.filter { it.dueDay != null && it.dueDay > today + 6 }.sortedWith(compareBy<TaskItem> { it.dueDay }.then(TASK_ORDER)))
                        L_INBOX -> listOf("" to open.filter { it.projectId == null }.sortedWith(compareBy<TaskItem> { it.dueDay ?: Long.MAX_VALUE }.then(TASK_ORDER)))
                        L_ALL -> listOf("Входящие" to open.filter { it.projectId == null }) +
                            projects.map { p -> p.name to open.filter { it.projectId == p.id } }
                        L_DONE -> listOf("" to tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0 })
                        else -> listOf(
                            "" to open.filter { it.projectId == selectedProject?.id }.sortedWith(compareBy<TaskItem> { it.dueDay ?: Long.MAX_VALUE }.then(TASK_ORDER)),
                            "Выполнено" to tasks.filter { it.done && it.projectId == selectedProject?.id },
                        )
                    }.filter { it.second.isNotEmpty() }

                    LazyColumn(modifier = Modifier.cardViewport(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        if (list == L_TODAY) {
                            item(key = "hero") {
                                val dueToday = open.count { it.dueDay != null && it.dueDay <= today }
                                TodayHero(doneToday.size, dueToday, overdue.size, today)
                            }
                            // Привычки на сегодня — здесь же, над задачами.
                            item(key = "habits") { TodayHabitsStrip(nav) { list = L_HABITS } }
                            // Цели — полосой над задачами: прогресс, срок (он же в календаре) и быстрый переход.
                            if (!showGoals) item(key = "goals") { GoalsStrip(nav) }
                        } else if (!showGoals && list == L_ALL) item(key = "goals") { GoalsStrip(nav) }

                        if (groups.isEmpty() && !(list == L_TODAY && doneToday.isNotEmpty())) item(key = "empty") {
                            Empty(
                                if (list == L_DONE) "ui:check" else "ui:leaves",
                                when (list) { L_DONE -> "Пока ничего не выполнено"; L_TODAY -> "На сегодня всё"; else -> "Здесь чисто" },
                                when (list) {
                                    L_DONE -> "Отмечайте задачи — они появятся здесь."
                                    L_TODAY -> "Свободный день. Добавьте задачу кнопкой «+» — можно писать «в 18 позвонить маме !2»."
                                    else -> "Добавьте задачу кнопкой «+». Можно писать «завтра в 10 !2 #работа»."
                                },
                            )
                        }
                        groups.forEach { (title, group) ->
                            if (title.isNotEmpty()) item(key = "h$title") {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { SectionTitle("$title  ${group.size}") }
                                    // Как в Todoist: всё просроченное — на сегодня одной кнопкой.
                                    if (title == "Просрочено") androidx.compose.material3.TextButton(onClick = {
                                        io { group.forEach { dao.upsertTask(it.copy(dueDay = today)) } }
                                    }) { Text(btnLabel("Перенести на сегодня"), fontSize = 13.sp) }
                                }
                            }
                            item(key = "g$title") {
                                Tile(padding = 6.dp) {
                                    group.forEach { t ->
                                        val subs = subtasks.filter { s -> s.taskId == t.id }
                                        TaskRow(
                                            t, projects.firstOrNull { it.id == t.projectId }, subs.count(Subtask::done), subs.size,
                                            onOpen = { nav.navigate(Routes.task(t.id)) }, showProject = list != "p${t.projectId}",
                                            onLongClick = { menuFor = t },
                                        )
                                    }
                                }
                            }
                        }
                        // Выполненное за сегодня — свёрнуто, по нажатию раскрывается.
                        if (list == L_TODAY && doneToday.isNotEmpty()) {
                            item(key = "doneTodayHead") {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable { showDoneToday = !showDoneToday }.padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Glyph("ui:check", 18.dp)
                                    Text("  Выполнено сегодня  ${doneToday.size}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = extra.dim, modifier = Modifier.weight(1f))
                                    Icon(if (showDoneToday) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = extra.dim)
                                }
                            }
                            if (showDoneToday) item(key = "doneToday") {
                                Tile(padding = 6.dp) {
                                    doneToday.forEach { t ->
                                        TaskRow(t, projects.firstOrNull { it.id == t.projectId }, 0, 0, onOpen = { nav.navigate(Routes.task(t.id)) }, onLongClick = { menuFor = t })
                                    }
                                }
                            }
                        }
                        item(key = "howto") { HowTo("tasks") }
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
    menuFor?.let { t -> TaskQuickMenu(t, today, nav) { menuFor = null } }
    editProject?.let { p -> ProjectDialog(p, onDismiss = { editProject = null }) }
    if (confirmClear) ConfirmDialog(
        "Очистить выполненные?", "Все выполненные задачи будут удалены навсегда.",
        onDismiss = { confirmClear = false },
    ) { io { dao.deleteDoneTasks() } }
}

/** Итог дня, как в TickTick и Things: кольцо «сделано из запланированного», дата и что осталось. */
@Composable
private fun TodayHero(done: Int, left: Int, overdue: Int, today: Long) {
    val extra = LocalExtra.current
    val total = done + left
    val progress = if (total == 0) 0f else done / total.toFloat()
    Tile(Modifier.padding(top = 2.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.dasein.poryadok.ui.common.ProgressRing(progress, if (left == 0 && total > 0) extra.ok else MaterialTheme.colorScheme.primary, size = 58.dp) {
                Text(if (total == 0) "—" else "$done/$total", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("${Dates.weekdayFull(today)}, ${Dates.short(today)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        total == 0 -> "Планов на сегодня нет"
                        left == 0 -> "Всё сделано — отличный день!"
                        overdue > 0 -> "Осталось $left, из них просрочено $overdue"
                        else -> "Осталось $left из $total"
                    },
                    fontSize = 13.sp, color = if (overdue > 0) extra.danger else extra.dim,
                )
            }
        }
    }
}

/**
 * Быстрые действия по удержанию задачи (как в Todoist): срок — сегодня, завтра, через неделю, без срока;
 * приоритет; выполнить; удалить.
 */
@Composable
private fun TaskQuickMenu(t: TaskItem, today: Long, nav: NavHostController, onDismiss: () -> Unit) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    var confirmDelete by remember { mutableStateOf(false) }
    fun save(n: TaskItem) { io { dao.upsertTask(n) }; onDismiss() }
    com.dasein.poryadok.ui.common.AppSheet(onDismissRequest = onDismiss, contentWindowInsets = com.dasein.poryadok.ui.common.NoSheetInsets) {
        Column(Modifier.padding(horizontal = 16.dp).safeBottom()) {
            Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            t.whenText(today)?.let { Text(it, fontSize = 12.sp, color = if (t.overdue(today)) extra.danger else extra.dim) }
            SectionTitle("Срок")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("Сегодня", "ui:sun", today), Triple("Завтра", "cal/10", today + 1), Triple("Через неделю", "cal/00", today + 7), Triple("Без срока", "cal/22", null))
                    .forEach { (label, glyph, day) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (t.dueDay == day) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else extra.card)
                                .clickable { save(t.copy(dueDay = day, dueMin = if (day == null) null else t.dueMin, remind = if (day == null) false else t.remind)) }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Glyph(glyph, 22.dp)
                            Text(label, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
            }
            SectionTitle("Приоритет")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Нет", 1 to "Низкий", 2 to "Средний", 3 to "Высокий").forEach { (p, label) ->
                    val c = if (p == 0) extra.dim else com.dasein.poryadok.ui.common.priorityColor(p)
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (t.priority == p) c.copy(alpha = .18f) else extra.card)
                            .clickable { save(t.copy(priority = p)) }.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Flag, null, tint = c, modifier = Modifier.size(16.dp))
                        Text(" $label", fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.dasein.poryadok.ui.common.OutlinedButton(onClick = { io { com.dasein.poryadok.Repo.toggleTask(t) }; onDismiss() }, modifier = Modifier.weight(1f)) {
                    Text(btnLabel(if (t.done) "Вернуть" else "Выполнить"))
                }
                com.dasein.poryadok.ui.common.OutlinedButton(onClick = { onDismiss(); nav.navigate(Routes.task(t.id)) }, modifier = Modifier.weight(1f)) { Text(btnLabel("Открыть")) }
            }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text(btnLabel("Удалить задачу"), color = extra.danger) }
        }
    }
    if (confirmDelete) ConfirmDialog("Удалить «${t.title}»?", "Задача и её подзадачи удалятся.", onDismiss = { confirmDelete = false }) {
        io { dao.deleteTask(t) }
        onDismiss()
    }
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
        Hint("tasks_matrix", "Важность — приоритет «средний» и выше. Срочность — срок в ближайшие 2 дня.", Modifier.padding(4.dp), title = "Как устроена матрица")
        quads.chunked(2).forEach { rowQuads ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                rowQuads.forEach { (meta, items) ->
                    val (title, hint, color) = meta
                    Tile(Modifier.weight(1f).height(250.dp), padding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = color, shape = RoundedCornerShape(4.dp), modifier = Modifier.height(14.dp).padding(end = 6.dp)) { Text(btnLabel(" ")) }
                            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Text(hint, fontSize = 11.sp, color = extra.dim)
                        Gap(6.dp)
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            if (items.isEmpty()) Text(btnLabel("—"), color = extra.dim)
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
            }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = {
            com.dasein.poryadok.ui.common.ButtonsRow {
                if (p.id != 0L) TextButton(onClick = { confirmDelete = true }) { Text(btnLabel("Удалить"), color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
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


/** Цели над списком задач: прогресс, дедлайн (виден и в календаре), «+ Цель» и «Все цели». */
@Composable
internal fun GoalsStrip(nav: NavHostController) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    val goals by observe(emptyList()) { dao.goals() }
    val tasks by observe(emptyList()) { dao.tasks() }
    val milestones by observe(emptyList()) { dao.milestones() }
    val active = goals.filter { !it.done }.sortedBy { it.deadline ?: Long.MAX_VALUE }
    val today = Dates.today()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(btnLabel("Цели"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = { nav.navigate(Routes.GOALS) }) { Text(btnLabel("Все цели"), fontSize = 13.sp) }
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        items(active, key = { "g" + it.id }) { g ->
            val gt = tasks.filter { it.goalId == g.id }
            val gm = milestones.filter { it.goalId == g.id }
            val pr = com.dasein.poryadok.ui.today.goalProgress(g, gt.count { it.done }, gt.size, gm.count { it.done }, gm.size)
            Tile(Modifier.width(170.dp), onClick = { nav.navigate(Routes.goal(g.id)) }, padding = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(g.emoji, 20.dp)
                    Text(" ${(pr * 100).toInt()}%", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Text(g.title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 14.sp)
                androidx.compose.material3.LinearProgressIndicator(progress = { pr }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                g.deadline?.let { d ->
                    val left = d - today
                    Text(
                        if (left < 0) "срок прошёл" else if (left == 0L) "срок сегодня" else "до ${Dates.label(d)} · $left дн.",
                        fontSize = 11.sp, color = if (left <= 7) extra.danger else extra.dim,
                    )
                }
            }
        }
        item {
            Tile(Modifier.width(110.dp), onClick = { nav.navigate(Routes.goal(0)) }, padding = 10.dp) {
                Text(btnLabel("+ Цель"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(btnLabel("этапы, задачи, срок"), fontSize = 11.sp, color = extra.dim)
            }
        }
    }
}
