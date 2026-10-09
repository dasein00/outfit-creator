package com.dasein.poryadok

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dasein.poryadok.system.Body
import com.dasein.poryadok.system.SleepTracker
import com.dasein.poryadok.system.Steps
import com.dasein.poryadok.ui.AppRoot
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.finance.NotebookInbox
import com.dasein.poryadok.ui.LockScreen
import com.dasein.poryadok.ui.theme.PoryadokTheme
import kotlinx.coroutines.launch

object Lock {
    val unlocked = mutableStateOf(false)
    private var stoppedAt = 0L

    fun onStop() { stoppedAt = System.currentTimeMillis() }
    fun onStart() {
        if (stoppedAt != 0L && System.currentTimeMillis() - stoppedAt > 30_000) unlocked.value = false
    }
}

class MainActivity : FragmentActivity() {
    /** Экран, который нужно открыть из уведомления или виджета. */
    val deepLink = mutableStateOf<String?>(null)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Graph.init(this)
        // При повороте экрана активность пересоздаётся с тем же intent — присланный файл (скриншот весов,
        // таблица финансов) обрабатываем только при первом запуске, иначе он открывается снова и снова.
        if (savedInstanceState == null) deepLink.value = intent?.let { routeOf(it) }
        if (savedInstanceState == null && intent?.getBooleanExtra(EXTRA_DEMO, false) == true) Graph.scope.launch { DemoData.fill() }
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        // Виджет перерисовывается при каждом запуске — после обновления сразу виден новый вид (погода вместо графика веса).
        if (savedInstanceState == null) Graph.scope.launch { runCatching { com.dasein.poryadok.system.Widgets.refresh(applicationContext) } }

        setContent {
            val settings by Graph.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            PoryadokTheme(theme = s.theme, accent = s.accent) {
                val unlocked by Lock.unlocked
                if (s.pinHash.isNotEmpty() && !unlocked) {
                    LockScreen(this, s) { Lock.unlocked.value = true }
                } else {
                    AppRoot(s, deepLink)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeOf(intent)?.let { deepLink.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Новости на месяц вперёд: есть сеть — догружаем сразу; нет — WorkManager догрузит, когда она появится.
        Graph.scope.launch {
            runCatching { com.dasein.poryadok.system.Offline.onAppStart(applicationContext) }
            runCatching { com.dasein.poryadok.system.Offline.sync(applicationContext) }
        }
        // Health Connect отдаёт данные только приложению на экране — подтягиваем шаги при каждом открытии.
        Graph.scope.launch {
            runCatching { Steps.sync(applicationContext) }
            runCatching { SleepTracker.run(applicationContext) }
            // Весы: если доступ к весу выдан — читаем всегда, без отдельного включения.
            runCatching { Body.syncHealthConnect(applicationContext, 30) }
            runCatching { Steps.ensureScheduled(applicationContext) }
            // Сбер: входящие переводы, записанные как расход, и дубли SMS + пуш.
            runCatching { com.dasein.poryadok.system.Sber.repair() }
        }
    }

    /** Куда вести: явный маршрут, пояснение Health Connect или открытый файл тетради. */
    private fun routeOf(intent: Intent): String? {
        intent.getStringExtra(EXTRA_ROUTE)?.let { return it }
        return when (intent.action) {
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE", "android.intent.action.VIEW_PERMISSION_USAGE" -> Routes.STEPS
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                val uri = intent.data ?: if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                // Картинка (скриншот из Fitdays и других приложений весов) — в «Вес», файлы таблиц — в тетрадь финансов.
                val type = intent.type ?: uri?.let { contentResolver.getType(it) }.orEmpty()
                if (uri != null && type.startsWith("image/")) {
                    com.dasein.poryadok.system.ScaleOcr.process(this, uri)
                    return Routes.health(1)
                }
                uri?.let { NotebookInbox.pending.value = it; Routes.FIN_NOTEBOOK }
            }
            else -> null
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_DEMO = "demo"
    }
}
