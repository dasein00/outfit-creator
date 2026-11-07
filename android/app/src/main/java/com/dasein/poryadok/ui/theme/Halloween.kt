package com.dasein.poryadok.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.dasein.poryadok.logic.HalloweenSeason

/**
 * Хэллоуинский режим оформления (с 15 октября по 2 ноября): ночной фиолетовый фон с луной, кладбище с туманом,
 * моргающие красные глаза, рука из могилы, летучие мыши на ветках, пауки в норах на плашках, привидение
 * вместо саней Санты и листопад при входе. Настройки — в ui_state, как остальное оформление.
 */
object Halloween {
    private const val P = "halloween_"

    /** Фон: градиент — центр, середина, края (тёмная тема и светлая). */
    class BgStyle(val name: String, val dark: List<Color>, val light: List<Color>)

    /** Цвет плашек — полупрозрачный, сквозь него виден фон. */
    class CardStyle(val name: String, val dark: Color, val light: Color)

    private fun c(v: Long) = Color(v)

    val BG_STYLES = listOf(
        BgStyle("Лунная ночь", listOf(c(0xFF7A4FA8), c(0xFF3E2163), c(0xFF1A0F2C)), listOf(c(0xFFF4EDFB), c(0xFFE4D6F3), c(0xFFCDB8E6))),
        BgStyle("Болото", listOf(c(0xFF5E4A7E), c(0xFF2C2840), c(0xFF121A10)), listOf(c(0xFFF1F4EA), c(0xFFE0E7D3), c(0xFFC9D5B8))),
        BgStyle("Тыквенная", listOf(c(0xFF8A4A1E), c(0xFF4A2410), c(0xFF1F0E06)), listOf(c(0xFFFDF1E6), c(0xFFF6DEC8), c(0xFFECC6A3))),
        BgStyle("Полночь", listOf(c(0xFF3A2A5A), c(0xFF1E1530), c(0xFF0A0710)), listOf(c(0xFFEFEDF6), c(0xFFDCD8EA), c(0xFFC4BEDB))),
    )

    val CARD_STYLES = listOf(
        CardStyle("Дымчатая слива", c(0xA62A1838), c(0xB3F3ECFA)),
        CardStyle("Стекло", c(0x8C2E2638), c(0xA6FFFFFF)),
        CardStyle("Тыква", c(0x99592A10), c(0xB3FBE7D3)),
        CardStyle("Болотные", c(0xA61C2A16), c(0xB3E5EEDC)),
        CardStyle("Тёмные", c(0xB3100A16), c(0xCCFFFFFF)),
    )

    /** Сочетание цветов иконок: тыквенные огоньки, лаймовый свет луны, лиловые формы и сливовые тени. */
    val ICONS = IconPalette("Хэллоуин", 0xF5A623, 0xC9F05A, 0xF2EEF6, 0xA77BC9, 0x2A1838)

    val auto = mutableStateOf(true)
    val preview = mutableStateOf(false)
    val fog = mutableStateOf(true)
    val bats = mutableStateOf(true)
    val eyes = mutableStateOf(true)
    val hand = mutableStateOf(true)
    val ghost = mutableStateOf(true)
    val spiders = mutableStateOf(true)
    val leaves = mutableStateOf(true)
    val bgStyle = mutableIntStateOf(0)
    val cardStyle = mutableIntStateOf(0)

    fun bg(): BgStyle = BG_STYLES.getOrElse(bgStyle.intValue) { BG_STYLES[0] }
    fun card(): CardStyle = CARD_STYLES.getOrElse(cardStyle.intValue) { CARD_STYLES[0] }

    /** Хэллоуинская тема сейчас включена (дата — общая с новогодним режимом). */
    val active: Boolean get() = HalloweenSeason.themeActive(auto.value, Holiday.today.value, preview.value)

    private var loaded = false
    private fun sp(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
    private val flags get() = listOf("auto" to auto, "fog" to fog, "bats" to bats, "eyes" to eyes, "hand" to hand, "ghost" to ghost, "spiders" to spiders, "leaves" to leaves)

    fun load(ctx: Context) {
        if (loaded) return
        val p = sp(ctx)
        flags.forEach { (k, s) -> s.value = p.getBoolean(P + k, true) }
        preview.value = p.getBoolean(P + "preview", false)
        bgStyle.intValue = p.getInt(P + "bg_style", 0).coerceIn(0, BG_STYLES.lastIndex)
        cardStyle.intValue = p.getInt(P + "card_style", 0).coerceIn(0, CARD_STYLES.lastIndex)
        loaded = true
    }

    fun reload(ctx: Context) { loaded = false; load(ctx) }

    /** Выключатель по имени (auto, fog, bats, eyes, hand, ghost, spiders, leaves). */
    fun set(ctx: Context, key: String, v: Boolean) {
        flags.firstOrNull { it.first == key }?.second?.value = v
        sp(ctx).edit().putBoolean(P + key, v).apply()
    }

    /** Тестовый режим: Хэллоуин в любой день. Новогодний тестовый режим при этом выключается — темы не смешиваются. */
    fun setPreview(ctx: Context, v: Boolean) {
        preview.value = v; sp(ctx).edit().putBoolean(P + "preview", v).apply()
        if (v) { if (Holiday.preview.value) Holiday.setPreview(ctx, false); Holiday.letItSnow() }
    }

    fun setBgStyle(ctx: Context, i: Int) { bgStyle.intValue = i; sp(ctx).edit().putInt(P + "bg_style", i).apply() }
    fun setCardStyle(ctx: Context, i: Int) { cardStyle.intValue = i; sp(ctx).edit().putInt(P + "card_style", i).apply() }
}
