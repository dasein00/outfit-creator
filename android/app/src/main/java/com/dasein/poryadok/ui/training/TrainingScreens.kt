@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.training

import android.content.Context
import com.dasein.poryadok.ui.common.HowTo
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.ExKind
import com.dasein.poryadok.data.Exercise
import com.dasein.poryadok.data.PlanExercise
import com.dasein.poryadok.data.ProgressNote
import com.dasein.poryadok.data.SetLog
import com.dasein.poryadok.data.TrainingRepo
import com.dasein.poryadok.data.WorkoutPlan
import com.dasein.poryadok.data.WorkoutSession
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.GlyphField
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.LineChart
import com.dasein.poryadok.ui.common.Media
import com.dasein.poryadok.ui.common.MediaView
import com.dasein.poryadok.ui.common.VideoAddButtons
import com.dasein.poryadok.ui.common.VideoPlayer
import com.dasein.poryadok.ui.common.MoodFace
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Series
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.rememberMediaPicker
import com.dasein.poryadok.ui.health.Sparkline
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val WD = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
internal val MUSCLES = listOf("Ноги", "Ягодицы", "Грудь", "Спина", "Плечи", "Руки", "Пресс", "Всё тело", "Кардио")

private fun days(mask: Int) = WD.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(" ")
private fun w(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else v.toString().replace('.', ',')
private fun mmss(sec: Long) = "%d:%02d".format(sec / 60, sec % 60)

/** Раздел тренировок: программы, библиотека упражнений, история и прогресс. */
@Composable
fun TrainingScreen(nav: NavHostController, initialTab: Int = 0) {
    val t = Graph.training
    val extra = LocalExtra.current
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    val plans by observe(emptyList()) { t.plans() }
    val planEx by observe(emptyList()) { t.planExercises() }
    val exercises by observe(emptyList()) { t.exercises() }
    val sessions by observe(emptyList()) { t.sessions() }
    val sets by observe(emptyList()) { t.setLogs() }
    val active by observe<WorkoutSession?>(null) { t.activeSession() }
    LaunchedEffect(Unit) { runCatching { TrainingRepo.seed() } }

    fun startPlan(id: Long?) = io {
        val sid = TrainingRepo.start(id)
        withContext(Dispatchers.Main) { nav.navigate(Routes.session(sid)) }
    }

    Screen(
        "Тренировки", onBack = { nav.popBackStack(); Unit },
        fab = {
            when (tab) {
                0 -> ExtendedFloatingActionButton(
                    onClick = {
                        io {
                            val id = t.upsertPlan(WorkoutPlan(name = "Новая программа", sort = plans.size, createdAt = System.currentTimeMillis()))
                            withContext(Dispatchers.Main) { nav.navigate(Routes.trainingPlan(id)) }
                        }
                    },
                    icon = { Icon(Icons.Default.Add, null) }, text = { Text("Программа") },
                )
                1 -> ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.exercise(0)) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Упражнение") })
                else -> {}
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            androidx.compose.material3.ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Программы", "Упражнения", "История", "Прогресс").forEachIndexed { i, label ->
                    androidx.compose.material3.Tab(tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            active?.let { s ->
                Tile(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), onClick = { nav.navigate(Routes.session(s.id)) }, color = MaterialTheme.colorScheme.primary.copy(alpha = .15f)) {
                    Text("Идёт тренировка: ${s.title}", fontWeight = FontWeight.SemiBold)
                    Text("Нажмите, чтобы продолжить", fontSize = 12.sp, color = extra.dim)
                }
            }
            when (tab) {
                0 -> LazyColumn(contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Hint(
                            "training_intro",
                            "Выберите программу и нажмите «Начать». В тренировке отмечайте подходы — после завершения приложение само повысит цель: " +
                                "сначала добавит повтор, а когда во всех подходах получится верх диапазона — вес (для упражнений без веса — подход). " +
                                "Так работает двойная прогрессия из рекомендаций Американского колледжа спортивной медицины (ACSM, 2009).",
                            title = "Как работает прогрессия",
                        )
                    }
                    item {
                        OutlinedButton(onClick = { startPlan(null) }, Modifier.fillMaxWidth()) { Text("Свободная тренировка без программы") }
                    }
                    items(plans, key = { it.id }) { p ->
                        val items = planEx.filter { it.planId == p.id }
                        val last = sessions.firstOrNull { it.planId == p.id && it.finishedAt != null }
                        Tile(onClick = { nav.navigate(Routes.trainingPlan(p.id)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Glyph(p.glyph, 30.dp)
                                HGap(12.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        listOfNotNull(
                                            "${items.size} упр.", days(p.daysMask).takeIf { it.isNotBlank() },
                                            last?.let { "было ${Dates.label(it.day)}" },
                                        ).joinToString(" · "),
                                        fontSize = 12.sp, color = extra.dim,
                                    )
                                }
                                Button(onClick = { startPlan(p.id) }, enabled = items.isNotEmpty()) { Text("Начать") }
                            }
                            if (p.description.isNotBlank()) Text(p.description, fontSize = 13.sp, color = extra.dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    item { HowTo("training") }
                }
                1 -> ExerciseLibrary(exercises, sets) { nav.navigate(Routes.exercise(it)) }
                2 -> LazyColumn(contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedButton(onClick = { nav.navigate(Routes.health(3)) }, Modifier.fillMaxWidth()) { Text("Бег, йога, прогулки — быстрая запись") }
                    }
                    val done = sessions.filter { it.finishedAt != null }
                    if (done.isEmpty()) item { Text("Завершённых тренировок пока нет.", color = extra.dim, modifier = Modifier.padding(8.dp)) }
                    items(done, key = { it.id }) { s ->
                        val mine = sets.filter { it.sessionId == s.id && it.done }
                        val minutes = ((s.finishedAt!! - s.startedAt) / 60000).toInt()
                        Tile(onClick = { nav.navigate(Routes.session(s.id)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, fontWeight = FontWeight.SemiBold)
                                    Text("${Dates.label(s.day)} · $minutes мин · подходов ${mine.size} · тоннаж ${w(mine.sumOf { it.weight * it.reps })} кг", fontSize = 12.sp, color = extra.dim)
                                }
                                if (s.feel > 0) MoodFace(s.feel, 26.dp)
                            }
                        }
                    }
                    item { HowTo("training") }
                }
                else -> LazyColumn(contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val used = exercises.filter { e -> sets.any { it.exerciseId == e.id && it.done } }
                    if (used.isEmpty()) item { Text("Здесь появятся графики, когда вы отметите первые подходы.", color = extra.dim, modifier = Modifier.padding(8.dp)) }
                    items(used, key = { it.id }) { e ->
                        val h = TrainingRepo.history(sets.filter { it.exerciseId == e.id }, sessions)
                        val values = h.map {
                            when (e.kind) { ExKind.TIME -> it.bestSeconds.toFloat(); ExKind.REPS -> it.bestReps.toFloat(); else -> it.best1rm.toFloat() }
                        }
                        Tile(onClick = { nav.navigate(Routes.exercise(e.id)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, fontWeight = FontWeight.SemiBold)
                                    val last = h.lastOrNull()
                                    Text(
                                        (last?.let { TrainingRepo.describeDone(e.kind, it.sets) } ?: "") +
                                            if (e.kind == ExKind.WEIGHT && last != null) " · 1ПМ ≈ ${TrainingRepo.e1rmText(last.best1rm)}" else "",
                                        fontSize = 12.sp, color = extra.dim,
                                    )
                                }
                                if (values.size >= 2) Sparkline(values, Modifier.width(90.dp).height(36.dp))
                            }
                        }
                    }
                    item { HowTo("training") }
                }
            }
        }
    }
}

@Composable
private fun ExerciseLibrary(exercises: List<Exercise>, sets: List<SetLog>, onOpen: (Long) -> Unit) {
    val extra = LocalExtra.current
    var q by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<String?>(null) }
    val list = exercises.filter { (muscle == null || it.muscle == muscle) && (q.isBlank() || it.name.contains(q, true) || it.equipment.contains(q, true)) }
    LazyColumn(contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            OutlinedTextField(q, { q = it }, placeholder = { Text("Поиск упражнения") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Pill("Все", muscle == null) { muscle = null }
                MUSCLES.forEach { m -> Pill(m, muscle == m) { muscle = if (muscle == m) null else m } }
            }
        }
        items(list, key = { it.id }) { e ->
            val count = sets.count { it.exerciseId == e.id && it.done }
            Tile(onClick = { onOpen(e.id) }, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val first = e.media.lines().firstOrNull { it.isNotBlank() }
                    if (first != null) MediaView(first, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), crop = true) else Glyph(e.glyph, 28.dp)
                    HGap(12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(e.name, fontWeight = FontWeight.Medium)
                        Text(listOf(e.muscle, e.equipment, ExKind.names[e.kind]).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = extra.dim)
                    }
                    if (count > 0) Text("$count подх.", fontSize = 12.sp, color = extra.dim)
                }
            }
        }
    }
}

/** Выбор упражнения из библиотеки. */
@Composable
internal fun ExercisePickerDialog(exercises: List<Exercise>, onDismiss: () -> Unit, onPick: (Exercise) -> Unit) {
    val extra = LocalExtra.current
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Упражнение") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(exercises.filter { q.isBlank() || it.name.contains(q, true) || it.muscle.contains(q, true) }, key = { it.id }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(e); onDismiss() }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Glyph(e.glyph, 22.dp)
                            HGap(10.dp)
                            Column {
                                Text(e.name)
                                Text(e.muscle, fontSize = 11.sp, color = extra.dim)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/** Редактор программы: название, дни, упражнения и цели. */
@Composable
fun PlanEditScreen(nav: NavHostController, id: Long) {
    val t = Graph.training
    val extra = LocalExtra.current
    val plan by observe<WorkoutPlan?>(null, id) { t.plan(id) }
    val items by observe(emptyList(), id) { t.planExercisesOf(id) }
    val exercises by observe(emptyList()) { t.exercises() }
    val byId = exercises.associateBy { it.id }
    var name by remember(id) { mutableStateOf<String?>(null) }
    var about by remember(id) { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlanExercise?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val p = plan
    LaunchedEffect(p?.id) { if (p != null && name == null) { name = p.name; about = p.description } }
    LaunchedEffect(name, about) {
        delay(400)
        val cur = plan ?: return@LaunchedEffect
        val n = name ?: return@LaunchedEffect
        val a = about ?: return@LaunchedEffect
        if (cur.name != n || cur.description != a) io { t.upsertPlan(cur.copy(name = n, description = a)) }
    }

    Screen(
        "Программа", onBack = { nav.popBackStack(); Unit },
        actions = { TextButton(onClick = { deleting = true }) { Text("Удалить", color = extra.danger) } },
    ) { pad ->
        if (p == null) return@Screen
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphField(p.glyph, { g -> io { t.upsertPlan(p.copy(glyph = g)) } })
                HGap(12.dp)
                TextInput(name ?: "", { name = it }, "Название", Modifier.weight(1f))
            }
            Gap(8.dp)
            TextInput(about ?: "", { about = it }, "Описание", singleLine = false, minLines = 2)
            SectionTitle("Дни недели")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WD.forEachIndexed { i, d ->
                    Pill(d, p.daysMask and (1 shl i) != 0) { io { t.upsertPlan(p.copy(daysMask = p.daysMask xor (1 shl i))) } }
                }
            }
            SectionTitle("Упражнения", action = "+ Добавить") { picking = true }
            items.forEachIndexed { i, pe ->
                val e = byId[pe.exerciseId]
                Tile(Modifier.padding(bottom = 6.dp), onClick = { editing = pe }, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = extra.dim, modifier = Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e?.name ?: "Упражнение удалено", fontWeight = FontWeight.Medium)
                            Text(
                                TrainingRepo.describe(e?.kind ?: 0, pe.sets, pe.reps, pe.weight, pe.seconds) +
                                    (if ((e?.kind ?: 0) != ExKind.TIME) " · диапазон ${pe.repMin}–${pe.repMax}" else "") + " · отдых ${pe.restSec} с",
                                fontSize = 12.sp, color = extra.dim,
                            )
                        }
                        Text("↑", Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = i > 0) {
                            io { t.upsertPlanExercises(listOf(pe.copy(pos = i - 1), items[i - 1].copy(pos = i))) }
                        }.padding(8.dp), fontSize = 18.sp)
                        Text("↓", Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = i < items.lastIndex) {
                            io { t.upsertPlanExercises(listOf(pe.copy(pos = i + 1), items[i + 1].copy(pos = i))) }
                        }.padding(8.dp), fontSize = 18.sp)
                    }
                }
            }
            Gap(12.dp)
            Button(
                onClick = {
                    io {
                        val sid = TrainingRepo.start(id)
                        withContext(Dispatchers.Main) { nav.navigate(Routes.session(sid)) }
                    }
                },
                enabled = items.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
            ) { Text("Начать тренировку") }
            Gap(40.dp)
        }
    }
    if (picking) ExercisePickerDialog(exercises, { picking = false }) { e ->
        io {
            val last = Graph.training.allSets().filter { it.exerciseId == e.id && it.done }.maxByOrNull { it.at }
            t.upsertPlanExercise(
                PlanExercise(
                    planId = id, exerciseId = e.id, pos = items.size,
                    reps = if (e.kind == ExKind.REPS) 10 else 10, repMin = 8, repMax = if (e.kind == ExKind.REPS) 15 else 12,
                    weight = last?.weight ?: 0.0, seconds = if (e.kind == ExKind.TIME) 30 else 0, restSec = if (e.kind == ExKind.WEIGHT) 90 else 60,
                )
            )
        }
    }
    editing?.let { pe -> TargetDialog(pe, byId[pe.exerciseId], { editing = null }, onDelete = { io { t.deletePlanExercise(pe) } }) { io { t.upsertPlanExercise(it) } } }
    if (deleting && p != null) ConfirmDialog("Удалить программу?", "История тренировок сохранится.", onDismiss = { deleting = false }) {
        io { t.upsertPlan(p.copy(archived = true)) }
        nav.popBackStack()
    }
}

@Composable
private fun TargetDialog(pe: PlanExercise, e: Exercise?, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (PlanExercise) -> Unit) {
    val kind = e?.kind ?: ExKind.WEIGHT
    var sets by remember { mutableStateOf(pe.sets.toString()) }
    var reps by remember { mutableStateOf(pe.reps.toString()) }
    var repMin by remember { mutableStateOf(pe.repMin.toString()) }
    var repMax by remember { mutableStateOf(pe.repMax.toString()) }
    var weight by remember { mutableStateOf(w(pe.weight)) }
    var step by remember { mutableStateOf(w(pe.weightStep)) }
    var seconds by remember { mutableStateOf(pe.seconds.toString()) }
    var rest by remember { mutableStateOf(pe.restSec.toString()) }
    var maxSets by remember { mutableStateOf(pe.maxSets.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e?.name ?: "Цель") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    NumberField(sets, { sets = it }, "Подходы", Modifier.weight(1f), decimal = false)
                    HGap(8.dp)
                    if (kind == ExKind.TIME) NumberField(seconds, { seconds = it }, "Секунды", Modifier.weight(1f), decimal = false)
                    else NumberField(reps, { reps = it }, "Повторы", Modifier.weight(1f), decimal = false)
                }
                if (kind != ExKind.TIME) {
                    Gap(6.dp)
                    Row {
                        NumberField(repMin, { repMin = it }, "Мин. повторов", Modifier.weight(1f), decimal = false)
                        HGap(8.dp)
                        NumberField(repMax, { repMax = it }, "Макс. повторов", Modifier.weight(1f), decimal = false)
                    }
                }
                if (kind == ExKind.WEIGHT) {
                    Gap(6.dp)
                    Row {
                        NumberField(weight, { weight = it }, "Вес", Modifier.weight(1f), suffix = "кг")
                        HGap(8.dp)
                        NumberField(step, { step = it }, "Шаг веса", Modifier.weight(1f), suffix = "кг")
                    }
                }
                if (kind == ExKind.REPS) { Gap(6.dp); NumberField(maxSets, { maxSets = it }, "Максимум подходов", decimal = false) }
                Gap(6.dp)
                NumberField(rest, { rest = it }, "Отдых между подходами", suffix = "с", decimal = false)
                Text(
                    when (kind) {
                        ExKind.WEIGHT -> "Когда во всех подходах сделаете «макс. повторов», вес вырастет на шаг, а повторы вернутся к минимуму."
                        ExKind.REPS -> "Когда во всех подходах получится максимум повторов — добавится подход (до максимума подходов)."
                        else -> "После каждой успешной тренировки время растёт на 5 секунд."
                    },
                    fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val mn = repMin.toIntOrNull() ?: pe.repMin
                val mx = (repMax.toIntOrNull() ?: pe.repMax).coerceAtLeast(mn)
                onSave(
                    pe.copy(
                        sets = (sets.toIntOrNull() ?: pe.sets).coerceIn(1, 20), reps = (reps.toIntOrNull() ?: pe.reps).coerceIn(0, 200),
                        repMin = mn, repMax = mx, weight = weight.num() ?: pe.weight, weightStep = step.num() ?: pe.weightStep,
                        seconds = seconds.toIntOrNull() ?: pe.seconds, restSec = rest.toIntOrNull() ?: pe.restSec,
                        maxSets = (maxSets.toIntOrNull() ?: pe.maxSets).coerceIn(1, 20),
                    )
                )
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(); onDismiss() }) { Text("Убрать", color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

/** Карточка упражнения: описание, фото/GIF/видео и история с графиком. id = 0 — новое упражнение. */
@Composable
fun ExerciseScreen(nav: NavHostController, id: Long) {
    val t = Graph.training
    val extra = LocalExtra.current
    val existing by observe<Exercise?>(null, id) { t.exercise(id) }
    val sets by observe(emptyList(), id) { t.setsOfExercise(id) }
    val sessions by observe(emptyList()) { t.sessions() }
    var e by remember(id) { mutableStateOf<Exercise?>(if (id == 0L) Exercise(name = "", createdAt = System.currentTimeMillis()) else null) }
    LaunchedEffect(existing) { if (e == null && existing != null) e = existing }
    var deleting by remember { mutableStateOf(false) }
    val ex = e
    val addMedia = rememberMediaPicker("training", video = true) { path -> e = e?.copy(media = (e?.media.orEmpty().lines().filter { it.isNotBlank() } + path).joinToString("\n")) }

    fun save(back: Boolean) {
        val cur = e ?: return
        if (cur.name.isBlank()) return
        io { t.upsertExercise(cur) }
        if (back) nav.popBackStack()
    }

    Screen(
        if (id == 0L) "Новое упражнение" else "Упражнение", onBack = { save(false); nav.popBackStack(); Unit },
        actions = { TextButton(onClick = { save(true) }, enabled = !ex?.name.isNullOrBlank()) { Text("Сохранить") } },
    ) { pad ->
        if (ex == null) return@Screen
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val media = ex.media.lines().filter { it.isNotBlank() }
            if (media.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(media) { m ->
                        Box {
                            if (Media.isVideo(m)) VideoPlayer(m, Modifier.size(260.dp, 220.dp).clip(RoundedCornerShape(14.dp)))
                            else MediaView(m, Modifier.size(220.dp).clip(RoundedCornerShape(14.dp)).background(extra.card), crop = false)
                            Text(
                                "✕", fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(12.dp)).background(extra.card)
                                    .clickable { e = ex.copy(media = media.filter { it != m }.joinToString("\n")) }.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Gap(8.dp)
            }
            OutlinedButton(onClick = { addMedia() }, modifier = Modifier.fillMaxWidth()) { Text("+ Фото или GIF техники") }
            VideoAddButtons("training") { path -> e = e?.copy(media = (e?.media.orEmpty().lines().filter { it.isNotBlank() } + path).joinToString("\n")) }
            Gap(8.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphField(ex.glyph, { g -> e = ex.copy(glyph = g) })
                HGap(12.dp)
                TextInput(ex.name, { e = ex.copy(name = it) }, "Название", Modifier.weight(1f))
            }
            SectionTitle("Мышцы")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MUSCLES.forEach { m -> Pill(m, ex.muscle == m) { e = ex.copy(muscle = m) } }
            }
            Gap(8.dp)
            TextInput(ex.equipment, { e = ex.copy(equipment = it) }, "Инвентарь")
            SectionTitle("Как считать")
            Segments(ExKind.names.mapIndexed { i, n -> i to n }, ex.kind, { e = ex.copy(kind = it) })
            SectionTitle("Техника и описание")
            TextInput(ex.description, { e = ex.copy(description = it) }, "Как выполнять, на что обратить внимание", singleLine = false, minLines = 4)

            if (id != 0L) {
                val h = TrainingRepo.history(sets, sessions)
                if (h.isNotEmpty()) {
                    SectionTitle(
                        when (ex.kind) { ExKind.TIME -> "Лучшее время по тренировкам"; ExKind.REPS -> "Лучший подход по тренировкам"; else -> "Расчётный максимум (формула Эпли)" },
                    )
                    val values = h.map {
                        when (ex.kind) { ExKind.TIME -> it.bestSeconds.toFloat(); ExKind.REPS -> it.bestReps.toFloat(); else -> it.best1rm.toFloat() }
                    }
                    Tile { LineChart(listOf(Series(values, MaterialTheme.colorScheme.primary)), labels = h.map { Dates.short(it.day) }.let { l -> if (l.size > 6) l.filterIndexed { i, _ -> i % (l.size / 6 + 1) == 0 } else l }) }
                    SectionTitle("История")
                    h.reversed().forEach { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(Dates.label(d.day), Modifier.width(96.dp), fontSize = 13.sp, color = extra.dim)
                            Column(Modifier.weight(1f)) {
                                Text(TrainingRepo.describeDone(ex.kind, d.sets), fontSize = 14.sp)
                                if (ex.kind == ExKind.WEIGHT) Text("1ПМ ≈ ${TrainingRepo.e1rmText(d.best1rm)} · тоннаж ${w(d.volume)} кг", fontSize = 12.sp, color = extra.dim)
                            }
                        }
                    }
                }
                Gap(16.dp)
                if (ex.custom) TextButton(onClick = { deleting = true }) { Text("Удалить упражнение", color = extra.danger) }
            }
            Gap(40.dp)
        }
    }
    if (deleting) ConfirmDialog("Удалить упражнение?", "Оно пропадёт из библиотеки; прошлые подходы останутся в истории тренировок.", onDismiss = { deleting = false }) {
        existing?.let { io { t.deleteExercise(it) } }
        nav.popBackStack()
    }
}

/** Небольшое числовое поле для подхода. */
@Composable
private fun SetField(value: String, onChange: (String) -> Unit, suffix: String, modifier: Modifier, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, scheme.outline, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value, { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }) }, enabled = enabled, singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier.weight(1f),
        )
        Text(" $suffix", fontSize = 12.sp, color = LocalExtra.current.dim)
    }
}

private fun vibrate(ctx: Context) {
    runCatching {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
    }
}

/** Тренировка: подходы с весом и повторами, таймер отдыха, прошлые результаты и завершение с прогрессией. */
@Composable
fun SessionScreen(nav: NavHostController, id: Long) {
    val ctx = LocalContext.current
    val t = Graph.training
    val extra = LocalExtra.current
    val scheme = MaterialTheme.colorScheme
    val session by observe<WorkoutSession?>(null, id) { t.session(id) }
    val sets by observe(emptyList(), id) { t.setsOf(id) }
    val allSets by observe(emptyList()) { t.setLogs() }
    val sessions by observe(emptyList()) { t.sessions() }
    val exercises by observe(emptyList()) { t.exercises() }
    val planEx by observe(emptyList()) { t.planExercises() }
    val byId = exercises.associateBy { it.id }
    val fields = remember(id) { mutableStateMapOf<Long, Pair<String, String>>() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var restEnd by remember { mutableStateOf<Long?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<List<ProgressNote>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            now = System.currentTimeMillis()
            restEnd?.let { if (now >= it) { restEnd = null; vibrate(ctx) } }
        }
    }
    val s = session
    val finished = s?.finishedAt != null
    val order = remember(sets, planEx) {
        val pos = planEx.associate { it.id to it.pos }
        sets.groupBy { it.exerciseId }.entries.sortedWith(compareBy({ e -> e.value.firstNotNullOfOrNull { it.planExerciseId }?.let { pos[it] } ?: 1000 }, { e -> e.value.minOf { it.at } }))
    }

    fun edit(set: SetLog, weight: String?, reps: String?) {
        val cur = fields[set.id] ?: (w(set.weight) to (if (byId[set.exerciseId]?.kind == ExKind.TIME) set.seconds else set.reps).toString())
        val nw = weight ?: cur.first
        val nr = reps ?: cur.second
        fields[set.id] = nw to nr
        val kind = byId[set.exerciseId]?.kind ?: ExKind.WEIGHT
        val r = nr.toIntOrNull() ?: 0
        io { t.upsertSet(set.copy(weight = nw.num() ?: set.weight, reps = if (kind == ExKind.TIME) set.reps else r, seconds = if (kind == ExKind.TIME) r else set.seconds)) }
    }

    Screen(
        s?.title ?: "Тренировка", onBack = { nav.popBackStack(); Unit },
        actions = { if (!finished) TextButton(onClick = { finishing = true }) { Text("Завершить") } },
    ) { pad ->
        if (s == null) return@Screen
        Column(Modifier.padding(pad).fillMaxSize()) {
            val elapsed = ((s.finishedAt ?: now) - s.startedAt) / 1000
            val done = sets.count { it.done }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(mmss(elapsed), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                HGap(12.dp)
                Text("подходов $done из ${sets.size} · тоннаж ${w(sets.filter { it.done }.sumOf { it.weight * it.reps })} кг", fontSize = 13.sp, color = extra.dim)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(order, key = { it.key }) { (exId, list) ->
                    val e = byId[exId]
                    val kind = e?.kind ?: ExKind.WEIGHT
                    val pe = list.firstNotNullOfOrNull { it.planExerciseId }?.let { pid -> planEx.firstOrNull { it.id == pid } }
                    val prev = allSets.filter { it.exerciseId == exId && it.sessionId != id && it.done }
                        .groupBy { it.sessionId }.maxByOrNull { (sid, _) -> sessions.firstOrNull { it.id == sid }?.startedAt ?: 0 }?.value
                    val video = e?.media?.lines()?.firstOrNull { it.isNotBlank() && Media.isVideo(it) }
                    var showVideo by remember(exId) { mutableStateOf(false) }
                    Tile(padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { nav.navigate(Routes.exercise(exId)) }) {
                            val first = e?.media?.lines()?.firstOrNull { it.isNotBlank() }
                            if (first != null) MediaView(first, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), crop = true) else Glyph(e?.glyph ?: "sport/15", 26.dp)
                            HGap(10.dp)
                            Column(Modifier.weight(1f)) {
                                Text(e?.name ?: "Упражнение", fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(
                                        pe?.let { "цель " + TrainingRepo.describe(kind, it.sets, it.reps, it.weight, it.seconds) },
                                        prev?.let { "прошлый раз " + TrainingRepo.describeDone(kind, it) },
                                    ).joinToString(" · "),
                                    fontSize = 12.sp, color = extra.dim,
                                )
                            }
                            if (video != null) Text(
                                if (showVideo) "Скрыть" else "▶ Видео", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showVideo = !showVideo }.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        if (showVideo && video != null) {
                            Gap(6.dp)
                            VideoPlayer(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
                        }
                        Gap(6.dp)
                        Row(Modifier.fillMaxWidth()) {
                            Text("#", Modifier.width(26.dp), fontSize = 12.sp, color = extra.dim)
                            if (kind == ExKind.WEIGHT) Text("вес", Modifier.weight(1f), fontSize = 12.sp, color = extra.dim)
                            Text(if (kind == ExKind.TIME) "время" else "повторы", Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp, color = extra.dim)
                            Box(Modifier.width(52.dp))
                        }
                        list.sortedBy { it.setIndex }.forEachIndexed { i, set ->
                            val f = fields[set.id] ?: (w(set.weight) to (if (kind == ExKind.TIME) set.seconds else set.reps).toString())
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", Modifier.width(26.dp), fontWeight = FontWeight.SemiBold)
                                if (kind == ExKind.WEIGHT) SetField(f.first, { edit(set, it, null) }, "кг", Modifier.weight(1f), !finished)
                                if (kind == ExKind.WEIGHT) HGap(8.dp)
                                SetField(f.second, { edit(set, null, it) }, if (kind == ExKind.TIME) "с" else "раз", Modifier.weight(1f), !finished)
                                HGap(8.dp)
                                Box(
                                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (set.done) scheme.primary else scheme.surface)
                                        .border(2.dp, if (set.done) scheme.primary else scheme.outline, RoundedCornerShape(12.dp))
                                        .clickable(enabled = !finished) {
                                            val nowDone = !set.done
                                            io { t.upsertSet(set.copy(done = nowDone, at = if (nowDone) System.currentTimeMillis() else set.at)) }
                                            if (nowDone) restEnd = System.currentTimeMillis() + (pe?.restSec ?: 90) * 1000L
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Text("✓", color = if (set.done) scheme.onPrimary else scheme.outline, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                            }
                        }
                        if (!finished) Row {
                            TextButton(onClick = { io { TrainingRepo.addSet(id, exId) } }) { Text("+ Подход") }
                            val last = list.maxByOrNull { it.setIndex }
                            if (last != null && list.size > 1) TextButton(onClick = { io { t.deleteSet(last) } }) { Text("− Подход", color = extra.dim) }
                        }
                    }
                }
                if (!finished) item {
                    OutlinedButton(onClick = { adding = true }, Modifier.fillMaxWidth()) { Text("+ Упражнение в эту тренировку") }
                }
                if (finished && s.note.isNotBlank()) item { Text(s.note, color = extra.dim, modifier = Modifier.padding(8.dp)) }
                if (finished) item {
                    TextButton(onClick = { discard = true }) { Text("Удалить тренировку", color = extra.danger) }
                }
            }
            restEnd?.let { end ->
                val left = ((end - now) / 1000).coerceAtLeast(0)
                Row(
                    Modifier.fillMaxWidth().background(scheme.primary.copy(alpha = .15f)).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Отдых ${mmss(left)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { restEnd = end - 15_000 }) { Text("−15") }
                    TextButton(onClick = { restEnd = end + 15_000 }) { Text("+15") }
                    TextButton(onClick = { restEnd = null }) { Text("Пропустить") }
                }
            }
        }
    }

    if (adding) ExercisePickerDialog(exercises, { adding = false }) { e -> io { TrainingRepo.addExercise(id, e.id) } }
    if (finishing) {
        var feel by remember { mutableStateOf(4) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { finishing = false },
            title = { Text("Завершить тренировку?") },
            text = {
                Column {
                    Text("Как прошла?", fontSize = 13.sp, color = extra.dim)
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { l -> Box(Modifier.clip(RoundedCornerShape(20.dp)).clickable { feel = l }) { MoodFace(l, 38.dp, selected = feel == l) } }
                    }
                    TextInput(note, { note = it }, "Заметка", singleLine = false)
                    if (sets.none { it.done }) Text("Ни один подход не отмечен — цели программы не изменятся.", fontSize = 12.sp, color = extra.danger, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    finishing = false
                    restEnd = null
                    io {
                        val notes = TrainingRepo.finish(id, feel, note)
                        withContext(Dispatchers.Main) { summary = notes }
                    }
                }) { Text("Завершить") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { finishing = false; discard = true }) { Text("Удалить", color = extra.danger) }
                    TextButton(onClick = { finishing = false }) { Text("Продолжить") }
                }
            },
        )
    }
    summary?.let { notes ->
        AlertDialog(
            onDismissRequest = { summary = null; nav.popBackStack() },
            title = { Text("Тренировка сохранена") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (notes.isEmpty()) Text("Записана в журнал тренировок и калории дня.")
                    notes.forEach { n ->
                        Text(n.exercise, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        Text("Сделано: ${n.done}", fontSize = 13.sp)
                        Text("В следующий раз: ${n.next}", fontSize = 13.sp, color = scheme.primary)
                        Text(n.reason, fontSize = 12.sp, color = extra.dim)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { summary = null; nav.popBackStack() }) { Text("Готово") } },
        )
    }
    if (discard) ConfirmDialog("Удалить тренировку?", "Подходы этой тренировки будут удалены.", onDismiss = { discard = false }) {
        io { TrainingRepo.discard(id) }
        nav.popBackStack()
    }
}
