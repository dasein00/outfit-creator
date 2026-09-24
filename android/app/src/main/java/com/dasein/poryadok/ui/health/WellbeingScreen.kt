@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.DayLog
import com.dasein.poryadok.data.MoodEntry
import com.dasein.poryadok.data.SleepEntry
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.ui.common.BarChart
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.LineChart
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.ProgressRing
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Series
import com.dasein.poryadok.ui.common.Stat
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.TimePickDialog
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette

val MOODS = listOf("😢" to "Ужасно", "😕" to "Плохо", "😐" to "Нормально", "🙂" to "Хорошо", "😄" to "Отлично")
val MOOD_TAGS = listOf(
    "💼 Работа", "👨‍👩‍👧 Семья", "🫂 Друзья", "❤️ Свидание", "🏋️ Спорт", "🚶 Прогулка", "😴 Выспался", "🥱 Не выспался",
    "🍽 Вкусно поел", "📚 Учёба", "🎮 Хобби", "🛋 Отдых", "✈️ Поездка", "🛍 Покупки", "🤒 Болезнь", "😤 Стресс", "☀️ Погода",
)

@Composable
fun WellbeingScreen(nav: NavHostController, initialTab: Int) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    Screen(title = "Самочувствие", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Настроение", "Сон", "Вода").forEachIndexed { i, t -> Tab(tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                when (tab) {
                    0 -> MoodTab()
                    1 -> SleepTab()
                    else -> WaterTab()
                }
                Gap(80.dp)
            }
        }
    }
}

@Composable
private fun MoodTab() {
    val extra = LocalExtra.current
    val moods by observe(emptyList()) { Graph.dao.moods() }
    var edit by remember { mutableStateOf<MoodEntry?>(null) }
    val today = Dates.today()
    Gap(10.dp)
    Tile {
        Text("Как вы сейчас?", style = MaterialTheme.typography.titleMedium)
        Gap(8.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MOODS.forEachIndexed { i, (e, l) ->
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                        edit = MoodEntry(at = System.currentTimeMillis(), day = today, level = i + 1)
                    }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(e, fontSize = 32.sp)
                    Text(l, fontSize = 10.sp, color = extra.dim)
                }
            }
        }
    }
    if (moods.isEmpty()) {
        Empty("🙂", "Дневник настроения", "Отмечайте настроение и что происходило — через пару недель появится аналитика, что делает вас счастливее.")
        edit?.let { MoodDialog(it) { edit = null } }
        return
    }
    val last30 = moods.filter { it.day > today - 30 }
    val avg = if (last30.isEmpty()) 0.0 else last30.map { it.level }.average()
    SectionTitle("30 дней")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat(if (avg > 0) MOODS[(avg.toInt() - 1).coerceIn(0, 4)].first + " %.1f".format(avg) else "—", "среднее", Modifier.weight(1f))
        Stat("${last30.map { it.day }.distinct().size}", "дней с записью", Modifier.weight(1f))
        Stat("${last30.count { it.level >= 4 }}", "хороших", Modifier.weight(1f))
    }
    Gap()
    Tile {
        val days = (29 downTo 0).map { today - it }
        LineChart(
            listOf(Series(days.map { d -> moods.filter { it.day == d }.map { it.level }.takeIf { it.isNotEmpty() }?.average()?.toFloat() }, MaterialTheme.colorScheme.primary)),
            height = 120.dp,
        )
    }
    val tagStats = remember(moods) {
        val overall = moods.map { it.level }.average()
        MOOD_TAGS.mapNotNull { tag ->
            val with = moods.filter { tag in it.tags.split(",") }
            if (with.size < 3) null else Triple(tag, with.map { it.level }.average() - overall, with.size)
        }.sortedByDescending { it.second }
    }
    if (tagStats.isNotEmpty()) {
        SectionTitle("Что влияет на настроение")
        Tile {
            tagStats.forEach { (tag, delta, n) ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text(tag, Modifier.weight(1f))
                    Text(
                        (if (delta >= 0) "▲ +" else "▼ ") + "%.1f".format(delta),
                        color = if (delta >= 0.2) extra.ok else if (delta <= -0.2) extra.danger else extra.dim,
                        fontWeight = FontWeight.Medium,
                    )
                    Text("  ×$n", color = extra.dim, fontSize = 12.sp)
                }
            }
            Text("Насколько настроение выше или ниже среднего в дни с этой отметкой.", fontSize = 11.sp, color = extra.dim)
        }
    }
    SectionTitle("Записи")
    moods.take(60).forEach { m ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { edit = m }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(MOODS[m.level - 1].first, fontSize = 26.sp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("${MOODS[m.level - 1].second} · ${Dates.label(m.day)}, ${Dates.time(Dates.minutesOf(m.at))}")
                val sub = listOf(m.tags.replace(",", " "), m.note).filter { it.isNotBlank() }.joinToString(" — ")
                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = extra.dim, maxLines = 3)
            }
        }
    }
    edit?.let { MoodDialog(it) { edit = null } }
}

@Composable
private fun MoodDialog(m0: MoodEntry, onDismiss: () -> Unit) {
    var m by remember { mutableStateOf(m0) }
    val tags = m.tags.split(",").filter { it.isNotBlank() }.toMutableSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${MOODS[m.level - 1].first} ${MOODS[m.level - 1].second}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MOODS.forEachIndexed { i, (e, _) ->
                        Box(
                            Modifier.clip(CircleShape).background(if (m.level == i + 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { m = m.copy(level = i + 1) }.padding(6.dp),
                        ) { Text(e, fontSize = 26.sp) }
                    }
                }
                Gap(10.dp)
                Text("Что было?", fontSize = 13.sp, color = LocalExtra.current.dim)
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MOOD_TAGS.forEach { t ->
                        Pill(t, t in tags) {
                            if (t in tags) tags.remove(t) else tags.add(t)
                            m = m.copy(tags = tags.joinToString(","))
                        }
                    }
                }
                Gap(10.dp)
                TextInput(m.note, { m = m.copy(note = it) }, "Пара слов о дне", singleLine = false, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { val cur = m; io { Graph.dao.upsertMood(cur) }; onDismiss() }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (m0.id != 0L) TextButton(onClick = { io { Graph.dao.deleteMood(m0) }; onDismiss() }) { Text("Удалить", color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

fun sleepMinutes(s: SleepEntry): Int {
    var d = s.wakeMin - s.bedMin
    if (d <= 0) d += 24 * 60
    return d
}

@Composable
private fun SleepTab() {
    val extra = LocalExtra.current
    val list by observe(emptyList()) { Graph.dao.sleep() }
    val profile by observe(null) { Graph.dao.profile() }
    val goal = profile?.sleepGoalMin ?: 480
    var edit by remember { mutableStateOf<SleepEntry?>(null) }
    val today = Dates.today()
    Gap(10.dp)
    Button(onClick = { edit = list.firstOrNull { it.day == today } ?: SleepEntry(today, 23 * 60, 7 * 60) }, modifier = Modifier.fillMaxWidth()) {
        Text("😴 Записать сон за эту ночь")
    }
    if (list.isEmpty()) { Empty("🌙", "Сон пока не записан", "Отмечайте, во сколько легли и встали — увидите среднюю длительность и режим."); edit?.let { SleepDialog(it) { edit = null } }; return }
    val last14 = list.filter { it.day > today - 14 }
    val avg = if (last14.isEmpty()) 0 else last14.map { sleepMinutes(it) }.average().toInt()
    SectionTitle("2 недели")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("${avg / 60}ч ${avg % 60}м", "в среднем", Modifier.weight(1f), if (avg >= goal - 30) extra.ok else extra.warn)
        Stat(if (last14.isEmpty()) "—" else Dates.time(last14.map { (it.bedMin + 12 * 60) % (24 * 60) }.average().toInt().let { (it + 12 * 60) % (24 * 60) }), "отбой", Modifier.weight(1f))
        Stat(if (last14.isEmpty()) "—" else "%.1f".format(last14.map { it.quality }.average()), "качество /5", Modifier.weight(1f))
    }
    Gap()
    Tile {
        val days = (13 downTo 0).map { today - it }
        BarChart(
            days.map { d -> list.firstOrNull { it.day == d }?.let { sleepMinutes(it) / 60f } ?: 0f },
            days.map { Dates.day(it).dayOfMonth.toString() }, Palette.item(11), target = goal / 60f, highlight = 13,
        )
        Text("Пунктир — цель ${goal / 60} ч", fontSize = 11.sp, color = extra.dim)
    }
    SectionTitle("Записи")
    list.reversed().take(30).forEach { s ->
        val m = sleepMinutes(s)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { edit = s }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${m / 60} ч ${m % 60} мин", fontWeight = FontWeight.Medium)
                Text("${Dates.label(s.day)} · ${Dates.time(s.bedMin)} → ${Dates.time(s.wakeMin)}", fontSize = 12.sp, color = extra.dim)
            }
            Text("★".repeat(s.quality), color = MaterialTheme.colorScheme.primary)
        }
    }
    edit?.let { SleepDialog(it) { edit = null } }
}

@Composable
private fun SleepDialog(s0: SleepEntry, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(s0) }
    var pickBed by remember { mutableStateOf(false) }
    var pickWake by remember { mutableStateOf(false) }
    var pickDay by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сон") },
        text = {
            Column {
                FieldButton("Утро дня", Dates.label(s.day), Modifier.fillMaxWidth()) { pickDay = true }
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldButton("Лёг", Dates.time(s.bedMin), Modifier.weight(1f)) { pickBed = true }
                    FieldButton("Встал", Dates.time(s.wakeMin), Modifier.weight(1f)) { pickWake = true }
                }
                val m = sleepMinutes(s)
                Text("Длительность: ${m / 60} ч ${m % 60} мин", color = LocalExtra.current.dim, modifier = Modifier.padding(vertical = 8.dp))
                Text("Качество")
                Row { (1..5).forEach { q -> Text(if (q <= s.quality) "★" else "☆", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { s = s.copy(quality = q) }.padding(4.dp)) } }
                TextInput(s.note, { s = s.copy(note = it) }, "Заметка (сны, пробуждения…)")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cur = s
                io { if (cur.day != s0.day) Graph.dao.deleteSleep(s0); Graph.dao.upsertSleep(cur) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { io { Graph.dao.deleteSleep(s0) }; onDismiss() }) { Text("Удалить", color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (pickBed) TimePickDialog(s.bedMin, onDismiss = { pickBed = false }, onPick = { it?.let { v -> s = s.copy(bedMin = v) } }, allowClear = false)
    if (pickWake) TimePickDialog(s.wakeMin, onDismiss = { pickWake = false }, onPick = { it?.let { v -> s = s.copy(wakeMin = v) } }, allowClear = false)
    if (pickDay) DatePickDialog(s.day, onDismiss = { pickDay = false }, onPick = { it?.let { d -> s = s.copy(day = d) } }, allowClear = false)
}

@Composable
private fun WaterTab() {
    val extra = LocalExtra.current
    val logs by observe(emptyList()) { Graph.dao.dayLogs() }
    val profile by observe(null) { Graph.dao.profile() }
    val goal = profile?.waterGoalMl ?: 2000
    val today = Dates.today()
    val ml = logs.firstOrNull { it.day == today }?.waterMl ?: 0
    val blue = Palette.item(2)
    fun add(v: Int) = io {
        val d = Graph.dao.dayLogNow(today) ?: DayLog(today)
        Graph.dao.upsertDayLog(d.copy(waterMl = (d.waterMl + v).coerceAtLeast(0)))
    }
    Gap(16.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ProgressRing(ml / goal.toFloat(), blue, size = 200.dp, stroke = 14.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💧", fontSize = 28.sp)
                Text("$ml мл", style = MaterialTheme.typography.headlineMedium)
                Text("из $goal", color = extra.dim)
            }
        }
    }
    Gap()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(150, 250, 330, 500).forEach { v -> Button(onClick = { add(v) }, modifier = Modifier.weight(1f)) { Text("+$v") } }
    }
    Gap(6.dp)
    OutlinedButton(onClick = { add(-250) }, modifier = Modifier.fillMaxWidth()) { Text("Отменить 250 мл") }
    if (ml >= goal) Text("Норма на сегодня выполнена 🎉", color = extra.ok, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
    SectionTitle("2 недели")
    Tile {
        val days = (13 downTo 0).map { today - it }
        BarChart(
            days.map { d -> (logs.firstOrNull { it.day == d }?.waterMl ?: 0) / 1000f },
            days.map { Dates.day(it).dayOfMonth.toString() }, blue, target = goal / 1000f, highlight = 13,
        )
        var streak = 0
        var d = if (ml >= goal) today else today - 1
        while ((logs.firstOrNull { it.day == d }?.waterMl ?: 0) >= goal) { streak++; d-- }
        Text("Литры по дням · серия выполнения нормы: $streak дн.", fontSize = 12.sp, color = extra.dim)
    }
}
