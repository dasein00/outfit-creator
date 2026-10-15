package ru.putzhizni.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Журнал сбоев: падения приложения и процесса WebView записываются в files/crash.txt,
 *  чтобы при следующем запуске их можно было посмотреть и отправить (Настройки → Отчёт о сбоях). */
object CrashLog {
    private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { write(app, "Поток ${t.name}: " + android.util.Log.getStackTraceString(e).take(4000)) } catch (_: Throwable) {}
            // Ошибка в фоновом потоке (резервная копия, Health Connect, шаги, банк) не должна закрывать
            // всё приложение: записываем её и продолжаем работу. Падение главного потока передаём системе.
            if (t === android.os.Looper.getMainLooper().thread) prev?.uncaughtException(t, e)
        }
    }

    /** Выполнить действие и записать ошибку в журнал вместо падения приложения. */
    inline fun guard(ctx: Context, where: String, block: () -> Unit) {
        try { block() } catch (e: Throwable) { write(ctx, where + ": " + android.util.Log.getStackTraceString(e).take(3000)) }
    }

    @Synchronized
    fun write(ctx: Context, text: String) {
        try {
            val f = File(ctx.filesDir, "crash.txt")
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val old = if (f.exists()) f.readText().takeLast(12000) else ""
            f.writeText(old + "=== $stamp · версия ${version(ctx)} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n$text\n\n")
        } catch (_: Throwable) {}
    }

    fun read(ctx: Context): String = try { File(ctx.filesDir, "crash.txt").takeIf { it.exists() }?.readText() ?: "" } catch (_: Throwable) { "" }
    fun clear(ctx: Context) { try { File(ctx.filesDir, "crash.txt").delete() } catch (_: Throwable) {} }

    private fun version(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Throwable) { "?" }
}
