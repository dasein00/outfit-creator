package com.dasein.poryadok.system

import android.Manifest
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Account
import com.dasein.poryadok.data.ImportRecord
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.BankSms
import com.dasein.poryadok.logic.Dates
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Операции Сбербанка без официального API: из уведомлений приложения СберБанк Онлайн и SMS с номера 900.
 * Каждое сообщение превращается в операцию на счёте «Сбер» один раз.
 */
object Sber {
    const val ACCOUNT = "Сбер"
    private const val KIND = "sber"
    private val lock = Mutex()

    fun listenerEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(ctx, SberNotificationListener::class.java).flattenToString()
        return flat.split(':').any { it == me }
    }

    fun smsAllowed(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private suspend fun accountId(): Long {
        val dao = Graph.dao
        return dao.accountsNow().firstOrNull { it.name == ACCOUNT }?.id
            ?: dao.upsertAccount(Account(name = ACCOUNT, emoji = "ui:bank", color = 1, sort = 40))
    }

    /** Откуда пришло сообщение. Одна операция приходит и SMS, и пушем приложения — это разные каналы. */
    enum class Channel(val prefix: String) { SMS("sms"), SMS_NOTIFICATION("sn"), PUSH("p") }

    /** SMS из базы и уведомление SMS-приложения — одно и то же сообщение, поэтому для поиска дублей это один канал. */
    private fun channelOf(recordKey: String): String? {
        val k = recordKey.removePrefix("$KIND:")
        return when {
            k.startsWith("sms:") || k.startsWith("sn:") -> "sms"
            k.startsWith("p:") || k.startsWith("n:") -> "push"
            else -> null
        }
    }

    private fun group(c: Channel) = if (c == Channel.PUSH) "push" else "sms"

    /** Окно, в котором SMS и пуш об одной операции считаются одной операцией. */
    private const val SAME_OP_MS = 10 * 60_000L

    private fun textKey(text: String) = "$KIND:t:" + text.lowercase().replace(Regex("\\s+"), " ").trim().hashCode()

    /**
     * Сохраняет операцию, если такой ещё не было. Возвращает true, если добавлена.
     * Дубли отсекаются трижды: по ключу источника, по тексту сообщения (SMS и её уведомление)
     * и по сумме и времени, если та же операция уже пришла по другому каналу (SMS и пуш СберБанк Онлайн).
     */
    suspend fun record(text: String, at: Long, sourceKey: String, channel: Channel = Channel.SMS, sameText: String = text): Boolean = lock.withLock {
        val op = BankSms.parse(text) ?: return false
        val key = "$KIND:${channel.prefix}:$sourceKey"
        val x = Graph.extra
        if (x.importRecord(key) != null) return false
        val dao = Graph.dao
        val tKey = textKey(sameText)
        x.importRecord(tKey)?.let { r ->
            if (abs(r.at - at) <= SAME_OP_MS) {
                x.putImportRecord(ImportRecord(key, KIND, r.targetId, at))
                return false
            }
        }
        val income = op.kind == BankSms.Kind.INCOME
        val type = if (income) TxnType.INCOME else TxnType.EXPENSE
        val acc = accountId()
        val records = x.importRecordsOf(KIND)
        val channels = records.groupBy({ it.targetId }, { channelOf(it.key) })
        val twin = dao.txnsNow().filter { t ->
            t.accountId == acc && t.type == type && abs(t.amount - op.amount) < 0.005 && abs(t.createdAt - at) <= SAME_OP_MS &&
                channels[t.id].orEmpty().filterNotNull().let { it.isNotEmpty() && group(channel) !in it }
        }.minByOrNull { abs(it.createdAt - at) }
        if (twin != null) {
            // Та же операция по другому каналу: запоминаем источник и, если у новой подписи есть магазин, берём её.
            if (op.merchant.isNotBlank() && label(twin.note).isEmpty()) dao.upsertTxn(twin.copy(note = noteOf(op)))
            x.putImportRecord(ImportRecord(key, KIND, twin.id, at))
            x.putImportRecord(ImportRecord(tKey, KIND, twin.id, at))
            return false
        }
        val cats = dao.categoriesNow().filter { it.income == income }
        val cat = cats.firstOrNull { it.name == op.category } ?: cats.firstOrNull { it.name == "Другое" }
        val id = dao.upsertTxn(
            Txn(type = type, amount = op.amount, accountId = acc, categoryId = cat?.id, day = Dates.dayOf(at), note = noteOf(op), createdAt = at)
        )
        x.putImportRecord(ImportRecord(key, KIND, id, at))
        x.putImportRecord(ImportRecord(tKey, KIND, id, at))
        Graph.prefs.update { it.copy(sberImported = it.sberImported + 1, sberLastAt = maxOf(it.sberLastAt, at)) }
        true
    }

    private fun noteOf(op: BankSms.Op) = "Сбер: " + listOf(op.merchant, op.card).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Сбер" }

    /**
     * Чинит уже записанные операции: входящие переводы, сохранённые как расход, и дубли,
     * когда одна покупка пришла и SMS, и пушем. Возвращает (исправлено типов, убрано дублей).
     */
    suspend fun repair(): Pair<Int, Int> = lock.withLock {
        val dao = Graph.dao
        val x = Graph.extra
        val acc = dao.accountsNow().firstOrNull { it.name == ACCOUNT }?.id ?: return 0 to 0
        val cats = dao.categoriesNow()
        val incomeCat = cats.filter { it.income }.let { l -> l.firstOrNull { it.name == "Другое" } ?: l.firstOrNull() }
        var flipped = 0
        dao.txnsNow().filter { it.accountId == acc && it.type == TxnType.EXPENSE && BankSms.noteLooksIncoming(it.note) }.forEach { t ->
            dao.upsertTxn(t.copy(type = TxnType.INCOME, categoryId = incomeCat?.id))
            flipped++
        }
        val records = x.importRecordsOf(KIND)
        val byTxn = records.groupBy { it.targetId }
        fun channels(id: Long) = byTxn[id].orEmpty().mapNotNull { channelOf(it.key) }.toSet()
        val removed = mutableSetOf<Long>()
        dao.txnsNow().filter { it.accountId == acc }.groupBy { it.type to Math.round(it.amount * 100) }.values.forEach { same ->
            val sorted = same.sortedBy { it.createdAt }
            for (i in sorted.indices) {
                val a = sorted[i]
                if (a.id in removed) continue
                for (j in i + 1 until sorted.size) {
                    val b = sorted[j]
                    if (b.createdAt - a.createdAt > SAME_OP_MS) break
                    if (b.id in removed) continue
                    val ca = channels(a.id); val cb = channels(b.id)
                    // Разные каналы — точно одна операция. Один канал, за 2 минуты и у одной из записей нет магазина —
                    // это SMS и пуш, которые старая версия записала как два уведомления.
                    val dup = (ca.isNotEmpty() && cb.isNotEmpty() && ca.intersect(cb).isEmpty()) ||
                        (b.createdAt - a.createdAt <= 2 * 60_000L && a.note != b.note && (label(a.note).isEmpty() || label(b.note).isEmpty()))
                    if (!dup) continue
                    val (keep, drop) = if (label(b.note).length > label(a.note).length) b to a else a to b
                    byTxn[drop.id].orEmpty().forEach { x.putImportRecord(it.copy(targetId = keep.id)) }
                    dao.deleteTxn(drop)
                    removed += drop.id
                    if (drop.id == a.id) break
                }
            }
        }
        if (removed.isNotEmpty()) Graph.prefs.update { it.copy(sberImported = (it.sberImported - removed.size).coerceAtLeast(0)) }
        flipped to removed.size
    }

    private fun label(note: String) = note.removePrefix("Сбер: ").let { if (it == "Сбер" || !it.any(Char::isLetter)) "" else it }

    /** Разовый импорт уже полученных SMS от 900 (нужно разрешение на чтение SMS). */
    suspend fun importSms(ctx: Context, days: Int = 90): Pair<Int, Int> {
        if (!smsAllowed(ctx)) return 0 to 0
        val since = System.currentTimeMillis() - days * 86_400_000L
        var seen = 0
        var added = 0
        val rows = mutableListOf<Triple<String, String, Long>>()
        ctx.contentResolver.query(
            Uri.parse("content://sms/inbox"), arrayOf("_id", "address", "body", "date"),
            "date > ?", arrayOf(since.toString()), "date ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val address = c.getString(1).orEmpty()
                if (address != "900" && !address.contains("sber", true) && !address.contains("сбер", true)) continue
                rows += Triple(c.getString(0), c.getString(2).orEmpty(), c.getLong(3))
            }
        }
        rows.forEach { (id, body, date) ->
            seen++
            if (record(body, date, id, Channel.SMS)) added++
        }
        repair()
        return seen to added
    }
}

class SberNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        if (!BankSms.isSberSource(sbn.packageName, title)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (text.isBlank()) return
        Graph.init(applicationContext)
        Graph.scope.launch {
            runCatching {
                if (!Graph.prefs.now().sberOn) return@launch
                // SMS от 900 в уведомлении SMS-приложения и та же SMS из базы — одно сообщение: сверяем их по тексту.
                val fromSms = "sber" !in sbn.packageName.lowercase()
                Sber.record(
                    "$title $text".trim(), sbn.postTime, Sber.notificationKey(text, sbn.postTime),
                    if (fromSms) Sber.Channel.SMS_NOTIFICATION else Sber.Channel.PUSH, sameText = text,
                )
            }.onFailure { Log.w("Sber", "notification", it) }
        }
    }
}
