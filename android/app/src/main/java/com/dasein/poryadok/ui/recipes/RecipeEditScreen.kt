@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.btnLabel
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
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
import com.dasein.poryadok.ui.common.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.dasein.poryadok.ui.common.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
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
import android.widget.Toast
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
import com.dasein.poryadok.ui.common.GlyphPickerDialog
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
    // Порция по умолчанию — обычная тарелка.
    var recipe by mutableStateOf(Recipe(name = "", custom = true, portionGrams = PLATE_GRAMS))
    /** «Я съел сейчас»: сколько порций записать в питание при сохранении. */
    var eaten by mutableStateOf("")
    var meal by mutableStateOf(MealType.LUNCH)
    val ingredients = mutableStateListOf<RecipeIngredient>()
    val steps = mutableStateListOf<String>()
    var step by mutableStateOf(0)
    /** Черновик новой карточки (вкладка «Черновики»); у правки существующего рецепта — null. */
    var draftId: String? = null
}

/** Обычная тарелка — около 300 г готового блюда. */
const val PLATE_GRAMS = 300.0
private val PLATE_SIZES = listOf("Полтарелки" to 150.0, "Тарелка" to 300.0, "Глубокая тарелка" to 350.0, "Большая" to 450.0)

private val STEP_TITLES = listOf("Основное", "Ингредиенты", "Шаги", "Параметры", "Сохранение")

@Composable
fun RecipeEditScreen(nav: NavHostController, id: Long, draft: String? = null) {
    val d: RecipeDraft = viewModel(key = "recipe-draft-$id-${draft.orEmpty()}")
    val scope = rememberCoroutineScope()
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    LaunchedEffect(id) {
        if (!d.loaded) {
            d.loaded = true
            if (id != 0L) {
                Graph.extra.recipeNow(id)?.let { d.recipe = it }
                d.ingredients.addAll(Graph.extra.ingredientsOfNow(id))
                d.steps.addAll(Graph.extra.stepsOfNow(id).map { it.text })
            }
            if (id == 0L) {
                d.draftId = draft ?: RecipeDrafts.newId()
                draft?.let { RecipeDrafts.get(it) }?.let { RecipeDrafts.load(d, it) }
            }
            if (d.steps.isEmpty()) d.steps.add("")
            RecipeAiQueue.kick(ctx)
        }
    }
    // Новая карточка сама сохраняется в черновики (не пока её заполняет ИИ — иначе затрётся результат).
    val drafts by RecipeDrafts.list
    val myDraft = d.draftId?.let { did -> drafts.firstOrNull { it.id == did } }
    val aiBusyHere = myDraft?.ai == 1 || myDraft?.ai == 2
    LaunchedEffect(d.recipe, d.ingredients.toList(), d.steps.toList(), aiBusyHere) {
        val did = d.draftId ?: return@LaunchedEffect
        if (aiBusyHere) return@LaunchedEffect
        kotlinx.coroutines.delay(1500)
        if (RecipeDrafts.hasContent(d.recipe, d.ingredients, d.steps)) com.dasein.poryadok.ui.common.io { RecipeDrafts.put(RecipeDrafts.from(d, did)) }
    }
    // ИИ дозаполнил эту карточку, пока она открыта, — показываем результат.
    var seenAi by remember { mutableStateOf(myDraft?.ai ?: 0) }
    LaunchedEffect(myDraft?.ai) {
        val st = myDraft?.ai ?: return@LaunchedEffect
        if (st == 3 && (seenAi == 1 || seenAi == 2)) { myDraft?.let { RecipeDrafts.load(d, it) }; d.step = 1 }
        seenAi = st
    }
    // Теги проставляются сами (ИИ или правила), когда карточка дошла до параметров или сохранения, а тегов нет.
    LaunchedEffect(d.step) {
        if (d.step >= 3 && d.recipe.tags.isBlank() && (d.recipe.name.isNotBlank() || d.ingredients.isNotEmpty()) && !aiBusyHere) {
            val got = aiTags(ctx, d)
            if (d.recipe.tags.isBlank() && got.isNotEmpty()) d.recipe = d.recipe.copy(tags = got.joinToString(", "))
        }
    }
    fun leave() {
        val did = d.draftId
        if (did != null && RecipeDrafts.hasContent(d.recipe, d.ingredients, d.steps)) {
            if (!aiBusyHere) com.dasein.poryadok.ui.common.io { RecipeDrafts.put(RecipeDrafts.from(d, did)) }
            Toast.makeText(ctx, "Карточка сохранена в «Черновиках»", Toast.LENGTH_SHORT).show()
        }
        nav.popBackStack()
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    val r = d.recipe
    val macros = RecipeRepo.macros(d.ingredients, r)

    fun save() {
        if (r.name.isBlank()) { error = "Введите название рецепта"; d.step = 0; return }
        scope.launch {
            val exact = RecipeRepo.exactServings(d.ingredients, r)
            val fixed = if (r.portionGrams > 0) r.copy(servings = Math.round(exact).toInt().coerceIn(1, 40)) else r
            val newId = RecipeRepo.saveRecipe(fixed.copy(name = r.name.trim()), d.ingredients.toList(), d.steps.toList())
            d.draftId?.let { did -> RecipeDrafts.delete(did); d.draftId = null }
            val ate = d.eaten.num() ?: 0.0
            if (ate > 0) {
                RecipeRepo.cook(newId, exact, ate, d.meal, addToFood = true)
                Toast.makeText(ctx, "Рецепт сохранён, ${Cooking.amount(ate)} порц. записано в питание", Toast.LENGTH_SHORT).show()
            }
            nav.popBackStack()
            if (id == 0L) nav.navigate(Routes.recipe(newId))
        }
    }

    androidx.activity.compose.BackHandler { if (id == 0L) leave() else confirmExit = true }
    Screen(
        title = if (id == 0L) "Новый рецепт" else "Изменить рецепт",
        onBack = { if (id == 0L) leave() else confirmExit = true },
        actions = { IconAction(Ic.check, "Сохранить") { save() } },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            if (aiBusyHere) com.dasein.poryadok.ui.common.Tile(Modifier.padding(bottom = 6.dp), padding = 10.dp) {
                Text(
                    (if (myDraft?.ai == 2) "✨ " + myDraft?.aiStage.orEmpty().ifBlank { "ИИ заполняет карточку…" } else "✨ Карточка в очереди ИИ…") +
                        " Можно выйти и создать другую — готовая будет в «Черновиках».",
                    fontSize = 13.sp,
                )
            }
            if (myDraft?.ai == 4) Text("ИИ не смог заполнить: ${myDraft?.aiError}", color = extra.danger, fontSize = 12.sp)
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
                HowTo("recipe_edit")
                Gap(24.dp)
            }
            Row(Modifier.padding(vertical = 10.dp)) {
                OutlinedButton(onClick = { if (d.step > 0) d.step-- else if (id == 0L) leave() else confirmExit = true }, Modifier.weight(1f)) { Text(if (d.step > 0) "Назад" else if (id == 0L) "В черновики" else "Отмена") }
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
    // PNG, чтобы прозрачный фон иконки не становился белым; фото без прозрачности RecipeIcons.finish пересохранит в JPEG.
    fun setPhoto(path: String) = com.dasein.poryadok.ui.common.io {
        val out = RecipeIcons.finish(path)
        RecipeIcons.addToFolder(out)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { d.recipe = d.recipe.copy(photo = out) }
    }
    val pickPhoto = com.dasein.poryadok.ui.common.rememberImagePickerWithCrop(1f, "recipes", png = true) { setPhoto(it) }
    var recrop by remember { mutableStateOf(false) }
    // Поделились рецептом из Instagram или ИИ придумал блюдо — сразу открываем разбор этого текста.
    var fromText by remember { mutableStateOf(RecipeInbox.take()?.let { t -> d.recipe = d.recipe.copy(notes = t); true } ?: false) }
    // Рецепт из подписи в Instagram: ингредиенты, порции и этапы разбираются сами.
    Button(onClick = { fromText = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (r.notes.isBlank()) "📋 Рецепт из видео, Instagram или по ссылке" else "📋 Описание блюда — разобрать заново")
    }
    if (fromText) RecipeTextDialog(d) { fromText = false }
    Gap(10.dp)
    TextInput(r.name, { d.recipe = r.copy(name = it) }, "Название")
    Gap(10.dp)
    var pickIllustration by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (r.photo.startsWith("dish/") || RecipeIcons.isIcon(r.photo)) { RecipeThumb(r, 72.dp); HGap(10.dp) }
        else {
            val img by rememberImage(r.photo.takeIf { it.isNotBlank() }, 400)
            img?.let { Image(it, null, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop); HGap(10.dp) }
        }
        Column {
            OutlinedButton(onClick = { pickPhoto() }) { Text(if (r.photo.isBlank() || r.photo.startsWith("dish/")) "Своё фото" else "Заменить фото") }
            if (r.photo.startsWith("/")) OutlinedButton(onClick = { recrop = true }) { Text(btnLabel("Кадр превью")) }
            OutlinedButton(onClick = { pickIllustration = true }) { Text(btnLabel("Иллюстрация")) }
        }
        if (r.photo.isNotBlank()) TextButton(onClick = { d.recipe = r.copy(photo = "") }) { Text(btnLabel("Убрать")) }
    }
    // Горизонтальная обложка сверху карточки блюда (необязательно).
    val pickCover = com.dasein.poryadok.ui.common.rememberImagePickerWithCrop(16f / 9f, "recipes", outW = 1600) { d.recipe = d.recipe.copy(cover = it) }
    Gap(8.dp)
    if (r.cover.isNotBlank()) {
        val cov by rememberImage(r.cover, 1200)
        cov?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop) }
        Row {
            OutlinedButton(onClick = { pickCover() }) { Text(btnLabel("Заменить обложку")) }
            TextButton(onClick = { d.recipe = r.copy(cover = "") }) { Text(btnLabel("Убрать обложку")) }
        }
    } else OutlinedButton(onClick = { pickCover() }) { Text(btnLabel("+ Обложка сверху карточки")) }
    if (recrop) com.dasein.poryadok.ui.common.CropDialog(com.dasein.poryadok.ui.common.Crop.sourceFor(r.photo), 1f, "recipes", png = true, onDismiss = { recrop = false }) {
        recrop = false; setPhoto(it)
    }
        if (pickIllustration) GlyphPickerDialog(r.photo, { pickIllustration = false }, dishes = true) { d.recipe = d.recipe.copy(photo = it) }
    RecipeVideosEditor(r.id, RecipeVideos.list(r), deleteFiles = false) { list -> d.recipe = d.recipe.copy(videos = RecipeVideos.join(list, RecipeVideos.source(d.recipe))) }
    RecipeSourceButton(r)
    SectionTitle("Категория")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RECIPE_CATEGORIES.forEach { c -> Pill(c, c == r.category) { d.recipe = r.copy(category = c) } }
    }
    SectionTitle("Приём пищи")
    MealTypeChips(MealType.parse(r.meals).toSet()) { set -> d.recipe = r.copy(meals = MealType.order.filter { it in set }.joinToString(",")) }
    SectionTitle("Теги")
    TextInput(r.tags, { d.recipe = r.copy(tags = it) }, "Через запятую: быстро, высокобелковые")
    var tagsBusy by remember { mutableStateOf(false) }
    com.dasein.poryadok.ui.common.TextButton(enabled = !tagsBusy, onClick = {
        tagsBusy = true
        scope.launch {
            val got = aiTags(ctx, d)
            d.recipe = d.recipe.copy(tags = (d.recipe.tagList() + got).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10).joinToString(", "))
            tagsBusy = false
        }
    }) { Text(if (tagsBusy) "Подбираю теги…" else "✨ Подобрать теги автоматически") }
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
        Button(onClick = { pickProduct = true }, Modifier.weight(1f)) { Text(btnLabel("+ Из базы")) }
        HGap(8.dp)
        OutlinedButton(onClick = { edit = -1 to RecipeIngredient(recipeId = 0, name = "", amount = 100.0, unit = "г", grams = 100.0) }, Modifier.weight(1f)) { Text(btnLabel("+ Свой")) }
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
                Text(btnLabel("✕"), color = extra.dim, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { d.ingredients.removeAt(i) }.padding(8.dp))
            }
        }
    }
    if (d.ingredients.isEmpty()) Text(btnLabel("Ингредиентов пока нет"), color = extra.dim, modifier = Modifier.padding(8.dp))
    else {
        val list = d.ingredients.map { it.toIngrSafe() }
        val r = d.recipe
        Tile(Modifier.padding(top = 6.dp)) {
            Text("Итого ${Cooking.amount(Cooking.rawGrams(list))} г", fontWeight = FontWeight.SemiBold)
            Text(if (r.portionGrams > 0) "На порцию ${Cooking.amount(r.portionGrams)} г:" else "На порцию (${r.servings} порц.):", fontSize = 13.sp, color = extra.dim)
            MacroLine(RecipeRepo.macros(d.ingredients, r))
            Text("Вес порции задаётся на шаге «Параметры» — там же КБЖУ на 100 г и на всё блюдо.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
        }
    }

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
                Text(btnLabel("КБЖУ на 100 г"), fontSize = 13.sp, color = LocalExtra.current.dim)
                Row {
                    NumberField(kc, { kc = it }, "Ккал", Modifier.weight(1f)); HGap(4.dp)
                    NumberField(p, { p = it }, "Б", Modifier.weight(1f))
                }
                Row {
                    NumberField(f, { f = it }, "Ж", Modifier.weight(1f)); HGap(4.dp)
                    NumberField(c, { c = it }, "У", Modifier.weight(1f))
                }
                Gap(6.dp)
                Text(btnLabel("Отдел в магазине"), fontSize = 13.sp, color = LocalExtra.current.dim)
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
            }) { Text(btnLabel("Готово")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
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
        title = { Text(btnLabel("База продуктов")) },
        text = {
            Column {
                TextInput(q, { q = it }, "Поиск продукта")
                TextButton(onClick = { creating = true }) { Text(btnLabel("+ Создать продукт") + if (q.isNotBlank()) " «$q»" else "") }
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) } },
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
        title = { Text(btnLabel("Новый продукт")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextInput(name, { name = it }, "Название")
                Text(btnLabel("На 100 г"), fontSize = 13.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 8.dp))
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
            }) { Text(btnLabel("Создать")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
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
                if (i > 0) Text(btnLabel("↑"), Modifier.clip(RoundedCornerShape(8.dp)).clickable { val t = d.steps[i]; d.steps[i] = d.steps[i - 1]; d.steps[i - 1] = t }.padding(8.dp))
                Text(btnLabel("✕"), Modifier.clip(RoundedCornerShape(8.dp)).clickable { d.steps.removeAt(i); if (d.steps.isEmpty()) d.steps.add("") }.padding(8.dp), color = extra.dim)
            }
        }
    }
    OutlinedButton(onClick = { d.steps.add("") }, Modifier.fillMaxWidth()) { Text(btnLabel("+ Добавить шаг")) }
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
    if (r.portionGrams <= 0) Row(verticalAlignment = Alignment.CenterVertically) {
        Text(btnLabel("Порций"), Modifier.weight(1f))
        Stepper(r.servings) { d.recipe = d.recipe.copy(servings = it.coerceIn(1, 40)) }
    } else Text(btnLabel("Порций: считаются по весу порции ниже"), color = LocalExtra.current.dim, fontSize = 13.sp)
    SectionTitle("Сложность")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DIFFICULTY.forEachIndexed { i, t -> Pill(t, r.difficulty == i + 1) { d.recipe = d.recipe.copy(difficulty = i + 1) } }
    }
    SectionTitle("Вес порции")
    val ingrs = d.ingredients.map { it.toIngrSafe() }
    val raw = Cooking.rawGrams(ingrs)
    var portion by remember { mutableStateOf(if (r.portionGrams > 0) r.portionGrams.plain() else "") }
    var dish by remember { mutableStateOf(if (r.dishGrams > 0) r.dishGrams.plain() else "") }
    fun apply(p: String, dsh: String) {
        val pg = p.num()?.takeIf { it > 0 } ?: 0.0
        val dg = dsh.num()?.takeIf { it > 0 } ?: 0.0
        val cur = d.recipe
        val exact = Cooking.exactServings(ingrs, cur.servings, pg, dg)
        // Число порций подстраивается под вес: так меню и список покупок считают так же.
        d.recipe = cur.copy(portionGrams = pg, dishGrams = dg, servings = if (pg > 0) Math.round(exact).toInt().coerceIn(1, 40) else cur.servings)
    }
    Tile {
        Text(
            "Порция — сколько помещается в обычную тарелку (≈300 г). КБЖУ порции, 100 г и всего блюда считаются по ингредиентам сами. " +
                "Если блюдо уварилось или ужарилось, взвесьте всё готовое блюдо и впишите вес.",
            fontSize = 13.sp, color = LocalExtra.current.dim,
        )
        Gap(8.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PLATE_SIZES.forEach { (name, g) ->
                Pill("$name ${Cooking.amount(g)} г", portion.num() == g) { portion = Cooking.amount(g); apply(portion, dish) }
            }
        }
        Gap(8.dp)
        Row {
            NumberField(portion, { portion = it; apply(it, dish) }, "Вес 1 порции", Modifier.weight(1f), suffix = "г")
            HGap(8.dp)
            NumberField(dish, { dish = it; apply(portion, it) }, "Всё блюдо (≈${Cooking.amount(raw)} г)", Modifier.weight(1f), suffix = "г")
        }
        val pg = r.portionGrams
        if (pg > 0) {
            val exact = Cooking.exactServings(ingrs, r.servings, pg, r.dishGrams)
            Text(
                "Выходит ${Cooking.amount(Math.round(exact * 10) / 10.0)} порц. по ${Cooking.amount(pg)} г",
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    SectionTitle("КБЖУ — считается автоматически")
    val total = Cooking.total(ingrs)
    Tile {
        Text(if (r.portionGrams > 0) "На порцию (${Cooking.amount(r.portionGrams)} г)" else "На порцию", fontSize = 13.sp, color = LocalExtra.current.dim)
        MacroLine(RecipeRepo.macros(d.ingredients, r), showFiber = true)
        Gap(8.dp)
        Text(btnLabel("На 100 г готового блюда"), fontSize = 13.sp, color = LocalExtra.current.dim)
        MacroLine(Cooking.per100(ingrs, r.dishGrams))
        Gap(8.dp)
        val dishW = if (r.dishGrams > 0) r.dishGrams else raw
        Text("На всё блюдо (${Cooking.amount(dishW)} г)", fontSize = 13.sp, color = LocalExtra.current.dim)
        MacroLine(total)
        if (d.ingredients.isEmpty()) Text("Добавьте ингредиенты на шаге «Ингредиенты» — без них КБЖУ будет нулевым.", fontSize = 12.sp, color = LocalExtra.current.warn, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun StepFinish(d: RecipeDraft) {
    val r = d.recipe
    val extra = LocalExtra.current
    TextInput(r.notes, { d.recipe = r.copy(notes = it) }, "Описание (ваши заметки и исходный текст)", singleLine = false, minLines = 2)
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
        MacroLine(RecipeRepo.macros(d.ingredients, r))
        if (d.ingredients.isEmpty()) Text(btnLabel("Без ингредиентов КБЖУ будет нулевым."), color = extra.warn, fontSize = 12.sp)
    }
    SectionTitle("Съели сейчас?")
    Tile {
        val one = RecipeRepo.macros(d.ingredients, r)
        Text(
            "Укажите, сколько порций съели, — при сохранении они запишутся в питание за сегодня. " +
                "Порция ${if (r.portionGrams > 0) "${Cooking.amount(r.portionGrams)} г" else "из рецепта"}: ${one.kcal.roundToInt()} ккал.",
            fontSize = 13.sp, color = extra.dim,
        )
        Gap(8.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("" to "Не записывать", "0.5" to "½", "1" to "1", "1.5" to "1½", "2" to "2").forEach { (v, t) ->
                Pill(t, d.eaten == v) { d.eaten = v }
            }
        }
        Gap(8.dp)
        NumberField(d.eaten, { d.eaten = it }, "Съедено порций", suffix = "порц.")
        d.eaten.num()?.takeIf { it > 0 }?.let { n ->
            val m = one * n
            Text(
                "Запишется: ${m.kcal.roundToInt()} ккал · Б ${m.protein.roundToInt()} · Ж ${m.fat.roundToInt()} · У ${m.carbs.roundToInt()}" +
                    if (r.portionGrams > 0) " (${Cooking.amount(r.portionGrams * n)} г)" else "",
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Gap(6.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MealType.order.forEach { mt -> Pill(MealType.name(mt), mt == d.meal) { d.meal = mt } }
            }
        }
    }
    Gap(6.dp)
    Text("После сохранения рецепт появится в разделе «Мои».", color = extra.dim, fontSize = 13.sp)
}
