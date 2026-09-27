@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.health

import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import com.dasein.poryadok.system.SleepTracker
import com.dasein.poryadok.logic.SleepDetect
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Switch
import android.widget.Toast
import android.provider.Settings
import android.content.Intent
import com.dasein.poryadok.ui.common.MoodFace
import com.dasein.poryadok.ui.common.Glyphs
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Ic
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

/** Уровни настроения 1..5; лицо рисуется MoodFace. */
val MOODS = listOf("Ужасно", "Плохо", "Нормально", "Хорошо", "Отлично")
val MOOD_TAGS: List<String> = Glyphs.MOOD_TAGS.keys.toList()

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
            MOODS.forEachIndexed { i, l ->
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                        edit = MoodEntry(at = System.currentTimeMillis(), day = today, level = i + 1)
                    }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MoodFace(i + 1, 36.dp)
                    Text(l, fontSize = 10.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    if (moods.isEmpty()) {
        Empty(Ic.smile, "Дневник настроения", "Отмечайте настроение и что происходило — через пару недель появится аналитика, что делает вас счастливее.")
        edit?.let { MoodDialog(it) { edit = null } }
        return
    }
    val last30 = moods.filter { it.day > today - 30 }
    val avg = if (last30.isEmpty()) 0.0 else last30.map { it.level }.average()
    SectionTitle("30 дней")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat(if (avg > 0) "%.1f".format(avg) else "—", "среднее из 5", Modifier.weight(1f))
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
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Glyphs.moodTag(tag), 18.dp)
                    Text("  $tag", Modifier.weight(1f))
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
            MoodFace(m.level, 30.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("${MOODS[m.level - 1]} · ${Dates.label(m.day)}, ${Dates.time(Dates.minutesOf(m.at))}")
                val sub = listOf(m.tags.replace(",", ", "), m.note).filter { it.isNotBlank() }.joinToString(" — ")
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
        title = { Text(MOODS[m.level - 1]) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MOODS.forEachIndexed { i, _ ->
                        Box(
                            Modifier.clip(CircleShape).background(if (m.level == i + 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { m = m.copy(level = i + 1) }.padding(6.dp),
                        ) { MoodFace(i + 1, 32.dp, selected = m.level == i + 1) }
                    }
                }
                Gap(10.dp)
                Text("Что было?", fontSize = 13.sp, color = LocalExtra.current.dim)
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MOOD_TAGS.forEach { t ->
                        Pill(t, t in tags, glyph = Glyphs.moodTag(t)) {
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
    val autos by observe(emptyList()) { Graph.extra.sleepAuto() }
    val autoDays = autos.associateBy { it.day }
    val goal = profile?.sleepGoalMin ?: 480
    var edit by remember { mutableStateOf<SleepEntry?>(null) }
    val today = Dates.today()
    Gap(10.dp)
    SleepAutoCard()
    Gap(8.dp)
    Button(onClick = { edit = list.firstOrNull { it.day == today } ?: SleepEntry(today, 23 * 60, 7 * 60) }, modifier = Modifier.fillMaxWidth()) {
        Text("Записать или исправить сон")
    }
    if (list.isEmpty()) { Empty(Ic.moon, "Сон пока не записан", "Отмечайте, во сколько легли и встали — увидите среднюю длительность и режим."); edit?.let { SleepDialog(it) { edit = null } }; return }
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
                Text(
                    "${Dates.label(s.day)} · ${Dates.time(s.bedMin)} → ${Dates.time(s.wakeMin)}" +
                        if (autoDays[s.day]?.applied == true && autoDays[s.day]?.userBedMin == null && autoDays[s.day]?.userWakeMin == null) " · авто" else "",
                    fontSize = 12.sp, color = extra.dim,
                )
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
                io {
                    if (cur.day != s0.day) Graph.dao.deleteSleep(s0)
                    Graph.dao.upsertSleep(cur)
                    SleepTracker.onUserEdit(cur.day, cur.bedMin, cur.wakeMin)
                }
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
                Glyph(Glyphs.WATER, 30.dp, badge = false)
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
    if (ml >= goal) Text("Норма на сегодня выполнена", color = extra.ok, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
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


/** Автоопределение сна по использованию телефона. */
@Composable
private fun SleepAutoCard() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings by observe(null) { Graph.prefs.settings }
    val autos by observe(emptyList()) { Graph.extra.sleepAuto() }
    var access by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var cal by remember { mutableStateOf<SleepDetect.Calibration?>(null) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            access = SleepTracker.hasAccess(ctx)
            cal = SleepTracker.calibration()
        }
    }
    val on = settings?.sleepAuto == true
    fun detect() {
        busy = true
        scope.launch {
            val r = runCatching { SleepTracker.run(ctx, 14) }.getOrNull()
            busy = false
            Toast.makeText(ctx, if (r != null) "Сон определён: ${Dates.time(Dates.minutesOf(r.sleepAt))} → ${Dates.time(Dates.minutesOf(r.wakeAt))}" else "Ночи обновлены", Toast.LENGTH_SHORT).show()
        }
    }
    Tile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(Glyphs.PHONE_OFF, 24.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Автоопределение сна", fontWeight = FontWeight.SemiBold)
                Text("По тому, когда вы перестаёте и начинаете пользоваться телефоном", fontSize = 12.sp, color = extra.dim)
            }
            Switch(on && access, { v ->
                if (v && !access) {
                    com.dasein.poryadok.ui.common.SystemScreens.usageAccess(ctx)
                }
                io { Graph.prefs.update { it.copy(sleepAuto = v) }; SleepTracker.ensureScheduled(ctx); if (v) SleepTracker.run(ctx, 14) }
            })
        }
        if (on && !access) {
            Gap(6.dp)
            Text("Нужен доступ к статистике использования: в открывшемся списке выберите DASEIN и включите «Разрешить доступ».", fontSize = 12.sp, color = extra.warn)
            Gap(6.dp)
            com.dasein.poryadok.ui.common.RestrictedSettingsHelp("Доступ к статистике") { com.dasein.poryadok.ui.common.SystemScreens.usageAccess(ctx) }
        }
        val last = autos.firstOrNull()
        if (on && access && last != null) {
            Gap(8.dp)
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Лёг ≈ ${Dates.time(Dates.minutesOf(last.sleepAt))}", style = MaterialTheme.typography.titleMedium)
                    Text("телефон отложен в ${Dates.time(Dates.minutesOf(last.lastUseAt))}", fontSize = 12.sp, color = extra.dim)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Встал ≈ ${Dates.time(Dates.minutesOf(last.wakeAt))}", style = MaterialTheme.typography.titleMedium)
                    Text(Dates.label(last.day), fontSize = 12.sp, color = extra.dim)
                }
            }
            Text(
                listOf(
                    "уверенность: " + listOf("низкая", "средняя", "высокая")[last.confidence.coerceIn(0, 2)],
                    if (last.awakenings > 0) "пробуждений: ${last.awakenings}" else null,
                    if (last.glances > 0) "уведомлений ночью: ${last.glances}" else null,
                ).filterNotNull().joinToString(" · "),
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (on && access) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { detect() }, enabled = !busy) { Text(if (busy) "Определяю…" else "Определить сейчас") }
                cal?.let { Text("засыпание ~${it.latencyMin} мин", fontSize = 12.sp, color = extra.dim) }
            }
        }
        Text(
            "Как это работает: ночью телефон не используют. Самая длинная пауза с 19:00 до 13:00 — это сон; короткие ночные проверки телефона " +
                "считаются пробуждениями, экран от уведомлений и будильника не учитывается. Отбой = когда отложили телефон + время засыпания, " +
                "подъём = первое использование утром. Если исправить время в записи, приложение подстроится под вас — после 3 исправлений точность обычно около 15–30 минут.",
            fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp),
        )
    }
}
