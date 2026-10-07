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
        try { activeNotifications?.forEach { handle(this, it) } } catch (_: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try { handle(this, sbn) } catch (_: Exception) {}
    }

    companion object {
        private val OPS = Regex("(покупк|оплат|списан|зачислен|перевод|перев[её]л|отправил|выдач|сняти|поступ|возврат|плат[её]ж|зарплат|аванс|пополн|кешб|кэшб)", RegexOption.IGNORE_CASE)
        private val SKIP = Regex("(код|пароль|никому не сообщайте|вход в сбербанк|отказ|недостаточно)", RegexOption.IGNORE_CASE)

        fun isBankPackage(pkg: String) = pkg.contains("sber", true)
        private fun isSmsPackage(pkg: String) = Regex("messag|mms|sms|telephony", RegexOption.IGNORE_CASE).containsMatchIn(pkg)
        private fun isSmsFrom900(title: String) = Regex("^\\s*(\\+?7?900|900|SBERBANK|СберБанк)\\s*$", RegexOption.IGNORE_CASE).matches(title)

        fun enabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(ctx, BankListener::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }

        private fun dir(ctx: Context) = File(ctx.filesDir, "bank").apply { mkdirs() }
        private fun queue(ctx: Context) = File(dir(ctx), "queue.jsonl")
        private fun seen(ctx: Context) = File(dir(ctx), "seen.txt")

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
            if (!bank && !isSmsFrom900(title)) return
            val parts = mutableListOf<String>()
            val lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (lines != null && lines.isNotEmpty()) lines.forEach { parts.add(it.toString()) }
            else (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.let { parts.add(it.toString()) }
            for (text in parts) {
                val full = if (bank) "$title $text" else text
                if (!OPS.containsMatchIn(full) || SKIP.containsMatchIn(full) || !Regex("\\d").containsMatchIn(full)) continue
                val id = (if (bank) "push" else "sms") + (full.trim().hashCode().toLong() and 0xffffffffL) + "_" + (sbn.postTime / 600_000)
                append(ctx, id, sbn.postTime, if (bank) "push" else "sms", title, text)
            }
        }
    }
}
