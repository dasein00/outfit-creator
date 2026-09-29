package com.dasein.poryadok.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
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
import com.dasein.poryadok.ui.productivity.FocusScreen
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
    const val FOCUS = "focus"
    const val HEALTH = "health"
    const val WELLBEING = "wellbeing"
    const val NOTES = "notes"
    const val TOPS = "tops"
    const val WARDROBE = "wardrobe"
    const val OUTFITS = "outfits"
    const val WHEEL = "wheel"
    const val REVIEW = "review"
    const val SETTINGS = "settings"
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

private data class Tab(val route: String, val label: String, val icon: String)

private val tabs = listOf(
    Tab(Routes.TODAY, "Главное", "ui:home"),
    Tab(Routes.RECIPES, "Рецепты", "food/00"),
    Tab(Routes.HEALTH_HUB, "Здоровье", "train/11"),
    Tab(Routes.PLAN, "График", "cal/00"),
    Tab(Routes.TOPS_HUB, "Топы", "sport/27"),
    Tab(Routes.MORE, "Ещё", "cal/40"),
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
    val showBar = tabs.any { it.route == current }

    LaunchedEffect(deepLink.value) {
        val link = deepLink.value ?: return@LaunchedEffect
        deepLink.value = null
        when {
            '?' !in link && tabs.any { it.route == link } -> nav.openTab(link)
            tabs.any { it.route == link.substringBefore('?') } -> nav.navigate(link) {
                popUpTo(nav.graph.findStartDestination().id)
                launchSingleTop = true
            }
            else -> nav.navigate(link)
        }
    }

    Column(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.weight(1f)) {
            composable(Routes.TODAY) { TodayScreen(nav, settings) }
            composable(Routes.TASKS) { TasksScreen(nav) { nav.popBackStack() } }
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
            composable(Routes.GOALS) { GoalsScreen(nav) }
            composable("goal/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                GoalScreen(nav, it.long("id"))
            }
            composable(Routes.FOCUS) { FocusScreen(nav, settings) }
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
            composable(Routes.BODY_SCIENCE) { com.dasein.poryadok.ui.health.BodyScienceScreen(nav) }
            composable(Routes.WIDGET_EDITOR) { com.dasein.poryadok.ui.more.WidgetEditorScreen(nav) }
            composable(Routes.WEATHER) { com.dasein.poryadok.ui.weather.WeatherScreen(nav) }
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
        }
        if (showBar) {
            var editTab by remember { mutableStateOf<Tab?>(null) }
            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                tabs.forEach { t ->
                    TabButton(t, current == t.route, { nav.openTab(t.route) }) { editTab = t }
                }
            }
            editTab?.let { t -> com.dasein.poryadok.ui.common.SectionEditDialog(t.route, t.label, t.icon) { editTab = null } }
        }
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
            color = if (selected) primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp),
        )
    }
}
