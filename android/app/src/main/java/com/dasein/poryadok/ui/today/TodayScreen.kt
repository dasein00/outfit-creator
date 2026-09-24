package com.dasein.poryadok.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.Repo
import com.dasein.poryadok.data.DayLog
import com.dasein.poryadok.data.MoodEntry
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.HabitSchedule
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.calendar.eventsOn
import com.dasein.poryadok.ui.calendar.remindersOn
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.CheckDot
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.ProgressRing
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.health.MOODS
import com.dasein.poryadok.ui.productivity.QuickAddSheet
import com.dasein.poryadok.ui.productivity.TaskRow
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.YearMonth

val QUOTES = listOf(
    "Маленькие шаги каждый день дают большие результаты.",
    "Сделай сегодня то, за что завтра скажешь себе спасибо.",
    "Дисциплина — это мост между целями и достижениями.",
    "Не жди идеального момента — возьми момент и сделай его идеальным.",
    "Порядок снаружи помогает порядку внутри.",
    "Лучшее время посадить дерево было 20 лет назад. Следующее лучшее — сейчас.",
    "Мотивация помогает начать. Привычка помогает продолжать.",
    "Одна задача за раз. Одна привычка за раз. Один день за раз.",
    "Ты не обязан быть великим, чтобы начать, но должен начать, чтобы стать великим.",
    "Отдых — тоже часть плана.",
    "Прогресс важнее совершенства.",
    "Что измеряется, то улучшается.",
)

@Composable
fun TodayScreen(nav: NavHostController, settings: Settings) {
    val dao = Graph.dao
    val today = Dates.today()
    val extra = LocalExtra.current
    val tasks by observe(emptyList()) { dao.tasks() }
    val projects by observe(emptyList()) { dao.projects() }
    val subtasks by observe(emptyList()) { dao.subtasks() }
    val habits by observe(emptyList()) { dao.habits() }
    val logs by observe(emptyList()) { dao.habitLogs() }
    val events by observe(emptyList()) { dao.events() }
    val reminders by observe(emptyList()) { dao.reminders() }
    val dayLogs by observe(emptyList()) { dao.dayLogs() }
    val moods by observe(emptyList()) { dao.moods() }
    val txns by observe(emptyList()) { dao.txns() }
    val budgets by observe(emptyList()) { dao.budgets() }
    val focus by observe(emptyList()) { dao.focusSessions() }
    val goals by observe(emptyList()) { dao.goals() }
    val profile by observe(null) { dao.profile() }
    var quickAdd by remember { mutableStateOf(false) }

    val todayTasks = tasks.filter { !it.done && it.dueDay != null && it.dueDay <= today }
    val doneToday = tasks.filter { it.done && it.doneAt != null && Dates.dayOf(it.doneAt) == today }
    val habitsToday = habits.filter { HabitSchedule(it.daysMask, it.timesPerWeek).isScheduled(today) }
    val logToday = logs.filter { it.day == today }.associateBy { it.habitId }
    val habitsDone = habitsToday.count { h -> (logToday[h.id]?.value ?: 0) >= h.target }
    val water = dayLogs.firstOrNull { it.day == today }
    val waterGoal = profile?.waterGoalMl ?: 2000
    val moodToday = moods.firstOrNull { it.day == today }
    val focusToday = focus.filter { it.day == today }.sumOf { it.minutes }
    val ym = YearMonth.now()
    val monthRange = Dates.monthRange(ym)
    val spentToday = txns.filter { it.type == TxnType.EXPENSE && it.day == today }.sumOf { it.amount }
    val spentMonth = txns.filter { it.type == TxnType.EXPENSE && it.day in monthRange }.sumOf { it.amount }
    val totalBudget = budgets.firstOrNull { it.categoryId == 0L }?.monthly
    val todayEvents = eventsOn(events, today)
    val todayReminders = remindersOn(reminders, today).filter { !it.done }
    val taskProgressTotal = todayTasks.size + doneToday.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { quickAdd = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Задача") },
                containerColor = MaterialTheme.colorScheme.primary,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Dates.greeting() + if (settings.name.isNotBlank()) ", ${settings.name}" else "",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text("${Dates.weekdayFull(today)}, ${Dates.full(today)}", color = extra.dim, fontSize = 14.sp)
                    }
                    IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Default.Search, "Поиск") }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, "Настройки") }
                }
                Gap(14.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RingStat(
                        Modifier.weight(1f), if (taskProgressTotal == 0) 0f else doneToday.size / taskProgressTotal.toFloat(),
                        MaterialTheme.colorScheme.primary, "${doneToday.size}/$taskProgressTotal", "задачи",
                    ) { nav.navigate(Routes.TASKS) }
                    RingStat(
                        Modifier.weight(1f), if (habitsToday.isEmpty()) 0f else habitsDone / habitsToday.size.toFloat(),
                        extra.ok, "$habitsDone/${habitsToday.size}", "привычки",
                    ) { nav.navigate(Routes.HABITS) }
                    RingStat(
                        Modifier.weight(1f), (focusToday / 120f), Palette.item(2), "$focusToday", "мин фокуса",
                    ) { nav.navigate(Routes.FOCUS) }
                }
            }

            item { SectionTitle("Задачи на сегодня", action = "Все") { nav.navigate(Routes.TASKS) } }
            if (todayTasks.isEmpty()) item {
                Tile {
                    Text(
                        if (doneToday.isNotEmpty()) "Все задачи на сегодня выполнены 🎉" else "На сегодня задач нет. Нажмите «Задача», чтобы добавить.",
                        color = extra.dim,
                    )
                }
            } else item {
                Tile(padding = 6.dp) {
                    todayTasks.take(8).forEach { t ->
                        val subs = subtasks.filter { it.taskId == t.id }
                        TaskRow(t, projects.firstOrNull { it.id == t.projectId }, subs.count { it.done }, subs.size, onOpen = {
                            nav.navigate(Routes.task(t.id))
                        })
                    }
                    if (todayTasks.size > 8) TextButton(onClick = { nav.navigate(Routes.TASKS) }) {
                        Text("Ещё ${todayTasks.size - 8}")
                    }
                }
            }

            if (habitsToday.isNotEmpty()) {
                item { SectionTitle("Привычки", action = "Все") { nav.navigate(Routes.HABITS) } }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(habitsToday, key = { it.id }) { h ->
                            val v = logToday[h.id]?.value ?: 0
                            val done = v >= h.target
                            val color = Palette.item(h.color)
                            Column(
                                Modifier.width(76.dp).clip(RoundedCornerShape(16.dp))
                                    .clickable { io { Repo.tapHabit(h, today, v) } }.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ProgressRing(v / h.target.toFloat(), color, size = 58.dp, stroke = 5.dp) {
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape)
                                            .background(if (done) color.copy(alpha = .3f) else extra.card),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(h.emoji, fontSize = 22.sp) }
                                }
                                Text(h.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (done) extra.dim else MaterialTheme.colorScheme.onSurface)
                                if (h.target > 1) Text("$v/${h.target}", fontSize = 10.sp, color = extra.dim)
                            }
                        }
                    }
                }
            }

            if (todayEvents.isNotEmpty() || todayReminders.isNotEmpty()) {
                item { SectionTitle("В календаре", action = "Открыть") { nav.navigate(Routes.CALENDAR) } }
                item {
                    Tile {
                        todayEvents.forEach { e ->
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.navigate(Routes.event(e.id)) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(width = 4.dp, height = 30.dp).clip(RoundedCornerShape(2.dp)).background(Palette.item(e.color)))
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(e.title)
                                    Text(
                                        e.startMin?.let { s -> Dates.time(s) + (e.endMin?.let { "–" + Dates.time(it) } ?: "") } ?: "Весь день",
                                        fontSize = 12.sp, color = extra.dim,
                                    )
                                }
                            }
                        }
                        todayReminders.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                CheckDot(false, extra.dim, onClick = { io { dao.upsertReminder(r.copy(done = true)) } }, size = 22.dp)
                                Text("  ${r.title}", Modifier.weight(1f))
                                Text(Dates.time(r.min), color = extra.dim, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Самочувствие") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(Modifier.weight(1f), onClick = { nav.navigate(Routes.wellbeing(2)) }) {
                        Text("💧 Вода", fontSize = 13.sp, color = extra.dim)
                        val ml = water?.waterMl ?: 0
                        Text("$ml / $waterGoal мл", style = MaterialTheme.typography.titleMedium)
                        Gap(6.dp)
                        Bar(ml / waterGoal.toFloat(), Palette.item(2))
                        Gap(6.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(250, 500).forEach { add ->
                                Text(
                                    "+$add", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable {
                                            io {
                                                val d = dao.dayLogNow(today) ?: DayLog(today)
                                                dao.upsertDayLog(d.copy(waterMl = d.waterMl + add))
                                            }
                                        }.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                    Tile(Modifier.weight(1f), onClick = { nav.navigate(Routes.wellbeing(0)) }) {
                        Text("🙂 Настроение", fontSize = 13.sp, color = extra.dim)
                        if (moodToday != null) {
                            val m = MOODS[moodToday.level - 1]
                            Text("${m.first} ${m.second}", style = MaterialTheme.typography.titleMedium)
                            if (moodToday.tags.isNotBlank()) Text(moodToday.tags.replace(",", ", "), fontSize = 12.sp, color = extra.dim, maxLines = 2)
                        } else {
                            Text("Как вы сегодня?", style = MaterialTheme.typography.titleMedium)
                            Gap(6.dp)
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                MOODS.forEachIndexed { i, (emoji, _) ->
                                    Text(emoji, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable {
                                        io { dao.upsertMood(MoodEntry(at = System.currentTimeMillis(), day = today, level = i + 1)) }
                                    })
                                }
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Деньги", action = "Финансы") { nav.navigate(Routes.FINANCE) } }
            item {
                Tile(onClick = { nav.navigate(Routes.txn(0)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Сегодня потрачено", fontSize = 13.sp, color = extra.dim)
                            Text(Money.format(spentToday, settings.currency), style = MaterialTheme.typography.titleLarge)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("За месяц", fontSize = 13.sp, color = extra.dim)
                            Text(Money.format(spentMonth, settings.currency), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (totalBudget != null && totalBudget > 0) {
                        Gap(8.dp)
                        val level = Money.budgetLevel(spentMonth, totalBudget)
                        Bar(
                            (spentMonth / totalBudget).toFloat(),
                            when (level) { Money.BudgetLevel.OK -> extra.ok; Money.BudgetLevel.NEAR -> extra.warn; Money.BudgetLevel.OVER -> extra.danger },
                        )
                        val daysLeft = (monthRange.last - today + 1).toInt()
                        Text(
                            "Можно тратить ${Money.format(Money.dailyAllowance(totalBudget, spentMonth, daysLeft), settings.currency)} в день",
                            fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Gap(6.dp)
                    Text("+ Записать расход", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            }

            val activeGoals = goals.filter { !it.done }.take(3)
            if (activeGoals.isNotEmpty()) {
                item { SectionTitle("Цели", action = "Все") { nav.navigate(Routes.GOALS) } }
                items(activeGoals, key = { "g" + it.id }) { g ->
                    val goalTasks = tasks.filter { it.goalId == g.id }
                    val pr = goalProgress(g, goalTasks.count { it.done }, goalTasks.size)
                    Tile(Modifier.padding(bottom = 8.dp), onClick = { nav.navigate(Routes.goal(g.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.emoji, fontSize = 22.sp)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(g.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Bar(pr, Palette.item(g.color), Modifier.padding(top = 6.dp))
                            }
                            Text("${(pr * 100).toInt()}%", color = extra.dim, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Gap(18.dp)
                Text(
                    "«${QUOTES[(today % QUOTES.size).toInt()]}»",
                    fontStyle = FontStyle.Italic, color = extra.dim, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
                if (doneToday.isNotEmpty()) Text(
                    "Сегодня закрыто ${doneToday.size} ${plural(doneToday.size, "задача", "задачи", "задач")} — так держать!",
                    fontSize = 12.sp, color = extra.ok, modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
    if (quickAdd) QuickAddSheet(onDismiss = { quickAdd = false }, defaultDay = today)
}

fun goalProgress(g: com.dasein.poryadok.data.Goal, tasksDone: Int, tasksTotal: Int, msDone: Int = 0, msTotal: Int = 0): Float {
    if (g.done) return 1f
    val t = g.target
    if (t != null && t > 0) return (g.progress / t).toFloat().coerceIn(0f, 1f)
    val total = tasksTotal + msTotal
    return if (total == 0) 0f else (tasksDone + msDone) / total.toFloat()
}

@Composable
private fun RingStat(modifier: Modifier, progress: Float, color: androidx.compose.ui.graphics.Color, value: String, label: String, onClick: () -> Unit) {
    Tile(modifier, onClick = onClick, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress, color, size = 32.dp, stroke = 4.dp)
            Column(Modifier.padding(start = 6.dp)) {
                Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(label, fontSize = 10.sp, color = LocalExtra.current.dim, maxLines = 1, softWrap = false)
            }
        }
    }
}
