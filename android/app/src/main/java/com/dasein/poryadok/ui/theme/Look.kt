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
    /** Стиль фона: номер в [Look.BG_FX], -1 — как в теме (бархат у глубоких цветов, однотонный у остальных). */
    val bgFx: Int = -1,
    /** Стиль плашек: номер в [Look.CARD_FX], -1 — как в теме. */
    val cardFx: Int = -1,
    /** Сочетание цветов иконок (режим 4): номер в [Look.DEEP_BACKGROUNDS], -1 — подбирается к фону и плашкам. */
    val iconPalette: Int = -1,
)

/**
 * Сочетание цветов для иконок, подобранное к фону: яркие детали, блики, светлые формы, полутона, тёмные контуры.
 * Цвета — RGB без прозрачности.
 */
data class IconPalette(val name: String, val accent: Int, val highlight: Int, val light: Int, val mid: Int, val deep: Int)

/** Глубокий фон: бархатный градиент — центр, середина, края. */
class DeepBg(val name: String, val colors: List<Color>, val icons: IconPalette)

/** Цвет плашек для тёмной и светлой темы (полупрозрачный — сквозь него виден фон). */
class CardColor(val name: String, val dark: Color, val light: Color, val palette: String? = null)

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
        "Сочетание к фону — подобранные цвета",
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

    /** Палитра глубоких цветов для фона: градиент, светлее к центру и глубже к краям, и подобранные к нему цвета иконок. */
    val DEEP_BACKGROUNDS = listOf(
        DeepBg("Изумруд", listOf(c(0xFF1F5C46), c(0xFF123829), c(0xFF081C14)), IconPalette("Изумруд", 0xD9A441, 0xF2D58A, 0xF3EBD8, 0x5FAF8A, 0x1E4D3A)),
        DeepBg("Малахит", listOf(c(0xFF0E6B4B), c(0xFF07442F), c(0xFF032116)), IconPalette("Малахит", 0xE6C15A, 0xF6E3A2, 0xE6F6EE, 0x3FBF8A, 0x0B4A33)),
        DeepBg("Хвоя", listOf(c(0xFF2E4A22), c(0xFF1C2F15), c(0xFF0D170A)), IconPalette("Хвоя", 0xE0A84A, 0xF3D99A, 0xEEF2E2, 0x8DB36A, 0x2B4420)),
        DeepBg("Оливка", listOf(c(0xFF5A5A24), c(0xFF393915), c(0xFF1B1B09)), IconPalette("Оливка", 0xE9B44C, 0xF6DC9C, 0xF2F1DF, 0xA7A85C, 0x3A3A16)),
        DeepBg("Бирюза", listOf(c(0xFF0F6E6A), c(0xFF0A4644), c(0xFF042120)), IconPalette("Бирюза", 0xFF9E7A, 0xFFD2C0, 0xE3F7F5, 0x52C2B6, 0x0C4A46)),
        DeepBg("Океан", listOf(c(0xFF145A66), c(0xFF0C3840), c(0xFF051B20)), IconPalette("Океан", 0xFF8A65, 0xFFD3B8, 0xE6F6F7, 0x4FB3BF, 0x0F4750)),
        DeepBg("Кобальт", listOf(c(0xFF1A4FA0), c(0xFF0F3166), c(0xFF061731)), IconPalette("Кобальт", 0xFFC857, 0xFFE6A6, 0xE8F0FC, 0x5E95E8, 0x12356E)),
        DeepBg("Сапфир", listOf(c(0xFF234A86), c(0xFF142B55), c(0xFF09152C)), IconPalette("Сапфир", 0xF0B45A, 0xFFE1A8, 0xEAF1FB, 0x6E9BD8, 0x1E355F)),
        DeepBg("Полночь", listOf(c(0xFF1E2A4A), c(0xFF111A30), c(0xFF070B16)), IconPalette("Полночь", 0x7FD1FF, 0xC9ECFF, 0xE8EDF8, 0x7B8FC7, 0x1B2547)),
        DeepBg("Индиго", listOf(c(0xFF35307A), c(0xFF1F1C4E), c(0xFF0E0C26)), IconPalette("Индиго", 0xFFB86B, 0xFFDDB0, 0xECEAFB, 0x8C86E0, 0x24205A)),
        DeepBg("Космос", listOf(c(0xFF2A1F4F), c(0xFF161030), c(0xFF080616)), IconPalette("Космос", 0x6FE3D6, 0xC3F5EF, 0xECE8FA, 0x9C84E8, 0x251A50)),
        DeepBg("Аметист", listOf(c(0xFF4C2A78), c(0xFF2E1A4C), c(0xFF160C26)), IconPalette("Аметист", 0xE6B85C, 0xF7E2A6, 0xF1E9FA, 0xA88AD6, 0x3A2560)),
        DeepBg("Сумерки", listOf(c(0xFF6A2E6A), c(0xFF431C44), c(0xFF200C21)), IconPalette("Сумерки", 0xF2A65E, 0xF9D3A8, 0xF6E8F6, 0xC27DC2, 0x481E4A)),
        DeepBg("Вино", listOf(c(0xFF6E1A2C), c(0xFF46101D), c(0xFF22070E)), IconPalette("Вино", 0xE8B04B, 0xF6DDA0, 0xF7E6E1, 0xC9707E, 0x5A1A28)),
        DeepBg("Рубин", listOf(c(0xFF8A1C3A), c(0xFF5A0F25), c(0xFF2B0611)), IconPalette("Рубин", 0xF2B94E, 0xFADFA0, 0xFBE9EC, 0xD9707F, 0x5C1428)),
        DeepBg("Терракота", listOf(c(0xFF8A4126), c(0xFF5A2817), c(0xFF2A120A)), IconPalette("Терракота", 0xF2B35A, 0xFAD9A4, 0xF8EADF, 0x6FA89A, 0x4A2416)),
        DeepBg("Бронза", listOf(c(0xFF6A4A1C), c(0xFF433010), c(0xFF201607)), IconPalette("Бронза", 0xF0C35A, 0xFBE6A8, 0xF7EDDC, 0xC08A4A, 0x4A3215)),
        DeepBg("Шоколад", listOf(c(0xFF5A3B28), c(0xFF382418), c(0xFF1C110B)), IconPalette("Шоколад", 0xE79A5C, 0xF6D2AE, 0xF6EBDF, 0xB07D5A, 0x3F281A)),
        DeepBg("Гранит", listOf(c(0xFF4A4440), c(0xFF2E2A27), c(0xFF161412)), IconPalette("Гранит", 0xE0A35A, 0xF2D1A4, 0xEFEBE7, 0xA39A92, 0x3A3430)),
        DeepBg("Графит", listOf(c(0xFF3A3F47), c(0xFF23272D), c(0xFF111316)), IconPalette("Графит", 0xF2C14E, 0xFBE3A0, 0xEDEFF2, 0x9AA3AF, 0x2E3239)),
        DeepBg("Жемчуг", listOf(c(0xFFFFFFFF), c(0xFFF2EFEA), c(0xFFE2DDD4)), IconPalette("Жемчуг", 0xC9853A, 0xE7B877, 0xFFFFFF, 0x8C7B66, 0x3B342C)),
        DeepBg("Ваниль", listOf(c(0xFFFFFBEF), c(0xFFF7EDCF), c(0xFFEBDCAE)), IconPalette("Ваниль", 0xC77A2E, 0xE9B36F, 0xFFFFFF, 0x9A8452, 0x3E3218)),
        DeepBg("Персик", listOf(c(0xFFFFF4EC), c(0xFFF8E2D2), c(0xFFEDCDB6)), IconPalette("Персик", 0xE0675A, 0xF4AE9C, 0xFFFFFF, 0xC08A6A, 0x4A2C22)),
        DeepBg("Пудра", listOf(c(0xFFFFF5F6), c(0xFFF7DFE3), c(0xFFEDC6CD)), IconPalette("Пудра", 0xC9505F, 0xEBA0A9, 0xFFFFFF, 0xB27A86, 0x4A2430)),
        DeepBg("Лаванда", listOf(c(0xFFF6F2FC), c(0xFFE9E1F5), c(0xFFD6CAEA)), IconPalette("Лаванда", 0xD9707E, 0xF0B0BA, 0xFFFFFF, 0x8E79B8, 0x3B2E5A)),
        DeepBg("Небо", listOf(c(0xFFF3F8FF), c(0xFFDCE9FA), c(0xFFC1D7F2)), IconPalette("Небо", 0xE8A33D, 0xF5CF8C, 0xFFFFFF, 0x5E8FD0, 0x1C3560)),
        DeepBg("Мята", listOf(c(0xFFF3FBF7), c(0xFFDDF2E8), c(0xFFC2E6D5)), IconPalette("Мята", 0xE07A5F, 0xF2B8A4, 0xFFFFFF, 0x5FA889, 0x1F4636)),
        DeepBg("Шалфей", listOf(c(0xFFF2F8F3), c(0xFFDFEDE3), c(0xFFC8DECF)), IconPalette("Шалфей", 0xD98A4E, 0xF0C08F, 0xFFFFFF, 0x6E9B7E, 0x24402F)),
    )

    /** Цвета плашек: полупрозрачные и плотные, для тёмной и светлой темы; [CardColor.palette] — сочетание для иконок. */
    val CARDS = listOf(
        CardColor("Матовое стекло", c(0x8C2E323C), c(0xA6FFFFFF)),
        CardColor("Дымка", c(0x26FFFFFF), c(0x12000000)),
        CardColor("Плотные", c(0xF21C1C1E), c(0xF7FFFFFF)),
        CardColor("Графит", c(0xB32A2D33), c(0xCCEFF0F2), "Графит"),
        CardColor("Ночь", c(0xB30C1020), c(0xCCF0F3FA), "Полночь"),
        CardColor("Бордо", c(0xA6611626), c(0xB3F7DDE0), "Вино"),
        CardColor("Рубин", c(0xA67A1530), c(0xB3FBDCE3), "Рубин"),
        CardColor("Роза", c(0x4DE08A9A), c(0xB3FBE3E8), "Пудра"),
        CardColor("Терракота", c(0xA6693220), c(0xB3F6E1D6), "Терракота"),
        CardColor("Хвоя", c(0xA61B4A33), c(0xB3DDEFE4), "Хвоя"),
        CardColor("Малахит", c(0xA60B4A33), c(0xB3D6F2E6), "Малахит"),
        CardColor("Бирюза", c(0xA60E4E4A), c(0xB3D9F1EE), "Бирюза"),
        CardColor("Сапфир", c(0xA61B2E5A), c(0xB3DCE6F7), "Сапфир"),
        CardColor("Индиго", c(0xA62A2560), c(0xB3E3E1F7), "Индиго"),
        CardColor("Слива", c(0xA63E1E5A), c(0xB3EADDF5), "Аметист"),
        CardColor("Золото", c(0x4DC9A24A), c(0x99F7E7C0), "Бронза"),
        CardColor("Шампань", c(0x40F2D9A8), c(0xB3FBF1DC), "Ваниль"),
        CardColor("Сливочный", c(0x40FADFAF), c(0xB3FFF6E6), "Персик"),
    )

    /** Стили фона — популярные приёмы: бархат, диагональный градиент, аврора, меш-градиент, прожектор, зерно, боке. */
    val BG_FX = listOf("Бархат", "Диагональ", "Аврора", "Меш", "Прожектор", "Зерно", "Боке")

    /** Стили плашек: сплошные, стекло (glassmorphism), градиент, контур, неоновая рамка, объём. */
    val CARD_FX = listOf("Сплошные", "Стекло", "Градиент", "Контур", "Неон", "Объём")

    /** Градиент выбранного глубокого фона или null (однотонный фон). */
    fun deep(s: LookSettings): List<Color>? = if (s.bgStyle >= 0 && s.bgRgb != 0) DEEP_BACKGROUNDS.getOrNull(s.bgStyle)?.colors else null

    /**
     * Градиент фона под экраном: глубокий цвет, или — если выбран стиль фона — три тона из однотонного фона [bg].
     * null — фон однотонный, отдельный слой не нужен.
     */
    fun gradient(s: LookSettings, bg: Color): List<Color>? {
        deep(s)?.let { return it }
        if (s.bgFx < 0 || bg == Color.Unspecified) return null
        val dark = isDark(bg)
        return listOf(mix(bg, Color.White, if (dark) .14f else .5f), bg, mix(bg, Color.Black, if (dark) .45f else .12f))
    }

    /** Стиль фона с учётом «как в теме»: у градиента по умолчанию — бархат. */
    fun fx(s: LookSettings) = if (s.bgFx in BG_FX.indices) s.bgFx else 0

    private fun palette(name: String) = DEEP_BACKGROUNDS.first { it.name == name }.icons

    /**
     * Сочетание цветов иконок: выбранное вручную, иначе подобранное к глубокому фону, к цвету плашек
     * или — для однотонного фона — от ближайшего по цвету глубокого фона.
     */
    fun paletteFor(s: LookSettings, bg: Color): IconPalette {
        DEEP_BACKGROUNDS.getOrNull(s.iconPalette)?.let { return it.icons }
        if (deep(s) != null) return DEEP_BACKGROUNDS[s.bgStyle].icons
        CARDS.getOrNull(s.cardStyle)?.palette?.let { return palette(it) }
        fun d(a: Color, b: Color) = (a.red - b.red).let { it * it } + (a.green - b.green).let { it * it } + (a.blue - b.blue).let { it * it }
        return DEEP_BACKGROUNDS.minBy { d(it.colors[1], bg) }.icons
    }

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
                bgFx = p.getInt("look_bg_fx", -1).coerceIn(-1, BG_FX.lastIndex), cardFx = p.getInt("look_card_fx", -1).coerceIn(-1, CARD_FX.lastIndex),
                iconPalette = p.getInt("look_icon_palette", -1).coerceIn(-1, DEEP_BACKGROUNDS.lastIndex),
            ).let { if (it.icons > ICON_MODES.lastIndex) it.copy(icons = 0) else it }.let { migrate(p, it) }
            loaded = true
        }
        return state.value
    }

    /** Палитры расширились и поменяли порядок: номера, сохранённые версией 1.0.283, переводятся по названию. */
    private fun migrate(p: android.content.SharedPreferences, s: LookSettings): LookSettings {
        if (p.getBoolean("look_palette_v2", false)) return s
        val oldBg = listOf("Изумруд", "Вино", "Сапфир", "Аметист", "Океан", "Полночь", "Хвоя", "Бронза", "Шоколад", "Графит", "Жемчуг", "Лаванда", "Шалфей", "Персик")
        val oldCard = listOf("Матовое стекло", "Дымка", "Плотные", "Бордо", "Хвоя", "Сапфир", "Слива", "Золото", "Сливочный")
        val bg = oldBg.getOrNull(s.bgStyle)?.let { n -> DEEP_BACKGROUNDS.indexOfFirst { it.name == n } } ?: -1
        val card = if (s.cardStyle >= 0) oldCard.getOrNull(s.cardStyle)?.let { n -> CARDS.indexOfFirst { it.name == n } } ?: -1 else s.cardStyle
        val r = s.copy(bgStyle = bg, cardStyle = card)
        p.edit().putBoolean("look_palette_v2", true).putInt("look_bg_style", r.bgStyle).putInt("look_card_style", r.cardStyle).apply()
        return r
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
            .putInt("look_bg_fx", s.bgFx).putInt("look_card_fx", s.cardFx).putInt("look_icon_palette", s.iconPalette)
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

/** Сочетание цветов иконок для текущего оформления. */
@androidx.compose.runtime.Composable
fun currentPalette(): IconPalette {
    val look = LocalLook.current
    val bg = androidx.compose.material3.MaterialTheme.colorScheme.background
    return androidx.compose.runtime.remember(look, bg) { Look.paletteFor(look, bg) }
}

/** Сохранённое оформление: название, все настройки «Оформления», номер акцента и тема. */
data class LookPreset(val name: String, val look: LookSettings, val accent: Int, val theme: String)

/** Любимые оформления — быстро переключаться между ними. Хранятся в ui_state (попадают в файл оформления и копию). */
object LookPresets {
    private const val KEY = "look_presets"
    val list = mutableStateOf<List<LookPreset>>(emptyList())

    private fun sp(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        list.value = runCatching {
            val a = org.json.JSONArray(sp(ctx).getString(KEY, "[]"))
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun store(ctx: Context, l: List<LookPreset>) {
        list.value = l
        val a = org.json.JSONArray()
        l.forEach { a.put(toJson(it)) }
        sp(ctx).edit().putString(KEY, a.toString()).apply()
    }

    fun add(ctx: Context, p: LookPreset) = store(ctx, list.value + p)
    fun replace(ctx: Context, i: Int, p: LookPreset) = store(ctx, list.value.toMutableList().also { if (i in it.indices) it[i] = p })
    fun remove(ctx: Context, i: Int) = store(ctx, list.value.filterIndexed { j, _ -> j != i })

    private fun toJson(p: LookPreset) = org.json.JSONObject().apply {
        val s = p.look
        put("name", p.name); put("accent", p.accent); put("theme", p.theme)
        put("accentRgb", s.accentRgb); put("bgRgb", s.bgRgb); put("textRgb", s.textRgb); put("font", s.font); put("scale", s.scale.toDouble())
        put("icons", s.icons); put("iconRgb", s.iconRgb); put("contrast", s.contrast); put("cardRound", s.cardRound)
        put("bgStyle", s.bgStyle); put("cardStyle", s.cardStyle); put("cardRgb", s.cardRgb); put("bgFx", s.bgFx); put("cardFx", s.cardFx); put("iconPalette", s.iconPalette)
    }

    private fun fromJson(o: org.json.JSONObject): LookPreset {
        val d = LookSettings()
        val s = LookSettings(
            accentRgb = o.optInt("accentRgb", 0), bgRgb = o.optInt("bgRgb", 0), textRgb = o.optInt("textRgb", 0), font = o.optInt("font", 5),
            scale = o.optDouble("scale", 1.0).toFloat().coerceIn(.8f, 1.5f), icons = o.optInt("icons", 0).coerceIn(0, Look.ICON_MODES.lastIndex),
            iconRgb = o.optInt("iconRgb", 0), contrast = o.optBoolean("contrast", false), cardRound = o.optInt("cardRound", d.cardRound).coerceIn(4, 32),
            bgStyle = o.optInt("bgStyle", -1).coerceIn(-1, Look.DEEP_BACKGROUNDS.lastIndex), cardStyle = o.optInt("cardStyle", -1).coerceIn(-2, Look.CARDS.lastIndex),
            cardRgb = o.optInt("cardRgb", 0), bgFx = o.optInt("bgFx", -1).coerceIn(-1, Look.BG_FX.lastIndex),
            cardFx = o.optInt("cardFx", -1).coerceIn(-1, Look.CARD_FX.lastIndex), iconPalette = o.optInt("iconPalette", -1).coerceIn(-1, Look.DEEP_BACKGROUNDS.lastIndex),
        )
        return LookPreset(o.optString("name", "Оформление"), s, o.optInt("accent", 0), o.optString("theme", "system"))
    }
}
