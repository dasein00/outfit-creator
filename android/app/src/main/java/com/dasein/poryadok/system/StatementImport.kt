package com.dasein.poryadok.system

import android.content.Context
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Account
import com.dasein.poryadok.data.Category
import com.dasein.poryadok.data.ImportRecord
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Statement
import kotlin.math.abs

/**
 * Запись выписки в операции так, чтобы ничего не задвоилось:
 *  - у каждой операции выписки свой ключ (номер счёта + номер документа или дата, сумма и остаток после неё) —
 *    повторная загрузка той же или пересекающейся выписки обновляет уже записанное, а не добавляет;
 *  - операция, которая уже пришла SMS 900 или пушем СберБанк Онлайн, заменяется версией из выписки (та же сумма и тип, ±1 день);
 *  - переводы между своими счетами записываются переводом и не считаются ни доходом, ни расходом.
 */
object StatementImport {
    const val KIND = "stmt"

    enum class Status { NEW, UPDATE, REPLACE }

    data class Plan(val op: Statement.Op, val key: String, val status: Status, val targetId: Long?)

    data class Result(val added: Int, val updated: Int, val replaced: Int, val aligned: Double?)

    /** Ключи операций: номер документа, а если его нет — дата, сумма и остаток (одинаковые покупки в один день нумеруются). */
    fun keys(ops: List<Statement.Op>, header: Statement.Header, fallback: String): List<String> {
        // Ключ привязан к счёту в приложении: выписка целиком и по частям (без шапки) дают одинаковые ключи.
        val acc = fallback
        val seen = HashMap<String, Int>()
        return ops.map { op ->
            val base = op.ref.ifBlank { "${op.day}:${"%.2f".format(java.util.Locale.US, op.amount)}:${if (op.income) "+" else "-"}:${op.balance?.let { "%.2f".format(java.util.Locale.US, it) } ?: op.text.lowercase().hashCode()}" }
            val n = seen.merge(base, 1, Int::plus)!!
            "$KIND:$acc:$base" + if (n > 1) "#$n" else ""
        }
    }

    private fun typeOf(op: Statement.Op, transfers: Boolean) = when {
        op.transfer && transfers -> TxnType.TRANSFER
        op.income -> TxnType.INCOME
        else -> TxnType.EXPENSE
    }

    /** Что будет с каждой операцией: новая, обновит запись из прошлой выписки или заменит запись из SMS/пуша. */
    suspend fun plan(ops: List<Statement.Op>, header: Statement.Header, fallback: String, transfers: Boolean, linkedId: Long?): List<Plan> {
        val x = Graph.extra
        val stmt = x.importRecordsOf(KIND)
        val linked = stmt.associate { it.key to it.targetId }
        val taken = stmt.mapTo(HashSet()) { it.targetId }
        val all = Graph.dao.txnsNow()
        val used = HashSet<Long>()
        return ops.zip(keys(ops, header, fallback)).map { (op, key) ->
            linked[key]?.takeIf { id -> all.any { it.id == id } }?.let { return@map Plan(op, key, Status.UPDATE, it) }
            val type = typeOf(op, transfers)
            val twin = all.filter { t ->
                // Перевод мог прийти и из выписки второго счёта — его тоже узнаём, чтобы не задвоить.
                (t.id !in taken || type == TxnType.TRANSFER) && t.id !in used && abs(t.amount - op.amount) < 0.005 && abs(t.day - op.day) <= 1 &&
                    (t.type == type || (type == TxnType.TRANSFER && linkedId != null && t.accountId == linkedId) || (t.type == TxnType.TRANSFER && op.transfer))
            }.minByOrNull { abs(it.day - op.day) }
            if (twin != null) { used += twin.id; Plan(op, key, Status.REPLACE, twin.id) } else Plan(op, key, Status.NEW, null)
        }
    }

    private val NEW_CAT_ICONS = mapOf(
        "Проценты" to "ui:coins", "Переводы" to "ui:card", "Переводы от людей" to "ui:card", "Наличные" to "ui:coins",
        "Дом" to "ui:home", "Возврат" to "ui:receipt", "Покупки по QR" to "ui:receipt",
    )

    /** Категория по имени; известные категории выписки (проценты, переводы, наличные…) создаются, если их ещё нет. */
    private suspend fun categoryId(name: String?, income: Boolean, cats: MutableList<Category>): Long? {
        val list = cats.filter { it.income == income }
        name?.let { n -> list.firstOrNull { it.name.equals(n, true) } ?: list.firstOrNull { it.name.startsWith(n, true) } ?: list.firstOrNull { n.lowercase() in it.name.lowercase() } }
            ?.let { return it.id }
        if (name != null && name in NEW_CAT_ICONS) {
            val c = Category(name = name, emoji = NEW_CAT_ICONS.getValue(name), color = cats.size, income = income, sort = 50 + cats.size)
            val id = Graph.dao.upsertCategory(c)
            cats += c.copy(id = id)
            return id
        }
        return list.firstOrNull { it.name == "Другое" }?.id
    }

    /** Счёт для операций выписки: выбранный или новый с названием из выписки (сберегательный — с процентом). */
    suspend fun account(ctx: Context, chosen: Long?, header: Statement.Header): Long {
        val dao = Graph.dao
        val name = if (header.card) Sber.ACCOUNT else header.title
        val id = chosen ?: dao.accountsNow().firstOrNull { it.name.equals(name, true) }?.id
            ?: dao.upsertAccount(Account(name = name.ifBlank { "Выписка" }, emoji = if (header.savings) "ui:coins" else "ui:bank", sort = 50))
        if (header.savings) {
            val meta = ctx.getSharedPreferences("fin_accounts", Context.MODE_PRIVATE)
            meta.edit().putBoolean("sav_$id", true).apply { header.rate?.let { putFloat("rate_$id", it.toFloat()) } }.apply()
        }
        return id
    }

    private fun isSavings(ctx: Context, a: Account) =
        ctx.getSharedPreferences("fin_accounts", Context.MODE_PRIVATE).getBoolean("sav_${a.id}", false) || listOf("накопит", "вклад", "копилк").any { it in a.name.lowercase() }

    /**
     * Второй счёт для переводов между своими: для выписки сберегательного счёта — «Сбер» (карта),
     * для выписки карты — сберегательный счёт. Ничего не создаёт.
     */
    fun defaultLinked(ctx: Context, accounts: List<Account>, accountId: Long?, header: Statement.Header): Long? {
        val others = accounts.filter { it.id != accountId && !it.archived }
        return if (header.savings) others.firstOrNull { it.name == Sber.ACCOUNT }?.id ?: others.firstOrNull { !isSavings(ctx, it) }?.id
        else others.firstOrNull { isSavings(ctx, it) }?.id
    }

    /** Связанный счёт: выбранный, подходящий по умолчанию или новый («Сбер» либо «Накопительный счёт»). */
    suspend fun linkedAccount(ctx: Context, chosen: Long?, accountId: Long, header: Statement.Header): Long {
        val dao = Graph.dao
        chosen?.takeIf { it != accountId }?.let { return it }
        defaultLinked(ctx, dao.accountsNow(), accountId, header)?.let { return it }
        return if (header.savings) dao.upsertAccount(Account(name = Sber.ACCOUNT, emoji = "ui:bank", color = 1, sort = 40))
        else dao.upsertAccount(Account(name = "Накопительный счёт", emoji = "ui:coins", sort = 41)).also {
            ctx.getSharedPreferences("fin_accounts", Context.MODE_PRIVATE).edit().putBoolean("sav_$it", true).apply()
        }
    }

    suspend fun apply(ctx: Context, plans: List<Plan>, accountId: Long, linkedId: Long, transfers: Boolean, header: Statement.Header): Result {
        val dao = Graph.dao
        val x = Graph.extra
        val cats = dao.categoriesNow().toMutableList()
        val now = System.currentTimeMillis()
        var added = 0; var updated = 0; var replaced = 0
        plans.forEach { p ->
            val op = p.op
            val type = typeOf(op, transfers)
            val (from, to) = when {
                type != TxnType.TRANSFER -> accountId to null
                op.income -> linkedId to accountId
                else -> accountId to linkedId
            }
            val catId = if (type == TxnType.TRANSFER) null else categoryId(op.category, type == TxnType.INCOME, cats)
            val old = p.targetId?.let { dao.txnById(it) }
            val id = if (old != null) {
                // Категорию, которую вы поправили сами, сохраняем, если тип не поменялся.
                val keepCat = old.type == type && old.categoryId != null && cats.firstOrNull { it.id == old.categoryId }?.name != "Другое"
                dao.upsertTxn(
                    old.copy(
                        type = type, amount = op.amount, accountId = from, toAccountId = to, day = op.day,
                        categoryId = if (keepCat) old.categoryId else catId,
                        note = if (old.note.isBlank() || old.note.startsWith("Сбер") || p.status == Status.UPDATE) op.text else old.note,
                    )
                )
                if (p.status == Status.UPDATE) updated++ else replaced++
                old.id
            } else {
                added++
                dao.upsertTxn(Txn(type = type, amount = op.amount, accountId = from, toAccountId = to, categoryId = catId, day = op.day, note = op.text, createdAt = now))
            }
            x.putImportRecord(ImportRecord(p.key, KIND, id, now))
        }
        // Остаток счёта — как в выписке на её последний день.
        var aligned: Double? = null
        // Остаток на конец выписки запоминаем: выписку, разделённую на части, выравниваем после каждой части.
        val meta = ctx.getSharedPreferences("fin_accounts", Context.MODE_PRIVATE)
        if (header.closingDay != null && header.closing != null) meta.edit().putString("close_$accountId", "${header.closingDay}|${header.closing}").apply()
        val stored = meta.getString("close_$accountId", null)?.split('|')?.takeIf { it.size == 2 }
        val closingDay = header.closingDay ?: stored?.get(0)?.toLongOrNull()
        val closing = header.closing ?: stored?.get(1)?.toDoubleOrNull()
        if (closingDay != null && closing != null) {
            val acc = dao.accountsNow().firstOrNull { it.id == accountId }
            if (acc != null) {
                val txns = dao.txnsNow().filter { it.day <= closingDay }
                var b = acc.initial
                txns.forEach { t ->
                    when (t.type) {
                        TxnType.INCOME -> if (t.accountId == acc.id) b += t.amount
                        TxnType.EXPENSE -> if (t.accountId == acc.id) b -= t.amount
                        TxnType.TRANSFER -> { if (t.accountId == acc.id) b -= t.amount; if (t.toAccountId == acc.id) b += t.amount }
                    }
                }
                val delta = closing - b
                if (abs(delta) >= 0.005) { dao.upsertAccount(acc.copy(initial = acc.initial + delta)); aligned = closing } else aligned = closing
            }
        }
        return Result(added, updated, replaced, aligned)
    }

    /** Последний день, за который есть операции из выписок: с него догружаются SMS 900. */
    suspend fun lastDay(): Long? {
        val ids = Graph.extra.importRecordsOf(KIND).mapTo(HashSet()) { it.targetId }
        return Graph.dao.txnsNow().filter { it.id in ids }.maxOfOrNull { it.day }
    }
}
