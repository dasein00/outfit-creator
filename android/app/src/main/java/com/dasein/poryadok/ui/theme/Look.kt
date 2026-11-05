package com.dasein.poryadok.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Свои настройки оформления: акцент, фон, цвет текста, шрифт, размер текста, вид иконок, контрастный режим.
 * Хранятся в ui_state — поэтому попадают и в файл оформления, и в резервную копию.
 * 0 в цветах — «как в теме по умолчанию».
 */
data class LookSettings(
    val accentRgb: Int = 0,
    val bgRgb: Int = 0,
    val textRgb: Int = 0,
    val font: Int = 0,
    val scale: Float = 1f,
    val icons: Int = 0,
    val iconRgb: Int = 0,
    val contrast: Boolean = false,
    val cardRound: Int = 18,
    /** Глубокий фон-градиент: номер в [Look.DEEP_BACKGROUNDS], -1 — однотонный фон. */
    val bgStyle: Int = -1,
    /** Плашки: номер в [Look.CARDS], -1 — по теме, -2 — свой цвет [cardRgb]. */
    val cardStyle: Int = -1,
    val cardRgb: Int = 0,
)

/** Глубокий фон: бархатный градиент — центр, середина, края. */
class DeepBg(val name: String, val colors: List<Color>)

/** Цвет плашек для тёмной и светлой темы (полупрозрачный — сквозь него виден фон). */
class CardColor(val name: String, val dark: Color, val light: Color)

object Look {
    /** Шрифты: заголовки / основной текст. */
    val FONTS = listOf(
        "Medeo — современный шрифт интерфейсов" to 5,
        "Классика: заголовки с засечками" to 0,
        "Современный: всё без засечек" to 1,
        "Книжный: всё с засечками" to 2,
        "Моноширинный" to 3,
        "Рукописные заголовки" to 4,
    )

    val ICON_MODES = listOf(
        "Цветные — как нарисованы",
        "Контрастные — подстраиваются под фон",
        "В цвет акцента",
        "Свой цвет",
    )

    /** Фоны: тёмные и светлые. Тема (светлая/тёмная) определяется по яркости фона. */
    val BACKGROUNDS = listOf(
        "Чернила" to 0xFF211D18.toInt(), "Графит" to 0xFF1E1F22.toInt(), "Полночь" to 0xFF141821.toInt(),
        "Чёрный (AMOLED)" to 0xFF000000.toInt(), "Лес" to 0xFF162019.toInt(), "Вино" to 0xFF22161A.toInt(),
        "Кофе" to 0xFF2A2018.toInt(), "Океан" to 0xFF0F1E26.toInt(),
        "Крем" to 0xFFF7F3EB.toInt(), "Белый" to 0xFFFFFFFF.toInt(), "Туман" to 0xFFF1F2F4.toInt(),
        "Мята" to 0xFFEEF6F0.toInt(), "Небо" to 0xFFEEF3F9.toInt(), "Пудра" to 0xFFF9EFEF.toInt(), "Песок" to 0xFFF3EBDD.toInt(),
    )

    val TEXTS = listOf(
        "Слоновая кость" to 0xFFF0ECE3.toInt(), "Белый" to 0xFFFFFFFF.toInt(), "Песочный" to 0xFFE9DCC4.toInt(),
        "Мятный" to 0xFFDDF0E4.toInt(), "Голубой" to 0xFFDCEAF5.toInt(),
        "Чернила" to 0xFF211D18.toInt(), "Чёрный" to 0xFF000000.toInt(), "Графит" to 0xFF2E3138.toInt(), "Тёмно-синий" to 0xFF1B2A41.toInt(),
    )

    private fun c(v: Long) = Color(v)

    /** Палитра глубоких цветов для фона: градиент, светлее к центру и глубже к краям. */
    val DEEP_BACKGROUNDS = listOf(
        DeepBg("Изумруд", listOf(c(0xFF1F5C46), c(0xFF123829), c(0xFF081C14))),
        DeepBg("Вино", listOf(c(0xFF6E1A2C), c(0xFF46101D), c(0xFF22070E))),
        DeepBg("Сапфир", listOf(c(0xFF234A86), c(0xFF142B55), c(0xFF09152C))),
        DeepBg("Аметист", listOf(c(0xFF4C2A78), c(0xFF2E1A4C), c(0xFF160C26))),
        DeepBg("Океан", listOf(c(0xFF145A66), c(0xFF0C3840), c(0xFF051B20))),
        DeepBg("Полночь", listOf(c(0xFF1E2A4A), c(0xFF111A30), c(0xFF070B16))),
        DeepBg("Хвоя", listOf(c(0xFF2E4A22), c(0xFF1C2F15), c(0xFF0D170A))),
        DeepBg("Бронза", listOf(c(0xFF6A4A1C), c(0xFF433010), c(0xFF201607))),
        DeepBg("Шоколад", listOf(c(0xFF5A3B28), c(0xFF382418), c(0xFF1C110B))),
        DeepBg("Графит", listOf(c(0xFF3A3F47), c(0xFF23272D), c(0xFF111316))),
        DeepBg("Жемчуг", listOf(c(0xFFFFFFFF), c(0xFFF2EFEA), c(0xFFE2DDD4))),
        DeepBg("Лаванда", listOf(c(0xFFF6F2FC), c(0xFFE9E1F5), c(0xFFD6CAEA))),
        DeepBg("Шалфей", listOf(c(0xFFF2F8F3), c(0xFFDFEDE3), c(0xFFC8DECF))),
        DeepBg("Персик", listOf(c(0xFFFFF4EC), c(0xFFF8E2D2), c(0xFFEDCDB6))),
    )

    /** Цвета плашек: полупрозрачные и плотные, для тёмной и светлой темы. */
    val CARDS = listOf(
        CardColor("Матовое стекло", c(0x8C2E323C), c(0xA6FFFFFF)),
        CardColor("Дымка", c(0x26FFFFFF), c(0x12000000)),
        CardColor("Плотные", c(0xF21C1C1E), c(0xF7FFFFFF)),
        CardColor("Бордо", c(0xA6611626), c(0xB3F7DDE0)),
        CardColor("Хвоя", c(0xA61B4A33), c(0xB3DDEFE4)),
        CardColor("Сапфир", c(0xA61B2E5A), c(0xB3DCE6F7)),
        CardColor("Слива", c(0xA63E1E5A), c(0xB3EADDF5)),
        CardColor("Золото", c(0x4DC9A24A), c(0x99F7E7C0)),
        CardColor("Сливочный", c(0x40FADFAF), c(0xB3FFF6E6)),
    )

    /** Градиент выбранного глубокого фона или null (однотонный фон). */
    fun deep(s: LookSettings): List<Color>? = if (s.bgStyle >= 0 && s.bgRgb != 0) DEEP_BACKGROUNDS.getOrNull(s.bgStyle)?.colors else null

    /** Свой цвет плашек или null («по теме»). */
    fun cardColor(s: LookSettings, dark: Boolean): Color? = when {
        s.cardStyle == -2 && s.cardRgb != 0 -> Color(s.cardRgb)
        s.cardStyle >= 0 -> CARDS.getOrNull(s.cardStyle)?.let { if (dark) it.dark else it.light }
        else -> null
    }

    val state = mutableStateOf(LookSettings())
    private var loaded = false

    private fun sp(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    fun load(ctx: Context): LookSettings {
        if (!loaded) {
            val p = sp(ctx)
            state.value = LookSettings(
                accentRgb = p.getInt("look_accent", 0), bgRgb = p.getInt("look_bg", 0), textRgb = p.getInt("look_text", 0),
                // Medeo ставится один раз для всех (потом можно выбрать другой шрифт в «Оформлении»).
                font = if (!p.getBoolean("look_medeo", false)) { p.edit().putBoolean("look_medeo", true).putInt("look_font", 5).apply(); 5 } else p.getInt("look_font", 5), scale = p.getFloat("look_scale", 1f).coerceIn(.8f, 1.5f),
                icons = p.getInt("look_icons", 0), iconRgb = p.getInt("look_icon_rgb", 0), contrast = p.getBoolean("look_contrast", false),
                cardRound = p.getInt("look_round", 18).coerceIn(4, 32),
                bgStyle = p.getInt("look_bg_style", -1).coerceIn(-1, DEEP_BACKGROUNDS.lastIndex),
                cardStyle = p.getInt("look_card_style", -1).coerceIn(-2, CARDS.lastIndex), cardRgb = p.getInt("look_card_rgb", 0),
            )
            loaded = true
        }
        return state.value
    }

    /** Перечитать после загрузки файла оформления или копии. */
    fun reload(ctx: Context) { loaded = false; load(ctx) }

    fun save(ctx: Context, s: LookSettings) {
        state.value = s
        sp(ctx).edit()
            .putInt("look_accent", s.accentRgb).putInt("look_bg", s.bgRgb).putInt("look_text", s.textRgb)
            .putInt("look_font", s.font).putFloat("look_scale", s.scale)
            .putInt("look_icons", s.icons).putInt("look_icon_rgb", s.iconRgb).putBoolean("look_contrast", s.contrast)
            .putInt("look_round", s.cardRound)
            .putInt("look_bg_style", s.bgStyle).putInt("look_card_style", s.cardStyle).putInt("look_card_rgb", s.cardRgb)
            .apply()
    }

    fun color(rgb: Int) = Color(rgb or (0xFF shl 24))

    /** Смешать два цвета: f = доля [b]. */
    fun mix(a: Color, b: Color, f: Float) = Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f)

    fun isDark(c: Color) = c.luminance() < .4f

    /**
     * Цвет, различимый на фоне [bg]: если контраст ниже [min], цвет плавно сдвигается к чёрному или белому
     * (что даёт больший контраст с этим фоном) — оттенок по возможности сохраняется. Полупрозрачный цвет
     * оценивается так, как он ляжет на фон.
     */
    fun readable(fg: Color, bg: Color, min: Float = 4.5f): Color {
        val solid = if (fg.alpha < 1f) mix(bg, fg.copy(alpha = 1f), fg.alpha) else fg
        if (contrast(solid, bg) >= min) return fg
        val toward = if (contrast(Color.Black, bg) >= contrast(Color.White, bg)) Color.Black else Color.White
        var f = 0f
        var c = solid
        while (f < 1f && contrast(c, bg) < min) { f = (f + .05f).coerceAtMost(1f); c = mix(solid, toward, f) }
        return c
    }

    /** Различимый сразу на нескольких фонах (фон экрана и карточек). */
    fun readableOn(fg: Color, bgs: List<Color>, min: Float = 4.5f): Color = bgs.fold(fg) { c, b -> readable(c, b, min) }

    /** Контраст по WCAG: от 1 до 21. */
    fun contrast(a: Color, b: Color): Float {
        val l1 = a.luminance() + .05f
        val l2 = b.luminance() + .05f
        return if (l1 > l2) l1 / l2 else l2 / l1
    }
}

/**
 * Фон, на котором сейчас рисуется текст (экран, карточка, панель). Задаётся через [OnBackground];
 * общий Text по нему подстраивает цвет текста, чтобы он не сливался с фоном.
 */
val LocalBg = androidx.compose.runtime.compositionLocalOf { Color.Unspecified }

/**
 * Всё внутри рисуется на фоне [bg]: цвет текста по умолчанию становится различимым на нём (контраст не ниже 4,5:1).
 * Полупрозрачный фон считается так, как он ложится на фон снаружи.
 */
@androidx.compose.runtime.Composable
fun OnBackground(bg: Color, content: @androidx.compose.runtime.Composable () -> Unit) {
    val parent = LocalBg.current
    val solid = when {
        bg == Color.Unspecified -> parent
        bg.alpha < 1f && parent != Color.Unspecified -> Look.mix(parent, bg.copy(alpha = 1f), bg.alpha)
        else -> bg.copy(alpha = 1f)
    }
    if (solid == Color.Unspecified) { content(); return }
    val text = androidx.compose.material3.LocalContentColor.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalBg provides solid,
        androidx.compose.material3.LocalContentColor provides Look.readable(text, solid, 4.5f),
        content = content,
    )
}
