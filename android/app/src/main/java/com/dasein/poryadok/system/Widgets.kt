package com.dasein.poryadok.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.remember
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.dasein.poryadok.Graph
import com.dasein.poryadok.MainActivity
import com.dasein.poryadok.Repo
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Energy
import com.dasein.poryadok.logic.HabitSchedule
import com.dasein.poryadok.ui.Routes

object Widgets {
    /** Картинка кольца как на виджете — для предпросмотра в приложении. */
    suspend fun ringPreview(ctx: Context): Bitmap {
        val dao = Graph.dao
        val today = Dates.today()
        val steps = dao.dayLogNow(today)?.steps ?: 0
        val burned = Energy.burned(steps, dao.lastWeight()?.kg ?: dao.profileNow()?.startWeight ?: 70.0, dao.workoutKcalOn(today), Graph.extra.dayEnergyOf(today)?.activeKcal).total
        return WidgetRing.render(ctx, steps, dao.profileNow()?.stepsGoal ?: 8000, burned)
    }

    suspend fun refresh(ctx: Context) {
        try {
            TodayWidget().updateAll(ctx)
        } catch (_: Exception) {
        }
    }
}

private data class WidgetTask(val id: Long, val title: String, val time: String, val overdue: Boolean)
private data class WidgetData(
    val tasks: List<WidgetTask>, val more: Int, val habitsDone: Int, val habitsTotal: Int, val date: String,
    val steps: Int, val stepsGoal: Int, val burned: Int,
    /** Вес: последнее значение, изменение за неделю и точки за 2 недели для мини-графика. */
    val weight: Double?, val weightDelta: Double?, val weightPoints: List<Double>,
)

private val TaskIdKey = ActionParameters.Key<Long>("taskId")
private val RouteKey = ActionParameters.Key<String>(MainActivity.EXTRA_ROUTE)

private val INK = Color(0xFF211D18)
private val INK2 = Color(0xFF2B261F)
private val TEXT = Color(0xFFF0ECE3)
private val DIM = Color(0xFFA79E90)
private val BRASS = Color(0xFFC79246)
private val DANGER = Color(0xFFD27A63)
private val GOOD = Color(0xFF8CC46E)

class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Graph.init(context)
        val data = load()
        provideContent { Content(data) }
    }

    private suspend fun load(): WidgetData {
        val dao = Graph.dao
        val today = Dates.today()
        val tasks = dao.openTasksUntil(today).sortedWith(compareBy({ it.dueDay }, { it.dueMin ?: 9999 }, { -it.priority }))
        val habits = dao.habitsNow().filter { HabitSchedule(it.daysMask, it.timesPerWeek).isScheduled(today) }
        val logs = dao.habitLogsOn(today).associateBy { it.habitId }
        val done = habits.count { h -> (logs[h.id]?.value ?: 0) >= h.target }
        val steps = dao.dayLogNow(today)?.steps ?: 0
        val recent = dao.weightsSince(today - 13)
        val last = recent.lastOrNull() ?: dao.lastWeight()
        val weekAgo = dao.weightsSince(today - 30).lastOrNull { it.day <= (last?.day ?: today) - 7 } ?: recent.firstOrNull()?.takeIf { it.day < (last?.day ?: today) }
        return WidgetData(
            tasks.take(5).map { t ->
                WidgetTask(t.id, t.title, t.dueMin?.let { Dates.time(it) } ?: "", (t.dueDay ?: today) < today)
            },
            (tasks.size - 5).coerceAtLeast(0), done, habits.size,
            "${Dates.weekdayShort(today)}, ${Dates.short(today)}",
            steps, dao.profileNow()?.stepsGoal ?: 8000,
            Energy.burned(steps, dao.lastWeight()?.kg ?: dao.profileNow()?.startWeight ?: 70.0, dao.workoutKcalOn(today), Graph.extra.dayEnergyOf(today)?.activeKcal).total,
            last?.kg, weekAgo?.let { w -> last?.let { it.kg - w.kg } }, recent.map { it.kg },
        )
    }

    @Composable
    private fun Content(d: WidgetData) {
        val size = LocalSize.current
        val context = LocalContext.current
        val compact = size.height < 140.dp
        // Кольцо занимает всю высоту под заголовком, но не больше половины ширины.
        val ringDp = ((if (compact) size.height - 20.dp else size.height - 56.dp).value)
            .coerceAtMost(size.width.value * 0.5f).coerceAtLeast(72f)
        val ring = remember(d.steps, d.stepsGoal, d.burned) { WidgetRing.render(context, d.steps, d.stepsGoal, d.burned) }
        Column(
            GlanceModifier.fillMaxSize().background(ColorProvider(INK)).cornerRadius(20.dp).padding(10.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (!compact) {
                Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Сегодня", style = TextStyle(color = ColorProvider(TEXT), fontSize = 19.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.width(8.dp))
                    Text(d.date, style = TextStyle(color = ColorProvider(DIM), fontSize = 14.sp))
                    Spacer(GlanceModifier.defaultWeight())
                    if (d.habitsTotal > 0) Text(
                        "Привычки ${d.habitsDone}/${d.habitsTotal}",
                        style = TextStyle(color = ColorProvider(BRASS), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    ImageProvider(ring), contentDescription = "${d.steps} шагов из ${d.stepsGoal}, ${d.burned} ккал сожжено",
                    modifier = GlanceModifier.size(ringDp.dp).clickable(actionStartActivity<MainActivity>(actionParametersOf(RouteKey to Routes.STEPS))),
                )
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    if (d.weight != null) {
                        Row(
                            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>(actionParametersOf(RouteKey to Routes.WEIGHT_TREND))),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(ImageProvider(com.dasein.poryadok.R.drawable.ic_v_weight), "Вес", modifier = GlanceModifier.size(26.dp))
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                "%.1f".format(d.weight).replace('.', ',') + " кг",
                                style = TextStyle(color = ColorProvider(TEXT), fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            )
                            Spacer(GlanceModifier.width(6.dp))
                            d.weightDelta?.let { dw ->
                                Text(
                                    (if (dw > 0.05) "▲ +" else if (dw < -0.05) "▼ " else "") + "%.1f".format(dw).replace('.', ',') + " за нед.",
                                    style = TextStyle(color = ColorProvider(if (dw > 0.05) DANGER else GOOD), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                        if (!compact && d.weightPoints.size >= 2) {
                            val spark = remember(d.weightPoints) { WidgetRing.spark(d.weightPoints) }
                            Image(ImageProvider(spark), "Вес за 2 недели", modifier = GlanceModifier.fillMaxWidth().height(34.dp))
                        }
                        Spacer(GlanceModifier.height(4.dp))
                    }
                    if (compact && d.habitsTotal > 0) Text(
                        "Привычки ${d.habitsDone}/${d.habitsTotal}",
                        style = TextStyle(color = ColorProvider(BRASS), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                    if (d.tasks.isEmpty()) {
                        Text("Задач на сегодня нет", style = TextStyle(color = ColorProvider(DIM), fontSize = 16.sp))
                    }
                    d.tasks.take(if (d.weight != null) (if (compact) 1 else 2) else 5).forEach { t ->
                        Row(
                            GlanceModifier.fillMaxWidth().padding(vertical = 5.dp)
                                .clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to t.id))),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("○", style = TextStyle(color = ColorProvider(BRASS), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                t.title, maxLines = 1,
                                style = TextStyle(color = ColorProvider(if (t.overdue) DANGER else TEXT), fontSize = 16.sp, fontWeight = FontWeight.Medium),
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            if (t.time.isNotEmpty()) Text(" " + t.time, style = TextStyle(color = ColorProvider(DIM), fontSize = 14.sp))
                        }
                    }
                    if (d.more > 0) Text("и ещё ${d.more}", style = TextStyle(color = ColorProvider(DIM), fontSize = 14.sp))
                }
            }
        }
    }
}

/** Кольцо шагов для виджета: заполняется по мере выполнения плана, внутри — шаги и калории крупно, с иконками. */
object WidgetRing {
    private const val S = 480

    private fun icon(ctx: Context, key: String): Bitmap? =
        runCatching { ctx.assets.open("glyphs/$key.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()

    private fun fit(p: Paint, text: String, maxW: Float, size: Float) {
        p.textSize = size
        val w = p.measureText(text)
        if (w > maxW) p.textSize = size * maxW / w
    }

    /** Мини-график веса: линия, заливка и точка последнего взвешивания. */
    fun spark(values: List<Double>, w: Int = 600, h: Int = 110): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        var lo = values.min(); var hi = values.max()
        if (hi - lo < 0.6) { val m = (hi + lo) / 2; lo = m - 0.3; hi = m + 0.3 }
        val pad = 10f
        fun x(i: Int) = pad + i * (w - 2 * pad) / (values.size - 1)
        fun y(v: Double) = (pad + (hi - v) / (hi - lo) * (h - 2 * pad)).toFloat()
        val path = android.graphics.Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        val area = android.graphics.Path(path).apply { lineTo(x(values.lastIndex), h.toFloat()); lineTo(x(0), h.toFloat()); close() }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), 0x55E0A04A, 0x00E0A04A, android.graphics.Shader.TileMode.CLAMP)
        }
        c.drawPath(area, fill)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0xFFE0A04A.toInt(); strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        c.drawPath(path, line)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        c.drawCircle(x(values.lastIndex), y(values.last()), 9f, dot)
        dot.color = 0xFFE0A04A.toInt()
        c.drawCircle(x(values.lastIndex), y(values.last()), 6f, dot)
        return b
    }

    fun render(ctx: Context, steps: Int, goal: Int, burned: Int): Bitmap {
        val b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val cx = S / 2f
        val stroke = S * 0.08f
        val oval = RectF(stroke / 2 + 2, stroke / 2 + 2, S - stroke / 2 - 2, S - stroke / 2 - 2)
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        arc.color = 0xFF3D362C.toInt()
        c.drawArc(oval, 0f, 360f, false, arc)
        val frac = (steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
        arc.color = if (frac >= 1f) 0xFF8CC46E.toInt() else 0xFFE0A04A.toInt()
        if (frac > 0f) c.drawArc(oval, -90f, (360f * frac).coerceAtLeast(4f), false, arc)

        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = bold }
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3E9D6.toInt() }
        val src = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Кроссовок на светлом кружке — чтобы тёмный силуэт был виден на тёмном фоне.
        c.drawCircle(cx, 112f, 44f, badge)
        icon(ctx, "sport/19")?.let { c.drawBitmap(it, null, RectF(cx - 36, 76f, cx + 36, 148f), src) }

        val stepsText = steps.toString().reversed().chunked(3).joinToString(" ").reversed()
        text.color = 0xFFFFFFFF.toInt()
        fit(text, stepsText, 330f, 104f)
        c.drawText(stepsText, cx, 244f, text)
        text.typeface = Typeface.DEFAULT
        text.color = 0xFFCFC6B8.toInt()
        fit(text, "из $goal", 300f, 36f)
        c.drawText("из $goal", cx, 286f, text)

        val kcal = "$burned"
        text.typeface = bold
        text.color = 0xFFFFA24C.toInt()
        fit(text, kcal, 210f, 78f)
        val w = text.measureText(kcal)
        val iconSize = 62f
        val left = cx - (iconSize + 8 + w) / 2
        icon(ctx, "sport/22")?.let { c.drawBitmap(it, null, RectF(left, 310f, left + iconSize, 310f + iconSize), src) }
        text.textAlign = Paint.Align.LEFT
        c.drawText(kcal, left + iconSize + 8, 366f, text)
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.DEFAULT
        text.color = 0xFFCFC6B8.toInt()
        text.textSize = 32f
        c.drawText("ккал", cx, 404f, text)
        return b
    }
}

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        Graph.init(context)
        Graph.dao.taskNow(id)?.let { Repo.toggleTask(it) }
        TodayWidget().updateAll(context)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
