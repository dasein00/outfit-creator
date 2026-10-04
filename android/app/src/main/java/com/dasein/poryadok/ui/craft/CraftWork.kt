package com.dasein.poryadok.ui.craft

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.logic.CraftPattern
import com.dasein.poryadok.system.CraftStore
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Работа по схеме, как в Pattern Keeper: схема с символами во весь экран, приближение двумя пальцами,
 * подсветка одного цвета (остальные бледнеют), отметка готовых клеток касанием или проведением пальца,
 * «Отметить весь цвет», прогресс по каждому цвету и общий. Отметки сохраняются.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun CraftWorkScreen(nav: NavHostController, id: Long) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val project = remember { CraftStore.load(ctx, id) }
    var pattern by remember { mutableStateOf<CraftPattern.Pattern?>(null) }
    var done by remember { mutableStateOf<BooleanArray?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var markMode by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val p = project ?: return@LaunchedEffect
        val pat = CraftStore.pattern(ctx, p) ?: return@LaunchedEffect
        done = CraftStore.loadDone(ctx, p, pat)
        pattern = pat
    }
    // Сохраняем отметки через полсекунды после последнего изменения.
    LaunchedEffect(version) {
        if (version == 0) return@LaunchedEffect
        delay(500)
        val p = CraftStore.load(ctx, id) ?: return@LaunchedEffect
        val pat = pattern ?: return@LaunchedEffect
        done?.let { CraftStore.saveDone(ctx, p, pat, it) }
    }
    Screen(project?.name ?: "Схема", onBack = { nav.popBackStack() }) { pad ->
        val pat = pattern; val d = done
        if (pat == null || d == null || project == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Screen
        }
        val counts = remember(pat) { pat.counts() }
        val doneBy = remember(pat, version) { IntArray(pat.colors.size).also { c -> pat.cells.forEachIndexed { i, v -> if (v >= 0 && d[i]) c[v]++ } } }
        val total = pat.filled
        val doneTotal = doneBy.sum()
        Column(Modifier.padding(pad).consumeWindowInsets(pad).navigationBarsPadding().padding(bottom = 8.dp).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Готово ${doneTotal} из $total · ${if (total > 0) doneTotal * 100 / total else 0} %", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Pill(if (markMode) "✓ Отмечаю" else "Отмечать", markMode) { markMode = !markMode }
                }
                Bar(if (total > 0) doneTotal.toFloat() / total else 0f, extra.ok, Modifier.padding(vertical = 4.dp))
                Text(
                    if (markMode) "Касание или проведение пальцем отмечает клетки" + (selected?.let { " цвета «${CraftPattern.symbol(it)}»" } ?: "") + ". Двумя пальцами — двигать и приближать."
                    else "Касание клетки подсвечивает её цвет. Двумя пальцами или одним — двигать, щипок — приблизить.",
                    fontSize = 11.sp, color = extra.dim,
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp).background(Color(0xFFE9E4D8))) {
                val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
                val fit = minOf(wPx / pat.width, hPx / pat.height)
                var scale by remember(pat) { mutableFloatStateOf(1f) }
                var ox by remember(pat) { mutableFloatStateOf((wPx - fit * pat.width) / 2) }
                var oy by remember(pat) { mutableFloatStateOf((hPx - fit * pat.height) / 2) }
                val cell = fit * scale
                fun cellAt(p: Offset): Int? {
                    val x = ((p.x - ox) / cell).toInt(); val y = ((p.y - oy) / cell).toInt()
                    if (p.x < ox || p.y < oy || x !in 0 until pat.width || y !in 0 until pat.height) return null
                    return y * pat.width + x
                }
                fun mark(i: Int, value: Boolean) {
                    val c = pat.cells[i]
                    if (c < 0 || (selected != null && c != selected)) return
                    if (d[i] != value) { d[i] = value; version++ }
                }
                val symPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD } }
                Canvas(
                    Modifier.fillMaxSize().pointerInput(pat, markMode, selected) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var moved = false
                            var multi = false
                            var paintValue: Boolean? = null
                            val startCell = cellAt(down.position)
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = ev.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    multi = true
                                    val a = pressed[0]; val b = pressed[1]
                                    val prevDist = (a.previousPosition - b.previousPosition).getDistance()
                                    val dist = (a.position - b.position).getDistance()
                                    val center = (a.position + b.position) / 2f
                                    val prevCenter = (a.previousPosition + b.previousPosition) / 2f
                                    if (prevDist > 0f) {
                                        val ns = (scale * dist / prevDist).coerceIn(0.5f, 12f)
                                        val k = ns / scale
                                        ox = center.x - (center.x - ox) * k + (center.x - prevCenter.x)
                                        oy = center.y - (center.y - oy) * k + (center.y - prevCenter.y)
                                        scale = ns
                                    }
                                    pressed.forEach { if (it.positionChanged()) it.consume() }
                                } else if (!multi) {
                                    val ch = pressed[0]
                                    val delta = ch.position - ch.previousPosition
                                    if ((ch.position - down.position).getDistance() > 12f) moved = true
                                    if (markMode && moved) {
                                        // Проведение пальцем — отмечаем клетки по пути (значение — обратное первой клетке).
                                        val i = cellAt(ch.position)
                                        if (i != null) {
                                            if (paintValue == null) paintValue = startCell?.let { !d[it] } ?: true
                                            mark(i, paintValue!!)
                                        }
                                    } else if (!markMode && moved) {
                                        ox += delta.x; oy += delta.y
                                    }
                                    if (ch.positionChanged()) ch.consume()
                                }
                            }
                            if (!moved && !multi && startCell != null) {
                                if (markMode) mark(startCell, !d[startCell])
                                else { val c = pat.cells[startCell]; selected = if (c < 0 || selected == c) null else c }
                            }
                        }
                    },
                ) {
                    @Suppress("UNUSED_VARIABLE") val redraw = version // перерисовать после отметок
                    val x0 = ((-ox) / cell).toInt().coerceAtLeast(0); val x1 = ((size.width - ox) / cell).toInt().coerceAtMost(pat.width - 1)
                    val y0 = ((-oy) / cell).toInt().coerceAtLeast(0); val y1 = ((size.height - oy) / cell).toInt().coerceAtMost(pat.height - 1)
                    val sel = selected
                    val showSym = cell >= 11f
                    symPaint.textSize = cell * .66f
                    drawIntoCanvas { cv ->
                        val nc = cv.nativeCanvas
                        for (y in y0..y1) for (x in x0..x1) {
                            val i = y * pat.width + x
                            val c = pat.cells[i]
                            val l = ox + x * cell; val t = oy + y * cell
                            if (c < 0) continue
                            val rgb = pat.colors[c].rgb
                            val dim = sel != null && c != sel
                            val fillColor = when {
                                d[i] -> Color(rgb or (0xFF shl 24)).copy(alpha = if (dim) .25f else 1f)
                                dim -> Color(0xFFF7F4EE)
                                else -> Color(CraftStore.tint(rgb, if (sel != null) .7f else .45f))
                            }
                            drawRect(fillColor, Offset(l, t), Size(cell, cell))
                            if (showSym && !(dim && cell < 18f)) {
                                symPaint.color = when {
                                    d[i] -> if (CraftPattern.symbolDark(rgb)) 0x66000000 else 0x88FFFFFF.toInt()
                                    dim -> 0x33000000
                                    else -> 0xFF000000.toInt()
                                }
                                nc.drawText(CraftPattern.symbol(c), l + cell / 2, t + cell * .74f, symPaint)
                            }
                        }
                    }
                    if (cell >= 6f) {
                        for (x in x0..x1 + 1) {
                            val thick = x % 10 == 0
                            drawLine(if (thick) Color(0xCC000000) else Color(0x33000000), Offset(ox + x * cell, oy + y0 * cell), Offset(ox + x * cell, oy + (y1 + 1) * cell), if (thick) 2f else 1f)
                        }
                        for (y in y0..y1 + 1) {
                            val thick = y % 10 == 0
                            drawLine(if (thick) Color(0xCC000000) else Color(0x33000000), Offset(ox + x0 * cell, oy + y * cell), Offset(ox + (x1 + 1) * cell, oy + y * cell), if (thick) 2f else 1f)
                        }
                    }
                }
            }
            // Цвета: символ и сколько осталось. Касание — подсветить цвет.
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(pat.colors.size) { i ->
                    val t = pat.colors[i]
                    val on = selected == i
                    Column(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else extra.card)
                            .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { selected = if (on) null else i }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ColorChip(t, i, 30)
                        Text(t.code, fontSize = 10.sp)
                        Text(if (doneBy[i] >= counts[i]) "✓" else "${counts[i] - doneBy[i]}", fontSize = 10.sp, color = if (doneBy[i] >= counts[i]) extra.ok else extra.dim)
                    }
                }
            }
            selected?.let { s ->
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("DMC ${pat.colors[s].code} · ${pat.colors[s].name}: ${doneBy[s]} из ${counts[s]}", Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = { pat.cells.forEachIndexed { i, c -> if (c == s) d[i] = true }; version++ }) { Text("Весь цвет ✓", fontSize = 12.sp) }
                    TextButton(onClick = { pat.cells.forEachIndexed { i, c -> if (c == s) d[i] = false }; version++ }) { Text("Снять", fontSize = 12.sp) }
                }
            }
        }
    }
}
