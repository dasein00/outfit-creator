@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.productivity

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.Repo
import com.dasein.poryadok.data.Habit
import com.dasein.poryadok.data.HabitLog
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.HabitSchedule
import com.dasein.poryadok.logic.HabitStats
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.BarChart
import com.dasein.poryadok.ui.common.ColorPicker
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.EmojiPicker
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Heatmap
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.ProgressRing
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Stat
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.TimePickDialog
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.YearMonth

data class HabitTemplate(val name: String, val emoji: String, val target: Int = 1, val unit: String = "", val color: Int = 0)

val HABIT_TEMPLATES = listOf(
    HabitTemplate("Выпить воду", "💧", 8, "стаканов", 2),
    HabitTemplate("Зарядка", "🤸", color = 1),
    HabitTemplate("Чтение", "📚", 20, "минут", 4),
    HabitTemplate("Медитация", "🧘", 10, "минут", 6),
    HabitTemplate("10 000 шагов", "🚶", color = 5),
    HabitTemplate("Без сахара", "🍎", color = 9),
    HabitTemplate("Английский", "🇬🇧", 15, "минут", 3),
    HabitTemplate("Лечь до 23:00", "😴", color = 11),
    HabitTemplate("Витамины", "💊", color = 7),
    HabitTemplate("Дневник благодарности", "🙏", color = 0),
    HabitTemplate("Без соцсетей утром", "📵", color = 8),
    HabitTemplate("Тренировка", "💪", color = 10),
)

private fun Habit.schedule() = HabitSchedule(daysMask, timesPerWeek)

private fun doneDays(h: Habit, logs: List<HabitLog>): Set<Long> =
    logs.filter { it.habitId == h.id && it.value >= h.target }.map { it.day }.toSet()

@Composable
fun HabitsScreen(nav: NavHostController) {
    val dao = Graph.dao
    val habits by observe(emptyList()) { dao.habits() }
    val logs by observe(emptyList()) { dao.habitLogs() }
    val today = Dates.today()
    val extra = LocalExtra.current
    val week = (6 downTo 0).map { today - it }

    Screen(
        title = "Привычки",
        onBack = { nav.popBackStack() },
        fab = {
            FloatingActionButton(onClick = { nav.navigate(Routes.habitEdit(0)) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "Новая привычка")
            }
        },
    ) { pad ->
        if (habits.isEmpty()) {
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Empty(Ic.leaves, "Начните с одной привычки", "Выберите шаблон или создайте свою. Маленькие шаги каждый день работают лучше рывков.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HABIT_TEMPLATES.forEach { tpl ->
                        Pill("${tpl.emoji} ${tpl.name}", false) {
                            io {
                                dao.upsertHabit(
                                    Habit(name = tpl.name, emoji = tpl.emoji, target = tpl.target, unit = tpl.unit, color = tpl.color, createdDay = today)
                                )
                            }
                        }
                    }
                }
            }
            return@Screen
        }
        val todays = habits.filter { it.schedule().isScheduled(today) }
        val doneCount = todays.count { h -> (logs.firstOrNull { it.habitId == h.id && it.day == today }?.value ?: 0) >= h.target }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
            item {
                Tile {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(if (todays.isEmpty()) 0f else doneCount / todays.size.toFloat(), extra.ok, size = 56.dp) {
                            Text("$doneCount/${todays.size}", fontSize = 13.sp)
                        }
                        Column(Modifier.padding(start = 14.dp)) {
                            Text("Сегодня", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (doneCount == todays.size && todays.isNotEmpty()) "Все привычки выполнены — отличный день!"
                                else "Нажимайте на кружки дней, чтобы отметить",
                                fontSize = 13.sp, color = extra.dim,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 136.dp, end = 8.dp)) {
                    week.forEach { d ->
                        Text(
                            Dates.weekdayShort(d).take(2), Modifier.weight(1f), textAlign = TextAlign.Center,
                            fontSize = 11.sp, color = if (d == today) MaterialTheme.colorScheme.primary else extra.dim,
                        )
                    }
                }
            }
            items(habits, key = { it.id }) { h ->
                val color = Palette.item(h.color)
                val hLogs = logs.filter { it.habitId == h.id }.associateBy { it.day }
                val summary = remember(logs, h) { HabitStats.summary(h.schedule(), doneDays(h, logs), today, h.createdDay) }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                        .background(extra.card).clickable { nav.navigate(Routes.habit(h.id)) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.size(width = 116.dp, height = 44.dp)) {
                        Text("${h.emoji} ${h.name}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalFireDepartment, null, Modifier.size(14.dp), tint = if (summary.streak > 0) extra.warn else extra.dim)
                            Text(" ${summary.streak} ${summary.streakUnit}", fontSize = 12.sp, color = extra.dim)
                        }
                    }
                    week.forEach { d ->
                        val v = hLogs[d]?.value ?: 0
                        val done = v >= h.target
                        val scheduled = h.schedule().isScheduled(d)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape)
                                    .then(
                                        when {
                                            done -> Modifier.background(color)
                                            v > 0 -> Modifier.background(color.copy(alpha = .35f))
                                            scheduled -> Modifier.border(1.5.dp, color.copy(alpha = .6f), CircleShape)
                                            else -> Modifier.border(1.dp, extra.line, CircleShape)
                                        }
                                    )
                                    .clickable { io { Repo.tapHabit(h, d, v) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (h.target > 1 && v in 1 until h.target) Text("$v", fontSize = 11.sp)
                                else if (done) Text("✓", fontSize = 14.sp, color = Color(0xFF1B1812), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HabitEditScreen(nav: NavHostController, id: Long) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    var h by remember { mutableStateOf(Habit(name = "", createdDay = Dates.today())) }
    var targetText by remember { mutableStateOf("1") }
    var loaded by remember { mutableStateOf(id == 0L) }
    var pickTime by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id != 0L) {
            dao.habitsNow().firstOrNull { it.id == id }?.let { h = it; targetText = it.target.toString() }
            loaded = true
        }
    }
    fun save() {
        if (h.name.isNotBlank() && loaded) {
            val cur = h.copy(name = h.name.trim(), target = targetText.toIntOrNull()?.coerceAtLeast(1) ?: 1)
            io { dao.upsertHabit(cur) }
        }
        nav.popBackStack()
    }
    BackHandler { save() }
    Screen(
        title = if (id == 0L) "Новая привычка" else "Привычка",
        onBack = { save() },
        actions = { IconButton(onClick = { save() }) { Icon(Icons.Default.Edit, "Сохранить") } },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (id == 0L) {
                SectionTitle("Шаблоны")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HABIT_TEMPLATES.forEach { tpl ->
                        Pill("${tpl.emoji} ${tpl.name}", h.name == tpl.name) {
                            h = h.copy(name = tpl.name, emoji = tpl.emoji, unit = tpl.unit, color = tpl.color)
                            targetText = tpl.target.toString()
                        }
                    }
                }
            }
            SectionTitle("Название")
            TextInput(h.name, { h = h.copy(name = it) }, "Например: читать 20 минут")
            SectionTitle("Цель на день")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(targetText, { targetText = it }, "Сколько раз", Modifier.weight(1f), decimal = false)
                TextInput(h.unit, { h = h.copy(unit = it) }, "Единица (раз, мин…)", Modifier.weight(1f))
            }
            Text("1 — простая галочка. Больше 1 — счётчик: каждое нажатие прибавляет единицу.", fontSize = 12.sp, color = extra.dim)

            SectionTitle("Расписание")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Каждый день", h.timesPerWeek == 0 && h.daysMask == HabitSchedule.ALL) { h = h.copy(daysMask = HabitSchedule.ALL, timesPerWeek = 0) }
                Pill("По будням", h.timesPerWeek == 0 && h.daysMask == HabitSchedule.WEEKDAYS) { h = h.copy(daysMask = HabitSchedule.WEEKDAYS, timesPerWeek = 0) }
                (2..5).forEach { n -> Pill("$n раза в неделю", h.timesPerWeek == n) { h = h.copy(timesPerWeek = n) } }
            }
            Gap(10.dp)
            if (h.timesPerWeek == 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HabitSchedule.DAY_NAMES.forEachIndexed { i, n ->
                    val on = h.daysMask and (1 shl i) != 0
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (on) Palette.item(h.color) else extra.card)
                            .clickable {
                                val m = h.daysMask xor (1 shl i)
                                if (m != 0) h = h.copy(daysMask = m)
                            }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(n, color = if (on) Color(0xFF1B1812) else extra.dim, fontSize = 13.sp) }
                }
            }

            SectionTitle("Напоминание")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldButton("Время", h.remindMin?.let { Dates.time(it) } ?: "Не напоминать", Modifier.weight(1f)) { pickTime = true }
                Switch(
                    checked = h.remindMin != null,
                    onCheckedChange = { h = h.copy(remindMin = if (it) (h.remindMin ?: 9 * 60) else null) },
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            SectionTitle("Цвет")
            ColorPicker(h.color) { h = h.copy(color = it) }
            SectionTitle("Значок")
            EmojiPicker(h.emoji) { h = h.copy(emoji = it) }
            Gap(40.dp)
        }
    }
    if (pickTime) TimePickDialog(h.remindMin, onDismiss = { pickTime = false }, onPick = { h = h.copy(remindMin = it) })
}

@Composable
fun HabitDetailScreen(nav: NavHostController, id: Long) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    val habit by observe(null, id) { dao.habit(id) }
    val logs by observe(emptyList()) { dao.habitLogs() }
    var confirm by remember { mutableStateOf(false) }
    val h = habit ?: return
    val today = Dates.today()
    val color = Palette.item(h.color)
    val done = doneDays(h, logs)
    val mine = logs.filter { it.habitId == h.id }
    val s = HabitStats.summary(h.schedule(), done, today, h.createdDay)
    Screen(
        title = "${h.emoji} ${h.name}",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { nav.navigate(Routes.habitEdit(h.id)) }) { Icon(Icons.Default.Edit, "Изменить") }
            IconAction(Ic.trash, "Удалить") { confirm = true }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                h.schedule().describe() + if (h.target > 1) " · цель ${h.target} ${h.unit}" else "",
                color = extra.dim,
            )
            Gap()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("🔥 ${s.streak}", "серия, ${s.streakUnit}", Modifier.weight(1f))
                Stat("🏆 ${s.best}", "рекорд", Modifier.weight(1f))
                Stat("${s.rate30}%", "за 30 дней", Modifier.weight(1f))
                Stat("${s.totalDone}", "всего", Modifier.weight(1f))
            }
            SectionTitle("Последние полгода")
            Tile {
                Heatmap(
                    mine.associate { it.day to (it.value / h.target.toFloat()) }, today, 26, color,
                )
            }
            SectionTitle("По месяцам")
            val months = (5 downTo 0).map { YearMonth.now().minusMonths(it.toLong()) }
            Tile {
                BarChart(
                    months.map { ym -> Dates.monthRange(ym).count { it in done }.toFloat() },
                    months.map { Dates.monthTitle(it).take(3) },
                    color, highlight = months.size - 1,
                )
            }
            SectionTitle("Отметить прошедшие дни")
            Tile {
                val start = today - 27
                (0 until 4).forEach { w ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        (0 until 7).forEach { d ->
                            val day = start + w * 7 + d
                            val v = mine.firstOrNull { it.day == day }?.value ?: 0
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape)
                                        .background(if (v >= h.target) color else if (v > 0) color.copy(alpha = .35f) else extra.cardHigh)
                                        .clickable { io { Repo.tapHabit(h, day, v) } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(Dates.day(day).dayOfMonth.toString(), fontSize = 12.sp, color = if (v >= h.target) Color(0xFF1B1812) else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
            Gap(40.dp)
        }
    }
    if (confirm) ConfirmDialog("Удалить привычку?", "История отметок тоже удалится.", onDismiss = { confirm = false }) {
        io { Repo.deleteHabit(h) }
        nav.popBackStack()
    }
}
