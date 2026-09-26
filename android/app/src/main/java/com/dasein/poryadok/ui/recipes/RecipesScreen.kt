@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Recipe
import com.dasein.poryadok.logic.MealType
import com.dasein.poryadok.logic.RECIPE_CATEGORIES
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.FullCenter
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra

private const val ALL = "Все"
private const val MINE = "Мои рецепты"
private const val FAV = "Избранное"

@Composable
fun RecipesScreen(nav: NavHostController, initialTab: Int, initialMode: Int) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    val book = rememberRecipeBook()
    Screen(
        title = "Рецепты",
        onBack = { nav.popBackStack() },
        actions = {
            IconAction(Ic.cart, "Список покупок") { nav.navigate(Routes.SHOPPING) }
            IconAction(Ic.book, "История готовки") { nav.navigate(Routes.COOK_HISTORY) }
            IconAction(Ic.folder, "Шаблоны меню") { nav.navigate(Routes.PRESETS) }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "Блюда, меню и планирование питания", fontSize = 13.sp, color = LocalExtra.current.dim,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Segments(
                listOf(0 to "Рецепты", 1 to "Меню", 2 to "Избранное", 3 to "Мои"), tab, { tab = it },
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (!book.loaded) {
                FullCenter { CircularProgressIndicator() }
                return@Column
            }
            when (tab) {
                0 -> Catalog(nav, book)
                1 -> MenuPlanner(nav, book, initialMode)
                2 -> Favorites(nav, book)
                else -> Mine(nav, book)
            }
        }
    }
}

@Composable
private fun Catalog(nav: NavHostController, book: RecipeBook) {
    var q by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf(ALL) }
    var filters by rememberSaveable { mutableStateOf(setOf<String>()) }
    val list = book.recipes.filter { r ->
        val m = book.macros(r.id)
        searchMatch(q, r, book.ingredients[r.id].orEmpty()) &&
            when (cat) {
                ALL -> true
                MINE -> r.custom
                FAV -> r.favorite
                else -> r.category == cat || (cat in MEAL_CATEGORY && MEAL_CATEGORY.getValue(cat) in MealType.parse(r.meals) && r.category !in RECIPE_CATEGORIES.drop(4))
            } &&
            filters.all { f -> matches(SmartFilter.valueOf(f), r, m) }
    }.sortedWith(compareByDescending<Recipe> { it.favorite }.thenBy { it.name })

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            TextInput(q, { q = it }, "Поиск: название, ингредиент, тег, приём пищи")
            Gap(8.dp)
            Row {
                Button(onClick = { nav.navigate(Routes.recipeEdit(0)) }, Modifier.weight(1f)) { Text("+ Добавить рецепт") }
                HGap(8.dp)
                OutlinedButton(onClick = { nav.navigate(Routes.MENU_CREATE) }, Modifier.weight(1f)) { Text("Создать меню") }
            }
            Gap(10.dp)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(ALL) + RECIPE_CATEGORIES + listOf(MINE, FAV)) { c -> Pill(c, c == cat) { cat = c } }
            }
            Gap(8.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SmartFilter.entries.forEach { f ->
                    Pill(f.label, f.name in filters) { filters = if (f.name in filters) filters - f.name else filters + f.name }
                }
            }
            Gap(10.dp)
            Text("${list.size} из ${book.recipes.size}", fontSize = 12.sp, color = LocalExtra.current.dim)
            Gap(6.dp)
        }
        if (list.isEmpty()) item {
            Empty(Ic.search, "Ничего не нашлось", "Измените запрос или фильтры — или добавьте свой рецепт.")
        }
        items(list, key = { it.id }) { r ->
            RecipeCard(r, book.macros(r.id), onClick = { nav.navigate(Routes.recipe(r.id)) }, onFavorite = { io { Graph.extra.setFavorite(r.id, !r.favorite) } })
        }
    }
}

/** Категории-приёмы пищи ищут и по отметкам «подходит для». */
private val MEAL_CATEGORY = mapOf("Завтраки" to MealType.BREAKFAST, "Обеды" to MealType.LUNCH, "Ужины" to MealType.DINNER, "Перекусы" to MealType.SNACK)

private val FAV_FILTERS = listOf("Все", "Завтраки", "Обеды", "Ужины", "Перекусы", "Быстрые", "Высокобелковые", "Низкокалорийные")

@Composable
private fun Favorites(nav: NavHostController, book: RecipeBook) {
    var f by rememberSaveable { mutableStateOf("Все") }
    val list = book.recipes.filter { it.favorite }.filter { r ->
        val m = book.macros(r.id)
        when (f) {
            "Быстрые" -> matches(SmartFilter.FAST20, r, m)
            "Высокобелковые" -> matches(SmartFilter.HIGHPROTEIN, r, m)
            "Низкокалорийные" -> matches(SmartFilter.LOWCAL, r, m)
            "Все" -> true
            else -> r.category == f || MEAL_CATEGORY[f]?.let { it in MealType.parse(r.meals) } == true
        }
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FAV_FILTERS.forEach { Pill(it, it == f) { f = it } }
            }
            Gap(10.dp)
        }
        if (list.isEmpty()) item {
            Empty(Ic.heart, "Избранного пока нет", "Отмечайте сердечком блюда, которые готовите чаще всего, — они будут под рукой и первыми в подборе меню.")
        }
        items(list, key = { it.id }) { r ->
            RecipeCard(r, book.macros(r.id), onClick = { nav.navigate(Routes.recipe(r.id)) }, onFavorite = { io { Graph.extra.setFavorite(r.id, false) } })
        }
    }
}

@Composable
private fun Mine(nav: NavHostController, book: RecipeBook) {
    val list = book.recipes.filter { it.custom }.sortedByDescending { it.createdAt }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            Button(onClick = { nav.navigate(Routes.recipeEdit(0)) }, Modifier.fillMaxWidth()) { Text("+ Добавить рецепт") }
            Gap(10.dp)
        }
        if (list.isEmpty()) item {
            Empty(Ic.notebook, "Своих рецептов пока нет", "Добавьте блюдо: ингредиенты из базы продуктов, шаги — КБЖУ посчитается автоматически.")
        }
        items(list, key = { it.id }) { r ->
            RecipeCard(r, book.macros(r.id), onClick = { nav.navigate(Routes.recipe(r.id)) }, onFavorite = { io { Graph.extra.setFavorite(r.id, !r.favorite) } })
        }
    }
}

@Composable
internal fun CenterNote(text: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = LocalExtra.current.dim, fontSize = 13.sp)
    }
}
