package com.dasein.poryadok.ui.more

import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.HowTo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.height
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.dasein.poryadok.ui.common.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import com.dasein.poryadok.ui.common.Text
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

data class Hub(val icon: String, val title: String, val sub: String, val route: String)

val HUB = listOf(
    Hub("d06/00", "Финансы", "операции, бюджеты, счета", Routes.FINANCE),
    Hub("d04/00", "Привычки", "серии и статистика", Routes.HABITS),
    Hub("d03/00", "Задачи и цели", "списки, проекты, цели и этапы", Routes.TASKS),
    Hub("d11/00", "Питание", "КБЖУ и калории", Routes.health(0)),
    Hub("d08/00", "Давление и пульс", "дневник для вас и близких, нормы по возрасту, советы врачей", com.dasein.poryadok.ui.health.PressureRoutes.HOME),
    Hub("d08/02", "Вес и состав тела", "весы, метрики, тренд", Routes.health(1)),
    Hub("d08/09", "Тренировки", "программы, подходы, прогресс", Routes.training(0)),
    Hub("d08/04", "Шаги", "синхронизация с телефоном", Routes.STEPS),
    Hub("d08/06", "Сон", "автоопределение и режим", Routes.wellbeing(1)),
    Hub("d08/05", "Вода", "норма на день", Routes.wellbeing(2)),
    Hub("d18/00", "Заметки", "страницы, статьи, картинки", Routes.NOTES),
    Hub("d17/00", "Фильмы, сериалы, книги", "коллекция и отзывы", Routes.topsHub(0)),
    Hub("books/08", "Читалка", "EPUB, FB2, MOBI, PDF, DjVu: закладки, заметки, чтение вслух", com.dasein.poryadok.ui.reader.READER_ROUTE),
    Hub("d19/00", "Гардероб", "образы на манекене", Routes.WARDROBE),
    Hub("d20/00", "Репетитор", "английский: слова на каждый день, тренировки, повторение", com.dasein.poryadok.ui.tutor.TutorRoutes.HOME),
    Hub("d20/04", "Переводчик", "в сети переводит ИИ, без интернета — словарь и модель на телефоне", com.dasein.poryadok.ui.tutor.TutorRoutes.translate(0)),
    Hub("d21/00", "Рукоделие", "алмазная мозаика, вышивка, бисер — схема из любого фото", Routes.CRAFT),
    Hub("d05/14", "Понравившееся", "отмеченные «+» события, факты, праздники — выгрузка файлом", LIKES_ROUTE),
    Hub("d22/00", "Small Talks", "факты, истории и вопросы для разговора", Routes.SMALL_TALK),
    Hub("d02/01", "Иконки", "папки, свои наборы, порядок, правка и замены", ICON_MANAGER_ROUTE),
    Hub("d22/06", "Итоги недели", "всё в одном отчёте", Routes.REVIEW),
    Hub("d05/01", "Напоминалки", "по времени", Routes.calendar(1)),
    Hub("d02/07", "Сбербанк", "операции из уведомлений", Routes.SBER),
    Hub("d06/13", "Тетрадь финансов", "импорт и месячные записи", Routes.FIN_NOTEBOOK),
    Hub("d02/06", "Виджет", "что показывать на рабочем столе", Routes.WIDGET_EDITOR),
    Hub("d02/04", "Резервное копирование", "всё или по разделам: данные, фото, видео, настройки", Routes.BACKUP),
    Hub("d02/00", "Настройки", "тема, PIN, профиль", Routes.SETTINGS),
)

/** Порядок плиток «Все разделы»: сохранённый пользователем (перемещение пальцем), новые разделы — в конце. */
private const val ORDER_KEY = "more_order"

private fun hubOrder(ctx: android.content.Context): List<Hub> {
    val saved = ctx.getSharedPreferences("ui_state", android.content.Context.MODE_PRIVATE).getString(ORDER_KEY, null)
        ?.split('|')?.filter { it.isNotBlank() } ?: return HUB
    return saved.mapNotNull { r -> HUB.firstOrNull { it.route == r } } + HUB.filter { it.route !in saved }
}

@Composable
fun MoreScreen(nav: NavHostController) {
    val extra = LocalExtra.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var editing by remember { mutableStateOf<Hub?>(null) }
    val order = remember { mutableStateListOf<Hub>().apply { addAll(hubOrder(ctx)) } }
    /** Раздел, который сейчас переносят пальцем («Изменить раздел» → «Переместить»). */
    var moving by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Все плитки одной высоты: место под название в две строки и подпись — даже если название короткое.
    val tileH = with(density) { 74.dp + (60.sp).toDp() }
    fun saveOrder() = ctx.getSharedPreferences("ui_state", android.content.Context.MODE_PRIVATE).edit()
        .putString(ORDER_KEY, order.joinToString("|") { it.route }).apply()
    /** Центр переносимой плитки над другой плиткой — меняем их местами, плитка остаётся под пальцем. */
    fun trySwap(route: String) {
        val items = grid.layoutInfo.visibleItemsInfo
        val self = items.firstOrNull { it.key == route } ?: return
        val cx = self.offset.x + self.size.width / 2f + drag.x
        val cy = self.offset.y + self.size.height / 2f + drag.y
        val target = items.firstOrNull { it.key != route && it.key is String && order.any { o -> o.route == it.key } &&
            cx >= it.offset.x && cx <= it.offset.x + it.size.width && cy >= it.offset.y && cy <= it.offset.y + it.size.height } ?: return
        val from = order.indexOfFirst { it.route == route }
        val to = order.indexOfFirst { it.route == target.key }
        if (from < 0 || to < 0) return
        // Лента держится за первую видимую плитку: если переставляется она, возвращаем прокрутку как была.
        val first = items.minByOrNull { it.index }?.key
        if (first == route || first == target.key) {
            val fi = grid.firstVisibleItemIndex
            val fo = grid.firstVisibleItemScrollOffset
            scope.launch { grid.scrollToItem(fi, fo) }
        }
        order.add(to, order.removeAt(from))
        drag -= androidx.compose.ui.geometry.Offset((target.offset.x - self.offset.x).toFloat(), (target.offset.y - self.offset.y).toFloat())
    }
    /** Палец с плиткой у верхнего или нижнего края — лента сама едет туда, тем быстрее, чем ближе к краю. */
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(moving, dragging) {
        val route = moving ?: return@LaunchedEffect
        if (!dragging) return@LaunchedEffect
        val edge = with(density) { 96.dp.toPx() }
        val maxStep = with(density) { 18.dp.toPx() }
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val info = grid.layoutInfo
            val self = info.visibleItemsInfo.firstOrNull { it.key == route } ?: continue
            val y = self.offset.y + self.size.height / 2f + drag.y
            val h = info.viewportSize.height.toFloat()
            val step = when {
                y < edge -> -maxStep * ((edge - y) / edge).coerceIn(0f, 1f)
                y > h - edge -> maxStep * ((y - (h - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step == 0f) continue
            // Прокрутку могла перебить перестановка плиток — это не конец переноса.
            val used = try { grid.scrollBy(step) } catch (e: kotlinx.coroutines.CancellationException) { if (!isActive) throw e; 0f }
            if (used == 0f) continue
            // Лента уехала — плитку сдвигаем обратно под палец и проверяем, над чем она теперь.
            drag += androidx.compose.ui.geometry.Offset(0f, used)
            trySwap(route)
        }
    }
    Screen(title = "Все разделы") { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = grid,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad),
        ) {
            item(key = "search", span = { GridItemSpan(2) }) { SectionSearchBar(nav, Modifier.padding(bottom = 4.dp)) }
            if (moving != null) item(key = "moving_hint", span = { GridItemSpan(2) }) {
                Tile(color = MaterialTheme.colorScheme.primary.copy(alpha = .14f), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Зажмите выделенный раздел и перетащите на новое место. Отпустите — положение сохранится.", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        com.dasein.poryadok.ui.common.TextButton(onClick = { moving = null; drag = androidx.compose.ui.geometry.Offset.Zero }) { Text(btnLabel("Готово")) }
                    }
                }
            }
            items(order, key = { it.route }) { h ->
                val look = com.dasein.poryadok.ui.common.rememberSection(h.route, h.title, h.icon)
                val isMoving = moving == h.route
                val mod = if (!isMoving) Modifier.height(tileH) else Modifier.height(tileH)
                    .zIndex(1f)
                    .graphicsLayer { translationX = drag.x; translationY = drag.y; scaleX = 1.05f; scaleY = 1.05f; shadowElevation = 12f }
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(com.dasein.poryadok.ui.theme.LocalLook.current.cardRound.dp))
                    .pointerInput(h.route) {
                        fun finish() { dragging = false; drag = androidx.compose.ui.geometry.Offset.Zero; saveOrder(); moving = null }
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                        ) { change, amount ->
                            change.consume()
                            drag += amount
                            trySwap(h.route)
                        }
                    }
                Tile(
                    mod,
                    onClick = if (moving == null) ({ nav.navigate(h.route) }) else null,
                    onLongClick = if (moving == null) ({ editing = h }) else null,
                ) {
                    Glyph(look.icon, 34.dp)
                    Gap(8.dp)
                    Text(look.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                    Text(h.sub, fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item(span = { GridItemSpan(2) }) {
                Hint("more_data", "Все данные хранятся только на этом телефоне. Делайте резервную копию в настройках.", Modifier.padding(8.dp), title = "Где хранятся данные")
            }
            item(span = { GridItemSpan(2) }) {
                Text("Удерживайте раздел, чтобы сменить его название и иконку или переместить его. Удерживайте кнопку нижней панели, чтобы заменить её любым разделом.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(horizontal = 8.dp))
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { HowTo("more") }
        }
    }
    editing?.let { h ->
        com.dasein.poryadok.ui.common.SectionEditDialog(h.route, h.title, h.icon, onMove = { moving = h.route; drag = androidx.compose.ui.geometry.Offset.Zero }) { editing = null }
    }
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
            }, modifier = Modifier.fillMaxWidth()) { Text(btnLabel("Сохранить оценку")) }
            if (wheels.isNotEmpty()) {
                SectionTitle("История")
                wheels.take(12).forEach { w ->
                    val s = w.scores.split(",").mapNotNull { it.toIntOrNull() }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(Dates.full(w.day), Modifier.weight(1f), fontSize = 13.sp)
                        Text(btnLabel("в среднем %.1f").format(s.average()), fontSize = 13.sp, color = extra.dim)
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
                    Text(btnLabel("‹"), fontSize = 28.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { offset++ }.padding(horizontal = 14.dp))
                    Text(
                        "${Dates.short(start)} — ${Dates.short(end)}", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(btnLabel("›"), fontSize = 28.sp, color = if (offset > 0) MaterialTheme.colorScheme.onSurface else extra.line,
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
                    Stat("${weekSleep.size}", "ночей со сном", Modifier.weight(1f))
                }
                SectionTitle("Деньги")
                Tile {
                    Row { Text(btnLabel("Расходы"), Modifier.weight(1f)); Text(Money.format(spent, settings.currency), fontWeight = FontWeight.SemiBold) }
                    Row { Text(btnLabel("Доходы"), Modifier.weight(1f)); Text(Money.format(income, settings.currency), color = extra.ok) }
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
                    if (weekSleep.isNotEmpty()) {
                        val avg = weekSleep.map { sleepMinutes(it) }.average().toInt()
                        Text("Сон: в среднем ${avg / 60} ч ${avg % 60} мин")
                    }
                    if (wStart != null && wEnd != null) Text("Вес: ${"%.1f".format(wEnd)} кг (${if (wEnd - wStart >= 0) "+" else ""}${"%.1f".format(wEnd - wStart)})")
                    if (weekSleep.isEmpty() && wEnd == null) Text(btnLabel("Записей о самочувствии нет"), color = extra.dim)
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
                if (doneTasks.isEmpty()) Text(btnLabel("Пока пусто"), color = extra.dim)
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
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                q, { q = it }, placeholder = { Text(btnLabel("Раздел, задача, заметка, операция…")) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus), shape = RoundedCornerShape(14.dp),
            )
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val sections = remember(q) { SectionIndex.search(ctx, q, limit = if (q.length < 2) 12 else 6) }
            if (q.isBlank()) {
                Text(btnLabel("Начните вводить — ниже появятся разделы и подразделы, а с 2 букв ещё задачи, заметки, события и операции."), color = extra.dim, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                return@Screen
            }
            if (q.length < 2) {
                Column(Modifier.padding(top = 8.dp)) { sections.forEach { DestRow(nav, it) } }
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
            if (results.isEmpty() && sections.isEmpty()) Text(btnLabel("Ничего не найдено"), color = extra.dim, modifier = Modifier.padding(12.dp))
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                if (sections.isNotEmpty()) {
                    item { Text(btnLabel("Разделы"), fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) }
                    items(sections) { DestRow(nav, it) }
                    if (results.isNotEmpty()) item { Text(btnLabel("Записи"), fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(start = 8.dp, top = 10.dp)) }
                }
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
