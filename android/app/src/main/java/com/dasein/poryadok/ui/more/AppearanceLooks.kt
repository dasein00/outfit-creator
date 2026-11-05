@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dasein.poryadok.ui.more

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.cardDecor
import com.dasein.poryadok.ui.common.cardFill
import com.dasein.poryadok.ui.common.fancyBackground
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.LocalLook
import com.dasein.poryadok.ui.theme.Look
import com.dasein.poryadok.ui.theme.LookPreset
import com.dasein.poryadok.ui.theme.LookPresets
import com.dasein.poryadok.ui.theme.LookSettings
import com.dasein.poryadok.ui.theme.Palette
import com.dasein.poryadok.ui.theme.currentPalette

private fun rgb(v: Int) = Color(v or (0xFF shl 24))

/** Образец с подписью: [content] рисуется в рамке, выбранный — с рамкой акцента. */
@Composable
private fun Sample(name: String, selected: Boolean, w: Int = 64, h: Int = 46, onClick: () -> Unit, content: @Composable () -> Unit) {
    val extra = LocalExtra.current
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width((w + 12).dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(w.dp, h.dp).clip(shape)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else extra.line, shape),
        ) { content() }
        Text(name, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Выбор стиля фона: каждый образец нарисован текущими цветами фона и сочетания. */
@Composable
fun BgFxPicker(look: LookSettings, onPick: (Int) -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    val dark = LocalExtra.current.dark
    val pal = currentPalette()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf(-1) + Look.BG_FX.indices).forEach { fx ->
            val name = if (fx < 0) "Как в теме" else Look.BG_FX[fx]
            Sample(name, look.bgFx == fx, onClick = { onPick(fx) }) {
                val g = Look.gradient(look.copy(bgFx = if (fx < 0) -1 else fx), bg)
                Canvas(Modifier.fillMaxSize()) { if (g != null) fancyBackground(g, if (fx < 0) 0 else fx, pal, dark) else drawRect(bg) }
            }
        }
    }
}

/** Выбор стиля плашек: образец плашки на текущем фоне. */
@Composable
fun CardFxPicker(look: LookSettings, onPick: (Int) -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    val dark = LocalExtra.current.dark
    val pal = currentPalette()
    val g = Look.gradient(look, bg)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf(-1) + Look.CARD_FX.indices).forEach { fx ->
            val name = if (fx < 0) "Как в теме" else Look.CARD_FX[fx]
            Sample(name, look.cardFx == fx, onClick = { onPick(fx) }) {
                Canvas(Modifier.fillMaxSize()) { if (g != null) fancyBackground(g, Look.fx(look), pal, dark) else drawRect(bg) }
                val sh = RoundedCornerShape(8.dp)
                Box(Modifier.padding(horizontal = 8.dp, vertical = 9.dp).fillMaxSize().clip(sh).cardFill(sh, fx = fx))
            }
        }
    }
}

/**
 * Сочетания цветов иконок: «Авто» подбирает к фону и плашкам, остальные — от каждого глубокого фона.
 * В образце настоящая иконка в этом сочетании и его цвета точками.
 */
@Composable
fun IconPalettePicker(look: LookSettings, onPick: (Int) -> Unit) {
    val extra = LocalExtra.current
    val auto = Look.paletteFor(look.copy(iconPalette = -1), MaterialTheme.colorScheme.background)
    Text(
        "Иконки раскрашиваются не в один цвет, а сочетанием: яркие детали, блики, светлые формы, полутона и контуры — " +
            "в тонах, подобранных к фону. «Авто» само подбирает сочетание к выбранному фону и плашкам. Сейчас: ${auto.name}.",
        fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (listOf(-1) + Look.DEEP_BACKGROUNDS.indices).forEach { i ->
            val p = if (i < 0) auto else Look.DEEP_BACKGROUNDS[i].icons
            Sample(if (i < 0) "Авто" else p.name, look.iconPalette == i, w = 56, h = 56, onClick = { onPick(i) }) {
                Box(Modifier.fillMaxSize().cardFill(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalLook provides look.copy(icons = 4, iconPalette = i)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Glyph("d01/00", 26.dp, badge = false)
                            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf(p.accent, p.highlight, p.mid, p.light).forEach { c -> Box(Modifier.size(7.dp).clip(CircleShape).background(rgb(c)).border(.5.dp, extra.line, CircleShape)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Цвет фона сохранённого оформления (для значка). */
private fun presetBg(p: LookPreset, systemDark: Boolean): Color = when {
    p.look.bgRgb != 0 -> Look.color(p.look.bgRgb)
    p.theme == "dark" || (p.theme == "system" && systemDark) -> Color(0xFF211D18)
    else -> Color(0xFFF7F3EB)
}

/**
 * Значок оформления: фон в его стиле, плашка в его цвете и стиле, точка акцента и цвета сочетания иконок —
 * по нему сразу видно ключевое цветовое решение.
 */
@Composable
private fun PresetBadge(p: LookPreset, modifier: Modifier) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bg = presetBg(p, systemDark)
    val dark = Look.isDark(bg)
    val g = Look.gradient(p.look, bg)
    val pal = Look.paletteFor(p.look, bg)
    val text = if (p.look.textRgb != 0) Look.color(p.look.textRgb) else if (dark) Color(0xFFF0ECE3) else Color(0xFF211D18)
    val card = Look.cardColor(p.look, dark) ?: Look.mix(bg, text, if (dark) .08f else .04f).let { if (!dark) Look.mix(bg, Color.White, .6f) else it }
    val decor = cardDecor(p.look.cardFx, card, pal, dark)
    val accent = if (p.look.accentRgb != 0) Look.color(p.look.accentRgb) else Palette.accents.getOrNull(p.accent)?.second ?: Palette.Brass
    Canvas(modifier) {
        if (g != null) fancyBackground(g, Look.fx(p.look), pal, dark) else drawRect(bg)
        val r = CornerRadius(p.look.cardRound * .35f * density)
        val tl = Offset(size.width * .12f, size.height * .2f)
        val sz = Size(size.width * .76f, size.height * .52f)
        if (decor?.brush != null) drawRoundRect(decor.brush, tl, sz, r) else drawRoundRect(decor?.fill ?: card, tl, sz, r)
        decor?.border?.let { drawRoundRect(it.brush, tl, sz, r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f * density)) }
        // Строка «текста» и кнопка акцента на плашке.
        drawRoundRect(text.copy(alpha = .8f), Offset(tl.x + 8 * density, tl.y + 8 * density), Size(sz.width * .5f, 4 * density), CornerRadius(2 * density))
        drawRoundRect(accent, Offset(tl.x + 8 * density, tl.y + sz.height - 14 * density), Size(sz.width * .34f, 7 * density), CornerRadius(4 * density))
        // Цвета сочетания иконок — точки внизу.
        listOf(pal.accent, pal.mid, pal.light).forEachIndexed { i, c ->
            drawCircle(rgb(c), 4 * density, Offset(size.width * .2f + i * 11 * density, size.height * .86f))
        }
    }
}

/**
 * «Мои оформления»: сохранить текущее оформление под своим именем, переключаться одним нажатием,
 * удержание — переименовать, перезаписать текущим или удалить.
 */
@Composable
fun LookPresetsSection(settings: Settings) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    remember { LookPresets.load(ctx); 0 }
    val list = LookPresets.list.value
    val look = Look.state.value
    var editing by remember { mutableStateOf<Int?>(null) }
    SectionTitle("Мои оформления", action = "Сохранить текущее", onAction = { editing = -1 })
    if (list.isEmpty()) Text(
        "Сохраните текущее оформление — фон, плашки, цвета, иконки, шрифт и тему — и переключайтесь между любимыми одним нажатием. " +
            "Удержание значка — переименовать или удалить.",
        fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
    ) else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        list.forEachIndexed { i, p ->
            val selected = p.look == look && p.accent == settings.accent && p.theme == settings.theme
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier.width(96.dp).clip(shape).combinedClickable(
                    onClick = {
                        Look.save(ctx, p.look)
                        io { Graph.prefs.update { it.copy(accent = p.accent, theme = p.theme) } }
                    },
                    onLongClick = { editing = i },
                ).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PresetBadge(
                    p, Modifier.size(92.dp, 66.dp).clip(shape)
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else extra.line, shape),
                )
                Text(p.name, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    editing?.let { i ->
        val old = list.getOrNull(i)
        var name by remember(i) { mutableStateOf(old?.name ?: "Оформление ${list.size + 1}") }
        val current = LookPreset(name.trim().ifEmpty { "Оформление" }, look, settings.accent, settings.theme)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(btnLabel(if (old == null) "Сохранить оформление" else "Оформление")) },
            text = {
                Column {
                    PresetBadge(old ?: current, Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(14.dp)))
                    TextInput(name, { name = it.take(40) }, "Название", modifier = Modifier.padding(top = 10.dp))
                    if (old != null) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { LookPresets.replace(ctx, i, current); editing = null }) { Text(btnLabel("Перезаписать текущим")) }
                        TextButton(onClick = { LookPresets.remove(ctx, i); editing = null }) { Text(btnLabel("Удалить"), color = extra.danger) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (old == null) LookPresets.add(ctx, current) else LookPresets.replace(ctx, i, old.copy(name = current.name))
                    editing = null
                }) { Text(btnLabel(if (old == null) "Сохранить" else "Переименовать")) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(btnLabel("Отмена")) } },
        )
    }
}
