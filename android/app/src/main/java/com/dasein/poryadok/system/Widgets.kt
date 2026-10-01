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
import androidx.glance.appwidget.LinearProgressIndicator
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

private val TaskIdKey = ActionParameters.Key<Long>("taskId")
private val RouteKey = ActionParameters.Key<String>(MainActivity.EXTRA_ROUTE)

class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Graph.init(context)
        val model = WidgetModels.load(context)
        val icons = model.rows.mapNotNull { it.icon }.distinct().associateWith { WidgetModels.icon(context, it) }
        provideContent { Content(model, icons) }
    }

    @Composable
    private fun Content(m: WidgetModel, icons: Map<String, Bitmap?>) {
        val size = LocalSize.current
        val context = LocalContext.current
        val cfg = m.cfg
        val pal = cfg.colors()
        val k = cfg.k()
        val compact = size.height < 140.dp
        val header = cfg.header && !compact
        val avail = size.height.value - 20 - (if (header) 34 * cfg.titleScale else 0f)
        val ringDp = ((if (compact) size.height.value - 20 else size.height.value - 56) * cfg.ringScale.coerceIn(0.5f, 1.5f))
            .coerceAtMost(size.width.value * 0.5f).coerceAtLeast(56f)
        val ring = remember(m.steps, m.stepsGoal, m.burned, cfg) { WidgetRing.render(context, m.steps, m.stepsGoal, m.burned, cfg.ringStyle()) }
        val visible = WidgetModels.visible(m.rows, cfg, avail)
        Column(
            GlanceModifier.fillMaxSize().background(ColorProvider(Color(pal.bg))).cornerRadius(cfg.radius.coerceIn(0, 32).dp).padding(10.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (header) {
                val t = k * cfg.titleScale
                Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (cfg.title.isNotBlank()) {
                        Text(cfg.title, maxLines = 1, style = TextStyle(color = ColorProvider(Color(pal.text)), fontSize = (19 * t).sp, fontWeight = FontWeight.Bold))
                        Spacer(GlanceModifier.width(8.dp))
                    }
                    if (cfg.showDate) Text(m.date, style = TextStyle(color = ColorProvider(Color(pal.dim)), fontSize = (14 * t).sp))
                    Spacer(GlanceModifier.defaultWeight())
                    if (m.habitsText != null && m.rows.none { it.route == Routes.HABITS }) Text(
                        m.habitsText, style = TextStyle(color = ColorProvider(Color(pal.accent)), fontSize = (14 * t).sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                if (cfg.ring) {
                    Image(
                        ImageProvider(ring), contentDescription = "${m.steps} шагов из ${m.stepsGoal}, ${m.burned} ккал сожжено",
                        modifier = GlanceModifier.size(ringDp.dp).clickable(actionStartActivity<MainActivity>(actionParametersOf(RouteKey to Routes.STEPS))),
                    )
                    Spacer(GlanceModifier.width(10.dp))
                }
                Column(GlanceModifier.defaultWeight()) {
                    if (visible.isEmpty()) Text(
                        "Выберите блоки в настройках виджета", style = TextStyle(color = ColorProvider(Color(pal.dim)), fontSize = (14 * k).sp),
                        modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>(actionParametersOf(RouteKey to Routes.WIDGET_EDITOR))),
                    )
                    visible.forEach { r -> RowView(r, icons[r.icon ?: ""], pal, k) }
                }
            }
        }
    }

    @Composable
    private fun RowView(r: WRow, icon: Bitmap?, pal: WColors, k0: Float) {
        val b = r.block
        val k = k0 * (b?.scale ?: 1f)
        val valueColor = Color(b?.valueColor?.takeIf { it != 0L }?.toInt() ?: pal.text)
        val labelColor = Color(b?.labelColor?.takeIf { it != 0L }?.toInt() ?: pal.dim)
        val showIcon = icon != null && (b?.icon ?: true)
        if (r.kind == 3) {
            Row(
                GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to (r.taskId ?: 0L)))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("○", style = TextStyle(color = ColorProvider(Color(pal.accent)), fontSize = (18 * k).sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(6.dp))
                val c = if (r.tone == WidgetModels.TONE_TEXT) valueColor else Color(pal.tone(r.tone))
                Text(r.title, maxLines = 1, style = TextStyle(color = ColorProvider(c), fontSize = (16 * k).sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
                if (r.value.isNotEmpty()) Text(" " + r.value, style = TextStyle(color = ColorProvider(labelColor), fontSize = (14 * k).sp))
            }
            return
        }
        val open = r.route?.let { actionStartActivity<MainActivity>(actionParametersOf(RouteKey to it)) } ?: actionStartActivity<MainActivity>()
        if (r.kind == 4) {
            Text(
                r.title, maxLines = 1, style = TextStyle(color = ColorProvider(labelColor), fontSize = (12.5f * k).sp),
                modifier = GlanceModifier.fillMaxWidth().padding(start = if (b?.icon != false) (28 * k).dp else 0.dp, bottom = 1.dp).clickable(open),
            )
            return
        }
        Column(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(open)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showIcon) {
                    Image(ImageProvider(icon!!), r.title, modifier = GlanceModifier.size((22 * k).dp))
                    Spacer(GlanceModifier.width(6.dp))
                }
                if (r.value.isBlank()) {
                    val c = if (r.tone == WidgetModels.TONE_TEXT || (b != null && b.valueColor != 0L)) valueColor else Color(pal.tone(r.tone))
                    Text(r.title, maxLines = 1, style = TextStyle(color = ColorProvider(c), fontSize = (15 * k).sp, fontWeight = FontWeight.Medium))
                } else {
                    if (!showIcon || (b?.label?.isNotBlank() == true)) {
                        Text(r.title + " ", maxLines = 1, style = TextStyle(color = ColorProvider(labelColor), fontSize = (14 * k).sp))
                    }
                    Text(r.value, maxLines = 1, style = TextStyle(color = ColorProvider(valueColor), fontSize = (18 * k).sp, fontWeight = FontWeight.Bold))
                }
                if (r.sub.isNotBlank()) {
                    Spacer(GlanceModifier.width(6.dp))
                    val c = if (r.tone == WidgetModels.TONE_TEXT || r.tone == WidgetModels.TONE_DIM) labelColor else Color(pal.tone(r.tone))
                    Text(r.sub, maxLines = 1, style = TextStyle(color = ColorProvider(c), fontSize = (13 * k).sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                }
            }
            if (r.kind == 1 && r.progress != null) {
                Spacer(GlanceModifier.height(3.dp))
                LinearProgressIndicator(
                    progress = r.progress.coerceIn(0f, 1f), modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = ColorProvider(Color(if (r.progress >= 1f) pal.good else pal.accent)), backgroundColor = ColorProvider(Color(pal.track)),
                )
            }
            if (r.kind == 2 && r.spark.size >= 2) {
                val spark = remember(r.spark) { WidgetRing.spark(r.spark) }
                Image(ImageProvider(spark), "График", modifier = GlanceModifier.fillMaxWidth().height(34.dp))
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

    fun render(ctx: Context, steps: Int, goal: Int, burned: Int, ringStyle: RingStyle = RingStyle()): Bitmap {
        val b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val cx = S / 2f
        val stroke = S * 0.08f
        val oval = RectF(stroke / 2 + 2, stroke / 2 + 2, S - stroke / 2 - 2, S - stroke / 2 - 2)
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        arc.color = ringStyle.track
        c.drawArc(oval, 0f, 360f, false, arc)
        val frac = (steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
        arc.color = if (frac >= 1f) ringStyle.done else ringStyle.progress
        if (frac > 0f) c.drawArc(oval, -90f, (360f * frac).coerceAtLeast(4f), false, arc)

        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = bold }
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3E9D6.toInt() }
        val src = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Кроссовок на светлом кружке — чтобы тёмный силуэт был виден на тёмном фоне.
        c.drawCircle(cx, 112f, 44f, badge)
        icon(ctx, "sport/19")?.let { c.drawBitmap(it, null, RectF(cx - 36, 76f, cx + 36, 148f), src) }

        val stepsText = steps.toString().reversed().chunked(3).joinToString(" ").reversed()
        text.color = ringStyle.steps
        fit(text, stepsText, 330f, 104f)
        c.drawText(stepsText, cx, if (ringStyle.showGoal) 244f else 262f, text)
        text.typeface = Typeface.DEFAULT
        text.color = ringStyle.dim
        if (ringStyle.showGoal) {
            fit(text, "из $goal", 300f, 36f)
            c.drawText("из $goal", cx, 286f, text)
        }

        val kcal = "$burned"
        text.typeface = bold
        text.color = ringStyle.kcal
        fit(text, kcal, 210f, 78f)
        val w = text.measureText(kcal)
        val iconSize = 62f
        val left = cx - (iconSize + 8 + w) / 2
        icon(ctx, "sport/22")?.let { c.drawBitmap(it, null, RectF(left, 310f, left + iconSize, 310f + iconSize), src) }
        text.textAlign = Paint.Align.LEFT
        c.drawText(kcal, left + iconSize + 8, 366f, text)
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.DEFAULT
        text.color = ringStyle.dim
        fit(text, ringStyle.kcalLabel, 300f, 32f)
        if (ringStyle.kcalLabel.isNotBlank()) c.drawText(ringStyle.kcalLabel, cx, 404f, text)
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
