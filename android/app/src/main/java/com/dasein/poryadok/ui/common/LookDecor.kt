package com.dasein.poryadok.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.dasein.poryadok.ui.theme.IconPalette
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.LocalLook
import com.dasein.poryadok.ui.theme.Look
import kotlin.random.Random

private fun rgb(v: Int) = Color(v or (0xFF shl 24))

/** Мягкое цветное пятно: радиальный градиент, растворяющийся к краю. */
private fun DrawScope.blob(c: Color, center: Offset, r: Float, a: Float) =
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = 0f)), center, r), r, center)

/** Точки «зерна» и кружки боке — в долях экрана, одни и те же при каждом рисовании. */
private val GRAIN = Random(7).let { r -> List(1600) { Triple(r.nextFloat(), r.nextFloat(), r.nextBoolean()) } }
private val BOKEH = Random(11).let { r -> List(14) { floatArrayOf(r.nextFloat(), r.nextFloat(), .04f + r.nextFloat() * .09f, .3f + r.nextFloat() * .7f) } }

/**
 * Фон в выбранном стиле. [v] — центр, середина, края; [pal] — сочетание цветов (для авроры, меша и боке).
 * Стили — популярные приёмы современных интерфейсов: бархат, диагональный градиент, аврора, меш-градиент,
 * прожектор с виньеткой, зерно (шум), боке.
 */
fun DrawScope.fancyBackground(v: List<Color>, fx: Int, pal: IconPalette, dark: Boolean) {
    val w = size.width; val h = size.height; val m = maxOf(w, h)
    val velvet = { drawRect(Brush.radialGradient(v, Offset(w * .5f, h * .3f), m * .9f)) }
    when (fx) {
        1 -> drawRect(Brush.linearGradient(listOf(v[0], v[1], v[2]), Offset.Zero, Offset(w, h)))
        2 -> {
            drawRect(v[2])
            val a = if (dark) .42f else .5f
            blob(rgb(pal.mid), Offset(w * .12f, h * .15f), m * .65f, a)
            blob(rgb(pal.accent), Offset(w * .95f, h * .32f), m * .5f, a * .55f)
            blob(v[0], Offset(w * .55f, h * .62f), m * .6f, .85f)
            blob(rgb(pal.highlight), Offset(w * .2f, h * .92f), m * .45f, a * .4f)
        }
        3 -> {
            drawRect(v[1])
            blob(v[0], Offset(0f, 0f), m * .85f, 1f)
            blob(rgb(pal.mid), Offset(w, h * .45f), m * .7f, if (dark) .35f else .45f)
            blob(v[2], Offset(w, 0f), m * .6f, .9f)
            blob(rgb(pal.accent), Offset(0f, h), m * .6f, if (dark) .22f else .3f)
            blob(v[0], Offset(w * .6f, h), m * .5f, .7f)
        }
        4 -> {
            drawRect(v[2])
            drawRect(Brush.radialGradient(listOf(v[0], v[1], v[2]), Offset(w * .5f, -h * .05f), m * .85f))
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) .35f else .08f)), Offset(w * .5f, h * .45f), m * .8f))
        }
        5 -> {
            velvet()
            val light = Color.White.copy(alpha = if (dark) .07f else .25f); val shade = Color.Black.copy(alpha = if (dark) .12f else .05f)
            GRAIN.forEach { (x, y, l) -> drawRect(if (l) light else shade, Offset(x * w, y * h), androidx.compose.ui.geometry.Size(1.6f, 1.6f)) }
        }
        6 -> {
            velvet()
            BOKEH.forEachIndexed { i, b ->
                val c = rgb(if (i % 3 == 0) pal.accent else if (i % 3 == 1) pal.highlight else pal.mid)
                blob(c, Offset(b[0] * w, b[1] * h), b[2] * m, (if (dark) .22f else .3f) * b[3])
            }
        }
        else -> velvet()
    }
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .04f else .12f), Color.Transparent), 0f, h * .35f))
}

/** Как выглядит плашка в выбранном стиле: заливка (или градиент), рамка и цвет, на котором проверяется текст. */
class CardDecor(val brush: Brush?, val fill: Color, val border: BorderStroke?)

private fun tint(c: Color, to: Color, f: Float) = Color(c.red + (to.red - c.red) * f, c.green + (to.green - c.green) * f, c.blue + (to.blue - c.blue) * f, c.alpha)

/** Стиль плашки [fx] (номер в Look.CARD_FX) для цвета [c]; null — обычная сплошная плашка. */
fun cardDecor(fx: Int, c: Color, pal: IconPalette, dark: Boolean): CardDecor? = when (fx) {
    1 -> CardDecor(
        Brush.verticalGradient(listOf(tint(c, Color.White, if (dark) .14f else .35f), c)), c,
        BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .32f else .9f), Color.White.copy(alpha = if (dark) .04f else .3f)))),
    )
    2 -> CardDecor(Brush.linearGradient(listOf(tint(c, rgb(pal.mid), .28f), c, tint(c, Color.Black, if (dark) .25f else .06f))), c, null)
    3 -> {
        val f = c.copy(alpha = c.alpha * .3f)
        CardDecor(null, f, BorderStroke(1.5.dp, rgb(if (dark) pal.mid else pal.deep).copy(alpha = if (dark) .8f else .45f)))
    }
    4 -> CardDecor(null, c, BorderStroke(1.5.dp, Brush.linearGradient(listOf(rgb(pal.accent), rgb(pal.mid), rgb(pal.accent).copy(alpha = .25f)))))
    5 -> CardDecor(
        Brush.verticalGradient(listOf(tint(c, Color.White, if (dark) .07f else .4f), tint(c, Color.Black, if (dark) .14f else .04f))), c,
        BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .2f else .9f), Color.Black.copy(alpha = if (dark) .3f else .1f)))),
    )
    else -> null
}

/** Стиль плашек из «Оформления» для обычной карточки цвета [c] (в новогоднем режиме — свои стеклянные плашки). */
@Composable
fun currentCardDecor(c: Color, fx: Int = LocalLook.current.cardFx): CardDecor? {
    if (com.dasein.poryadok.ui.theme.Holiday.active) return null
    return cardDecor(fx, c, com.dasein.poryadok.ui.theme.currentPalette(), LocalExtra.current.dark)
}

/** Заливка плашки с её стилем — для образцов и превью. */
@Composable
fun Modifier.cardFill(shape: Shape, c: Color = LocalExtra.current.card, fx: Int = LocalLook.current.cardFx): Modifier {
    val d = currentCardDecor(c, fx) ?: return this.background(c, shape)
    return this.let { m -> if (d.brush != null) m.background(d.brush, shape) else m.background(d.fill, shape) }
        .let { m -> d.border?.let { m.border(it, shape) } ?: m }
}
