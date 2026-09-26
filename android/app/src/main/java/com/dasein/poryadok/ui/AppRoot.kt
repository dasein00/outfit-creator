package com.dasein.poryadok.ui

import androidx.compose.foundation.layout.Column
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
}

private data class Tab(val route: String, val label: String, val icon: Int)

private val tabs = listOf(
    Tab(Routes.TODAY, "Сегодня", Ic.sun),
    Tab(Routes.TASKS, "Задачи", Ic.check),
    Tab(Routes.CALENDAR, "Календарь", Ic.calendar),
    Tab(Routes.FINANCE, "Финансы", Ic.wallet),
    Tab(Routes.MORE, "Ещё", Ic.grid),
)

private fun NavBackStackEntry.long(name: String): Long = arguments?.getLong(name) ?: -1L
private fun NavBackStackEntry.int(name: String): Int = arguments?.getInt(name) ?: 0

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
            composable(Routes.TASKS) { TasksScreen(nav) }
            composable(
                "${Routes.CALENDAR}?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { CalendarScreen(nav, it.int("tab")) }
            composable(
                "${Routes.FINANCE}?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { FinanceScreen(nav, settings, it.int("tab")) }
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
            composable(Routes.NOTES) { NotesScreen(nav) }
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
            ) { RecipesScreen(nav, it.int("tab"), it.int("mode")) }
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
            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { nav.openTab(t.route) },
                        icon = { AppIcon(t.icon, 24.dp, dimmed = current != t.route) },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
}
