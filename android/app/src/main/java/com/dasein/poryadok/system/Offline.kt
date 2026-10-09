package com.dasein.poryadok.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dasein.poryadok.Graph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Чтение без интернета: «В этот день в истории» и «в культуре» загружаются на месяц вперёд и хранятся на телефоне.
 * Факты Small Talks и праздники встроены в приложение и работают без сети всегда.
 *
 * Загрузка запускается при каждом входе в приложение и ещё фоном, как только появится Wi-Fi или мобильная сеть
 * (утром дома — и на весь день и месяц всё уже лежит в телефоне).
 */
object Offline {
    /** Сколько дней вперёд держать загруженными (сегодня + 30). */
    const val DAYS = 31

    data class Status(val stored: Int, val total: Int, val until: LocalDate?, val running: Boolean, val lastAt: Long)

    private val lock = Mutex()
    private val _status = MutableStateFlow(Status(0, DAYS, null, false, 0))
    val status: StateFlow<Status> = _status

    private fun sp(ctx: Context) = ctx.getSharedPreferences("offline", Context.MODE_PRIVATE)

    fun hasNetwork(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    private fun days(from: LocalDate = LocalDate.now()) = (0 until DAYS).map { from.plusDays(it.toLong()) }

    /** Пересчитать, сколько дней из ближайшего месяца уже лежит на телефоне. */
    fun refreshStatus(ctx: Context, running: Boolean = _status.value.running) {
        val list = days()
        val stored = list.count { HistoryRepo.isStored(ctx, it, fresh = false) }
        val until = list.takeWhile { HistoryRepo.isStored(ctx, it, fresh = false) }.lastOrNull()
        _status.value = Status(stored, list.size, until, running, sp(ctx).getLong("last_at", 0))
    }

    /**
     * Догрузить недостающие и устаревшие дни месяца. Возвращает, сколько загружено.
     * Сначала ближайшие дни — если сеть пропадёт на середине, самое нужное уже сохранено.
     */
    suspend fun sync(ctx: Context): Int {
        if (lock.isLocked) return 0
        return lock.withLock {
            refreshStatus(ctx, running = true)
            var got = 0
            var failures = 0
            try {
                if (!hasNetwork(ctx)) return@withLock 0
                for (d in days()) {
                    if (HistoryRepo.isStored(ctx, d)) continue
                    if (HistoryRepo.download(ctx, d)) {
                        got++
                        failures = 0
                        refreshStatus(ctx, running = true)
                    } else if (++failures >= 3) break // сеть пропала — не мучаем телефон
                    delay(150)
                }
                if (got > 0 || failures == 0) sp(ctx).edit().putLong("last_at", System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                Log.w("DASEIN", "offline sync", e)
            } finally {
                refreshStatus(ctx, running = false)
            }
            got
        }
    }

    /** При входе в приложение: сразу пробуем, а если сети нет — WorkManager догрузит, как только она появится. */
    fun onAppStart(ctx: Context) {
        val app = ctx.applicationContext
        val wm = WorkManager.getInstance(app)
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniqueWork("offline_now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<OfflineWorker>().setConstraints(net).build())
        wm.enqueueUniquePeriodicWork(
            "offline_daily", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OfflineWorker>(6, TimeUnit.HOURS).setConstraints(net).build(),
        )
    }
}

class OfflineWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        Offline.sync(applicationContext)
        Offline.refreshStatus(applicationContext)
        val s = Offline.status.value
        return if (s.stored < s.total && runAttemptCount < 3) Result.retry() else Result.success()
    }
}
