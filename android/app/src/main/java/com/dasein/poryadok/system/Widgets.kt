package com.dasein.poryadok.system

import android.content.Context
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
import com.dasein.poryadok.logic.HabitSchedule

object Widgets {
    suspend fun refresh(ctx: Context) {
        try {
            TodayWidget().updateAll(ctx)
        } catch (_: Exception) {
        }
    }
}

private data class WidgetTask(val id: Long, val title: String, val time: String, val overdue: Boolean)
private data class WidgetData(val tasks: List<WidgetTask>, val more: Int, val habitsDone: Int, val habitsTotal: Int, val date: String)

private val TaskIdKey = ActionParameters.Key<Long>("taskId")

private val INK = Color(0xFF211D18)
private val TEXT = Color(0xFFF0ECE3)
private val DIM = Color(0xFFA79E90)
private val BRASS = Color(0xFFC79246)
private val DANGER = Color(0xFFD27A63)

class TodayWidget : GlanceAppWidget() {
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
        return WidgetData(
            tasks.take(5).map { t ->
                WidgetTask(t.id, t.title, t.dueMin?.let { Dates.time(it) } ?: "", (t.dueDay ?: today) < today)
            },
            (tasks.size - 5).coerceAtLeast(0), done, habits.size,
            "${Dates.weekdayShort(today)}, ${Dates.short(today)}",
        )
    }

    @Composable
    private fun Content(d: WidgetData) {
        Column(
            GlanceModifier.fillMaxSize().background(ColorProvider(INK)).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Сегодня", style = TextStyle(color = ColorProvider(TEXT), fontSize = 17.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(8.dp))
                Text(d.date, style = TextStyle(color = ColorProvider(DIM), fontSize = 12.sp))
                Spacer(GlanceModifier.defaultWeight())
                if (d.habitsTotal > 0) Text(
                    "Привычки ${d.habitsDone}/${d.habitsTotal}",
                    style = TextStyle(color = ColorProvider(BRASS), fontSize = 12.sp),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            if (d.tasks.isEmpty()) {
                Text("Задач на сегодня нет ✨", style = TextStyle(color = ColorProvider(DIM), fontSize = 14.sp))
            }
            d.tasks.forEach { t ->
                Row(
                    GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to t.id))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("○", style = TextStyle(color = ColorProvider(BRASS), fontSize = 16.sp))
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        t.title, maxLines = 1,
                        style = TextStyle(color = ColorProvider(if (t.overdue) DANGER else TEXT), fontSize = 14.sp),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    if (t.time.isNotEmpty()) Text(t.time, style = TextStyle(color = ColorProvider(DIM), fontSize = 12.sp))
                }
            }
            if (d.more > 0) Text("и ещё ${d.more}", style = TextStyle(color = ColorProvider(DIM), fontSize = 12.sp))
        }
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
