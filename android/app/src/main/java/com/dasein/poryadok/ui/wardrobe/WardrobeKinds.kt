package com.dasein.poryadok.ui.wardrobe

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dasein.poryadok.Graph
import kotlinx.serialization.Serializable

/** Своя категория (тип) вещей: название в единственном и множественном числе, раздел и длина для размера в наряде. */
@Serializable
data class CustomKind(val id: String, val single: String, val plural: String, val parent: String, val cm: Float = 60f)

/** Настройки категорий: свои, переименованные и скрытые встроенные, длина по типу. */
@Serializable
data class KindsConfig(
    val custom: List<CustomKind> = emptyList(),
    /** id встроенного типа → [ед. число, мн. число]. */
    val renamed: Map<String, List<String>> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val cm: Map<String, Float> = emptyMap(),
)

/**
 * Категории (типы) вещей: встроенные плюс свои. Их можно добавлять, переименовывать, удалять (встроенные — скрывать)
 * и менять длину, от которой зависит размер вещи в наряде. Хранится в настройках телефона.
 */
object Kinds {
    private fun sp() = Graph.app.getSharedPreferences("wardrobe", Context.MODE_PRIVATE)
    private var loaded = false
    private var state by mutableStateOf(KindsConfig())

    val cfg: KindsConfig
        get() {
            if (!loaded) {
                loaded = true
                state = runCatching { wjson.decodeFromString(KindsConfig.serializer(), sp().getString("kinds", null) ?: "{}") }.getOrDefault(KindsConfig())
            }
            return state
        }

    private fun save(c: KindsConfig) {
        state = c
        loaded = true
        sp().edit().putString("kinds", wjson.encodeToString(KindsConfig.serializer(), c)).apply()
    }

    fun custom(k: String) = cfg.custom.firstOrNull { it.id == k }
    fun isKind(k: String) = k in KIND_CM || custom(k) != null

    /** Все видимые типы: встроенные в порядке «основные сначала», затем свои. */
    fun all(): List<String> = KIND_ORDER.filter { it !in cfg.hidden } + cfg.custom.map { it.id }

    /** Типы раздела (верх, низ…). */
    fun of(category: String): List<String> =
        builtinKindsOf(category).filter { it !in cfg.hidden } + cfg.custom.filter { it.parent == category }.map { it.id }

    fun parentOf(k: String): String = custom(k)?.parent ?: CATEGORIES.firstOrNull { k in builtinKindsOf(it) } ?: "верх"
    fun label(k: String): String = custom(k)?.single ?: cfg.renamed[k]?.getOrNull(0) ?: KIND_LABELS[k] ?: k
    fun plural(k: String): String = custom(k)?.plural ?: cfg.renamed[k]?.getOrNull(1) ?: KIND_PLURAL[k] ?: label(k)
    fun cm(k: String): Float = cfg.cm[k] ?: custom(k)?.cm ?: KIND_CM[k] ?: 60f

    fun add(single: String, plural: String, parent: String, cm: Float): String {
        val id = "c_" + System.currentTimeMillis().toString(36)
        save(cfg.copy(custom = cfg.custom + CustomKind(id, single, plural, parent, cm)))
        return id
    }

    fun edit(k: String, single: String, plural: String, parent: String, cm: Float) {
        val c = cfg
        save(
            if (custom(k) != null) c.copy(custom = c.custom.map { if (it.id == k) it.copy(single = single, plural = plural, parent = parent, cm = cm) else it })
            else c.copy(renamed = c.renamed + (k to listOf(single, plural)), cm = c.cm + (k to cm))
        )
    }

    /** Удалить свой тип или скрыть встроенный. */
    fun remove(k: String) {
        val c = cfg
        save(if (custom(k) != null) c.copy(custom = c.custom.filter { it.id != k }) else c.copy(hidden = c.hidden + k))
    }

    /** Вернуть скрытые встроенные типы и прежние названия. */
    fun restoreBuiltins() = save(cfg.copy(hidden = emptySet(), renamed = emptyMap(), cm = emptyMap()))
}
