@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.calendar

import com.dasein.poryadok.ui.common.swipeTabs
import com.dasein.poryadok.ui.common.tabSlide
import com.dasein.poryadok.ui.common.swipeTabsOf
import androidx.activity.compose.BackHandler
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.Glyphs
import com.dasein.poryadok.ui.common.Glyph
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
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
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
import com.dasein.poryadok.ui.common.HGap
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
fun CalendarScreen(nav: NavHostController, initialTab: Int, onBack: (() -> Unit)? = null) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    var addMenu by remember { mutableStateOf(false) }
    var newVacation by remember { mutableStateOf(false) }
    var editReminder by remember { mutableStateOf<Reminder?>(null) }
    var quickTask by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(Dates.today()) }
    Screen(
        title = "Календарь",
        onBack = onBack,
        actions = {
            IconButton(onClick = { nav.navigate(com.dasein.poryadok.ui.more.LIKES_ROUTE) }) { Text(btnLabel("♥"), fontSize = 20.sp, color = com.dasein.poryadok.ui.common.LikeGreen) }
            IconButton(onClick = { selected = Dates.today() }) { Icon(Icons.Default.Today, "Сегодня") }
        },
        fab = {
            Box {
                FloatingActionButton(onClick = { addMenu = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "Добавить")
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text(btnLabel("Событие")) }, leadingIcon = { Glyph(Glyphs.EVENT, 20.dp) }, onClick = { addMenu = false; nav.navigate(Routes.event(0, selected)) })
                    DropdownMenuItem(text = { Text(btnLabel("Задача")) }, leadingIcon = { Glyph(Glyphs.CHECK, 20.dp) }, onClick = { addMenu = false; quickTask = true })
                    DropdownMenuItem(text = { Text(btnLabel("Отпуск")) }, leadingIcon = { Glyph("ui:sun", 20.dp) }, onClick = { addMenu = false; newVacation = true })
                    DropdownMenuItem(text = { Text(btnLabel("Напоминалка")) }, leadingIcon = { Glyph(Glyphs.REMINDER, 20.dp) }, onClick = {
                        addMenu = false
                        editReminder = Reminder(title = "", day = selected, min = (Dates.nowMinutes() / 60 + 1).coerceAtMost(23) * 60)
                    })
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).swipeTabsOf(listOf(0, 2, 3, 4, 1), tab) { tab = it }) {
            androidx.compose.material3.ScrollableTabRow(
                selectedTabIndex = when (tab) { 2 -> 1; 3 -> 2; 4 -> 3; 1 -> 4; else -> 0 }, containerColor = MaterialTheme.colorScheme.background, edgePadding = 8.dp,
            ) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text(btnLabel("Месяц"), fontSize = 13.sp, maxLines = 1) })
                Tab(tab == 2, onClick = { tab = 2 }, text = { Text(btnLabel("Праздники"), fontSize = 13.sp, maxLines = 1) })
                Tab(tab == 3, onClick = { tab = 3 }, text = { Text(btnLabel("История"), fontSize = 13.sp, maxLines = 1) })
                Tab(tab == 4, onClick = { tab = 4 }, text = { Text(btnLabel("Культура"), fontSize = 13.sp, maxLines = 1) })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text(btnLabel("Напоминалки"), fontSize = 13.sp, maxLines = 1, softWrap = false) })
            }
            Column(Modifier.tabSlide(listOf(0, 2, 3, 4, 1).indexOf(tab))) {
                when (tab) {
                    0 -> MonthView(nav, selected, onSelect = { selected = it }, onReminder = { editReminder = it })
                    2 -> HolidaysTab(nav, selected, onSelect = { selected = it })
                    3 -> HistoryTab(selected)
                    4 -> CultureTab(selected)
                    else -> RemindersList(onEdit = { editReminder = it })
                }
            }
        }
    }
    editReminder?.let { r -> ReminderDialog(r) { editReminder = null } }
    if (quickTask) QuickAddSheet(onDismiss = { quickTask = false }, defaultDay = selected)
    if (newVacation) VacationDialog(null, selected) { newVacation = false }
}

@Composable
private fun MonthView(nav: NavHostController, selected: Long, onSelect: (Long) -> Unit, onReminder: (Reminder) -> Unit) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    val events by observe(emptyList()) { dao.events() }
    val tasks by observe(emptyList()) { dao.tasks() }
    // Сроки целей тоже в календаре — рядом с задачами.
    val goals by observe(emptyList()) { dao.goals() }
    val reminders by observe(emptyList()) { dao.reminders() }
    val projects by observe(emptyList()) { dao.projects() }
    val subtasks by observe(emptyList()) { dao.subtasks() }
    var ym by remember { mutableStateOf(YearMonth.from(Dates.day(selected))) }
    val today = Dates.today()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val custom by observe(emptyList()) { Graph.days.customDays() }
    val marks by observe(emptyList()) { Graph.days.marks() }
    // Праздники и выделения дней месяца считаются один раз на месяц.
    val monthInfo = remember(ym, custom, marks) {
        val first = ym.atDay(1).toEpochDay() - 7
        (first..first + 49).associateWith { d ->
            val ld = Dates.day(d)
            Triple(
                com.dasein.poryadok.logic.ProdCalendar.kind(ld),
                com.dasein.poryadok.data.HolidayRepo.highlight(ctx, ld, custom, marks),
                // Точкой отмечаются только свои дни и праздники, которые вы выделили цветом.
                com.dasein.poryadok.data.HolidayRepo.highlight(ctx, ld, custom, marks) != null,
            )
        }
    }
    val red = Color(0xFFD25B4B)
    val vacations = com.dasein.poryadok.data.VacationStore.list.value
    val vacationColor = Palette.item(6)
    var vacationEdit by remember { mutableStateOf<Pair<com.dasein.poryadok.logic.Vacation?, Long>?>(null) }
    vacationEdit?.let { (v, day) -> VacationDialog(v, day) { vacationEdit = null } }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
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
                        val info = monthInfo[day]
                        val kind = info?.first
                        val off = kind == com.dasein.poryadok.logic.DayKind.WEEKEND || kind == com.dasein.poryadok.logic.DayKind.HOLIDAY
                        // Праздник или перенесённый выходной в будни — нерабочий день, который легко пропустить: подсвечиваем фоном.
                        val holidayOff = kind == com.dasein.poryadok.logic.DayKind.HOLIDAY || (off && Dates.day(day).dayOfWeek.value <= 5)
                        val onVacation = com.dasein.poryadok.logic.VacationMath.on(vacations, day) != null
                        val dots = buildList {
                            if (info?.third == true) add(info!!.second?.let { Color(it) } ?: Color(0xFFC7A46A))
                            eventsOn(events, day).take(2).forEach { add(Palette.item(it.color)) }
                            if (tasks.any { !it.done && it.dueDay == day }) add(MaterialTheme.colorScheme.primary)
                            if (goals.any { !it.done && it.deadline == day }) add(extra.danger)
                            if (remindersOn(reminders, day).any { !it.done }) add(extra.dim)
                        }
                        Column(
                            Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        day == selected -> MaterialTheme.colorScheme.primaryContainer
                                        info?.second != null && inMonth -> Color(info!!.second!!).copy(alpha = .22f)
                                        onVacation && inMonth -> vacationColor.copy(alpha = .26f)
                                        holidayOff && inMonth -> red.copy(alpha = .12f)
                                        else -> Color.Transparent
                                    },
                                )
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
                                    Dates.day(day).dayOfMonth.toString() + if (kind == com.dasein.poryadok.logic.DayKind.SHORT) "*" else "", fontSize = 14.sp,
                                    color = when {
                                        !inMonth -> extra.dim.copy(alpha = .5f)
                                        off -> red
                                        kind == com.dasein.poryadok.logic.DayKind.SHORT -> extra.warn
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (day == today || kind == com.dasein.poryadok.logic.DayKind.HOLIDAY) FontWeight.Bold else FontWeight.Normal,
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
        item {
            val k = com.dasein.poryadok.logic.ProdCalendar.kind(Dates.day(selected))
            val weekday = Dates.day(selected).dayOfWeek.value <= 5
            SectionTitle(
                "${Dates.weekdayFull(selected)}, ${Dates.label(selected)} · " + when (k) {
                    com.dasein.poryadok.logic.DayKind.WORK -> "рабочий"
                    com.dasein.poryadok.logic.DayKind.SHORT -> "сокращённый на час"
                    com.dasein.poryadok.logic.DayKind.HOLIDAY -> "нерабочий праздничный"
                    com.dasein.poryadok.logic.DayKind.WEEKEND -> if (weekday) "нерабочий (перенос)" else "выходной"
                } + if (com.dasein.poryadok.logic.VacationMath.on(vacations, selected) != null) " · отпуск" else "",
            )
        }
        val dayHolidays = com.dasein.poryadok.data.HolidayRepo.entries(ctx, Dates.day(selected), custom, marks, hiddenCats(ctx))
        items(dayHolidays, key = { "h" + it.key }) { e -> ExpandableEntry(e, selected) }
        val dayGoals = goals.filter { !it.done && it.deadline == selected }
        items(dayGoals, key = { "g" + it.id }) { g ->
            Tile(Modifier.padding(bottom = 8.dp), onClick = { nav.navigate(Routes.goal(g.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(g.emoji, 24.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("Срок цели: ${g.title}", fontWeight = FontWeight.Medium)
                        Text(btnLabel("дедлайн — сегодня в календаре"), fontSize = 12.sp, color = extra.danger)
                    }
                }
            }
        }
        if (selected == today) {
            val overdue = tasks.filter { !it.done && it.dueDay != null && it.dueDay < today }
            if (overdue.isNotEmpty()) item {
                Tile(Modifier.padding(bottom = 8.dp), onClick = { nav.navigate(Routes.TASKS) }) {
                    Text("Просрочено задач: ${overdue.size} — " + overdue.take(3).joinToString { it.title }, color = extra.danger, fontSize = 13.sp, maxLines = 2)
                }
            }
        }
        if (dayEvents.isEmpty() && dayTasks.isEmpty() && dayReminders.isEmpty() && dayHolidays.isEmpty() && dayGoals.isEmpty()) item {
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
                            listOfNotNull(time, e.location.takeIf { it.isNotBlank() }, if (e.repeat != "none") "повтор" else null).joinToString(" · "),
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
        item {
            VacationTile(vacations, ym, vacationColor, onAdd = { vacationEdit = null to selected }, onEdit = { vacationEdit = it to it.start })
        }
        item {
            val st = com.dasein.poryadok.logic.ProdCalendar.month(ym.year, ym.monthValue)
            Tile(Modifier.padding(top = 10.dp), padding = 10.dp) {
                Text("Производственный календарь · ${Dates.monthTitle(ym)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "Рабочих дней ${st.workDays} · выходных и праздничных ${st.offDays}" + (if (st.shortDays > 0) " · сокращённых ${st.shortDays}" else "") +
                        " · норма ${st.hours40} ч при 40-часовой неделе",
                    fontSize = 12.sp, color = extra.dim,
                )
                Text(
                    "Красным — выходные и праздники; красным фоном — нерабочие дни в будни (праздники и переносы); * — сокращённый на час предпраздничный день; " +
                        "фоном цвета мяты — ваш отпуск. Точкой отмечены ваши дни и праздники, выделенные цветом. " +
                        if (com.dasein.poryadok.logic.ProdCalendar.exact(ym.year)) "С учётом переносов по постановлению Правительства РФ." else "Переносы на этот год ещё не утверждены — расчёт по общим правилам ТК РФ.",
                    fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        item { HowTo("calendar") }
    }
}

/** Отпуска года: список, сколько дней из 28 уже запланировано, кнопка «+ Отпуск». */
@Composable
private fun VacationTile(
    vacations: List<com.dasein.poryadok.logic.Vacation>, ym: YearMonth, color: Color,
    onAdd: () -> Unit, onEdit: (com.dasein.poryadok.logic.Vacation) -> Unit,
) {
    val extra = LocalExtra.current
    val year = ym.year
    val from = java.time.LocalDate.of(year, 1, 1).toEpochDay()
    val to = java.time.LocalDate.of(year, 12, 31).toEpochDay()
    val mine = vacations.filter { it.end >= from && it.start <= to }
    val used = com.dasein.poryadok.logic.VacationMath.daysInYear(vacations, year)
    val inMonth = com.dasein.poryadok.logic.VacationMath.daysInMonth(vacations, year, ym.monthValue)
    Tile(Modifier.padding(top = 10.dp), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Отпуск · $year", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    (if (used > 0) "Запланировано $used из 28 дн." else "Отпуск ещё не запланирован") +
                        if (inMonth > 0) " · в этом месяце $inMonth дн." else "",
                    fontSize = 12.sp, color = extra.dim,
                )
            }
            TextButton(onClick = onAdd) { Text(btnLabel("+ Отпуск")) }
        }
        mine.forEach { v ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onEdit(v) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(v.title.ifBlank { "Отпуск" }, fontWeight = FontWeight.Medium)
                    Text(
                        "${Dates.label(v.start)} — ${Dates.label(v.end)} · ${com.dasein.poryadok.logic.VacationMath.days(v)} дн." +
                            com.dasein.poryadok.logic.VacationMath.holidays(v).let { h -> if (h > 0) " + праздников $h" else "" },
                        fontSize = 12.sp, color = extra.dim,
                    )
                }
            }
        }
    }
}

/** Добавить или изменить отпуск: даты начала и конца, название; сразу видно, сколько дней он займёт. */
@Composable
private fun VacationDialog(initial: com.dasein.poryadok.logic.Vacation?, day: Long, onDismiss: () -> Unit) {
    val extra = LocalExtra.current
    var start by remember { mutableStateOf(initial?.start ?: day) }
    var end by remember { mutableStateOf(initial?.end ?: (day + 13)) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var pick by remember { mutableStateOf(0) }
    var range by remember { mutableStateOf(initial == null) }
    var deleting by remember { mutableStateOf(false) }
    // Даты отпуска: в календаре — первый день, потом последний, или вручную.
    if (range) com.dasein.poryadok.ui.common.DateRangeDialog(start, end, onDismiss = { range = false }, title = "Даты отпуска") { f, t -> start = f; end = t; range = false }
    if (pick != 0) DatePickDialog(if (pick == 1) start else end, { pick = 0 }, { d ->
        if (d != null) {
            if (pick == 1) { start = d; if (end < d) end = d } else { end = d; if (start > d) start = d }
        }
    }, allowClear = false)
    if (deleting && initial != null) ConfirmDialog("Удалить отпуск?", "${Dates.label(initial.start)} — ${Dates.label(initial.end)}", onDismiss = { deleting = false }) {
        com.dasein.poryadok.data.VacationStore.remove(initial); onDismiss()
    }
    val v = com.dasein.poryadok.logic.Vacation(start, end, title.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый отпуск" else "Отпуск") },
        text = {
            Column {
                com.dasein.poryadok.ui.common.OutlinedButton(onClick = { range = true }, modifier = Modifier.fillMaxWidth()) { Text(btnLabel("Отметить даты в календаре")) }
                Gap(8.dp)
                Row {
                    FieldButton("Начало", Dates.label(start), Modifier.weight(1f)) { pick = 1 }
                    HGap(8.dp)
                    FieldButton("Конец", Dates.label(end), Modifier.weight(1f)) { pick = 2 }
                }
                Gap(12.dp)
                TextInput(title, { title = it.take(60) }, "Название (необязательно)")
                Gap(10.dp)
                val h = com.dasein.poryadok.logic.VacationMath.holidays(v)
                Text(
                    "${com.dasein.poryadok.logic.VacationMath.days(v)} дн. отпуска · рабочих дней ${com.dasein.poryadok.logic.VacationMath.workDays(v)}" +
                        if (h > 0) ". Праздники ($h) в дни отпуска не входят и продлевают его." else "",
                    fontSize = 13.sp, color = extra.dim,
                )
            }
        },
        confirmButton = { TextButton(onClick = { com.dasein.poryadok.data.VacationStore.put(initial, v); onDismiss() }) { Text(btnLabel("Сохранить")) } },
        dismissButton = {
            com.dasein.poryadok.ui.common.ButtonsRow {
                if (initial != null) TextButton(onClick = { deleting = true }) { Text(btnLabel("Удалить"), color = extra.danger) }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
            }
        },
    )
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
                "${Dates.label(r.day)}, ${Dates.time(r.min)}" + if (r.repeat != "none") " · ${Repeat.of(r.repeat).label.lowercase()}" else "",
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
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
        val open = reminders.filter { !it.done }
        val done = reminders.filter { it.done }
        if (open.isNotEmpty()) item { Tile(padding = 10.dp) { open.forEach { r -> ReminderRow(r) { onEdit(r) } } } }
        if (done.isNotEmpty()) {
            item { SectionTitle("Выполнено", action = "Очистить") { io { done.forEach { Graph.dao.deleteReminder(it) } } } }
            item { Tile(padding = 10.dp) { done.forEach { r -> ReminderRow(r) { onEdit(r) } } } }
        }
        item { HowTo("reminders") }
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
            }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = {
            com.dasein.poryadok.ui.common.ButtonsRow {
                if (initial.id != 0L) TextButton(onClick = { io { Graph.dao.deleteReminder(initial) }; onDismiss() }) {
                    Text(btnLabel("Удалить"), color = LocalExtra.current.danger)
                }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
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
                Text(btnLabel("Весь день"), Modifier.weight(1f))
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
