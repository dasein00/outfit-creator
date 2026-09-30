@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.more

import android.appwidget.AppWidgetManager
import com.dasein.poryadok.ui.common.HowTo
import android.content.ComponentName
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dasein.poryadok.system.WColors
import com.dasein.poryadok.system.WIDGET_BLOCK_TYPES
import com.dasein.poryadok.system.WRow
import com.dasein.poryadok.system.WidgetBlock
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
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.weather.WeatherIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Цвета на выбор для текста и фона виджета. */
private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFFF0ECE3, 0xFFD5CDBF, 0xFFA79E90, 0xFF7A7064, 0xFF3D362C, 0xFF211D18, 0xFF000000,
    0xFFE0A04A, 0xFFC79246, 0xFFFFA24C, 0xFFE06A4B, 0xFFD27A63, 0xFFE06A9A, 0xFFB06FC4, 0xFF7C6FE0,
    0xFF5B8DD2, 0xFF4FB3D9, 0xFF4BA38C, 0xFF8CC46E, 0xFFC7D95B, 0xFFE0B040,
)

/** Настройка виджета на рабочем столе: что показывать, в каком виде, цвета, подписи и размеры. Предпросмотр обновляется сразу. */
@Composable
fun WidgetEditorScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var cfg by remember { mutableStateOf(WidgetPrefs.load(ctx)) }
    var sizeIdx by remember { mutableStateOf(0) }
    var editBlock by remember { mutableStateOf<Int?>(null) }
    val model by produceState<WidgetModel?>(null, cfg) { value = withContext(Dispatchers.IO) { runCatching { WidgetModels.load(ctx, cfg) }.getOrNull() } }

    fun update(c: WidgetConfig) {
        cfg = c
        WidgetPrefs.save(ctx, c)
        io { Widgets.refresh(ctx) }
    }

    fun setBlock(i: Int, f: (WidgetBlock) -> WidgetBlock) {
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

            SectionTitle("Тема")
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

            SectionTitle("Цвета")
            val pal = cfg.colors()
            ColorRow("Фон", cfg.bgColor, pal.bg) { update(cfg.copy(bgColor = it)) }
            SliderRow("Непрозрачность фона", cfg.bgAlpha.toFloat(), 0f..100f, "${cfg.bgAlpha}%") { update(cfg.copy(bgAlpha = it.roundToInt())) }
            ColorRow("Основной текст", cfg.textColor, pal.text) { update(cfg.copy(textColor = it)) }
            ColorRow("Подписи и второстепенный текст", cfg.dimColor, pal.dim) { update(cfg.copy(dimColor = it)) }
            ColorRow("Акцент (полосы, отметки)", cfg.accentColor, pal.accent) { update(cfg.copy(accentColor = it)) }
            ColorRow("«Хорошо» (цель выполнена)", cfg.goodColor, pal.good) { update(cfg.copy(goodColor = it)) }
            ColorRow("«Плохо» (перебор, просрочено)", cfg.badColor, pal.bad) { update(cfg.copy(badColor = it)) }
            SliderRow("Скругление углов", cfg.radius.toFloat(), 0f..32f, "${cfg.radius} dp") { update(cfg.copy(radius = it.roundToInt())) }

            SectionTitle("Текст")
            Toggle("Крупный шрифт", "Удобно читать издалека", cfg.large) { update(cfg.copy(large = it)) }
            SliderRow("Размер всего текста", cfg.textScale, 0.7f..1.6f, "${(cfg.textScale * 100).roundToInt()}%") { update(cfg.copy(textScale = it)) }

            SectionTitle("Заголовок")
            Toggle("Показывать заголовок", "Надпись, дата и привычки сверху", cfg.header) { update(cfg.copy(header = it)) }
            if (cfg.header) {
                TextInput(cfg.title, { update(cfg.copy(title = it.take(24))) }, "Текст заголовка (можно оставить пустым)")
                Toggle("Дата", "День недели и число рядом с заголовком", cfg.showDate) { update(cfg.copy(showDate = it)) }
                SliderRow("Размер заголовка", cfg.titleScale, 0.7f..1.6f, "${(cfg.titleScale * 100).roundToInt()}%") { update(cfg.copy(titleScale = it)) }
            }

            SectionTitle("Кольцо шагов и калорий")
            Toggle("Показывать кольцо", "Большое кольцо слева", cfg.ring) { update(cfg.copy(ring = it)) }
            if (cfg.ring) {
                val rs = cfg.ringStyle()
                SliderRow("Размер кольца", cfg.ringScale, 0.5f..1.5f, "${(cfg.ringScale * 100).roundToInt()}%") { update(cfg.copy(ringScale = it)) }
                ColorRow("Полоса прогресса", cfg.ringColor, rs.progress) { update(cfg.copy(ringColor = it)) }
                ColorRow("Полоса, когда цель выполнена", cfg.ringDoneColor, rs.done) { update(cfg.copy(ringDoneColor = it)) }
                ColorRow("Фон кольца", cfg.ringTrackColor, rs.track) { update(cfg.copy(ringTrackColor = it)) }
                ColorRow("Число шагов", cfg.ringStepsColor, rs.steps) { update(cfg.copy(ringStepsColor = it)) }
                ColorRow("Число калорий", cfg.ringKcalColor, rs.kcal) { update(cfg.copy(ringKcalColor = it)) }
                Toggle("Цель шагов", "Строка «из 8000» под числом шагов", cfg.ringGoal) { update(cfg.copy(ringGoal = it)) }
                TextInput(cfg.kcalLabel, { update(cfg.copy(kcalLabel = it.take(12))) }, "Подпись под калориями")
            }

            SectionTitle("Блоки справа — сверху вниз")
            Text(
                "Отметьте, что показывать, выберите вид и порядок. «Настроить» — своя подпись, цвета, размер и иконка блока. " +
                    "Если блоки не помещаются, лишние скрываются — увеличьте виджет.",
                fontSize = 12.sp, color = extra.dim,
            )
            Gap(6.dp)
            cfg.blocks.forEachIndexed { i, b ->
                val type = WIDGET_BLOCK_TYPES.firstOrNull { it.type == b.type } ?: return@forEachIndexed
                Tile(Modifier.padding(bottom = 6.dp), padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(b.on, { on -> setBlock(i) { it.copy(on = on) } })
                        Column(Modifier.weight(1f)) {
                            Text(type.title, fontWeight = FontWeight.Medium, color = if (b.on) MaterialTheme.colorScheme.onSurface else extra.dim)
                            if (b.label.isNotBlank()) Text("подпись: «${b.label}»", fontSize = 11.sp, color = extra.dim)
                        }
                        Text("↑", fontSize = 18.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = i > 0) {
                            update(cfg.copy(blocks = cfg.blocks.toMutableList().also { l -> val x = l.removeAt(i); l.add(i - 1, x) }))
                        }.padding(8.dp))
                        Text("↓", fontSize = 18.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = i < cfg.blocks.lastIndex) {
                            update(cfg.copy(blocks = cfg.blocks.toMutableList().also { l -> val x = l.removeAt(i); l.add(i + 1, x) }))
                        }.padding(8.dp))
                    }
                    if (b.on) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)) {
                            if (type.styles.size > 1) type.styles.forEachIndexed { si, sname -> Pill(sname, b.style == si) { setBlock(i) { it.copy(style = si) } } }
                            if (type.counted) (1..5).forEach { n -> Pill("$n", b.count == n) { setBlock(i) { it.copy(count = n) } } }
                            Pill("Настроить…", false) { editBlock = i }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { update(WidgetConfig().normalized()) }, modifier = Modifier.padding(vertical = 12.dp)) { Text("Как было по умолчанию") }
            HowTo("widget")
            Gap(24.dp)
        }
    }

    editBlock?.let { i ->
        val b = cfg.blocks.getOrNull(i) ?: return@let
        val type = WIDGET_BLOCK_TYPES.firstOrNull { it.type == b.type }
        val pal = cfg.colors()
        AlertDialog(
            onDismissRequest = { editBlock = null },
            title = { Text(type?.title ?: "Блок") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextInput(b.label, { v -> setBlock(i) { it.copy(label = v.take(20)) } }, "Своя подпись (пусто — «${type?.title ?: ""}»)")
                    Gap(8.dp)
                    ColorRow("Цвет значения", b.valueColor, pal.text) { c -> setBlock(i) { it.copy(valueColor = c) } }
                    ColorRow("Цвет подписи", b.labelColor, pal.dim) { c -> setBlock(i) { it.copy(labelColor = c) } }
                    SliderRow("Размер текста", b.scale, 0.6f..1.8f, "${(b.scale * 100).roundToInt()}%") { v -> setBlock(i) { it.copy(scale = v) } }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Иконка", Modifier.weight(1f))
                        Switch(b.icon, { on -> setBlock(i) { it.copy(icon = on) } })
                    }
                    TextButton(onClick = { setBlock(i) { WidgetBlock(it.type, it.style, it.count, it.on) } }) { Text("Сбросить оформление блока") }
                }
            },
            confirmButton = { TextButton(onClick = { editBlock = null }) { Text("Готово") } },
        )
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    val extra = LocalExtra.current
    var v by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 2.dp)) {
        Row {
            Text(title, Modifier.weight(1f), fontSize = 14.sp)
            Text(valueText, fontSize = 13.sp, color = extra.dim)
        }
        Slider(v, { v = it }, valueRange = range, onValueChangeFinished = { onChange(v) })
    }
}

/** Выбор цвета: «Авто» (как в теме), готовые цвета и свой в формате #RRGGBB. */
@Composable
private fun ColorRow(title: String, value: Long, effective: Int, onChange: (Long) -> Unit) {
    val extra = LocalExtra.current
    var custom by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(Color(effective)).border(1.dp, extra.dim, CircleShape))
            HGap(8.dp)
            Text(title, Modifier.weight(1f), fontSize = 14.sp)
            Text(if (value == 0L) "авто" else "#%06X".format(value.toInt() and 0xFFFFFF), fontSize = 12.sp, color = extra.dim)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            Text(
                "Авто", fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(if (value == 0L) 2.dp else 1.dp, if (value == 0L) MaterialTheme.colorScheme.primary else extra.dim, RoundedCornerShape(12.dp))
                    .clickable { onChange(0) }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            SWATCHES.forEach { c ->
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color(c.toInt()))
                        .border(if (value == c) 3.dp else 1.dp, if (value == c) MaterialTheme.colorScheme.primary else extra.dim.copy(alpha = .5f), CircleShape)
                        .clickable { onChange(c) },
                )
            }
            Text(
                "Свой…", fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, extra.dim, RoundedCornerShape(12.dp)).clickable { custom = true }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    if (custom) {
        var hex by remember { mutableStateOf(if (value == 0L) "" else "%06X".format(value.toInt() and 0xFFFFFF)) }
        val parsed = hex.trim().removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000L or it }
        AlertDialog(
            onDismissRequest = { custom = false },
            title = { Text(title) },
            text = {
                Column {
                    TextInput(hex, { hex = it.take(7) }, "Цвет в формате RRGGBB, например E0A04A")
                    Gap(8.dp)
                    Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(parsed?.let { Color(it.toInt()) } ?: Color.Transparent).border(1.dp, extra.dim, RoundedCornerShape(10.dp)))
                }
            },
            confirmButton = { TextButton(enabled = parsed != null, onClick = { parsed?.let(onChange); custom = false }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Отмена") } },
        )
    }
}

/** Предпросмотр: те же строки, цвета и правила размещения, что у настоящего виджета. */
@Composable
private fun WidgetPreview(m: WidgetModel, wDp: Int, hDp: Int) {
    val ctx = LocalContext.current
    val cfg = m.cfg
    val p = cfg.colors()
    val k = cfg.k()
    val compact = hDp < 140
    val header = cfg.header && !compact
    val avail = hDp - 20 - (if (header) 34 * cfg.titleScale else 0f)
    val ringDp = ((if (compact) hDp - 20 else hDp - 56) * cfg.ringScale.coerceIn(0.5f, 1.5f)).coerceAtMost(wDp * .5f).coerceAtLeast(56f)
    val ring = remember(m.steps, m.stepsGoal, m.burned, cfg) { WidgetRing.render(ctx, m.steps, m.stepsGoal, m.burned, cfg.ringStyle()).asImageBitmap() }
    val visible = buildList {
        var used = 0
        for (r in m.rows) {
            val h = WidgetModels.rowHeight(r, cfg)
            if (used + h > avail && isNotEmpty()) break
            used += h
            add(r)
        }
    }.take(8)
    val shape = RoundedCornerShape(cfg.radius.coerceIn(0, 32).dp)
    Column(
        Modifier.size(wDp.dp, hDp.dp).clip(shape)
            .background(Color(0xFF6E8BA3))
            .background(Color(p.bg)).padding(10.dp),
    ) {
        if (header) {
            val t = k * cfg.titleScale
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (cfg.title.isNotBlank()) {
                    Text(cfg.title, color = Color(p.text), fontSize = (19 * t).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    HGap(8.dp)
                }
                Text(if (cfg.showDate) m.date else "", color = Color(p.dim), fontSize = (14 * t).sp, modifier = Modifier.weight(1f), maxLines = 1)
                if (m.habitsText != null && visible.none { it.icon == "sport/24" }) Text(m.habitsText, color = Color(p.accent), fontSize = (14 * t).sp, fontWeight = FontWeight.Bold)
            }
            Gap(6.dp)
        }
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (cfg.ring) {
                Image(ring, null, Modifier.size(ringDp.dp))
                HGap(10.dp)
            }
            Column(Modifier.weight(1f)) {
                if (visible.isEmpty()) Text("Выберите блоки ниже", color = Color(p.dim), fontSize = (14 * k).sp)
                visible.forEach { r -> PreviewRow(r, p, k) }
            }
        }
    }
}

@Composable
private fun PreviewRow(r: WRow, p: WColors, k0: Float) {
    val b = r.block
    val k = k0 * (b?.scale ?: 1f)
    val valueColor = Color(b?.valueColor?.takeIf { it != 0L }?.toInt() ?: p.text)
    val labelColor = Color(b?.labelColor?.takeIf { it != 0L }?.toInt() ?: p.dim)
    if (r.kind == 3) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("○", color = Color(p.accent), fontSize = (18 * k).sp, fontWeight = FontWeight.Bold)
            HGap(6.dp)
            Text(
                r.title, Modifier.weight(1f), color = if (r.tone == WidgetModels.TONE_TEXT) valueColor else Color(p.tone(r.tone)),
                fontSize = (16 * k).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (r.value.isNotEmpty()) Text(" " + r.value, color = labelColor, fontSize = (14 * k).sp)
        }
        return
    }
    val icon = r.icon
    val hasIcon = icon != null && !icon.startsWith("ui:") && (b?.icon ?: true)
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasIcon) {
                if (icon!!.startsWith("wx:")) icon.split(':').let { WeatherIcon(it.getOrNull(1)?.toIntOrNull() ?: 2, it.getOrNull(2) != "0", (22 * k).dp) }
                else Glyph(icon, (22 * k).dp, badge = false)
                HGap(6.dp)
            }
            if (r.value.isBlank()) Text(
                r.title, color = if (r.tone == WidgetModels.TONE_TEXT || (b != null && b.valueColor != 0L)) valueColor else Color(p.tone(r.tone)),
                fontSize = (15 * k).sp, fontWeight = FontWeight.Medium, maxLines = 1,
            )
            else {
                if (!hasIcon || b?.label?.isNotBlank() == true) Text(r.title + " ", color = labelColor, fontSize = (14 * k).sp, maxLines = 1)
                Text(r.value, color = valueColor, fontSize = (18 * k).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            if (r.sub.isNotBlank()) {
                HGap(6.dp)
                Text(
                    r.sub, color = if (r.tone == WidgetModels.TONE_TEXT || r.tone == WidgetModels.TONE_DIM) labelColor else Color(p.tone(r.tone)),
                    fontSize = (13 * k).sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
            }
        }
        if (r.kind == 1 && r.progress != null) {
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { r.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color(if (r.progress >= 1f) p.good else p.accent), trackColor = Color(p.track),
            )
        }
        if (r.kind == 2 && r.spark.size >= 2) {
            val spark = remember(r.spark) { WidgetRing.spark(r.spark).asImageBitmap() }
            Image(spark, null, Modifier.fillMaxWidth().height(34.dp))
        }
    }
}
