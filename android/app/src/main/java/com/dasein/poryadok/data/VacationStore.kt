package com.dasein.poryadok.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.dasein.poryadok.Graph
import com.dasein.poryadok.logic.Vacation
import com.dasein.poryadok.logic.VacationMath

/** Отпуска пользователя. Хранятся в ui_state — попадают в резервную копию (но не в файл оформления). */
object VacationStore {
    const val KEY = "vacations"
    private fun sp() = Graph.app.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    val list = mutableStateOf(runCatching { VacationMath.parse(sp().getString(KEY, "").orEmpty()) }.getOrDefault(emptyList()))

    /** Перечитать после восстановления копии. */
    fun reload() { list.value = runCatching { VacationMath.parse(sp().getString(KEY, "").orEmpty()) }.getOrDefault(emptyList()) }

    private fun save(v: List<Vacation>) {
        list.value = v.sortedBy { it.start }
        runCatching { sp().edit().putString(KEY, VacationMath.format(list.value)).apply() }
    }

    fun put(old: Vacation?, new: Vacation) = save(list.value.filter { it != old } + new)

    fun remove(v: Vacation) = save(list.value.filter { it != v })
}
