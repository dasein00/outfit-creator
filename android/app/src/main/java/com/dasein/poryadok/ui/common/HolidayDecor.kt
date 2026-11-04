package com.dasein.poryadok.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dasein.poryadok.ui.theme.Holiday
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * Новогодние украшения поверх интерфейса: гирлянда с еловыми ветками вверху экрана, снежные шапки на карточках
 * и шестиконечные снежинки. Рисуются только в новогоднем режиме (сезон или тестовый режим).
 */

/** Огоньки гирлянды: красный, золотой, зелёный, синий и тёплый белый — в тон новогодней палитры иконок. */
private val BULBS = listOf(Color(0xFFE53935), Color(0xFFFFC24A), Color(0xFF43A047), Color(0xFF42A5F5), Color(0xFFFFE7B0))
private val PINE_DARK = Color(0xFF1C4A2C)
private val PINE = Color(0xFF2E6B3F)
private val BERRY = Color(0xFFD32F2F)

/**
 * Гирлянда вдоль строки состояния, по углам — еловые ветки с ягодами. Нажатие на гирлянду включает и выключает
 * её, удержание — следующий режим: переливы, бегущие огни, мерцание. Касаться можно только полосы строки состояния,
 * поэтому кнопки верхней панели под гирляндой нажимаются как обычно.
 */
@Composable
fun HolidayGarland() {
    if (!Holiday.active) return
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val bar = with(density) { WindowInsets.statusBars.getTop(density).toDp() }.coerceAtLeast(20.dp)
    val on = Holiday.lights.value
    val mode = Holiday.lightsMode.intValue
    val time = remember { mutableFloatStateOf(0f) }
    // Огоньки живут ~30 кадров в секунду и только пока гирлянда горит; в энергосбережении горят ровно.
    LaunchedEffect(on, mode) {
        if (!on || TabAnim.saving(ctx)) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) {
            time.floatValue = (withFrameMillis { it } - start) / 1000f
            kotlinx.coroutines.delay(33)
        }
    }
    Box(Modifier.fillMaxWidth().height(bar + 30.dp)) {
        Canvas(Modifier.fillMaxWidth().height(bar + 30.dp)) {
            drawGarland(bar.toPx(), on, mode, time.floatValue)
            drawPine(Offset(0f, 0f), mirror = false)
            drawPine(Offset(size.width, 0f), mirror = true)
        }
        // Зона касания — только строка состояния.
        Box(
            Modifier.fillMaxWidth().height(bar).pointerInput(Unit) {
                detectTapGestures(
                    onTap = { Holiday.setLights(ctx, !Holiday.lights.value) },
                    onLongPress = { Holiday.nextLightsMode(ctx) },
                )
            },
        )
    }
}

private fun DrawScope.drawGarland(bar: Float, on: Boolean, mode: Int, t: Float) {
    val swags = 3
    val y0 = bar * .45f
    val sag = 14.dp.toPx()
    val w = size.width
    val wire = Path()
    val points = ArrayList<Offset>()
    val perSwag = 8
    for (s in 0 until swags) {
        val x0 = w * s / swags
        val x1 = w * (s + 1) / swags
        val cx = (x0 + x1) / 2
        val cy = y0 + sag * 2
        if (s == 0) wire.moveTo(x0, y0)
        wire.quadraticTo(cx, cy, x1, y0)
        for (i in 0 until perSwag) {
            val k = (i + .5f) / perSwag
            val x = (1 - k) * (1 - k) * x0 + 2 * (1 - k) * k * cx + k * k * x1
            val y = (1 - k) * (1 - k) * y0 + 2 * (1 - k) * k * cy + k * k * y0
            points += Offset(x, y)
        }
    }
    drawPath(wire, Color(0xFF2B2620), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    val r = 4.dp.toPx()
    points.forEachIndexed { i, p ->
        val (color, glow) = if (!on) Color(0xFF4A4238) to 0f else bulb(i, mode, t)
        val c = Offset(p.x, p.y + r * 1.6f)
        if (glow > 0f) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .55f * glow), Color.Transparent), c, r * 3.4f), r * 3.4f, c)
        // Цоколь и колба.
        drawRect(Color(0xFF3A342C), Offset(p.x - r * .45f, p.y), Size(r * .9f, r * .7f))
        drawOval(color, Offset(c.x - r * .8f, c.y - r), Size(r * 1.6f, r * 2.1f))
        drawOval(Color.White.copy(alpha = if (on) .35f * glow + .1f else .08f), Offset(c.x - r * .45f, c.y - r * .7f), Size(r * .5f, r * .7f))
    }
}

/** Цвет и яркость огонька [i] в момент [t]. */
private fun bulb(i: Int, mode: Int, t: Float): Pair<Color, Float> = when (mode) {
    // Переливы: каждый огонёк плавно перетекает в следующий цвет, волна идёт вдоль гирлянды.
    0 -> {
        val f = t * .6f + i * .35f
        val a = floor(f).toInt()
        val c = lerp(BULBS[a.mod(BULBS.size)], BULBS[(a + 1).mod(BULBS.size)], f - a)
        c to (.75f + .25f * sin(t * 2.2f + i))
    }
    // Бегущие огни: свои цвета, по гирлянде бежит яркая полоса.
    1 -> {
        val pos = (t * 7f) % 24f
        val d = ((i - pos) % 24f + 24f) % 24f
        BULBS[i % BULBS.size] to (if (d < 4f) 1f else .25f)
    }
    // Мерцание: каждый огонёк вспыхивает в своём ритме.
    else -> {
        val v = sin(t * (1.3f + (i * 37 % 11) / 6f) + i * 1.7f)
        BULBS[i % BULBS.size] to (if (v > .2f) 1f else .2f)
    }
}

/** Еловая ветка в углу с тремя ягодами. */
private fun DrawScope.drawPine(corner: Offset, mirror: Boolean) {
    val s = 1.dp.toPx()
    val dir = if (mirror) -1f else 1f
    val sprigs = listOf(Triple(10f, 62f, 54f), Triple(30f, 70f, 46f), Triple(52f, 58f, 40f), Triple(-8f, 50f, 34f))
    sprigs.forEachIndexed { k, (angle, len, needles) ->
        val a = (angle * PI / 180).toFloat()
        val end = Offset(corner.x + dir * cos(a) * len * s, corner.y + sin(a) * len * s)
        drawLine(PINE_DARK, corner, end, 2.2f * s, StrokeCap.Round)
        val n = (needles / 6).toInt()
        for (j in 1..n) {
            val f = j / (n + 1f)
            val base = Offset(corner.x + (end.x - corner.x) * f, corner.y + (end.y - corner.y) * f)
            val nl = (9f - 4f * f) * s
            for (side in listOf(-1f, 1f)) {
                val na = a + side * .9f
                drawLine(if ((j + k) % 2 == 0) PINE else PINE_DARK, base, Offset(base.x + dir * cos(na) * nl, base.y + sin(na) * nl), 1.4f * s, StrokeCap.Round)
            }
        }
    }
    listOf(Offset(22f, 22f), Offset(28f, 28f), Offset(18f, 30f)).forEach { o ->
        val c = Offset(corner.x + dir * o.x * s, corner.y + o.y * s)
        drawCircle(BERRY, 3.2f * s, c)
        drawCircle(Color.White.copy(alpha = .45f), 1f * s, Offset(c.x - s, c.y - s))
    }
}

/** Шестиконечная снежинка: три оси через центр, на каждом луче по паре веточек. */
fun DrawScope.snowflake(center: Offset, radius: Float, color: Color, alpha: Float, angle: Float = 0f) {
    val stroke = (radius * .16f).coerceAtLeast(1f)
    rotate(angle, center) {
        for (k in 0 until 6) {
            val a = (k * PI / 3).toFloat()
            val tip = Offset(center.x + cos(a) * radius, center.y + sin(a) * radius)
            drawLine(color, center, tip, stroke, StrokeCap.Round, alpha = alpha)
            val bx = center.x + cos(a) * radius * .58f
            val by = center.y + sin(a) * radius * .58f
            for (side in listOf(-1f, 1f)) {
                val ba = a + side * (PI / 4).toFloat()
                drawLine(color, Offset(bx, by), Offset(bx + cos(ba) * radius * .32f, by + sin(ba) * radius * .32f), stroke, StrokeCap.Round, alpha = alpha)
            }
        }
    }
}

/**
 * Снежная шапка по верхнему краю карточки (в новогоднем режиме): белый сугроб с неровным краем и парой сосулек-капель.
 * Только рисование — размеры, отступы и нажатия карточки не меняются.
 */
fun Modifier.snowCap(): Modifier = drawWithContent {
    drawContent()
    if (!Holiday.active) return@drawWithContent
    val h = 7.dp.toPx()
    val w = size.width
    if (w < 80.dp.toPx() || size.height < 36.dp.toPx()) return@drawWithContent
    val path = Path().apply {
        moveTo(0f, 0f)
        lineTo(w, 0f)
        lineTo(w, h * .6f)
        // Неровный нижний край: волны разной высоты, по ширине карточки всегда одинаковые.
        val bumps = (w / 40.dp.toPx()).toInt().coerceAtLeast(3)
        for (i in bumps downTo 1) {
            val x0 = w * i / bumps
            val x1 = w * (i - 1) / bumps
            val depth = h * (.75f + .35f * sin(i * 2.3f))
            quadraticTo((x0 + x1) / 2, depth * 1.35f, x1, h * (.45f + .2f * cos(i * 1.7f)))
        }
        close()
    }
    val dark = Color.White
    drawPath(path, Color(0x22000000), style = Stroke(1.2.dp.toPx()))
    drawPath(path, dark.copy(alpha = .93f))
    // Пара капель-сосулек.
    listOf(.18f, .63f).forEach { f ->
        val x = w * f
        drawOval(dark.copy(alpha = .9f), Offset(x - 2.dp.toPx(), h * .6f), Size(4.dp.toPx(), 6.dp.toPx()))
    }
}

/** Цвет снега: на тёмной теме белый, на светлой — ледяной голубой, чтобы был виден. */
@Composable
fun snowTint(): Color = if (LocalExtra.current.dark) Color.White else Color(0xFFAFC4DA)
