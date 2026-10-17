package com.dasein.poryadok.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.dasein.poryadok.Graph

/**
 * Архив проведённых тренировок: они уходят из истории, но не удаляются (калории и подходы остаются).
 * Из архива тренировку можно вернуть или удалить насовсем. Хранится в ui_state — попадает в резервную копию.
 */
object TrainingArchive {
    private const val KEY = "training_archived_sessions"
    private fun sp() = Graph.app.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
    private fun read(): Set<Long> = runCatching { sp().getString(KEY, "").orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet() }.getOrDefault(emptySet())

    val sessions = mutableStateOf(read())

    private fun save(v: Set<Long>) {
        sessions.value = v
        runCatching { sp().edit().putString(KEY, v.joinToString(",")).apply() }
    }

    fun archive(id: Long) = save(sessions.value + id)
    fun restore(id: Long) = save(sessions.value - id)
    fun reload() { sessions.value = read() }
}
