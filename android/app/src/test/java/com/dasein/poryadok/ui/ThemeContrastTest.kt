package com.dasein.poryadok.ui

import androidx.compose.ui.graphics.Color
import com.dasein.poryadok.ui.theme.DarkExtra
import com.dasein.poryadok.ui.theme.LightExtra
import com.dasein.poryadok.ui.theme.Palette
import com.dasein.poryadok.ui.theme.lightPrimary
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Текст должен читаться на любом фоне темы: контраст не ниже 4,5:1 (WCAG AA для обычного текста). */
class ThemeContrastTest {
    private fun lin(c: Float) = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun lum(c: Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    private fun ratio(a: Color, b: Color): Double {
        val x = lum(a); val y = lum(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }
    private fun check(name: String, fg: Color, bg: Color, min: Double = 4.5) {
        val r = ratio(fg, bg)
        assertTrue("$name: контраст ${"%.2f".format(r)} < $min", r >= min)
    }

    @Test fun lightThemeText() {
        val bgs = listOf("фон" to Color(0xFFF7F3EB), "карточка" to LightExtra.card, "плашка" to LightExtra.cardHigh)
        bgs.forEach { (n, bg) ->
            check("бледный текст на $n", LightExtra.dim, bg)
            check("зелёный на $n", LightExtra.ok, bg)
            check("красный на $n", LightExtra.danger, bg)
            check("предупреждение на $n", LightExtra.warn, bg)
            Palette.accents.forEach { (an, acc) -> check("акцент «$an» на $n", lightPrimary(acc), bg) }
        }
    }

    @Test fun darkThemeText() {
        // Приподнятые плашки в тёмной теме — для крупных подписей и иконок: там хватает 3,5:1.
        listOf(Triple("фон", Palette.Ink, 4.5), Triple("карточка", DarkExtra.card, 4.5), Triple("плашка", DarkExtra.cardHigh, 3.5)).forEach { (n, bg, min) ->
            check("бледный текст на $n", DarkExtra.dim, bg)
            check("зелёный на $n", DarkExtra.ok, bg, min)
            check("красный на $n", DarkExtra.danger, bg, min)
            check("предупреждение на $n", DarkExtra.warn, bg, min)
            Palette.accents.forEach { (an, acc) -> check("акцент «$an» на $n", acc, bg, min) }
        }
    }
}
