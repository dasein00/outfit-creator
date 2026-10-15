package com.dasein.poryadok.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Перекраска иконок под оформление. Иконки нарисованы в латуни (кремовый + оранжевый). При другом цвете акцента
 * (шалфей, небо, роза, лаванда) оттенок поворачивается к нему, насыщенность подстраивается; в светлой теме
 * светлые формы становятся тёмными — иконки контрастны на светлом фоне без тёмной плашки.
 */
data class IconTone(val hueShift: Float, val sat: Float, val light: Boolean, val mono: Int = 0) {
    val identity get() = hueShift == 0f && sat == 1f && !light && mono == 0
    val key get() = "$hueShift|$sat|$light|$mono"
}

object IconTint {
    private val brass = hsv(0xC79246)

    private fun hsv(rgb: Int) = FloatArray(3).also { android.graphics.Color.colorToHSV(rgb or (0xFF shl 24), it) }

    fun tone(accent: Int, light: Boolean): IconTone {
        val h = hsv(accent)
        var dh = h[0] - brass[0]
        if (dh > 180) dh -= 360
        if (dh < -180) dh += 360
        if (abs(dh) < 10) return IconTone(0f, 1f, light)
        val sat = sqrt((h[1] / brass[1]).coerceIn(.3f, 1.4f)).coerceIn(.6f, 1.1f)
        return IconTone(dh, sat, light)
    }

    /** Пиксели в HSL: поворот оттенка, насыщенность, в светлой теме — светлота наоборот. */
    fun apply(src: Bitmap, t: IconTone): Bitmap {
        if (t.identity) return src
        val w = src.width; val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val hsl = FloatArray(3)
        if (t.mono != 0) {
            // Одноцветные иконки: цвет задан (контраст с фоном, акцент или свой), светлота формы сохраняет детали рисунка.
            val mr = t.mono shr 16 and 255; val mg = t.mono shr 8 and 255; val mb = t.mono and 255
            val lightTint = (mr * 299 + mg * 587 + mb * 114) / 1000 > 128
            for (i in px.indices) {
                val c = px[i]
                val a = c ushr 24
                if (a == 0) continue
                val l = ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 255000f
                val k = if (lightTint) .55f + .45f * l else 1.25f - .45f * l
                fun ch(v: Int) = (v * k).toInt().coerceIn(0, 255)
                px[i] = (a shl 24) or (ch(mr) shl 16) or (ch(mg) shl 8) or ch(mb)
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = (c shr 16 and 255) / 255f; val g = (c shr 8 and 255) / 255f; val b = (c and 255) / 255f
            toHsl(r, g, b, hsl)
            if (hsl[1] > .04f) { hsl[0] = ((hsl[0] + t.hueShift) % 360f + 360f) % 360f; hsl[1] = (hsl[1] * t.sat).coerceIn(0f, 1f) }
            if (t.light) hsl[2] = (.9f - .7f * hsl[2]).coerceIn(0f, 1f)
            px[i] = (a shl 24) or fromHsl(hsl[0], hsl[1], hsl[2])
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun toHsl(r: Float, g: Float, b: Float, out: FloatArray) {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        if (mx == mn) { out[0] = 0f; out[1] = 0f; out[2] = l; return }
        val d = mx - mn
        val s = if (l > .5f) d / (2 - mx - mn) else d / (mx + mn)
        val hh = when (mx) {
            r -> (g - b) / d + (if (g < b) 6 else 0)
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * 60f
        out[0] = hh; out[1] = s; out[2] = l
    }

    private fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1 - abs(2 * l - 1)) * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        fun v(f: Float) = ((f + m) * 255f + .5f).toInt().coerceIn(0, 255)
        return (v(r) shl 16) or (v(g) shl 8) or v(b)
    }

    private val cache = LruCache<String, ImageBitmap>(600)

    fun evictAll() = cache.evictAll()

    /** Уже готовая иконка из памяти — без декодирования (для мгновенной отрисовки при прокрутке). */
    fun peek(id: String, t: IconTone): ImageBitmap? = cache.get(id + "#" + t.key)

    fun cached(id: String, t: IconTone, load: () -> Bitmap?): ImageBitmap? {
        val k = id + "#" + t.key
        cache.get(k)?.let { return it }
        val b = load() ?: return null
        return apply(b, t).asImageBitmap().also { cache.put(k, it) }
    }

    fun res(ctx: Context, res: Int, t: IconTone): ImageBitmap? =
        cached("res:$res", t) { runCatching { BitmapFactory.decodeResource(ctx.resources, res) }.getOrNull() }
}

/** Оттенок иконок для текущего оформления. [onPlate] — иконка лежит на тёмной плашке: тогда без инверсии светлоты. */
@Composable
fun iconTone(onPlate: Boolean): IconTone {
    val p = MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF
    val dark = LocalExtra.current.dark
    val look = com.dasein.poryadok.ui.theme.LocalLook.current
    val text = MaterialTheme.colorScheme.onBackground.toArgb() and 0xFFFFFF
    val mono = when (look.icons) {
        // Контраст: на плашке (она тёмная) — светлые, иначе — цвета текста, то есть всегда видны на фоне.
        1 -> if (onPlate) 0xF0ECE3 else text
        2 -> p
        3 -> look.iconRgb.takeIf { it != 0 }?.and(0xFFFFFF) ?: text
        else -> 0
    }
    // Чистый чёрный (0) совпал бы с «без перекраски» — берём почти чёрный.
    val m = if (look.icons != 0 && mono == 0) 0x010101 else mono
    return remember(p, dark, onPlate, m) { if (m != 0) IconTone(0f, 1f, false, m) else IconTint.tone(p, light = !dark && !onPlate) }
}
