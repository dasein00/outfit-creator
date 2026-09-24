@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BodyProfile
import com.dasein.poryadok.data.DayLog
import com.dasein.poryadok.data.FoodEntry
import com.dasein.poryadok.data.Measurement
import com.dasein.poryadok.data.ProgressPhoto
import com.dasein.poryadok.data.WeightEntry
import com.dasein.poryadok.data.Workout
import com.dasein.poryadok.logic.ACTIVITY_LEVELS
import com.dasein.poryadok.logic.BodyInput
import com.dasein.poryadok.logic.CalorieGoal
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Intensity
import com.dasein.poryadok.logic.Nutrition
import com.dasein.poryadok.logic.NutritionPlan
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.BarChart
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Images
import com.dasein.poryadok.ui.common.LineChart
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.ProgressRing
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Series
import com.dasein.poryadok.ui.common.Stat
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.plain
import com.dasein.poryadok.ui.common.rememberImage
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val MEALS = listOf("🌅 Завтрак", "☀️ Обед", "🌙 Ужин", "🍎 Перекус")
val WORKOUT_TYPES = listOf("🏋️ Силовая", "🏃 Бег", "🚴 Вело", "🏊 Плавание", "🧘 Йога", "🤸 Растяжка", "⚡ HIIT", "🚶 Ходьба", "⚽ Игры", "✨ Другое")

fun BodyProfile.input(weight: Double) = BodyInput(
    male, heightCm, age, weight, activity,
    runCatching { CalorieGoal.valueOf(goal) }.getOrDefault(CalorieGoal.DEFICIT),
    runCatching { Intensity.valueOf(intensity) }.getOrDefault(Intensity.STANDARD),
    proteinPerKg, fatPerKg,
)

@Composable
fun HealthScreen(nav: NavHostController, initialTab: Int) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val dao = Graph.dao
    val profileOrNull by observe(null) { dao.profile() }
    val weights by observe(emptyList()) { dao.weights() }
    LaunchedEffect(profileOrNull) {
        if (profileOrNull == null) dao.upsertProfile(BodyProfile(startDay = Dates.weekStart(Dates.today())))
    }
    val profile = profileOrNull ?: BodyProfile()
    val current = weights.lastOrNull()?.kg ?: profile.startWeight
    val plan = Nutrition.plan(profile.input(current))
    Screen(title = "Здоровье и тело", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Питание", "Вес", "Замеры", "Тренировки", "Калькулятор", "Прогресс").forEachIndexed { i, t ->
                    Tab(tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                when (tab) {
                    0 -> FoodTab(plan, profile)
                    1 -> WeightTab(profile, weights)
                    2 -> MeasureTab()
                    3 -> WorkoutTab()
                    4 -> CalcTab(profile, current, plan)
                    5 -> ProgressTab(weights)
                }
                Gap(80.dp)
            }
        }
    }
}

@Composable
private fun CalcTab(p: BodyProfile, current: Double, plan: NutritionPlan) {
    val extra = LocalExtra.current
    fun upd(b: BodyProfile) = io { Graph.dao.upsertProfile(b) }
    var height by remember(p.id) { mutableStateOf(p.heightCm.plain()) }
    var age by remember(p.id) { mutableStateOf(p.age.toString()) }
    var start by remember(p.id) { mutableStateOf(p.startWeight.plain()) }
    var change by remember(p.id) { mutableStateOf(p.changeKg.plain()) }
    var protein by remember(p.id) { mutableStateOf(p.proteinPerKg.plain()) }
    var fat by remember(p.id) { mutableStateOf(p.fatPerKg.plain()) }
    var water by remember(p.id) { mutableStateOf(p.waterGoalMl.toString()) }
    var steps by remember(p.id) { mutableStateOf(p.stepsGoal.toString()) }

    Gap(8.dp)
    Text("Заполните один раз — норма пересчитается сама после каждого взвешивания.", fontSize = 13.sp, color = extra.dim)
    Gap(10.dp)
    Segments(listOf(false to "Женщина", true to "Мужчина"), p.male, { upd(p.copy(male = it)) })
    Gap(10.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(height, { height = it; it.num()?.let { v -> upd(p.copy(heightCm = v)) } }, "Рост", Modifier.weight(1f), "см")
        NumberField(age, { age = it; it.toIntOrNull()?.let { v -> upd(p.copy(age = v)) } }, "Возраст", Modifier.weight(1f), "лет", decimal = false)
    }
    Gap(8.dp)
    NumberField(start, { start = it; it.num()?.let { v -> upd(p.copy(startWeight = v)) } }, "Стартовый вес", suffix = "кг")
    Text("Текущий вес: ${current.plain()} кг (из последнего взвешивания)", fontSize = 12.sp, color = extra.dim)
    SectionTitle("Активность")
    ACTIVITY_LEVELS.forEach { (k, label) ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { upd(p.copy(activity = k)) }
                .background(if (p.activity == k) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                .padding(10.dp),
        ) {
            Text("×$k", Modifier.size(width = 56.dp, height = 20.dp), color = extra.dim, fontSize = 13.sp)
            Text(label, fontSize = 14.sp)
        }
    }
    SectionTitle("Цель по калориям")
    Segments(CalorieGoal.entries.map { it.name to it.label }, p.goal, { upd(p.copy(goal = it)) })
    Gap(8.dp)
    Segments(Intensity.entries.map { it.name to it.label }, p.intensity, { upd(p.copy(intensity = it)) })
    Gap(8.dp)
    NumberField(change, { change = it; it.num()?.let { v -> upd(p.copy(changeKg = v)) } }, "Изменить за 3 месяца", suffix = "кг")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        NumberField(protein, { protein = it; it.num()?.let { v -> upd(p.copy(proteinPerKg = v)) } }, "Белок", Modifier.weight(1f), "г/кг")
        NumberField(fat, { fat = it; it.num()?.let { v -> upd(p.copy(fatPerKg = v)) } }, "Жир", Modifier.weight(1f), "г/кг")
    }
    Text("Белок: 1.6 минимум · 1.8 стандарт на дефиците · 2.0–2.2 при силовых. Жир: 0.7–1.0 г/кг.", fontSize = 12.sp, color = extra.dim)

    SectionTitle("Ваша норма")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("${plan.bmr.roundToInt()}", "базовый обмен", Modifier.weight(1f))
        Stat("${plan.tdee.roundToInt()}", "расход в день", Modifier.weight(1f))
        Stat("${plan.targetKcal}", "цель, ккал", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
    }
    Gap(8.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("${plan.proteinG} г", "белки", Modifier.weight(1f))
        Stat("${plan.fatG} г", "жиры", Modifier.weight(1f))
        Stat("${plan.carbsG} г", "углеводы", Modifier.weight(1f))
    }
    Gap(8.dp)
    Tile {
        val d = plan.deltaKcal.roundToInt()
        Text(
            if (d == 0) "Поддержание веса: едим на уровне расхода."
            else "${if (d < 0) "Дефицит" else "Профицит"} ${kotlin.math.abs(d)} ккал в день. " +
                "Реалистично за 3 месяца: ≈ ${"%.1f".format(plan.realisticKgPer12Weeks)} кг.",
        )
        Text("У всех по-разному — ориентируйтесь на самочувствие и реальные замеры, а не только на цифры.", fontSize = 12.sp, color = extra.dim)
    }
    SectionTitle("Другие цели")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(water, { water = it; it.toIntOrNull()?.let { v -> upd(p.copy(waterGoalMl = v)) } }, "Вода", Modifier.weight(1f), "мл", decimal = false)
        NumberField(steps, { steps = it; it.toIntOrNull()?.let { v -> upd(p.copy(stepsGoal = v)) } }, "Шаги", Modifier.weight(1f), decimal = false)
    }
    TextButton(onClick = { water = Nutrition.waterGoalMl(current).toString(); upd(p.copy(waterGoalMl = Nutrition.waterGoalMl(current))) }) {
        Text("Рассчитать воду по весу (30 мл/кг)")
    }
}

@Composable
private fun FoodTab(plan: NutritionPlan, profile: BodyProfile) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    var day by rememberSaveable { mutableStateOf(Dates.today()) }
    val food by observe(emptyList()) { dao.food() }
    val dayLogs by observe(emptyList()) { dao.dayLogs() }
    var edit by remember { mutableStateOf<FoodEntry?>(null) }
    val entries = food.filter { it.day == day }
    val kcal = entries.sumOf { it.kcal }
    val p = entries.sumOf { it.protein }
    val f = entries.sumOf { it.fat }
    val c = entries.sumOf { it.carbs }
    val pct = if (plan.targetKcal > 0) kcal * 100 / plan.targetKcal else 0
    val levelColor = when (Nutrition.level(pct)) { Nutrition.Level.OK -> extra.ok; Nutrition.Level.WARN -> extra.warn; Nutrition.Level.BAD -> extra.danger }
    val log = dayLogs.firstOrNull { it.day == day } ?: DayLog(day)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { day-- }) { Icon(Icons.Default.ChevronLeft, "Раньше") }
        Text("${Dates.weekdayShort(day)}, ${Dates.label(day)}", Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = { if (day < Dates.today()) day++ }) { Icon(Icons.Default.ChevronRight, "Позже") }
    }
    Tile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(kcal / plan.targetKcal.coerceAtLeast(1).toFloat(), levelColor, size = 96.dp, stroke = 9.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$kcal", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text("из ${plan.targetKcal}", fontSize = 11.sp, color = extra.dim)
                }
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                MacroBar("Белки", p, plan.proteinG, Palette.item(2))
                MacroBar("Жиры", f, plan.fatG, Palette.item(0))
                MacroBar("Углеводы", c, plan.carbsG, Palette.item(1))
            }
        }
        Text(
            when (Nutrition.level(pct)) {
                Nutrition.Level.OK -> "$pct% от плана — в норме 👍"
                Nutrition.Level.WARN -> "$pct% от плана — " + if (pct < 100) "небольшой недобор" else "немного больше плана"
                Nutrition.Level.BAD -> "$pct% от плана — " + if (pct < 100) "сильно недоели" else "переели"
            },
            fontSize = 13.sp, color = levelColor, modifier = Modifier.padding(top = 8.dp),
        )
    }
    MEALS.forEachIndexed { mi, meal ->
        val list = entries.filter { it.meal == mi }
        SectionTitle("$meal  ${list.sumOf { it.kcal }} ккал", action = "+ Добавить") { edit = FoodEntry(day = day, meal = mi, name = "") }
        list.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { edit = e }.padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Text(e.name, Modifier.weight(1f))
                Text("${e.kcal} ккал", color = extra.dim, fontSize = 13.sp)
            }
        }
    }
    SectionTitle("Шаги и активность")
    var stepsText by remember(day, log.steps) { mutableStateOf(if (log.steps > 0) log.steps.toString() else "") }
    var act by remember(day, log.activity) { mutableStateOf(log.activity) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(stepsText, { stepsText = it; io { dao.upsertDayLog(log.copy(steps = it.toIntOrNull() ?: 0)) } }, "Шаги", Modifier.weight(1f), decimal = false)
        TextInput(act, { act = it; io { dao.upsertDayLog(log.copy(activity = it)) } }, "Доп. активность", Modifier.weight(1.4f))
    }
    if (log.steps > 0) Bar(log.steps / profile.stepsGoal.coerceAtLeast(1).toFloat(), extra.ok, Modifier.padding(top = 8.dp))

    edit?.let { FoodDialog(it, food) { edit = null } }
}

@Composable
private fun MacroBar(label: String, v: Double, target: Int, color: androidx.compose.ui.graphics.Color) {
    Row { Text(label, Modifier.weight(1f), fontSize = 12.sp); Text("${v.roundToInt()}/$target г", fontSize = 12.sp, color = LocalExtra.current.dim) }
    Bar((v / target.coerceAtLeast(1)).toFloat(), color, Modifier.padding(top = 2.dp, bottom = 6.dp), height = 6.dp)
}

@Composable
private fun FoodDialog(e0: FoodEntry, history: List<FoodEntry>, onDismiss: () -> Unit) {
    var e by remember { mutableStateOf(e0) }
    var kcal by remember { mutableStateOf(if (e0.kcal > 0) e0.kcal.toString() else "") }
    var p by remember { mutableStateOf(if (e0.protein > 0) e0.protein.plain() else "") }
    var f by remember { mutableStateOf(if (e0.fat > 0) e0.fat.plain() else "") }
    var c by remember { mutableStateOf(if (e0.carbs > 0) e0.carbs.plain() else "") }
    val frequent = remember(history) { history.groupBy { it.name.lowercase() }.values.sortedByDescending { it.size }.map { it.first() }.take(12) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(MEALS[e.meal]) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextInput(e.name, { e = e.copy(name = it) }, "Что съели")
                if (e0.id == 0L && frequent.isNotEmpty()) {
                    Text("Часто едите:", fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        frequent.forEach { fr ->
                            Pill(fr.name, false) {
                                e = e.copy(name = fr.name)
                                kcal = fr.kcal.toString(); p = fr.protein.plain(); f = fr.fat.plain(); c = fr.carbs.plain()
                            }
                        }
                    }
                }
                Gap(8.dp)
                NumberField(kcal, { kcal = it }, "Калории", suffix = "ккал", decimal = false)
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField(p, { p = it }, "Б", Modifier.weight(1f))
                    NumberField(f, { f = it }, "Ж", Modifier.weight(1f))
                    NumberField(c, { c = it }, "У", Modifier.weight(1f))
                }
                if (kcal.isBlank() && (p.num() != null || f.num() != null || c.num() != null)) {
                    val est = ((p.num() ?: 0.0) * 4 + (f.num() ?: 0.0) * 9 + (c.num() ?: 0.0) * 4).roundToInt()
                    TextButton(onClick = { kcal = est.toString() }) { Text("Посчитать по БЖУ: $est ккал") }
                }
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MEALS.forEachIndexed { i, m -> Pill(m, e.meal == i) { e = e.copy(meal = i) } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (e.name.isNotBlank()) {
                    val cur = e.copy(
                        name = e.name.trim(), kcal = kcal.toIntOrNull() ?: 0,
                        protein = p.num() ?: 0.0, fat = f.num() ?: 0.0, carbs = c.num() ?: 0.0,
                    )
                    io { Graph.dao.upsertFood(cur) }
                }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (e0.id != 0L) TextButton(onClick = { io { Graph.dao.deleteFood(e0) }; onDismiss() }) { Text("Удалить", color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun WeightTab(profile: BodyProfile, weights: List<WeightEntry>) {
    val extra = LocalExtra.current
    var add by remember { mutableStateOf(false) }
    val goal = runCatching { CalorieGoal.valueOf(profile.goal) }.getOrDefault(CalorieGoal.DEFICIT)
    val start = if (profile.startDay > 0) profile.startDay else weights.firstOrNull()?.day ?: Dates.today()
    Gap(8.dp)
    Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Взвеситься") }
    Text(
        "Взвешивайтесь раз в неделю, утром натощак, после туалета — тогда цифры сравнимы.",
        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 8.dp),
    )
    val last = weights.lastOrNull()
    if (last != null) {
        val bmi = last.kg / ((profile.heightCm / 100) * (profile.heightCm / 100))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${last.kg.plain()} кг", "сейчас", Modifier.weight(1f))
            val diff = last.kg - profile.startWeight
            Stat("${if (diff > 0) "+" else ""}${"%.1f".format(diff)}", "от старта", Modifier.weight(1f), if ((diff < 0) == (goal == CalorieGoal.DEFICIT)) extra.ok else extra.warn)
            Stat("%.1f".format(bmi), "ИМТ", Modifier.weight(1f))
        }
    }
    SectionTitle("План и факт по неделям")
    Tile {
        val planPts = (0..12).map { Nutrition.plannedWeight(profile.startWeight, profile.changeKg, goal, it).toFloat() }
        val factPts = (0..12).map { w ->
            val from = start + w * 7L
            weights.filter { it.day in from..(from + 6) }.lastOrNull()?.kg?.toFloat()
        }
        LineChart(
            listOf(Series(planPts, extra.dim, dashed = true), Series(factPts, MaterialTheme.colorScheme.primary)),
            labels = (0..12 step 2).map { "н${it + 1}" },
        )
        Row(Modifier.padding(top = 6.dp)) {
            Text("— — план", fontSize = 12.sp, color = extra.dim)
            Text("   ● факт", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
    SectionTitle("Записи")
    weights.reversed().forEach { w ->
        val week = ((w.day - start) / 7).toInt()
        val plan = Nutrition.plannedWeight(profile.startWeight, profile.changeKg, goal, week)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${w.kg.plain()} кг", fontWeight = FontWeight.Medium)
                Text("${Dates.label(w.day)} · неделя ${week + 1} · план ${"%.1f".format(plan)}", fontSize = 12.sp, color = extra.dim)
            }
            IconButton(onClick = { io { Graph.dao.deleteWeight(w) } }) { Icon(Icons.Default.Close, "Удалить", tint = extra.dim) }
        }
    }
    if (weights.isEmpty()) Empty("⚖️", "Нет взвешиваний", "Добавьте первое — и график оживёт.")
    if (add) WeightDialog(weights.lastOrNull()?.kg ?: profile.startWeight) { add = false }
}

@Composable
private fun WeightDialog(last: Double, onDismiss: () -> Unit) {
    var day by remember { mutableStateOf(Dates.today()) }
    var kg by remember { mutableStateOf(last.plain()) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Взвешивание") },
        text = {
            Column {
                FieldButton("Дата", Dates.label(day), Modifier.fillMaxWidth()) { pick = true }
                Gap(8.dp)
                NumberField(kg, { kg = it }, "Вес", suffix = "кг")
            }
        },
        confirmButton = {
            TextButton(onClick = { kg.num()?.let { v -> io { Graph.dao.upsertWeight(WeightEntry(day, v)) } }; onDismiss() }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    if (pick) DatePickDialog(day, onDismiss = { pick = false }, onPick = { it?.let { d -> day = d } }, allowClear = false)
}

private val MEASURE_FIELDS = listOf("chest" to "Грудь", "waist" to "Талия", "belly" to "Низ живота", "hips" to "Бёдра", "arm" to "Плечо (бицепс)")

private fun Measurement.get(k: String): Double? = when (k) { "chest" -> chest; "waist" -> waist; "belly" -> belly; "hips" -> hips; else -> arm }

@Composable
private fun MeasureTab() {
    val extra = LocalExtra.current
    val ms by observe(emptyList()) { Graph.dao.measurements() }
    var edit by remember { mutableStateOf<Measurement?>(null) }
    Gap(8.dp)
    Button(onClick = { edit = Measurement(Dates.today()) }, modifier = Modifier.fillMaxWidth()) { Text("+ Новые замеры") }
    Text(
        "Раз в 2 недели, сантиметровой лентой, утром натощак: 1 — грудь, 2 — талия, 3 — низ живота, 4 — бёдра, 5 — плечо.",
        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 8.dp),
    )
    if (ms.size >= 2) {
        SectionTitle("Талия и бёдра")
        Tile {
            LineChart(
                listOf(
                    Series(ms.map { it.waist?.toFloat() }, MaterialTheme.colorScheme.primary),
                    Series(ms.map { it.hips?.toFloat() }, Palette.item(3)),
                ),
            )
            Row(Modifier.padding(top = 6.dp)) {
                Text("● талия", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text("   ● бёдра", fontSize = 12.sp, color = Palette.item(3))
            }
        }
    }
    SectionTitle("Записи")
    if (ms.isEmpty()) Empty("📏", "Замеров пока нет", "Сантиметры часто показывают прогресс раньше весов.")
    ms.reversed().forEach { m ->
        Tile(Modifier.padding(bottom = 8.dp), onClick = { edit = m }) {
            Text(Dates.full(m.day), fontWeight = FontWeight.Medium)
            Text(MEASURE_FIELDS.joinToString(" · ") { (k, l) -> "$l ${m.get(k)?.plain() ?: "—"}" }, fontSize = 12.sp, color = extra.dim)
        }
    }
    edit?.let { m0 ->
        val values = remember(m0) { MEASURE_FIELDS.associate { (k, _) -> k to mutableStateOf(m0.get(k)?.plain() ?: "") } }
        var day by remember(m0) { mutableStateOf(m0.day) }
        var pick by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("Замеры, см") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FieldButton("Дата", Dates.label(day), Modifier.fillMaxWidth()) { pick = true }
                    MEASURE_FIELDS.forEachIndexed { i, (k, l) ->
                        Gap(6.dp)
                        NumberField(values.getValue(k).value, { values.getValue(k).value = it }, "${i + 1}. $l", suffix = "см")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = values.mapValues { it.value.value.num() }
                    val m = Measurement(day, v["chest"], v["waist"], v["belly"], v["hips"], v["arm"])
                    io { if (day != m0.day) Graph.dao.deleteMeasurement(m0); Graph.dao.upsertMeasurement(m) }
                    edit = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { io { Graph.dao.deleteMeasurement(m0) }; edit = null }) { Text("Удалить", color = extra.danger) }
                    TextButton(onClick = { edit = null }) { Text("Отмена") }
                }
            },
        )
        if (pick) DatePickDialog(day, onDismiss = { pick = false }, onPick = { it?.let { d -> day = d } }, allowClear = false)
    }
}

@Composable
private fun WorkoutTab() {
    val extra = LocalExtra.current
    val list by observe(emptyList()) { Graph.dao.workouts() }
    var edit by remember { mutableStateOf<Workout?>(null) }
    val today = Dates.today()
    val weekStart = Dates.weekStart(today)
    val thisWeek = list.filter { it.day >= weekStart }
    Gap(8.dp)
    Button(onClick = { edit = Workout(day = today, type = WORKOUT_TYPES.first()) }, modifier = Modifier.fillMaxWidth()) { Text("+ Тренировка") }
    Gap(8.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("${thisWeek.size}", "на этой неделе", Modifier.weight(1f))
        Stat("${thisWeek.sumOf { it.minutes }}", "минут", Modifier.weight(1f))
        Stat("${thisWeek.sumOf { it.kcal }}", "ккал", Modifier.weight(1f))
    }
    SectionTitle("8 недель")
    Tile {
        val weeks = (7 downTo 0).map { weekStart - it * 7L }
        BarChart(weeks.map { w -> list.count { it.day in w..(w + 6) }.toFloat() }, weeks.map { Dates.short(it) }, MaterialTheme.colorScheme.primary, highlight = 7)
    }
    SectionTitle("Журнал")
    if (list.isEmpty()) Empty("💪", "Тренировок пока нет", "Записывайте каждую — и смотрите, как растёт регулярность.")
    list.forEach { w ->
        Tile(Modifier.padding(bottom = 8.dp), onClick = { edit = w }) {
            Row {
                Text(w.type, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(Dates.label(w.day), color = extra.dim, fontSize = 13.sp)
            }
            Text(listOfNotNull(
                if (w.minutes > 0) "${w.minutes} мин" else null,
                if (w.kcal > 0) "${w.kcal} ккал" else null,
                w.note.takeIf { it.isNotBlank() },
            ).joinToString(" · "), fontSize = 12.sp, color = extra.dim)
            if (w.exercises.isNotBlank()) Text(w.exercises, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
    edit?.let { w0 ->
        var w by remember(w0) { mutableStateOf(w0) }
        var minutes by remember(w0) { mutableStateOf(if (w0.minutes > 0) w0.minutes.toString() else "") }
        var kcal by remember(w0) { mutableStateOf(if (w0.kcal > 0) w0.kcal.toString() else "") }
        var pick by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("Тренировка") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        WORKOUT_TYPES.forEach { t -> Pill(t, w.type == t) { w = w.copy(type = t) } }
                    }
                    Gap(8.dp)
                    FieldButton("Дата", Dates.label(w.day), Modifier.fillMaxWidth()) { pick = true }
                    Gap(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(minutes, { minutes = it }, "Минут", Modifier.weight(1f), decimal = false)
                        NumberField(kcal, { kcal = it }, "Ккал", Modifier.weight(1f), decimal = false)
                    }
                    Gap(8.dp)
                    TextInput(w.exercises, { w = w.copy(exercises = it) }, "Упражнения (жим 3×10 50 кг…)", singleLine = false, minLines = 3)
                    Gap(8.dp)
                    TextInput(w.note, { w = w.copy(note = it) }, "Заметка")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cur = w.copy(minutes = minutes.toIntOrNull() ?: 0, kcal = kcal.toIntOrNull() ?: 0)
                    io { Graph.dao.upsertWorkout(cur) }
                    edit = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    if (w0.id != 0L) TextButton(onClick = { io { Graph.dao.deleteWorkout(w0) }; edit = null }) { Text("Удалить", color = extra.danger) }
                    TextButton(onClick = { edit = null }) { Text("Отмена") }
                }
            },
        )
        if (pick) DatePickDialog(w.day, onDismiss = { pick = false }, onPick = { it?.let { d -> w = w.copy(day = d) } }, allowClear = false)
    }
}

@Composable
private fun ProgressTab(weights: List<WeightEntry>) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ms by observe(emptyList()) { Graph.dao.measurements() }
    val photos by observe(emptyList()) { Graph.dao.photos() }
    var pose by remember { mutableStateOf("Анфас") }
    var open by remember { mutableStateOf<ProgressPhoto?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            Images.importUri(ctx, uri, "progress")?.let { path ->
                Graph.dao.insertPhoto(ProgressPhoto(day = Dates.today(), path = path, pose = pose))
            }
        }
    }
    SectionTitle("Старт и сейчас")
    Tile {
        val rows = buildList {
            add(Triple("Вес, кг", weights.firstOrNull()?.kg, weights.lastOrNull()?.kg))
            MEASURE_FIELDS.forEach { (k, l) ->
                val with = ms.filter { it.get(k) != null }
                add(Triple("$l, см", with.firstOrNull()?.get(k), with.lastOrNull()?.get(k)))
            }
        }
        Row(Modifier.padding(bottom = 4.dp)) {
            Text("Показатель", Modifier.weight(1.4f), fontSize = 12.sp, color = extra.dim)
            Text("Старт", Modifier.weight(1f), fontSize = 12.sp, color = extra.dim, textAlign = TextAlign.End)
            Text("Сейчас", Modifier.weight(1f), fontSize = 12.sp, color = extra.dim, textAlign = TextAlign.End)
            Text("Разница", Modifier.weight(1f), fontSize = 12.sp, color = extra.dim, textAlign = TextAlign.End)
        }
        rows.forEach { (label, s, n) ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(label, Modifier.weight(1.4f), fontSize = 14.sp)
                Text(s?.plain() ?: "—", Modifier.weight(1f), textAlign = TextAlign.End)
                Text(n?.plain() ?: "—", Modifier.weight(1f), textAlign = TextAlign.End)
                val d = if (s != null && n != null) n - s else null
                Text(
                    d?.let { (if (it > 0) "+" else "") + "%.1f".format(it) } ?: "—", Modifier.weight(1f), textAlign = TextAlign.End,
                    color = when { d == null || d == 0.0 -> extra.dim; d < 0 -> extra.ok; else -> extra.warn },
                )
            }
        }
    }
    SectionTitle("Фото до/после")
    Text("Раз в месяц, в одно время суток, при одинаковом свете и в той же одежде.", fontSize = 12.sp, color = extra.dim)
    Gap(8.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Анфас", "Профиль", "Со спины").forEach { p -> Pill(p, pose == p) { pose = p } }
    }
    Gap(8.dp)
    OutlinedButton(
        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("📷 Добавить фото «$pose»") }
    Gap(8.dp)
    photos.groupBy { it.day }.forEach { (day, list) ->
        Text("${Dates.full(day)} · ${list.size} ${plural(list.size, "фото", "фото", "фото")}", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(vertical = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.take(3).forEach { ph ->
                val img by rememberImage(ph.path, 400)
                Box(Modifier.weight(1f).aspectRatio(.75f).clip(RoundedCornerShape(12.dp)).background(extra.card).clickable { open = ph }) {
                    img?.let { Image(it, ph.pose, Modifier.fillMaxWidth().aspectRatio(.75f), contentScale = ContentScale.Crop) }
                    Text(ph.pose, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                }
            }
            repeat(3 - list.take(3).size) { Box(Modifier.weight(1f)) }
        }
    }
    open?.let { ph ->
        var confirm by remember { mutableStateOf(false) }
        val big by rememberImage(ph.path, 1600)
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text("${ph.pose}, ${Dates.full(ph.day)}") },
            text = { big?.let { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) } },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Закрыть") } },
            dismissButton = { TextButton(onClick = { confirm = true }) { Text("Удалить", color = extra.danger) } },
        )
        if (confirm) ConfirmDialog("Удалить фото?", "", onDismiss = { confirm = false }) {
            io { java.io.File(ph.path).delete(); Graph.dao.deletePhoto(ph) }
            open = null
        }
    }
}
