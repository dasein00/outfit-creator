package com.dasein.poryadok.ui.more

import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.HowTo
import androidx.compose.foundation.clickable
import com.dasein.poryadok.ui.common.Glyph
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BalanceWheel
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.HabitSchedule
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.AppIcon
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.Radar
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Stat
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.health.MOODS
import com.dasein.poryadok.ui.health.sleepMinutes
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette

private data class Hub(val icon: String, val title: String, val sub: String, val route: String)

private val HUB = listOf(
    Hub("ui:wallet", "Финансы", "операции, бюджеты, счета", Routes.FINANCE),
    Hub("sport/24", "Привычки", "серии и статистика", Routes.HABITS),
    Hub("ui:notebook", "Задачи", "списки и проекты", Routes.TASKS),
    Hub("sport/21", "Цели", "этапы и прогресс", Routes.GOALS),
    Hub("food/02", "Питание", "КБЖУ и калории", Routes.health(0)),
    Hub("ui:heart", "Давление и пульс", "дневник для вас и близких, нормы по возрасту, советы врачей", com.dasein.poryadok.ui.health.PressureRoutes.HOME),
    Hub("sport/11", "Вес и состав тела", "весы, метрики, тренд", Routes.health(1)),
    Hub("sport/00", "Тренировки", "программы, подходы, прогресс", Routes.training(0)),
    Hub("sport/19", "Шаги", "синхронизация с телефоном", Routes.STEPS),
    Hub("ui:smile", "Настроение", "дневник и инсайты", Routes.wellbeing(0)),
    Hub("sleep/00", "Сон", "автоопределение и режим", Routes.wellbeing(1)),
    Hub("ui:drop", "Вода", "норма на день", Routes.wellbeing(2)),
    Hub("habit/23", "Заметки", "страницы, статьи, картинки", Routes.NOTES),
    Hub("habit/01", "Фильмы, сериалы, книги", "коллекция и отзывы", Routes.topsHub(0)),
    Hub("ui:hanger", "Гардероб", "образы на манекене", Routes.WARDROBE),
    Hub("minimal/04", "Рукоделие", "алмазная мозаика, вышивка, бисер — схема из любого фото", Routes.CRAFT),
    Hub("ui:people", "Small Talks", "факты, истории и вопросы для разговора", Routes.SMALL_TALK),
    Hub("ui:wheel", "Колесо баланса", "8 сфер жизни", Routes.WHEEL),
    Hub("train/29", "Итоги недели", "всё в одном отчёте", Routes.REVIEW),
    Hub("cal/23", "Напоминалки", "по времени", Routes.calendar(1)),
    Hub("ui:bank", "Сбербанк", "операции из уведомлений", Routes.SBER),
    Hub("ui:notebook", "Тетрадь финансов", "импорт и месячные записи", Routes.FIN_NOTEBOOK),
    Hub("ui:v_chart", "Виджет", "что показывать на рабочем столе", Routes.WIDGET_EDITOR),
    Hub("ui:archive", "Резервное копирование", "всё или по разделам: данные, фото, видео, настройки", Routes.BACKUP),
    Hub("train/38", "Настройки", "тема, PIN, профиль", Routes.SETTINGS),
)

@Composable
fun MoreScreen(nav: NavHostController) {
    val extra = LocalExtra.current
    var editing by remember { mutableStateOf<Hub?>(null) }
    Screen(title = "Все разделы") { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad),
        ) {
            items(HUB) { h ->
                val look = com.dasein.poryadok.ui.common.rememberSection(h.route, h.title, h.icon)
                Tile(onClick = { nav.navigate(h.route) }, onLongClick = { editing = h }) {
                    Glyph(look.icon, 34.dp)
                    Gap(8.dp)
                    Text(look.name, fontWeight = FontWeight.SemiBold)
                    Text(h.sub, fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item(span = { GridItemSpan(2) }) {
                Hint("more_data", "Все данные хранятся только на этом телефоне. Делайте резервную копию в настройках.", Modifier.padding(8.dp), title = "Где хранятся данные")
            }
            item(span = { GridItemSpan(2) }) {
                Text("Удерживайте раздел, чтобы сменить его название и иконку.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(horizontal = 8.dp))
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { HowTo("more") }
        }
    }
    editing?.let { h -> com.dasein.poryadok.ui.common.SectionEditDialog(h.route, h.title, h.icon) { editing = null } }
}

val WHEEL_AREAS = listOf("Здоровье", "Карьера", "Финансы", "Отношения", "Друзья", "Развитие", "Отдых", "Духовность")

@Composable
fun WheelScreen(nav: NavHostController) {
    val extra = LocalExtra.current
    val wheels by observe(emptyList()) { Graph.dao.wheels() }
    val values = remember { mutableStateListOf(5f, 5f, 5f, 5f, 5f, 5f, 5f, 5f) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(wheels) {
        if (!initialized && wheels.isNotEmpty()) {
            wheels.first().scores.split(",").mapNotNull { it.toFloatOrNull() }.forEachIndexed { i, v -> if (i < 8) values[i] = v }
            initialized = true
        }
    }
    Screen(title = "Колесо баланса", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Hint("wheel_about", "Оцените каждую сферу от 0 до 10. Раз в месяц — и станет видно, куда уходит энергия и где нужен рост.", title = "Как пользоваться")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Radar(values.toList(), WHEEL_AREAS, MaterialTheme.colorScheme.primary, size = 300.dp)
            }
            WHEEL_AREAS.forEachIndexed { i, a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a, Modifier.fillMaxWidth(.34f), fontSize = 14.sp)
                    Slider(value = values[i], onValueChange = { values[i] = Math.round(it).toFloat() }, valueRange = 0f..10f, steps = 9, modifier = Modifier.weight(1f))
                    Text(values[i].toInt().toString(), Modifier.padding(start = 8.dp))
                }
            }
            val weakest = WHEEL_AREAS.withIndex().minByOrNull { values[it.index] }
            if (weakest != null) Tile {
                Text("Фокус на месяц: ${weakest.value}", fontWeight = FontWeight.SemiBold)
                Text("Подумайте об одном небольшом шаге в этой сфере и добавьте его как цель или привычку.", fontSize = 13.sp, color = extra.dim)
            }
            Gap()
            Button(onClick = {
                val scores = values.joinToString(",") { it.toInt().toString() }
                io { Graph.dao.upsertWheel(BalanceWheel(day = Dates.today(), scores = scores)) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить оценку") }
            if (wheels.isNotEmpty()) {
                SectionTitle("История")
                wheels.take(12).forEach { w ->
                    val s = w.scores.split(",").mapNotNull { it.toIntOrNull() }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(Dates.full(w.day), Modifier.weight(1f), fontSize = 13.sp)
                        Text("в среднем %.1f".format(s.average()), fontSize = 13.sp, color = extra.dim)
                    }
                }
            }
            HowTo("wheel")
            Gap(40.dp)
        }
    }
}

@Composable
fun ReviewScreen(nav: NavHostController, settings: Settings) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    val tasks by observe(emptyList()) { dao.tasks() }
    val habits by observe(emptyList()) { dao.habits() }
    val logs by observe(emptyList()) { dao.habitLogs() }
    val txns by observe(emptyList()) { dao.txns() }
    val moods by observe(emptyList()) { dao.moods() }
    val sleep by observe(emptyList()) { dao.sleep() }
    val workouts by observe(emptyList()) { dao.workouts() }
    val weights by observe(emptyList()) { dao.weights() }
    var offset by remember { mutableStateOf(0) }
    val today = Dates.today()
    val start = Dates.weekStart(today) - offset * 7L
    val end = start + 6
    val range = start..end
    val prevRange = (start - 7)..(start - 1)

    val doneTasks = tasks.filter { it.done && it.doneAt != null && Dates.dayOf(it.doneAt) in range }
    val scheduled = habits.sumOf { h -> range.count { d -> d <= today && HabitSchedule(h.daysMask, h.timesPerWeek).isScheduled(d) } }
    val hits = habits.sumOf { h -> logs.count { it.habitId == h.id && it.day in range && it.value >= h.target } }
    val spent = txns.filter { it.type == TxnType.EXPENSE && it.day in range }.sumOf { it.amount }
    val spentPrev = txns.filter { it.type == TxnType.EXPENSE && it.day in prevRange }.sumOf { it.amount }
    val income = txns.filter { it.type == TxnType.INCOME && it.day in range }.sumOf { it.amount }
    val weekMoods = moods.filter { it.day in range }
    val weekSleep = sleep.filter { it.day in range }
    val weekWorkouts = workouts.filter { it.day in range }
    val wStart = weights.lastOrNull { it.day < start }?.kg
    val wEnd = weights.lastOrNull { it.day <= end }?.kg

    Screen(title = "Итоги недели", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", fontSize = 28.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { offset++ }.padding(horizontal = 14.dp))
                    Text(
                        "${Dates.short(start)} — ${Dates.short(end)}", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text("›", fontSize = 28.sp, color = if (offset > 0) MaterialTheme.colorScheme.onSurface else extra.line,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = offset > 0) { offset-- }.padding(horizontal = 14.dp))
                }
                Gap()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${doneTasks.size}", plural(doneTasks.size, "задача", "задачи", "задач") + " сделано", Modifier.weight(1f))
                    Stat(if (scheduled == 0) "—" else "${hits * 100 / scheduled}%", "привычки", Modifier.weight(1f), extra.ok)
                }
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${weekWorkouts.size}", "тренировок", Modifier.weight(1f))
                    Stat("${weekMoods.size}", "отметок настроения", Modifier.weight(1f))
                }
                SectionTitle("Деньги")
                Tile {
                    Row { Text("Расходы", Modifier.weight(1f)); Text(Money.format(spent, settings.currency), fontWeight = FontWeight.SemiBold) }
                    Row { Text("Доходы", Modifier.weight(1f)); Text(Money.format(income, settings.currency), color = extra.ok) }
                    if (spentPrev > 0) {
                        val d = ((spent - spentPrev) / spentPrev * 100).toInt()
                        Text(
                            if (d <= 0) "На ${-d}% меньше, чем на прошлой неделе" else "На $d% больше, чем на прошлой неделе",
                            fontSize = 12.sp, color = if (d <= 0) extra.ok else extra.warn, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                SectionTitle("Самочувствие")
                Tile {
                    if (weekMoods.isNotEmpty()) {
                        val avg = weekMoods.map { it.level }.average()
                        Text("Настроение: ${MOODS[(avg.toInt() - 1).coerceIn(0, 4)].lowercase()}, %.1f из 5".format(avg))
                    }
                    if (weekSleep.isNotEmpty()) {
                        val avg = weekSleep.map { sleepMinutes(it) }.average().toInt()
                        Text("Сон: в среднем ${avg / 60} ч ${avg % 60} мин")
                    }
                    if (wStart != null && wEnd != null) Text("Вес: ${"%.1f".format(wEnd)} кг (${if (wEnd - wStart >= 0) "+" else ""}${"%.1f".format(wEnd - wStart)})")
                    if (weekMoods.isEmpty() && weekSleep.isEmpty() && wEnd == null) Text("Записей о самочувствии нет", color = extra.dim)
                }
                SectionTitle("Привычки")
            }
            items(habits, key = { it.id }) { h ->
                val sched = HabitSchedule(h.daysMask, h.timesPerWeek)
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(h.emoji, 16.dp)
                    Text("  ${h.name}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    range.forEach { d ->
                        val ok = logs.any { it.habitId == h.id && it.day == d && it.value >= h.target }
                        Text(
                            if (ok) "●" else if (sched.isScheduled(d) && d <= today) "○" else "·",
                            color = if (ok) Palette.item(h.color) else extra.dim, modifier = Modifier.padding(horizontal = 3.dp),
                        )
                    }
                }
            }
            item {
                SectionTitle("Сделано за неделю")
                if (doneTasks.isEmpty()) Text("Пока пусто", color = extra.dim)
                doneTasks.take(20).forEach { Text("✓ ${it.title}", modifier = Modifier.padding(vertical = 2.dp)) }
            }
            item { HowTo("review") }
        }
    }
}

@Composable
fun SearchScreen(nav: NavHostController) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val tasks by observe(emptyList()) { dao.tasks() }
    val pages by observe(emptyList()) { Graph.pages.pages() }
    val pageText by observe(emptyList()) { Graph.pages.textBlocks() }
    val events by observe(emptyList()) { dao.events() }
    val goals by observe(emptyList()) { dao.goals() }
    val txns by observe(emptyList()) { dao.txns() }
    val tops by observe(emptyList()) { dao.topItems() }
    Screen(title = "Поиск", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 12.dp)) {
            OutlinedTextField(
                q, { q = it }, placeholder = { Text("Задачи, заметки, события, операции…") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus), shape = RoundedCornerShape(14.dp),
            )
            if (q.length < 2) {
                Text("Введите хотя бы 2 символа", color = extra.dim, modifier = Modifier.padding(12.dp))
                return@Screen
            }
            fun String.hit() = contains(q, ignoreCase = true)
            val results = buildList {
                tasks.filter { it.title.hit() || it.note.hit() }.forEach { add(Triple("ui:check|" + it.title, if (it.done) "выполнена" else "задача", Routes.task(it.id))) }
                val textHits = pageText.filter { it.text.hit() }.map { it.pageId }.toSet()
                pages.filter { !it.archived && (it.title.hit() || it.id in textHits) }.forEach { add(Triple("ui:notebook|" + it.title.ifBlank { "Без названия" }, "заметка", Routes.page(it.id))) }
                events.filter { it.title.hit() || it.note.hit() || it.location.hit() }.forEach { add(Triple("cal/00|" + it.title, Dates.label(it.day), Routes.event(it.id))) }
                goals.filter { it.title.hit() || it.why.hit() }.forEach { add(Triple("sport/21|" + it.title, "цель", Routes.goal(it.id))) }
                txns.filter { it.note.hit() }.take(20).forEach { add(Triple("ui:wallet|" + it.note, Dates.label(it.day) + " · " + it.amount.toInt(), Routes.txn(it.id))) }
                tops.filter { it.title.hit() || it.note.hit() }.forEach { add(Triple("ui:trophy|" + it.title, "в топе", Routes.top(it.listId))) }
            }
            if (results.isEmpty()) Text("Ничего не найдено", color = extra.dim, modifier = Modifier.padding(12.dp))
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(results) { (title, sub, route) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.navigate(route) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(title.substringBefore('|'), 20.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(title.substringAfter('|'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sub, fontSize = 12.sp, color = extra.dim)
                        }
                    }
                }
                item { HowTo("search") }
            }
        }
    }
}
