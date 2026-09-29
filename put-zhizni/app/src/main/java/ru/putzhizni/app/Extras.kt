package ru.putzhizni.app

import android.app.Activity
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Telephony
import android.view.View
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/* ---------------- SMS от 900 (Сбербанк) ---------------- */
object SmsReader {
    private val SENDERS = setOf("900", "+7900", "SBERBANK", "Sberbank")

    /** SMS от 900 за последние [days] дней: [{id, date, body}]. */
    fun list(ctx: Context, days: Int): String {
        val arr = JSONArray()
        val since = System.currentTimeMillis() - days * 86_400_000L
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY),
            Telephony.Sms.DATE + " > ?", arrayOf(since.toString()), Telephony.Sms.DATE + " DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val addr = c.getString(1) ?: continue
                if (SENDERS.none { addr.equals(it, true) }) continue
                arr.put(JSONObject().put("id", "sms" + c.getLong(0)).put("date", c.getLong(2)).put("body", c.getString(3) ?: ""))
            }
        }
        return arr.toString()
    }
}

/** Мониторинг новых SMS от 900: уведомление о найденной операции, запись — при открытии приложения. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!ctx.getSharedPreferences("flags", Context.MODE_PRIVATE).getBoolean("sms", false)) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val from = msgs.firstOrNull()?.originatingAddress ?: return
        if (from != "900" && from != "+7900") return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        if (!Regex("(покупка|оплата|списан|зачислен|перевод|выдача)", RegexOption.IGNORE_CASE).containsMatchIn(body)) return
        Notifications.show(ctx, body.hashCode(), "📩 Новая операция по карте", body.take(120) + "\nОткройте приложение — операция добавится в финансы.", "money/ops")
    }
}

/* ---------------- Определение сна по использованию телефона ---------------- */
object SleepDetector {
    fun allowed(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Самый длинный период выключенного экрана с 18:00 предыдущего дня до 13:00 указанного дня.
     * Возвращает {"bed":"HH:mm","wake":"HH:mm"} или пустую строку.
     */
    fun detect(ctx: Context, date: String): String {
        if (Build.VERSION.SDK_INT < 28 || !allowed(ctx)) return ""
        val p = date.split("-").map { it.toInt() }
        val end = Calendar.getInstance().apply { set(p[0], p[1] - 1, p[2], 13, 0, 0) }
        val start = (end.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, -19) }
        val to = minOf(end.timeInMillis, System.currentTimeMillis())
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ev = usm.queryEvents(start.timeInMillis, to)
        val e = UsageEvents.Event()
        var blockStart = start.timeInMillis
        var off = true
        var onAt = 0L
        var bestFrom = 0L; var bestTo = 0L
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (off && e.timeStamp - blockStart > bestTo - bestFrom) { bestFrom = blockStart; bestTo = e.timeStamp }
                    off = false; onAt = e.timeStamp
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    // Короткое включение экрана ночью (до 3 минут) не прерывает сон.
                    if (!off) { if (e.timeStamp - onAt > 180_000L) blockStart = e.timeStamp; off = true }
                }
            }
        }
        if (bestTo - bestFrom < 3 * 3600_000L) return ""
        val f = SimpleDateFormat("HH:mm", Locale.US)
        return JSONObject().put("bed", f.format(Date(bestFrom))).put("wake", f.format(Date(bestTo))).toString()
    }
}

/* ---------------- Фото (прогресс, рецепты) ---------------- */
object Photos {
    fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }
    fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "")
    fun save(ctx: Context, id: String, bytes: ByteArray) = File(dir(ctx), safe(id) + ".jpg").writeBytes(bytes)
    fun delete(ctx: Context, id: String) { File(dir(ctx), safe(id) + ".jpg").delete() }
    fun file(ctx: Context, name: String): File? { val f = File(dir(ctx), safe(name.removeSuffix(".jpg")) + ".jpg"); return if (f.exists()) f else null }
}

/* ---------------- Виджет на главном экране ---------------- */
class LifeWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { render(ctx); Background.schedule(ctx) }
    override fun onEnabled(ctx: Context) { Background.schedule(ctx) }

    companion object {
        private fun open(ctx: Context, route: String, code: Int): PendingIntent =
            PendingIntent.getActivity(ctx, 1000 + code, Intent(ctx, MainActivity::class.java).putExtra("route", route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun render(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, LifeWidget::class.java))
            if (ids.isEmpty()) return
            val prefs = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val o = try { JSONObject(prefs.getString("data", "{}")!!) } catch (e: Exception) { JSONObject() }
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            // Шаги: берём максимум из данных приложения и фонового обновления за сегодня.
            var steps = o.optLong("steps", 0)
            if (prefs.getString("liveDate", "") == today) steps = maxOf(steps, prefs.getLong("liveSteps", 0))
            if (prefs.getString("dataDate", "") != today) steps = if (prefs.getString("liveDate", "") == today) prefs.getLong("liveSteps", 0) else 0
            val goal = o.optLong("goal", 8000).coerceAtLeast(1)
            val v = RemoteViews(ctx.packageName, R.layout.widget_life)
            val dow = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date())
            v.setTextViewText(R.id.w_date, dow.replaceFirstChar { it.uppercase() })
            v.setTextViewText(R.id.w_steps, String.format(Locale("ru"), "%,d", steps).replace(',', ' '))
            v.setTextViewText(R.id.w_goal, "из " + String.format(Locale("ru"), "%,d", goal).replace(',', ' '))
            v.setProgressBar(R.id.w_ring, 100, ((steps * 100) / goal).toInt().coerceIn(0, 100), false)
            v.setTextViewText(R.id.w_burn, "🔥 " + o.optInt("burn", 0) + " ккал")
            val w = o.optDouble("weight", Double.NaN)
            v.setTextViewText(R.id.w_weight, if (w.isNaN()) "⚖ —" else String.format(Locale("ru"), "⚖ %.1f кг %s", w, o.optString("arrow", "")))
            v.setImageViewBitmap(R.id.w_spark, spark(o.optJSONArray("spark")))
            val tasks = o.optJSONArray("tasks") ?: JSONArray()
            v.setTextViewText(R.id.w_task1, if (tasks.length() > 0) "☐ " + tasks.optString(0) else "Задач на сегодня нет 💜")
            v.setTextViewText(R.id.w_task2, if (tasks.length() > 1) "☐ " + tasks.optString(1) else "")
            v.setViewVisibility(R.id.w_task2, if (tasks.length() > 1) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.w_date, open(ctx, "summary", 1))
            v.setOnClickPendingIntent(R.id.w_steps_box, open(ctx, "health/steps", 2))
            v.setOnClickPendingIntent(R.id.w_burn, open(ctx, "workouts", 3))
            v.setOnClickPendingIntent(R.id.w_weight_box, open(ctx, "health/weight", 4))
            v.setOnClickPendingIntent(R.id.w_tasks_box, open(ctx, "tasks", 5))
            mgr.updateAppWidget(ids, v)
        }

        private fun spark(arr: JSONArray?): Bitmap {
            val W = 240; val H = 60
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val vals = ArrayList<Pair<Int, Double>>()
            if (arr != null) for (i in 0 until arr.length()) if (!arr.isNull(i)) vals.add(i to arr.optDouble(i))
            if (vals.size < 2) return bmp
            val mn = vals.minOf { it.second }; val mx = vals.maxOf { it.second }; val r = if (mx - mn < 0.1) 1.0 else mx - mn
            val n = (arr?.length() ?: 7).coerceAtLeast(2) - 1
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6C9FD3"); strokeWidth = 5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
            val path = Path()
            vals.forEachIndexed { k, (i, v) ->
                val x = 8f + (W - 16f) * i / n; val y = H - 8f - (H - 16f) * ((v - mn) / r).toFloat()
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            c.drawPath(path, p)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6C9FD3") }
            vals.forEach { (i, v) -> c.drawCircle(8f + (W - 16f) * i / n, H - 8f - (H - 16f) * ((v - mn) / r).toFloat(), 5f, dot) }
            return bmp
        }
    }
}

/* ---------------- Фоновое обновление каждые 15 минут ---------------- */
object Background {
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 555, Intent(ctx, BgReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi)
    }
}

class BgReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val prefs = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val hcOn = ctx.getSharedPreferences("flags", Context.MODE_PRIVATE).getBoolean("hc", false)
        CoroutineScope(Dispatchers.IO).launch {
            var steps: Long? = null
            if (hcOn && HealthSync.available(ctx)) steps = HealthSync.stepsToday(ctx)
            fun finish(s: Long?) {
                if (s != null && s >= 0) prefs.edit().putString("liveDate", today).putLong("liveSteps", maxOf(s, if (prefs.getString("liveDate", "") == today) prefs.getLong("liveSteps", 0) else 0)).apply()
                LifeWidget.render(ctx)
                pending.finish()
            }
            if (steps != null) finish(steps)
            else android.os.Handler(android.os.Looper.getMainLooper()).post {
                StepCounter.read(ctx, 3000) { s -> finish(if (s >= 0) s.toLong() else null) }
            }
        }
    }
}

/* ---------------- Экран о приватности для Health Connect ---------------- */
class HealthPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = TextView(this)
        t.setPadding(48, 64, 48, 48)
        t.textSize = 16f
        t.text = "Путь жизни читает из Health Connect только шаги, вес, сон, тренировки и активные калории, чтобы показывать их в вашем дневнике.\n\nДанные хранятся только на этом телефоне, никуда не отправляются и не передаются третьим лицам. Приложение ничего не записывает в Health Connect.\n\nОтключить доступ можно в настройках Health Connect в любой момент."
        setContentView(ScrollView(this).apply { addView(t) })
    }
}

