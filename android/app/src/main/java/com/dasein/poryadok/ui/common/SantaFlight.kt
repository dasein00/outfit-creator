package com.dasein.poryadok.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.dasein.poryadok.ui.theme.Holiday
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Сколько летят сани через экран и сколько ждут перед следующим пролётом. */
private const val FLIGHT_MS = 7_500L
private const val PAUSE_MS = 35_000L
private const val FIRST_MS = 3_000L

/** Общие для приложения: когда сани пролетали, куда полетят следующими, какой запуск кнопкой уже показан. */
private object Flights {
    var lastAt = 0L
    var toLeft = false
    var shownFor = 0
}

/**
 * Сани Санты с упряжкой из трёх оленей (впереди — красноносый) пролетают через «коридор» — полосу, на которую
 * наложен этот слой, — и уходят за край экрана. Следующий пролёт — в обратную сторону. Только рисунок:
 * нажатия проходят сквозь сани. Летают в новогоднем режиме, если не выключены в «Оформлении».
 */
@Composable
fun SantaFlight(modifier: Modifier) {
    if (!Holiday.active || !Holiday.santa.value) return
    val ctx = LocalContext.current
    val assets = HolidayAssets
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { assets.load(ctx.applicationContext) } }
    if (!assets.ready.value || assets.sleigh.size < 2 || assets.deer.size < 3 || assets.deerRed.size < 3) return
    val progress = remember { mutableFloatStateOf(-1f) }
    var toLeft by remember { mutableStateOf(Flights.toLeft) }
    val now = Holiday.santaNow.intValue
    LaunchedEffect(now) {
        var first = true
        while (true) {
            // Кнопка «Запустить сани сейчас» — сразу; иначе пауза считается от прошлого пролёта, а не от возврата на главную.
            val wait = when {
                first && now > Flights.shownFor -> 0L
                else -> maxOf(if (first) FIRST_MS else PAUSE_MS, PAUSE_MS - (System.currentTimeMillis() - Flights.lastAt))
            }
            Flights.shownFor = now
            kotlinx.coroutines.delay(wait)
            first = false
            if (TabAnim.saving(ctx)) { Flights.lastAt = System.currentTimeMillis(); continue }
            val start = withFrameMillis { it }
            while (true) {
                val t = (withFrameMillis { it } - start) / FLIGHT_MS.toFloat()
                if (t >= 1f) break
                progress.floatValue = t
            }
            progress.floatValue = -1f
            toLeft = !toLeft
            Flights.toLeft = toLeft
            Flights.lastAt = System.currentTimeMillis()
        }
    }
    val p = progress.floatValue
    if (p < 0f) return
    Canvas(modifier) {
        val elapsed = p * FLIGHT_MS / 1000f
        if (toLeft) scale(-1f, 1f) { team(p, elapsed) } else team(p, elapsed)
    }
}

private fun DrawScope.sprite(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float) = drawImage(
    img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(x.roundToInt(), y.roundToInt()), IntSize(w.roundToInt(), h.roundToInt()),
)

/** Упряжка летит слева направо: [p] — доля пути (0 — за левым краем, 1 — за правым), [t] — секунды полёта. */
private fun DrawScope.team(p: Float, t: Float) {
    val a = HolidayAssets
    val deerH = size.height * .92f
    val deerW = deerH * a.deer[0].width / a.deer[0].height
    val sleighH = size.height * 1.12f
    val sleighW = sleighH * a.sleigh[0].width / a.sleigh[0].height
    val step = deerW * .78f
    val gap = deerW * .25f
    val teamW = deerW + step * 2 + gap + sleighW
    // Нос вожака проходит от-за левого края (с запасом на поля экрана) до правого плюс длина упряжки.
    val front = -size.width * .15f + p * (size.width * 1.3f + teamW)
    // Лёгкая дуга по высоте коридора и покачивание в такт.
    val arc = -sin(p * PI.toFloat()) * size.height * .12f
    val wave = sin(p * PI.toFloat() * 3f) * size.height * .05f
    val baseY = (size.height - deerH) / 2f + arc + wave
    // Галоп: кадры A → C → B → C, у каждого оленя со сдвигом, ~9 кадров в секунду.
    val cycle = intArrayOf(0, 2, 1, 2)
    val frame = (t * 9f).toInt()
    data class Deer(val x: Float, val y: Float, val img: ImageBitmap)
    val deers = (0 until 3).map { i ->
        val x = front - deerW - i * step
        val bob = sin(t * 2f * PI.toFloat() * 2.2f + i * 1.3f) * size.height * .04f
        val f = cycle[(frame + i) % cycle.size]
        Deer(x, baseY + bob, if (i == 0) a.deerRed[f] else a.deer[f])
    }
    val sleighX = front - deerW - step * 2 - gap - sleighW
    val sleighY = baseY + deerH - sleighH * .92f + sin(t * 2f * PI.toFloat() * 1.1f) * size.height * .03f
    // Санта машет, когда сани посередине экрана; остальное время держит вожжи.
    val mid = (sleighX + sleighW / 2f) / size.width
    val sleighImg = if (mid in 0.25f..0.75f) a.sleigh[1] else a.sleigh[0]

    // Золотой шлейф искр за санями: искры отстают и гаснут.
    val gold = Color(0xFFF2C760)
    for (k in 1..22) {
        val back = k * size.width * .022f
        val sx = sleighX + sleighW * .12f - back
        val sy = sleighY + sleighH * (.82f + .08f * sin(k * 1.7f + t * 3f))
        val alpha = (1f - k / 23f) * (.55f + .45f * sin(t * 9f + k * 2.1f).let { it * it })
        val r = (1.2f + (k % 3) * .9f) * density * (1f - k / 30f)
        drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = .45f * alpha), Color.Transparent), Offset(sx, sy), r * 3f), r * 3f, Offset(sx, sy))
        drawCircle(gold.copy(alpha = alpha), r, Offset(sx, sy))
    }

    // Вожжи: от саней к ошейникам оленей, с провисанием (за оленями).
    val rein = Color(0xFF7A1A24)
    val path = Path()
    var px = sleighX + sleighW * .96f
    var py = sleighY + sleighH * .6f
    path.moveTo(px, py)
    for (d in deers.reversed()) {
        val cx = d.x + deerW * .67f
        val cy = d.y + deerH * .56f
        path.quadraticTo((px + cx) / 2f, maxOf(py, cy) + size.height * .06f, cx, cy)
        px = cx; py = cy
    }
    drawPath(path, rein, style = Stroke(width = 1.6f * density, cap = StrokeCap.Round))

    rotate(sin(t * 2f * PI.toFloat() * 1.1f) * 2.5f, Offset(sleighX + sleighW / 2f, sleighY + sleighH * .8f)) {
        sprite(sleighImg, sleighX, sleighY, sleighW, sleighH)
    }
    deers.reversed().forEach { sprite(it.img, it.x, it.y, deerW, deerH) }
}
