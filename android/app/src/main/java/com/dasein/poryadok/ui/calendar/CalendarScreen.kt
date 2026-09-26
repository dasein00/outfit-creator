@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.calendar

import androidx.activity.compose.BackHandler
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Ic
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.EventItem
import com.dasein.poryadok.data.Reminder
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Repeat
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.CheckDot
import com.dasein.poryadok.ui.common.ColorPicker
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.TimePickDialog
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.productivity.QuickAddSheet
import com.dasein.poryadok.ui.productivity.TaskRow
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.YearMonth

@Composable
fun CalendarScreen(nav: NavHostController, initialTab: Int) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    var addMenu by remember { mutableStateOf(false) }
    var editReminder by remember { mutableStateOf<Reminder?>(null) }
    var quickTask by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(Dates.today()) }
    Screen(
        title = "Календарь",
        actions = { IconButton(onClick = { selected = Dates.today() }) { Icon(Icons.Default.Today, "Сегодня") } },
        fab = {
            Box {
                FloatingActionButton(onClick = { addMenu = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "Добавить")
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text("📅 Событие") }, onClick = { addMenu = false; nav.navigate(Routes.event(0, selected)) })
                    DropdownMenuItem(text = { Text("✅ Задача") }, onClick = { addMenu = false; quickTask = true })
                    DropdownMenuItem(text = { Text("⏰ Напоминалка") }, onClick = {
                        addMenu = false
                        editReminder = Reminder(title = "", day = selected, min = (Dates.nowMinutes() / 60 + 1).coerceAtMost(23) * 60)
                    })
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Месяц") })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Напоминалки") })
            }
            if (tab == 0) MonthView(nav, selected, onSelect = { selected = it }, onReminder = { editReminder = it })
            else RemindersList(onEdit = { editReminder = it })
        }
    }
    editReminder?.let { r -> ReminderDialog(r) { editReminder = null } }
    if (quickTask) QuickAddSheet(onDismiss = { quickTask = false }, defaultDay = selected)
}

@Composable
private fun MonthView(nav: NavHostController, selected: Long, onSelect: (Long) -> Unit, onReminder: (Reminder) -> Unit) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    val events by observe(emptyList()) { dao.events() }
    val tasks by observe(emptyList()) { dao.tasks() }
    val reminders by observe(emptyList()) { dao.reminders() }
    val projects by observe(emptyList()) { dao.projects() }
    val subtasks by observe(emptyList()) { dao.subtasks() }
    var ym by remember { mutableStateOf(YearMonth.from(Dates.day(selected))) }
    val today = Dates.today()

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ym = ym.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "Назад") }
                Text(Dates.monthTitle(ym), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { ym = ym.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "Вперёд") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = extra.dim)
                }
            }
            val first = ym.atDay(1)
            val offset = first.dayOfWeek.value - 1
            val start = first.toEpochDay() - offset
            val weeks = (offset + ym.lengthOfMonth() + 6) / 7
            for (w in 0 until weeks) {
                Row(Modifier.fillMaxWidth()) {
                    for (d in 0 until 7) {
                        val day = start + w * 7 + d
                        val inMonth = YearMonth.from(Dates.day(day)) == ym
                        val dots = buildList {
                            eventsOn(events, day).take(2).forEach { add(Palette.item(it.color)) }
                            if (tasks.any { !it.done && it.dueDay == day }) add(MaterialTheme.colorScheme.primary)
                            if (remindersOn(reminders, day).any { !it.done }) add(extra.dim)
                        }
                        Column(
                            Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (day == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { onSelect(day) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier.size(28.dp).then(
                                    if (day == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    Dates.day(day).dayOfMonth.toString(), fontSize = 14.sp,
                                    color = if (inMonth) MaterialTheme.colorScheme.onSurface else extra.dim.copy(alpha = .5f),
                                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                dots.take(3).forEach { c -> Box(Modifier.size(5.dp).clip(CircleShape).background(c)) }
                            }
                        }
                    }
                }
            }
        }

        val dayEvents = eventsOn(events, selected)
        val dayTasks = tasks.filter { it.dueDay == selected }
        val dayReminders = remindersOn(reminders, selected)
        item { SectionTitle("${Dates.weekdayFull(selected)}, ${Dates.label(selected)}") }
        if (dayEvents.isEmpty() && dayTasks.isEmpty() && dayReminders.isEmpty()) item {
            Tile { Text("Свободный день. Нажмите «+», чтобы запланировать.", color = extra.dim) }
        }
        items(dayEvents, key = { "e" + it.id }) { e ->
            Tile(Modifier.padding(bottom = 8.dp), onClick = { nav.navigate(Routes.event(e.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(38.dp).clip(RoundedCornerShape(2.dp)).background(Palette.item(e.color)))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(e.title, fontWeight = FontWeight.Medium)
                        val time = e.startMin?.let { s -> Dates.time(s) + (e.endMin?.let { "–" + Dates.time(it) } ?: "") } ?: "Весь день"
                        Text(
                            listOfNotNull(time, e.location.takeIf { it.isNotBlank() }, if (e.repeat != "none") "🔁" else null).joinToString(" · "),
                            fontSize = 12.sp, color = extra.dim,
                        )
                    }
                }
            }
        }
        if (dayTasks.isNotEmpty()) item {
            Tile(Modifier.padding(bottom = 8.dp), padding = 6.dp) {
                dayTasks.forEach { t ->
                    val subs = subtasks.filter { it.taskId == t.id }
                    TaskRow(t, projects.firstOrNull { it.id == t.projectId }, subs.count { it.done }, subs.size, onOpen = { nav.navigate(Routes.task(t.id)) })
                }
            }
        }
        if (dayReminders.isNotEmpty()) item {
            Tile(padding = 10.dp) {
                dayReminders.forEach { r -> ReminderRow(r, onEdit = { onReminder(r) }) }
            }
        }
    }
}

@Composable
private fun ReminderRow(r: Reminder, onEdit: () -> Unit) {
    val extra = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onEdit).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckDot(r.done, extra.ok, onClick = { io { Graph.dao.upsertReminder(r.copy(done = !r.done)) } }, size = 24.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(r.title, textDecoration = if (r.done) TextDecoration.LineThrough else null, color = if (r.done) extra.dim else MaterialTheme.colorScheme.onSurface)
            Text(
                "⏰ ${Dates.label(r.day)}, ${Dates.time(r.min)}" + if (r.repeat != "none") " · ${Repeat.of(r.repeat).label.lowercase()}" else "",
                fontSize = 12.sp, color = if (!r.done && Dates.millis(r.day, r.min) < System.currentTimeMillis()) extra.danger else extra.dim,
            )
        }
    }
}

@Composable
private fun RemindersList(onEdit: (Reminder) -> Unit) {
    val reminders by observe(emptyList()) { Graph.dao.reminders() }
    if (reminders.isEmpty()) {
        Empty(Ic.bell, "Напоминалок нет", "Добавьте через «+» — придёт уведомление в нужное время, даже если приложение закрыто.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp)) {
        val open = reminders.filter { !it.done }
        val done = reminders.filter { it.done }
        if (open.isNotEmpty()) item { Tile(padding = 10.dp) { open.forEach { r -> ReminderRow(r) { onEdit(r) } } } }
        if (done.isNotEmpty()) {
            item { SectionTitle("Выполнено", action = "Очистить") { io { done.forEach { Graph.dao.deleteReminder(it) } } } }
            item { Tile(padding = 10.dp) { done.forEach { r -> ReminderRow(r) { onEdit(r) } } } }
        }
    }
}

@Composable
fun ReminderDialog(initial: Reminder, onDismiss: () -> Unit) {
    var r by remember { mutableStateOf(initial) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Напоминалка" else "Изменить напоминалку") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextInput(r.title, { r = r.copy(title = it) }, "О чём напомнить")
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldButton("Дата", Dates.label(r.day), Modifier.weight(1f)) { pickDate = true }
                    FieldButton("Время", Dates.time(r.min), Modifier.weight(1f)) { pickTime = true }
                }
                Gap(10.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Repeat.entries.forEach { rep -> Pill(rep.label, r.repeat == rep.code) { r = r.copy(repeat = rep.code) } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (r.title.isNotBlank()) { val cur = r.copy(title = r.title.trim(), done = false); io { Graph.dao.upsertReminder(cur) } }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (initial.id != 0L) TextButton(onClick = { io { Graph.dao.deleteReminder(initial) }; onDismiss() }) {
                    Text("Удалить", color = LocalExtra.current.danger)
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (pickDate) DatePickDialog(r.day, onDismiss = { pickDate = false }, onPick = { it?.let { d -> r = r.copy(day = d) } }, allowClear = false)
    if (pickTime) TimePickDialog(r.min, onDismiss = { pickTime = false }, onPick = { it?.let { m -> r = r.copy(min = m) } }, allowClear = false)
}

private val REMIND_OPTIONS = listOf(null to "Без напоминания", 0 to "В момент начала", 10 to "За 10 минут", 30 to "За 30 минут", 60 to "За час", 1440 to "За день")

@Composable
fun EventEditScreen(nav: NavHostController, id: Long, day: Long) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    var e by remember { mutableStateOf(EventItem(title = "", day = if (day >= 0) day else Dates.today(), startMin = 10 * 60, endMin = 11 * 60, remindBefore = 10)) }
    var loaded by remember { mutableStateOf(id == 0L) }
    LaunchedEffect(id) {
        if (id != 0L) { dao.eventsNow().firstOrNull { it.id == id }?.let { e = it }; loaded = true }
    }
    var pickDate by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    fun save() {
        if (e.title.isNotBlank() && loaded) { val cur = e.copy(title = e.title.trim()); io { dao.upsertEvent(cur) } }
        nav.popBackStack()
    }
    BackHandler { save() }
    Screen(
        title = if (id == 0L) "Новое событие" else "Событие",
        onBack = { save() },
        actions = {
            if (id != 0L) IconAction(Ic.trash, "Удалить") { confirm = true }
            IconButton(onClick = { save() }) { Icon(Icons.Default.Check, "Сохранить") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            TextInput(e.title, { e = e.copy(title = it) }, "Название")
            Gap(10.dp)
            FieldButton("Дата", "${Dates.weekdayShort(e.day)}, ${Dates.full(e.day)}", Modifier.fillMaxWidth()) { pickDate = true }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Весь день", Modifier.weight(1f))
                Switch(checked = e.startMin == null, onCheckedChange = { all ->
                    e = if (all) e.copy(startMin = null, endMin = null) else e.copy(startMin = 10 * 60, endMin = 11 * 60)
                })
            }
            if (e.startMin != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldButton("Начало", Dates.time(e.startMin ?: 0), Modifier.weight(1f)) { pickStart = true }
                FieldButton("Конец", e.endMin?.let { Dates.time(it) } ?: "—", Modifier.weight(1f)) { pickEnd = true }
            }
            Gap(10.dp)
            TextInput(e.location, { e = e.copy(location = it) }, "Место")
            Gap(10.dp)
            TextInput(e.note, { e = e.copy(note = it) }, "Заметка", singleLine = false, minLines = 2)
            SectionTitle("Повтор")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Repeat.entries.forEach { r -> Pill(r.label, e.repeat == r.code) { e = e.copy(repeat = r.code) } }
            }
            SectionTitle("Напомнить")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                REMIND_OPTIONS.forEach { (v, label) -> Pill(label, e.remindBefore == v) { e = e.copy(remindBefore = v) } }
            }
            SectionTitle("Цвет")
            ColorPicker(e.color) { e = e.copy(color = it) }
            Gap(40.dp)
            if (e.repeat != "none") Text("Повторяющееся событие показывается в календаре во все подходящие дни.", fontSize = 12.sp, color = extra.dim)
        }
    }
    if (pickDate) DatePickDialog(e.day, onDismiss = { pickDate = false }, onPick = { it?.let { d -> e = e.copy(day = d) } }, allowClear = false)
    if (pickStart) TimePickDialog(e.startMin, onDismiss = { pickStart = false }, onPick = { m ->
        if (m != null) {
            val dur = (e.endMin ?: (m + 60)) - (e.startMin ?: m)
            e = e.copy(startMin = m, endMin = (m + dur.coerceAtLeast(15)).coerceAtMost(24 * 60 - 1))
        }
    }, allowClear = false)
    if (pickEnd) TimePickDialog(e.endMin, onDismiss = { pickEnd = false }, onPick = { e = e.copy(endMin = it) })
    if (confirm) ConfirmDialog("Удалить событие?", "«${e.title}» исчезнет из календаря.", onDismiss = { confirm = false }) {
        val cur = e
        io { dao.deleteEvent(cur) }
        nav.popBackStack()
    }
}
