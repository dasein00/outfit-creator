@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.more

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.system.TodayWidgetReceiver
import com.dasein.poryadok.system.WIDGET_BLOCK_TYPES
import com.dasein.poryadok.system.WRow
import com.dasein.poryadok.system.WidgetConfig
import com.dasein.poryadok.system.WidgetModel
import com.dasein.poryadok.system.WidgetModels
import com.dasein.poryadok.system.WidgetPrefs
import com.dasein.poryadok.system.WidgetRing
import com.dasein.poryadok.system.Widgets
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PPal(val bg: Color, val text: Color, val dim: Color, val track: Color)

private fun ppal(theme: Int) = when (theme) {
    1 -> PPal(Color(0xFFF6F1E7), Color(0xFF211D18), Color(0xFF7A7064), Color(0xFFE2D8C6))
    2 -> PPal(Color(0x99141210), Color(0xFFF0ECE3), Color(0xFFD5CDBF), Color(0x55FFFFFF))
    else -> PPal(Color(0xFF211D18), Color(0xFFF0ECE3), Color(0xFFA79E90), Color(0xFF3D362C))
}

private fun tone(t: Int, p: PPal) = when (t) {
    WidgetModels.TONE_GOOD -> Color(0xFF8CC46E)
    WidgetModels.TONE_BAD -> Color(0xFFD27A63)
    WidgetModels.TONE_ACCENT -> Color(0xFFC79246)
    WidgetModels.TONE_DIM -> p.dim
    else -> p.text
}

/** Настройка виджета на рабочем столе: что показывать, в каком виде и в каком порядке. Предпросмотр обновляется сразу. */
@Composable
fun WidgetEditorScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var cfg by remember { mutableStateOf(WidgetPrefs.load(ctx)) }
    var sizeIdx by remember { mutableStateOf(0) }
    val model by produceState<WidgetModel?>(null, cfg) { value = withContext(Dispatchers.IO) { runCatching { WidgetModels.load(ctx, cfg) }.getOrNull() } }

    fun update(c: WidgetConfig) {
        cfg = c
        WidgetPrefs.save(ctx, c)
        io { Widgets.refresh(ctx) }
    }

    fun setBlock(i: Int, f: (com.dasein.poryadok.system.WidgetBlock) -> com.dasein.poryadok.system.WidgetBlock) {
        update(cfg.copy(blocks = cfg.blocks.toMutableList().also { it[i] = f(it[i]) }))
    }

    Screen("Виджет на рабочем столе", onBack = { nav.popBackStack(); Unit }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Segments(listOf(0 to "4×2", 1 to "4×3", 2 to "2×2"), sizeIdx, { sizeIdx = it })
            Gap(10.dp)
            val (w, h) = when (sizeIdx) { 1 -> 340 to 250; 2 -> 170 to 170; else -> 340 to 170 }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                model?.let { WidgetPreview(it, w, h) }
            }
            Gap(10.dp)
            Hint(
                "widget_help",
                "Изменения сразу применяются к виджету. Чтобы добавить виджет: удерживайте палец на пустом месте рабочего стола → «Виджеты» → DASEIN. Размер меняется, если подержать виджет и потянуть за рамку.",
                title = "Как добавить виджет",
            )
            val mgr = remember { AppWidgetManager.getInstance(ctx) }
            if (android.os.Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported) {
                OutlinedButton(onClick = { mgr.requestPinAppWidget(ComponentName(ctx, TodayWidgetReceiver::class.java), null, null) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Добавить виджет на рабочий стол")
                }
            }

            SectionTitle("Оформление")
            Segments(listOf(0 to "Тёмная", 1 to "Светлая", 2 to "Прозрачная"), cfg.theme, { update(cfg.copy(theme = it)) })
            @Composable
            fun Toggle(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { set(!on) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Medium)
                        Text(sub, fontSize = 12.sp, color = extra.dim)
                    }
                    Switch(on, set)
                }
            }
            Toggle("Заголовок с датой", "«Сегодня», день недели и привычки", cfg.header) { update(cfg.copy(header = it)) }
            Toggle("Кольцо шагов и калорий", "Большое кольцо слева", cfg.ring) { update(cfg.copy(ring = it)) }
            Toggle("Крупный шрифт", "Удобно читать издалека", cfg.large) { update(cfg.copy(large = it)) }

            SectionTitle("Блоки справа — сверху вниз")
            Text("Отметьте, что показывать, выберите вид и порядок. Если блоки не помещаются, лишние скрываются — увеличьте виджет.", fontSize = 12.sp, color = extra.dim)
            Gap(6.dp)
            cfg.blocks.forEachIndexed { i, b ->
                val type = WIDGET_BLOCK_TYPES.firstOrNull { it.type == b.type } ?: return@forEachIndexed
                Tile(Modifier.padding(bottom = 6.dp), padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(b.on, { on -> setBlock(i) { it.copy(on = on) } })
                        Text(type.title, Modifier.weight(1f), fontWeight = FontWeight.Medium, color = if (b.on) MaterialTheme.colorScheme.onSurface else extra.dim)
                        Text("↑", fontSize = 18.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = i > 0) {
                            update(cfg.copy(blocks = cfg.blocks.toMutableList().also { l -> val x = l.removeAt(i); l.add(i - 1, x) }))
                        }.padding(8.dp))
                        Text("↓", fontSize = 18.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = i < cfg.blocks.lastIndex) {
                            update(cfg.copy(blocks = cfg.blocks.toMutableList().also { l -> val x = l.removeAt(i); l.add(i + 1, x) }))
                        }.padding(8.dp))
                    }
                    if (b.on && (type.styles.size > 1 || type.counted)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)) {
                            if (type.styles.size > 1) type.styles.forEachIndexed { si, sname -> Pill(sname, b.style == si) { setBlock(i) { it.copy(style = si) } } }
                            if (type.counted) (1..5).forEach { n -> Pill("$n", b.count == n) { setBlock(i) { it.copy(count = n) } } }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { update(WidgetConfig().normalized()) }, modifier = Modifier.padding(vertical = 12.dp)) { Text("Как было по умолчанию") }
            Gap(24.dp)
        }
    }
}

/** Предпросмотр: те же строки и правила размещения, что у настоящего виджета. */
@Composable
private fun WidgetPreview(m: WidgetModel, wDp: Int, hDp: Int) {
    val ctx = LocalContext.current
    val cfg = m.cfg
    val p = ppal(cfg.theme)
    val k = if (cfg.large) 1.2f else 1f
    val compact = hDp < 140
    val header = cfg.header && !compact
    val avail = hDp - 20 - (if (header) 34 else 0)
    val ringDp = (if (compact) hDp - 20 else hDp - 56).toFloat().coerceAtMost(wDp * .5f).coerceAtLeast(72f)
    val ring = remember(m.steps, m.stepsGoal, m.burned) { WidgetRing.render(ctx, m.steps, m.stepsGoal, m.burned).asImageBitmap() }
    val visible = buildList {
        var used = 0
        for (r in m.rows) {
            val h = WidgetModels.rowHeight(r, cfg.large)
            if (used + h > avail && isNotEmpty()) break
            used += h
            add(r)
        }
    }.take(8)
    Column(
        Modifier.size(wDp.dp, hDp.dp).clip(RoundedCornerShape(20.dp))
            .background(if (cfg.theme == 2) Color(0xFF6E8BA3) else Color.Transparent)
            .background(p.bg).padding(10.dp),
    ) {
        if (header) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Сегодня", color = p.text, fontSize = (19 * k).sp, fontWeight = FontWeight.Bold)
                HGap(8.dp)
                Text(m.date, color = p.dim, fontSize = (14 * k).sp, modifier = Modifier.weight(1f), maxLines = 1)
                if (m.habitsText != null && visible.none { it.icon == "sport/24" }) Text(m.habitsText, color = Color(0xFFC79246), fontSize = (14 * k).sp, fontWeight = FontWeight.Bold)
            }
            Gap(6.dp)
        }
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (cfg.ring) {
                Image(ring, null, Modifier.size(ringDp.dp))
                HGap(10.dp)
            }
            Column(Modifier.weight(1f)) {
                if (visible.isEmpty()) Text("Выберите блоки ниже", color = p.dim, fontSize = (14 * k).sp)
                visible.forEach { r -> PreviewRow(r, p, k) }
            }
        }
    }
}

@Composable
private fun PreviewRow(r: WRow, p: PPal, k: Float) {
    if (r.kind == 3) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("○", color = Color(0xFFC79246), fontSize = (18 * k).sp, fontWeight = FontWeight.Bold)
            HGap(6.dp)
            Text(r.title, Modifier.weight(1f), color = tone(r.tone, p), fontSize = (16 * k).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.value.isNotEmpty()) Text(" " + r.value, color = p.dim, fontSize = (14 * k).sp)
        }
        return
    }
    val hasIcon = r.icon != null && !r.icon.startsWith("ui:")
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasIcon) { Glyph(r.icon!!, (22 * k).dp, badge = false); HGap(6.dp) }
            if (r.value.isBlank()) Text(r.title, color = tone(r.tone, p), fontSize = (15 * k).sp, fontWeight = FontWeight.Medium, maxLines = 1)
            else {
                if (!hasIcon) Text(r.title + " ", color = p.dim, fontSize = (14 * k).sp, maxLines = 1)
                Text(r.value, color = p.text, fontSize = (18 * k).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            if (r.sub.isNotBlank()) {
                HGap(6.dp)
                Text(r.sub, color = if (r.tone == WidgetModels.TONE_TEXT) p.dim else tone(r.tone, p), fontSize = (13 * k).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        if (r.kind == 1 && r.progress != null) {
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { r.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (r.progress >= 1f) Color(0xFF8CC46E) else Color(0xFFC79246), trackColor = p.track,
            )
        }
        if (r.kind == 2 && r.spark.size >= 2) {
            val spark = remember(r.spark) { WidgetRing.spark(r.spark).asImageBitmap() }
            Image(spark, null, Modifier.fillMaxWidth().height(34.dp))
        }
    }
}
