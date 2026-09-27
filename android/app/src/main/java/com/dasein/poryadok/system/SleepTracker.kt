package com.dasein.poryadok.system

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.SleepAuto
import com.dasein.poryadok.data.SleepEntry
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.SleepDetect
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Автоопределение сна. Android хранит историю включения экрана и разблокировок (статистика использования),
 * поэтому приложению не нужно работать всю ночь: утром оно смотрит, когда телефон перестали и начали использовать.
 */
object SleepTracker {
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun events(ctx: Context, from: Long, to: Long): List<SleepDetect.Event> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val q = usm.queryEvents(from, to) ?: return emptyList()
        val out = mutableListOf<SleepDetect.Event>()
        val e = UsageEvents.Event()
        while (q.hasNextEvent()) {
            q.getNextEvent(e)
            val kind = when (e.eventType) {
                15 -> SleepDetect.Kind.SCREEN_ON      // SCREEN_INTERACTIVE
                16 -> SleepDetect.Kind.SCREEN_OFF     // SCREEN_NON_INTERACTIVE
                18 -> SleepDetect.Kind.UNLOCK         // KEYGUARD_HIDDEN
                1 -> if (e.packageName == ctx.packageName) null else SleepDetect.Kind.APP // ACTIVITY_RESUMED
                else -> null
            } ?: continue
            out += SleepDetect.Event(e.timeStamp, kind)
        }
        return out
    }

    private fun midnight(day: Long): Long = LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun minOfDay(ms: Long) = Dates.minutesOf(ms)

    /** Персональная подстройка по прошлым исправлениям. */
    suspend fun calibration(): SleepDetect.Calibration {
        val corrected = Graph.extra.sleepAutoNow().take(30)
        return SleepDetect.calibrate(
            corrected.mapNotNull { a -> a.userBedMin?.let { minOfDay(a.lastUseAt) to it } },
            corrected.mapNotNull { a -> a.userWakeMin?.let { minOfDay(a.firstUseAt) to it } },
        )
    }

    /**
     * Определяет сон за последние [nights] ночей. Если за утро нет записи сна — создаёт её;
     * запись, которую человек поправил, не трогает. Возвращает определение за сегодняшнее утро.
     */
    suspend fun run(ctx: Context, nights: Int = 7): SleepAuto? {
        if (!Graph.prefs.now().sleepAuto || !hasAccess(ctx)) return null
        val today = Dates.today()
        val now = System.currentTimeMillis()
        val cal = calibration()
        var result: SleepAuto? = null
        for (d in (today - nights + 1)..today) {
            val m = midnight(d)
            // Утро ещё не наступило или вы ещё можете спать — сегодняшнюю ночь считаем после 5:00.
            if (d == today && now < m + 5 * 3_600_000L) continue
            val ev = events(ctx, m - 11 * 3_600_000L, minOf(now, m + 14 * 3_600_000L))
            val g = SleepDetect.detect(ev, m, cal) ?: continue
            // Утро ещё идёт, а телефоном после паузы не пользовались — ждём.
            if (d == today && g.firstUseAt > now) continue
            val prev = Graph.extra.sleepAutoOf(d)
            val auto = SleepAuto(
                day = d, lastUseAt = g.lastUseAt, firstUseAt = g.firstUseAt, sleepAt = g.sleepAt, wakeAt = g.wakeAt,
                awakenings = g.awakenings, glances = g.glances, confidence = g.confidence,
                applied = prev?.applied ?: false, userBedMin = prev?.userBedMin, userWakeMin = prev?.userWakeMin,
            )
            val existing = Graph.dao.sleepNow(d)
            val corrected = prev?.userBedMin != null || prev?.userWakeMin != null
            val mine = existing != null && (prev == null || !prev.applied)
            if (!corrected && !mine) {
                Graph.dao.upsertSleep(
                    SleepEntry(d, minOfDay(g.sleepAt), minOfDay(g.wakeAt), existing?.quality ?: 3, existing?.note ?: "")
                )
                Graph.extra.upsertSleepAuto(auto.copy(applied = true))
            } else Graph.extra.upsertSleepAuto(auto)
            if (d == today) result = auto
        }
        return result
    }

    /** Человек поправил сон за [day] — запоминаем, чтобы подстроить алгоритм. */
    suspend fun onUserEdit(day: Long, bedMin: Int, wakeMin: Int) {
        val a = Graph.extra.sleepAutoOf(day) ?: return
        val sameBed = bedMin == minOfDay(a.sleepAt)
        val sameWake = wakeMin == minOfDay(a.wakeAt)
        if (sameBed && sameWake) return
        Graph.extra.upsertSleepAuto(a.copy(userBedMin = if (sameBed) a.userBedMin else bedMin, userWakeMin = if (sameWake) a.userWakeMin else wakeMin))
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<SleepWorker>(2, TimeUnit.HOURS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("sleep", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    suspend fun ensureScheduled(ctx: Context) {
        if (Graph.prefs.now().sleepAuto) schedule(ctx) else WorkManager.getInstance(ctx).cancelUniqueWork("sleep")
    }
}

class SleepWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        runCatching { SleepTracker.run(applicationContext) }
        return Result.success()
    }
}
