@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui

import com.dasein.poryadok.ui.common.FitText
import androidx.compose.foundation.background
import com.dasein.poryadok.ui.common.HowTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import kotlinx.coroutines.flow.map
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Energy
import com.dasein.poryadok.ui.calendar.CalendarScreen
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.health.sleepMinutes
import com.dasein.poryadok.ui.media.MediaListScreen
import com.dasein.poryadok.ui.notes.TopsScreen
import com.dasein.poryadok.ui.productivity.TasksScreen
import com.dasein.poryadok.ui.recipes.rememberNutritionPlan
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette

/** Вкладка с переключателем сверху; вложенный экран не получает отступ под статус-бар повторно. */
@Composable
private fun TabbedHost(options: List<Pair<Int, String>>, tab: Int, onTab: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Segments(options, tab, onTab, Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp))
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) { content(tab) }
    }
}

/** «График»: календарь, задачи и заметки (цели — в разделе «Ещё»). */
@Composable
fun PlanScreen(nav: NavHostController, initialTab: Int) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    TabbedHost(listOf(0 to "Календарь", 1 to "Задачи", 2 to "Заметки"), tab, { tab = it }) { t ->
        when (t) {
            0 -> CalendarScreen(nav, 0)
            1 -> TasksScreen(nav)
            else -> com.dasein.poryadok.ui.notes.PagesHome(nav, embedded = true)
        }
    }
}

/** «Топы»: коллекция фильмов, сериалов, книг и свои рейтинги. */
@Composable
fun TopsHubScreen(nav: NavHostController, initialTab: Int) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    TabbedHost(listOf(0 to "Фильмы", 1 to "Сериалы", 2 to "Книги", 3 to "Топы"), tab, { tab = it }) { t ->
        when (t) {
            0 -> MediaListScreen(nav, MediaKind.MOVIE)
            1 -> MediaListScreen(nav, MediaKind.SERIES)
            2 -> MediaListScreen(nav, MediaKind.BOOK)
            else -> TopsScreen(nav, embedded = true)
        }
    }
}

private data class HealthTile(val icon: String, val title: String, val value: String, val sub: String, val progress: Float?, val route: String)

/** «Здоровье»: сон, вес, шаги, тренировки, вода и питание на одном экране. */
@Composable
fun HealthHubScreen(nav: NavHostController) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    val today = Dates.today()
    val food by observe(emptyList()) { dao.food() }
    val dayLogs by observe(emptyList()) { dao.dayLogs() }
    val weights by observe(emptyList()) { dao.weights() }
    val workouts by observe(emptyList()) { dao.workouts() }
    val sleep by observe(emptyList()) { dao.sleep() }
    val profile by observe(null) { dao.profile() }
    val energy by observe(emptyList()) { Graph.extra.dayEnergy() }
    val plan = rememberNutritionPlan()
    val log = dayLogs.firstOrNull { it.day == today }
    val eaten = food.filter { it.day == today }.sumOf { it.kcal }
    val steps = log?.steps ?: 0
    val stepsGoal = profile?.stepsGoal ?: 8000
    val waterGoal = profile?.waterGoalMl ?: 2000
    val weight = weights.lastOrNull()
    val pace by observe(4.8) { Graph.prefs.settings.map { it.walkPace } }
    val burned = Energy.burned(steps, weight?.kg ?: profile?.startWeight ?: 70.0, workouts.filter { it.day == today }.sumOf { it.kcal }, energy.firstOrNull { it.day == today }?.activeKcal, profile?.heightCm, pace)
    val lastSleep = sleep.maxByOrNull { it.day }?.takeIf { it.day >= today - 1 }
    val weekStart = Dates.weekStart(today)
    val pctx = androidx.compose.ui.platform.LocalContext.current
    val diary by com.dasein.poryadok.system.PressureStore.flow(pctx).collectAsState()
    val bp = diary?.let { d -> d.readings.filter { it.personId == (d.people.firstOrNull { p -> p.id == d.current } ?: d.people.firstOrNull())?.id }.maxByOrNull { it.time } }
    val tiles = listOf(
        HealthTile("d08/00", "Давление", bp?.let { "${it.sys}/${it.dia}" } ?: "—", bp?.let { com.dasein.poryadok.logic.Pressure.category(it.sys, it.dia).short.lowercase() } ?: "дневник для семьи", null, com.dasein.poryadok.ui.health.PressureRoutes.HOME),
        HealthTile("d08/08", "Питание", "$eaten ккал", "из ${plan.targetKcal}", eaten / plan.targetKcal.coerceAtLeast(1).toFloat(), Routes.health(0)),
        HealthTile("d08/06", "Сон", lastSleep?.let { "${sleepMinutes(it) / 60} ч ${sleepMinutes(it) % 60} м" } ?: "—",
            lastSleep?.let { "${Dates.time(it.bedMin)} → ${Dates.time(it.wakeMin)}" } ?: "нет записи", lastSleep?.let { sleepMinutes(it) / (profile?.sleepGoalMin ?: 480).toFloat() }, Routes.wellbeing(1)),
        HealthTile("d08/02", "Вес", weight?.let { "%.1f кг".format(it.kg).replace('.', ',') } ?: "—", weight?.let { Dates.label(it.day) } ?: "нет взвешиваний", null, Routes.health(1)),
        HealthTile("d08/04", "Шаги", "$steps", "из $stepsGoal", steps / stepsGoal.coerceAtLeast(1).toFloat(), Routes.STEPS),
        HealthTile("d08/09", "Тренировки", "${workouts.count { it.day >= weekStart }}", "на этой неделе", null, Routes.training(0)),
        HealthTile("d08/05", "Вода", "${log?.waterMl ?: 0} мл", "из $waterGoal", (log?.waterMl ?: 0) / waterGoal.toFloat(), Routes.wellbeing(2)),
        HealthTile("d08/03", "Замеры", "см", "талия, бёдра…", null, Routes.health(2)),
        HealthTile("d08/07", "Настроение", "дневник", "и инсайты", null, Routes.wellbeing(0)),
        HealthTile("d08/10", "Фото прогресса", "до/после", "анфас, профиль", null, Routes.health(5)),
    )
    var editing by remember { mutableStateOf<HealthTile?>(null) }
    editing?.let { t -> com.dasein.poryadok.ui.common.SectionEditDialog(t.route, t.title, t.icon) { editing = null } }
    Screen(title = "Здоровье") { pad ->
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.padding(pad),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Tile(padding = 12.dp) {
                    Text("Баланс за сегодня", fontSize = 12.sp, color = extra.dim)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            FitText("$eaten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("съедено, ккал", fontSize = 11.sp, color = extra.dim)
                        }
                        Column(Modifier.weight(1f)) {
                            FitText("${burned.total}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Palette.item(0))
                            Text("сожжено, ккал", fontSize = 11.sp, color = extra.dim)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            FitText("${plan.targetKcal}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("цель, ккал", fontSize = 11.sp, color = extra.dim)
                        }
                    }
                }
            }
            items(tiles) { t ->
                val look = com.dasein.poryadok.ui.common.rememberSection(t.route, t.title, t.icon)
                Tile(onClick = { nav.navigate(t.route) }, padding = 12.dp, onLongClick = { editing = t }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(look.icon, 22.dp)
                        Text("  " + look.name, fontSize = 13.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(t.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
                    Text(t.sub, fontSize = 11.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    t.progress?.let { Bar(it, extra.ok, Modifier.padding(top = 6.dp), height = 5.dp) }
                }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { HowTo("health_hub") }
        }
    }
}
