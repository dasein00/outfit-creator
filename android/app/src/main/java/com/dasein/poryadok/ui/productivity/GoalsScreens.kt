package com.dasein.poryadok.ui.productivity

import androidx.activity.compose.BackHandler
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Ic
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Goal
import com.dasein.poryadok.data.Milestone
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.CheckDot
import com.dasein.poryadok.ui.common.ColorPicker
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.EmojiPicker
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.ProgressRing
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.plain
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import com.dasein.poryadok.ui.today.goalProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun GoalsScreen(nav: NavHostController) {
    val dao = Graph.dao
    val goals by observe(emptyList()) { dao.goals() }
    val tasks by observe(emptyList()) { dao.tasks() }
    val milestones by observe(emptyList()) { dao.milestones() }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val extra = LocalExtra.current
    val today = Dates.today()
    Screen(
        title = "Цели",
        onBack = { nav.popBackStack() },
        fab = {
            FloatingActionButton(onClick = { nav.navigate(Routes.goal(0)) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "Новая цель")
            }
        },
    ) { pad ->
        val shown = goals.filter { it.done == showDone }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    Pill("В процессе  ${goals.count { !it.done }}", !showDone) { showDone = false }
                    Pill("Достигнуты  ${goals.count { it.done }}", showDone) { showDone = true }
                }
            }
            if (shown.isEmpty()) item {
                Empty(
                    if (showDone) "🏆" else "🎯",
                    if (showDone) "Пока без трофеев" else "Поставьте первую цель",
                    if (showDone) "Достигнутые цели будут собираться здесь." else "Разбейте её на этапы и задачи — прогресс посчитается сам.",
                )
            }
            items(shown, key = { it.id }) { g ->
                val gt = tasks.filter { it.goalId == g.id }
                val gm = milestones.filter { it.goalId == g.id }
                val pr = goalProgress(g, gt.count { it.done }, gt.size, gm.count { it.done }, gm.size)
                Tile(Modifier.padding(bottom = 10.dp), onClick = { nav.navigate(Routes.goal(g.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(pr, Palette.item(g.color), size = 52.dp) { Text(g.emoji, fontSize = 20.sp) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(g.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val parts = buildList {
                                add("${(pr * 100).toInt()}%")
                                g.deadline?.let { d ->
                                    val left = d - today
                                    add(
                                        when {
                                            g.done -> "до ${Dates.short(d)}"
                                            left < 0 -> "срок прошёл"
                                            left == 0L -> "срок сегодня"
                                            else -> "осталось $left ${plural(left, "день", "дня", "дней")}"
                                        }
                                    )
                                }
                                if (gm.isNotEmpty()) add("этапы ${gm.count { it.done }}/${gm.size}")
                                if (gt.isNotEmpty()) add("задачи ${gt.count { it.done }}/${gt.size}")
                            }
                            Text(parts.joinToString(" · "), fontSize = 12.sp, color = extra.dim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoalScreen(nav: NavHostController, id: Long) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    var g by remember { mutableStateOf(Goal(title = "", createdAt = System.currentTimeMillis())) }
    var loaded by remember { mutableStateOf(id == 0L) }
    var targetText by remember { mutableStateOf("") }
    var progressText by remember { mutableStateOf("") }
    LaunchedEffect(id) {
        if (id != 0L) {
            dao.goal(id).first()?.let { found ->
                g = found
                targetText = found.target?.plain() ?: ""
                progressText = found.progress.plain()
            }
            loaded = true
        }
    }
    val milestones by observe(emptyList()) { dao.milestones() }
    val tasks by observe(emptyList()) { dao.tasks() }
    val projects by observe(emptyList()) { dao.projects() }
    val subtasks by observe(emptyList()) { dao.subtasks() }
    var newMs by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(id == 0L) }
    val scope = rememberCoroutineScope()

    fun current() = g.copy(title = g.title.trim(), target = targetText.num(), progress = progressText.num() ?: 0.0)
    fun save() {
        val cur = current()
        if (cur.title.isNotBlank() && loaded) io { dao.upsertGoal(cur) }
        nav.popBackStack()
    }
    BackHandler { save() }

    val gm = milestones.filter { it.goalId == g.id && g.id != 0L }
    val gt = tasks.filter { it.goalId == g.id && g.id != 0L }
    val pr = goalProgress(current(), gt.count { it.done }, gt.size, gm.count { it.done }, gm.size)

    Screen(
        title = if (id == 0L) "Новая цель" else "Цель",
        onBack = { save() },
        actions = {
            if (id != 0L) IconAction(Ic.trash, "Удалить") { confirm = true }
            IconButton(onClick = { save() }) { Icon(Icons.Default.Check, "Сохранить") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(pr, Palette.item(g.color), size = 76.dp, stroke = 8.dp) { Text(g.emoji, fontSize = 30.sp) }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("${(pr * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                    g.deadline?.let { Text("Срок: ${Dates.full(it)}", color = extra.dim, fontSize = 13.sp) }
                }
            }
            Gap()
            TextInput(g.title, { g = g.copy(title = it) }, "Цель")
            Gap(8.dp)
            TextInput(g.why, { g = g.copy(why = it) }, "Зачем это мне? (мотивация)", singleLine = false, minLines = 2)
            Gap(8.dp)
            FieldButton("Срок", g.deadline?.let { Dates.full(it) } ?: "Без срока", Modifier.fillMaxWidth()) { pickDate = true }

            SectionTitle("Измеримый результат (необязательно)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(progressText, { progressText = it }, "Сейчас", Modifier.weight(1f))
                NumberField(targetText, { targetText = it }, "Цель", Modifier.weight(1f))
                TextInput(g.unit, { g = g.copy(unit = it) }, "Ед.", Modifier.weight(.8f))
            }
            if (targetText.num() != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(1.0, 5.0, 10.0).forEach { step ->
                    OutlinedButton(onClick = { progressText = ((progressText.num() ?: 0.0) + step).plain() }) { Text("+${step.plain()}") }
                }
            }

            SectionTitle("Этапы")
            if (g.id == 0L) Button(
                onClick = {
                    val cur = current()
                    if (cur.title.isNotBlank()) scope.launch {
                        val newId = dao.upsertGoal(cur)
                        g = cur.copy(id = newId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Создать цель и добавить этапы") }
            else {
                gm.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CheckDot(m.done, Palette.item(g.color), onClick = { io { dao.upsertMilestone(m.copy(done = !m.done)) } }, size = 22.dp)
                        Text(
                            m.title, Modifier.weight(1f).padding(start = 10.dp),
                            textDecoration = if (m.done) TextDecoration.LineThrough else null,
                        )
                        IconButton(onClick = { io { dao.deleteMilestone(m) } }) { Icon(Icons.Default.Close, null, tint = extra.dim) }
                    }
                }
                fun addMs() {
                    if (newMs.isNotBlank()) {
                        val t = newMs.trim(); newMs = ""
                        io { dao.upsertMilestone(Milestone(goalId = g.id, title = t, sort = gm.size)) }
                    }
                }
                OutlinedTextField(
                    value = newMs, onValueChange = { newMs = it }, placeholder = { Text("+ Этап") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addMs() }),
                    trailingIcon = { IconButton(onClick = { addMs() }) { Icon(Icons.Default.Add, "Добавить") } },
                )

                SectionTitle("Задачи цели", action = "+ Задача") {
                    io { dao.upsertGoal(current()) }
                    nav.navigate(Routes.task(0, goal = g.id))
                }
                if (gt.isEmpty()) Text("Задачи с этой целью появятся здесь.", color = extra.dim, fontSize = 13.sp)
                Tile(padding = 6.dp) {
                    gt.forEach { t ->
                        val subs = subtasks.filter { it.taskId == t.id }
                        TaskRow(t, projects.firstOrNull { it.id == t.projectId }, subs.count { it.done }, subs.size, onOpen = { nav.navigate(Routes.task(t.id)) })
                    }
                }
            }

            SectionTitle("Оформление")
            ColorPicker(g.color) { g = g.copy(color = it) }
            Gap()
            if (editMode || id == 0L) EmojiPicker(g.emoji) { g = g.copy(emoji = it) }
            else Text("Сменить значок", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { editMode = true })
            Gap(20.dp)
            if (g.id != 0L) Button(
                onClick = { g = g.copy(done = !g.done); val cur = current(); io { dao.upsertGoal(cur) } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (g.done) extra.cardHigh else extra.ok),
            ) { Text(if (g.done) "Вернуть в работу" else "🏆 Цель достигнута!") }
            Gap(40.dp)
        }
    }
    if (pickDate) DatePickDialog(g.deadline, onDismiss = { pickDate = false }, onPick = { g = g.copy(deadline = it) })
    if (confirm) ConfirmDialog("Удалить цель?", "Этапы удалятся, задачи останутся без цели.", onDismiss = { confirm = false }) {
        val cur = g
        io { dao.detachGoal(cur.id); dao.deleteMilestonesOf(cur.id); dao.deleteGoal(cur) }
        nav.popBackStack()
    }
}
