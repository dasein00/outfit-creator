package com.dasein.poryadok.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.dasein.poryadok.logic.HolidaySeason
import com.dasein.poryadok.ui.theme.Holiday
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Корень приложения в новогодний сезон: следит за датой (тема включается и выключается сама, без перезапуска),
 * ведёт общие часы украшений, рисует гирлянду и при новом входе один раз показывает снегопад поверх экрана.
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
    // Одни часы на все украшения (~30 кадров в секунду) — и только пока есть что анимировать.
    val active = Holiday.active
    // Часы идут весь новогодний режим: по ним мигают гирлянда и лампочки на графиках, падает снег на фоне.
    val animate = active
    // Картинки снега и сосулек — в фоне, один раз.
    val appCtx = LocalContext.current.applicationContext
    LaunchedEffect(active) { if (active) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HolidayAssets.load(appCtx) } }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        val start = withFrameMillis { it } - (Holiday.clock.floatValue * 1000).toLong()
        while (true) {
            Holiday.clock.floatValue = (withFrameMillis { it } - start) / 1000f
            kotlinx.coroutines.delay(33)
        }
    }
    Box(Modifier.fillMaxSize()) {
        content()
        HolidayGarland()
        Snowfall()
    }
}

/** Цвет снежинок: на тёмной теме белый, на светлой — ледяной голубой, чтобы был виден. */
@Composable
fun snowTint(): Color = if (LocalExtra.current.dark) Color.White else Color(0xFFAFC4DA)

/** Снежинка фона: где, как быстро падает, покачивается и вращается. Всё в долях экрана. */
private class BackFlake(val x: Float, val y0: Float, val speed: Float, val radius: Float, val sway: Float, val swayHz: Float, val phase: Float, val alpha: Float)

private val BACK_FLAKES = Random(2026).let { rnd ->
    List(26) {
        BackFlake(
            x = rnd.nextFloat(), y0 = rnd.nextFloat(), speed = .03f + rnd.nextFloat() * .045f,
            radius = 3.5f + rnd.nextFloat() * 5f, sway = .01f + rnd.nextFloat() * .02f, swayHz = .08f + rnd.nextFloat() * .15f,
            phase = rnd.nextFloat() * 6.28f, alpha = .35f + rnd.nextFloat() * .65f,
        )
    }
}

/**
 * Полупрозрачный снег на фоне экрана — под карточками, верхней панелью и кнопками (рисуется первым слоем,
 * всё остальное лежит поверх). Медленный и бледный, чтобы не мешать читать. Без касаний — только рисунок.
 */
@Composable
fun HolidayBackdrop() {
    if (!Holiday.active) { DeepBackdrop(); return }
    val tint = snowTint()
    val dark = LocalExtra.current.dark
    val strength = if (dark) .16f else .3f
    val flakes = Holiday.backdrop.value
    Canvas(Modifier.fillMaxSize()) {
        // Бархат выбранного цвета: светлее и насыщеннее сверху по центру, к краям глубже — как ткань под светом.
        val st = Holiday.bg()
        val velvet = if (dark) st.dark else st.light
        drawRect(Brush.radialGradient(velvet, Offset(size.width * .5f, size.height * .3f), maxOf(size.width, size.height) * .9f))
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .04f else .12f), Color.Transparent), 0f, size.height * .35f))
        val t0 = Holiday.clock.floatValue
        // Хвойная кайма сверху и снизу — под карточками, видна между ними.
        HolidayAssets.borderBottom?.let { img -> drawBorder(img, top = false) }
        HolidayAssets.borderTop?.let { img -> drawBorder(img, top = true) }
        // Мерцающие золотые искорки вместо статичных снежинок.
        SPARKLES.forEach { sp ->
            val a = (.5f + .5f * sin(t0 * sp.speed + sp.phase)).let { it * it }
            if (a > .05f) sparkle(Offset(sp.x * size.width, sp.y * size.height), sp.r * density, Color(0xFFF2C760), a * (if (dark) .85f else .7f))
        }
        if (!flakes) return@Canvas
        val t = Holiday.clock.floatValue
        BACK_FLAKES.forEach { f ->
            // Падает сверху вниз и снова появляется сверху — бесконечно, по кругу.
            val p = ((f.y0 + f.speed * t) % 1.1f + 1.1f) % 1.1f - .05f
            val x = (f.x + f.sway * sin(2f * PI.toFloat() * f.swayHz * t + f.phase)) * size.width
            snowflake(Offset(x, p * size.height), f.radius * density, tint, strength * f.alpha, angle = f.phase * 57f + t * 8f)
        }
    }
}

/** Под экраном рисуется свой слой фона: новогодний бархат или градиент/стиль фона из «Оформления». */
@Composable
fun hasBackdrop(): Boolean = Holiday.active ||
    com.dasein.poryadok.ui.theme.Look.gradient(com.dasein.poryadok.ui.theme.LocalLook.current, androidx.compose.material3.MaterialTheme.colorScheme.background) != null

/** Фон обычной темы в выбранном стиле (бархат, диагональ, аврора, меш, прожектор, зерно, боке). */
@Composable
private fun DeepBackdrop() {
    val look = com.dasein.poryadok.ui.theme.LocalLook.current
    val v = com.dasein.poryadok.ui.theme.Look.gradient(look, androidx.compose.material3.MaterialTheme.colorScheme.background) ?: return
    val dark = LocalExtra.current.dark
    val pal = com.dasein.poryadok.ui.theme.currentPalette()
    val fx = com.dasein.poryadok.ui.theme.Look.fx(look)
    Canvas(Modifier.fillMaxSize()) { fancyBackground(v, fx, pal, dark) }
}

/** Снежинка входного снегопада. Всё в долях экрана; [ground] — на какой высоте от низа она осядет. */
private class Flake(val x: Float, val y0: Float, val speed: Float, val radius: Float, val sway: Float, val swayHz: Float, val phase: Float, val alpha: Float, val ground: Float)

/**
 * Снегопад при новом входе в приложение (не при переходах между разделами): 5 секунд идёт снег, потом новые
 * снежинки больше не появляются, а уже летящие улетают за нижний край экрана. Только рисование — нажатия, прокрутка и жесты проходят сквозь снег.
 * Позиции — функция времени: каждый кадр перерисовывается только холст, без перестройки интерфейса.
 */
@Composable
private fun Snowfall() {
    val ctx = LocalContext.current
    val dens = LocalDensity.current
    val entry = Holiday.entry.intValue
    val allowed = Holiday.snowNow
    val navBottom = WindowInsets.navigationBars.getBottom(dens).toFloat()
    var running by remember { mutableStateOf(false) }
    val elapsed = remember { mutableLongStateOf(0L) }
    val flakes = remember(entry) {
        val rnd = Random(entry)
        List(56) {
            // Три размера: мелкие падают медленнее и прозрачнее — так снег выглядит объёмным.
            val layer = rnd.nextInt(3)
            val speed = .16f + .07f * layer + rnd.nextFloat() * .06f
            // Появляется за первые 5 секунд (стартовая высота подобрана под скорость), дальше — только долетают.
            val appearAt = rnd.nextFloat() * (HolidaySeason.SNOW_MS / 1000f)
            Flake(
                x = rnd.nextFloat(), y0 = -.04f - speed * appearAt, speed = speed,
                radius = 3f + 2f * layer + rnd.nextFloat() * 1.5f, sway = .008f + rnd.nextFloat() * .018f,
                swayHz = .25f + rnd.nextFloat() * .35f, phase = rnd.nextFloat() * 2f * PI.toFloat(),
                alpha = .55f + .15f * layer + rnd.nextFloat() * .15f, ground = rnd.nextFloat() * .028f,
            )
        }
    }
    // Сколько всего длится: пока последняя снежинка не улетит за нижний край экрана.
    val goneAll = flakes.maxOf { (1.06f - it.y0) / it.speed }
    val totalMs = (goneAll * 1000).toLong()
    LaunchedEffect(entry, allowed) {
        // Снег один раз на вход: перерисовки, поворот экрана и возврат в раздел не запускают его снова.
        // Вход засчитывается, даже если снег сейчас не нужен: включённые позже снежинки ждут следующего входа.
        if (entry <= Holiday.snowShownFor) return@LaunchedEffect
        Holiday.snowShownFor = entry
        if (!allowed || TabAnim.saving(ctx)) return@LaunchedEffect
        running = true
        elapsed.longValue = 0L
        val start = withFrameMillis { it }
        while (elapsed.longValue < totalMs) {
            elapsed.longValue = withFrameMillis { it } - start
        }
        running = false
    }
    if (!running) return
    val tint = snowTint()
    Canvas(Modifier.fillMaxSize()) {
        val t = elapsed.longValue / 1000f
        // Появляется за 0,4 с, дальше снежинки просто улетают за нижний край экрана — без исчезания на месте.
        val fadeIn = (t / .4f).coerceAtMost(1f)
        flakes.forEach { f ->
            val y = (f.y0 + f.speed * t) * size.height
            if (y < -20f || y > size.height + 30f) return@forEach
            val x = (f.x + f.sway * sin(2f * PI.toFloat() * f.swayHz * t + f.phase)) * size.width
            val spin = f.phase * 57f + t * 25f * (if (f.swayHz > .4f) 1f else -1f)
            snowflake(Offset(x, y), f.radius * density, tint, f.alpha * fadeIn, angle = spin)
        }
    }
}

/** Золотые искорки фона: место (доли экрана), размер, ритм мерцания. */
private class Sparkle(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float)

private val SPARKLES = Random(1225).let { rnd ->
    List(16) {
        // Больше у верхнего и нижнего края — рядом с хвойной каймой.
        val y = if (it % 3 == 0) .3f + rnd.nextFloat() * .4f else if (it % 2 == 0) rnd.nextFloat() * .22f else .78f + rnd.nextFloat() * .2f
        Sparkle(rnd.nextFloat(), y, 3f + rnd.nextFloat() * 4f, .8f + rnd.nextFloat() * 1.6f, rnd.nextFloat() * 6.28f)
    }
}

/** Четырёхлучевая искорка: ромб с вогнутыми гранями и мягким ореолом. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.sparkle(c: Offset, r: Float, color: Color, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .35f * alpha), Color.Transparent), c, r * 2.2f), r * 2.2f, c)
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color, alpha = alpha)
}

/** Кайма во всю ширину экрана: снизу — над кнопками Android, сверху — от края экрана. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBorder(img: androidx.compose.ui.graphics.ImageBitmap, top: Boolean) {
    val h = size.width * img.height / img.width
    val y = if (top) 0f else size.height - h
    drawImage(
        img, srcOffset = androidx.compose.ui.unit.IntOffset.Zero, srcSize = androidx.compose.ui.unit.IntSize(img.width, img.height),
        dstOffset = androidx.compose.ui.unit.IntOffset(0, y.toInt()), dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), h.toInt()),
        alpha = if (top) .75f else .9f,
    )
}
