package ru.putzhizni.app

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Читает уведомления СберБанк Онлайн и SMS от 900 (через уведомления любого SMS-приложения)
 * и складывает банковские операции в очередь files/bank/queue.jsonl.
 * Интерфейс забирает очередь при открытии приложения и записывает операции в финансы.
 * Ничего не отправляется наружу.
 */
class BankListener : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = true
        try { activeNotifications?.forEach { handle(this, it) } } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() { connected = false }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try { handle(this, sbn) } catch (_: Exception) {}
    }

    companion object {
        @Volatile var connected = false
        private val OPS = Regex("(покупк|оплат|списан|зачислен|перевод|перев[её]л|отправил|выдач|сняти|поступ|возврат|плат[её]ж|зарплат|аванс|пополн|кешб|кэшб)", RegexOption.IGNORE_CASE)
        private val SKIP = Regex("(код|пароль|никому не сообщайте|вход в сбербанк|отказ|недостаточно)", RegexOption.IGNORE_CASE)

        fun isBankPackage(pkg: String) = pkg.contains("sber", true)
        private fun isSmsPackage(pkg: String) = Regex("messag|mms|sms|telephony", RegexOption.IGNORE_CASE).containsMatchIn(pkg)
        // «900», «+7900», «СберБанк 900», «SBERBANK», «Сбер» — как бы номер ни был записан в контактах
        private fun isSmsFrom900(title: String) = Regex("(^|\\D)900(\\D|$)|sber|сбер", RegexOption.IGNORE_CASE).containsMatchIn(title)

        fun enabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(ctx, BankListener::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }

        private fun dir(ctx: Context) = File(ctx.filesDir, "bank").apply { mkdirs() }
        private fun queue(ctx: Context) = File(dir(ctx), "queue.jsonl")
        private fun seen(ctx: Context) = File(dir(ctx), "seen.txt")
        private fun dbg(ctx: Context) = File(dir(ctx), "debug.jsonl")

        /** Журнал для диагностики: последние уведомления от банка и SMS-приложений и что с ними сделано. */
        @Synchronized
        fun log(ctx: Context, pkg: String, title: String, text: String, verdict: String) {
            try {
                val f = dbg(ctx)
                val old = if (f.exists()) f.readLines().takeLast(29) else emptyList()
                val o = JSONObject().put("ts", System.currentTimeMillis()).put("pkg", pkg).put("title", title.take(60)).put("text", text.take(160)).put("v", verdict)
                f.writeText((old + o.toString()).joinToString("\n") + "\n")
            } catch (_: Exception) {}
        }
        fun debug(ctx: Context): String {
            val arr = JSONArray(); val f = dbg(ctx)
            if (f.exists()) f.readLines().filter { it.isNotBlank() }.forEach { try { arr.put(JSONObject(it)) } catch (_: Exception) {} }
            return arr.toString()
        }

        @Synchronized
        fun append(ctx: Context, id: String, ts: Long, src: String, title: String, text: String) {
            val s = seen(ctx)
            val known = if (s.exists()) s.readLines() else emptyList()
            if (id in known) return
            s.writeText((known.takeLast(400) + id).joinToString("\n"))
            val o = JSONObject().put("id", id).put("ts", ts).put("src", src).put("title", title).put("text", text)
            queue(ctx).appendText(o.toString() + "\n")
        }

        @Synchronized
        fun read(ctx: Context): String {
            val f = queue(ctx)
            val arr = JSONArray()
            if (f.exists()) f.readLines().filter { it.isNotBlank() }.forEach { try { arr.put(JSONObject(it)) } catch (_: Exception) {} }
            return arr.toString()
        }

        /** Удаляет из очереди записи, которые интерфейс уже обработал. */
        @Synchronized
        fun ack(ctx: Context, idsJson: String) {
            val ids = try { val a = JSONArray(idsJson); (0 until a.length()).map { a.getString(it) }.toSet() } catch (e: Exception) { return }
            val f = queue(ctx)
            if (!f.exists()) return
            val rest = f.readLines().filter { it.isNotBlank() && try { JSONObject(it).getString("id") !in ids } catch (_: Exception) { false } }
            f.writeText(if (rest.isEmpty()) "" else rest.joinToString("\n") + "\n")
        }

        fun handle(ctx: Context, sbn: StatusBarNotification) {
            val pkg = sbn.packageName ?: return
            if (pkg == ctx.packageName) return
            val bank = isBankPackage(pkg)
            if (!bank && !isSmsPackage(pkg)) return
            val ex = sbn.notification.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val conv = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: ""
            // Сообщения в стиле переписки (Google Сообщения, Samsung): текст и отправитель лежат в EXTRA_MESSAGES
            val msgs = mutableListOf<Pair<String, String>>()
            try {
                ex.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { p ->
                    val b = p as? android.os.Bundle ?: return@forEach
                    val t = b.getCharSequence("text")?.toString() ?: return@forEach
                    val snd = b.getCharSequence("sender")?.toString() ?: ""
                    msgs.add(snd to t)
                }
            } catch (_: Exception) {}
            val from900 = bank || isSmsFrom900(title) || isSmsFrom900(conv) || msgs.any { isSmsFrom900(it.first) }
            if (!from900) return
            val parts = mutableListOf<String>()
            if (msgs.isNotEmpty()) msgs.forEach { parts.add(it.second) }
            else {
                val lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                if (lines != null && lines.isNotEmpty()) lines.forEach { parts.add(it.toString()) }
                else (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.let { parts.add(it.toString()) }
            }
            if (parts.isEmpty()) log(ctx, pkg, title, "", "пустой текст")
            for (text in parts) {
                val full = if (bank) "$title $text" else text
                val verdict = when {
                    !Regex("\\d").containsMatchIn(full) -> "нет суммы"
                    SKIP.containsMatchIn(full) -> "служебное (код, отказ)"
                    !OPS.containsMatchIn(full) -> "не операция"
                    else -> "в очередь"
                }
                log(ctx, pkg, title, text, verdict)
                if (verdict != "в очередь") continue
                val id = (if (bank) "push" else "sms") + (full.trim().hashCode().toLong() and 0xffffffffL) + "_" + (sbn.postTime / 600_000)
                append(ctx, id, sbn.postTime, if (bank) "push" else "sms", title, text)
            }
        }
    }
}
