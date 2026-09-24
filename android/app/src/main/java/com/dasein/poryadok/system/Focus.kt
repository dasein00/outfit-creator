package com.dasein.poryadok.system

import android.content.Context
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.FocusSession
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.ui.Routes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Состояние таймера живёт в настройках (фаза, время окончания, план в минутах),
 * поэтому переживает закрытие приложения; окончание ловит будильник.
 */
object Focus {
    const val WORK = "work"
    const val BREAK = "break"
    private val mutex = Mutex()

    suspend fun start(phase: String, minutes: Int, taskId: Long?) {
        val now = System.currentTimeMillis()
        Graph.prefs.update {
            it.copy(focusPhase = phase, focusEndsAt = now + minutes * 60_000L, focusCycle = minutes, focusTaskId = taskId ?: 0)
        }
        Alarms.rescheduleAll(Graph.app)
    }

    /** Досрочная остановка: засчитываем уже отработанные минуты. */
    suspend fun stop() {
        mutex.withLock {
            val s = Graph.prefs.now()
            if (s.focusPhase == WORK) {
                val started = s.focusEndsAt - s.focusCycle * 60_000L
                val mins = ((System.currentTimeMillis() - started) / 60_000L).toInt()
                if (mins >= 1) record(s, mins, started)
            }
            Graph.prefs.update { it.copy(focusPhase = "", focusEndsAt = 0) }
        }
        Alarms.rescheduleAll(Graph.app)
    }

    /** Завершает фазу, если время вышло. Возвращает завершённую фазу ровно одному вызывающему. */
    suspend fun finishIfDue(): String? = mutex.withLock {
        val s = Graph.prefs.now()
        if (s.focusPhase.isEmpty() || System.currentTimeMillis() < s.focusEndsAt - 1500) return@withLock null
        if (s.focusPhase == WORK) record(s, s.focusCycle, s.focusEndsAt - s.focusCycle * 60_000L)
        Graph.prefs.update { it.copy(focusPhase = "", focusEndsAt = 0) }
        s.focusPhase
    }

    fun announce(ctx: Context, phase: String) {
        if (phase == WORK) Alarms.notify(ctx, 7001, Alarms.CH_FOCUS, "Фокус-сессия завершена 🎉", "Отличная работа! Сделайте перерыв.", Routes.FOCUS)
        else Alarms.notify(ctx, 7001, Alarms.CH_FOCUS, "Перерыв окончен", "Готовы к следующему подходу?", Routes.FOCUS)
    }

    private suspend fun record(s: Settings, minutes: Int, started: Long) {
        val task = if (s.focusTaskId > 0) Graph.dao.taskNow(s.focusTaskId) else null
        Graph.dao.insertFocus(
            FocusSession(
                startedAt = started, day = Dates.dayOf(started), minutes = minutes,
                taskId = task?.id, label = task?.title ?: "",
            )
        )
    }
}
