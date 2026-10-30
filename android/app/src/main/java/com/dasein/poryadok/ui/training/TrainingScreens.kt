@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.training

import com.dasein.poryadok.ui.common.swipeTabs
import com.dasein.poryadok.ui.common.tabSlide
import com.dasein.poryadok.ui.common.swipeTabsOf
import android.content.Context
import com.dasein.poryadok.ui.common.btnLabel
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
import androidx.compose.foundation.layout.Spacer
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
import com.dasein.poryadok.ui.common.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.dasein.poryadok.ui.common.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
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
    val archivedPlans by observe(emptyList()) { t.archivedPlans() }
    val archivedSessions = com.dasein.poryadok.data.TrainingArchive.sessions.value
    LaunchedEffect(Unit) { runCatching { TrainingRepo.seed() } }
    // Меню по удержанию карточки и архив: программы и проведённые тренировки сначала уходят в архив, оттуда — удаляются.
    var planMenu by remember { mutableStateOf<WorkoutPlan?>(null) }
    var sessionMenu by remember { mutableStateOf<WorkoutSession?>(null) }
    var planArchiveOpen by rememberSaveable { mutableStateOf(false) }
    var sessionArchiveOpen by rememberSaveable { mutableStateOf(false) }
    var deletePlan by remember { mutableStateOf<WorkoutPlan?>(null) }
    var deleteSession by remember { mutableStateOf<WorkoutSession?>(null) }

    // В начале тренировки — вес гантелей и утяжелителей (для подсчёта калорий).
    var gearFor by remember { mutableStateOf<Long?>(null) }
    var askGear by remember { mutableStateOf(false) }
    fun startPlan(id: Long?) { gearFor = id; askGear = true }
    if (askGear) GearDialog(onDismiss = { askGear = false }) { gear ->
        askGear = false
        val planId = gearFor
        io {
            val sid = TrainingRepo.start(planId, gear)
            withContext(Dispatchers.Main) { nav.navigate(Routes.session(sid)) }
        }
    }

    planMenu?.let { p ->
        val close = { planMenu = null }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(p.name) },
            text = {
                Column {
                    TextButton(onClick = { close(); nav.navigate(Routes.trainingPlan(p.id)) }, Modifier.fillMaxWidth()) { Text(btnLabel("Открыть программу")) }
                    TextButton(onClick = { close(); startPlan(p.id) }, Modifier.fillMaxWidth()) { Text(btnLabel("Начать тренировку")) }
                    TextButton(onClick = { close(); io { t.upsertPlan(p.copy(archived = true)) } }, Modifier.fillMaxWidth()) { Text(btnLabel("Убрать в архив")) }
                }
            },
            confirmButton = { TextButton(onClick = close) { Text(btnLabel("Закрыть")) } },
        )
    }
    sessionMenu?.let { s ->
        val close = { sessionMenu = null }
        val inArchive = s.id in archivedSessions
        AlertDialog(
            onDismissRequest = close,
            title = { Text(s.title) },
            text = {
                Column {
                    TextButton(onClick = { close(); nav.navigate(Routes.session(s.id)) }, Modifier.fillMaxWidth()) { Text(btnLabel("Открыть тренировку")) }
                    if (inArchive) {
                        TextButton(onClick = { close(); com.dasein.poryadok.data.TrainingArchive.restore(s.id) }, Modifier.fillMaxWidth()) { Text(btnLabel("Вернуть из архива")) }
                        TextButton(onClick = { close(); deleteSession = s }, Modifier.fillMaxWidth()) { Text(btnLabel("Удалить навсегда"), color = extra.danger) }
                    } else TextButton(onClick = { close(); com.dasein.poryadok.data.TrainingArchive.archive(s.id) }, Modifier.fillMaxWidth()) { Text(btnLabel("Убрать в архив")) }
                }
            },
            confirmButton = { TextButton(onClick = close) { Text(btnLabel("Закрыть")) } },
        )
    }
    deletePlan?.let { p ->
        ConfirmDialog("Удалить программу навсегда?", "«${p.name}» исчезнет из архива. История проведённых тренировок останется.", onDismiss = { deletePlan = null }) {
            io { TrainingRepo.deletePlanForever(p) }
        }
    }
    deleteSession?.let { s ->
        ConfirmDialog("Удалить тренировку навсегда?", "Подходы и запись в журнале (с калориями) будут удалены.", onDismiss = { deleteSession = null }) {
            com.dasein.poryadok.data.TrainingArchive.restore(s.id)
            io { TrainingRepo.discard(s.id) }
        }
    }

    Screen(
        "Тренировки", onBack = { nav.popBackStack(); Unit },
        fab = {
            when (tab) {
                0 -> ExtendedFloatingActionButton(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, 
                    onClick = {
                        io {
                            val id = t.upsertPlan(WorkoutPlan(name = "Новая программа", sort = plans.size, createdAt = System.currentTimeMillis()))
                            withContext(Dispatchers.Main) { nav.navigate(Routes.trainingPlan(id)) }
                        }
                    },
                    icon = { Icon(Icons.Default.Add, null) }, text = { Text(btnLabel("Программа")) },
                )
                1 -> ExtendedFloatingActionButton(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, onClick = { nav.navigate(Routes.exercise(0)) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(btnLabel("Упражнение")) })
                else -> {}
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().swipeTabs(tab, 5) { tab = it }) {
            androidx.compose.material3.ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Программы", "Упражнения", "История", "Прогресс", "Журнал").forEachIndexed { i, label ->
                    androidx.compose.material3.Tab(tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            active?.let { s ->
                Tile(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onClick = { nav.navigate(Routes.session(s.id)) }, color = MaterialTheme.colorScheme.primary.copy(alpha = .15f)) {
                    Text("Идёт тренировка: ${s.title}", fontWeight = FontWeight.SemiBold)
                    Text(btnLabel("Нажмите, чтобы продолжить"), fontSize = 12.sp, color = extra.dim)
                }
            }
            Column(Modifier.tabSlide(tab)) {
                when (tab) {
                    0 -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            OutlinedButton(onClick = { startPlan(null) }, Modifier.fillMaxWidth()) { Text(btnLabel("Свободная тренировка без программы")) }
                        }
                        if (plans.isEmpty()) item {
                            Tile {
                                Text(btnLabel("Программ пока нет"), fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Составьте свою: кнопка «+ Программа» внизу → название и дни → добавьте упражнения из вкладки «Упражнения» с подходами, повторами и весом. " +
                                        "Сначала создайте упражнения (с видео или GIF), если их ещё нет.",
                                    fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
                                )
                            }
                        }
                        items(plans, key = { it.id }) { p ->
                            val items = planEx.filter { it.planId == p.id }
                            val last = sessions.firstOrNull { it.planId == p.id && it.finishedAt != null }
                            Tile(onClick = { nav.navigate(Routes.trainingPlan(p.id)) }, onLongClick = { planMenu = p }) {
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
                                    Button(onClick = { startPlan(p.id) }, enabled = items.isNotEmpty()) { Text(btnLabel("Начать")) }
                                }
                                if (p.description.isNotBlank()) Text(p.description, fontSize = 13.sp, color = extra.dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        if (archivedPlans.isNotEmpty()) item {
                            TextButton(onClick = { planArchiveOpen = !planArchiveOpen }, Modifier.fillMaxWidth()) {
                                Text((if (planArchiveOpen) "Скрыть архив программ" else "Архив программ") + " (${archivedPlans.size})")
                            }
                        }
                        if (planArchiveOpen) items(archivedPlans, key = { "a" + it.id }) { p ->
                            Tile(color = extra.cardHigh) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Glyph(p.glyph, 26.dp)
                                    HGap(10.dp)
                                    Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.Medium, color = extra.dim)
                                }
                                Row {
                                    TextButton(onClick = { io { t.upsertPlan(p.copy(archived = false)) } }) { Text(btnLabel("Вернуть")) }
                                    TextButton(onClick = { deletePlan = p }) { Text(btnLabel("Удалить навсегда"), color = extra.danger) }
                                }
                            }
                        }
                        item { HowTo("training") }
                    }
                    1 -> ExerciseShorts(exercises, sets, onAdd = { nav.navigate(Routes.exercise(0)) }) { nav.navigate(Routes.exercise(it)) }
                    // Журнал: быстрая запись бега, йоги, прогулок и любых тренировок, статистика недели и 8 недель.
                    4 -> Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp)) {
                        com.dasein.poryadok.ui.health.WorkoutTab(nav, inTraining = true)
                        Gap(80.dp)
                    }
                    2 -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            OutlinedButton(onClick = { tab = 4 }, Modifier.fillMaxWidth()) { Text(btnLabel("Бег, йога, прогулки — быстрая запись")) }
                        }
                        val done = sessions.filter { it.finishedAt != null && it.id !in archivedSessions }
                        val archived = sessions.filter { it.finishedAt != null && it.id in archivedSessions }
                        if (done.isEmpty()) item { Text(btnLabel("Завершённых тренировок пока нет."), color = extra.dim, modifier = Modifier.padding(8.dp)) }
                        items(done, key = { it.id }) { s ->
                            val mine = sets.filter { it.sessionId == s.id && it.done }
                            val minutes = ((s.finishedAt!! - s.startedAt) / 60000).toInt()
                            Tile(onClick = { nav.navigate(Routes.session(s.id)) }, onLongClick = { sessionMenu = s }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(s.title, fontWeight = FontWeight.SemiBold)
                                        Text("${Dates.label(s.day)} · $minutes мин · подходов ${mine.size} · тоннаж ${w(mine.sumOf { it.weight * it.reps })} кг", fontSize = 12.sp, color = extra.dim)
                                    }
                                    if (s.feel > 0) MoodFace(s.feel, 26.dp)
                                }
                            }
                        }
                        if (archived.isNotEmpty()) item {
                            TextButton(onClick = { sessionArchiveOpen = !sessionArchiveOpen }, Modifier.fillMaxWidth()) {
                                Text((if (sessionArchiveOpen) "Скрыть архив тренировок" else "Архив тренировок") + " (${archived.size})")
                            }
                        }
                        if (sessionArchiveOpen) items(archived, key = { "a" + it.id }) { s ->
                            Tile(color = extra.cardHigh, onClick = { nav.navigate(Routes.session(s.id)) }, onLongClick = { sessionMenu = s }) {
                                Text(s.title, fontWeight = FontWeight.Medium, color = extra.dim)
                                Text(Dates.label(s.day), fontSize = 12.sp, color = extra.dim)
                                Row {
                                    TextButton(onClick = { com.dasein.poryadok.data.TrainingArchive.restore(s.id) }) { Text(btnLabel("Вернуть")) }
                                    TextButton(onClick = { deleteSession = s }) { Text(btnLabel("Удалить навсегда"), color = extra.danger) }
                                }
                            }
                        }
                        item { HowTo("training") }
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

@Composable
private fun ExerciseLibrary(exercises: List<Exercise>, sets: List<SetLog>, onOpen: (Long) -> Unit) {
    val extra = LocalExtra.current
    var q by remember { mutableStateOf("") }
    var muscles by remember { mutableStateOf(emptySet<String>()) }
    val list = exercises.filter { (muscles.isEmpty() || it.muscle in muscles) && (q.isBlank() || it.name.contains(q, true) || it.equipment.contains(q, true)) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            OutlinedTextField(q, { q = it }, placeholder = { Text(btnLabel("Поиск упражнения")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Column(Modifier.padding(vertical = 8.dp)) {
                MuscleFilter(muscles, exercises, { muscles = it }) { Pill("Все", muscles.isEmpty()) { muscles = emptySet() } }
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
        title = { Text(btnLabel("Упражнение")) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text(btnLabel("Поиск")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    val archivedIds = com.dasein.poryadok.data.ExercisePrefs.archived.value
                    items(exercises.filter { it.id !in archivedIds && (q.isBlank() || it.name.contains(q, true) || it.muscle.contains(q, true)) }, key = { it.id }) { e ->
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) } },
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
    var askGear by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // Два режима карточки: просмотр (по умолчанию) и правка — по кнопке «Изменить тренировку».
    // Только что созданная программа сразу открывается в правке.
    var edit by rememberSaveable(id) { mutableStateOf(false) }
    val p = plan
    LaunchedEffect(p?.id) {
        if (p != null && name == null) {
            name = p.name; about = p.description
            if (System.currentTimeMillis() - p.createdAt < 60_000) edit = true
        }
    }
    LaunchedEffect(name, about) {
        delay(400)
        val cur = plan ?: return@LaunchedEffect
        val n = name ?: return@LaunchedEffect
        val a = about ?: return@LaunchedEffect
        if (cur.name != n || cur.description != a) io { t.upsertPlan(cur.copy(name = n, description = a)) }
    }

    if (askGear) GearDialog(onDismiss = { askGear = false }) { gear ->
        askGear = false
        io {
            val sid = TrainingRepo.start(id, gear)
            withContext(Dispatchers.Main) { nav.navigate(Routes.session(sid)) }
        }
    }
    Screen(
        if (edit) "Изменить тренировку" else "Программа", onBack = { if (edit && p?.archived != true) edit = false else nav.popBackStack(); Unit },
        actions = {
            if (edit) TextButton(onClick = { edit = false }) { Text(btnLabel("Готово")) }
            else TextButton(onClick = { edit = true }) { Text(btnLabel("Изменить")) }
        },
    ) { pad ->
        if (p == null) return@Screen
        if (!edit) {
            PlanView(Modifier.padding(pad), nav, p, items, byId, onStart = { askGear = true }, onEdit = { edit = true }, onArchive = { deleting = true })
            return@Screen
        }
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
                        Text(btnLabel("↑"), Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = i > 0) {
                            io { t.upsertPlanExercises(listOf(pe.copy(pos = i - 1), items[i - 1].copy(pos = i))) }
                        }.padding(8.dp), fontSize = 18.sp)
                        Text(btnLabel("↓"), Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = i < items.lastIndex) {
                            io { t.upsertPlanExercises(listOf(pe.copy(pos = i + 1), items[i + 1].copy(pos = i))) }
                        }.padding(8.dp), fontSize = 18.sp)
                    }
                }
            }
            Gap(12.dp)
            Button(onClick = { edit = false }, modifier = Modifier.fillMaxWidth()) { Text(btnLabel("Готово")) }
            TextButton(onClick = { deleting = true }, Modifier.fillMaxWidth()) { Text(btnLabel("Убрать программу в архив"), color = extra.danger) }
            Gap(40.dp)
        }
    }
    if (picking) ExercisePickerDialog(exercises, { picking = false }) { e ->
        io {
            val last = Graph.training.allSets().filter { it.exerciseId == e.id && it.done }.maxByOrNull { it.at }
            // Мой подход из карточки упражнения — сразу целью в программе, чтобы не набирать заново.
            val st = com.dasein.poryadok.data.ExercisePrefs.setup(e.id)
            t.upsertPlanExercise(
                PlanExercise(
                    planId = id, exerciseId = e.id, pos = items.size, sets = st?.sets ?: 3,
                    reps = st?.reps ?: 10, repMin = 8, repMax = maxOf(st?.reps ?: 0, if (e.kind == ExKind.REPS) 15 else 12),
                    weight = last?.weight ?: if (st?.usesDumbbells == true && e.kind == ExKind.WEIGHT) (st?.dumbbellKg ?: 0.0) else 0.0,
                    seconds = if (e.kind == ExKind.TIME) st?.seconds ?: 30 else 0, restSec = if (e.kind == ExKind.WEIGHT) 90 else 60,
                )
            )
        }
    }
    editing?.let { pe -> TargetDialog(pe, byId[pe.exerciseId], { editing = null }, onDelete = { io { t.deletePlanExercise(pe) } }) { io { t.upsertPlanExercise(it) } } }
    if (deleting && p != null) ConfirmDialog(
        "Убрать программу в архив?", "История тренировок сохранится. Вернуть программу или удалить насовсем можно в «Архиве» на вкладке «Программы».",
        confirm = "В архив", onDismiss = { deleting = false },
    ) {
        io { t.upsertPlan(p.copy(archived = true)) }
        nav.popBackStack()
    }
}

/** Карточка программы в режиме просмотра: что в ней, цели упражнений и кнопки «Начать» / «Изменить тренировку». */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlanView(
    modifier: Modifier, nav: NavHostController, p: WorkoutPlan, items: List<PlanExercise>, byId: Map<Long, Exercise>,
    onStart: () -> Unit, onEdit: () -> Unit, onArchive: () -> Unit,
) {
    val extra = LocalExtra.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(p.glyph, 40.dp)
            HGap(12.dp)
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull("${items.size} упр.", days(p.daysMask).takeIf { it.isNotBlank() }).joinToString(" · "),
                    fontSize = 13.sp, color = extra.dim,
                )
            }
        }
        if (p.description.isNotBlank()) Text(p.description, fontSize = 14.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
        Gap(16.dp)
        Button(onClick = onStart, enabled = items.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(btnLabel("Начать тренировку")) }
        Gap(8.dp)
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(btnLabel("Изменить тренировку")) }
        SectionTitle("Упражнения")
        if (items.isEmpty()) Text("Упражнений пока нет — нажмите «Изменить тренировку» и добавьте их.", fontSize = 13.sp, color = extra.dim)
        items.forEachIndexed { i, pe ->
            val e = byId[pe.exerciseId]
            Tile(Modifier.padding(bottom = 6.dp), onClick = { e?.let { nav.navigate(Routes.exercise(it.id)) } }, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val first = e?.media?.lines()?.firstOrNull { it.isNotBlank() }
                    if (first != null) MediaView(first, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), crop = true)
                    else Text("${i + 1}", color = extra.dim, modifier = Modifier.width(22.dp))
                    HGap(10.dp)
                    Column(Modifier.weight(1f)) {
                        Text(e?.name ?: "Упражнение удалено", fontWeight = FontWeight.Medium)
                        Text(
                            TrainingRepo.describe(e?.kind ?: 0, pe.sets, pe.reps, pe.weight, pe.seconds) + " · отдых ${pe.restSec} с",
                            fontSize = 12.sp, color = extra.dim,
                        )
                    }
                }
            }
        }
        TextButton(onClick = onArchive, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(btnLabel("Убрать программу в архив"), color = extra.danger) }
        Gap(40.dp)
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
                StepField(sets, { sets = it }, "Подходы", 1.0)
                Gap(6.dp)
                if (kind == ExKind.TIME) StepField(seconds, { seconds = it }, "Секунды", 5.0)
                else StepField(reps, { reps = it }, "Повторы", 1.0)
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
                    StepField(weight, { weight = it }, "Вес, кг", step.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 2.5, decimal = true)
                    Gap(6.dp)
                    NumberField(step, { step = it }, "Шаг веса (на сколько «+» и прогрессия)", suffix = "кг")
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
            }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = {
            com.dasein.poryadok.ui.common.ButtonsRow {
                TextButton(onClick = { onDelete(); onDismiss() }) { Text(btnLabel("Убрать"), color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
            }
        },
    )
}

/**
 * Карточка упражнения. Два режима: просмотр (по умолчанию) — видео на всю ширину, как в рецептах, мышцы и инвентарь,
 * «Мой подход» с кнопкой «Начать», техника и история; правка — по кнопке «Изменить». id = 0 — новое упражнение (сразу правка).
 */
@Composable
fun ExerciseScreen(nav: NavHostController, id: Long) {
    val t = Graph.training
    val ctx = LocalContext.current
    val existing by observe<Exercise?>(null, id) { t.exercise(id) }
    val sets by observe(emptyList(), id) { t.setsOfExercise(id) }
    val sessions by observe(emptyList()) { t.sessions() }
    var e by remember(id) { mutableStateOf<Exercise?>(if (id == 0L) Exercise(name = "", createdAt = System.currentTimeMillis()) else null) }
    LaunchedEffect(existing) { if (e == null && existing != null) e = existing }
    var edit by rememberSaveable(id) { mutableStateOf(id == 0L) }
    var setupFor by remember { mutableStateOf<Boolean?>(null) } // true — настроить и начать, false — только настроить
    val setupVer = com.dasein.poryadok.data.ExercisePrefs.version.intValue
    val setup = remember(id, setupVer) { if (id != 0L) com.dasein.poryadok.data.ExercisePrefs.setup(id) else null }
    val ex = e

    fun save(back: Boolean) {
        val cur = e ?: return
        if (cur.name.isBlank()) return
        io { t.upsertExercise(cur) }
        if (back) nav.popBackStack()
    }
    fun startNow(x: Exercise, s: com.dasein.poryadok.data.ExerciseSetup) = io {
        val sid = TrainingRepo.startExercise(x, s)
        withContext(Dispatchers.Main) { nav.navigate(Routes.session(sid)) }
    }

    if (ex != null) setupFor?.let { andStart ->
        ExerciseSetupDialog(ex, setup, andStart, onDismiss = { setupFor = null }) { s ->
            com.dasein.poryadok.data.ExercisePrefs.saveSetup(ex.id, s)
            setupFor = null
            if (andStart) startNow(ex, s)
        }
    }

    Screen(
        when { id == 0L -> "Новое упражнение"; edit -> "Изменить упражнение"; else -> "Упражнение" },
        onBack = {
            when {
                id == 0L -> { save(false); nav.popBackStack() }
                edit -> { save(false); edit = false }
                else -> nav.popBackStack()
            }
            Unit
        },
        actions = {
            if (edit) TextButton(onClick = { if (id == 0L) save(true) else { save(false); edit = false } }, enabled = !ex?.name.isNullOrBlank()) { Text(btnLabel("Готово")) }
            else TextButton(onClick = { edit = true }) { Text(btnLabel("Изменить")) }
        },
    ) { pad ->
        if (ex == null) return@Screen
        if (edit) ExerciseEditBody(Modifier.padding(pad), ex, id, onChange = { e = it }, onDone = { if (id == 0L) save(true) else { save(false); edit = false } })
        else ExerciseViewBody(
            Modifier.padding(pad), nav, ex, setup, TrainingRepo.history(sets, sessions),
            onStart = { if (setup == null) setupFor = true else startNow(ex, setup) },
            onSetup = { setupFor = false },
            onArchive = { com.dasein.poryadok.data.ExercisePrefs.archive(ex.id); nav.popBackStack() },
            onEdit = { edit = true },
        )
    }
}

/** «3 подхода × 12 повторов · гантели по 5 кг». */
internal fun describeSetup(kind: Int, s: com.dasein.poryadok.data.ExerciseSetup): String {
    val work = if (kind == ExKind.TIME) "${s.sets} × ${s.seconds} с" else "${s.sets} × ${s.reps} повт."
    val load = when (s.load) {
        com.dasein.poryadok.data.ExerciseSetup.DUMBBELLS -> "гантели по ${w(s.dumbbellKg)} кг"
        com.dasein.poryadok.data.ExerciseSetup.WEAR -> "утяжелители ${w(s.wearKg)} кг"
        com.dasein.poryadok.data.ExerciseSetup.BOTH -> "гантели по ${w(s.dumbbellKg)} кг + утяжелители ${w(s.wearKg)} кг"
        else -> "свой вес"
    }
    return "$work · $load"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExerciseViewBody(
    modifier: Modifier, nav: NavHostController, ex: Exercise, setup: com.dasein.poryadok.data.ExerciseSetup?, h: List<TrainingRepo.ExerciseDay>,
    onStart: () -> Unit, onSetup: () -> Unit, onArchive: () -> Unit, onEdit: () -> Unit,
) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val media = ex.media.lines().filter { it.isNotBlank() && java.io.File(it).exists() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // Видео и фото техники — крупно, на всю ширину.
        if (media.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(com.dasein.poryadok.ui.theme.LocalLook.current.cardRound.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .3f), extra.card))),
                contentAlignment = Alignment.Center,
            ) { Glyph(ex.glyph, 88.dp, badge = false) }
        } else media.forEachIndexed { i, m ->
            if (i > 0) Gap(8.dp)
            if (Media.isVideo(m)) VideoPlayer(m, Modifier.fillMaxWidth())
            else MediaView(m, Modifier.fillMaxWidth().heightIn(max = 380.dp).clip(RoundedCornerShape(12.dp)), crop = false)
        }
        Gap(16.dp)
        Text(ex.name, style = MaterialTheme.typography.headlineSmall)
        Gap(8.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (ex.muscle.isNotBlank()) InfoChip(ex.muscle, Muscles.glyphOf(ctx, ex.muscle))
            if (ex.equipment.isNotBlank()) InfoChip(ex.equipment, null)
            InfoChip(ExKind.names[ex.kind], null)
        }
        Gap(16.dp)
        // Главное действие: мой подход и «Начать».
        Tile(color = MaterialTheme.colorScheme.primary.copy(alpha = .1f)) {
            Text(btnLabel("Мой подход"), fontSize = 13.sp, color = extra.dim)
            Text(
                setup?.let { describeSetup(ex.kind, it) } ?: "Ещё не настроен — при первом старте спросим подходы, повторы и вес",
                fontSize = 16.sp, fontWeight = if (setup != null) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 2.dp),
            )
            Gap(12.dp)
            Row {
                Button(onClick = onStart, Modifier.weight(1f)) { Text(btnLabel("Начать")) }
                HGap(8.dp)
                OutlinedButton(onClick = onSetup, Modifier.weight(1f)) { Text(btnLabel("Настроить")) }
            }
        }
        if (h.isNotEmpty()) {
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.dasein.poryadok.ui.common.Stat("${h.sumOf { d -> d.sets.size }}", "подходов", Modifier.weight(1f))
                com.dasein.poryadok.ui.common.Stat(
                    when (ex.kind) { ExKind.TIME -> "${h.maxOf { it.bestSeconds }} с"; ExKind.REPS -> "${h.maxOf { it.bestReps }}"; else -> TrainingRepo.e1rmText(h.maxOf { it.best1rm }) },
                    if (ex.kind == ExKind.WEIGHT) "1ПМ, рекорд" else "рекорд", Modifier.weight(1f),
                )
                com.dasein.poryadok.ui.common.Stat(Dates.label(h.last().day), "последний раз", Modifier.weight(1f))
            }
        }
        if (ex.description.isNotBlank()) {
            Gap(12.dp)
            com.dasein.poryadok.ui.common.FoldTile("ex_tech", "Техника") {
                Text(ex.description, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
        if (h.isNotEmpty()) {
            ExerciseRecords(ex.kind, h)
            val prDays = remember(h) { recordDays(ex.kind, h) }
            SectionTitle("История")
            Tile(padding = 12.dp) {
                h.reversed().forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(Dates.label(d.day), Modifier.width(96.dp), fontSize = 13.sp, color = extra.dim)
                        Column(Modifier.weight(1f)) {
                            Text(TrainingRepo.describeDone(ex.kind, d.sets) + if (d.day in prDays) "  · рекорд!" else "", fontSize = 14.sp, fontWeight = if (d.day in prDays) FontWeight.SemiBold else FontWeight.Normal)
                            if (ex.kind == ExKind.WEIGHT) Text("1ПМ ≈ ${TrainingRepo.e1rmText(d.best1rm)} · тоннаж ${w(d.volume)} кг", fontSize = 12.sp, color = extra.dim)
                        }
                    }
                }
            }
        }
        Gap(12.dp)
        // Всё в карточке правится и после сохранения: название, мышцы, техника и сами видео (монтаж, замена).
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(btnLabel("Изменить карточку и видео")) }
        TextButton(onClick = onArchive, Modifier.fillMaxWidth()) { Text(btnLabel("Убрать в архив"), color = extra.danger) }
        Gap(40.dp)
    }
}

@Composable
private fun InfoChip(text: String, glyph: String?) {
    val extra = LocalExtra.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(extra.cardHigh).padding(start = if (glyph != null) 8.dp else 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) { Glyph(glyph, 18.dp, badge = false); HGap(6.dp) }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Режим правки карточки: видео и фото, название, мышцы, инвентарь, как считать, техника. */
@Composable
private fun ExerciseEditBody(modifier: Modifier, ex: Exercise, id: Long, onChange: (Exercise) -> Unit, onDone: () -> Unit) {
    val t = Graph.training
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val cur by androidx.compose.runtime.rememberUpdatedState(ex)
    val addMedia = rememberMediaPicker("training", video = true) { path -> onChange(cur.copy(media = (cur.media.lines().filter { it.isNotBlank() } + path).joinToString("\n"))) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        val media = ex.media.lines().filter { it.isNotBlank() }
        SectionTitle("Видео и фото техники")
        media.forEach { m ->
            // Видео можно смонтировать заново (обрезка, куски, скорость), заменить другим или убрать — и после сохранения тоже.
            var replacing by remember(m) { mutableStateOf(false) }
            Tile(Modifier.padding(bottom = 8.dp), padding = 10.dp) {
                if (Media.isVideo(m)) VideoPlayer(m, Modifier.fillMaxWidth(), maxHeight = 360.dp)
                else MediaView(m, Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(12.dp)), crop = false)
                val swap: (String) -> Unit = { new ->
                    val upd = cur.copy(media = cur.media.lines().filter { it.isNotBlank() }.map { if (it == m) new else it }.joinToString("\n"))
                    onChange(upd)
                    if (id != 0L) io { t.upsertExercise(upd) }
                }
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.Center) {
                    if (Media.isVideo(m)) com.dasein.poryadok.ui.common.EditVideoButton(m, onDone = swap)
                    if (Media.isVideo(m)) TextButton(onClick = { replacing = !replacing }) { Text(btnLabel(if (replacing) "Не заменять" else "Заменить")) }
                    if (Media.isVideo(m)) com.dasein.poryadok.ui.common.CompressVideoButton(m, swap)
                    TextButton(onClick = {
                        val upd = ex.copy(media = media.filter { it != m }.joinToString("\n"))
                        onChange(upd)
                        // Убираем сразу: сохраняем упражнение и удаляем файл, если он больше нигде не нужен.
                        io {
                            if (id != 0L) t.upsertExercise(upd)
                            if (m.startsWith(ctx.filesDir.absolutePath) && t.exercisesNow().none { x -> x.id != upd.id && m in x.media.lines() }) java.io.File(m).delete()
                        }
                    }) { Text(btnLabel("Убрать"), color = extra.danger) }
                }
                if (replacing) {
                    Text("Новое видео встанет на место этого — сначала откроется монтаж.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 4.dp))
                    VideoAddButtons("training", galleryLabel = "Из галереи", filesLabel = "Из файлов") { new ->
                        replacing = false
                        swap(new)
                        // Прежний файл больше не нужен, если его нет в других упражнениях.
                        io {
                            if (m.startsWith(ctx.filesDir.absolutePath) && t.exercisesNow().none { x -> x.id != id && m in x.media.lines() }) java.io.File(m).delete()
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { addMedia() }, modifier = Modifier.fillMaxWidth()) { Text(btnLabel("+ Фото или GIF техники")) }
        VideoAddButtons("training") { path -> onChange(cur.copy(media = (cur.media.lines().filter { it.isNotBlank() } + path).joinToString("\n"))) }
        SectionTitle("Название")
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphField(ex.glyph, { g -> onChange(ex.copy(glyph = g)) })
            HGap(12.dp)
            TextInput(ex.name, { onChange(ex.copy(name = it)) }, "Название", Modifier.weight(1f))
        }
        SectionTitle("Мышцы")
        MusclePills(ex.muscle, { m -> onChange(ex.copy(muscle = m)) })
        SectionTitle("Инвентарь")
        TextInput(ex.equipment, { onChange(ex.copy(equipment = it)) }, "Например: гантели, стул")
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("Без инвентаря") + com.dasein.poryadok.data.ExercisePrefs.myGear.value.toList()).forEach { g ->
                Pill(g, ex.equipment.equals(g, true)) { onChange(ex.copy(equipment = g)) }
            }
        }
        SectionTitle("Как считать")
        Segments(ExKind.names.mapIndexed { i, n -> i to n }, ex.kind, { onChange(ex.copy(kind = it)) })
        SectionTitle("Техника и описание")
        TextInput(ex.description, { onChange(ex.copy(description = it)) }, "Как выполнять, на что обратить внимание", singleLine = false, minLines = 4)
        Gap(16.dp)
        Button(onClick = onDone, enabled = ex.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(btnLabel("Готово")) }
        Gap(40.dp)
    }
}

/**
 * «Мой подход»: подходы, повторы (или секунды) и чем утяжеляю. Запоминается для упражнения и подставляется
 * при следующих стартах — набирать заново не нужно; поправить можно кнопкой «Настроить».
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExerciseSetupDialog(
    ex: Exercise, initial: com.dasein.poryadok.data.ExerciseSetup?, andStart: Boolean,
    onDismiss: () -> Unit, onSave: (com.dasein.poryadok.data.ExerciseSetup) -> Unit,
) {
    val extra = LocalExtra.current
    val base = initial ?: com.dasein.poryadok.data.ExerciseSetup(
        load = when {
            "гантел" in ex.equipment.lowercase() -> com.dasein.poryadok.data.ExerciseSetup.DUMBBELLS
            "утяжел" in ex.equipment.lowercase() -> com.dasein.poryadok.data.ExerciseSetup.WEAR
            else -> com.dasein.poryadok.data.ExerciseSetup.BODY
        },
    )
    var sets by remember { mutableStateOf(base.sets.toString()) }
    var reps by remember { mutableStateOf(base.reps.toString()) }
    var seconds by remember { mutableStateOf(base.seconds.toString()) }
    var load by remember { mutableStateOf(base.load) }
    var db by remember { mutableStateOf(w(base.dumbbellKg)) }
    var wear by remember { mutableStateOf(w(base.wearKg)) }
    fun d(v: String) = v.replace(',', '.').toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Мой подход")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Запомню для «${ex.name}» — в следующий раз ничего набирать не придётся.", fontSize = 13.sp, color = extra.dim)
                Gap(12.dp)
                StepField(sets, { sets = it }, "Подходы", 1.0)
                Gap(8.dp)
                if (ex.kind == ExKind.TIME) StepField(seconds, { seconds = it }, "Секунд в подходе", 5.0)
                else StepField(reps, { reps = it }, "Повторов в подходе", 1.0)
                Gap(12.dp)
                Text(btnLabel("С каким весом"), fontSize = 12.sp, color = extra.dim)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.dasein.poryadok.data.ExerciseSetup.LOADS.forEach { (k, label) -> Pill(label, load == k) { load = k } }
                }
                if (load == com.dasein.poryadok.data.ExerciseSetup.DUMBBELLS || load == com.dasein.poryadok.data.ExerciseSetup.BOTH) {
                    Gap(8.dp); StepField(db, { db = it }, "Одна гантель, кг", 1.0, decimal = true)
                }
                if (load == com.dasein.poryadok.data.ExerciseSetup.WEAR || load == com.dasein.poryadok.data.ExerciseSetup.BOTH) {
                    Gap(8.dp); StepField(wear, { wear = it }, "Утяжелители, всего кг", 0.5, decimal = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    com.dasein.poryadok.data.ExerciseSetup(
                        sets = (sets.toIntOrNull() ?: 3).coerceIn(1, 20), reps = (reps.toIntOrNull() ?: 12).coerceIn(1, 500),
                        seconds = (seconds.toIntOrNull() ?: 30).coerceIn(5, 7200), load = load,
                        dumbbellKg = d(db).coerceIn(0.0, 100.0), wearKg = d(wear).coerceIn(0.0, 50.0),
                    )
                )
            }) { Text(btnLabel(if (andStart) "Начать" else "Сохранить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
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
    var summary by remember { mutableStateOf<com.dasein.poryadok.data.FinishResult?>(null) }
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
        actions = { if (!finished) TextButton(onClick = { finishing = true }) { Text(btnLabel("Завершить")) } },
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
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        // Подсказка на сегодня по прошлой тренировке — для упражнений не из программы.
                        if (!finished && pe == null && prev != null && list.none { it.done }) {
                            val sug = remember(prev) { com.dasein.poryadok.logic.Progression.suggest(kind, prev.map { com.dasein.poryadok.logic.DoneSet(it.reps, it.weight, it.seconds, it.done) }) }
                            sug?.let { nt ->
                                val tg = nt.target
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(scheme.primary.copy(alpha = .10f)).padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Сегодня: " + TrainingRepo.describe(kind, tg.sets, tg.reps, tg.weight, tg.seconds), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(nt.reason, fontSize = 12.sp, color = extra.dim)
                                    }
                                    TextButton(onClick = {
                                        io { list.filter { !it.done }.forEach { st -> t.upsertSet(st.copy(weight = if (kind == ExKind.WEIGHT) tg.weight else st.weight, reps = if (kind == ExKind.TIME) st.reps else tg.reps, seconds = if (kind == ExKind.TIME) tg.seconds else st.seconds)) } }
                                        list.forEach { fields.remove(it.id) }
                                    }) { Text("Применить") }
                                }
                            }
                        }
                        if (showVideo && video != null) {
                            Gap(6.dp)
                            VideoPlayer(video, Modifier.fillMaxWidth(), maxHeight = 420.dp)
                        }
                        Gap(6.dp)
                        Row(Modifier.fillMaxWidth()) {
                            Text(btnLabel("#"), Modifier.width(26.dp), fontSize = 12.sp, color = extra.dim)
                            // У упражнений на время вес необязателен: блин на спине в планке, гантели в руках — он идёт в калории.
                            if (kind != ExKind.REPS) Text(btnLabel("вес"), Modifier.weight(1f), fontSize = 12.sp, color = extra.dim)
                            Text(if (kind == ExKind.TIME) "время" else "повторы", Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp, color = extra.dim)
                            Box(Modifier.width(52.dp))
                        }
                        list.sortedBy { it.setIndex }.forEachIndexed { i, set ->
                            val f = fields[set.id] ?: (w(set.weight) to (if (kind == ExKind.TIME) set.seconds else set.reps).toString())
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", Modifier.width(26.dp), fontWeight = FontWeight.SemiBold)
                                if (kind != ExKind.REPS) SetField(f.first, { edit(set, it, null) }, "кг", Modifier.weight(1f), !finished)
                                if (kind != ExKind.REPS) HGap(8.dp)
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
                                ) { Text(btnLabel("✓"), color = if (set.done) scheme.onPrimary else scheme.outline, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                            }
                        }
                        if (!finished) Row {
                            TextButton(onClick = { io { TrainingRepo.addSet(id, exId) } }) { Text(btnLabel("+ Подход")) }
                            val last = list.maxByOrNull { it.setIndex }
                            if (last != null && list.size > 1) TextButton(onClick = { io { t.deleteSet(last) } }) { Text(btnLabel("− Подход"), color = extra.dim) }
                        }
                    }
                }
                if (!finished) item {
                    OutlinedButton(onClick = { adding = true }, Modifier.fillMaxWidth()) { Text(btnLabel("+ Упражнение в эту тренировку")) }
                }
                if (finished && s.note.isNotBlank()) item { Text(s.note, color = extra.dim, modifier = Modifier.padding(8.dp)) }
                if (finished) item {
                    TextButton(onClick = { discard = true }) { Text(btnLabel("Удалить тренировку"), color = extra.danger) }
                }
            }
            restEnd?.let { end ->
                val left = ((end - now) / 1000).coerceAtLeast(0)
                Row(
                    Modifier.fillMaxWidth().background(scheme.primary.copy(alpha = .15f)).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Отдых ${mmss(left)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { restEnd = end - 15_000 }) { Text(btnLabel("−15")) }
                    TextButton(onClick = { restEnd = end + 15_000 }) { Text(btnLabel("+15")) }
                    TextButton(onClick = { restEnd = null }) { Text(btnLabel("Пропустить")) }
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
            title = { Text(btnLabel("Завершить тренировку?")) },
            text = {
                Column {
                    Text(btnLabel("Как прошла?"), fontSize = 13.sp, color = extra.dim)
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
                        val result = TrainingRepo.finish(id, feel, note)
                        withContext(Dispatchers.Main) { summary = result }
                    }
                }) { Text(btnLabel("Завершить")) }
            },
            dismissButton = {
                com.dasein.poryadok.ui.common.ButtonsRow {
                    TextButton(onClick = { finishing = false; discard = true }) { Text(btnLabel("Удалить"), color = extra.danger) }
                    TextButton(onClick = { finishing = false }) { Text(btnLabel("Продолжить")) }
                }
            },
        )
    }
    summary?.let { result ->
        val notes = result.notes
        AlertDialog(
            onDismissRequest = { summary = null; nav.popBackStack() },
            title = { Text(btnLabel("Тренировка сохранена")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Сожжено примерно ${result.kcal} ккал", fontWeight = FontWeight.SemiBold)
                    if (result.exerciseKcal > 0) Text("Из них на упражнения — ${result.exerciseKcal} ккал, остальное — по времени тренировки.", fontSize = 13.sp, color = extra.dim)
                    if (notes.isEmpty()) Text("Записана в журнал тренировок и калории дня.", modifier = Modifier.padding(top = 8.dp))
                    notes.forEach { n ->
                        Text(n.exercise, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        Text("Сделано: ${n.done}", fontSize = 13.sp)
                        Text("В следующий раз: ${n.next}", fontSize = 13.sp, color = scheme.primary)
                        Text(n.reason, fontSize = 12.sp, color = extra.dim)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { summary = null; nav.popBackStack() }) { Text(btnLabel("Готово")) } },
        )
    }
    if (discard) ConfirmDialog("Удалить тренировку?", "Подходы этой тренировки будут удалены.", onDismiss = { discard = false }) {
        io { TrainingRepo.discard(id) }
        nav.popBackStack()
    }
}

/**
 * Перед тренировкой: вес одной гантели и утяжелителей. Прошлые значения подставлены — обычно достаточно нажать «Начать».
 * По ним считаются калории упражнений с гантелями (если вес в подходе не указан) и всех упражнений в утяжелителях.
 */
@Composable
private fun GearDialog(onDismiss: () -> Unit, onStart: (com.dasein.poryadok.logic.ExerciseEnergy.Gear) -> Unit) {
    val extra = LocalExtra.current
    val last = remember { com.dasein.poryadok.data.TrainingGear.last() }
    var dumbbell by remember { mutableStateOf(w(last.dumbbellKg)) }
    var wear by remember { mutableStateOf(w(last.wearKg)) }
    fun num(v: String) = v.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("С каким весом тренируетесь?")) },
        text = {
            Column {
                Text("Нужно для подсчёта калорий. Оставьте 0, если без гантелей или утяжелителей.", fontSize = 13.sp, color = extra.dim)
                Gap(16.dp)
                StepField(dumbbell, { dumbbell = it }, "Одна гантель, кг", 1.0, decimal = true)
                Gap(12.dp)
                StepField(wear, { wear = it }, "Утяжелители, всего кг", 0.5, decimal = true)
            }
        },
        confirmButton = { TextButton(onClick = { onStart(com.dasein.poryadok.logic.ExerciseEnergy.Gear(num(dumbbell), num(wear))) }) { Text(btnLabel("Начать")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/** Число с кнопками «−» и «+» по бокам: подходы, повторы, вес меняются одним касанием. */
@Composable
private fun StepField(value: String, onChange: (String) -> Unit, label: String, step: Double, modifier: Modifier = Modifier, decimal: Boolean = false) {
    val extra = LocalExtra.current
    fun shift(k: Int) {
        val v = (value.replace(',', '.').toDoubleOrNull() ?: 0.0) + k * step
        val r = v.coerceAtLeast(0.0)
        onChange(if (decimal && r != Math.floor(r)) "%.2f".format(r).trimEnd('0').trimEnd(',', '.').replace('.', ',') else r.toLong().toString())
    }
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−") { shift(-1) }
            androidx.compose.material3.OutlinedTextField(
                value, { onChange(it.filter { c -> c.isDigit() || (decimal && (c == ',' || c == '.')) }) },
                singleLine = true, modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (decimal) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
            )
            StepButton("+") { shift(1) }
        }
    }
}

@Composable
private fun StepButton(sign: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .16f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(sign, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
}
