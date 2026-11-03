package com.dasein.poryadok.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dasein.poryadok.logic.HolidaySeason
import com.dasein.poryadok.ui.theme.Holiday
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Корень приложения в новогодний сезон: следит за датой (тема включается и выключается сама, без перезапуска)
 * и при новом входе в приложение один раз показывает снегопад поверх всего экрана.
 */
@Composable
fun HolidayHost(content: @Composable () -> Unit) {
    // Дата меняется, пока приложение открыто (например, в полночь 15 декабря), — проверяем раз в минуту.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            Holiday.refreshDate()
        }
    }
    Box(Modifier.fillMaxSize()) {
        content()
        Snowfall()
    }
}

/** Одна снежинка: где начинается, как быстро падает и как покачивается. Всё в долях экрана. */
private class Flake(val x: Float, val y0: Float, val speed: Float, val radius: Float, val sway: Float, val swayHz: Float, val phase: Float, val alpha: Float)

/**
 * Снегопад на 5 секунд при новом входе в приложение (не при переходах между разделами).
 * Только рисование — без обработки касаний, поэтому нажатия, прокрутка и жесты проходят сквозь снег.
 * Позиции снежинок — функция времени: каждый кадр перерисовывается только холст, без перестройки интерфейса.
 * Не показывается в режиме энергосбережения, при низком заряде и с выключенными в системе анимациями.
 */
@Composable
private fun Snowfall() {
    val ctx = LocalContext.current
    val entry = Holiday.entry.intValue
    val allowed = Holiday.snowNow
    var running by remember { mutableStateOf(false) }
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(entry, allowed) {
        // Снег один раз на вход: перерисовки, поворот экрана и возврат в раздел не запускают его снова.
        // Вход засчитывается, даже если снег сейчас не нужен: включённые позже снежинки ждут следующего входа.
        if (entry <= Holiday.snowShownFor) return@LaunchedEffect
        Holiday.snowShownFor = entry
        if (!allowed || TabAnim.saving(ctx)) return@LaunchedEffect
        running = true
        elapsed.longValue = 0L
        val start = withFrameMillis { it }
        while (elapsed.longValue < HolidaySeason.SNOW_MS) {
            elapsed.longValue = withFrameMillis { it } - start
        }
        running = false
    }
    if (!running) return
    val dark = LocalExtra.current.dark
    // На светлом фоне белый снег не виден — там снежинки ледяного голубого оттенка.
    val tint = if (dark) Color.White else Color(0xFFAFC4DA)
    val flakes = remember {
        val rnd = Random(entry)
        List(70) {
            // Три размера: мелкие падают медленнее и прозрачнее — так снег выглядит объёмным.
            val layer = rnd.nextInt(3)
            Flake(
                x = rnd.nextFloat(),
                y0 = -rnd.nextFloat() * .9f - .05f,
                speed = (.16f + .07f * layer + rnd.nextFloat() * .06f),
                radius = (1.6f + 1.4f * layer + rnd.nextFloat()),
                sway = .008f + rnd.nextFloat() * .018f,
                swayHz = .25f + rnd.nextFloat() * .35f,
                phase = rnd.nextFloat() * 2f * PI.toFloat(),
                alpha = .55f + .15f * layer + rnd.nextFloat() * .15f,
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = elapsed.longValue / 1000f
        val total = HolidaySeason.SNOW_MS / 1000f
        // Плавно появляется за 0,4 с и тает последнюю секунду.
        val fade = (t / .4f).coerceAtMost(1f) * ((total - t) / 1f).coerceIn(0f, 1f)
        val dp = density
        flakes.forEach { f ->
            val y = (f.y0 + f.speed * t) * size.height
            if (y < -20f || y > size.height + 20f) return@forEach
            val x = (f.x + f.sway * sin(2f * PI.toFloat() * f.swayHz * t + f.phase)) * size.width
            drawCircle(tint, f.radius * dp, Offset(x, y), alpha = f.alpha * fade)
        }
    }
}
