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
import com.dasein.poryadok.logic.Fridge
import com.dasein.poryadok.logic.RecipeFacets
import com.dasein.poryadok.ui.common.FilterButton
import com.dasein.poryadok.ui.common.FilterOption
import com.dasein.poryadok.ui.common.FilterRow
import com.dasein.poryadok.ui.common.FilterSheet
import com.dasein.poryadok.ui.common.OptionSheet
import com.dasein.poryadok.ui.common.SectionTabs
import com.dasein.poryadok.ui.common.SortFilterBar
import com.dasein.poryadok.ui.common.observe

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
            // Все пять вкладок видны сразу — без прокрутки вбок.
            SectionTabs(
                RECIPE_TABS, RECIPE_TAB_GLYPHS, tab,
                listOf(0, 0, book.recipes.count { it.favorite }, book.recipes.count { it.custom }, book.archived.size),
            ) { tab = it }
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

/** Убрать блюдо в архив (или вернуть) одной кнопкой с подсказкой. */
private fun toggleArchive(ctx: android.content.Context, r: Recipe) {
    io { Graph.extra.setArchived(r.id, !r.archived) }
    Toast.makeText(ctx, if (r.archived) "«${r.name}» снова в каталоге" else "«${r.name}» в архиве — вернуть можно на вкладке «Архив»", Toast.LENGTH_SHORT).show()
}

/** Сортировка каталога. */
private enum class RecipeSort(val label: String) {
    NAME("По названию"), FRIDGE("Из холодильника"), FAST("Сначала быстрые"), KCAL("Меньше калорий"), PROTEIN("Больше белка"),
    EASY("Сначала простые"), NEW("Сначала новые"), RELEVANCE("По совпадению"),
}

/** Время приготовления и калорийность порции — варианты фильтров. */
private val TIME_OPTIONS = listOf("15" to "До 15 минут", "30" to "До 30 минут", "45" to "До 45 минут", "60" to "До 1 часа")
private val KCAL_OPTIONS = listOf("300" to "До 300 ккал", "450" to "До 450 ккал", "600" to "До 600 ккал", "800" to "До 800 ккал")

/** Какой лист фильтра открыт. */
private enum class CatalogSheet { SORT, ALL, CATEGORY, CUISINE, TIME, KCAL, FEATURES, FRIDGE, MEAL, BASE, PROTEIN, DIFFICULTY }

@Composable
private fun Catalog(nav: NavHostController, book: RecipeBook) {
    val ctx = LocalContext.current
    var menuFor by remember { mutableStateOf<Recipe?>(null) }
    menuFor?.let { RecipeQuickMenu(nav, it) { menuFor = null } }
    val settings by observe(null) { Graph.prefs.settings }
    val fridge = remember(settings?.fridge) { Fridge.decode(settings?.fridge.orEmpty()) }
    var q by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf(ALL) }
    var filters by rememberSaveable { mutableStateOf(setOf<String>()) }
    var cuisine by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var meal by rememberSaveable { mutableStateOf("") }
    var base by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var diff by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(RecipeSort.NAME) }
    var sheet by remember { mutableStateOf<CatalogSheet?>(null) }
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
    val names: (Recipe) -> List<String> = { r -> book.ingredients[r.id].orEmpty().map { it.name } }
    val fits = remember(book, fridge) {
        if (fridge.isEmpty && !fridge.onlyHave) emptyMap() else book.recipes.associate { it.id to Fridge.fit(names(it), fridge) }
    }
    val fridgeOn = fits.isNotEmpty()
    // Все фильтры, кроме категории и кухни (по ним считаются числа в списках этих групп).
    val pass: (Recipe, String?) -> Boolean = { r, skip ->
        val m = book.macros(r.id)
        (skip == "features" || filters.all { f -> matches(SmartFilter.valueOf(f), r, m) }) &&
            (skip == "time" || time.isEmpty() || r.totalMin() <= time.toInt()) &&
            (skip == "kcal" || kcal.isEmpty() || m.kcal <= kcal.toInt()) &&
            (skip == "meal" || meal.isEmpty() || meal.toInt() in MealType.parse(r.meals)) &&
            (skip == "base" || RecipeFacets.matchesBase(base, names(r))) &&
            (skip == "protein" || RecipeFacets.matchesProtein(protein, m.protein)) &&
            (skip == "diff" || RecipeFacets.matchesDifficulty(diff, r.difficulty)) &&
            (skip == "fridge" || fits.isEmpty() || fits[r.id]?.ok == true)
    }
    val searched = remember(book, query) { book.recipes.filter { r -> searchMatch(query, r, book.ingredients[r.id].orEmpty()) } }
    val matched = remember(searched, filters, time, kcal, fits, meal, base, protein, diff) { searched.filter { pass(it, null) } }
    /** Сколько блюд даст вариант группы при остальных выбранных фильтрах. */
    fun countWith(group: String, test: (Recipe) -> Boolean): Int =
        searched.count { r -> pass(r, group) && inCat(r, cat) && (cuisine.isEmpty() || cuisineOf(r.tags) == cuisine) && test(r) }
    val sorts = RecipeSort.entries.filter { (it != RecipeSort.RELEVANCE || !query.isEmpty) && (it != RecipeSort.FRIDGE || fridgeOn) }
    val sortNow = if (sort in sorts) sort else RecipeSort.NAME
    val list = remember(matched, cat, cuisine, sortNow, fits) {
        val base = matched.filter { r -> inCat(r, cat) && (cuisine.isEmpty() || cuisineOf(r.tags) == cuisine) }
        when (sortNow) {
            RecipeSort.NAME -> base.sortedWith(compareByDescending<Recipe> { it.favorite }.thenBy { it.name })
            RecipeSort.FRIDGE -> base.sortedWith(compareBy<Recipe> { fits[it.id]?.let(Fridge::rank) ?: 0 }.thenBy { it.name })
            RecipeSort.FAST -> base.sortedBy { it.totalMin() }
            RecipeSort.KCAL -> base.sortedBy { book.macros(it.id).kcal }
            RecipeSort.PROTEIN -> base.sortedByDescending { book.macros(it.id).protein }
            RecipeSort.EASY -> base.sortedWith(compareBy<Recipe> { it.difficulty }.thenBy { it.totalMin() })
            RecipeSort.NEW -> base.sortedByDescending { it.createdAt }
            RecipeSort.RELEVANCE -> base.sortedByDescending { TextQuery.score(it.name, it.tags + " " + book.ingredients[it.id].orEmpty().joinToString(" ") { i -> i.name }, query) }
        }
    }
    LaunchedEffect(q, cat, cuisine, filters, sort, time, kcal, fridge, meal, base, protein, diff) { page = 0 }
    val p = Paging.clamp(page, list.size, size)
    val pages = Paging.pages(list.size, size)
    val go: (Int) -> Unit = { page = it; scope.launch { listState.animateScrollToItem(1) } }
    val cuisines = remember(book) { book.recipes.mapNotNull { cuisineOf(it.tags) }.distinct().sorted() }
    val active = listOf(
        cat != ALL, cuisine.isNotEmpty(), time.isNotEmpty(), kcal.isNotEmpty(), filters.isNotEmpty(), fridgeOn,
        meal.isNotEmpty(), base.isNotEmpty(), protein.isNotEmpty(), diff.isNotEmpty(),
    ).count { it }
    val mealLabel = meal.toIntOrNull()?.let { MealType.name(it) }
    val baseLabel = RecipeFacets.BASE_OPTIONS.firstOrNull { it.first == base }?.second
    val proteinLabel = RecipeFacets.PROTEIN_OPTIONS.firstOrNull { it.first == protein }?.second?.substringBefore(" (")
    val diffLabel = RecipeFacets.DIFFICULTY_OPTIONS.firstOrNull { it.first == diff }?.second
    val fridgeProducts = remember(book) {
        book.recipes.flatMap { r -> names(r).distinct() }.filter { !Fridge.isStaple(it) }
            .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
    }
    fun saveFridge(spec: Fridge.Spec) {
        io { Graph.prefs.update { it.copy(fridge = Fridge.encode(spec)) } }
        sort = if (spec.isEmpty && !spec.onlyHave) RecipeSort.NAME else RecipeSort.FRIDGE
    }

    when (sheet) {
        CatalogSheet.SORT -> OptionSheet("Сортировка", sorts.map { FilterOption(it.name, it.label) }, setOf(sortNow.name), onDismiss = { sheet = null }) {
            sort = RecipeSort.valueOf(it.first()); sheet = null
        }
        CatalogSheet.CATEGORY -> OptionSheet(
            "Категория", (listOf(ALL, FAV, MINE) + RECIPE_CATEGORIES).map { c -> FilterOption(c, c, matched.count { inCat(it, c) }) },
            setOf(cat), onDismiss = { sheet = null },
        ) { cat = it.firstOrNull() ?: ALL; sheet = null }
        CatalogSheet.CUISINE -> OptionSheet(
            "Кухня", listOf(FilterOption("", "Любая", matched.size)) + cuisines.map { c -> FilterOption(c, c, matched.count { cuisineOf(it.tags) == c }) },
            setOf(cuisine), onDismiss = { sheet = null },
        ) { cuisine = it.firstOrNull().orEmpty(); sheet = null }
        CatalogSheet.MEAL -> OptionSheet(
            "Приём пищи",
            listOf(FilterOption("", "Любой", countWith("meal") { true })) +
                MealType.order.map { m -> FilterOption("$m", MealType.name(m), countWith("meal") { m in MealType.parse(it.meals) }) },
            setOf(meal), onDismiss = { sheet = null },
        ) { meal = it.firstOrNull().orEmpty(); sheet = null }
        CatalogSheet.BASE -> OptionSheet(
            "Мясо и основа",
            listOf(FilterOption("", "Все", countWith("base") { true })) +
                RecipeFacets.BASE_OPTIONS.map { (k, l) -> FilterOption(k, l, countWith("base") { RecipeFacets.matchesBase(k, names(it)) }) },
            setOf(base), onDismiss = { sheet = null },
        ) { base = it.firstOrNull().orEmpty(); sheet = null }
        CatalogSheet.PROTEIN -> OptionSheet(
            "Белок на порцию",
            listOf(FilterOption("", "Любой", countWith("protein") { true })) +
                RecipeFacets.PROTEIN_OPTIONS.map { (k, l) -> FilterOption(k, l, countWith("protein") { RecipeFacets.matchesProtein(k, book.macros(it.id).protein) }) },
            setOf(protein), onDismiss = { sheet = null },
        ) { protein = it.firstOrNull().orEmpty(); sheet = null }
        CatalogSheet.DIFFICULTY -> OptionSheet(
            "Сложность",
            listOf(FilterOption("", "Любая", countWith("diff") { true })) +
                RecipeFacets.DIFFICULTY_OPTIONS.map { (k, l) -> FilterOption(k, l, countWith("diff") { RecipeFacets.matchesDifficulty(k, it.difficulty) }) },
            setOf(diff), onDismiss = { sheet = null },
        ) { diff = it.firstOrNull().orEmpty(); sheet = null }
        CatalogSheet.TIME -> OptionSheet(
            "Время приготовления", listOf(FilterOption("", "Любое", countWith("time") { true })) + TIME_OPTIONS.map { (k, l) -> FilterOption(k, l, countWith("time") { it.totalMin() <= k.toInt() }) },
            setOf(time), onDismiss = { sheet = null },
        ) { time = it.firstOrNull().orEmpty(); sheet = null }
        CatalogSheet.KCAL -> OptionSheet(
            "Калорийность порции", listOf(FilterOption("", "Любая", countWith("kcal") { true })) + KCAL_OPTIONS.map { (k, l) -> FilterOption(k, l, countWith("kcal") { book.macros(it.id).kcal <= k.toInt() }) },
            setOf(kcal), onDismiss = { sheet = null },
        ) { kcal = it.firstOrNull().orEmpty(); sheet = null }
        CatalogSheet.FEATURES -> OptionSheet(
            "Особенности", SmartFilter.entries.map { f -> FilterOption(f.name, f.label.replaceFirstChar(Char::uppercase), countWith("features") { matches(f, it, book.macros(it.id)) }) },
            filters, multi = true, onDismiss = { sheet = null },
        ) { filters = it; sheet = null }
        CatalogSheet.FRIDGE -> FridgeSheet(
            fridgeProducts, fridge,
            countFor = { spec -> book.recipes.count { Fridge.fit(names(it), spec).ok } },
            onDismiss = { sheet = null },
        ) { saveFridge(it); sheet = null }
        CatalogSheet.ALL -> FilterSheet(
            "Фильтры", { sheet = null },
            onReset = if (active == 0) null else ({
                cat = ALL; cuisine = ""; time = ""; kcal = ""; filters = emptySet(); meal = ""; base = ""; protein = ""; diff = ""
                saveFridge(Fridge.Spec())
            }),
            applyLabel = "Показать: ${list.size}", onApply = { sheet = null },
        ) {
            FilterRow("Из холодильника", if (fridgeOn) fridgeLabel(fridge) else null) { sheet = CatalogSheet.FRIDGE }
            FilterRow("Мясо и основа", baseLabel) { sheet = CatalogSheet.BASE }
            FilterRow("Белок", proteinLabel) { sheet = CatalogSheet.PROTEIN }
            FilterRow("Приём пищи", mealLabel) { sheet = CatalogSheet.MEAL }
            FilterRow("Категория", cat.takeIf { it != ALL }) { sheet = CatalogSheet.CATEGORY }
            FilterRow("Кухня", cuisine.ifEmpty { null }) { sheet = CatalogSheet.CUISINE }
            FilterRow("Время", TIME_OPTIONS.firstOrNull { it.first == time }?.second) { sheet = CatalogSheet.TIME }
            FilterRow("Калории", KCAL_OPTIONS.firstOrNull { it.first == kcal }?.second) { sheet = CatalogSheet.KCAL }
            FilterRow("Сложность", diffLabel) { sheet = CatalogSheet.DIFFICULTY }
            FilterRow("Особенности", featuresLabel(filters)) { sheet = CatalogSheet.FEATURES }
        }
        null -> Unit
    }

    LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            Gap(8.dp)
            SearchField(q, { q = it }, "Блюдо, ингредиенты, тег: «курица рис -грибы»")
            Gap(8.dp)
            Row {
                Button(onClick = { nav.navigate(Routes.recipeEdit(0)) }, Modifier.weight(1f)) { Text("+ Свой рецепт", maxLines = 1, softWrap = false) }
                HGap(8.dp)
                OutlinedButton(onClick = { nav.navigate(Routes.MENU_CREATE) }, Modifier.weight(1f)) { Text("Создать меню", maxLines = 1, softWrap = false) }
            }
            Gap(4.dp)
        }
        item {
            SortFilterBar(sortNow.label, { sheet = CatalogSheet.SORT }, active) { sheet = CatalogSheet.ALL }
            // Все группы фильтров видны сразу, без карусели: нажали — снизу список вариантов с числом блюд.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterButton("Из холодильника", if (fridgeOn) fridgeLabel(fridge) else null) { sheet = CatalogSheet.FRIDGE }
                FilterButton("Мясо", baseLabel) { sheet = CatalogSheet.BASE }
                FilterButton("Белок", proteinLabel) { sheet = CatalogSheet.PROTEIN }
                FilterButton("Приём пищи", mealLabel) { sheet = CatalogSheet.MEAL }
                FilterButton("Категория", cat.takeIf { it != ALL }) { sheet = CatalogSheet.CATEGORY }
                FilterButton("Время", TIME_OPTIONS.firstOrNull { it.first == time }?.second) { sheet = CatalogSheet.TIME }
                FilterButton("Калории", KCAL_OPTIONS.firstOrNull { it.first == kcal }?.second) { sheet = CatalogSheet.KCAL }
                FilterButton("Сложность", diffLabel) { sheet = CatalogSheet.DIFFICULTY }
                FilterButton("Кухня", cuisine.ifEmpty { null }) { sheet = CatalogSheet.CUISINE }
                FilterButton("Особенности", featuresLabel(filters)) { sheet = CatalogSheet.FEATURES }
            }
            PageInfo(list.size, p, size, { size = it; page = 0 }, "рецептов")
            PageBar(p, pages, go)
            Text("Значок архива рядом с сердечком убирает блюдо из каталога", fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 6.dp))
            Gap(6.dp)
        }
        if (list.isEmpty()) item {
            Empty(Ic.search, "Ничего не нашлось", if (fridgeOn) "Отметьте в холодильнике больше продуктов или выключите «Только если всё есть»." else "Измените запрос или фильтры — или добавьте свой рецепт.")
        }
        items(Paging.slice(list, p, size), key = { it.id }) { r ->
            Column {
                RecipeCard(r, book.macros(r.id), onClick = { nav.navigate(Routes.recipe(r.id)) }, onFavorite = { io { Graph.extra.setFavorite(r.id, !r.favorite) } }, onLongClick = { menuFor = r }, onArchive = { toggleArchive(ctx, r) })
                fits[r.id]?.let { f ->
                    Text(
                        if (f.lacking.isEmpty()) "✓ Всё есть дома" else "Не хватает: " + f.lacking.take(4).joinToString(", ") + if (f.lacking.size > 4) " и ещё ${f.lacking.size - 4}" else "",
                        fontSize = 12.sp, color = if (f.lacking.isEmpty()) MaterialTheme.colorScheme.primary else LocalExtra.current.dim,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 6.dp),
                    )
                }
            }
        }
        item { Gap(4.dp); PageBar(p, pages, go) }
        item { HowTo("recipes") }
    }
}

private fun fridgeLabel(f: Fridge.Spec): String = when {
    f.main.isNotEmpty() -> f.main.first() + if (f.count > 1) " +${f.count - 1}" else ""
    f.have.isNotEmpty() -> "Есть: ${f.have.size}"
    f.missing.isNotEmpty() -> "Без ${f.missing.size} прод."
    else -> "Всё есть"
}

private fun featuresLabel(f: Set<String>): String? = when (f.size) {
    0 -> null
    1 -> SmartFilter.valueOf(f.first()).label.replaceFirstChar(Char::uppercase)
    else -> "Особенности: ${f.size}"
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
            Empty(Ic.archive, "Архив пуст", "Не понравилось блюдо? Нажмите значок архива рядом с сердечком — оно перестанет попадаться.")
        }
        items(list, key = { it.id }) { r ->
            RecipeCard(r, book.macros(r.id), onClick = { nav.navigate(Routes.recipe(r.id)) }, onFavorite = { io { Graph.extra.setFavorite(r.id, !r.favorite) } }, onArchive = { toggleArchive(ctx, r) })
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
    val ctx = LocalContext.current
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
            RecipeCard(r, book.macros(r.id), onClick = { nav.navigate(Routes.recipe(r.id)) }, onFavorite = { io { Graph.extra.setFavorite(r.id, false) } }, onLongClick = { menuFor = r }, onArchive = { toggleArchive(ctx, r) })
        }
        item { HowTo("recipes") }
    }
}

@Composable
private fun Mine(nav: NavHostController, book: RecipeBook) {
    val ctx = LocalContext.current
    var menuFor by remember { mutableStateOf<Recipe?>(null) }
    menuFor?.let { RecipeQuickMenu(nav, it) { menuFor = null } }
    val list = book.recipes.filter { it.custom }.sortedByDescending { it.createdAt }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            Button(onClick = { nav.navigate(Routes.recipeEdit(0)) }, Modifier.fillMaxWidth()) { Text("+ Добавить рецепт") }
            Gap(8.dp)
            RecipeShareButtons(list)
            Gap(10.dp)
        }
        if (list.isEmpty()) item {
            Empty(Ic.notebook, "Своих рецептов пока нет", "Добавьте блюдо: ингредиенты из базы продуктов, шаги — КБЖУ посчитается автоматически.")
        }
        items(list, key = { it.id }) { r ->
            RecipeCard(r, book.macros(r.id), onClick = { nav.navigate(Routes.recipe(r.id)) }, onFavorite = { io { Graph.extra.setFavorite(r.id, !r.favorite) } }, onLongClick = { menuFor = r }, onArchive = { toggleArchive(ctx, r) })
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
    var share by remember { mutableStateOf(false) }
    if (share) { ShareRecipesDialog(listOf(r), setOf(r.id)) { share = false; onDismiss() }; return }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(r.name) },
        text = {
            Column {
                TextButton(onClick = { onDismiss(); nav.navigate(Routes.recipe(r.id)) }, Modifier.fillMaxWidth()) { Text("Открыть рецепт") }
                TextButton(onClick = { share = true }, Modifier.fillMaxWidth()) { Text("Поделиться рецептом (файл)") }
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
