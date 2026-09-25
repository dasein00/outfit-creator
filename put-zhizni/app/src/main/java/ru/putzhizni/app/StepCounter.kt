package ru.putzhizni.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Шаги за сегодня по системному датчику TYPE_STEP_COUNTER.
 * Датчик отдаёт накопленное число шагов с момента включения телефона, поэтому храним
 * «базу» на начало дня. База снимается в полночь (StepMidnightReceiver) или при первом
 * чтении за день. Перезагрузку телефона (счётчик сбрасывается) учитываем отдельно.
 */
object StepCounter {
    const val PREFS = "steps"

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 29 || ctx.checkSelfPermission(MainActivity.PERM_STEPS) == PackageManager.PERMISSION_GRANTED

    /** Одно чтение датчика; onResult(-1) если датчика нет или нет разрешения. */
    fun read(ctx: Context, timeoutMs: Long = 4000, onResult: (Int) -> Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null || !hasPermission(ctx)) { onResult(-1); return }
        val handler = Handler(Looper.getMainLooper())
        var done = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (done) return
                done = true
                sm.unregisterListener(this)
                onResult(update(ctx, e.values[0].toLong()))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        handler.postDelayed({
            if (!done) {
                done = true
                sm.unregisterListener(listener)
                // Нет свежего события — отдаём последнее известное значение за сегодня.
                val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (p.getString("date", "") == today()) onResult((p.getLong("last", 0) - p.getLong("base", 0)).toInt().coerceAtLeast(0))
                else onResult(-1)
            }
        }, timeoutMs)
    }

    /** Обновляет базу и возвращает шаги за сегодня. */
    fun update(ctx: Context, value: Long, midnight: Boolean = false): Int {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val t = today()
        var base = p.getLong("base", value)
        val last = p.getLong("last", value)
        val date = p.getString("date", "")
        if (date != t || midnight) {
            base = value
        } else if (value < last) {
            // Телефон перезагружался: сохраняем уже пройденное за день.
            base = value - (last - base)
        }
        p.edit().putString("date", t).putLong("base", base).putLong("last", value).apply()
        return (value - base).toInt().coerceAtLeast(0)
    }

    fun scheduleMidnight(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 30)
        val pi = PendingIntent.getBroadcast(ctx, 777, Intent(ctx, StepMidnightReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
    }
}

class StepMidnightReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        StepCounter.scheduleMidnight(ctx)
        if (sensor == null || !StepCounter.hasPermission(ctx)) { pending.finish(); return }
        val handler = Handler(Looper.getMainLooper())
        var done = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (done) return
                done = true
                sm.unregisterListener(this)
                StepCounter.update(ctx, e.values[0].toLong(), midnight = true)
                pending.finish()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        handler.postDelayed({ if (!done) { done = true; sm.unregisterListener(listener); pending.finish() } }, 8000)
    }
}
