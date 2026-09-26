@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.FoodEntry
import com.dasein.poryadok.data.MealPlanItem
import com.dasein.poryadok.data.PlanStatus
import com.dasein.poryadok.data.RecipeRepo
import com.dasein.poryadok.logic.Cooking
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.MealType
import com.dasein.poryadok.logic.RECIPE_CATEGORIES
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Stat
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.roundToInt

private fun List<MealPlanItem>.active() = filter { it.status != PlanStatus.SKIPPED }
private fun List<MealPlanItem>.kcal() = sumOf { it.kcal * it.servings }
private fun List<MealPlanItem>.protein() = sumOf { it.protein * it.servings }

@Composable
fun MenuPlanner(nav: NavHostController, book: RecipeBook, initialMode: Int) {
    var mode by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
    var day by rememberSaveable { mutableStateOf(Dates.today()) }
    val plan by observe(emptyList()) { Graph.extra.plan() }
    val food by observe(emptyList()) { Graph.dao.food() }
    val target = rememberNutritionPlan()
    Column {
        Segments(listOf(0 to "День", 1 to "Неделя", 2 to "Месяц"), mode, { mode = it }, Modifier.padding(horizontal = 16.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Gap(8.dp)
            when (mode) {
                0 -> DayView(nav, book, day, { day = it }, plan, food, target.targetKcal, target.proteinG)
                1 -> WeekView(nav, day, { day = it }, plan, food, target.targetKcal) { d -> day = d; mode = 0 }
                else -> MonthView(day, { day = it }, plan) { d -> day = d; mode = 0 }
            }
            Gap(40.dp)
        }
    }
}

@Composable
private fun DayNav(label: String, onPrev: () -> Unit, onNext: () -> Unit, onToday: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Раньше") }
        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        if (onToday != null) TextButton(onClick = onToday) { Text("Сегодня") }
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Позже") }
    }
}

@Composable
private fun DayView(
    nav: NavHostController, book: RecipeBook, day: Long, setDay: (Long) -> Unit,
    plan: List<MealPlanItem>, food: List<FoodEntry>, targetKcal: Int, targetProtein: Int,
) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = plan.filter { it.day == day }
    val active = items.active()
    val fact = food.filter { it.day == day }
    var addFor by remember { mutableStateOf<Int?>(null) }
    var menuFor by remember { mutableStateOf<MealPlanItem?>(null) }
    var savePreset by remember { mutableStateOf(false) }

    DayNav("${Dates.weekdayShort(day)}, ${Dates.label(day)}", { setDay(day - 1) }, { setDay(day + 1) }, if (day != Dates.today()) ({ setDay(Dates.today()) }) else null)
    Tile {
        val planned = active.kcal()
        val eaten = fact.sumOf { it.kcal }
        Row {
            Column(Modifier.weight(1f)) {
                Text("Запланировано", fontSize = 12.sp, color = extra.dim)
                Text("${planned.roundToInt()} ккал", style = MaterialTheme.typography.titleMedium)
                Text("Б ${active.protein().roundToInt()} · Ж ${active.sumOf { it.fat * it.servings }.roundToInt()} · У ${active.sumOf { it.carbs * it.servings }.roundToInt()} г", fontSize = 12.sp, color = extra.dim)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Фактически", fontSize = 12.sp, color = extra.dim)
                Text("$eaten ккал", style = MaterialTheme.typography.titleMedium)
                Text("цель $targetKcal · белок $targetProtein г", fontSize = 12.sp, color = extra.dim)
            }
        }
        Gap(6.dp)
        Bar((planned / targetKcal.coerceAtLeast(1)).toFloat(), MaterialTheme.colorScheme.primary)
        Gap(4.dp)
        Bar(eaten / targetKcal.coerceAtLeast(1).toFloat(), extra.ok)
    }
    MealType.order.forEach { meal ->
        val list = items.filter { it.meal == meal }
        if (list.isEmpty() && meal in listOf(MealType.BRUNCH, MealType.AFTERNOON)) {
            TextButton(onClick = { addFor = meal }) { Text("+ ${MealType.name(meal)}", color = extra.dim, fontSize = 13.sp) }
            return@forEach
        }
        SectionTitle("${MealType.name(meal)}  ${list.active().kcal().roundToInt()} ккал", action = "+ Блюдо") { addFor = meal }
        list.forEach { p ->
            val eaten = p.status == PlanStatus.EATEN
            val skipped = p.status == PlanStatus.SKIPPED
            Tile(Modifier.padding(bottom = 6.dp), padding = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = p.recipeId != null && book.byId(p.recipeId) != null) { nav.navigate(Routes.recipe(p.recipeId!!)) }) {
                        Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (skipped) extra.dim else MaterialTheme.colorScheme.onSurface)
                        Text(
                            "${Cooking.amount(p.servings)} порц. · ${(p.kcal * p.servings).roundToInt()} ккал · Б ${(p.protein * p.servings).roundToInt()} г" +
                                (if (p.repeatId != null) " · повтор" else "") + (p.timeMin?.let { " · ${Dates.time(it)}" } ?: ""),
                            fontSize = 12.sp, color = extra.dim,
                        )
                    }
                    when {
                        skipped -> TextButton(onClick = { io { Graph.extra.upsertPlan(p.copy(status = PlanStatus.PLANNED)) } }) { Text("Вернуть") }
                        eaten -> TextButton(onClick = { io { RecipeRepo.unmarkEaten(p) } }) { Text("✓ Съедено", color = extra.ok) }
                        else -> TextButton(onClick = { io { RecipeRepo.markEaten(p) } }) { Text("Съедено") }
                    }
                    Text("⋯", Modifier.clip(RoundedCornerShape(8.dp)).clickable { menuFor = p }.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 18.sp)
                }
            }
        }
    }
    Gap(10.dp)
    Row {
        OutlinedButton(onClick = { nav.navigate(Routes.menuCreate(day)) }, Modifier.weight(1f)) { Text("Заполнить") }
        HGap(8.dp)
        OutlinedButton(onClick = {
            scope.launch {
                val n = RecipeRepo.shoppingFromMenu(day, day)
                Toast.makeText(ctx, if (n > 0) "В списке покупок: $n позиций" else "В меню на этот день нет блюд из рецептов", Toast.LENGTH_SHORT).show()
                if (n > 0) nav.navigate(Routes.SHOPPING)
            }
        }, Modifier.weight(1f)) { Text("Покупки") }
    }
    TextButton(onClick = { savePreset = true }, enabled = active.isNotEmpty()) { Text("Сохранить день как шаблон") }

    addFor?.let { meal ->
        RecipePickerDialog(book, mealHint = meal, onDismiss = { addFor = null }) { r, s -> io { RecipeRepo.addToPlan(day, meal, r.id, s, MealType.defaultTime[meal]) } }
    }
    menuFor?.let { p -> PlanItemDialog(p) { menuFor = null } }
    if (savePreset) PresetNameDialog("Мой обычный день", onDismiss = { savePreset = false }) { name ->
        scope.launch { RecipeRepo.presetFromDays(name, day, 1); Toast.makeText(ctx, "Шаблон «$name» сохранён", Toast.LENGTH_SHORT).show() }
    }
}

@Composable
private fun PlanItemDialog(p: MealPlanItem, onDismiss: () -> Unit) {
    var meal by remember { mutableStateOf(p.meal) }
    var servings by remember { mutableStateOf(Cooking.amount(p.servings)) }
    var day by remember { mutableStateOf(p.day) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text("Перенести в приём пищи", fontSize = 13.sp, color = LocalExtra.current.dim)
                Gap(4.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealType.order.forEach { m -> Pill(MealType.name(m), m == meal) { meal = m } }
                }
                Gap(8.dp)
                FieldButton("День", "${Dates.weekdayShort(day)}, ${Dates.label(day)}", Modifier.fillMaxWidth()) { pickDate = true }
                Gap(8.dp)
                NumberField(servings, { servings = it }, "Порций")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = servings.num()?.takeIf { it > 0 } ?: p.servings
                io { Graph.extra.upsertPlan(p.copy(meal = meal, servings = s, day = day, repeatId = if (day != p.day) null else p.repeatId)) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { io { RecipeRepo.removeFromPlan(p) }; onDismiss() }) { Text("Убрать", color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (pickDate) DatePickDialog(day, { pickDate = false }, { it?.let { d -> day = d } }, allowClear = false)
}

@Composable
fun PresetNameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Название шаблона") },
        text = { TextInput(name, { name = it }, "Например: рабочая неделя") },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { onSave(name.trim()); onDismiss() } }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun WeekView(
    nav: NavHostController, day: Long, setDay: (Long) -> Unit, plan: List<MealPlanItem>, food: List<FoodEntry>,
    targetKcal: Int, openDay: (Long) -> Unit,
) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val start = Dates.weekStart(day)
    val days = (0 until 7).map { start + it }
    var savePreset by remember { mutableStateOf(false) }
    val weekItems = plan.filter { it.day in start..start + 6 }.active()
    DayNav("${Dates.short(start)} – ${Dates.short(start + 6)}", { setDay(day - 7) }, { setDay(day + 7) }, if (Dates.today() !in start..start + 6) ({ setDay(Dates.today()) }) else null)

    val plannedDays = days.filter { d -> weekItems.any { it.day == d } }
    val avgKcal = if (plannedDays.isEmpty()) 0.0 else weekItems.kcal() / plannedDays.size
    val avgProtein = if (plannedDays.isEmpty()) 0.0 else weekItems.protein() / plannedDays.size
    val eatenCount = weekItems.count { it.status == PlanStatus.EATEN }
    val factWeek = food.filter { it.day in start..start + 6 }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("${avgKcal.roundToInt()}", "ккал/день в плане", Modifier.weight(1f))
        Stat("${avgProtein.roundToInt()} г", "белка/день", Modifier.weight(1f))
        Stat("$eatenCount/${weekItems.size}", "съедено из плана", Modifier.weight(1f))
    }
    Gap(8.dp)
    Text(
        "Итого за неделю: план ${weekItems.kcal().roundToInt()} ккал · факт ${factWeek.sumOf { it.kcal }} ккал · цель ${targetKcal * 7}",
        fontSize = 12.sp, color = extra.dim,
    )
    Gap(8.dp)
    days.forEach { d ->
        val list = weekItems.filter { it.day == d }
        val fact = food.filter { it.day == d }.sumOf { it.kcal }
        Tile(Modifier.padding(bottom = 8.dp), onClick = { openDay(d) }, padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${Dates.weekdayShort(d)}, ${Dates.short(d)}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    color = if (d == Dates.today()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text("план ${list.kcal().roundToInt()} · факт $fact", fontSize = 12.sp, color = extra.dim)
            }
            if (list.isEmpty()) Text("Меню не составлено", fontSize = 13.sp, color = extra.dim)
            MealType.order.forEach { m ->
                val ms = list.filter { it.meal == m }
                if (ms.isNotEmpty()) Text(
                    "${MealType.name(m)}: " + ms.joinToString { it.title + if (it.status == PlanStatus.EATEN) " ✓" else "" },
                    fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Row {
        OutlinedButton(onClick = {
            scope.launch {
                val n = RecipeRepo.shoppingFromMenu(start, start + 6)
                Toast.makeText(ctx, if (n > 0) "Список покупок собран: $n позиций" else "На неделе нет блюд из рецептов", Toast.LENGTH_SHORT).show()
                if (n > 0) nav.navigate(Routes.SHOPPING)
            }
        }, Modifier.weight(1f)) { Text("Покупки на неделю") }
        HGap(8.dp)
        OutlinedButton(onClick = { savePreset = true }, Modifier.weight(1f), enabled = weekItems.isNotEmpty()) { Text("Как шаблон") }
    }
    if (savePreset) PresetNameDialog("Рабочая неделя", onDismiss = { savePreset = false }) { name ->
        scope.launch { RecipeRepo.presetFromDays(name, start, 7); Toast.makeText(ctx, "Шаблон недели «$name» сохранён", Toast.LENGTH_SHORT).show() }
    }
}

@Composable
private fun MonthView(day: Long, setDay: (Long) -> Unit, plan: List<MealPlanItem>, openDay: (Long) -> Unit) {
    val extra = LocalExtra.current
    val ld = Dates.day(day)
    val ym = YearMonth.of(ld.year, ld.monthValue)
    val range = Dates.monthRange(ym)
    val byDay = plan.active().groupBy { it.day }
    DayNav(Dates.monthTitle(ym), { setDay(ym.minusMonths(1).atDay(1).toEpochDay()) }, { setDay(ym.plusMonths(1).atDay(1).toEpochDay()) }, null)
    Row(Modifier.fillMaxWidth()) {
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = extra.dim) }
    }
    val first = range.first
    val offset = Dates.day(first).dayOfWeek.value - 1
    val cells = offset + ym.lengthOfMonth()
    val rows = (cells + 6) / 7
    for (row in 0 until rows) {
        Row(Modifier.fillMaxWidth()) {
            for (col in 0 until 7) {
                val idx = row * 7 + col - offset
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (idx in 0 until ym.lengthOfMonth()) {
                        val d = first + idx
                        val items = byDay[d].orEmpty()
                        val allEaten = items.isNotEmpty() && items.all { it.status == PlanStatus.EATEN }
                        Column(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (items.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else extra.card)
                                .clickable { openDay(d) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("${idx + 1}", fontSize = 13.sp, fontWeight = if (d == Dates.today()) FontWeight.Bold else FontWeight.Normal)
                            if (items.isNotEmpty()) Text(if (allEaten) "✓✓" else "✓ меню", fontSize = 9.sp, color = if (allEaten) extra.ok else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    Gap(8.dp)
    val planned = range.count { byDay.containsKey(it) }
    Text("Дней с меню: $planned из ${ym.lengthOfMonth()}. «✓✓» — всё из меню съедено.", fontSize = 12.sp, color = extra.dim)
}

// ---------- Создать меню ----------

private enum class Period(val label: String) { TODAY("Сегодня"), DAYS("Несколько дней"), WEEK("Неделя"), MONTH("Месяц"), CUSTOM("Произвольный") }

@Composable
fun CreateMenuScreen(nav: NavHostController, startDay: Long) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val target = rememberNutritionPlan()
    val presets by observe(emptyList()) { Graph.extra.presets() }
    val base = if (startDay > 0) startDay else Dates.today()
    var period by rememberSaveable { mutableStateOf(Period.TODAY) }
    var nDays by rememberSaveable { mutableStateOf(3) }
    var from by rememberSaveable { mutableStateOf(base) }
    var to by rememberSaveable { mutableStateOf(base + 6) }
    var mode by rememberSaveable { mutableStateOf(0) }
    var meals by rememberSaveable { mutableStateOf(setOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)) }
    var kcal by rememberSaveable { mutableStateOf(target.targetKcal.toString()) }
    var protein by rememberSaveable { mutableStateOf("") }
    var maxMin by rememberSaveable { mutableStateOf("") }
    var prefer by rememberSaveable { mutableStateOf("") }
    var exclude by rememberSaveable { mutableStateOf("") }
    var cats by rememberSaveable { mutableStateOf(setOf<String>()) }
    var replace by rememberSaveable { mutableStateOf(false) }
    var presetId by rememberSaveable { mutableStateOf(0L) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val range: LongRange = when (period) {
        Period.TODAY -> base..base
        Period.DAYS -> base until base + nDays
        Period.WEEK -> base..base + 6
        Period.MONTH -> base..base + 29
        Period.CUSTOM -> from..maxOf(from, to)
    }

    Screen("Создать меню", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Период")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.entries.forEach { p -> Pill(p.label, p == period) { period = p } }
            }
            Gap(8.dp)
            when (period) {
                Period.DAYS -> Row(verticalAlignment = Alignment.CenterVertically) { Text("Дней", Modifier.weight(1f)); Stepper(nDays) { nDays = it.coerceIn(2, 31) } }
                Period.CUSTOM -> Row {
                    FieldButton("С", Dates.label(from), Modifier.weight(1f)) { pickFrom = true }
                    HGap(8.dp)
                    FieldButton("По", Dates.label(to), Modifier.weight(1f)) { pickTo = true }
                }
                else -> {}
            }
            Text("${Dates.full(range.first)} — ${Dates.full(range.last)} · ${range.last - range.first + 1} дн.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))

            SectionTitle("Приёмы пищи")
            MealTypeChips(meals) { meals = it }

            SectionTitle("Как заполнить")
            Segments(listOf(0 to "Автоматически", 1 to "Из шаблона", 2 to "Вручную"), mode, { mode = it })
            Gap(10.dp)
            when (mode) {
                0 -> {
                    Row {
                        NumberField(kcal, { kcal = it }, "Калорий в день", Modifier.weight(1f), decimal = false)
                        HGap(8.dp)
                        NumberField(protein, { protein = it }, "Белка не меньше", Modifier.weight(1f), suffix = "г", decimal = false)
                    }
                    Gap(6.dp)
                    NumberField(maxMin, { maxMin = it }, "Время готовки до", suffix = "мин", decimal = false)
                    Gap(6.dp)
                    TextInput(prefer, { prefer = it }, "Предпочитаемые продукты (через запятую)")
                    Gap(6.dp)
                    TextInput(exclude, { exclude = it }, "Исключить продукты (через запятую)")
                    Gap(6.dp)
                    Text("Категории (пусто — все)", fontSize = 13.sp, color = extra.dim)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RECIPE_CATEGORIES.forEach { c -> Pill(c, c in cats) { cats = if (c in cats) cats - c else cats + c } }
                    }
                    Text(
                        "Цель по калориям взята из калькулятора в разделе «Здоровье». Подбор — это удобство, а не медицинская рекомендация.",
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp),
                    )
                }
                1 -> {
                    if (presets.isEmpty()) Text("Шаблонов пока нет. Составьте день или неделю и сохраните как шаблон.", color = extra.dim)
                    presets.forEach { p ->
                        Tile(Modifier.padding(bottom = 6.dp), onClick = { presetId = p.id }, color = if (presetId == p.id) MaterialTheme.colorScheme.primaryContainer else extra.card) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(if (p.days == 7) "Шаблон недели — блюда по дням недели" else "Шаблон дня — на каждый день периода", fontSize = 12.sp, color = extra.dim)
                        }
                    }
                    TextButton(onClick = { nav.navigate(Routes.PRESETS) }) { Text("Управлять шаблонами") }
                }
                else -> Text("Откроется меню на первый день периода — добавляйте блюда в нужные приёмы пищи кнопкой «+ Блюдо».", color = extra.dim)
            }
            if (mode != 2) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Заменить уже запланированное", Modifier.weight(1f))
                Switch(replace, { replace = it })
            }
            Gap(16.dp)
            Button(
                enabled = !busy && (mode != 1 || presetId != 0L) && (mode != 0 || meals.isNotEmpty()),
                onClick = {
                    busy = true
                    scope.launch {
                        val msg = when (mode) {
                            0 -> {
                                val n = RecipeRepo.autofill(
                                    range.first, range.last,
                                    Cooking.AutoParams(
                                        kcal = kcal.toIntOrNull() ?: target.targetKcal, meals = meals.toList(), minProtein = protein.toIntOrNull() ?: 0,
                                        maxMinutes = maxMin.toIntOrNull() ?: 0, exclude = split(exclude), prefer = split(prefer), categories = cats,
                                    ),
                                    replace,
                                )
                                if (n == 0) "Не нашлось подходящих блюд — ослабьте фильтры" else "Добавлено блюд: $n"
                            }
                            1 -> { RecipeRepo.applyPreset(presetId, range.first, range.last, replace); "Шаблон применён" }
                            else -> null
                        }
                        busy = false
                        msg?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
                        nav.popBackStack()
                        nav.navigate(Routes.recipes(1, if (range.last > range.first) 1 else 0))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (mode == 2) "Перейти к меню" else "Составить меню") }
            Gap(40.dp)
        }
    }
    if (pickFrom) DatePickDialog(from, { pickFrom = false }, { it?.let { d -> from = d; if (to < d) to = d } }, allowClear = false)
    if (pickTo) DatePickDialog(to, { pickTo = false }, { it?.let { d -> to = d } }, allowClear = false)
}

private fun split(s: String) = s.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
