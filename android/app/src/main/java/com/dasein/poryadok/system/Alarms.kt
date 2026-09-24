package com.dasein.poryadok.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dasein.poryadok.Graph
import com.dasein.poryadok.MainActivity
import com.dasein.poryadok.R
import com.dasein.poryadok.Repo
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.HabitSchedule
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.logic.Repeat
import com.dasein.poryadok.logic.nextOccurrence
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.logic.upcomingOccurrence
import com.dasein.poryadok.ui.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Alarms {
    const val CH_REMIND = "reminders"
    const val CH_HABITS = "habits"
    const val CH_FOCUS = "focus"
    const val CH_SUMMARY = "summary"

    private const val PREFS = "alarms"
    private const val KEY_CODES = "codes"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Напоминания", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Задачи, события, напоминалки и платежи"
        })
        nm.createNotificationChannel(NotificationChannel(CH_HABITS, "Привычки", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_FOCUS, "Фокус-таймер", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_SUMMARY, "Сводка дня", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun notify(ctx: Context, id: Int, channel: String, title: String, text: String, route: String? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route)
        }
        val pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(if (channel == CH_REMIND || channel == CH_FOCUS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    private data class Plan(val code: Int, val at: Long, val kind: String, val id: Long)

    private fun code(kind: String, id: Long): Int = (kind.hashCode() * 31 + id.hashCode()) and 0x7fffffff

    /** Отменяет все поставленные будильники и ставит ближайшие заново по данным из базы. */
    suspend fun rescheduleAll(ctx: Context) {
        val dao = Graph.dao
        val now = System.currentTimeMillis()
        val today = Dates.today()
        val plans = mutableListOf<Plan>()
        fun add(kind: String, id: Long, at: Long) {
            if (at > now) plans += Plan(code(kind, id), at, kind, id)
        }

        dao.tasksWithReminders().forEach { t ->
            val d = t.dueDay ?: return@forEach
            add("task", t.id, Dates.millis(d, t.dueMin ?: 9 * 60))
        }
        dao.eventsNow().forEach { e ->
            val before = e.remindBefore ?: return@forEach
            val rep = Repeat.of(e.repeat)
            var occ = upcomingOccurrence(e.day, rep, today) ?: return@forEach
            repeat(3) {
                val at = Dates.millis(occ, e.startMin ?: 9 * 60) - before * 60_000L
                if (at > now) {
                    add("event", e.id, at); return@forEach
                }
                occ = upcomingOccurrence(e.day, rep, occ + 1) ?: return@forEach
            }
        }
        dao.openReminders().forEach { r ->
            val rep = Repeat.of(r.repeat)
            var d = r.day
            var at = Dates.millis(d, r.min)
            var guard = 0
            while (at <= now && rep != Repeat.NONE && guard++ < 400) {
                d = nextOccurrence(d, rep, r.day) ?: break
                at = Dates.millis(d, r.min)
            }
            add("reminder", r.id, at)
        }
        dao.habitsNow().forEach { h ->
            val m = h.remindMin ?: return@forEach
            val sched = HabitSchedule(h.daysMask, h.timesPerWeek)
            for (k in 0..7) {
                val d = today + k
                val at = Dates.millis(d, m)
                if (at > now && sched.isScheduled(d)) { add("habit", h.id, at); break }
            }
        }
        dao.activeRecurring().filter { it.remind }.forEach { r ->
            add("bill", r.id, Dates.millis(r.nextDay, 10 * 60))
        }
        val s = Graph.prefs.now()
        if (s.summaryOn) {
            val t = Dates.millis(today, s.summaryHour * 60)
            add("summary", 0, if (t > now) t else Dates.millis(today + 1, s.summaryHour * 60))
        }
        if (s.eveningReviewOn) {
            val t = Dates.millis(today, 21 * 60)
            add("evening", 0, if (t > now) t else Dates.millis(today + 1, 21 * 60))
        }
        if (s.focusPhase.isNotEmpty() && s.focusEndsAt > now) add("focus", 0, s.focusEndsAt)

        val am = ctx.getSystemService(AlarmManager::class.java)
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = sp.getStringSet(KEY_CODES, emptySet()).orEmpty()
        val newCodes = plans.map { it.code.toString() }.toSet()
        old.filter { it !in newCodes }.forEach { c ->
            val pi = PendingIntent.getBroadcast(
                ctx, c.toInt(), Intent(ctx, AlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) { am.cancel(pi); pi.cancel() }
        }
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        plans.forEach { p ->
            val intent = Intent(ctx, AlarmReceiver::class.java).putExtra("kind", p.kind).putExtra("id", p.id)
            val pi = PendingIntent.getBroadcast(ctx, p.code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.at, pi)
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.at, pi)
            }
        }
        sp.edit().putStringSet(KEY_CODES, newCodes).apply()
    }

    /** Что показать, когда будильник сработал. */
    suspend fun fire(ctx: Context, kind: String, id: Long) {
        val dao = Graph.dao
        val today = Dates.today()
        val nid = code(kind, id)
        when (kind) {
            "task" -> dao.taskNow(id)?.takeIf { !it.done }?.let { t ->
                notify(ctx, nid, CH_REMIND, "Задача: ${t.title}", t.note.ifBlank { "Пора сделать" }, Routes.task(t.id))
            }
            "event" -> dao.eventsNow().firstOrNull { it.id == id }?.let { e ->
                val time = e.startMin?.let { Dates.time(it) } ?: "сегодня"
                notify(ctx, nid, CH_REMIND, e.title, "Начало в $time" + if (e.location.isNotBlank()) " · ${e.location}" else "", Routes.CALENDAR)
            }
            "reminder" -> dao.reminderNow(id)?.takeIf { !it.done }?.let { r ->
                notify(ctx, nid, CH_REMIND, "Напоминание", r.title, Routes.calendar(1))
                if (Repeat.of(r.repeat) != Repeat.NONE) Repo.advanceReminder(r)
            }
            "habit" -> {
                val h = dao.habitsNow().firstOrNull { it.id == id } ?: return
                val v = dao.habitLogsOn(today).firstOrNull { it.habitId == id }?.value ?: 0
                if (v < h.target) notify(ctx, nid, CH_HABITS, "${h.emoji} ${h.name}", "Не забудьте про привычку сегодня", Routes.HABITS)
            }
            "bill" -> dao.activeRecurring().firstOrNull { it.id == id }?.let { r ->
                val cur = Graph.prefs.now().currency
                notify(
                    ctx, nid, CH_REMIND, "Платёж: ${r.title}",
                    "${Money.format(r.amount, cur)} — сегодня" + if (r.autoAdd) ". Операция будет добавлена автоматически." else "",
                    Routes.finance(3),
                )
            }
            "summary" -> {
                val tasks = dao.openTasksUntil(today)
                val overdue = tasks.count { (it.dueDay ?: today) < today }
                val habits = dao.habitsNow().count { HabitSchedule(it.daysMask, it.timesPerWeek).isScheduled(today) }
                val events = dao.eventsNow().count { com.dasein.poryadok.logic.occursOn(it.day, Repeat.of(it.repeat), today) }
                val parts = buildList {
                    add("${tasks.size} ${plural(tasks.size, "задача", "задачи", "задач")}" + if (overdue > 0) " (просрочено $overdue)" else "")
                    if (habits > 0) add("$habits ${plural(habits, "привычка", "привычки", "привычек")}")
                    if (events > 0) add("$events ${plural(events, "событие", "события", "событий")}")
                }
                notify(ctx, nid, CH_SUMMARY, "План на сегодня", parts.joinToString(", "), Routes.TODAY)
            }
            "evening" -> {
                val spent = dao.txns().first().filter { it.day == today && it.type == TxnType.EXPENSE }.sumOf { it.amount }
                val hasMood = dao.moods().first().any { it.day == today }
                val text = buildString {
                    append(if (hasMood) "Отметьте привычки и подведите итог дня." else "Как прошёл день? Отметьте настроение и привычки.")
                    if (spent > 0) append(" Потрачено сегодня: ${Money.format(spent, Graph.prefs.now().currency)}.")
                }
                notify(ctx, nid, CH_SUMMARY, "Вечерний итог", text, Routes.wellbeing(0))
            }
            "focus" -> Focus.finishIfDue()?.let { phase -> Focus.announce(ctx, phase) }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: return
        val id = intent.getLongExtra("id", 0)
        val pending = goAsync()
        Graph.init(context)
        Graph.scope.launch {
            try {
                Alarms.fire(context, kind, id)
                Repo.processRecurring()
                Alarms.rescheduleAll(context)
                Widgets.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Graph.init(context)
        Graph.scope.launch {
            try {
                Repo.processRecurring()
                Alarms.rescheduleAll(context)
                Widgets.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
