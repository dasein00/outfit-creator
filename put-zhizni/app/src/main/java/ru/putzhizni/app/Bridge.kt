package ru.putzhizni.app

import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.VibrationEffect
import android.os.Vibrator
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Мост между интерфейсом (JavaScript, объект window.Android) и системой Android.
 * Асинхронные ответы приходят в JS через window.__nativeCb(тип, значение).
 */
class Bridge(private val act: MainActivity) {

    private var pendingSave: ByteArray? = null

    fun callback(type: String, value: String) {
        act.js("window.__nativeCb&&window.__nativeCb(${JSONObject.quote(type)},${JSONObject.quote(value)})")
    }

    @JavascriptInterface fun isAndroid(): Boolean = true

    @JavascriptInterface fun version(): String = try {
        act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    // ---------- Файлы ----------

    @JavascriptInterface fun saveFile(name: String, mime: String, content: String) =
        startSave(name, mime, content.toByteArray(Charsets.UTF_8))

    @JavascriptInterface fun saveFileBase64(name: String, mime: String, b64: String) =
        startSave(name, mime, Base64.decode(b64, Base64.DEFAULT))

    private fun startSave(name: String, mime: String, bytes: ByteArray) = act.runOnUiThread {
        pendingSave = bytes
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = mime
        i.putExtra(Intent.EXTRA_TITLE, name)
        try {
            @Suppress("DEPRECATION")
            act.startActivityForResult(i, MainActivity.REQ_SAVE)
        } catch (e: Exception) {
            pendingSave = null; callback("save", "error")
        }
    }

    fun onSaveResult(uri: Uri?) {
        val bytes = pendingSave; pendingSave = null
        if (uri == null || bytes == null) { callback("save", "cancel"); return }
        try {
            act.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
            callback("save", "ok")
        } catch (e: Exception) { callback("save", "error") }
    }

    @JavascriptInterface fun openFile() = act.runOnUiThread {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        try {
            @Suppress("DEPRECATION")
            act.startActivityForResult(i, MainActivity.REQ_OPEN)
        } catch (e: Exception) { callback("open", "") }
    }

    fun onOpenResult(uri: Uri?) {
        if (uri == null) { callback("open", ""); return }
        try {
            val text = act.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            callback("open", text)
        } catch (e: Exception) { callback("open", "") }
    }

    @JavascriptInterface fun print(title: String) = act.runOnUiThread {
        val pm = act.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = act.web.createPrintDocumentAdapter(title)
        pm.print(title, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }

    @JavascriptInterface fun shareText(title: String, text: String) = act.runOnUiThread {
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_SUBJECT, title)
        i.putExtra(Intent.EXTRA_TEXT, text)
        act.startActivity(Intent.createChooser(i, title))
    }

    // ---------- Автоматическая резервная копия во внутренней памяти ----------

    @JavascriptInterface fun backup(json: String) {
        try {
            val dir = File(act.filesDir, "backup").apply { mkdirs() }
            val tmp = File(dir, "current.tmp")
            tmp.writeText(json)
            tmp.renameTo(File(dir, "current.json"))
            // Ежедневные копии, храним последние 14.
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            File(dir, "day-$day.json").writeText(json)
            dir.listFiles { f -> f.name.startsWith("day-") }?.sortedBy { it.name }?.dropLast(14)?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    @JavascriptInterface fun readBackup(): String = try {
        File(File(act.filesDir, "backup"), "current.json").readText()
    } catch (e: Exception) { "" }

    @JavascriptInterface fun listBackups(): String {
        val dir = File(act.filesDir, "backup")
        val names = dir.listFiles { f -> f.name.startsWith("day-") }?.map { it.name.removePrefix("day-").removeSuffix(".json") }?.sorted()
            ?: emptyList()
        return names.joinToString(",")
    }

    @JavascriptInterface fun readBackupDay(day: String): String = try {
        File(File(act.filesDir, "backup"), "day-$day.json").readText()
    } catch (e: Exception) { "" }

    @JavascriptInterface fun wipe() {
        try { File(act.filesDir, "backup").deleteRecursively() } catch (_: Exception) {}
        ReminderScheduler.replaceAll(act, "[]")
        act.getSharedPreferences(StepCounter.PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ---------- Напоминания ----------

    @JavascriptInterface fun notificationsAllowed(): Boolean = Notifications.allowed(act)

    @JavascriptInterface fun requestNotifications() = act.runOnUiThread {
        if (Build.VERSION.SDK_INT < 33) callback("notif", Notifications.allowed(act).toString())
        else if (act.askPermission(MainActivity.PERM_NOTIF, MainActivity.REQ_NOTIF)) callback("notif", "true")
    }

    @JavascriptInterface fun scheduleReminders(json: String) {
        ReminderScheduler.replaceAll(act, json)
    }

    @JavascriptInterface fun testNotification(title: String, text: String) {
        Notifications.show(act, 99999, title, text)
    }

    // ---------- Шаги ----------

    @JavascriptInterface fun stepsAvailable(): Boolean {
        val sm = act.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    @JavascriptInterface fun requestSteps() = act.runOnUiThread {
        if (Build.VERSION.SDK_INT >= 29 && !act.askPermission(MainActivity.PERM_STEPS, MainActivity.REQ_STEPS)) return@runOnUiThread
        StepCounter.read(act) { steps -> callback("steps", steps.toString()) }
        StepCounter.scheduleMidnight(act)
    }

    // ---------- Блокировка ----------

    @JavascriptInterface fun biometricAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val bm = act.getSystemService(BiometricManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 30)
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        else
            @Suppress("DEPRECATION") (bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS)
    }

    @JavascriptInterface fun authenticate() = act.runOnUiThread {
        if (Build.VERSION.SDK_INT < 29) { callback("auth", "false"); return@runOnUiThread }
        val b = BiometricPrompt.Builder(act).setTitle("Путь жизни").setSubtitle("Подтвердите вход")
        if (Build.VERSION.SDK_INT >= 30) {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
        }
        try {
            b.build().authenticate(CancellationSignal(), act.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = callback("auth", "true")
                override fun onAuthenticationError(code: Int, msg: CharSequence?) = callback("auth", "false")
            })
        } catch (e: Exception) { callback("auth", "false") }
    }

    // ---------- Прочее ----------

    @JavascriptInterface fun setBars(hex: String, light: Boolean) = act.setBars(hex, light)

    @JavascriptInterface fun vibrate(ms: Int) {
        try {
            val v = act.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(ms.toLong().coerceIn(5, 400), VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }
}
