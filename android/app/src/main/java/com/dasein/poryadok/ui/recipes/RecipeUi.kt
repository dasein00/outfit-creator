@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BodyProfile
import com.dasein.poryadok.data.Recipe
import com.dasein.poryadok.data.RecipeIngredient
import com.dasein.poryadok.data.RecipeRepo
import com.dasein.poryadok.logic.Cooking
import com.dasein.poryadok.logic.Macros
import com.dasein.poryadok.logic.MealType
import com.dasein.poryadok.logic.Nutrition
import com.dasein.poryadok.logic.NutritionPlan
import com.dasein.poryadok.ui.common.AppIcon
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.rememberImage
import com.dasein.poryadok.ui.health.input
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.math.roundToInt

/** Все рецепты с ингредиентами и КБЖУ на порцию — одним объектом для экранов. */
data class RecipeBook(
    val loaded: Boolean,
    val recipes: List<Recipe>,
    val ingredients: Map<Long, List<RecipeIngredient>>,
    val perServing: Map<Long, Macros>,
) {
    fun macros(id: Long?): Macros = perServing[id] ?: Macros()
    fun byId(id: Long?): Recipe? = recipes.firstOrNull { it.id == id }
}

@Composable
fun rememberRecipeBook(): RecipeBook {
    val recipes by observe<List<Recipe>?>(null) { Graph.extra.recipes() }
    val ingredients by observe(emptyList()) { Graph.extra.ingredients() }
    return remember(recipes, ingredients) {
        val grouped = ingredients.groupBy { it.recipeId }
        val list = recipes.orEmpty()
        RecipeBook(recipes != null, list, grouped, list.associate { it.id to RecipeRepo.macros(grouped[it.id].orEmpty(), it.servings) })
    }
}

/** Цель по калориям из калькулятора в разделе «Здоровье». */
@Composable
fun rememberNutritionPlan(): NutritionPlan {
    val profile by observe(null) { Graph.dao.profile() }
    val weights by observe(emptyList()) { Graph.dao.weights() }
    val p = profile ?: BodyProfile()
    return remember(p, weights) { Nutrition.plan(p.input(weights.lastOrNull()?.kg ?: p.startWeight)) }
}

fun categoryIcon(cat: String): Int = when (cat) {
    "Завтраки" -> Ic.sun
    "Обеды" -> Ic.salad
    "Ужины" -> Ic.moon
    "Перекусы" -> Ic.leaves
    "Супы" -> Ic.flame
    "Салаты" -> Ic.leaves
    "Гарниры" -> Ic.grid
    "Десерты" -> Ic.heart
    "Напитки" -> Ic.drop
    "Выпечка" -> Ic.gift
    "Заготовки" -> Ic.folder
    else -> Ic.salad
}

fun kcal(v: Double) = "${v.roundToInt()} ккал"
fun g(v: Double) = "${Cooking.amount(v)} г"
fun minutes(m: Int) = if (m >= 60) "${m / 60} ч ${if (m % 60 > 0) "${m % 60} мин" else ""}".trim() else "$m мин"
fun Recipe.totalMin() = prepMin + cookMin
fun Recipe.tagList() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

@Composable
fun RecipeThumb(r: Recipe, size: Dp) {
    val img by rememberImage(r.photo.takeIf { it.isNotBlank() }, 400)
    val bmp = img
    if (bmp != null) {
        Image(bmp, null, Modifier.size(size).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(LocalExtra.current.cardHigh),
            contentAlignment = Alignment.Center,
        ) { AppIcon(categoryIcon(r.category), size * .5f, badge = false) }
    }
}

@Composable
fun RecipeCard(r: Recipe, m: Macros, onClick: () -> Unit, onFavorite: () -> Unit) {
    val extra = LocalExtra.current
    Tile(Modifier.padding(bottom = 8.dp), onClick = onClick, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecipeThumb(r, 64.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${minutes(r.totalMin())} · ${r.servings} порц. · ${kcal(m.kcal)}",
                    fontSize = 12.sp, color = extra.dim, maxLines = 1,
                )
                Text(
                    "Б ${m.protein.roundToInt()} · Ж ${m.fat.roundToInt()} · У ${m.carbs.roundToInt()} г на порцию",
                    fontSize = 12.sp, color = extra.dim, maxLines = 1,
                )
                val tags = r.tagList().take(3)
                if (tags.isNotEmpty()) Text(tags.joinToString("  ·  "), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onFavorite).padding(6.dp)) {
                AppIcon(Ic.heart, 22.dp, badge = false, dimmed = !r.favorite)
            }
        }
    }
}

/** Фильтры, которые можно комбинировать с поиском и категориями. */
enum class SmartFilter(val label: String) {
    KCAL500("до 500 ккал"),
    PROTEIN30("от 30 г белка"),
    FAST20("до 20 мин"),
    LOWCAL("низкокалорийные"),
    HIGHPROTEIN("высокобелковые"),
    NO_COOK("без готовки"),
}

fun matches(f: SmartFilter, r: Recipe, m: Macros): Boolean = when (f) {
    SmartFilter.KCAL500 -> m.kcal <= 500
    SmartFilter.PROTEIN30 -> m.protein >= 30
    SmartFilter.FAST20 -> r.totalMin() <= 20
    SmartFilter.LOWCAL -> m.kcal <= 300
    SmartFilter.HIGHPROTEIN -> m.protein >= 25 || "высокобелков" in r.tags.lowercase()
    SmartFilter.NO_COOK -> r.cookMin == 0
}

fun searchMatch(q: String, r: Recipe, ing: List<RecipeIngredient>): Boolean {
    if (q.isBlank()) return true
    val words = q.lowercase().replace('ё', 'е').split(' ').filter { it.isNotBlank() }
    val hay = buildString {
        append(r.name); append(' '); append(r.category); append(' '); append(r.tags); append(' ')
        MealType.parse(r.meals).forEach { append(MealType.name(it)); append(' ') }
        ing.forEach { append(it.name); append(' ') }
    }.lowercase().replace('ё', 'е')
    return words.all { it in hay }
}

/** Выбор рецепта из базы с поиском — для меню, шаблонов и повторов. */
@Composable
fun RecipePickerDialog(book: RecipeBook, title: String = "Выберите блюдо", mealHint: Int? = null, onDismiss: () -> Unit, onPick: (Recipe, Double) -> Unit) {
    var q by remember { mutableStateOf("") }
    var onlyMeal by remember { mutableStateOf(mealHint != null) }
    var servings by remember { mutableStateOf("1") }
    val extra = LocalExtra.current
    val list = book.recipes.filter { r ->
        searchMatch(q, r, book.ingredients[r.id].orEmpty()) && (!onlyMeal || mealHint == null || mealHint in MealType.parse(r.meals))
    }.sortedWith(compareByDescending<Recipe> { it.favorite }.thenBy { it.name })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextInput(q, { q = it }, "Поиск: название, продукт, тег")
                Gap(6.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mealHint != null) Pill("Для: ${MealType.name(mealHint).lowercase()}", onlyMeal) { onlyMeal = !onlyMeal }
                    Box(Modifier.weight(1f))
                    NumberField(servings, { servings = it }, "Порций", Modifier.width(110.dp))
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.id }) { r ->
                        val m = book.macros(r.id)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(r, servings.num()?.takeIf { it > 0 } ?: 1.0); onDismiss() }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RecipeThumb(r, 36.dp)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${kcal(m.kcal)} · Б ${m.protein.roundToInt()} г · ${minutes(r.totalMin())}", fontSize = 12.sp, color = extra.dim)
                            }
                        }
                    }
                    if (list.isEmpty()) item { Text("Ничего не нашлось", color = extra.dim, modifier = Modifier.padding(12.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/** Выбор приёмов пищи (несколько). */
@Composable
fun MealTypeChips(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MealType.order.forEach { m ->
            Pill(MealType.name(m), m in selected) { onChange(if (m in selected) selected - m else selected + m) }
        }
    }
}

@Composable
fun MacroLine(m: Macros, modifier: Modifier = Modifier, showFiber: Boolean = false) {
    val extra = LocalExtra.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOfNotNull(
            "Ккал" to m.kcal.roundToInt().toString(),
            "Белки" to g(m.protein),
            "Жиры" to g(m.fat),
            "Углеводы" to g(m.carbs),
            if (showFiber) "Клетчатка" to g(m.fiber) else null,
        ).forEach { (label, v) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(label, fontSize = 11.sp, color = extra.dim)
            }
        }
    }
}


@Composable
fun ScrollColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState())) { content() }
}
