package com.dasein.poryadok.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Look
import com.dasein.poryadok.ui.theme.LookSettings
import com.dasein.poryadok.ui.theme.Palette
import kotlin.math.roundToInt

const val APPEARANCE_ROUTE = "appearance"

/** Что выбираем в палитре: акцент, фон, текст или цвет иконок. */
private enum class Pick { ACCENT, BG, TEXT, ICON }

/**
 * «Оформление»: тема, акцент (16 цветов и свой), фон, цвет текста, контрастный режим, иконки, шрифт, размер текста,
 * скругление карточек. Всё меняется сразу, сверху — живой пример.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(nav: NavHostController, settings: Settings) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val look = Look.state.value
    fun set(f: (LookSettings) -> LookSettings) = Look.save(ctx, f(Look.state.value))
    var pick by remember { mutableStateOf<Pick?>(null) }

    Screen("Оформление", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Preview()

            SectionTitle("Тема")
            Segments(listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная"), settings.theme, { v -> io { Graph.prefs.update { it.copy(theme = v) } } })
            if (look.bgRgb != 0) Text("Сейчас выбран свой фон — светлая или тёмная тема определяется по нему.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))

            SectionTitle("Цвет акцента")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.accents.forEachIndexed { i, (label, c) ->
                    Swatch(c, label, look.accentRgb == 0 && settings.accent == i) {
                        set { it.copy(accentRgb = 0) }
                        io { Graph.prefs.update { s -> s.copy(accent = i) } }
                    }
                }
                Swatch(if (look.accentRgb != 0) Look.color(look.accentRgb) else null, "Свой", look.accentRgb != 0) { pick = Pick.ACCENT }
            }

            SectionTitle("Фон")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatch(null, "По теме", look.bgRgb == 0, auto = true) { set { it.copy(bgRgb = 0) } }
                Look.BACKGROUNDS.forEach { (label, rgb) -> Swatch(Look.color(rgb), label, look.bgRgb == rgb) { set { it.copy(bgRgb = rgb) } } }
                Swatch(null, "Свой", look.bgRgb != 0 && Look.BACKGROUNDS.none { it.second == look.bgRgb }) { pick = Pick.BG }
            }

            SectionTitle("Цвет текста")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatch(null, "Авто", look.textRgb == 0, auto = true) { set { it.copy(textRgb = 0) } }
                Look.TEXTS.forEach { (label, rgb) -> Swatch(Look.color(rgb), label, look.textRgb == rgb) { set { it.copy(textRgb = rgb) } } }
                Swatch(null, "Свой", look.textRgb != 0 && Look.TEXTS.none { it.second == look.textRgb }) { pick = Pick.TEXT }
            }
            val bg = MaterialTheme.colorScheme.background
            val fg = MaterialTheme.colorScheme.onBackground
            val ratio = Look.contrast(bg, fg)
            Text(
                "Контраст текста и фона: ${"%.1f".format(ratio)} : 1 — " + when {
                    ratio >= 7 -> "отлично"
                    ratio >= 4.5 -> "хорошо читается"
                    else -> "плохо читается, выберите цвет темнее или светлее"
                },
                fontSize = 12.sp, color = if (ratio >= 4.5) extra.ok else extra.danger, modifier = Modifier.padding(top = 6.dp),
            )

            SectionTitle("Контрастный режим")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(btnLabel("Максимальный контраст"), fontWeight = FontWeight.SemiBold)
                    Text(
                        "Чисто белый или чёрный текст, чёткие рамки, яркие подписи. Вместе с «контрастными» иконками — всё хорошо видно на любом фоне.",
                        fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
                    )
                }
                Switch(look.contrast, { on -> set { it.copy(contrast = on, icons = if (on && it.icons == 0) 1 else it.icons) } })
            }

            SectionTitle("Иконки")
            Look.ICON_MODES.forEachIndexed { i, label ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (i == 3 && look.iconRgb == 0) pick = Pick.ICON; set { it.copy(icons = i) } }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(look.icons == i, { if (i == 3 && look.iconRgb == 0) pick = Pick.ICON; set { it.copy(icons = i) } })
                    Text(label, Modifier.weight(1f))
                    if (i == 3) Box(Modifier.size(22.dp).clip(CircleShape).background(if (look.iconRgb != 0) Look.color(look.iconRgb) else extra.line).clickable { pick = Pick.ICON })
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("d01/00", "d12/00", "d01/02", "d06/00", "tutor/18", "ui:home").forEach { Glyph(it, 30.dp, badge = false) }
            }

            SectionTitle("Шрифт")
            Look.FONTS.forEach { (label, id) ->
                val family = when (id) { 1 -> FontFamily.SansSerif; 2 -> FontFamily.Serif; 3 -> FontFamily.Monospace; 4 -> FontFamily.Cursive; else -> FontFamily.Serif }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { set { it.copy(font = id) } }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(look.font == id, { set { it.copy(font = id) } })
                    Column {
                        Text("Доброе утро", fontFamily = family, fontSize = 20.sp)
                        Text(label, fontSize = 12.sp, color = extra.dim)
                    }
                }
            }

            SectionTitle("Размер текста")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A", fontSize = 13.sp)
                Slider(look.scale, { v -> set { it.copy(scale = (v * 20).roundToInt() / 20f) } }, valueRange = .8f..1.5f, steps = 13, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text("A", fontSize = 22.sp)
            }
            Text("${(look.scale * 100).roundToInt()}% от системного размера", fontSize = 12.sp, color = extra.dim)

            SectionTitle("Скругление карточек")
            Slider(look.cardRound.toFloat(), { v -> set { it.copy(cardRound = v.roundToInt()) } }, valueRange = 4f..32f)
            Text("${look.cardRound} dp", fontSize = 12.sp, color = extra.dim)

            Gap(16.dp)
            OutlinedButton(onClick = { set { LookSettings() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(btnLabel("Вернуть оформление по умолчанию")) }
            Text(
                "Все эти настройки сохраняются в «Файл оформления» и в резервную копию — на новом телефоне всё будет так же.",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp),
            )
            Gap(40.dp)
        }
    }

    pick?.let { p ->
        val start = when (p) {
            Pick.ACCENT -> look.accentRgb.takeIf { it != 0 } ?: (MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF)
            Pick.BG -> look.bgRgb.takeIf { it != 0 } ?: (MaterialTheme.colorScheme.background.toArgb() and 0xFFFFFF)
            Pick.TEXT -> look.textRgb.takeIf { it != 0 } ?: (MaterialTheme.colorScheme.onBackground.toArgb() and 0xFFFFFF)
            Pick.ICON -> look.iconRgb.takeIf { it != 0 } ?: (MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF)
        }
        ColorPickerDialog(start, onDismiss = { pick = null }) { rgb ->
            // 0 значит «по умолчанию», поэтому чистый чёрный храним как почти чёрный.
            val v = (rgb and 0xFFFFFF).let { if (it == 0) 0x010101 else it }
            set {
                when (p) {
                    Pick.ACCENT -> it.copy(accentRgb = v)
                    Pick.BG -> it.copy(bgRgb = v)
                    Pick.TEXT -> it.copy(textRgb = v)
                    Pick.ICON -> it.copy(iconRgb = v, icons = 3)
                }
            }
            pick = null
        }
    }
}

/** Живой пример: заголовок, текст, кнопки и иконки в текущем оформлении. */
@Composable
private fun Preview() {
    val extra = LocalExtra.current
    Tile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("d01/00", 30.dp, badge = false)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Доброе утро", style = MaterialTheme.typography.titleLarge)
                Text("Так будет выглядеть приложение", fontSize = 13.sp, color = extra.dim)
            }
        }
        Text(
            "Обычный текст карточки: задачи, рецепты, заметки. Подписи и пояснения — приглушённым цветом.",
            fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Кнопка") }
            OutlinedButton(onClick = {}) { Text("Вторая") }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("d12/00", "d01/02", "d06/00", "d17/03").forEach { Glyph(it, 26.dp, badge = false) }
        }
    }
}

/** Кружок цвета с подписью. [color] null — «авто/свой» (рисуется рамкой). */
@Composable
private fun Swatch(color: Color?, label: String, selected: Boolean, auto: Boolean = false, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .background(color ?: Color.Transparent)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else extra.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) Text(if (auto) "A" else "+", fontWeight = FontWeight.Bold, color = extra.dim)
        }
        Text(label, fontSize = 10.sp, color = extra.dim, maxLines = 2, lineHeight = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Свой цвет: тон, насыщенность, яркость и код #RRGGBB. */
@Composable
fun ColorPickerDialog(start: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start or (0xFF shl 24), it) } }
    var h by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var v by remember { mutableStateOf(hsv[2]) }
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF
    var hex by remember { mutableStateOf("%06X".format(start and 0xFFFFFF)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Свой цвет")) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(Look.color(rgb)))
                Text(btnLabel("Тон"), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 10.dp).clip(RoundedCornerShape(5.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })))
                Slider(h, { h = it; hex = "%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF) }, valueRange = 0f..359f)
                Text(btnLabel("Насыщенность"), fontSize = 12.sp)
                Slider(sat, { sat = it; hex = "%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF) })
                Text(btnLabel("Яркость"), fontSize = 12.sp)
                Slider(v, { v = it; hex = "%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF) })
                TextInput(hex, { t ->
                    hex = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                    if (hex.length == 6) hex.toIntOrNull(16)?.let { c ->
                        val f = FloatArray(3); android.graphics.Color.colorToHSV(c or (0xFF shl 24), f); h = f[0]; sat = f[1]; v = f[2]
                    }
                }, "Код цвета #RRGGBB")
            }
        },
        confirmButton = { TextButton(onClick = { onPick(rgb) }) { Text(btnLabel("Выбрать")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}
