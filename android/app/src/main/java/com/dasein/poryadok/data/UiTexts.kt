package com.dasein.poryadok.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.dasein.poryadok.Graph

/**
 * Тексты интерфейса: каталог из assets/ui_texts.tsv + правки пользователя. Правки хранятся там же, где названия
 * кнопок (CustomNames, ключ «btn:<исходный текст>»), — попадают в резервную копию и файл оформления.
 */
object UiTexts {
    /** Меняется при каждой правке — надписи на экране перерисуются. */
    val version = mutableIntStateOf(0)
    private const val PREFIX = "name_btn:"

    @Volatile private var engine: UiTextEngine? = null
    private var catalog: List<UiTextEngine.Entry>? = null

    @Synchronized
    fun engine(): UiTextEngine {
        engine?.let { return it }
        val ctx = runCatching { Graph.app }.getOrNull()
        val entries = catalog ?: runCatching {
            ctx!!.assets.open("ui_texts.tsv").bufferedReader().useLines { UiTextEngine.parse(it) }
        }.getOrDefault(emptyList()).also { if (ctx != null) catalog = it }
        val user = runCatching {
            ctx!!.getSharedPreferences("ui_state", Context.MODE_PRIVATE).all.mapNotNull { (k, v) ->
                if (k.startsWith(PREFIX) && v is String) k.removePrefix(PREFIX) to v else null
            }.toMap()
        }.getOrDefault(emptyMap())
        return UiTextEngine(entries, user).also { if (ctx != null) engine = it }
    }

    fun tr(s: String): String = runCatching { engine().translate(s) }.getOrDefault(s)

    /** Своя правка: null — вернуть вариант по умолчанию. */
    fun set(key: String, value: String?) {
        val sp = runCatching { Graph.app.getSharedPreferences("ui_state", Context.MODE_PRIVATE) }.getOrNull() ?: return
        // Без trim: у частей составных фраз важны пробелы по краям.
        if (value.isNullOrEmpty()) sp.edit().remove(PREFIX + key).apply() else sp.edit().putString(PREFIX + key, value).apply()
        CustomNames.version.intValue++
        invalidate()
    }

    fun resetAll() {
        val sp = runCatching { Graph.app.getSharedPreferences("ui_state", Context.MODE_PRIVATE) }.getOrNull() ?: return
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith(PREFIX) }.forEach { e.remove(it) }
        e.apply()
        invalidate()
    }

    internal fun invalidate() {
        synchronized(this) { engine = null }
        version.intValue++
    }

    private const val EDITOR_KEY = "text_editor_mode"
    /** Режим редактора: удержание любого текста 3 секунды открывает окно правки. */
    val editorMode = androidx.compose.runtime.mutableStateOf(
        runCatching { Graph.app.getSharedPreferences("ui_state", Context.MODE_PRIVATE).getBoolean(EDITOR_KEY, false) }.getOrDefault(false)
    )
    /** Текст (как в коде), который сейчас правят в режиме редактора. */
    val editTarget = androidx.compose.runtime.mutableStateOf<String?>(null)

    fun setEditorMode(on: Boolean) {
        editorMode.value = on
        runCatching { Graph.app.getSharedPreferences("ui_state", Context.MODE_PRIVATE).edit().putBoolean(EDITOR_KEY, on).apply() }
    }

    /** Прогрев в фоне при старте, чтобы первый экран не ждал чтения каталога. */
    fun warmUp() { runCatching { engine() } }
}
