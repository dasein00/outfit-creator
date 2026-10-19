package com.dasein.poryadok

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.graphics.toArgb
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
            androidx.compose.runtime.LaunchedEffect(s.accent) {
                com.dasein.poryadok.ui.theme.Palette.accents.getOrNull(s.accent)?.second?.let {
                    com.dasein.poryadok.system.IconSets.rememberAccent(applicationContext, it.toArgb())
                    com.dasein.poryadok.system.Widgets.refresh(applicationContext)
                }
            }
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
            // Уже загруженные видео рецептов и тренировок — до минимального размера, в фоне.
            runCatching { com.dasein.poryadok.system.VideoCompress.autoCompact(applicationContext) }
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
                // Текст или ссылка (Instagram «Поделиться», сайт с рецептом) — в новый рецепт с разбором.
                val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
                // Ссылка на фильм или сериал (Кинопоиск, Шикимори) — сразу карточка в «Фильмы и сериалы».
                if (intent.action == Intent.ACTION_SEND && !shared.isNullOrBlank() && com.dasein.poryadok.system.MediaSearch.isMediaLink(shared)) {
                    com.dasein.poryadok.ui.media.MediaInbox.put(shared.trim())
                    return com.dasein.poryadok.ui.media.MEDIA_LINK_ROUTE
                }
                if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/plain") == true && !shared.isNullOrBlank()) {
                    com.dasein.poryadok.ui.recipes.RecipeInbox.put(shared.trim())
                    return Routes.recipeEdit(0)
                }
                val uri = intent.data ?: if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                // Картинка (скриншот из Fitdays и других приложений весов) — в «Вес», файлы таблиц — в тетрадь финансов.
                val type = intent.type ?: uri?.let { contentResolver.getType(it) }.orEmpty()
                // Книга (EPUB, FB2, MOBI, PDF, DjVu…) — в читалку.
                if (uri != null && isBook(uri, type)) {
                    com.dasein.poryadok.ui.reader.ReaderInbox.pending.value = uri
                    return com.dasein.poryadok.ui.reader.READER_ROUTE
                }
                if (uri != null && type.startsWith("image/")) {
                    com.dasein.poryadok.system.ScaleOcr.process(this, uri)
                    return Routes.health(1)
                }
                uri?.let { NotebookInbox.pending.value = it; Routes.FIN_NOTEBOOK }
            }
            else -> null
        }
    }

    private fun isBook(uri: Uri, type: String): Boolean {
        if (type in BOOK_TYPES) return true
        val name = runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        val ext = name.lowercase().let { if (it.endsWith(".fb2.zip")) "fb2" else it.substringAfterLast('.', "") }
        return ext in setOf("epub", "fb2", "fbz", "mobi", "azw", "azw3", "kf8", "prc", "djvu", "djv", "cbz", "rtf", "docx", "odt") ||
            (ext == "pdf" && type == "application/pdf") || (ext == "txt" && intent?.action == Intent.ACTION_VIEW)
    }

    /** Кнопки громкости листают страницы в читалке. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val h = com.dasein.poryadok.ui.reader.ReaderKeys.handler
        if (h != null && (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) { if (h(if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)) return true }
            else if (event.action == android.view.KeyEvent.ACTION_UP) return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        private val BOOK_TYPES = setOf(
            "application/epub+zip", "application/x-fictionbook+xml", "application/x-fictionbook", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook",
            "application/x-mobi8-ebook", "image/vnd.djvu", "image/x-djvu", "application/djvu", "application/x-cbz", "application/vnd.comicbook+zip", "application/pdf",
        )
        const val EXTRA_ROUTE = "route"
        const val EXTRA_DEMO = "demo"
    }
}
