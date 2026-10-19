package com.dasein.poryadok.system

import android.content.Context
import androidx.room.withTransaction
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BodyMetric
import com.dasein.poryadok.data.BodyProfile
import com.dasein.poryadok.data.Measurement
import com.dasein.poryadok.data.ProgressPhoto
import com.dasein.poryadok.data.WeightEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs

/**
 * Аккаунты весов были в версии 1.0.192 и убраны. Остаток — возврат данных владельца телефона:
 * если тогда был открыт другой аккаунт, его данные лежали в базе, а свои — в files/body_accounts/accounts.json.
 * При первом чтении Health Connect свои данные возвращаются в базу. Файл с данными других аккаунтов остаётся
 * (попадает в копию разделом «Здоровье»), чтобы их взвешивания не подтягивались владельцу второй раз.
 */
object BodyAccounts {
    @Serializable
    data class Account(val id: Long, val name: String, val createdAt: Long = 0)

    @Serializable
    data class Snapshot(
        val profile: BodyProfile? = null,
        val weights: List<WeightEntry> = emptyList(),
        val measurements: List<Measurement> = emptyList(),
        val metrics: List<BodyMetric> = emptyList(),
        val photos: List<ProgressPhoto> = emptyList(),
    )

    @Serializable
    data class Saved(val id: Long, val data: Snapshot)

    @Serializable
    data class State(
        val accounts: List<Account> = emptyList(),
        val active: Long = 0,
        val saved: List<Saved> = emptyList(),
        val switching: Long = 0,
    ) {
        val owner: Account? get() = accounts.minByOrNull { it.createdAt }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    @Volatile private var cached: State? = null

    private fun file(ctx: Context) = File(File(ctx.filesDir, "body_accounts"), "accounts.json")

    private fun now(ctx: Context): State = cached ?: runCatching {
        file(ctx).takeIf { it.exists() }?.let { json.decodeFromString(State.serializer(), it.readText()) }
    }.getOrNull().let { it ?: State() }.also { cached = it }

    private suspend fun save(ctx: Context, s: State) = withContext(Dispatchers.IO) {
        val f = file(ctx)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "accounts.tmp")
        tmp.writeText(json.encodeToString(State.serializer(), s))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        cached = s
    }

    private suspend fun snapshotDb(): Snapshot {
        val d = Graph.db.backupDao()
        return Snapshot(d.allBodyProfile().firstOrNull(), d.allWeightEntry(), d.allMeasurement(), Graph.extra.bodyMetricsNow(), d.allProgressPhoto())
    }

    private suspend fun loadDb(s: Snapshot) {
        val d = Graph.db.backupDao()
        Graph.db.withTransaction {
            d.wipeBodyProfile(); s.profile?.let { d.putBodyProfile(listOf(it.copy(id = 1))) }
            d.wipeWeightEntry(); d.putWeightEntry(s.weights)
            d.wipeMeasurement(); d.putMeasurement(s.measurements)
            d.wipeProgressPhoto(); d.putProgressPhoto(s.photos)
        }
        Graph.extraDb.withTransaction { Graph.extra.wipeBodyMetrics(); Graph.extra.putBodyMetrics(s.metrics) }
    }

    /** Вернуть в базу данные владельца телефона, если открыт был другой аккаунт (или переключение прервалось). */
    suspend fun backToOwner(ctx: Context): Unit = lock.withLock {
        cached = null
        var s = now(ctx)
        // Прерванное переключение: данные цели ещё в файле — доделываем.
        if (s.switching != 0L) {
            val target = s.saved.firstOrNull { it.id == s.switching }
            if (target != null) loadDb(target.data)
            s = s.copy(active = if (target != null) s.switching else s.active, switching = 0, saved = s.saved.filter { it.id != s.switching })
            save(ctx, s)
        }
        val owner = s.owner ?: return@withLock
        if (s.active == owner.id) return@withLock
        val mine = s.saved.firstOrNull { it.id == owner.id } ?: return@withLock
        // Сначала данные открытого аккаунта — в файл, потом свои — в базу.
        val mid = s.copy(saved = s.saved.filter { it.id != s.active } + Saved(s.active, snapshotDb()), switching = owner.id)
        save(ctx, mid)
        loadDb(mine.data)
        save(ctx, mid.copy(active = owner.id, switching = 0, saved = mid.saved.filter { it.id != owner.id }))
        runCatching { Widgets.refresh(Graph.app) }
        Unit
    }

    /** Взвешивания других (бывших) аккаунтов — чтобы Health Connect не дописал их владельцу второй раз. */
    fun othersReadings(ctx: Context): List<BodyMetric> = now(ctx).saved.flatMap { it.data.metrics }

    fun isForeign(others: List<BodyMetric>, extId: String, at: Long, kg: Double): Boolean =
        others.any { it.extId == extId || (abs(it.at - at) <= 10 * 60_000 && abs(it.weight - kg) < 0.3) }
}
