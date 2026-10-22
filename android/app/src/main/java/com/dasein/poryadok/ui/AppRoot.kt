package com.dasein.poryadok.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.dasein.poryadok.ui.common.Glyph
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.ui.common.AppIcon
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.calendar.CalendarScreen
import com.dasein.poryadok.ui.calendar.EventEditScreen
import com.dasein.poryadok.ui.finance.FinanceScreen
import com.dasein.poryadok.ui.finance.NotebookScreen
import com.dasein.poryadok.ui.finance.TxnEditScreen
import com.dasein.poryadok.ui.health.HealthScreen
import com.dasein.poryadok.ui.health.WellbeingScreen
import com.dasein.poryadok.ui.media.MediaCardScreen
import com.dasein.poryadok.ui.media.MediaSearchScreen
import com.dasein.poryadok.ui.more.MoreScreen
import com.dasein.poryadok.ui.more.ReviewScreen
import com.dasein.poryadok.ui.more.SearchScreen
import com.dasein.poryadok.ui.more.SberScreen
import com.dasein.poryadok.ui.more.SettingsScreen
import com.dasein.poryadok.ui.more.StepsScreen
import com.dasein.poryadok.ui.more.WheelScreen
import com.dasein.poryadok.ui.notes.NoteEditScreen
import com.dasein.poryadok.ui.notes.NotesScreen
import com.dasein.poryadok.ui.notes.TopScreen
import com.dasein.poryadok.ui.notes.TopsScreen
import com.dasein.poryadok.ui.productivity.GoalScreen
import com.dasein.poryadok.ui.productivity.GoalsScreen
import com.dasein.poryadok.ui.productivity.HabitDetailScreen
import com.dasein.poryadok.ui.productivity.HabitEditScreen
import com.dasein.poryadok.ui.productivity.HabitsScreen
import com.dasein.poryadok.ui.productivity.TaskEditScreen
import com.dasein.poryadok.ui.productivity.TasksScreen
import com.dasein.poryadok.ui.recipes.CookHistoryScreen
import com.dasein.poryadok.ui.recipes.CreateMenuScreen
import com.dasein.poryadok.ui.recipes.PresetEditScreen
import com.dasein.poryadok.ui.recipes.PresetsScreen
import com.dasein.poryadok.ui.recipes.RecipeDetailScreen
import com.dasein.poryadok.ui.recipes.RecipeEditScreen
import com.dasein.poryadok.ui.recipes.RecipesScreen
import com.dasein.poryadok.ui.recipes.ShoppingScreen
import com.dasein.poryadok.ui.today.TodayScreen
import com.dasein.poryadok.ui.wardrobe.OutfitsScreen
import com.dasein.poryadok.ui.wardrobe.WardrobeScreen

object Routes {
    const val TODAY = "today"
    const val TASKS = "tasks"
    const val CALENDAR = "calendar"
    const val FINANCE = "finance"
    const val MORE = "more"
    const val HABITS = "habits"
    const val GOALS = "goals"
    const val HEALTH = "health"
    const val WELLBEING = "wellbeing"
    const val NOTES = "notes"
    const val TOPS = "tops"
    const val WARDROBE = "wardrobe"
    const val OUTFITS = "outfits"
    const val WHEEL = "wheel"
    const val REVIEW = "review"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val CRAFT = "craft"
    const val CRAFT_STASH = "craftStash"
    const val CRAFT_MINE = "craftMine"
    const val CRAFT_PBN = "craftPbn"
    const val CRAFT_PENCILS = "craftPencils"
    fun pbn(id: Long) = "craftPbn/$id"
    const val SEARCH = "search"
    const val RECIPES = "recipes"
    const val MENU_CREATE = "menuCreate"
    const val PRESETS = "presets"
    const val SHOPPING = "shopping"
    const val COOK_HISTORY = "cookHistory"
    const val STEPS = "steps"
    const val SBER = "sber"
    const val FIN_NOTEBOOK = "finNotebook"
    const val PLAN = "plan"
    const val HEALTH_HUB = "healthHub"
    const val TOPS_HUB = "topsHub"
    const val MEDIA_SEARCH = "mediaSearch"
    const val WEIGHT_TREND = "weightTrend"
    const val BODY_COMPARE = "bodyCompare"
    const val BODY_SCIENCE = "bodyScience"
    const val WIDGET_EDITOR = "widgetEditor"
    const val WEATHER = "weather"
    const val TRAINING = "training"
    const val SMALL_TALK = "smallTalk"

    fun task(id: Long, day: Long = -1, goal: Long = -1) = "task/$id?day=$day&goal=$goal"
    fun event(id: Long, day: Long = -1) = "event/$id?day=$day"
    fun habit(id: Long) = "habit/$id"
    fun habitEdit(id: Long) = "habitEdit/$id"
    fun goal(id: Long) = "goal/$id"
    fun txn(id: Long, income: Boolean = false) = "txn/$id?income=$income"
    fun note(id: Long) = "note/$id"
    fun top(id: Long) = "top/$id"
    fun health(tab: Int) = "$HEALTH?tab=$tab"
    fun wellbeing(tab: Int) = "$WELLBEING?tab=$tab"
    fun finance(tab: Int) = "$FINANCE?tab=$tab"
    fun calendar(tab: Int) = "$CALENDAR?tab=$tab"
    fun recipes(tab: Int = 0, mode: Int = 0) = "$RECIPES?tab=$tab&mode=$mode"
    fun recipe(id: Long) = "recipe/$id"
    fun recipeEdit(id: Long) = "recipeEdit/$id"
    fun menuCreate(day: Long = 0) = "$MENU_CREATE?day=$day"
    fun preset(id: Long) = "preset/$id"
    fun plan(tab: Int) = "$PLAN?tab=$tab"
    fun topsHub(tab: Int) = "$TOPS_HUB?tab=$tab"
    fun media(id: Long, kind: Int = 0) = "media/$id?kind=$kind"
    fun craft(id: Long) = "craft/$id"
    fun craftWork(id: Long) = "craftWork/$id"
    fun mediaSearch(kind: Int) = "$MEDIA_SEARCH?kind=$kind"
    fun bodyDetail(at: Long) = "bodyDetail/$at"
    fun kpImport(kind: Int) = "kpImport?kind=$kind"
    fun page(id: Long) = "page/$id"
    fun holiday(id: String) = "holiday/$id"
    fun training(tab: Int = 0) = "$TRAINING?tab=$tab"
    fun trainingPlan(id: Long) = "trainingPlan/$id"
    fun exercise(id: Long) = "exercise/$id"
    fun session(id: Long) = "session/$id"
}

internal data class Tab(val route: String, val label: String, val icon: String) {
    /** Маршрут без параметров — так его называет навигация. */
    val base get() = route.substringBefore('?')
}

private val tabs = listOf(
    Tab(Routes.TODAY, "Главное", "d01/00"),
    Tab(Routes.RECIPES, "Рецепты", "d12/00"),
    Tab(Routes.HEALTH_HUB, "Здоровье", "d01/02"),
    Tab(Routes.PLAN, "График", "d01/01"),
    Tab(Routes.TOPS_HUB, "Топы", "d17/03"),
    Tab(Routes.MORE, "Ещё", "d01/04"),
)

private fun NavBackStackEntry.long(name: String): Long = arguments?.getLong(name) ?: -1L
private fun NavBackStackEntry.int(name: String): Int = arguments?.getInt(name) ?: 0

/** Открыть вкладку с конкретным разделом внутри (например, «График» → «Задачи»). */
fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(settings: Settings, deepLink: MutableState<String?>) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route?.substringBefore('?')
    val barTabs by NavTabs.state
    val showBar = barTabs.any { it.base == current }

    LaunchedEffect(deepLink.value) {
        val link = deepLink.value ?: return@LaunchedEffect
        deepLink.value = null
        // Старые виджеты и напоминания могут вести на удалённый экран (например, «focus») — тогда открываем «Главное».
        try {
            when {
                '?' !in link && tabs.any { it.route == link } -> nav.openTab(link)
                tabs.any { it.route == link.substringBefore('?') } -> nav.navigate(link) {
                    popUpTo(nav.graph.findStartDestination().id)
                    launchSingleTop = true
                }
                else -> nav.navigate(link)
            }
        } catch (_: IllegalArgumentException) {
            nav.openTab(Routes.TODAY)
        }
    }

    Column(Modifier.fillMaxSize().editorModeHold()) {
        // Полоса режима редактора — сверху, подальше от системных кнопок Android внизу.
        if (com.dasein.poryadok.data.UiTexts.editorMode.value) EditorModeStrip()
        // Под экранами вкладок стоит нижняя панель, и отступ от кнопок Android даёт она сама — экранам он не нужен,
        // иначе над панелью остаётся пустая полоса.
        NavHost(
            nav, startDestination = Routes.TODAY,
            modifier = Modifier.weight(1f).then(
                if (showBar) Modifier.consumeWindowInsets(androidx.compose.foundation.layout.WindowInsets.navigationBars) else Modifier,
            ),
        ) {
            composable(Routes.TODAY) { TodayScreen(nav, settings) }
            composable(Routes.TASKS) { TasksGoalsScreen(nav, 0) { nav.popBackStack() } }
            composable(
                "${Routes.CALENDAR}?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { CalendarScreen(nav, it.int("tab")) { nav.popBackStack() } }
            composable(
                "${Routes.FINANCE}?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { FinanceScreen(nav, settings, it.int("tab")) { nav.popBackStack() } }
            composable(
                "${Routes.PLAN}?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { PlanScreen(nav, it.int("tab")) }
            composable(Routes.HEALTH_HUB) { HealthHubScreen(nav) }
            composable(
                "${Routes.TOPS_HUB}?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { TopsHubScreen(nav, it.int("tab")) }
            composable(
                "media/{id}?kind={kind}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("kind") { type = NavType.IntType; defaultValue = 0 }),
            ) { MediaCardScreen(nav, it.long("id"), it.int("kind")) }
            composable(
                "${Routes.MEDIA_SEARCH}?kind={kind}",
                arguments = listOf(navArgument("kind") { type = NavType.IntType; defaultValue = 0 }),
            ) { MediaSearchScreen(nav, it.int("kind")) }
            composable("bodyDetail/{at}", arguments = listOf(navArgument("at") { type = NavType.LongType })) {
                com.dasein.poryadok.ui.health.BodyDetailScreen(nav, it.long("at"))
            }
            composable("kpImport?kind={kind}", arguments = listOf(navArgument("kind") { type = NavType.IntType; defaultValue = 0 })) {
                com.dasein.poryadok.ui.media.KpImportScreen(nav, it.int("kind"))
            }
            composable(Routes.WEIGHT_TREND) { com.dasein.poryadok.ui.health.WeightTrendScreen(nav) }
            composable(Routes.BODY_COMPARE) { com.dasein.poryadok.ui.health.BodyCompareScreen(nav) }
            composable(Routes.MORE) { MoreScreen(nav) }

            composable(
                "task/{id}?day={day}&goal={goal}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("day") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("goal") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { TaskEditScreen(nav, it.long("id"), it.long("day"), it.long("goal")) }
            composable(
                "event/{id}?day={day}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("day") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { EventEditScreen(nav, it.long("id"), it.long("day")) }
            composable(Routes.HABITS) { HabitsScreen(nav) }
            composable("habit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                HabitDetailScreen(nav, it.long("id"))
            }
            composable("habitEdit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                HabitEditScreen(nav, it.long("id"))
            }
            composable(Routes.GOALS) { TasksGoalsScreen(nav, 1) { nav.popBackStack() } }
            composable("goal/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                GoalScreen(nav, it.long("id"))
            }
            composable(
                "txn/{id}?income={income}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("income") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { TxnEditScreen(nav, it.long("id"), it.arguments?.getBoolean("income") ?: false, settings) }
            composable(
                "${Routes.HEALTH}?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { HealthScreen(nav, it.int("tab")) }
            composable(
                "${Routes.WELLBEING}?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { WellbeingScreen(nav, it.int("tab")) }
            composable(Routes.NOTES) { com.dasein.poryadok.ui.notes.PagesHome(nav) }
            composable("page/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                com.dasein.poryadok.ui.notes.PageScreen(nav, it.long("id"))
            }
            composable("holiday/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                com.dasein.poryadok.ui.calendar.HolidayScreen(nav, it.arguments?.getString("id") ?: "")
            }
            composable(Routes.BODY_SCIENCE) { com.dasein.poryadok.ui.health.BodyDetailScreen(nav, 0L, initialTab = 1) }
            composable(Routes.WIDGET_EDITOR) { com.dasein.poryadok.ui.more.WidgetEditorScreen(nav) }
            composable(Routes.WEATHER) { com.dasein.poryadok.ui.weather.WeatherScreen(nav) }
            composable(Routes.SMALL_TALK) { com.dasein.poryadok.ui.talk.SmallTalkScreen(nav) }
            composable(
                "${Routes.TRAINING}?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { com.dasein.poryadok.ui.training.TrainingScreen(nav, it.int("tab")) }
            composable("trainingPlan/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                com.dasein.poryadok.ui.training.PlanEditScreen(nav, it.long("id"))
            }
            composable("exercise/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                com.dasein.poryadok.ui.training.ExerciseScreen(nav, it.long("id"))
            }
            composable("session/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                com.dasein.poryadok.ui.training.SessionScreen(nav, it.long("id"))
            }
            composable("note/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                NoteEditScreen(nav, it.long("id"))
            }
            composable(Routes.TOPS) { TopsScreen(nav) }
            composable("top/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                TopScreen(nav, it.long("id"))
            }
            composable(Routes.WARDROBE) { WardrobeScreen(nav) }
            composable(Routes.OUTFITS) { OutfitsScreen(nav) }
            composable(Routes.WHEEL) { WheelScreen(nav) }
            composable(Routes.REVIEW) { ReviewScreen(nav, settings) }
            composable(Routes.SETTINGS) { SettingsScreen(nav, settings) }
            composable(Routes.BACKUP) { com.dasein.poryadok.ui.more.BackupScreen(nav) }
            composable(Routes.CRAFT) { com.dasein.poryadok.ui.craft.CraftHomeScreen(nav) }
            composable("craft/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                com.dasein.poryadok.ui.craft.CraftEditorScreen(nav, it.long("id"))
            }
            composable("craftWork/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                com.dasein.poryadok.ui.craft.CraftWorkScreen(nav, it.long("id"))
            }
            composable(Routes.CRAFT_STASH) { com.dasein.poryadok.ui.craft.CraftStashScreen(nav) }
            composable(Routes.CRAFT_MINE) { com.dasein.poryadok.ui.craft.CraftMineScreen(nav) }
            composable(Routes.CRAFT_PBN) { com.dasein.poryadok.ui.craft.PbnHomeScreen(nav) }
            composable(Routes.CRAFT_PENCILS) { com.dasein.poryadok.ui.craft.PencilsScreen(nav) }
            composable("craftPbn/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                com.dasein.poryadok.ui.craft.PbnScreen(nav, it.long("id"))
            }
            composable(com.dasein.poryadok.ui.tutor.TutorRoutes.HOME) { com.dasein.poryadok.ui.tutor.TutorHomeScreen(nav) }
            composable(com.dasein.poryadok.ui.tutor.TutorRoutes.WORDS) { com.dasein.poryadok.ui.tutor.TutorWordsScreen(nav) }
            composable(com.dasein.poryadok.ui.more.LIKES_ROUTE) { com.dasein.poryadok.ui.more.LikesScreen(nav) }
            composable(com.dasein.poryadok.ui.more.ICON_MANAGER_ROUTE) { com.dasein.poryadok.ui.more.IconManagerScreen(nav) }
            composable(com.dasein.poryadok.ui.recipes.WHAT_TO_COOK_ROUTE) { com.dasein.poryadok.ui.recipes.WhatToCookScreen(nav) }
            composable(com.dasein.poryadok.ui.media.MEDIA_LINK_ROUTE) { com.dasein.poryadok.ui.media.MediaLinkScreen(nav) }
            composable(com.dasein.poryadok.ui.tutor.TUTOR_CARDS_ROUTE) { com.dasein.poryadok.ui.tutor.TutorCardsScreen(nav) }
            composable(
                "${com.dasein.poryadok.ui.tutor.TUTOR_CHAT_ROUTE}?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { com.dasein.poryadok.ui.tutor.TutorChatScreen(nav, it.int("tab")) }
            composable(com.dasein.poryadok.ui.tutor.TutorRoutes.DICT) { com.dasein.poryadok.ui.tutor.TutorDictScreen(nav) }
            composable(
                "${com.dasein.poryadok.ui.tutor.TutorRoutes.TRANSLATE}?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { com.dasein.poryadok.ui.tutor.TranslatorScreen(nav, it.int("tab")) }
            composable(
                "tutorSession?mode={mode}&topic={topic}",
                arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "learn" }, navArgument("topic") { type = NavType.StringType; defaultValue = "" }),
            ) { com.dasein.poryadok.ui.tutor.TutorSessionScreen(nav, it.arguments?.getString("mode") ?: "learn", it.arguments?.getString("topic") ?: "") }
            composable(com.dasein.poryadok.ui.today.HOME_EDIT) { com.dasein.poryadok.ui.today.HomeEditScreen(nav) }
            composable(com.dasein.poryadok.ui.health.PressureRoutes.HOME) { com.dasein.poryadok.ui.health.PressureHomeScreen(nav) }
            composable(
                "pressureAdd?person={person}&id={id}",
                arguments = listOf(navArgument("person") { type = NavType.LongType; defaultValue = 0L }, navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { com.dasein.poryadok.ui.health.PressureAddScreen(nav, it.long("person"), it.long("id")) }
            composable("pressurePerson/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                com.dasein.poryadok.ui.health.PressurePersonScreen(nav, it.long("id"))
            }
            composable(Routes.SEARCH) { SearchScreen(nav) }

            composable(
                "${Routes.RECIPES}?tab={tab}&mode={mode}",
                arguments = listOf(
                    navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("mode") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { RecipesScreen(nav, it.int("tab"), it.int("mode"), embedded = true) }
            composable("recipe/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                RecipeDetailScreen(nav, it.long("id"))
            }
            composable("recipeEdit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                RecipeEditScreen(nav, it.long("id"))
            }
            composable(
                "${Routes.MENU_CREATE}?day={day}",
                arguments = listOf(navArgument("day") { type = NavType.LongType; defaultValue = 0L }),
            ) { CreateMenuScreen(nav, it.long("day")) }
            composable(Routes.PRESETS) { PresetsScreen(nav) }
            composable("preset/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                PresetEditScreen(nav, it.long("id"))
            }
            composable(Routes.SHOPPING) { ShoppingScreen(nav) }
            composable(Routes.COOK_HISTORY) { CookHistoryScreen(nav) }
            composable(Routes.STEPS) { StepsScreen(nav, settings) }
            composable(Routes.SBER) { SberScreen(nav, settings) }
            composable(Routes.FIN_NOTEBOOK) { NotebookScreen(nav, settings) }
            composable(com.dasein.poryadok.ui.finance.STATEMENT_ROUTE) { com.dasein.poryadok.ui.finance.StatementImportScreen(nav, settings) }
            composable(com.dasein.poryadok.ui.finance.FIN_AI_ROUTE) { com.dasein.poryadok.ui.finance.FinanceAiScreen(nav, settings) }
            composable(com.dasein.poryadok.ui.reader.READER_ROUTE) { com.dasein.poryadok.ui.reader.ReaderLibraryScreen(nav) }
            composable(com.dasein.poryadok.ui.reader.READER_NOTES_ROUTE) { com.dasein.poryadok.ui.reader.ReaderNotesScreen(nav) }
            composable("readerBook/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                com.dasein.poryadok.ui.reader.ReaderBookScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
            composable(com.dasein.poryadok.ui.more.APPEARANCE_ROUTE) { com.dasein.poryadok.ui.more.AppearanceScreen(nav, settings) }
            composable(com.dasein.poryadok.ui.more.UI_TEXTS_ROUTE) { com.dasein.poryadok.ui.more.UiTextsScreen(nav) }
            composable(com.dasein.poryadok.ui.finance.FIN_INFO_ROUTE) { com.dasein.poryadok.ui.finance.FinanceInfographicScreen(nav, settings) }
        }
        if (showBar) {
            var editTab by remember { mutableStateOf<Pair<Int, Tab>?>(null) }
            // В нижней панели долгое нажатие — своё меню вкладки (там же меняется иконка раздела).
            androidx.compose.runtime.CompositionLocalProvider(com.dasein.poryadok.ui.common.LocalIconEditable provides false) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    barTabs.forEachIndexed { i, t ->
                        TabButton(t, current == t.base, { nav.openTab(t.route) }) { editTab = i to t }
                    }
                }
            }
            editTab?.let { (i, t) -> TabMenu(nav, i, t) { editTab = null } }
        }
        com.dasein.poryadok.ui.common.IconEditHost()
        com.dasein.poryadok.ui.common.LabelEditHost()
        com.dasein.poryadok.ui.more.TextEditHost()
    }
}

/**
 * Удержание пальца на экране 3 секунды без движения включает режим редактора текстов. Касание только наблюдаем
 * (не забираем): нажатия, прокрутка и меню работают как обычно.
 */
private fun Modifier.editorModeHold(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        if (com.dasein.poryadok.data.UiTexts.editorMode.value) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop * 2
        val released = withTimeoutOrNull(3000L) {
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (ev.changes.size > 1) return@withTimeoutOrNull true
                val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!c.pressed) return@withTimeoutOrNull true
                if ((c.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
            }
        }
        if (released == null) {
            com.dasein.poryadok.data.UiTexts.setEditorMode(true)
            runCatching {
                android.widget.Toast.makeText(com.dasein.poryadok.Graph.app, "Режим редактора: удерживайте текст 1 секунду, чтобы изменить его", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** Полоса «Режим редактора» сверху экрана: напоминает, как править текст, и выключает режим. */
@Composable
private fun EditorModeStrip() {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = .14f))
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✎ Режим редактора: удерживайте текст 1 секунду", Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        androidx.compose.material3.TextButton(onClick = { com.dasein.poryadok.data.UiTexts.setEditorMode(false) }) { Text("Выключить", fontSize = 12.sp) }
    }
}

/** Кнопка нижней панели: нажатие — открыть раздел, долгое нажатие — сменить название и иконку. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RowScope.TabButton(t: Tab, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val look = com.dasein.poryadok.ui.common.rememberSection(t.route, t.label, t.icon)
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Изменить раздел"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(16.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) { Glyph(look.icon, 24.dp, dimmed = !selected) }
        Text(
            look.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
            color = if (selected) primary else com.dasein.poryadok.ui.theme.LocalExtra.current.dim, modifier = Modifier.padding(top = 4.dp),
        )
    }
}
