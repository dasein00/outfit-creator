@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.FoodProduct
import com.dasein.poryadok.data.MealRepeat
import com.dasein.poryadok.data.RecipeRepo
import com.dasein.poryadok.data.toIngr
import com.dasein.poryadok.logic.Cooking
import com.dasein.poryadok.logic.DIFFICULTY
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.MealType
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.CheckDot
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.FullCenter
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.plain
import com.dasein.poryadok.ui.common.rememberImage
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import android.widget.Toast
import kotlin.math.roundToInt

@Composable
fun RecipeDetailScreen(nav: NavHostController, id: Long) {
    val x = Graph.extra
    val recipe by observe(null, id) { x.recipe(id) }
    val baseIngr by observe(emptyList(), id) { x.ingredientsOf(id) }
    val steps by observe(emptyList(), id) { x.stepsOf(id) }
    val products by observe(emptyList()) { x.products() }
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var perServing by rememberSaveable { mutableStateOf(true) }
    var servings by rememberSaveable(id) { mutableStateOf(0) }
    val done = remember(id) { mutableStateMapOf<Int, Boolean>() }
    val swaps = remember(id) { mutableStateMapOf<Int, FoodProduct>() }
    var swapFor by remember { mutableStateOf<Int?>(null) }
    var addToMenu by remember { mutableStateOf(false) }
    var cook by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val r = recipe
    if (r == null) {
        Screen("Рецепт", onBack = { nav.popBackStack() }) { FullCenter { Text("Рецепт не найден или удалён", color = extra.dim) } }
        return
    }
    val serv = if (servings == 0) r.servings else servings
    val ingr = baseIngr.mapIndexed { i, ing ->
        swaps[i]?.let { p -> ing.copy(productId = p.id, name = p.name, kcal100 = p.kcal, protein100 = p.protein, fat100 = p.fat, carbs100 = p.carbs, fiber100 = p.fiber, shopCategory = p.category) } ?: ing
    }
    val factor = serv / r.servings.toDouble()
    val scaled = Cooking.scale(ingr.map { it.toIngr() }, factor)
    val total = Cooking.total(scaled)
    val shown = if (perServing) total / serv.toDouble() else total

    Screen(
        title = r.name,
        onBack = { nav.popBackStack() },
        actions = {
            IconAction(Ic.heart, if (r.favorite) "Убрать из избранного" else "В избранное") { io { x.setFavorite(r.id, !r.favorite) } }
            IconAction(Ic.sliders, "Изменить") { nav.navigate(Routes.recipeEdit(r.id)) }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val img by rememberImage(r.photo.takeIf { it.isNotBlank() }, 1200)
            img?.let {
                Image(it, null, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                Gap(10.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (img == null) { RecipeThumb(r, 56.dp); HGap(12.dp) }
                Column(Modifier.weight(1f)) {
                    Text(r.category + if (r.custom) " · мой рецепт" else "", color = extra.dim, fontSize = 13.sp)
                    Text(
                        listOf(
                            "Подготовка ${minutes(r.prepMin)}", if (r.cookMin > 0) "готовка ${minutes(r.cookMin)}" else "без готовки",
                            DIFFICULTY.getOrElse(r.difficulty - 1) { "" }.lowercase(),
                        ).joinToString(" · "),
                        fontSize = 13.sp,
                    )
                    val meals = MealType.parse(r.meals)
                    if (meals.isNotEmpty()) Text("Подходит: " + meals.joinToString { MealType.name(it).lowercase() }, fontSize = 12.sp, color = extra.dim)
                }
            }
            if (r.tagList().isNotEmpty()) {
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.tagList().forEach { Text("#$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                }
            }

            Gap(12.dp)
            Tile {
                Segments(listOf(true to "На 1 порцию", false to "На весь рецепт"), perServing, { perServing = it })
                Gap(10.dp)
                MacroLine(shown, showFiber = true)
                Gap(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Порций", Modifier.weight(1f))
                    Stepper(serv) { servings = it.coerceIn(1, 40) }
                }
                if (swaps.isNotEmpty()) Text("С учётом замен ингредиентов", fontSize = 12.sp, color = extra.warn, modifier = Modifier.padding(top = 6.dp))
            }

            SectionTitle("Ингредиенты на $serv порц.")
            Tile(padding = 8.dp) {
                scaled.forEachIndexed { i, ing ->
                    val hasSwap = Cooking.SWAPS.containsKey(baseIngr.getOrNull(i)?.name) || swaps.containsKey(i)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ing.name, Modifier.weight(1f), color = if (swaps.containsKey(i)) extra.warn else MaterialTheme.colorScheme.onSurface)
                        Text(Cooking.label(ing), color = extra.dim, fontSize = 14.sp)
                        if (hasSwap) Text(
                            "замена", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { swapFor = i }.padding(4.dp),
                        )
                    }
                }
            }

            SectionTitle("Приготовление", action = if (done.isNotEmpty()) "Сбросить" else null) { done.clear() }
            steps.forEachIndexed { i, s ->
                Tile(Modifier.padding(bottom = 8.dp), onClick = { done[i] = !(done[i] ?: false) }) {
                    Row {
                        CheckDot(done[i] == true, MaterialTheme.colorScheme.primary, onClick = { done[i] = !(done[i] ?: false) }, size = 24.dp)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text("Шаг ${i + 1}", fontSize = 12.sp, color = extra.dim)
                            Text(s.text, textDecoration = if (done[i] == true) TextDecoration.LineThrough else null, color = if (done[i] == true) extra.dim else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (steps.isEmpty()) Text("Шаги не добавлены", color = extra.dim)

            if (r.notes.isNotBlank()) { SectionTitle("Заметки"); Text(r.notes) }
            if (r.tips.isNotBlank()) { SectionTitle("Советы"); Text(r.tips) }
            val swapList = baseIngr.mapNotNull { ing -> Cooking.SWAPS[ing.name]?.let { ing.name to it } }
            if (swapList.isNotEmpty() || r.swaps.isNotBlank()) {
                SectionTitle("Замены ингредиентов")
                swapList.forEach { (from, to) -> Text("$from → ${to.joinToString(", ")}", fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp)) }
                if (r.swaps.isNotBlank()) Text(r.swaps, fontSize = 14.sp)
            }
            SectionTitle("Пищевая ценность")
            Tile {
                val per100 = scaled.sumOf { it.grams }.takeIf { it > 0 }?.let { total * (100 / it) }
                Text("Порция (${Cooking.amount(scaled.sumOf { it.grams } / serv)} г)", fontSize = 13.sp, color = extra.dim)
                MacroLine(total / serv.toDouble(), showFiber = true)
                if (per100 != null) {
                    Gap(8.dp)
                    Text("На 100 г", fontSize = 13.sp, color = extra.dim)
                    MacroLine(per100, showFiber = true)
                }
                Text("Расчёт по базе продуктов, значения приблизительные.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
            }

            Gap(16.dp)
            Row {
                Button(onClick = { addToMenu = true }, Modifier.weight(1f)) { Text("В меню") }
                HGap(8.dp)
                Button(onClick = { cook = true }, Modifier.weight(1f)) { Text("Приготовить") }
            }
            Gap(8.dp)
            OutlinedButton(onClick = {
                scope.launch {
                    RecipeRepo.addToShopping(r.id, factor)
                    Toast.makeText(ctx, "Ингредиенты добавлены в список покупок", Toast.LENGTH_SHORT).show()
                }
            }, Modifier.fillMaxWidth()) { Text("Ингредиенты в список покупок") }
            Row {
                OutlinedButton(onClick = { io { x.setFavorite(r.id, !r.favorite) } }, Modifier.weight(1f)) { Text(if (r.favorite) "В избранном ✓" else "В избранное") }
                HGap(8.dp)
                OutlinedButton(onClick = { nav.navigate(Routes.recipeEdit(r.id)) }, Modifier.weight(1f)) { Text("Изменить") }
            }
            Row {
                TextButton(onClick = {
                    scope.launch { RecipeRepo.duplicate(r.id)?.let { nav.navigate(Routes.recipeEdit(it)) } }
                }) { Text(if (swaps.isEmpty()) "Сделать копию" else "Копия") }
                if (swaps.isNotEmpty()) TextButton(onClick = {
                    scope.launch {
                        val newId = RecipeRepo.saveRecipe(
                            r.copy(id = 0, name = r.name + " (с заменами)", custom = true, seedKey = null, favorite = false, createdAt = 0),
                            ingr, steps.map { it.text },
                        )
                        nav.navigate(Routes.recipe(newId))
                    }
                }) { Text("Сохранить с заменами") }
                if (r.custom) TextButton(onClick = { confirmDelete = true }) { Text("Удалить", color = extra.danger) }
            }
            Gap(40.dp)
        }
    }

    swapFor?.let { i ->
        val from = baseIngr.getOrNull(i)
        val options = Cooking.SWAPS[from?.name].orEmpty().mapNotNull { n -> products.firstOrNull { it.name == n } }
        AlertDialog(
            onDismissRequest = { swapFor = null },
            title = { Text("Заменить: ${from?.name}") },
            text = {
                Column {
                    Text("КБЖУ пересчитается. Рецепт не изменится, пока вы не сохраните копию.", fontSize = 13.sp, color = extra.dim)
                    Gap(8.dp)
                    options.forEach { p ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { swaps[i] = p; swapFor = null }.padding(10.dp)) {
                            Text(p.name, Modifier.weight(1f))
                            Text("${p.kcal.roundToInt()} ккал · Б ${p.protein.plain()}", fontSize = 12.sp, color = extra.dim)
                        }
                    }
                    if (options.isEmpty()) Text("Для этого продукта нет готовых замен.", color = extra.dim)
                }
            },
            confirmButton = { if (swaps.containsKey(i)) TextButton(onClick = { swaps.remove(i); swapFor = null }) { Text("Вернуть исходный") } },
            dismissButton = { TextButton(onClick = { swapFor = null }) { Text("Закрыть") } },
        )
    }
    if (addToMenu) AddToMenuDialog(r.id, r.meals, onDismiss = { addToMenu = false }) { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    if (cook) CookDialog(r.id, serv, r.meals, onDismiss = { cook = false }) { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    if (confirmDelete) ConfirmDialog("Удалить рецепт?", "Рецепт исчезнет из базы. Уже запланированные блюда останутся в меню.", onDismiss = { confirmDelete = false }) {
        scope.launch { RecipeRepo.deleteRecipe(r.id); nav.popBackStack() }
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onChange(value - 1) }, Modifier.width(48.dp), contentPadding = PaddingValues(0.dp)) { Text("−") }
        Text("$value", Modifier.width(40.dp), fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = { onChange(value + 1) }, Modifier.width(48.dp), contentPadding = PaddingValues(0.dp)) { Text("+") }
    }
}

private const val DAYS_ALL = 127

/** Добавить блюдо в меню: день, приём пищи, порции и при желании — повтор по дням недели. */
@Composable
fun AddToMenuDialog(recipeId: Long, mealsCsv: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val suggested = MealType.parse(mealsCsv).firstOrNull() ?: MealType.LUNCH
    var day by remember { mutableStateOf(Dates.today()) }
    var meal by remember { mutableStateOf(suggested) }
    var servings by remember { mutableStateOf("1") }
    var repeat by remember { mutableStateOf(false) }
    var mask by remember { mutableStateOf(DAYS_ALL) }
    var pickDate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в меню") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FieldButton(if (repeat) "Начиная с" else "День", "${Dates.weekdayShort(day)}, ${Dates.label(day)}", Modifier.fillMaxWidth()) { pickDate = true }
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealType.order.forEach { m -> Pill(MealType.name(m), m == meal) { meal = m } }
                }
                Gap(8.dp)
                NumberField(servings, { servings = it }, "Порций")
                Gap(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Повторять по дням недели", Modifier.weight(1f))
                    Switch(repeat, { repeat = it })
                }
                if (repeat) WeekdayMask(mask) { mask = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = servings.num()?.takeIf { it > 0 } ?: 1.0
                scope.launch {
                    if (repeat) {
                        RecipeRepo.saveRepeat(MealRepeat(recipeId = recipeId, meal = meal, daysMask = mask, fromDay = day, servings = s))
                        onDone("Блюдо будет в меню по выбранным дням")
                    } else {
                        RecipeRepo.addToPlan(day, meal, recipeId, s)
                        onDone("Добавлено: ${MealType.name(meal).lowercase()}, ${Dates.label(day)}")
                    }
                    onDismiss()
                }
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    if (pickDate) DatePickDialog(day, { pickDate = false }, { it?.let { d -> day = d } }, allowClear = false)
}

private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
fun WeekdayMask(mask: Int, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAYS.forEachIndexed { i, d ->
            val on = mask and (1 shl i) != 0
            Pill(d, on) { onChange(mask xor (1 shl i)) }
        }
    }
}

fun maskLabel(mask: Int): String = when (mask) {
    127 -> "каждый день"
    31 -> "по будням"
    96 -> "по выходным"
    else -> WEEKDAYS.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(", ")
}

/** «Приготовить»: запись в историю и по желанию — в дневник питания. */
@Composable
fun CookDialog(recipeId: Long, servings: Int, mealsCsv: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var cooked by remember { mutableStateOf(servings.toString()) }
    var eaten by remember { mutableStateOf("1") }
    var meal by remember { mutableStateOf(MealType.parse(mealsCsv).firstOrNull() ?: MealType.LUNCH) }
    var toFood by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Приготовлено") },
        text = {
            Column {
                Row {
                    NumberField(cooked, { cooked = it }, "Приготовлено порций", Modifier.weight(1f))
                    HGap(8.dp)
                    NumberField(eaten, { eaten = it }, "Съедено порций", Modifier.weight(1f))
                }
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealType.order.forEach { m -> Pill(MealType.name(m), m == meal) { meal = m } }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Добавить в питание за сегодня", Modifier.weight(1f))
                    Switch(toFood, { toFood = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    RecipeRepo.cook(recipeId, cooked.num() ?: servings.toDouble(), eaten.num() ?: 0.0, meal, toFood)
                    onDone(if (toFood) "Записано в историю и в питание" else "Записано в историю готовки")
                    onDismiss()
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
