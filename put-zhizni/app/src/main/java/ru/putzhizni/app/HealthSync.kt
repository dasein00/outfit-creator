package ru.putzhizni.app

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Чтение данных из Health Connect: шаги, вес (умные весы), сон и тренировки (часы, браслеты).
 * Только чтение, ничего не записывается.
 */
object HealthSync {
    val PERMS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    )

    private val contract = PermissionController.createRequestPermissionResultContract()

    fun available(ctx: Context): Boolean = try {
        HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE
    } catch (e: Throwable) { false }

    fun permissionIntent(ctx: Context): Intent = contract.createIntent(ctx, PERMS)

    suspend fun hasPermissions(ctx: Context): Boolean = try {
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
            .contains(HealthPermission.getReadPermission(StepsRecord::class))
    } catch (e: Throwable) { false }

    private fun type(t: Int): String = when (t) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "run"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "walk"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "gym"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "cardio"
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING, ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "stretch"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "dance"
        else -> "other"
    }

    /** Шаги только за сегодня — для фонового обновления виджета. */
    suspend fun stepsToday(ctx: Context): Long? = try {
        val zone = ZoneId.systemDefault()
        val d = LocalDate.now(zone)
        val res = HealthConnectClient.getOrCreate(ctx).aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(d.atStartOfDay(zone).toInstant(), java.time.Instant.now()))
        )
        res[StepsRecord.COUNT_TOTAL]
    } catch (e: Throwable) { null }

    suspend fun read(ctx: Context, days: Int): JSONObject {
        val client = HealthConnectClient.getOrCreate(ctx)
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        val today = LocalDate.now(zone)
        val from = today.minusDays(days.toLong() - 1)
        val range = TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), java.time.Instant.now())
        val out = JSONObject()

        val steps = JSONObject()
        var d = from
        while (!d.isAfter(today)) {
            try {
                val r = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL),
                    TimeRangeFilter.between(d.atStartOfDay(zone).toInstant(), d.plusDays(1).atStartOfDay(zone).toInstant())))
                val v = r[StepsRecord.COUNT_TOTAL]
                if (v != null && v > 0) steps.put(d.format(fmt), v)
            } catch (_: Throwable) {}
            d = d.plusDays(1)
        }
        out.put("steps", steps)

        val weight = JSONObject()
        try {
            client.readRecords(ReadRecordsRequest(WeightRecord::class, range)).records.forEach {
                weight.put(it.time.atZone(zone).toLocalDate().format(fmt), it.weight.inKilograms)
            }
        } catch (_: Throwable) {}
        out.put("weight", weight)

        val sleep = JSONObject()
        try {
            // Сон относим ко дню пробуждения, берём самую длинную сессию.
            val best = HashMap<String, SleepSessionRecord>()
            client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records.forEach {
                val key = it.endTime.atZone(zone).toLocalDate().format(fmt)
                val cur = best[key]
                if (cur == null || java.time.Duration.between(it.startTime, it.endTime) > java.time.Duration.between(cur.startTime, cur.endTime)) best[key] = it
            }
            best.forEach { (k, v) ->
                sleep.put(k, JSONObject().put("bed", v.startTime.atZone(zone).format(hm)).put("wake", v.endTime.atZone(zone).format(hm)))
            }
        } catch (_: Throwable) {}
        out.put("sleep", sleep)

        val workouts = JSONArray()
        try {
            client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records.forEach { s ->
                val o = JSONObject()
                o.put("id", s.metadata.id)
                o.put("date", s.startTime.atZone(zone).toLocalDate().format(fmt))
                o.put("dur", java.time.Duration.between(s.startTime, s.endTime).toMinutes())
                o.put("type", type(s.exerciseType))
                o.put("title", s.title ?: "")
                try {
                    val kc = client.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), TimeRangeFilter.between(s.startTime, s.endTime)))
                    kc[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { o.put("kcal", Math.round(it.inKilocalories)) }
                } catch (_: Throwable) {}
                workouts.put(o)
            }
        } catch (_: Throwable) {}
        out.put("workouts", workouts)
        return out
    }
}
