package com.dasein.poryadok.system

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
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
        HealthPermission.getReadPermission(BodyWaterMassRecord::class),
    )
    val READ_WEIGHT: String = HealthPermission.getReadPermission(WeightRecord::class)

    /** Чтение в фоне (Health Connect на Android 14+ с обновлениями). Без него данные подтягиваются при открытии приложения. */
    const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    /** Понятные имена приложений-источников. */
    fun appName(pkg: String): String = when {
        pkg.isBlank() -> "неизвестно"
        pkg == "com.google.android.apps.fitness" -> "Google Fit"
        pkg == "com.google.android.apps.healthdata" || pkg == "com.android.healthconnect.controller" -> "Health Connect"
        pkg.contains("okok", true) || pkg.contains("chipsea", true) -> "OKOK"
        pkg.contains("samsung", true) -> "Samsung Health"
        pkg.contains("xiaomi", true) || pkg.contains("mi.health", true) -> "Mi Fitness"
        pkg.contains("huawei", true) -> "Huawei Health"
        pkg.contains("zepp", true) || pkg.contains("amazfit", true) -> "Zepp"
        pkg == "com.dasein.poryadok" -> "DASEIN"
        else -> pkg
    }

    /** Что видно в Health Connect — чтобы понять, на каком шаге рвётся цепочка «весы → OKOK → Google Fit → Health Connect → DASEIN». */
    data class Diag(
        val status: Steps.HcStatus,
        val granted: Int,
        val total: Int,
        val weightAllowed: Boolean,
        val background: Boolean,
        val lastKg: Double?,
        val lastAt: Long?,
        val lastApp: String?,
        val count30: Int,
        val error: String? = null,
    )

    suspend fun diagnose(ctx: Context): Diag {
        val st = Steps.hcStatus(ctx)
        if (st != Steps.HcStatus.AVAILABLE) return Diag(st, 0, PERMISSIONS.size, false, false, null, null, null, 0)
        return runCatching {
            val client = HealthConnectClient.getOrCreate(ctx)
            val all = client.permissionController.getGrantedPermissions()
            val ok = all.intersect(PERMISSIONS)
            val base = Diag(st, ok.size, PERMISSIONS.size, READ_WEIGHT in ok, BACKGROUND in all, null, null, null, 0)
            if (READ_WEIGHT !in ok) base
            else {
                val recs = client.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.after(Instant.now().minus(30, ChronoUnit.DAYS)))).records
                val last = recs.maxByOrNull { it.time }
                base.copy(
                    lastKg = last?.weight?.inKilograms, lastAt = last?.time?.toEpochMilli(),
                    lastApp = last?.metadata?.dataOrigin?.packageName?.let { appName(it) }, count30 = recs.size,
                )
            }
        }.getOrElse { Diag(st, 0, PERMISSIONS.size, false, false, null, null, null, 0, it.message ?: it.javaClass.simpleName) }
    }

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
        val waters = read(BodyWaterMassRecord::class)
        var added = 0
        weights.forEach { w ->
            val ext = "hc:" + w.metadata.id
            val at = w.time.toEpochMilli()
            val kg = w.weight.inKilograms
            val fat = near(fats, w.time) { it.time }?.percentage?.value
            val bone = near(bones, w.time) { it.time }?.mass?.inKilograms
            val lean = near(leans, w.time) { it.time }?.mass?.inKilograms
            val bmr = near(bmrs, w.time) { it.time }?.basalMetabolicRate?.inKilocaloriesPerDay
            val water = near(waters, w.time) { it.time }?.mass?.inKilograms?.let { if (kg > 0) it / kg * 100 else null }
            val existing = Graph.extra.bodyByExt(ext)
            if (existing != null) {
                // Google Fit иногда передаёт жир и другие показатели позже веса — дописываем их в уже сохранённое взвешивание.
                val filled = existing.copy(
                    fatPct = existing.fatPct ?: fat, boneKg = existing.boneKg ?: bone, leanKg = existing.leanKg ?: lean,
                    bmr = existing.bmr ?: bmr, waterPct = existing.waterPct ?: water,
                )
                if (filled != existing) Graph.extra.upsertBodyMetric(filled)
                return@forEach
            }
            save(
                BodyMetric(
                    at = at, day = Dates.dayOf(at), weight = kg,
                    fatPct = fat, boneKg = bone, leanKg = lean, bmr = bmr, waterPct = water,
                    muscleKg = lean?.let { l -> bone?.let { l - it } },
                    source = "Health Connect · " + appName(w.metadata.dataOrigin.packageName),
                    extId = ext,
                )
            )
            added++
        }
        if (added > 0) Graph.prefs.update { it.copy(bodyHc = true) }
        return added
    }
}
