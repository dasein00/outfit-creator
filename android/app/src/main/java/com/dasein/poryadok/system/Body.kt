package com.dasein.poryadok.system

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BodyMetric
import com.dasein.poryadok.data.WeightEntry
import com.dasein.poryadok.logic.Dates
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Состав тела из Health Connect. Весы OKOK передают данные в приложение OKOK International,
 * а оттуда — в Google Fit / Health Connect (если синхронизация включена в OKOK).
 * Health Connect хранит вес, процент жира, костную и безжировую массу, базовый обмен;
 * остальные показатели OKOK (вода, белок, висцеральный жир и др.) вводятся вручную.
 */
object Body {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(BoneMassRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
    )

    suspend fun granted(ctx: Context): Set<String> = runCatching {
        if (Steps.hcStatus(ctx) != Steps.HcStatus.AVAILABLE) emptySet()
        else HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().intersect(PERMISSIONS)
    }.getOrDefault(emptySet())

    /** Сохраняет взвешивание и обновляет вес дня для графиков и плана. */
    suspend fun save(m: BodyMetric): Long {
        val id = Graph.extra.upsertBodyMetric(m)
        syncWeightOfDay(m.day)
        return id
    }

    suspend fun delete(m: BodyMetric) {
        Graph.extra.deleteBodyMetric(m)
        val rest = Graph.extra.bodyMetricsNow().filter { it.day == m.day }
        if (rest.isEmpty()) Graph.dao.deleteWeightOfDay(m.day) else syncWeightOfDay(m.day)
    }

    private suspend fun syncWeightOfDay(day: Long) {
        val last = Graph.extra.bodyMetricsNow().filter { it.day == day }.maxByOrNull { it.at } ?: return
        Graph.dao.upsertWeight(WeightEntry(day, last.weight))
    }

    /** Читает показатели за [days] дней. Возвращает число новых взвешиваний. */
    suspend fun syncHealthConnect(ctx: Context, days: Long = 365): Int {
        val ok = granted(ctx)
        if (HealthPermission.getReadPermission(WeightRecord::class) !in ok) return 0
        val client = HealthConnectClient.getOrCreate(ctx)
        val range = TimeRangeFilter.after(Instant.now().minus(days, ChronoUnit.DAYS))
        val weights = client.readRecords(ReadRecordsRequest(WeightRecord::class, range)).records
        suspend fun <T : androidx.health.connect.client.records.Record> read(cls: kotlin.reflect.KClass<T>): List<T> =
            if (HealthPermission.getReadPermission(cls) in ok) runCatching { client.readRecords(ReadRecordsRequest(cls, range)).records }.getOrDefault(emptyList())
            else emptyList()
        val fats = read(BodyFatRecord::class)
        val bones = read(BoneMassRecord::class)
        val leans = read(LeanBodyMassRecord::class)
        val bmrs = read(BasalMetabolicRateRecord::class)
        fun <T> near(list: List<T>, at: Instant, time: (T) -> Instant): T? =
            list.filter { abs(time(it).epochSecond - at.epochSecond) <= 300 }.minByOrNull { abs(time(it).epochSecond - at.epochSecond) }
        var added = 0
        weights.forEach { w ->
            val ext = "hc:" + w.metadata.id
            if (Graph.extra.bodyExtCount(ext) > 0) return@forEach
            val at = w.time.toEpochMilli()
            save(
                BodyMetric(
                    at = at, day = Dates.dayOf(at), weight = w.weight.inKilograms,
                    fatPct = near(fats, w.time) { it.time }?.percentage?.value,
                    boneKg = near(bones, w.time) { it.time }?.mass?.inKilograms,
                    leanKg = near(leans, w.time) { it.time }?.mass?.inKilograms,
                    bmr = near(bmrs, w.time) { it.time }?.basalMetabolicRate?.inKilocaloriesPerDay,
                    source = "Health Connect" + (w.metadata.dataOrigin.packageName.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    extId = ext,
                )
            )
            added++
        }
        return added
    }
}
