package com.dasein.poryadok.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object Palette {
    val Ink = Color(0xFF211D18)
    val Ink2 = Color(0xFF2B261F)
    val Ink3 = Color(0xFF3A342B)
    val Stage = Color(0xFFEDE9DF)
    val Stage2 = Color(0xFFDCD6C7)
    val Brass = Color(0xFFC79246)
    val Text = Color(0xFFF0ECE3)
    val Dim = Color(0xFFC6BDAF)
    val Danger = Color(0xFFB4553F)
    val Ok = Color(0xFF8AB07A)

    /** Цвета для проектов, привычек, категорий и счетов. */
    val items = listOf(
        Color(0xFFC79246), Color(0xFF8AB07A), Color(0xFF6FA3C7), Color(0xFFC77A8E), Color(0xFF9C86C9),
        Color(0xFFD08C5B), Color(0xFF5FB3A3), Color(0xFFB9A24A), Color(0xFF8A8F99), Color(0xFFB4553F),
        Color(0xFF4F8A6B), Color(0xFF7A6FC7),
    )

    fun item(i: Int): Color = items[((i % items.size) + items.size) % items.size]

    val accents = listOf(
        "Латунь" to Color(0xFFC79246),
        "Шалфей" to Color(0xFF8AB07A),
        "Небо" to Color(0xFF6FA3C7),
        "Роза" to Color(0xFFC77A8E),
        "Лаванда" to Color(0xFF9C86C9),
        "Терракота" to Color(0xFFDB7D5B),
        "Мята" to Color(0xFF5FB3A3),
        "Индиго" to Color(0xFF8C95E8),
        "Янтарь" to Color(0xFFD69C22),
        "Бирюза" to Color(0xFF2FA9C2),
        "Вишня" to Color(0xFFE0687C),
        "Оливка" to Color(0xFF9AA14A),
        "Коралл" to Color(0xFFF07B6B),
        "Сирень" to Color(0xFFB57BD1),
        "Графит" to Color(0xFF9AA0A8),
        "Изумруд" to Color(0xFF3FA06A),
    )

    /** Цвета заметок в духе Google Keep, приглушённые под тему. */
    val notes = listOf(
        Color.Transparent, Color(0xFF5C4A2E), Color(0xFF3E5236), Color(0xFF2F4A5C), Color(0xFF5C2F3E), Color(0xFF45385C),
    )
    val notesLight = listOf(
        Color.Transparent, Color(0xFFF6E7C1), Color(0xFFDDEBCF), Color(0xFFD3E6F2), Color(0xFFF5D5DE), Color(0xFFE3DAF2),
    )
}

data class Extra(
    val ok: Color,
    val warn: Color,
    val danger: Color,
    val dim: Color,
    val card: Color,
    val cardHigh: Color,
    val line: Color,
    val dark: Boolean,
)

/** Дополнительные цвета тем. Пары «текст — фон» держат контраст не ниже 4,5:1 (WCAG AA), см. ThemeContrastTest. */
val DarkExtra = Extra(Palette.Ok, Palette.Brass, Color(0xFFD9785F), Palette.Dim, Palette.Ink2, Palette.Ink3, Color(0x24F6F3EC), true)
val LightExtra = Extra(Color(0xFF3B6A2E), Color(0xFF8A5A1C), Color(0xFF9A3A25), Color(0xFF4A4237), Color(0xFFFFFCF6), Color(0xFFEDE7DB), Color(0x1F211D18), false)

/** Основной цвет светлой темы: акцент, затемнённый так, чтобы текст им читался на светлом фоне. */
fun lightPrimary(acc: Color): Color = acc.darken(.6f)

val LocalExtra = staticCompositionLocalOf { DarkExtra }

val Serif = FontFamily.Serif

/** Шрифты по выбору в «Оформлении»: заголовки и основной текст. */
private fun typographyFor(font: Int): Typography {
    val (head, body) = when (font) {
        1 -> FontFamily.SansSerif to FontFamily.SansSerif
        2 -> Serif to Serif
        3 -> FontFamily.Monospace to FontFamily.Monospace
        4 -> FontFamily.Cursive to FontFamily.SansSerif
        else -> Serif to FontFamily.Default
    }
    val t = Typography()
    fun TextStyle.h() = copy(fontFamily = head)
    // Высота строки — в долях кегля (em): текст с любым fontSize получает ровный интерлиньяж,
    // а не 24 sp на мелкой подписи. Кириллице не нужна разрядка, поэтому трекинг почти нулевой.
    fun b(size: Int, line: Double, weight: FontWeight = FontWeight.Normal, track: Double = 0.0) = TextStyle(
        fontFamily = body, fontSize = size.sp, lineHeight = line.em, fontWeight = weight, letterSpacing = track.sp,
    )
    return t.copy(
        displayLarge = t.displayLarge.h(), displayMedium = t.displayMedium.h(), displaySmall = t.displaySmall.h(),
        headlineLarge = t.headlineLarge.h(), headlineMedium = t.headlineMedium.h(), headlineSmall = t.headlineSmall.h(),
        titleLarge = TextStyle(fontFamily = head, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        titleMedium = b(16, 1.4, FontWeight.SemiBold),
        titleSmall = b(14, 1.4, FontWeight.SemiBold),
        bodyLarge = b(16, 1.45),
        bodyMedium = b(14, 1.45),
        bodySmall = b(12, 1.4),
        labelLarge = b(14, 1.3, FontWeight.SemiBold, .1),
        labelMedium = b(12, 1.3, FontWeight.SemiBold, .2),
        labelSmall = b(11, 1.3, FontWeight.Medium, .2),
    )
}

/** Текущие свои настройки оформления (скругление карточек и т.п.). */
val LocalLook = staticCompositionLocalOf { LookSettings() }

@Composable
fun PoryadokTheme(theme: String, accent: Int, content: @Composable () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.remember { Look.load(ctx) }
    val look = Look.state.value
    val systemDark = isSystemInDarkTheme()
    // Свой фон задаёт и светлоту темы: тёмный фон — тёмная тема.
    val dark = if (look.bgRgb != 0) Look.isDark(Look.color(look.bgRgb)) else when (theme) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val acc = if (look.accentRgb != 0) Look.color(look.accentRgb) else Palette.accents.getOrNull(accent)?.second ?: Palette.Brass
    val base = if (dark) baseDark(acc) else baseLight(acc)
    val bg = if (look.bgRgb != 0) Look.color(look.bgRgb) else base.background
    val text = when {
        look.textRgb != 0 -> Look.color(look.textRgb)
        look.contrast -> if (dark) Color.White else Color.Black
        else -> base.onBackground
    }
    val custom = look.bgRgb != 0 || look.textRgb != 0 || look.contrast
    val scheme = if (!custom) base else {
        val card = Look.mix(bg, text, if (dark) .07f else .03f).let { if (!dark && look.bgRgb != 0) Look.mix(bg, Color.White, .6f) else it }
        val cardHigh = Look.mix(bg, text, if (dark) .13f else .08f)
        val dim = Look.mix(text, bg, if (look.contrast) .12f else .3f)
        base.copy(
            background = bg, onBackground = text, surface = bg, onSurface = text,
            surfaceVariant = card, onSurfaceVariant = dim,
            surfaceContainer = card, surfaceContainerHigh = cardHigh, surfaceContainerLow = Look.mix(bg, text, .04f),
            surfaceContainerLowest = bg, surfaceContainerHighest = Look.mix(bg, text, .18f),
            outline = text.copy(alpha = if (look.contrast) .6f else .25f), outlineVariant = text.copy(alpha = if (look.contrast) .35f else .14f),
            onPrimaryContainer = text, secondaryContainer = cardHigh, onSecondaryContainer = text,
        )
    }
    val extra = if (!custom) (if (dark) DarkExtra else LightExtra) else {
        val e = if (dark) DarkExtra else LightExtra
        e.copy(dim = scheme.onSurfaceVariant, card = scheme.surfaceContainer, cardHigh = scheme.surfaceContainerHigh, line = scheme.outlineVariant)
    }
    val typography = androidx.compose.runtime.remember(look.font) { typographyFor(look.font) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalExtra provides extra,
        LocalLook provides look,
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale * look.scale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

private fun baseDark(acc: Color) = darkColorScheme(
    primary = acc, onPrimary = Color(0xFF241C0D),
    primaryContainer = acc.copy(alpha = .25f), onPrimaryContainer = Palette.Text,
    secondary = Palette.Ok, onSecondary = Color(0xFF1B2A16),
    secondaryContainer = Palette.Ink3, onSecondaryContainer = Palette.Text,
    tertiary = Color(0xFF6FA3C7),
    background = Palette.Ink, onBackground = Palette.Text,
    surface = Palette.Ink, onSurface = Palette.Text,
    surfaceVariant = Palette.Ink2, onSurfaceVariant = Palette.Dim,
    surfaceContainer = Palette.Ink2, surfaceContainerHigh = Palette.Ink3,
    surfaceContainerLow = Color(0xFF26221C), surfaceContainerLowest = Palette.Ink,
    surfaceContainerHighest = Color(0xFF443D33),
    outline = Color(0x40F6F3EC), outlineVariant = Color(0x24F6F3EC),
    error = Palette.Danger, onError = Color.White,
)

private fun baseLight(acc: Color) = lightColorScheme(
    primary = lightPrimary(acc), onPrimary = Color.White,
    primaryContainer = acc.copy(alpha = .22f), onPrimaryContainer = Palette.Ink,
    secondary = Color(0xFF5E8A4F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E0D3), onSecondaryContainer = Palette.Ink,
    tertiary = Color(0xFF3F7AA3),
    background = Color(0xFFF7F3EB), onBackground = Palette.Ink,
    surface = Color(0xFFF7F3EB), onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFEDE7DB), onSurfaceVariant = Color(0xFF4A4237),
    surfaceContainer = Color(0xFFFFFCF6), surfaceContainerHigh = Color(0xFFEDE7DB),
    surfaceContainerLow = Color(0xFFFBF8F2), surfaceContainerLowest = Color.White,
    surfaceContainerHighest = Color(0xFFE3DCCD),
    outline = Color(0x40211D18), outlineVariant = Color(0x1F211D18),
    error = Color(0xFFA2432E), onError = Color.White,
)

internal fun Color.darken(f: Float = .78f) = Color(red * f, green * f, blue * f, alpha)
