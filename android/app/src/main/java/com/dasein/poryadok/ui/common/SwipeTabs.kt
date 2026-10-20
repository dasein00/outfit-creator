package com.dasein.poryadok.ui.common

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Свайп одним пальцем влево/вправо — соседняя вкладка (подраздел). Жест срабатывает, только если его не взял
 * вложенный элемент с горизонтальной прокруткой (лента карточек, ползунок, график): тогда прокручивается он.
 * Вертикальная прокрутка не мешает: живой палец почти всегда немного уходит вверх-вниз, и список успевает
 * перехватить касание — если при этом движение в целом горизонтальное, вкладка всё равно листается.
 * Хватает и короткого быстрого взмаха. На крайней вкладке жест уходит дальше — во внешние вкладки
 * (например, «Календарь → Задачи»).
 */
fun Modifier.swipeTabs(index: Int, count: Int, onSelect: (Int) -> Unit): Modifier = pointerInput(index, count) {
    val threshold = 64.dp.toPx()
    val flingMin = 36.dp.toPx()
    val flingSpeed = 500.dp.toPx()
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var dx = 0f
        var dy = 0f
        // Касание взял вложенный элемент: горизонтальный — жест его; вертикальный список — следим дальше, но строже.
        var takenByList = false
        // Жест наш: дальше гасим движение, чтобы кнопка под пальцем не нажалась, а внешние вкладки его не взяли.
        var claimed = false
        fun horizontal() = abs(dx) > abs(dy) * (if (takenByList) 2f else 1.5f)
        fun target() = if (dx < 0) index + 1 else index - 1
        fun go(): Boolean {
            val t = target()
            if (t !in 0 until count) return false
            SwipeGesture.handled = down.id
            onSelect(t)
            return true
        }
        while (true) {
            val ev = awaitPointerEvent()
            if (ev.changes.size > 1) return@awaitEachGesture
            val c = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            // Вложенные вкладки уже перелистнули этим жестом.
            if (SwipeGesture.handled == down.id) return@awaitEachGesture
            val d = c.positionChangeIgnoreConsumed()
            dx += d.x; dy += d.y
            velocity.addPosition(c.uptimeMillis, c.position)
            if (!c.pressed) {
                // Отпустили: короткий, но быстрый горизонтальный взмах тоже листает.
                if ((claimed || takenByList) && abs(dx) > flingMin && horizontal() && abs(velocity.calculateVelocity().x) > flingSpeed && go()) c.consume()
                return@awaitEachGesture
            }
            if (c.isConsumed && !claimed && !takenByList) {
                if (abs(dx) >= abs(dy)) return@awaitEachGesture
                takenByList = true
            }
            if (abs(dy) > slop * 4 && abs(dy) > abs(dx)) return@awaitEachGesture
            if (!claimed && !takenByList && abs(dx) > slop && horizontal() && target() in 0 until count) claimed = true
            if (claimed) c.consume()
            if (abs(dx) > threshold && horizontal()) {
                if (!go()) return@awaitEachGesture
                c.consume()
                // Остаток жеста гасим раньше вложенных элементов, чтобы ничего не нажалось и список не ехал.
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    e.changes.forEach { it.consume() }
                    if (e.changes.none { it.pressed }) break
                }
                return@awaitEachGesture
            }
        }
    }
}

/** Какой жест уже перелистнул вкладки — чтобы внешние вкладки не листнули тем же жестом второй раз. */
private object SwipeGesture {
    var handled: PointerId? = null
}

/** То же для вкладок со своими значениями (не 0, 1, 2…): [values] — в порядке на экране. */
fun <T> Modifier.swipeTabsOf(values: List<T>, current: T, onSelect: (T) -> Unit): Modifier =
    swipeTabs(values.indexOf(current).coerceAtLeast(0), values.size) { onSelect(values[it]) }

/**
 * Плавный переход между подразделами: новая вкладка въезжает с той стороны, куда листали, и проявляется.
 * Выключается в «Оформление → Анимация переходов», а сама — в режиме энергосбережения, при заряде 15 % и ниже
 * (если телефон не заряжается) и когда анимации отключены в системе.
 */
object TabAnim {
    private const val KEY = "tab_anim"
    private fun sp() = com.dasein.poryadok.Graph.app.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    /** Рубильник в настройках. */
    val enabled = mutableStateOf(runCatching { sp().getBoolean(KEY, true) }.getOrDefault(true))
    fun setEnabled(v: Boolean) { enabled.value = v; runCatching { sp().edit().putBoolean(KEY, v).apply() } }
    fun reload() { enabled.value = runCatching { sp().getBoolean(KEY, true) }.getOrDefault(true) }

    /** Батарея садится или включено энергосбережение — анимацию не показываем. */
    fun saving(ctx: Context): Boolean = runCatching {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isPowerSaveMode == true) return@runCatching true
        val scale = android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (scale == 0f) return@runCatching true
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return@runCatching false
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = Build.VERSION.SDK_INT >= 23 && bm.isCharging
        level in 1..LOW_BATTERY && !charging
    }.getOrDefault(false)

    const val LOW_BATTERY = 15

    fun active(ctx: Context): Boolean = enabled.value && !saving(ctx)
}

/** Подключается к содержимому вкладок: [index] — номер вкладки по порядку на экране. */
@Composable
fun Modifier.tabSlide(index: Int): Modifier {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val last = remember { intArrayOf(index) }
    val dir = remember { mutableFloatStateOf(0f) }
    val t = remember { mutableFloatStateOf(1f) }
    // Направление и начало перехода ставим сразу при смене вкладки — первый же кадр новой вкладки уже сдвинут.
    if (last[0] != index) {
        dir.floatValue = if (index > last[0]) 1f else -1f
        last[0] = index
        t.floatValue = if (TabAnim.active(ctx)) 0f else 1f
    }
    LaunchedEffect(index) {
        if (t.floatValue < 1f) animate(t.floatValue, 1f, animationSpec = tween(280, easing = FastOutSlowInEasing)) { v, _ -> t.floatValue = v }
    }
    return graphicsLayer {
        val k = 1f - t.floatValue
        translationX = dir.floatValue * k * size.width * 0.3f
        alpha = 1f - k * 0.75f
    }
}
