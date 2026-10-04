package com.dasein.poryadok.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.dasein.poryadok.Graph

/**
 * Свои названия для встроенных статусов, умных папок и разделов: «Хочу посмотреть» → «В планах» и т. п.
 * Хранятся в ui_state (попадают в резервную копию вместе с настройками). Пустое значение — вернуть стандартное.
 */
object CustomNames {
    private const val PREFIX = "name_"

    /** Меняется при каждом переименовании — экраны, прочитавшие названия, перерисуются. */
    val version = mutableIntStateOf(0)

    private fun sp() = runCatching { Graph.app.getSharedPreferences("ui_state", Context.MODE_PRIVATE) }.getOrNull()

    fun get(key: String, default: String): String {
        version.intValue
        return sp()?.getString(PREFIX + key, null)?.takeIf { it.isNotBlank() } ?: default
    }

    fun set(key: String, value: String?) {
        val e = sp()?.edit() ?: return
        if (value.isNullOrBlank()) e.remove(PREFIX + key) else e.putString(PREFIX + key, value.trim())
        e.apply()
        version.intValue++
    }
}
