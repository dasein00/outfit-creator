package com.dasein.poryadok.ui.today

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MediaStatus
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Fact
import com.dasein.poryadok.logic.FactDeck
import com.dasein.poryadok.logic.HabitSchedule
import com.dasein.poryadok.logic.HabitStats
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.calendar.eventsOn
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.goTab
import com.dasein.poryadok.ui.health.sleepMinutes
import com.dasein.poryadok.ui.recipes.rememberNutritionPlan
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.LocalDate
import kotlin.random.Random

/** Картотека фактов из assets/facts/*.txt (строка: «Тема|Текст»). */
object FactsRepo {
    @Volatile private var cache: List<Fact>? = null

    fun load(ctx: Context): List<Fact> = cache ?: runCatching {
        val files = ctx.assets.list("facts").orEmpty().filter { it.endsWith(".txt") }.sorted()
        FactDeck.parse(files.joinToString("\n") { f -> ctx.assets.open("facts/$f").bufferedReader().use { it.readText() } })
    }.getOrDefault(emptyList()).also { cache = it }

    fun glyph(tag: String): String = when (tag) {
        "Сон" -> "ui:moon"
        "Питание" -> "ui:salad"
        "Движение", "Спорт" -> "ui:dumbbell"
        "Сердце" -> "ui:heart"
        "Мозг" -> "ui:bulb"
        "Вода" -> "ui:drop"
        "Стресс" -> "ui:smile"
        "Долголетие" -> "ui:leaves"
        "История" -> "ui:book"
        "Кожа" -> "ui:sun"
        "Иммунитет" -> "ui:thermo"
        else -> "ui:people"
    }
}

/** Плашка «Интересный факт»: → новый непрочитанный, ← предыдущий. */
@Composable
fun FactsCard() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val facts = remember { FactsRepo.load(ctx) }
    val settings by observe(null) { Graph.prefs.settings }
    val s = settings ?: return
    if (facts.isEmpty()) return
    val st = remember(s.factsHistory, s.factsPos, s.factsRound) {
        FactDeck.normalize(facts, FactDeck.State(FactDeck.decode(s.factsHistory), s.factsPos, s.factsRound), Random.Default)
    }
    fun save(n: FactDeck.State) = io {
        Graph.prefs.update { it.copy(factsHistory = FactDeck.encode(n.history), factsPos = n.pos, factsRound = n.round) }
    }
    // Первый показ: запоминаем выбранный факт, чтобы он не менялся при каждом открытии.
    LaunchedEffect(st.history) { if (FactDeck.decode(s.factsHistory) != st.history) save(st) }
    val fact = FactDeck.current(facts, st) ?: return

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(extra.card).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(FactsRepo.glyph(fact.tag), 22.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Интересный факт", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(fact.tag, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "${FactDeck.read(st)} из ${facts.size}" + if (st.round > 0) " · круг ${st.round + 1}" else "",
                fontSize = 12.sp, color = extra.dim,
            )
        }
        AnimatedContent(fact, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "fact") { f ->
            Text(
                f.text, fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(top = 10.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(onClick = { save(FactDeck.prev(facts, st, Random.Default)) }, enabled = st.pos > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Предыдущий факт")
            }
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Bar(FactDeck.read(st) / facts.size.toFloat(), MaterialTheme.colorScheme.primary, height = 4.dp)
            }
            FilledIconButton(
                onClick = {
                    val (n, newRound) = FactDeck.next(facts, st, Random.Default)
                    if (newRound) Toast.makeText(ctx, "Вы прочитали все ${facts.size} фактов! Начинаем новый круг.", Toast.LENGTH_LONG).show()
                    save(n)
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следующий факт") }
        }
    }
}

private data class Metric(
    val glyph: String,
    val label: String,
    val value: String,
    val sub: String,
    val progress: Float? = null,
    val color: Color? = null,
    val go: () -> Unit,
)

private fun kg(v: Double) = "%.1f".format(v).replace('.', ',')

/** Сетка метрик дня: каждая ведёт в свой раздел. */
@Composable
fun DayMetrics(nav: NavHostController, currency: String) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    val today = Dates.today()
    val weekAgo = today - 6
    val sleep by observe(emptyList()) { dao.sleep() }
    val food by observe(emptyList()) { dao.food() }
    val dayLogs by observe(emptyList()) { dao.dayLogs() }
    val weights by observe(emptyList()) { dao.weights() }
    val workouts by observe(emptyList()) { dao.workouts() }
    val habits by observe(emptyList()) { dao.habits() }
    val habitLogs by observe(emptyList()) { dao.habitLogs() }
    val focus by observe(emptyList()) { dao.focusSessions() }
    val moods by observe(emptyList()) { dao.moods() }
    val tasks by observe(emptyList()) { dao.tasks() }
    val events by observe(emptyList()) { dao.events() }
    val txns by observe(emptyList()) { dao.txns() }
    val budgets by observe(emptyList()) { dao.budgets() }
    val profile by observe(null) { dao.profile() }
    val body by observe(emptyList()) { Graph.extra.bodyMetrics() }
    val media by observe(emptyList()) { Graph.extra.media() }
    val plan = rememberNutritionPlan()

    val eatenToday = food.filter { it.day == today }
    val kcal = eatenToday.sumOf { it.kcal }
    val protein = eatenToday.sumOf { it.protein }.toInt()
    val lastSleep = sleep.filter { it.day >= today - 1 }.maxByOrNull { it.day }
    val sleepWeek = sleep.filter { it.day in weekAgo..today }
    val lastW = weights.lastOrNull()
    val weekW = weights.lastOrNull { it.day <= today - 7 }
    val fat = body.lastOrNull { it.fatPct != null }
    val heightM = (profile?.heightCm ?: 0.0) / 100
    val stepsWeek = dayLogs.filter { it.day in weekAgo..today }
    val stepsAvg = if (stepsWeek.isEmpty()) 0 else stepsWeek.sumOf { it.steps } / 7
    val workoutsWeek = workouts.filter { it.day >= Dates.weekStart(today) }
    val bestStreak = habits.filter { !it.archived }.maxOfOrNull { h ->
        val done = habitLogs.filter { it.habitId == h.id && it.value >= h.target }.map { it.day }.toSet()
        HabitStats.summary(HabitSchedule(h.daysMask, h.timesPerWeek), done, today, h.createdDay).streak
    } ?: 0
    val streakHabit = habits.filter { !it.archived }.maxByOrNull { h ->
        val done = habitLogs.filter { it.habitId == h.id && it.value >= h.target }.map { it.day }.toSet()
        HabitStats.summary(HabitSchedule(h.daysMask, h.timesPerWeek), done, today, h.createdDay).streak
    }
    val focusWeek = focus.filter { it.day in weekAgo..today }.sumOf { it.minutes }
    val moodWeek = moods.filter { it.day in weekAgo..today }
    val overdue = tasks.count { !it.done && it.dueDay != null && it.dueDay < today }
    val nextEvent = (0..60).firstNotNullOfOrNull { d -> eventsOn(events, today + d).firstOrNull()?.let { d to it } }
    val monthStart = LocalDate.now().withDayOfMonth(1).toEpochDay()
    val spentMonth = txns.filter { it.type == TxnType.EXPENSE && it.day >= monthStart }.sumOf { it.amount }
    val budget = budgets.firstOrNull { it.categoryId == 0L }?.monthly
    val yearStart = LocalDate.now().withDayOfYear(1).toEpochDay()
    val doneYear = media.filter { it.status == MediaStatus.DONE && (it.finishedDay ?: Dates.dayOf(it.createdAt)) >= yearStart }

    val metrics = listOf(
        Metric(
            "ui:moon", "Сон", lastSleep?.let { "${sleepMinutes(it) / 60} ч ${sleepMinutes(it) % 60} м" } ?: "—",
            if (sleepWeek.isNotEmpty()) "в среднем ${sleepWeek.sumOf { sleepMinutes(it) } / sleepWeek.size / 60} ч за неделю" else "нет записи",
            lastSleep?.let { sleepMinutes(it) / (profile?.sleepGoalMin ?: 480).toFloat() }, Palette.item(5),
        ) { nav.navigate(Routes.wellbeing(1)) },
        Metric(
            "ui:scale", "Вес", lastW?.let { kg(it.kg) + " кг" } ?: "—",
            if (lastW != null && weekW != null) {
                val d = lastW.kg - weekW.kg
                (if (d > 0) "▲ +" else if (d < 0) "▼ " else "") + kg(d) + " за неделю"
            } else if (lastW != null) Dates.label(lastW.day) else "нет взвешиваний",
        ) { nav.navigate(Routes.health(1)) },
        Metric(
            "ui:salad", "Съедено", "$kcal ккал", "из ${plan.targetKcal}",
            kcal / plan.targetKcal.coerceAtLeast(1).toFloat(), if (kcal > plan.targetKcal) extra.warn else extra.ok,
        ) { nav.navigate(Routes.health(0)) },
        Metric(
            "ui:dumbbell", "Белок", "$protein г", "из ${plan.proteinG} г",
            protein / plan.proteinG.coerceAtLeast(1).toFloat(), Palette.item(1),
        ) { nav.navigate(Routes.health(0)) },
        Metric(
            "ui:flame", "Тренировки", "${workoutsWeek.size}",
            "на неделе · ${workoutsWeek.sumOf { it.minutes }} мин",
        ) { nav.navigate(Routes.health(3)) },
        Metric(
            "ui:stats", "Шаги в среднем", "$stepsAvg", "в день за 7 дней",
            stepsAvg / (profile?.stepsGoal ?: 8000).coerceAtLeast(1).toFloat(), extra.ok,
        ) { nav.navigate(Routes.STEPS) },
        Metric(
            "ui:heart", if (fat != null) "Жир в теле" else "ИМТ",
            when {
                fat != null -> kg(fat.fatPct!!) + " %"
                lastW != null && heightM > 0.5 -> kg(lastW.kg / (heightM * heightM))
                else -> "—"
            },
            if (fat != null) Dates.label(fat.day) else "индекс массы тела",
        ) { nav.navigate(Routes.health(1)) },
        Metric(
            "ui:check", "Лучшая серия", "$bestStreak",
            streakHabit?.takeIf { bestStreak > 0 }?.name ?: "привычек подряд",
        ) { nav.navigate(Routes.HABITS) },
        Metric(
            "ui:timer", "Фокус", "${focusWeek / 60} ч ${focusWeek % 60} м", "за 7 дней",
        ) { nav.navigate(Routes.FOCUS) },
        Metric(
            "ui:smile", "Настроение",
            if (moodWeek.isEmpty()) "—" else "%.1f".format(moodWeek.map { it.level }.average()).replace('.', ',') + " / 5",
            "в среднем за неделю",
        ) { nav.navigate(Routes.wellbeing(0)) },
        Metric(
            "ui:target", "Просрочено", "$overdue", if (overdue == 0) "задач нет — отлично" else "задач ждут",
            color = if (overdue > 0) extra.warn else null,
        ) { nav.goTab(Routes.plan(1)) },
        Metric(
            "ui:calendar", "Ближайшее",
            nextEvent?.let { (d, _) -> if (d == 0) "сегодня" else if (d == 1) "завтра" else "через $d дн." } ?: "—",
            nextEvent?.second?.title ?: "событий нет",
        ) { nav.goTab(Routes.plan(0)) },
        Metric(
            "ui:wallet", "Траты за месяц", Money.format(spentMonth, currency),
            if (budget != null && budget > 0) "из " + Money.format(budget, currency) else "без бюджета",
            budget?.takeIf { it > 0 }?.let { (spentMonth / it).toFloat() }, if (budget != null && spentMonth > budget) extra.danger else extra.ok,
        ) { nav.navigate(Routes.FINANCE) },
        Metric(
            "ui:book", "За ${LocalDate.now().year} год",
            "${doneYear.size}",
            "фильмов, сериалов и книг",
        ) { nav.goTab(Routes.topsHub(0)) },
        Metric(
            "ui:drop", "Вода за неделю",
            "${stepsWeek.sumOf { it.waterMl } / 7} мл", "в среднем в день",
            (stepsWeek.sumOf { it.waterMl } / 7) / (profile?.waterGoalMl ?: 2000).toFloat(), Palette.item(2),
        ) { nav.navigate(Routes.wellbeing(2)) },
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m -> MetricTile(m, Modifier.weight(1f)) }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MetricTile(m: Metric, modifier: Modifier) {
    val extra = LocalExtra.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(extra.card)
            .clickable(onClick = m.go)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(CircleShape), contentAlignment = Alignment.Center) { Glyph(m.glyph, 18.dp, badge = false) }
            Text(" " + m.label, fontSize = 11.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            m.value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = m.color?.takeIf { m.progress == null } ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(m.sub, fontSize = 10.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (m.progress != null) Bar(m.progress.coerceIn(0f, 1f), m.color ?: extra.ok, Modifier.padding(top = 5.dp), height = 4.dp)
    }
}
