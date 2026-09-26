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
        Graph.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = Lock.onStart()
            override fun onStop(owner: LifecycleOwner) = Lock.onStop()
        })
    }
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
        Alarms.createChannels(app)
        scope.launch {
            Repo.seed()
            runCatching { RecipeRepo.seed(); RecipeRepo.materializeRepeats() }.onFailure { Log.e("DASEIN", "recipes seed", it) }
            runCatching { Steps.ensureScheduled(app) }.onFailure { Log.e("DASEIN", "steps", it) }
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
