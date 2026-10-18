package com.dasein.poryadok.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Свайп одним пальцем влево/вправо — соседняя вкладка (подраздел). Жест срабатывает, только если его не взял
 * вложенный элемент (лента карточек, ползунок, график): тогда прокручивается он. Вертикальная прокрутка не мешает.
 * На крайней вкладке жест уходит дальше — во внешние вкладки (например, «Календарь → Задачи»).
 */
fun Modifier.swipeTabs(index: Int, count: Int, onSelect: (Int) -> Unit): Modifier = pointerInput(index, count) {
    val threshold = 72.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dx = 0f
        var dy = 0f
        while (true) {
            val ev = awaitPointerEvent()
            if (ev.changes.size > 1) return@awaitEachGesture
            val c = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!c.pressed) return@awaitEachGesture
            if (c.isConsumed) return@awaitEachGesture
            val d = c.positionChange()
            dx += d.x; dy += d.y
            if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) return@awaitEachGesture
            if (abs(dx) > threshold && abs(dx) > abs(dy) * 1.5f) {
                val target = if (dx < 0) index + 1 else index - 1
                if (target !in 0 until count) return@awaitEachGesture
                c.consume()
                onSelect(target)
                // Остаток жеста гасим, чтобы не нажалось то, что под пальцем.
                while (true) {
                    val e = awaitPointerEvent()
                    e.changes.forEach { it.consume() }
                    if (e.changes.none { it.pressed }) break
                }
                return@awaitEachGesture
            }
        }
    }
}

/** То же для вкладок со своими значениями (не 0, 1, 2…): [values] — в порядке на экране. */
fun <T> Modifier.swipeTabsOf(values: List<T>, current: T, onSelect: (T) -> Unit): Modifier =
    swipeTabs(values.indexOf(current).coerceAtLeast(0), values.size) { onSelect(values[it]) }
