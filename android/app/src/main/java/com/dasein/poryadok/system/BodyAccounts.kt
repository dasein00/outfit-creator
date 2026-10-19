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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs

/**
 * Несколько аккаунтов в «Весах»: у каждого человека свои пол, рост, возраст, цели, взвешивания, замеры и фото прогресса.
 * Данные активного аккаунта лежат в базе как обычно — весь остальной код (графики, план, виджеты) их и показывает.
 * Данные остальных хранятся в files/body_accounts/accounts.json (попадает в копию разделом «Здоровье»).
 * Переключение — обмен: текущие данные уходят в файл, данные выбранного аккаунта — в базу.
 * Первый аккаунт — владелец телефона: только ему подтягиваются взвешивания из Health Connect.
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
        /** Данные неактивных аккаунтов; данные активного — в базе. */
        val saved: List<Saved> = emptyList(),
        /** Идёт переключение на этот аккаунт. Если приложение закрылось посередине — доделывается при следующем обращении. */
        val switching: Long = 0,
    ) {
        val owner: Account? get() = accounts.minByOrNull { it.createdAt }
        val current: Account? get() = accounts.firstOrNull { it.id == active }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow<State?>(null)
    private val lock = Mutex()

    private fun file(ctx: Context) = File(File(ctx.filesDir, "body_accounts").apply { mkdirs() }, "accounts.json")

    private fun load(ctx: Context): State = runCatching { json.decodeFromString(State.serializer(), file(ctx).readText()) }.getOrDefault(State())

    fun flow(ctx: Context): StateFlow<State?> {
        if (state.value == null) state.value = load(ctx)
        return state
    }

    fun now(ctx: Context): State = state.value ?: load(ctx).also { state.value = it }

    /** После восстановления копии — перечитать файл. */
    fun reload(ctx: Context) { state.value = load(ctx) }

    private suspend fun save(ctx: Context, s: State) = withContext(Dispatchers.IO) {
        val f = file(ctx)
        val tmp = File(f.parentFile, "accounts.tmp")
        tmp.writeText(json.encodeToString(State.serializer(), s))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        state.value = s
    }

    private suspend fun snapshotDb(): Snapshot {
        val d = Graph.db.backupDao()
        return Snapshot(
            profile = d.allBodyProfile().firstOrNull(),
            weights = d.allWeightEntry(),
            measurements = d.allMeasurement(),
            metrics = Graph.extra.bodyMetricsNow(),
            photos = d.allProgressPhoto(),
        )
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

    /** Доделать прерванное переключение: данные цели ещё лежат в файле, их можно загрузить повторно. */
    private suspend fun recoverLocked(ctx: Context) {
        val s = now(ctx)
        if (s.switching == 0L) return
        val target = s.saved.firstOrNull { it.id == s.switching }
        if (target != null) loadDb(target.data)
        save(ctx, s.copy(active = if (target != null) s.switching else s.active, switching = 0, saved = s.saved.filter { it.id != s.switching }))
    }

    suspend fun recover(ctx: Context) = lock.withLock { recoverLocked(ctx) }

    private fun newId(s: State) = maxOf(System.currentTimeMillis(), (s.accounts.maxOfOrNull { it.id } ?: 0) + 1)

    /**
     * Новый аккаунт со своими полом, ростом и возрастом. Если аккаунтов ещё не было, текущие данные становятся
     * первым аккаунтом (владельца) с именем [currentName]. После создания открывается новый аккаунт.
     */
    suspend fun create(ctx: Context, name: String, male: Boolean, heightCm: Double, age: Int, currentName: String? = null) {
        val id = lock.withLock {
            recoverLocked(ctx)
            var s = now(ctx)
            if (s.accounts.isEmpty()) {
                val owner = Account(newId(s), currentName?.trim().orEmpty().ifBlank { "Я" }, System.currentTimeMillis())
                s = s.copy(accounts = listOf(owner), active = owner.id)
            }
            val acc = Account(newId(s), name.trim().ifBlank { "Аккаунт ${s.accounts.size + 1}" }, System.currentTimeMillis())
            val base = Graph.dao.profileNow() ?: BodyProfile()
            // Цели питания и нормы — как у текущего, но тело своё.
            val profile = base.copy(male = male, heightCm = heightCm, age = age, startWeight = BodyProfile().startWeight, startDay = 0, changeKg = BodyProfile().changeKg)
            save(ctx, s.copy(accounts = s.accounts + acc, saved = s.saved + Saved(acc.id, Snapshot(profile = profile))))
            acc.id
        }
        switchTo(ctx, id)
    }

    suspend fun switchTo(ctx: Context, id: Long) {
        lock.withLock {
            recoverLocked(ctx)
            val s = now(ctx)
            if (id == s.active || s.accounts.none { it.id == id } || s.saved.none { it.id == id }) return
            // 1) текущие данные — в файл (и отметка, куда переключаемся); 2) данные выбранного — в базу; 3) готово.
            val mid = s.copy(saved = s.saved.filter { it.id != s.active } + Saved(s.active, snapshotDb()), switching = id)
            save(ctx, mid)
            recoverLocked(ctx)
        }
        runCatching { Widgets.refresh(Graph.app) }
    }

    suspend fun rename(ctx: Context, id: Long, name: String) = lock.withLock {
        val s = now(ctx)
        if (name.isBlank()) return@withLock
        save(ctx, s.copy(accounts = s.accounts.map { if (it.id == id) it.copy(name = name.trim()) else it }))
    }

    /** Удалить неактивный аккаунт вместе с его данными. Владельца и открытый аккаунт удалить нельзя. */
    suspend fun delete(ctx: Context, id: Long): Boolean = lock.withLock {
        recoverLocked(ctx)
        val s = now(ctx)
        if (id == s.active || id == s.owner?.id) return@withLock false
        val left = s.accounts.filter { it.id != id }
        // Остался один — аккаунты больше не нужны, данные и так в базе.
        save(ctx, if (left.size <= 1) State() else s.copy(accounts = left, saved = s.saved.filter { it.id != id }))
        true
    }

    /** Health Connect читается только в аккаунт владельца телефона (или когда аккаунтов нет). */
    fun syncAllowed(ctx: Context): Boolean = now(ctx).let { it.accounts.isEmpty() || (it.switching == 0L && it.active == it.owner?.id) }

    /** Взвешивания других аккаунтов — чтобы Health Connect не дописал их владельцу второй раз. */
    fun othersReadings(ctx: Context): List<BodyMetric> = now(ctx).saved.flatMap { it.data.metrics }

    fun isForeign(others: List<BodyMetric>, extId: String, at: Long, kg: Double): Boolean =
        others.any { it.extId == extId || (abs(it.at - at) <= 10 * 60_000 && abs(it.weight - kg) < 0.3) }

    /** Перенести взвешивание (например, подтянутое из Health Connect) в другой аккаунт. */
    suspend fun moveReading(ctx: Context, m: BodyMetric, toId: Long) {
        lock.withLock {
            recoverLocked(ctx)
            val s = now(ctx)
            val target = s.saved.firstOrNull { it.id == toId } ?: return
            val data = target.data
            val moved = m.copy(id = (data.metrics.maxOfOrNull { it.id } ?: 0) + 1)
            val metrics = (data.metrics + moved).sortedBy { it.at }
            val dayLast = metrics.filter { it.day == m.day }.maxBy { it.at }
            val weights = data.weights.filter { it.day != m.day } + WeightEntry(m.day, dayLast.weight)
            save(ctx, s.copy(saved = s.saved.map { if (it.id == toId) Saved(toId, data.copy(metrics = metrics, weights = weights)) else it }))
        }
        Body.delete(m)
    }
}
