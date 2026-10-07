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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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


    // ---------- Фото ----------

    @JavascriptInterface fun savePhoto(id: String, b64: String) {
        try { Photos.save(act, id, Base64.decode(b64, Base64.DEFAULT)) } catch (_: Exception) {}
    }

    @JavascriptInterface fun deletePhoto(id: String) = Photos.delete(act, id)

    @JavascriptInterface fun backupNow(json: String) = backup(json)

    // ---------- Виджет ----------

    @JavascriptInterface fun updateWidget(json: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        act.getSharedPreferences("widget", Context.MODE_PRIVATE).edit().putString("data", json).putString("dataDate", today).apply()
        LifeWidget.render(act)
    }

    // ---------- Health Connect ----------

    private var hcDays = 14

    @JavascriptInterface fun hcAvailable(): Boolean = HealthSync.available(act)

    @JavascriptInterface fun hcSync(days: Int) {
        hcDays = days
        setFlag("hc", true)
        if (!HealthSync.available(act)) { callback("hc", JSONObject().put("error", "Health Connect не установлен").toString()); return }
        CoroutineScope(Dispatchers.IO).launch {
            if (HealthSync.hasPermissions(act)) readHc()
            else act.runOnUiThread {
                try {
                    @Suppress("DEPRECATION")
                    act.startActivityForResult(HealthSync.permissionIntent(act), MainActivity.REQ_HC)
                } catch (e: Exception) { callback("hc", JSONObject().put("error", "не удалось открыть разрешения").toString()) }
            }
        }
    }

    fun onHcPermissionResult() {
        CoroutineScope(Dispatchers.IO).launch {
            if (HealthSync.hasPermissions(act)) readHc()
            else callback("hc", JSONObject().put("error", "доступ не выдан").toString())
        }
    }

    private suspend fun readHc() {
        val r = try { HealthSync.read(act, hcDays).toString() } catch (e: Throwable) { JSONObject().put("error", e.message ?: "ошибка").toString() }
        callback("hc", r)
    }

    // ---------- SMS 900 ----------

    private var smsDays = 3

    @JavascriptInterface fun smsList(days: Int) {
        smsDays = days
        setFlag("sms", true)
        act.runOnUiThread {
            if (!act.askPermission(android.Manifest.permission.READ_SMS, MainActivity.REQ_SMS)) return@runOnUiThread
            if (act.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                act.requestPermissions(arrayOf(android.Manifest.permission.RECEIVE_SMS), MainActivity.REQ_SMS + 1)
            smsRead()
        }
    }

    fun smsRead() {
        val r = try { SmsReader.list(act, smsDays) } catch (e: Exception) { "" }
        callback("sms", r)
    }

    // ---------- Операции Сбербанка из уведомлений ----------

    @JavascriptInterface fun bankAccess(): Boolean = BankListener.enabled(act)
    @JavascriptInterface fun smsAllowed(): Boolean =
        act.checkSelfPermission(android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    @JavascriptInterface fun bankQueue(): String = try { BankListener.read(act) } catch (e: Exception) { "[]" }
    @JavascriptInterface fun bankAck(ids: String) { try { BankListener.ack(act, ids) } catch (_: Exception) {} }
    @JavascriptInterface fun openBankAccess() = act.runOnUiThread {
        try { act.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
    }
    @JavascriptInterface fun openAppSettings() = act.runOnUiThread {
        try { act.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.packageName))) } catch (_: Exception) {}
    }

    // ---------- Сон по использованию телефона ----------

    @JavascriptInterface fun usageAllowed(): Boolean = SleepDetector.allowed(act)

    @JavascriptInterface fun openUsageSettings() = act.runOnUiThread {
        try { act.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (_: Exception) {}
    }

    @JavascriptInterface fun detectSleep(date: String): String = try { SleepDetector.detect(act, date) } catch (e: Exception) { "" }

    private fun setFlag(k: String, v: Boolean) = act.getSharedPreferences("flags", Context.MODE_PRIVATE).edit().putBoolean(k, v).apply()

    @JavascriptInterface fun setFlags(hc: Boolean, sms: Boolean) {
        act.getSharedPreferences("flags", Context.MODE_PRIVATE).edit().putBoolean("hc", hc).putBoolean("sms", sms).apply()
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
