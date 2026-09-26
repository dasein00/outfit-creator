package com.dasein.poryadok.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.DayLog
import com.dasein.poryadok.logic.Dates
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.Period
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Шаги из двух источников:
 * 1) Health Connect — туда пишут Mi Fitness, Google Fit, Samsung Health, Huawei Health (через синхронизацию) и др.;
 * 2) встроенный датчик шагов телефона — работает без сторонних приложений, пока DASEIN установлен.
 * В дневник записывается большее из значений, чтобы источники не затирали друг друга.
 */
object Steps {
    private const val TAG = "Steps"
    val READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
    val HC_PERMISSIONS = setOf(READ_STEPS)

    enum class HcStatus { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

    fun hcStatus(ctx: Context): HcStatus = when (HealthConnectClient.getSdkStatus(ctx)) {
        HealthConnectClient.SDK_AVAILABLE -> HcStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcStatus.NEEDS_UPDATE
        else -> HcStatus.UNAVAILABLE
    }

    suspend fun hcGranted(ctx: Context): Boolean = runCatching {
        hcStatus(ctx) == HcStatus.AVAILABLE &&
            HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(HC_PERMISSIONS)
    }.getOrDefault(false)

    fun hasSensor(ctx: Context): Boolean =
        (ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun sensorAllowed(ctx: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Шаги по дням из Health Connect за последние [days] дней. */
    suspend fun readHealthConnect(ctx: Context, days: Int = 30): Map<Long, Int> {
        val client = HealthConnectClient.getOrCreate(ctx)
        val start = LocalDate.now().minusDays(days.toLong() - 1).atStartOfDay()
        val end = LocalDate.now().plusDays(1).atStartOfDay()
        val res = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        return res.associate { it.startTime.toLocalDate().toEpochDay() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt() }
    }

    /** Текущее значение аппаратного счётчика (шаги с момента включения телефона). */
    private suspend fun readCounter(ctx: Context): Float? {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0])
                    }

                    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                }
                sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(l) }
            }
        }
    }

    /**
     * Прибавляет к сегодняшним шагам разницу с прошлым замером датчика.
     * После перезагрузки счётчик начинается с нуля — тогда прибавляется само значение.
     */
    private suspend fun sampleSensor(ctx: Context): Int? {
        if (!hasSensor(ctx) || !sensorAllowed(ctx)) return null
        val value = readCounter(ctx)?.toLong() ?: return null
        val s = Graph.prefs.now()
        val today = Dates.today()
        val delta = when {
            s.sensorLast < 0 -> 0L
            value < s.sensorLast -> value
            else -> value - s.sensorLast
        }
        val daySteps = (if (s.sensorDay == today) s.sensorSteps else 0) + delta.toInt()
        Graph.prefs.update { it.copy(sensorLast = value, sensorDay = today, sensorSteps = daySteps) }
        return daySteps
    }

    private suspend fun store(day: Long, steps: Int, source: String) {
        if (steps <= 0) return
        val d = Graph.dao.dayLogNow(day) ?: DayLog(day)
        if (steps > d.steps) Graph.dao.upsertDayLog(d.copy(steps = steps))
        Graph.prefs.update { it.copy(stepsSyncedAt = System.currentTimeMillis(), stepsSource = source) }
    }

    /** Синхронизация всех включённых источников. Возвращает шаги за сегодня или null. */
    suspend fun sync(ctx: Context): Int? {
        val s = Graph.prefs.now()
        var today: Int? = null
        if (s.stepsHc && hcGranted(ctx)) {
            runCatching { readHealthConnect(ctx) }
                .onSuccess { map -> map.forEach { (d, v) -> store(d, v, "Health Connect") }; today = map[Dates.today()] }
                .onFailure { Log.w(TAG, "Health Connect", it) }
        }
        if (s.stepsSensor) {
            runCatching { sampleSensor(ctx) }
                .onSuccess { v -> if (v != null) { store(Dates.today(), v, "датчик телефона"); today = maxOf(today ?: 0, v) } }
                .onFailure { Log.w(TAG, "sensor", it) }
        }
        return today
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<StepsWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("steps", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork("steps")

    /** Включить или выключить фоновый замер в зависимости от настроек. */
    suspend fun ensureScheduled(ctx: Context) {
        val s = Graph.prefs.now()
        if (s.stepsSensor || s.stepsHc) schedule(ctx) else cancel(ctx)
    }
}

class StepsWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        runCatching { Steps.sync(applicationContext) }
        return Result.success()
    }
}
