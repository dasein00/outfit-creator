package com.dasein.poryadok.ui.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.system.Focus
import com.dasein.poryadok.ui.common.BarChart
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.ProgressRing
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Stat
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(nav: NavHostController, settings: Settings) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val tasks by observe(emptyList()) { dao.tasks() }
    val sessions by observe(emptyList()) { dao.focusSessions() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var taskMenu by remember { mutableStateOf(false) }
    val running = settings.focusPhase.isNotEmpty()
    val remaining = if (running) (settings.focusEndsAt - now).coerceAtLeast(0) else settings.focusWork * 60_000L
    val total = if (running) settings.focusCycle * 60_000L else settings.focusWork * 60_000L

    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(running, settings.focusEndsAt) {
        while (running) {
            now = System.currentTimeMillis()
            if (now >= settings.focusEndsAt) {
                Focus.finishIfDue()?.let { Focus.announce(ctx, it) }
                break
            }
            delay(500)
        }
    }

    val today = Dates.today()
    val task = tasks.firstOrNull { it.id == settings.focusTaskId }
    val isBreak = settings.focusPhase == Focus.BREAK
    val color = if (isBreak) extra.ok else MaterialTheme.colorScheme.primary

    Screen(title = "Фокус", onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when (settings.focusPhase) { Focus.WORK -> "Сосредоточьтесь"; Focus.BREAK -> "Перерыв"; else -> "Помодоро" },
                color = extra.dim,
            )
            Gap()
            ProgressRing(
                if (total == 0L) 0f else 1f - remaining / total.toFloat(), color, size = 240.dp, stroke = 12.dp,
            ) {
                val sec = remaining / 1000
                Text("%02d:%02d".format(sec / 60, sec % 60), fontSize = 54.sp, style = MaterialTheme.typography.displaySmall)
            }
            Gap()
            Box {
                FieldButton("Над чем работаем", task?.title ?: "Без задачи", Modifier.fillMaxWidth()) { taskMenu = true }
                DropdownMenu(expanded = taskMenu, onDismissRequest = { taskMenu = false }) {
                    DropdownMenuItem(text = { Text("Без задачи") }, onClick = { io { Graph.prefs.update { it.copy(focusTaskId = 0) } }; taskMenu = false })
                    tasks.filter { !it.done }.take(30).forEach { t ->
                        DropdownMenuItem(text = { Text(t.title) }, onClick = { io { Graph.prefs.update { it.copy(focusTaskId = t.id) } }; taskMenu = false })
                    }
                }
            }
            Gap()
            if (running) {
                OutlinedButton(onClick = { io { Focus.stop() } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Stop, null); Text("  Остановить")
                }
            } else {
                Button(
                    onClick = { io { Focus.start(Focus.WORK, settings.focusWork, task?.id) } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Icon(Icons.Default.PlayArrow, null); Text("  Начать фокус ${settings.focusWork} мин") }
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { io { Focus.start(Focus.BREAK, settings.focusBreak, task?.id) } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.LocalCafe, null); Text(" ${settings.focusBreak} мин")
                    }
                    OutlinedButton(onClick = { io { Focus.start(Focus.BREAK, settings.focusLong, task?.id) } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.LocalCafe, null); Text(" ${settings.focusLong} мин")
                    }
                }
                SectionTitle("Длительность фокуса")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60, 90).forEach { m ->
                        Pill("$m", settings.focusWork == m) { io { Graph.prefs.update { it.copy(focusWork = m) } } }
                    }
                }
                SectionTitle("Перерыв")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10).forEach { m ->
                        Pill("$m", settings.focusBreak == m) { io { Graph.prefs.update { it.copy(focusBreak = m) } } }
                    }
                    listOf(15, 20, 30).forEach { m ->
                        Pill("длинный $m", settings.focusLong == m) { io { Graph.prefs.update { it.copy(focusLong = m) } } }
                    }
                }
            }

            SectionTitle("Статистика")
            val week = (6 downTo 0).map { today - it }
            val todayMin = sessions.filter { it.day == today }.sumOf { it.minutes }
            val weekMin = sessions.filter { it.day in week }.sumOf { it.minutes }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("$todayMin", "мин сегодня", Modifier.weight(1f))
                Stat("${sessions.count { it.day == today }}", "сессий сегодня", Modifier.weight(1f))
                Stat("${weekMin / 60}ч ${weekMin % 60}м", "за 7 дней", Modifier.weight(1f))
            }
            Gap()
            Tile {
                BarChart(
                    week.map { d -> sessions.filter { it.day == d }.sumOf { it.minutes }.toFloat() },
                    week.map { Dates.weekdayShort(it).take(2) },
                    MaterialTheme.colorScheme.primary, highlight = 6,
                )
            }
            val byTask = sessions.filter { it.day in week && it.label.isNotBlank() }.groupBy { it.label }
                .mapValues { e -> e.value.sumOf { it.minutes } }.entries.sortedByDescending { it.value }.take(5)
            if (byTask.isNotEmpty()) {
                SectionTitle("На что ушло время")
                Tile {
                    byTask.forEach { (label, m) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(label, Modifier.weight(1f), maxLines = 1)
                            Text("$m мин", color = extra.dim)
                        }
                    }
                }
            }
            Gap(40.dp)
        }
    }
}
