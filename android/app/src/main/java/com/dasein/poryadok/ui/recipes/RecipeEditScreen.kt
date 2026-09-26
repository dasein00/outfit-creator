@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.FoodProduct
import com.dasein.poryadok.data.Recipe
import com.dasein.poryadok.data.RecipeIngredient
import com.dasein.poryadok.data.RecipeRepo
import com.dasein.poryadok.data.toIngr
import com.dasein.poryadok.logic.Cooking
import com.dasein.poryadok.logic.DIFFICULTY
import com.dasein.poryadok.logic.MealType
import com.dasein.poryadok.logic.RECIPE_CATEGORIES
import com.dasein.poryadok.logic.SHOP_CATEGORIES
import com.dasein.poryadok.logic.UNITS
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Images
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.plain
import com.dasein.poryadok.ui.common.rememberImage
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Черновик рецепта переживает поворот экрана. */
class RecipeDraft : ViewModel() {
    var loaded = false
    var recipe by mutableStateOf(Recipe(name = "", custom = true))
    val ingredients = mutableStateListOf<RecipeIngredient>()
    val steps = mutableStateListOf<String>()
    var step by mutableStateOf(0)
}

private val STEP_TITLES = listOf("Основное", "Ингредиенты", "Шаги", "Параметры", "Сохранение")

@Composable
fun RecipeEditScreen(nav: NavHostController, id: Long) {
    val d: RecipeDraft = viewModel(key = "recipe-draft-$id")
    val scope = rememberCoroutineScope()
    val extra = LocalExtra.current
    LaunchedEffect(id) {
        if (!d.loaded) {
            d.loaded = true
            if (id != 0L) {
                Graph.extra.recipeNow(id)?.let { d.recipe = it }
                d.ingredients.addAll(Graph.extra.ingredientsOfNow(id))
                d.steps.addAll(Graph.extra.stepsOfNow(id).map { it.text })
            }
            if (d.steps.isEmpty()) d.steps.add("")
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    val r = d.recipe
    val macros = RecipeRepo.macros(d.ingredients, r.servings)

    fun save() {
        if (r.name.isBlank()) { error = "Введите название рецепта"; d.step = 0; return }
        scope.launch {
            val newId = RecipeRepo.saveRecipe(r.copy(name = r.name.trim()), d.ingredients.toList(), d.steps.toList())
            nav.popBackStack()
            if (id == 0L) nav.navigate(Routes.recipe(newId))
        }
    }

    Screen(
        title = if (id == 0L) "Новый рецепт" else "Изменить рецепт",
        onBack = { confirmExit = true },
        actions = { IconAction(Ic.check, "Сохранить") { save() } },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${d.step + 1}/5 · ${STEP_TITLES[d.step]}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${macros.kcal.roundToInt()} ккал/порц.", color = extra.dim, fontSize = 13.sp)
            }
            Gap(6.dp)
            LinearProgressIndicator(progress = { (d.step + 1) / 5f }, modifier = Modifier.fillMaxWidth())
            Gap(6.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                STEP_TITLES.forEachIndexed { i, t -> Pill(t, i == d.step) { d.step = i } }
            }
            error?.let { Text(it, color = extra.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Gap(8.dp)
                when (d.step) {
                    0 -> StepBasics(d)
                    1 -> StepIngredients(d)
                    2 -> StepSteps(d)
                    3 -> StepParams(d)
                    else -> StepFinish(d)
                }
                Gap(24.dp)
            }
            Row(Modifier.padding(vertical = 10.dp)) {
                OutlinedButton(onClick = { if (d.step > 0) d.step-- else confirmExit = true }, Modifier.weight(1f)) { Text(if (d.step > 0) "Назад" else "Отмена") }
                HGap(8.dp)
                Button(onClick = { if (d.step < 4) d.step++ else save() }, Modifier.weight(1f)) { Text(if (d.step < 4) "Далее" else "Сохранить") }
            }
        }
    }
    if (confirmExit) ConfirmDialog("Выйти без сохранения?", "Изменения в рецепте будут потеряны.", confirm = "Выйти", onDismiss = { confirmExit = false }) {
        nav.popBackStack()
    }
}

@Composable
private fun StepBasics(d: RecipeDraft) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val r = d.recipe
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch { Images.importUri(ctx, uri, "recipes")?.let { d.recipe = d.recipe.copy(photo = it) } }
    }
    TextInput(r.name, { d.recipe = r.copy(name = it) }, "Название")
    Gap(10.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val img by rememberImage(r.photo.takeIf { it.isNotBlank() }, 400)
        img?.let { Image(it, null, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop); HGap(10.dp) }
        OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (r.photo.isBlank()) "Добавить фото" else "Заменить фото") }
        if (r.photo.isNotBlank()) TextButton(onClick = { d.recipe = r.copy(photo = "") }) { Text("Убрать") }
    }
    SectionTitle("Категория")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RECIPE_CATEGORIES.forEach { c -> Pill(c, c == r.category) { d.recipe = r.copy(category = c) } }
    }
    SectionTitle("Приём пищи")
    MealTypeChips(MealType.parse(r.meals).toSet()) { set -> d.recipe = r.copy(meals = MealType.order.filter { it in set }.joinToString(",")) }
    SectionTitle("Теги")
    TextInput(r.tags, { d.recipe = r.copy(tags = it) }, "Через запятую: быстро, высокобелковые")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        listOf("быстро", "высокобелковые", "низкоуглеводные", "вегетарианские", "заготовка", "без сахара", "постное").forEach { t ->
            val on = t in r.tagList()
            Pill(t, on) {
                val list = r.tagList().toMutableList()
                if (on) list.remove(t) else list.add(t)
                d.recipe = r.copy(tags = list.joinToString(", "))
            }
        }
    }
}

@Composable
private fun StepIngredients(d: RecipeDraft) {
    val extra = LocalExtra.current
    var pickProduct by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Pair<Int, RecipeIngredient>?>(null) }
    Text("Выберите продукты из базы или добавьте свой — КБЖУ посчитается автоматически.", color = extra.dim, fontSize = 13.sp)
    Gap(8.dp)
    Row {
        Button(onClick = { pickProduct = true }, Modifier.weight(1f)) { Text("+ Из базы") }
        HGap(8.dp)
        OutlinedButton(onClick = { edit = -1 to RecipeIngredient(recipeId = 0, name = "", amount = 100.0, unit = "г", grams = 100.0) }, Modifier.weight(1f)) { Text("+ Свой") }
    }
    Gap(8.dp)
    d.ingredients.forEachIndexed { i, ing ->
        Tile(Modifier.padding(bottom = 6.dp), onClick = { edit = i to ing }, padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ing.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val m = Cooking.macrosOf(ing.toIngrSafe())
                    Text("${Cooking.label(ing.toIngrSafe())} · ${m.kcal.roundToInt()} ккал · Б ${m.protein.roundToInt()} г", fontSize = 12.sp, color = extra.dim)
                }
                Text("✕", color = extra.dim, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { d.ingredients.removeAt(i) }.padding(8.dp))
            }
        }
    }
    if (d.ingredients.isEmpty()) Text("Ингредиентов пока нет", color = extra.dim, modifier = Modifier.padding(8.dp))

    if (pickProduct) ProductPickerDialog(onDismiss = { pickProduct = false }) { p ->
        pickProduct = false
        val unit = if (p.gramsPerUnit > 0) "шт" else "г"
        val amount = if (unit == "шт") 1.0 else 100.0
        edit = -1 to RecipeIngredient(
            recipeId = 0, productId = p.id, name = p.name, amount = amount, unit = unit, grams = Cooking.grams(amount, unit, p.gramsPerUnit),
            kcal100 = p.kcal, protein100 = p.protein, fat100 = p.fat, carbs100 = p.carbs, fiber100 = p.fiber, shopCategory = p.category,
        )
    }
    edit?.let { (i, ing) ->
        IngredientDialog(ing, onDismiss = { edit = null }) { res ->
            if (i >= 0) d.ingredients[i] = res else d.ingredients.add(res)
            edit = null
        }
    }
}

private fun RecipeIngredient.toIngrSafe() = toIngr()

@Composable
private fun IngredientDialog(ing: RecipeIngredient, onDismiss: () -> Unit, onSave: (RecipeIngredient) -> Unit) {
    val gpu0 = if (ing.unit == "шт" && ing.amount > 0) ing.grams / ing.amount else 0.0
    var name by remember { mutableStateOf(ing.name) }
    var amount by remember { mutableStateOf(ing.amount.plain()) }
    var unit by remember { mutableStateOf(ing.unit) }
    var gpu by remember { mutableStateOf(if (gpu0 > 0) gpu0.plain() else "100") }
    var gramsManual by remember { mutableStateOf("") }
    var kc by remember { mutableStateOf(ing.kcal100.plain()) }
    var p by remember { mutableStateOf(ing.protein100.plain()) }
    var f by remember { mutableStateOf(ing.fat100.plain()) }
    var c by remember { mutableStateOf(ing.carbs100.plain()) }
    var shop by remember { mutableStateOf(ing.shopCategory) }
    val a = amount.num() ?: 0.0
    val autoGrams = Cooking.grams(a, unit, gpu.num() ?: 0.0)
    val grams = gramsManual.num() ?: autoGrams
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ing.name.isBlank()) "Ингредиент" else ing.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextInput(name, { name = it }, "Название")
                Gap(6.dp)
                NumberField(amount, { amount = it }, "Количество")
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    UNITS.forEach { u -> Pill(u, u == unit) { unit = u } }
                }
                if (unit == "шт") { Gap(6.dp); NumberField(gpu, { gpu = it }, "Вес одной штуки", suffix = "г") }
                Gap(6.dp)
                NumberField(gramsManual, { gramsManual = it }, "Вес для расчёта: ${Cooking.amount(autoGrams)} г", suffix = "г")
                Gap(10.dp)
                Text("КБЖУ на 100 г", fontSize = 13.sp, color = LocalExtra.current.dim)
                Row {
                    NumberField(kc, { kc = it }, "Ккал", Modifier.weight(1f)); HGap(4.dp)
                    NumberField(p, { p = it }, "Б", Modifier.weight(1f))
                }
                Row {
                    NumberField(f, { f = it }, "Ж", Modifier.weight(1f)); HGap(4.dp)
                    NumberField(c, { c = it }, "У", Modifier.weight(1f))
                }
                Gap(6.dp)
                Text("Отдел в магазине", fontSize = 13.sp, color = LocalExtra.current.dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SHOP_CATEGORIES.forEach { s -> Pill(s, s == shop) { shop = s } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                onSave(
                    ing.copy(
                        name = name.trim(), amount = a, unit = unit, grams = if (unit == "по вкусу") 0.0 else grams,
                        kcal100 = kc.num() ?: 0.0, protein100 = p.num() ?: 0.0, fat100 = f.num() ?: 0.0, carbs100 = c.num() ?: 0.0, shopCategory = shop,
                    )
                )
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** База продуктов с поиском; можно создать свой продукт. */
@Composable
fun ProductPickerDialog(onDismiss: () -> Unit, onPick: (FoodProduct) -> Unit) {
    val products by observe(emptyList()) { Graph.extra.products() }
    var q by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val extra = LocalExtra.current
    val list = products.filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) || it.category.contains(q.trim(), true) }
    if (creating) {
        NewProductDialog(q, onDismiss = { creating = false }) { onPick(it) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("База продуктов") },
        text = {
            Column {
                TextInput(q, { q = it }, "Поиск продукта")
                TextButton(onClick = { creating = true }) { Text("+ Создать продукт" + if (q.isNotBlank()) " «$q»" else "") }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.id }) { p ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(p) }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name + if (p.custom) " · мой" else "")
                                Text("${p.category} · на 100 г: ${p.kcal.roundToInt()} ккал, Б ${p.protein.plain()}, Ж ${p.fat.plain()}, У ${p.carbs.plain()}", fontSize = 11.sp, color = extra.dim)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun NewProductDialog(initialName: String, onDismiss: () -> Unit, onCreated: (FoodProduct) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var kc by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var gpu by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Другое") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый продукт") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextInput(name, { name = it }, "Название")
                Text("На 100 г", fontSize = 13.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 8.dp))
                Row { NumberField(kc, { kc = it }, "Ккал", Modifier.weight(1f)); HGap(4.dp); NumberField(p, { p = it }, "Белки", Modifier.weight(1f)) }
                Row { NumberField(f, { f = it }, "Жиры", Modifier.weight(1f)); HGap(4.dp); NumberField(c, { c = it }, "Углеводы", Modifier.weight(1f)) }
                NumberField(gpu, { gpu = it }, "Вес 1 шт (если считаете штуками)", suffix = "г")
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SHOP_CATEGORIES.forEach { s -> Pill(s, s == cat) { cat = s } }
                }
                if (kc.isBlank() && (p.num() != null || f.num() != null || c.num() != null)) {
                    val est = ((p.num() ?: 0.0) * 4 + (f.num() ?: 0.0) * 9 + (c.num() ?: 0.0) * 4).roundToInt()
                    TextButton(onClick = { kc = est.toString() }) { Text("Посчитать калории по БЖУ: $est") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                scope.launch {
                    val prod = FoodProduct(
                        name = name.trim(), category = cat, kcal = kc.num() ?: 0.0, protein = p.num() ?: 0.0, fat = f.num() ?: 0.0,
                        carbs = c.num() ?: 0.0, gramsPerUnit = gpu.num() ?: 0.0, custom = true,
                    )
                    val id = Graph.extra.upsertProduct(prod)
                    onCreated(prod.copy(id = id))
                }
            }) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun StepSteps(d: RecipeDraft) {
    val extra = LocalExtra.current
    Text("Каждый шаг — отдельный блок. При готовке их можно отмечать.", color = extra.dim, fontSize = 13.sp)
    Gap(8.dp)
    d.steps.forEachIndexed { i, s ->
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("${i + 1}.", Modifier.padding(top = 18.dp, end = 6.dp), color = extra.dim)
            OutlinedTextField(
                s, { d.steps[i] = it }, Modifier.weight(1f), minLines = 2, shape = RoundedCornerShape(14.dp),
                label = { Text("Шаг ${i + 1}") },
            )
            Column {
                if (i > 0) Text("↑", Modifier.clip(RoundedCornerShape(8.dp)).clickable { val t = d.steps[i]; d.steps[i] = d.steps[i - 1]; d.steps[i - 1] = t }.padding(8.dp))
                Text("✕", Modifier.clip(RoundedCornerShape(8.dp)).clickable { d.steps.removeAt(i); if (d.steps.isEmpty()) d.steps.add("") }.padding(8.dp), color = extra.dim)
            }
        }
    }
    OutlinedButton(onClick = { d.steps.add("") }, Modifier.fillMaxWidth()) { Text("+ Добавить шаг") }
}

@Composable
private fun StepParams(d: RecipeDraft) {
    val r = d.recipe
    var prep by remember { mutableStateOf(if (r.prepMin > 0) r.prepMin.toString() else "") }
    var cook by remember { mutableStateOf(if (r.cookMin > 0) r.cookMin.toString() else "") }
    Row {
        NumberField(prep, { prep = it; d.recipe = d.recipe.copy(prepMin = it.toIntOrNull() ?: 0) }, "Подготовка", Modifier.weight(1f), suffix = "мин", decimal = false)
        HGap(8.dp)
        NumberField(cook, { cook = it; d.recipe = d.recipe.copy(cookMin = it.toIntOrNull() ?: 0) }, "Готовка", Modifier.weight(1f), suffix = "мин", decimal = false)
    }
    Gap(10.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Порций", Modifier.weight(1f))
        Stepper(r.servings) { d.recipe = d.recipe.copy(servings = it.coerceIn(1, 40)) }
    }
    SectionTitle("Сложность")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DIFFICULTY.forEachIndexed { i, t -> Pill(t, r.difficulty == i + 1) { d.recipe = d.recipe.copy(difficulty = i + 1) } }
    }
    SectionTitle("КБЖУ — считается автоматически")
    val total = Cooking.total(d.ingredients.map { it.toIngrSafe() })
    Tile {
        Text("На порцию", fontSize = 13.sp, color = LocalExtra.current.dim)
        MacroLine(total / r.servings.toDouble(), showFiber = true)
        Gap(8.dp)
        Text("На весь рецепт (${r.servings} порц.)", fontSize = 13.sp, color = LocalExtra.current.dim)
        MacroLine(total)
    }
}

@Composable
private fun StepFinish(d: RecipeDraft) {
    val r = d.recipe
    val extra = LocalExtra.current
    TextInput(r.notes, { d.recipe = r.copy(notes = it) }, "Заметки", singleLine = false, minLines = 2)
    Gap(8.dp)
    TextInput(r.tips, { d.recipe = r.copy(tips = it) }, "Советы", singleLine = false, minLines = 2)
    Gap(8.dp)
    TextInput(r.swaps, { d.recipe = r.copy(swaps = it) }, "Замены ингредиентов (свои)", singleLine = false, minLines = 2)
    SectionTitle("Проверка")
    Tile {
        Text(r.name.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium)
        Text("${r.category} · ${r.servings} порц. · ${minutes(r.prepMin + r.cookMin)}", color = extra.dim, fontSize = 13.sp)
        Text("${d.ingredients.size} ингредиентов · ${d.steps.count { it.isNotBlank() }} шагов", color = extra.dim, fontSize = 13.sp)
        Gap(6.dp)
        MacroLine(RecipeRepo.macros(d.ingredients, r.servings))
        if (d.ingredients.isEmpty()) Text("Без ингредиентов КБЖУ будет нулевым.", color = extra.warn, fontSize = 12.sp)
    }
    Gap(6.dp)
    Text("После сохранения рецепт появится в разделе «Мои».", color = extra.dim, fontSize = 13.sp)
}
