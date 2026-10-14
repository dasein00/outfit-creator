package com.dasein.poryadok.system

import android.content.Context
import android.util.Log
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Account
import com.dasein.poryadok.data.ImportRecord
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Два счёта, как в жизни: дебетовая карта Сбербанка («Сбер» — все доходы и расходы, SMS 900 и пуши)
 * и накопительный счёт Сбербанка. Все остальные счета («Карта», «Наличные», «Тетрадь», «Выписка»…) сливаются в карту:
 * их операции переносятся, переводы между ними и картой исчезают, дубли с выписками убираются, остатки пересчитываются.
 */
object AccountsCleanup {
    const val SAVINGS = "Накопительный счёт"
    private const val FLAG = "accounts_two_v1"
    private val lock = Mutex()

    data class Report(val merged: Int, val moved: Int, val removedTransfers: Int, val removedDuplicates: Int, val card: Double, val savings: Double)

    private fun meta(ctx: Context) = ctx.getSharedPreferences("fin_accounts", Context.MODE_PRIVATE)

    private fun looksSavings(ctx: Context, a: Account) =
        meta(ctx).getBoolean("sav_${a.id}", false) || listOf("накопит", "вклад", "копилк", "сберегат").any { it in a.name.lowercase() }

    /** Один раз после обновления — сам (вы попросили оставить только карту и накопительный счёт). */
    suspend fun runOnce(ctx: Context) {
        val ui = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
        if (ui.getBoolean(FLAG, false)) return
        runCatching { consolidate(ctx) }.onFailure { Log.e("DASEIN", "accounts cleanup", it) }
        ui.edit().putBoolean(FLAG, true).apply()
    }

    suspend fun consolidate(ctx: Context): Report = lock.withLock {
        val dao = Graph.dao
        val x = Graph.extra
        val accounts = dao.accountsNow()
        val txns = dao.txnsNow()
        fun count(a: Account) = txns.count { it.accountId == a.id || it.toAccountId == a.id }

        // Накопительный: самый «живой» из похожих на вклад; карта — «Сбер» или самый «живой» из остальных.
        val savingsLike = accounts.filter { looksSavings(ctx, it) }
        val savings = savingsLike.maxByOrNull { count(it) * 10 + if (it.name == SAVINGS) 5 else 0 }
            ?: Account(name = SAVINGS, emoji = "ui:coins", sort = 1).let { it.copy(id = dao.upsertAccount(it)) }
        val cardCandidates = accounts.filter { it.id != savings.id && it !in savingsLike }
        val card = cardCandidates.firstOrNull { it.name == Sber.ACCOUNT }
            ?: cardCandidates.maxByOrNull { count(it) }?.let { it.copy(name = Sber.ACCOUNT) }?.also { dao.upsertAccount(it) }
            ?: Account(name = Sber.ACCOUNT, emoji = "ui:bank", color = 1, sort = 0).let { it.copy(id = dao.upsertAccount(it)) }
        meta(ctx).edit().putBoolean("sav_${savings.id}", true).putBoolean("sav_${card.id}", false).apply()
        if (savings.name != SAVINGS) dao.upsertAccount(savings.copy(name = SAVINGS))

        val target = accounts.associate { a -> a.id to if (a.id == savings.id || (a in savingsLike)) savings.id else card.id }
        val others = accounts.filter { it.id != card.id && it.id != savings.id }

        // Перенос операций. Перевод между слитыми счетами становится переводом «карта → карта» — его не было, удаляем.
        var moved = 0
        var removedTransfers = 0
        txns.forEach { t ->
            val from = target[t.accountId] ?: card.id
            val to = t.toAccountId?.let { target[it] ?: card.id }
            if (from == t.accountId && to == t.toAccountId) return@forEach
            if (t.type == TxnType.TRANSFER && from == to) { dao.deleteTxn(t); removedTransfers++; return@forEach }
            dao.upsertTxn(t.copy(accountId = from, toAccountId = to)); moved++
        }
        dao.recurring().first().forEach { r -> target[r.accountId]?.let { to -> if (to != r.accountId) dao.upsertRecurring(r.copy(accountId = to)) } }

        // Остатки: начальные остатки слитых счетов переходят на их новый счёт; остаток на конец выписки — тоже.
        var cardInitial = (accounts.firstOrNull { it.id == card.id }?.initial ?: 0.0)
        var savInitial = (accounts.firstOrNull { it.id == savings.id }?.initial ?: 0.0)
        val m = meta(ctx)
        fun closing(id: Long) = m.getString("close_$id", null)?.split('|')?.takeIf { it.size == 2 }?.let { (d, v) -> d.toLongOrNull()?.let { dd -> v.toDoubleOrNull()?.let { dd to it } } }
        var cardClose = closing(card.id)
        var savClose = closing(savings.id)
        others.forEach { a ->
            if (target[a.id] == savings.id) savInitial += a.initial else cardInitial += a.initial
            closing(a.id)?.let { c ->
                if (target[a.id] == savings.id) { if (savClose == null || c.first > savClose!!.first) savClose = c }
                else if (cardClose == null || c.first > cardClose!!.first) cardClose = c
            }
        }
        cardClose?.let { m.edit().putString("close_${card.id}", "${it.first}|${it.second}").apply() }
        savClose?.let { m.edit().putString("close_${savings.id}", "${it.first}|${it.second}").apply() }

        // Дубли: операция из выписки точнее — ручные записи, SMS и пуши с той же суммой (±1 день) на том же счёте убираем.
        val stmtIds = x.importRecordsOf(StatementImport.KIND).mapTo(HashSet()) { it.targetId }
        val sberRecords = x.importRecordsOf("sber").groupBy { it.targetId }
        var removedDup = 0
        val now = dao.txnsNow()
        val gone = HashSet<Long>()
        now.filter { it.id in stmtIds }.forEach { s ->
            now.filter { t ->
                t.id !in stmtIds && t.id !in gone && t.accountId == s.accountId && t.type == s.type && t.toAccountId == s.toAccountId &&
                    abs(t.amount - s.amount) < 0.005 && abs(t.day - s.day) <= 1
            }.minByOrNull { abs(it.day - s.day) }?.let { d ->
                sberRecords[d.id].orEmpty().forEach { r -> x.putImportRecord(ImportRecord(r.key, r.kind, s.id, r.at)) }
                dao.deleteTxn(d); gone += d.id; removedDup++
            }
        }

        // Остальные счета исчезают (их операции уже на карте или накопительном).
        others.forEach { dao.deleteAccount(it) }

        val after = dao.txnsNow()
        fun balanceUpTo(id: Long, initial: Double, day: Long) = after.filter { it.day <= day }.fold(initial) { b, t ->
            when (t.type) {
                TxnType.INCOME -> if (t.accountId == id) b + t.amount else b
                TxnType.EXPENSE -> if (t.accountId == id) b - t.amount else b
                else -> b - (if (t.accountId == id) t.amount else 0.0) + (if (t.toAccountId == id) t.amount else 0.0)
            }
        }
        cardClose?.let { (d, v) -> cardInitial += v - balanceUpTo(card.id, cardInitial, d) }
        savClose?.let { (d, v) -> savInitial += v - balanceUpTo(savings.id, savInitial, d) }
        dao.upsertAccount((dao.accountsNow().first { it.id == card.id }).copy(name = Sber.ACCOUNT, initial = cardInitial, archived = false, sort = 0))
        dao.upsertAccount((dao.accountsNow().first { it.id == savings.id }).copy(name = SAVINGS, initial = savInitial, archived = false, sort = 1))

        fun balance(id: Long, initial: Double) = balanceUpTo(id, initial, Long.MAX_VALUE)
        Report(others.size, moved, removedTransfers, removedDup, balance(card.id, cardInitial), balance(savings.id, savInitial))
    }
}
