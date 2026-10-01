@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import com.dasein.poryadok.ui.common.HowTo
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
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Recipe
import com.dasein.poryadok.logic.MealType
import com.dasein.poryadok.logic.RECIPE_CATEGORIES
import com.dasein.poryadok.logic.WORLD_CATEGORY
import com.dasein.poryadok.logic.cuisineOf
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.FullCenter
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.dasein.poryadok.logic.Paging
import com.dasein.poryadok.logic.TextQuery
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.PageBar
import com.dasein.poryadok.ui.common.PageInfo
import com.dasein.poryadok.ui.common.SearchField
import kotlinx.coroutines.launch

private const val ALL = "Все"
private const val MINE = "Мои рецепты"
private const val FAV = "Избранное"

private val RECIPE_TABS = listOf("Каталог", "Меню", "Избранное", "Мои", "Архив")
private val RECIPE_TAB_GLYPHS = listOf("ui:book", "ui:calendar", "ui:heart", "ui:notebook", "ui:folder")

@Composable
fun RecipesScreen(nav: NavHostController, initialTab: Int, initialMode: Int, embedded: Boolean = false) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    val book = rememberRecipeBook()
    Screen(
        title = "Рецепты",
        onBack = if (embedded) null else ({ nav.popBackStack() }),
        actions = {
            IconAction("food/04", "Список покупок") { nav.navigate(Routes.SHOPPING) }
            IconAction("food/22", "История готовки") { nav.navigate(Routes.COOK_HISTORY) }
            IconAction("food/28", "Шаблоны меню") { nav.navigate(Routes.PRESETS) }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "Кулинарная книга: ${book.recipes.size} рецептов, меню и планирование питания", fontSize = 13.sp, color = LocalExtra.current.dim,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ScrollableTabRow(
                selectedTabIndex = tab, edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.primary,
            ) {
                RECIPE_TABS.forEachIndexed { i, t ->
                    val n = when (i) { 2 -> book.recipes.count { it.favorite }; 3 -> book.recipes.count { it.custom }; 4 -> book.archived.size; else -> 0 }
                    Tab(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Glyph(RECIPE_TAB_GLYPHS[i], 20.dp) },
                        text = { Text(if (n > 0) "$t · $n" else t, maxLines = 1) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = LocalExtra.current.dim,
                    )
                }
            }
            if (!book.loaded) {
                FullCenter { CircularProgressIndicator() }
                return@Column
            }
            when (tab) {
                0 -> Catalog(nav, book)
                1 -> MenuPlanner(nav, book, initialMode)
                2 -> Favorites(nav, book)
                3 -> Mine(nav, book)
                else -> Archive(nav, book)
            }
        }
    }
}

/** Сортировка каталога. */
private enum class RecipeSort(val label: String) {
    NAME("А–Я"), FAST("Быстрее"), KCAL("Меньше ккал"), PROTEIN("Больше белка"), EASY("Проще"), NEW("Новые"), RELEVANCE("По совпадению"),
}

@Composable
private fun Catalog(nav: NavHostController, book: RecipeBook) {
    var menuFor by remember { mutableStateOf<Recipe?>(null) }
    menuFor?.let { RecipeQuickMenu(nav, it) { menuFor = null } }
    var q by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf(ALL) }
    var filters by rememberSaveable { mutableStateOf(setOf<String>()) }
    var cuisine by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(RecipeSort.NAME) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var size by rememberSaveable { mutableIntStateOf(20) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val query = remember(q) { TextQuery.parse(q) }
    val inCat: (Recipe, String) -> Boolean = { r, c ->
        when (c) {
            ALL -> true
            MINE -> r.custom
            FAV -> r.favorite
            else -> r.category == c || (c in MEAL_CATEGORY && MEAL_CATEGORY.getValue(c) in MealType.parse(r.meals) && r.category !in RECIPE_CATEGORIES.drop(5))
        }
    }
    val matched = remember(book, query, filters) {
        book.recipes.filter { r -> searchMatch(query, r, book.ingredients[r.id].orEmpty()) && filters.all { f -> matches(SmartFilter.valueOf(f), r, book.macros(r.id)) } }
    }
    val list = remember(matched, cat, cuisine, sort) {
        val base = matched.filter { r -> inCat(r, cat) && (cat != WORLD_CATEGORY || cuisine.isEmpty() || cuisineOf(r.tags) == cuisine) }
        when (sort) {
            RecipeSort.NAME -> base.sortedWith(compareByDescending<Recipe> { it.favorite }.thenBy { it.name })
            RecipeSort.FAST -> base.sortedBy { it.totalMin() }
            RecipeSort.KCAL -> base.sortedBy { book.macros(it.id).kcal }
            RecipeSort.PROTEIN -> base.sortedByDescending { book.macros(it.id).protein }
            RecipeSort.EASY -> base.sortedWith(compareBy<Recipe> { it.difficulty }.thenBy { it.totalMin() })
            RecipeSort.NEW -> base.sortedByDescending { it.createdAt }
            RecipeSort.RELEVANCE -> base.sortedByDescending { TextQuery.score(it.name, it.tags + " " + book.ingredients[it.id].orEmpty().joinToString(" ") { i -> i.name }, query) }
        }
    }
    LaunchedEffect(q, cat, cuisine, filters, sort) { page = 0 }
    val p = Paging.clamp(page, list.size, size)
    val pages = Paging.pages(list.size, size)
    val go: (Int) -> Unit = { page = it; scope.launch { listState.animateScrollToItem(1) } }

    LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            SearchField(q, { q = it }, "Блюдо, ингредиенты, тег: «курица рис -грибы»")
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
                items(listOf(ALL, FAV, MINE) + RECIPE_CATEGORIES) { c ->
                    val n = matched.count { inCat(it, c) }
                    Pill("$c $n", c == cat) { cat = c; cuisine = "" }
                }
            }
            if (cat == WORLD_CATEGORY) {
                val cuisines = book.recipes.filter { it.category == WORLD_CATEGORY }.mapNotNull { cuisineOf(it.tags) }.distinct().sorted()
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Все кухни", cuisine.isEmpty()) { cuisine = "" }
                    cuisines.forEach { c -> Pill(c, c == cuisine) { cuisine = if (cuisine == c) "" else c } }
                }
            }
            Gap(8.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SmartFilter.entries.forEach { f ->
                    Pill(f.label, f.name in filters) { filters = if (f.name in filters) filters - f.name else filters + f.name }
                }
            }
            Gap(8.dp)
            Text("Сортировка", fontSize = 12.sp, color = LocalExtra.current.dim)
            Gap(4.dp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(RecipeSort.entries.filter { it != RecipeSort.RELEVANCE || !query.isEmpty }) { s -> Pill(s.label, s == sort) { sort = s } }
            }
            PageInfo(list.size, p, size, { size = it; page = 0 }, "рецептов")
            PageBar(p, pages, go)
            Text("Удерживайте рецепт, чтобы убрать его в архив", fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 6.dp))
            Gap(6.dp)
        }
        if (list.isEmpty()) item {
            Empty(Ic.search, "Ничего не нашлось", "Измените запрос или фильтры — или добавьте свой рецепт.")
        }
        items(Paging.slice(list, p, size), key = { it.id }) { r ->
            RecipeCard(r, book.macros(r.id), onClick = { nav.navigate(Routes.recipe(r.id)) }, onFavorite = { io { Graph.extra.setFavorite(r.id, !r.favorite) } }, onLongClick = { menuFor = r })
        }
        item { Gap(4.dp); PageBar(p, pages, go) }
        item { HowTo("recipes") }
    }
}

/** Архив: блюда, которые не понравились. Они не попадают в каталог и подбор меню, но их можно вернуть. */
@Composable
private fun Archive(nav: NavHostController, book: RecipeBook) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }
    val query = remember(q) { TextQuery.parse(q) }
    val list = book.archived.filter { searchMatch(query, it, book.ingredients[it.id].orEmpty()) }.sortedBy { it.name }
    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("Вернуть все рецепты?") },
        text = { Text("Все ${book.archived.size} рецептов из архива снова появятся в каталоге и подборе меню.") },
        confirmButton = {
            TextButton(onClick = {
                val ids = book.archived.map { it.id }
                io { ids.forEach { Graph.extra.setArchived(it, false) } }
                confirmAll = false
            }) { Text("Вернуть") }
        },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Отмена") } },
    )
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            Text(
                "Сюда попадают блюда, которые вам не понравились: они не видны в каталоге и не попадают в подбор меню. " +
                    "Убрать в архив — удержать рецепт в каталоге или кнопка «Убрать в архив» на странице рецепта.",
                fontSize = 13.sp, color = LocalExtra.current.dim,
            )
            Gap(8.dp)
            if (book.archived.size > 5) {
                SearchField(q, { q = it }, "Поиск в архиве")
                Gap(8.dp)
            }
            if (book.archived.size > 1) OutlinedButton(onClick = { confirmAll = true }, Modifier.fillMaxWidth()) { Text("Вернуть все в каталог") }
            Gap(8.dp)
        }
        if (book.archived.isEmpty()) item {
            Empty(Ic.folder, "Архив пуст", "Не понравилось блюдо? Удерживайте его в каталоге и выберите «Убрать в архив» — оно перестанет попадаться.")
        }
        items(list, key = { it.id }) { r ->
            RecipeCard(r, book.macros(r.id), onClick = { nav.navigate(Routes.recipe(r.id)) }, onFavorite = { io { Graph.extra.setFavorite(r.id, !r.favorite) } })
            TextButton(onClick = {
                io { Graph.extra.setArchived(r.id, false) }
                Toast.makeText(ctx, "«${r.name}» снова в каталоге", Toast.LENGTH_SHORT).show()
            }) { Text("Вернуть в каталог") }
        }
        item { HowTo("recipes") }
    }
}

/** Категории-приёмы пищи ищут и по отметкам «подходит для». */
private val MEAL_CATEGORY = mapOf("Завтраки" to MealType.BREAKFAST, "Обеды" to MealType.LUNCH, "Ужины" to MealType.DINNER, "Перекусы" to MealType.SNACK)

private val FAV_FILTERS = listOf("Все", "Завтраки", "Обеды", "Ужины", "Перекусы", "Быстрые", "Высокобелковые", "Низкокалорийные")

@Composable
private fun Favorites(nav: NavHostController, book: RecipeBook) {
    var menuFor by remember { mutableStateOf<Recipe?>(null) }
    menuFor?.let { RecipeQuickMenu(nav, it) { menuFor = null } }
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
            RecipeCard(r, book.macros(r.id), onClick = { nav.navigate(Routes.recipe(r.id)) }, onFavorite = { io { Graph.extra.setFavorite(r.id, false) } }, onLongClick = { menuFor = r })
        }
        item { HowTo("recipes") }
    }
}

@Composable
private fun Mine(nav: NavHostController, book: RecipeBook) {
    var menuFor by remember { mutableStateOf<Recipe?>(null) }
    menuFor?.let { RecipeQuickMenu(nav, it) { menuFor = null } }
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
            RecipeCard(r, book.macros(r.id), onClick = { nav.navigate(Routes.recipe(r.id)) }, onFavorite = { io { Graph.extra.setFavorite(r.id, !r.favorite) } }, onLongClick = { menuFor = r })
        }
        item { HowTo("recipes") }
    }
}

@Composable
internal fun CenterNote(text: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = LocalExtra.current.dim, fontSize = 13.sp)
    }
}

/** Меню по долгому нажатию на рецепт: открыть, избранное, архив. */
@Composable
private fun RecipeQuickMenu(nav: NavHostController, r: Recipe, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(r.name) },
        text = {
            Column {
                TextButton(onClick = { onDismiss(); nav.navigate(Routes.recipe(r.id)) }, Modifier.fillMaxWidth()) { Text("Открыть рецепт") }
                TextButton(onClick = { io { Graph.extra.setFavorite(r.id, !r.favorite) }; onDismiss() }, Modifier.fillMaxWidth()) {
                    Text(if (r.favorite) "Убрать из избранного" else "В избранное")
                }
                TextButton(onClick = {
                    io { Graph.extra.setArchived(r.id, !r.archived) }
                    Toast.makeText(ctx, if (r.archived) "Рецепт возвращён в каталог" else "Рецепт в архиве — вернуть можно на вкладке «Архив»", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }, Modifier.fillMaxWidth()) { Text(if (r.archived) "Вернуть из архива" else "Убрать в архив") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
