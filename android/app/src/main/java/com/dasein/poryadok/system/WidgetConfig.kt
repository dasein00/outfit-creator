package com.dasein.poryadok.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Energy
import com.dasein.poryadok.logic.HabitSchedule
import com.dasein.poryadok.logic.Nutrition
import com.dasein.poryadok.logic.WeatherLogic
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.health.input
import com.dasein.poryadok.ui.health.sleepMinutes
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Один блок на виджете: тип, вид (0 — текст, 1 — полоса или график), количество строк (для задач и событий)
 * и оформление: своя подпись, цвета значения и подписи (0 — как в теме), размер текста, показывать ли иконку.
 */
@Serializable
data class WidgetBlock(
    val type: String,
    val style: Int = 0,
    val count: Int = 2,
    val on: Boolean = true,
    val label: String = "",
    val valueColor: Long = 0,
    val labelColor: Long = 0,
    val scale: Float = 1f,
    val icon: Boolean = true,
    /** Для погоды: какие подробности показывать (ключи WEATHER_FIELDS через запятую; пусто — набор по умолчанию, «-» — ничего). */
    val fields: String = "",
) {
    fun weatherFields(): List<String> = when (fields) {
        "" -> WEATHER_DEFAULT_FIELDS
        "-" -> emptyList()
        else -> fields.split(',').filter { k -> WEATHER_FIELDS.any { it.first == k } }
    }
}

/** Подробности погоды на виджете: ключ и название в настройках. */
val WEATHER_FIELDS = listOf(
    "pop" to "Вероятность осадков", "feels" to "Ощущается как", "minmax" to "Мин. и макс. за день", "humidity" to "Влажность",
    "gust" to "Порывы ветра", "pressure" to "Давление", "uv" to "УФ-индекс", "sun" to "Восход и закат", "next" to "Погода через 3 часа",
)
val WEATHER_DEFAULT_FIELDS = listOf("pop", "feels", "minmax", "humidity", "gust", "pressure")

/**
 * Как выглядит виджет: тема, заголовок, кольцо шагов слева, крупный шрифт и блоки справа в выбранном порядке.
 * Цвета хранятся как ARGB; 0 — взять из темы.
 */
@Serializable
data class WidgetConfig(
    val theme: Int = 0,
    val header: Boolean = true,
    val ring: Boolean = true,
    val large: Boolean = false,
    val blocks: List<WidgetBlock> = DEFAULT_BLOCKS,
    val title: String = "Сегодня",
    val showDate: Boolean = true,
    val titleScale: Float = 1f,
    val textScale: Float = 1f,
    val bgColor: Long = 0,
    val bgAlpha: Int = 100,
    val textColor: Long = 0,
    val dimColor: Long = 0,
    val accentColor: Long = 0,
    val goodColor: Long = 0,
    val badColor: Long = 0,
    val radius: Int = 20,
    val ringScale: Float = 1f,
    val ringColor: Long = 0,
    val ringDoneColor: Long = 0,
    val ringTrackColor: Long = 0,
    val ringStepsColor: Long = 0,
    val ringKcalColor: Long = 0,
    val ringGoal: Boolean = true,
    val kcalLabel: String = "ккал",
    /** Версия настроек — чтобы один раз включить новые блоки (погоду) у тех, кто уже настроил виджет. */
    val rev: Int = 0,
) {
    /**
     * Все известные блоки: сохранённые в выбранном порядке, затем новые — выключенными.
     * Версия 2: вес с графиком по умолчанию убран, погода — первой и включена (вес можно вернуть в редакторе).
     */
    fun normalized(): WidgetConfig {
        val known = WIDGET_BLOCK_TYPES.map { it.type }
        var kept = blocks.filter { it.type in known }.distinctBy { it.type }
        if (rev < 1 && kept.none { it.type == "weather" }) kept = listOf(WidgetBlock("weather", 0)) + kept
        if (rev < 2) {
            val weather = kept.firstOrNull { it.type == "weather" }?.copy(on = true) ?: WidgetBlock("weather", 0)
            kept = listOf(weather) + kept.filter { it.type != "weather" }.map { if (it.type == "weight") it.copy(on = false) else it }
        }
        val missing = WIDGET_BLOCK_TYPES.filter { t -> kept.none { it.type == t.type } }.map { WidgetBlock(it.type, on = false) }
        return copy(blocks = kept + missing, rev = 2)
    }

    /** Итоговые цвета с учётом темы и своих настроек. */
    fun colors(): WColors {
        val base = when (theme) {
            1 -> WColors(0xFFF6F1E7.toInt(), 0xFF211D18.toInt(), 0xFF7A7064.toInt(), 0xFFE2D8C6.toInt())
            2 -> WColors(0x99141210.toInt(), 0xFFF0ECE3.toInt(), 0xFFD5CDBF.toInt(), 0x55FFFFFF)
            else -> WColors(0xFF211D18.toInt(), 0xFFF0ECE3.toInt(), 0xFFA79E90.toInt(), 0xFF3D362C.toInt())
        }
        fun pick(c: Long, def: Int) = if (c == 0L) def else c.toInt()
        val bg0 = pick(bgColor, base.bg)
        val alpha = if (bgColor == 0L && bgAlpha == 100) (bg0 ushr 24) else (bgAlpha.coerceIn(0, 100) * 255 / 100)
        return base.copy(
            bg = (alpha shl 24) or (bg0 and 0xFFFFFF),
            text = pick(textColor, base.text), dim = pick(dimColor, base.dim),
            accent = pick(accentColor, base.accent), good = pick(goodColor, base.good), bad = pick(badColor, base.bad),
        )
    }

    fun ringStyle(): RingStyle {
        val c = colors()
        return RingStyle(
            progress = if (ringColor == 0L) 0xFFE0A04A.toInt() else ringColor.toInt(),
            done = if (ringDoneColor == 0L) c.good else ringDoneColor.toInt(),
            track = if (ringTrackColor == 0L) (if (theme == 1) 0xFFE2D8C6.toInt() else 0xFF3D362C.toInt()) else ringTrackColor.toInt(),
            steps = if (ringStepsColor == 0L) (if (theme == 1) 0xFF211D18.toInt() else 0xFFFFFFFF.toInt()) else ringStepsColor.toInt(),
            kcal = if (ringKcalColor == 0L) 0xFFFFA24C.toInt() else ringKcalColor.toInt(),
            dim = c.dim, kcalLabel = kcalLabel, showGoal = ringGoal,
        )
    }

    /** Общий множитель размера текста. */
    fun k(): Float = (if (large) 1.2f else 1f) * textScale.coerceIn(0.6f, 2f)

    companion object {
        val DEFAULT_BLOCKS = listOf(WidgetBlock("weather", 0), WidgetBlock("tasks", 0, 2), WidgetBlock("weight", 1, on = false))
    }
}

/** Цвета виджета (ARGB). */
data class WColors(
    val bg: Int, val text: Int, val dim: Int, val track: Int,
    val accent: Int = 0xFFC79246.toInt(), val good: Int = 0xFF8CC46E.toInt(), val bad: Int = 0xFFD27A63.toInt(),
) {
    fun tone(t: Int): Int = when (t) {
        WidgetModels.TONE_GOOD -> good
        WidgetModels.TONE_BAD -> bad
        WidgetModels.TONE_ACCENT -> accent
        WidgetModels.TONE_DIM -> dim
        else -> text
    }
}

/** Оформление кольца шагов. */
data class RingStyle(
    val progress: Int = 0xFFE0A04A.toInt(),
    val done: Int = 0xFF8CC46E.toInt(),
    val track: Int = 0xFF3D362C.toInt(),
    val steps: Int = 0xFFFFFFFF.toInt(),
    val kcal: Int = 0xFFFFA24C.toInt(),
    val dim: Int = 0xFFCFC6B8.toInt(),
    val kcalLabel: String = "ккал",
    val showGoal: Boolean = true,
)

data class WidgetBlockType(val type: String, val title: String, val styles: List<String>, val counted: Boolean = false)

val WIDGET_BLOCK_TYPES = listOf(
    WidgetBlockType("weather", "Погода", listOf("Подробности — если виджет растянут", "Подробности всегда")),
    WidgetBlockType("weight", "Вес", listOf("Число и изменение", "С графиком за 2 недели")),
    WidgetBlockType("tasks", "Задачи на сегодня", listOf("Список"), counted = true),
    WidgetBlockType("habits", "Привычки", listOf("Счётчик", "Полоса прогресса")),
    WidgetBlockType("food", "Съедено калорий", listOf("Текст", "Полоса к норме")),
    WidgetBlockType("water", "Вода", listOf("Текст", "Полоса к норме")),
    WidgetBlockType("steps", "Шаги", listOf("Текст", "Полоса к цели")),
    WidgetBlockType("burned", "Сожжено калорий", listOf("Текст")),
    WidgetBlockType("sleep", "Сон прошлой ночью", listOf("Текст", "Полоса к норме")),
    WidgetBlockType("events", "События сегодня", listOf("Список"), counted = true),
    WidgetBlockType("workout", "Тренировка", listOf("Текст")),
    WidgetBlockType("budget", "Расходы за месяц", listOf("Текст", "Полоса к бюджету")),
    WidgetBlockType("note", "Избранная заметка", listOf("Заголовок")),
)

object WidgetPrefs {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun sp(ctx: Context) = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun load(ctx: Context): WidgetConfig =
        runCatching { sp(ctx).getString("config", null)?.let { json.decodeFromString(WidgetConfig.serializer(), it) } }.getOrNull()?.normalized()
            ?: WidgetConfig().normalized()

    fun save(ctx: Context, cfg: WidgetConfig) {
        sp(ctx).edit().putString("config", json.encodeToString(WidgetConfig.serializer(), cfg)).apply()
    }
}

/**
 * Строка виджета. kind: 0 — текст, 1 — текст с полосой, 2 — текст с графиком, 3 — задача (кружок-отметка),
 * 4 — мелкая строка подробностей (например, «Осадки 60 % · Ощущается +8°»).
 * extra — необязательная строка: показывается, только если после остальных блоков осталось место.
 * Одинаково рисуется и в самом виджете, и в предпросмотре в приложении.
 */
data class WRow(
    val kind: Int,
    val icon: String?,
    val title: String,
    val value: String = "",
    val sub: String = "",
    val tone: Int = 0,
    val progress: Float? = null,
    val spark: List<Double> = emptyList(),
    val route: String? = null,
    val taskId: Long? = null,
    /** Блок, из которого строка, — для его оформления (подпись, цвета, размер). */
    val block: WidgetBlock? = null,
    val extra: Boolean = false,
)

/** Всё, что нужно для отрисовки: заголовок, кольцо и строки. */
data class WidgetModel(
    val cfg: WidgetConfig,
    val date: String,
    val habitsText: String?,
    val steps: Int,
    val stepsGoal: Int,
    val burned: Int,
    val rows: List<WRow>,
)

object WidgetModels {
    const val TONE_TEXT = 0
    const val TONE_GOOD = 1
    const val TONE_BAD = 2
    const val TONE_ACCENT = 3
    const val TONE_DIM = 4

    private fun fmt1(v: Double) = "%.1f".format(v).replace('.', ',')
    private fun thousands(n: Int) = n.toString().reversed().chunked(3).joinToString(" ").reversed()

    fun icon(ctx: Context, key: String): Bitmap? =
        if (key.startsWith("wx:")) key.split(':').let { WeatherIcons.render(it.getOrNull(1)?.toIntOrNull() ?: 2, it.getOrNull(2) != "0", 96) }
        else runCatching { ctx.assets.open("glyphs/$key.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()

    suspend fun load(ctx: Context, cfg: WidgetConfig = WidgetPrefs.load(ctx)): WidgetModel {
        val dao = Graph.dao
        val today = Dates.today()
        val profile = dao.profileNow()
        val habits = dao.habitsNow().filter { HabitSchedule(it.daysMask, it.timesPerWeek).isScheduled(today) }
        val logs = dao.habitLogsOn(today).associateBy { it.habitId }
        val habitsDone = habits.count { h -> (logs[h.id]?.value ?: 0) >= h.target }
        val log = dao.dayLogNow(today)
        val steps = log?.steps ?: 0
        val stepsGoal = profile?.stepsGoal ?: 8000
        val lastW = dao.lastWeight()
        val burned = Energy.burned(steps, lastW?.kg ?: profile?.startWeight ?: 70.0, dao.workoutKcalOn(today), Graph.extra.dayEnergyOf(today)?.activeKcal).total

        val rows = mutableListOf<WRow>()
        for (b in cfg.blocks.filter { it.on }) {
            val start = rows.size
            when (b.type) {
                "weather" -> {
                    val w = Weather.cached(ctx)
                    if (w == null) rows += WRow(0, "wx:2:1", "Погода", "", "нажмите, чтобы загрузить", TONE_DIM, route = Routes.WEATHER)
                    else {
                        val n = w.now
                        val sub = "ветер ${n.windMs.roundToInt()} м/с · " + WeatherLogic.describe(n.code).lowercase()
                        rows += WRow(0, "wx:${n.code}:${if (n.isDay) 1 else 0}", "Погода", WeatherLogic.temp(n.temp), sub, TONE_DIM, route = Routes.WEATHER)
                        weatherDetails(w, b.weatherFields()).chunked(2).forEach { pair ->
                            rows += WRow(4, null, pair.joinToString(" · "), tone = TONE_DIM, route = Routes.WEATHER, extra = b.style == 0)
                        }
                    }
                }
                "weight" -> {
                    val recent = dao.weightsSince(today - 13)
                    val last = recent.lastOrNull() ?: lastW ?: continue
                    val weekAgo = dao.weightsSince(today - 30).lastOrNull { it.day <= last.day - 7 } ?: recent.firstOrNull()?.takeIf { it.day < last.day }
                    val d = weekAgo?.let { last.kg - it.kg }
                    val sub = d?.let { (if (it > 0.05) "▲ +" else if (it < -0.05) "▼ " else "") + fmt1(it) + " за нед." } ?: ""
                    val tone = when { d == null -> TONE_DIM; d > 0.05 -> TONE_BAD; d < -0.05 -> TONE_GOOD; else -> TONE_DIM }
                    rows += WRow(if (b.style == 1 && recent.size >= 2) 2 else 0, "sport/11", "Вес", fmt1(last.kg) + " кг", sub, tone, spark = recent.map { it.kg }, route = Routes.WEIGHT_TREND)
                }
                "tasks" -> {
                    val tasks = dao.openTasksUntil(today).sortedWith(compareBy({ it.dueDay }, { it.dueMin ?: 9999 }, { -it.priority }))
                    if (tasks.isEmpty()) rows += WRow(0, "ui:check", "Задач на сегодня нет", tone = TONE_DIM, route = Routes.TASKS)
                    tasks.take(b.count.coerceIn(1, 5)).forEach { t ->
                        rows += WRow(3, null, t.title, t.dueMin?.let { Dates.time(it) } ?: "", tone = if ((t.dueDay ?: today) < today) TONE_BAD else TONE_TEXT, taskId = t.id)
                    }
                    if (tasks.size > b.count) rows += WRow(0, null, "и ещё ${tasks.size - b.count}", tone = TONE_DIM, route = Routes.TASKS)
                }
                "habits" -> if (habits.isNotEmpty()) rows += WRow(
                    if (b.style == 1) 1 else 0, "sport/24", "Привычки", "$habitsDone из ${habits.size}",
                    tone = if (habitsDone == habits.size) TONE_GOOD else TONE_ACCENT, progress = habitsDone / habits.size.toFloat(), route = Routes.HABITS,
                )
                "food" -> {
                    val eaten = dao.food().first().filter { it.day == today }.sumOf { it.kcal }
                    val p = profile ?: com.dasein.poryadok.data.BodyProfile()
                    val target = Nutrition.plan(p.input(lastW?.kg ?: p.startWeight)).targetKcal
                    rows += WRow(if (b.style == 1) 1 else 0, "food/02", "Съедено", "$eaten ккал", "из $target", if (eaten > target * 1.1) TONE_BAD else TONE_TEXT, eaten / target.coerceAtLeast(1).toFloat(), route = Routes.health(0))
                }
                "water" -> {
                    val ml = log?.waterMl ?: 0
                    val goal = profile?.waterGoalMl ?: 2000
                    rows += WRow(if (b.style == 1) 1 else 0, null, "Вода", fmt1(ml / 1000.0) + " л", "из " + fmt1(goal / 1000.0), if (ml >= goal) TONE_GOOD else TONE_TEXT, ml / goal.toFloat(), route = Routes.wellbeing(2))
                }
                "steps" -> rows += WRow(if (b.style == 1) 1 else 0, "sport/19", "Шаги", thousands(steps), "из ${thousands(stepsGoal)}", if (steps >= stepsGoal) TONE_GOOD else TONE_TEXT, steps / stepsGoal.coerceAtLeast(1).toFloat(), route = Routes.STEPS)
                "burned" -> rows += WRow(0, "sport/22", "Сожжено", "$burned ккал", route = Routes.STEPS)
                "sleep" -> {
                    val s = dao.sleepNow(today) ?: dao.sleepNow(today - 1)
                    if (s != null) {
                        val m = sleepMinutes(s)
                        val goal = profile?.sleepGoalMin ?: 480
                        rows += WRow(if (b.style == 1) 1 else 0, "sleep/00", "Сон", "${m / 60} ч ${m % 60} мин", "${Dates.time(s.bedMin)}–${Dates.time(s.wakeMin)}", if (m >= goal * 0.9) TONE_GOOD else TONE_ACCENT, m / goal.toFloat(), route = Routes.wellbeing(1))
                    } else rows += WRow(0, "sleep/00", "Сон", "нет записи", tone = TONE_DIM, route = Routes.wellbeing(1))
                }
                "events" -> {
                    val events = dao.eventsNow().filter { it.day == today }.sortedBy { it.startMin ?: -1 }
                    if (events.isEmpty()) rows += WRow(0, "cal/00", "Событий сегодня нет", tone = TONE_DIM, route = Routes.calendar(0))
                    events.take(b.count.coerceIn(1, 5)).forEach { e -> rows += WRow(0, "cal/00", "Событие", e.title, e.startMin?.let { Dates.time(it) } ?: "весь день", route = Routes.event(e.id)) }
                }
                "focus" -> {
                    val min = dao.focusSessions().first().filter { it.day == today }.sumOf { it.minutes }
                    rows += WRow(0, "train/18", "Фокус", "$min мин", route = Routes.TODAY)
                }
                "workout" -> {
                    val t = Graph.training
                    val dow = (java.time.LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
                    val plan = t.plansNow().filter { !it.archived && it.daysMask and (1 shl dow) != 0 }.minByOrNull { it.sort }
                    val doneToday = t.sessionsNow().any { it.day == today && it.finishedAt != null }
                    rows += when {
                        doneToday -> WRow(0, "sport/15", "Тренировка", "Тренировка", "выполнена", TONE_GOOD, route = Routes.training(2))
                        plan != null -> WRow(0, "sport/15", "Тренировка", plan.name, "сегодня", TONE_ACCENT, route = Routes.trainingPlan(plan.id))
                        else -> WRow(0, "sport/15", "Тренировка", "Отдых", "по плану", TONE_DIM, route = Routes.training(0))
                    }
                }
                "budget" -> {
                    val from = Dates.day(today).withDayOfMonth(1).toEpochDay()
                    val spent = dao.txnsNow().filter { it.type == TxnType.EXPENSE && it.day >= from && it.day <= today }.sumOf { it.amount }
                    val limit = dao.budgets().first().firstOrNull { it.categoryId == 0L }?.monthly
                    rows += WRow(
                        if (b.style == 1 && limit != null) 1 else 0, null, "Расходы", thousands(spent.roundToInt()) + " ₽",
                        limit?.let { "из " + thousands(it.roundToInt()) } ?: "за месяц",
                        if (limit != null && spent > limit) TONE_BAD else TONE_TEXT, limit?.let { (spent / it).toFloat() }, route = Routes.finance(0),
                    )
                }
                "note" -> {
                    val page = Graph.pages.pagesNow().filter { it.favorite && !it.archived }.maxByOrNull { it.updatedAt }
                    if (page != null) rows += WRow(0, "ui:notebook", "Заметка", page.title.ifBlank { "Без названия" }, route = Routes.page(page.id))
                }
            }
            // Оформление блока: своя подпись и ссылка на блок для цветов и размера.
            for (i in start until rows.size) {
                val r = rows[i]
                rows[i] = r.copy(block = b, title = if (b.label.isNotBlank() && r.kind != 3 && r.value.isNotBlank()) b.label else r.title)
            }
        }
        return WidgetModel(
            cfg, "${Dates.weekdayShort(today)}, ${Dates.short(today)}",
            if (habits.isNotEmpty()) "Привычки $habitsDone/${habits.size}" else null,
            steps, stepsGoal, burned, rows,
        )
    }

    /** Примерная высота строки в dp — чтобы на виджете оказалось столько строк, сколько помещается. */
    fun rowHeight(r: WRow, cfg: WidgetConfig): Int {
        val k = cfg.k() * (r.block?.scale ?: 1f)
        return (when (r.kind) { 1 -> 40; 2 -> 64; 3 -> 30; 4 -> 20; else -> 28 } * k).toInt()
    }

    /**
     * Какие строки поместятся по высоте: сначала обязательные по порядку,
     * затем в оставшееся место — подробности (extra), на своих местах. Чем больше виджет, тем больше подробностей.
     */
    fun visible(rows: List<WRow>, cfg: WidgetConfig, avail: Float): List<WRow> {
        val chosen = HashSet<Int>()
        var used = 0
        for ((i, r) in rows.withIndex()) {
            if (r.extra) continue
            val h = rowHeight(r, cfg)
            if (used + h > avail && chosen.isNotEmpty()) break
            used += h
            chosen += i
        }
        for ((i, r) in rows.withIndex()) {
            if (!r.extra) continue
            // Подробности показываются только под своим блоком, если сам блок уместился.
            val owner = (i - 1 downTo 0).firstOrNull { !rows[it].extra } ?: continue
            if (owner !in chosen) continue
            val h = rowHeight(r, cfg)
            if (used + h > avail) break
            used += h
            chosen += i
        }
        return rows.filterIndexed { i, _ -> i in chosen }.take(14)
    }

    /** Подробности погоды выбранными пунктами. */
    fun weatherDetails(w: com.dasein.poryadok.logic.WeatherData, fields: List<String>): List<String> {
        val n = w.now
        val day = w.days.firstOrNull()
        return fields.mapNotNull { f ->
            when (f) {
                "pop" -> day?.let { "Осадки ${it.pop} %" }
                "feels" -> "Ощущается ${WeatherLogic.temp(n.feels)}"
                "minmax" -> day?.let { "${WeatherLogic.temp(it.tMin)}…${WeatherLogic.temp(it.tMax)}" }
                "humidity" -> "Влажность ${n.humidity} %"
                "gust" -> if (n.gustMs > 0) "Порывы ${n.gustMs.roundToInt()} м/с" else null
                "pressure" -> "${WeatherLogic.mmHg(n.pressureHpa)} мм рт. ст."
                "uv" -> day?.let { "УФ ${it.uv.roundToInt()}" }
                "sun" -> day?.takeIf { it.sunrise.length >= 16 }?.let { "☀ ${it.sunrise.takeLast(5)}–${it.sunset.takeLast(5)}" }
                "next" -> {
                    val now = java.time.LocalDateTime.now().plusHours(3).toString().take(13)
                    w.hours.firstOrNull { it.time.take(13) == now }?.let { "Через 3 ч ${WeatherLogic.temp(it.temp)}, ${WeatherLogic.describe(it.code).lowercase()}" }
                }
                else -> null
            }
        }
    }
}
