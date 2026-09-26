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
            ?: dao.upsertAccount(Account(name = ACCOUNT, emoji = "🏦", color = 1, sort = 40))
    }

    /** Сохраняет операцию, если такой ещё не было. Возвращает true, если добавлена. */
    suspend fun record(text: String, at: Long, sourceKey: String): Boolean = lock.withLock {
        val op = BankSms.parse(text) ?: return false
        val key = "$KIND:$sourceKey"
        val x = Graph.extra
        if (x.importRecord(key) != null) return false
        val dao = Graph.dao
        val income = op.kind == BankSms.Kind.INCOME
        val cats = dao.categoriesNow().filter { it.income == income }
        val cat = cats.firstOrNull { it.name == op.category } ?: cats.firstOrNull { it.name == "Другое" }
        val note = listOf(op.merchant, op.card).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Сбер" }
        val id = dao.upsertTxn(
            Txn(
                type = if (income) TxnType.INCOME else TxnType.EXPENSE, amount = op.amount, accountId = accountId(),
                categoryId = cat?.id, day = Dates.dayOf(at), note = "Сбер: $note", createdAt = at,
            )
        )
        x.putImportRecord(ImportRecord(key, KIND, id, System.currentTimeMillis()))
        Graph.prefs.update { it.copy(sberImported = it.sberImported + 1, sberLastAt = at) }
        true
    }

    /** Ключ уведомления: текст и минута. Повторные показы того же уведомления не создают дублей. */
    fun notificationKey(text: String, at: Long) = "n:${at / 60_000}:${text.hashCode()}"

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
            if (record(body, date, "sms:$id")) added++
        }
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
                Sber.record("$title $text".trim(), sbn.postTime, Sber.notificationKey(text, sbn.postTime))
            }.onFailure { Log.w("Sber", "notification", it) }
        }
    }
}
