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
data class IconTone(
    val hueShift: Float, val sat: Float, val light: Boolean, val mono: Int = 0, val festive: Boolean = false, val gold: Boolean = false,
    /** Сочетание цветов, подобранное к фону: иконка раскрашивается по ролям частей рисунка, а не в один цвет. */
    val palette: com.dasein.poryadok.ui.theme.IconPalette? = null,
) {
    val identity get() = hueShift == 0f && sat == 1f && !light && mono == 0 && !festive && palette == null
    val key get() = "$hueShift|$sat|$light|$mono|$festive|$gold|${palette?.name}"
}

/** Иконки «жёлтых» вещей — солнце, огонь, свет, награды, звёзды: в новогоднем режиме их акцент праздничный золотой. */
val GOLD_ICONS = setOf(
    "ui:sun", "ui:flame", "ui:bulb", "ui:trophy", "ui:v_spark", "sport/22", "d04/01", "d08/14", "d11/02",
    "fest/17", "habit/22", "habit/28", "sleep/18", "d01/06", "d03/14",
)

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
        if (t.festive) { festive(px, t.light, t.gold, hsl); return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888) }
        t.palette?.let { paletteMap(px, t.light, t.gold, it, hsl); return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888) }
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

    /**
     * Новогодняя палитра по ролям частей рисунка, одинаково для всех иконок (формы и прозрачность не меняются):
     * яркий акцент (оранжевый) — праздничный красный, его светлые блики — золотистый, светлые кремовые формы — белый,
     * их тени и полутона — хвойный зелёный. В светлой теме светлые формы становятся тёмно-зелёными, как раньше
     * становились тёмными: иконка остаётся контрастной на светлом фоне. Цвета приглушённые, не кислотные.
     */
    private fun festive(px: IntArray, light: Boolean, gold: Boolean, hsl: FloatArray) {
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = (c shr 16 and 255) / 255f; val g = (c shr 8 and 255) / 255f; val b = (c and 255) / 255f
            val chroma = max(r, max(g, b)) - min(r, min(g, b))
            toHsl(r, g, b, hsl)
            val l = hsl[2]
            val hue = hsl[0]
            val rgb = when {
                // Жёлтые детали (солнце, свет) и «золотые» иконки (огонь, награды) — праздничное золото.
                chroma >= .45f && (hue in 40f..75f || gold) && !(hue in 170f..280f) ->
                    fromHsl(42f, .62f, if (light) (l * .85f).coerceIn(.36f, .5f) else (l * 1.05f).coerceIn(.5f, .68f))
                // Голубое (вода, дождь, лёд) остаётся холодным голубым.
                chroma >= .45f && hue in 170f..280f -> fromHsl(205f, .55f, l.coerceIn(.4f, .7f))
                // Акцент: светлые блики — золото, остальное — праздничный красный, чуть светлее фона, чтобы не сливаться.
                chroma >= .55f && l > .7f -> fromHsl(43f, .55f, if (light) .46f else .62f)
                chroma >= .55f -> fromHsl(356f, .7f, (l * 1.05f).coerceIn(.42f, .58f))
                // Светлые формы: белый (тёмная тема) или тёмно-зелёный (светлая).
                l >= .8f -> if (light) fromHsl(150f, .42f, (.9f - .7f * l).coerceIn(.18f, .4f)) else fromHsl(40f, .3f, (l * 1.08f).coerceAtMost(.96f))
                // Полутона и тени форм — зелёный.
                l >= .35f -> if (light) fromHsl(150f, .4f, (.9f - .7f * l).coerceIn(.3f, .62f)) else fromHsl(148f, .36f, (l * .62f).coerceIn(.3f, .5f))
                // Тёмные детали (контуры, стержень) — почти чёрный зелёный, в светлой теме остаются тёмными.
                else -> fromHsl(150f, .3f, if (light) (l + .08f).coerceAtMost(.3f) else l.coerceAtLeast(.12f))
            }
            px[i] = (a shl 24) or rgb
        }
    }

    /**
     * Раскраска по сочетанию [p] — по тем же ролям, что и новогодняя: яркие детали — акцент сочетания, их блики и
     * жёлтое — светлый акцент, светлые формы — светлый тон (в светлой теме — тёмный), полутона — средний, контуры — тёмный.
     * Светлота исходного пикселя сохраняет детали рисунка.
     */
    private fun paletteMap(px: IntArray, light: Boolean, gold: Boolean, p: com.dasein.poryadok.ui.theme.IconPalette, hsl: FloatArray) {
        val roles = listOf(p.accent, p.highlight, p.light, p.mid, p.deep).map { c ->
            FloatArray(3).also { toHsl((c shr 16 and 255) / 255f, (c shr 8 and 255) / 255f, (c and 255) / 255f, it) }
        }
        val (acc, hi, lt, md, dp) = roles
        fun role(r: FloatArray, l: Float, center: Float, lo: Float = .08f, top: Float = .97f) =
            fromHsl(r[0], r[1], (r[2] + (l - center) * .5f).coerceIn(lo, top))
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = (c shr 16 and 255) / 255f; val g = (c shr 8 and 255) / 255f; val b = (c and 255) / 255f
            val chroma = max(r, max(g, b)) - min(r, min(g, b))
            toHsl(r, g, b, hsl)
            val l = hsl[2]; val hue = hsl[0]
            val rgb = when {
                chroma >= .45f && (hue in 40f..75f || gold) && hue !in 170f..280f -> role(hi, l, .6f, if (light) .35f else .5f)
                chroma >= .45f && hue in 170f..280f -> role(md, l, .55f, .3f, .75f)
                chroma >= .55f && l > .7f -> role(hi, l, .8f, if (light) .35f else .5f)
                chroma >= .55f -> role(acc, l, .5f, .3f, .75f)
                // Светлые формы: на тёмном фоне — светлый тон сочетания, на светлом — тёмный, чтобы иконка была видна.
                l >= .8f -> if (light) role(dp, .9f - .7f * l, .25f, .1f, .42f) else role(lt, l, .9f, .7f)
                l >= .35f -> if (light) role(md, l * .8f, .45f, .25f, .6f) else role(md, l, .55f, .3f, .72f)
                else -> if (light) role(dp, l, .2f, .08f, .3f) else role(dp, l, .2f, .12f, .4f)
            }
            px[i] = (a shl 24) or rgb
        }
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
fun iconTone(onPlate: Boolean, gold: Boolean = false): IconTone {
    // Новогодний режим: праздничные цвета для всех иконок. Контрастный режим и «контрастные» иконки — для читаемости — не трогаем.
    val holiday = com.dasein.poryadok.ui.theme.Holiday.active
    val look0 = com.dasein.poryadok.ui.theme.LocalLook.current
    if (holiday && !look0.contrast && look0.icons != 1) {
        val dark0 = LocalExtra.current.dark
        return remember(dark0, onPlate, gold) { IconTone(0f, 1f, light = !dark0 && !onPlate, festive = true, gold = gold) }
    }
    val p = MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF
    val dark = LocalExtra.current.dark
    val look = com.dasein.poryadok.ui.theme.LocalLook.current
    val text = MaterialTheme.colorScheme.onBackground.toArgb() and 0xFFFFFF
    if (look.icons == 4) {
        val pal = com.dasein.poryadok.ui.theme.currentPalette()
        return remember(pal, dark, onPlate, gold) { IconTone(0f, 1f, light = !dark && !onPlate, gold = gold, palette = pal) }
    }
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
