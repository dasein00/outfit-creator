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
import androidx.compose.ui.unit.sp

object Palette {
    val Ink = Color(0xFF211D18)
    val Ink2 = Color(0xFF2B261F)
    val Ink3 = Color(0xFF3A342B)
    val Stage = Color(0xFFEDE9DF)
    val Stage2 = Color(0xFFDCD6C7)
    val Brass = Color(0xFFC79246)
    val Text = Color(0xFFF0ECE3)
    val Dim = Color(0xFFA79E90)
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

val LocalExtra = staticCompositionLocalOf {
    Extra(Palette.Ok, Palette.Brass, Palette.Danger, Palette.Dim, Palette.Ink2, Palette.Ink3, Color(0x24F6F3EC), true)
}

val Serif = FontFamily.Serif

private val typography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = Serif),
        headlineLarge = t.headlineLarge.copy(fontFamily = Serif),
        headlineMedium = t.headlineMedium.copy(fontFamily = Serif),
        headlineSmall = t.headlineSmall.copy(fontFamily = Serif),
        titleLarge = TextStyle(fontFamily = Serif, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
    )
}

@Composable
fun PoryadokTheme(theme: String, accent: Int, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val acc = Palette.accents.getOrNull(accent)?.second ?: Palette.Brass
    val scheme = if (dark) darkColorScheme(
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
    ) else lightColorScheme(
        primary = acc.darken(), onPrimary = Color.White,
        primaryContainer = acc.copy(alpha = .22f), onPrimaryContainer = Palette.Ink,
        secondary = Color(0xFF5E8A4F), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6E0D3), onSecondaryContainer = Palette.Ink,
        tertiary = Color(0xFF3F7AA3),
        background = Color(0xFFF7F3EB), onBackground = Palette.Ink,
        surface = Color(0xFFF7F3EB), onSurface = Palette.Ink,
        surfaceVariant = Color(0xFFEDE7DB), onSurfaceVariant = Color(0xFF6C6355),
        surfaceContainer = Color(0xFFFFFCF6), surfaceContainerHigh = Color(0xFFEDE7DB),
        surfaceContainerLow = Color(0xFFFBF8F2), surfaceContainerLowest = Color.White,
        surfaceContainerHighest = Color(0xFFE3DCCD),
        outline = Color(0x40211D18), outlineVariant = Color(0x1F211D18),
        error = Color(0xFFA2432E), onError = Color.White,
    )
    val extra = if (dark) Extra(Palette.Ok, Palette.Brass, Palette.Danger, Palette.Dim, Palette.Ink2, Palette.Ink3, Color(0x24F6F3EC), true)
    else Extra(Color(0xFF4F7F40), Color(0xFFA8742E), Color(0xFFA2432E), Color(0xFF6C6355), Color(0xFFFFFCF6), Color(0xFFEDE7DB), Color(0x1F211D18), false)
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

private fun Color.darken(f: Float = .78f) = Color(red * f, green * f, blue * f, alpha)
