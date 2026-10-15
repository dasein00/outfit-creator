package com.dasein.poryadok

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dasein.poryadok.data.AppDb
import com.dasein.poryadok.data.ExtraDao
import com.dasein.poryadok.data.ExtraDb
import com.dasein.poryadok.data.LifeDao
import com.dasein.poryadok.data.Prefs
import com.dasein.poryadok.data.RecipeRepo
import com.dasein.poryadok.system.SleepTracker
import com.dasein.poryadok.system.Steps
import android.util.Log
import com.dasein.poryadok.system.Alarms
import com.dasein.poryadok.system.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Graph.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = Lock.onStart()
            override fun onStop(owner: LifecycleOwner) = Lock.onStop()
        })
    }

    /** Системе не хватает памяти — отдаём кэши картинок (они загрузятся снова при надобности). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            runCatching { com.dasein.poryadok.ui.common.ImageMemory.clear() }
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            runCatching { com.dasein.poryadok.ui.common.Glyphs.evictAll(); com.dasein.poryadok.ui.common.IconTint.evictAll() }
        }
    }
}

/**
 * Журнал сбоев: если приложение всё-таки упадёт, причина сохраняется в files/crash.log (последние 5 сбоев).
 * Её видно в «Настройки» → «О приложении» и можно отправить разработчику; приложение при этом работает как раньше.
 */
object CrashLog {
    fun file(ctx: android.content.Context) = java.io.File(ctx.filesDir, "crash.log")

    fun install(ctx: android.content.Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val f = file(app)
                val old = if (f.exists()) f.readText().split("\n=====\n").takeLast(4) else emptyList()
                val now = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale("ru")).format(java.util.Date())
                val ver = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                f.writeText((old + "$now · версия $ver · поток ${t.name}\n${Log.getStackTraceString(e).take(6000)}").joinToString("\n=====\n"))
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun read(ctx: android.content.Context): String = runCatching { file(ctx).readText() }.getOrDefault("")
    fun clear(ctx: android.content.Context) { file(ctx).delete() }
}

/** Простой контейнер зависимостей: одна база, одни настройки, общий фоновый scope. */
object Graph {
    lateinit var app: Context
        private set
    lateinit var db: AppDb
        private set
    lateinit var extraDb: ExtraDb
        private set
    lateinit var prefs: Prefs
        private set
    val dao: LifeDao get() = db.dao()
    val extra: ExtraDao get() = extraDb.dao()
    val pages: com.dasein.poryadok.data.PageDao get() = extraDb.pages()
    val training: com.dasein.poryadok.data.TrainingDao get() = extraDb.training()
    val days: com.dasein.poryadok.data.DaysDao get() = extraDb.days()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ready = false

    @OptIn(FlowPreview::class)
    @Synchronized
    fun init(context: Context) {
        if (ready) return
        ready = true
        app = context.applicationContext
        db = AppDb.create(app)
        extraDb = ExtraDb.create(app)
        prefs = Prefs(app)
        // Встроенный файл оформления (если есть) — до первого экрана, только на новой установке.
        com.dasein.poryadok.system.UiProfile.applyBundled(app)
        Alarms.createChannels(app)
        scope.launch {
            Repo.seed()
            runCatching { Repo.migrateGlyphs() }.onFailure { Log.e("DASEIN", "glyphs", it) }
            // Счета как в жизни: дебетовая карта Сбера и накопительный счёт; остальные сливаются в карту (один раз).
            runCatching { com.dasein.poryadok.system.AccountsCleanup.runOnce(app) }.onFailure { Log.e("DASEIN", "accounts", it) }
            runCatching { RecipeRepo.seed(); RecipeRepo.materializeRepeats() }.onFailure { Log.e("DASEIN", "recipes seed", it) }
            runCatching { com.dasein.poryadok.data.TrainingRepo.seed() }.onFailure { Log.e("DASEIN", "training seed", it) }
            runCatching { com.dasein.poryadok.data.PagesRepo.migrateOldNotes(app) }.onFailure { Log.e("DASEIN", "pages", it) }
            runCatching { Steps.ensureScheduled(app) }.onFailure { Log.e("DASEIN", "steps", it) }
            runCatching { SleepTracker.ensureScheduled(app) }.onFailure { Log.e("DASEIN", "sleep", it) }
            runCatching { com.dasein.poryadok.system.Weather.schedule(app) }.onFailure { Log.e("DASEIN", "weather", it) }
            // Новости истории и культуры на месяц вперёд — чтобы было что почитать без интернета.
            runCatching { com.dasein.poryadok.system.Offline.onAppStart(app) }.onFailure { Log.e("DASEIN", "offline", it) }
            Repo.processRecurring()
            Alarms.rescheduleAll(app)
            Widgets.refresh(app)
        }
        scope.launch {
            merge(
                dao.tasks().map { }, dao.events().map { }, dao.reminders().map { },
                dao.habits().map { }, dao.habitLogs().map { }, dao.recurring().map { },
            ).drop(6).debounce(700).collect {
                Alarms.rescheduleAll(app)
                Widgets.refresh(app)
            }
        }
    }
}

object BuildConfigInfo {
    fun version(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" }.getOrDefault("")
}
