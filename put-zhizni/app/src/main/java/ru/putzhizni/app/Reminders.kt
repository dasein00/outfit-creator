package ru.putzhizni.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object Notifications {
    const val CHANNEL = "reminders"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Напоминания о делах, привычках, платежах и занятиях"
            nm.createNotificationChannel(ch)
        }
    }

    fun allowed(ctx: Context): Boolean = ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun show(ctx: Context, id: Int, title: String, text: String) {
        ensureChannel(ctx)
        if (!allowed(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setColor(0xFF8E7CC3.toInt())
            .build()
        try { ctx.getSystemService(NotificationManager::class.java).notify(id, n) } catch (_: SecurityException) {}
    }
}

/**
 * Хранит список напоминаний, присланный интерфейсом, и ставит системные будильники
 * на ближайшее срабатывание каждого. После срабатывания напоминание переставляется на следующее.
 *
 * Формат элемента: {id, title, text, type: once|daily|weekdays|monthly, time: "HH:MM",
 *                   date: "YYYY-MM-DD" (для once), days: [1..7] (1 — понедельник), dom: число месяца}
 */
object ReminderScheduler {
    private const val PREFS = "reminders"
    private const val KEY_LIST = "list"
    private const val KEY_IDS = "scheduled_ids"

    fun replaceAll(ctx: Context, json: String) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Снимаем все ранее поставленные будильники.
        p.getString(KEY_IDS, "")!!.split(',').filter { it.isNotBlank() }.forEach { cancel(ctx, it.toInt()) }
        p.edit().putString(KEY_LIST, json).putString(KEY_IDS, "").apply()
        rescheduleAll(ctx)
    }

    fun rescheduleAll(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try { JSONArray(p.getString(KEY_LIST, "[]")) } catch (e: Exception) { JSONArray() }
        val ids = ArrayList<Int>()
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val at = nextTime(r, now) ?: continue
            val code = r.optString("id").hashCode()
            schedule(ctx, code, at, r.toString())
            ids.add(code)
        }
        p.edit().putString(KEY_IDS, ids.joinToString(",")).apply()
    }

    fun find(ctx: Context, id: String): JSONObject? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try { JSONArray(p.getString(KEY_LIST, "[]")) } catch (e: Exception) { return null }
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (r.optString("id") == id) return r
        }
        return null
    }

    /** Ближайшее время срабатывания строго позже [after] или null. */
    fun nextTime(r: JSONObject, after: Long): Long? {
        val time = r.optString("time", "09:00").split(':')
        val hh = time.getOrNull(0)?.toIntOrNull() ?: 9
        val mm = time.getOrNull(1)?.toIntOrNull() ?: 0
        val c = Calendar.getInstance()
        c.timeInMillis = after
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        when (r.optString("type")) {
            "once" -> {
                val d = r.optString("date").split('-')
                if (d.size != 3) return null
                c.set(d[0].toInt(), d[1].toInt() - 1, d[2].toInt(), hh, mm, 0)
                return if (c.timeInMillis > after) c.timeInMillis else null
            }
            "daily" -> {
                c.set(Calendar.HOUR_OF_DAY, hh); c.set(Calendar.MINUTE, mm)
                if (c.timeInMillis <= after) c.add(Calendar.DAY_OF_MONTH, 1)
                return c.timeInMillis
            }
            "weekdays" -> {
                val days = r.optJSONArray("days") ?: return null
                val set = HashSet<Int>()
                for (i in 0 until days.length()) set.add(days.optInt(i))
                if (set.isEmpty()) return null
                c.set(Calendar.HOUR_OF_DAY, hh); c.set(Calendar.MINUTE, mm)
                for (k in 0..7) {
                    val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 // 1 = понедельник
                    if (set.contains(dow) && c.timeInMillis > after) return c.timeInMillis
                    c.add(Calendar.DAY_OF_MONTH, 1)
                }
                return null
            }
            "monthly" -> {
                val dom = r.optInt("dom", 1).coerceIn(1, 31)
                for (k in 0..12) {
                    val max = c.getActualMaximum(Calendar.DAY_OF_MONTH)
                    c.set(Calendar.DAY_OF_MONTH, minOf(dom, max))
                    c.set(Calendar.HOUR_OF_DAY, hh); c.set(Calendar.MINUTE, mm)
                    if (c.timeInMillis > after) return c.timeInMillis
                    c.set(Calendar.DAY_OF_MONTH, 1)
                    c.add(Calendar.MONTH, 1)
                }
                return null
            }
        }
        return null
    }

    private fun intent(ctx: Context, code: Int, payload: String?): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.action = "ru.putzhizni.app.REMIND.$code"
        if (payload != null) i.putExtra("r", payload)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun schedule(ctx: Context, code: Int, at: Long, payload: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = intent(ctx, code, payload)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context, code: Int) {
        ctx.getSystemService(AlarmManager::class.java).cancel(intent(ctx, code, null))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val payload = intent.getStringExtra("r") ?: return
        val r = try { JSONObject(payload) } catch (e: Exception) { return }
        val id = r.optString("id")
        // Показываем, только если напоминание всё ещё в актуальном списке.
        val current = ReminderScheduler.find(ctx, id) ?: return
        Notifications.show(ctx, id.hashCode(), current.optString("title", "Путь жизни"), current.optString("text", ""))
        val next = ReminderScheduler.nextTime(current, System.currentTimeMillis() + 30_000)
        if (next != null) ReminderScheduler.schedule(ctx, id.hashCode(), next, current.toString())
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ReminderScheduler.rescheduleAll(ctx)
        StepCounter.scheduleMidnight(ctx)
    }
}
