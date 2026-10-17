@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dasein.poryadok.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import com.dasein.poryadok.ui.common.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.data.UiTexts
import com.dasein.poryadok.ui.theme.Palette

@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = actions,
                // Цвет шапки не меняется при прокрутке: экран остаётся одним спокойным полотном.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = { Editable { fab() } },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun Tile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = LocalExtra.current.card,
    padding: Dp = Space.card,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Карточка-кнопка без своего удержания: удержание открывает правку её надписи и иконки.
    val holder = if (onClick != null && onLongClick == null) androidx.compose.runtime.remember { LabelHolder() } else null
    val round = com.dasein.poryadok.ui.theme.LocalLook.current.cardRound.dp
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(round)).let {
            when {
                onLongClick != null -> it.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                onClick != null -> (if (holder != null && LocalIconEditable.current) it.labelLongPress(holder) else it).clickable(onClick = onClick)
                else -> it
            }
        },
        color = color,
        shape = RoundedCornerShape(round),
        // Рамка — только у обычной карточки: цветные плашки и так отделены от фона.
        border = if (color == LocalExtra.current.card) cardBorder() else null,
    ) {
        if (holder != null) androidx.compose.runtime.CompositionLocalProvider(LocalLabelHolder provides holder) { Column(Modifier.padding(padding), content = content) }
        else Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.s).heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Заголовок пишется капителью, но правится и переводится по исходному тексту (раньше «Тексты
        // интерфейса» не находили его из-за заглавных). Старые правки, сделанные по заглавному варианту, сохраняются.
        UiTexts.version.intValue
        val upper = text.uppercase()
        val legacy = UiTexts.tr(upper)
        androidx.compose.material3.Text(
            if (legacy != upper) legacy else UiTexts.tr(text).uppercase(),
            style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp,
            color = LocalExtra.current.dim, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = (if (UiTexts.editorMode.value) Modifier.textEditPress(if (legacy != upper) upper else text) else Modifier).weight(1f),
        )
        if (action != null) Editable {
            Text(
                btnLabel(action), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Space.s).clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction)
                    .heightIn(min = 36.dp).padding(horizontal = Space.s, vertical = Space.s),
            )
        }
    }
}

@Composable
fun Empty(emoji: String, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(emoji, 48.dp, badge = true)
        Spacer(Modifier.height(Space.l))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.s))
        Text(text, color = LocalExtra.current.dim, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = 320.dp))
    }
}

@Composable
fun CheckDot(checked: Boolean, color: Color, onClick: () -> Unit, size: Dp = 26.dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (checked) Modifier.background(color) else Modifier.border(2.dp, color, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Default.Check, null, tint = Color(0xFF1B1812), modifier = Modifier.size(size * .66f))
    }
}

@Composable
fun priorityColor(p: Int): Color = when (p) {
    3 -> LocalExtra.current.danger
    2 -> MaterialTheme.colorScheme.primary
    1 -> Color(0xFF6FA3C7)
    else -> LocalExtra.current.dim
}

val PRIORITY_NAMES = listOf("Без приоритета", "Низкий", "Средний", "Высокий")

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Palette.items.forEachIndexed { i, c ->
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(c)
                    .then(if (i == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(i) }
            )
        }
    }
}

/** Иконки для привычек, целей, списков и категорий — фирменный набор вместо эмодзи. */
val GLYPH_CHOICES = listOf(
    "ui:check", "sport/21", "habit/11", "sport/12", "sport/01", "sport/04", "ui:drop", "ui:salad", "sleep/00", "sleep/15",
    "sport/18", "sport/14", "ui:home", "ui:leaves", "ui:notebook", "habit/14", "fest/15", "sleep/19", "habit/12", "ui:sun",
    "sport/06", "sport/08", "sport/09", "food/11", "fest/29", "ui:wallet", "cal/33", "ui:heart", "ui:image", "ui:folder",
    "cal/29", "food/24", "cal/35", "fest/01", "habit/01", "habit/20", "habit/22", "ui:flame", "ui:trophy", "fest/23",
    "sport/15", "sport/03", "train/10", "sleep/08", "cal/31", "fest/12", "habit/03", "habit/27",
)

/** Выбор иконки: частые варианты сразу, весь набор — по кнопке «Все иконки». */
@Composable
fun EmojiPicker(selected: String, onSelect: (String) -> Unit) {
    var all by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column {
        GlyphRow(GLYPH_CHOICES, selected, onSelect)
        TextButton(onClick = { all = true }) { Text(btnLabel("Все иконки…")) }
    }
    if (all) GlyphPickerDialog(selected, { all = false }, onPick = onSelect)
}

@Composable
fun <T> Segments(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            val holder = androidx.compose.runtime.remember { LabelHolder() }
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                modifier = if (LocalIconEditable.current) Modifier.labelLongPress(holder) else Modifier,
                label = { androidx.compose.runtime.CompositionLocalProvider(LocalLabelHolder provides holder) { Text(btnLabel(label), maxLines = 1, fontSize = 13.sp) } },
            )
        }
    }
}

@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Pill(label, value == selected) { onSelect(value) } }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, glyph: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Удержание — правка иконки и названия (подписи с числами меняются сами и не переименовываются).
    val holder = androidx.compose.runtime.remember { LabelHolder() }
    holder.label = text.takeIf { t -> t.none { it.isDigit() } }
    val shownText = text
    Surface(
        onClick = onClick,
        modifier = (if (LocalIconEditable.current) Modifier.labelLongPress(holder) else Modifier).heightIn(min = 36.dp),
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.onSurface else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, scheme.outline),
    ) {
        Row(
            Modifier.padding(start = if (glyph != null) 10.dp else 14.dp, end = 14.dp, top = if (glyph != null) 6.dp else 8.dp, bottom = if (glyph != null) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalLabelHolder provides holder) {
                if (glyph != null) { Glyph(glyph, 18.dp); Spacer(Modifier.width(6.dp)) }
            }
            // Подпись в одну строку: слово не должно переноситься по слогам («Последн-яя»).
            Text(
                shownText, fontSize = 14.sp, maxLines = 1, softWrap = false,
                color = if (selected) scheme.surface else scheme.onSurface.copy(alpha = .8f),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String = "",
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) || it == '-' }) },
        label = { Text(label) },
        suffix = if (suffix.isNotEmpty()) ({ Text(suffix) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Space.fieldRound),
    )
}

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine, minLines = minLines,
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(Space.fieldRound),
    )
}

fun String.num(): Double? = replace(" ", "").replace(',', '.').toDoubleOrNull()
fun Double.plain(): String = if (this == Math.floor(this) && kotlin.math.abs(this) < 1e12) toLong().toString() else "%.2f".format(this).trimEnd('0').trimEnd(',', '.')

@Composable
fun DatePickDialog(initial: Long?, onDismiss: () -> Unit, onPick: (Long?) -> Unit, allowClear: Boolean = true) {
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: Dates.today()) * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.selectedDateMillis?.let { it / 86_400_000L }); onDismiss() }) { Text(btnLabel("Готово")) }
        },
        dismissButton = {
            com.dasein.poryadok.ui.common.ButtonsRow {
                if (allowClear) TextButton(onClick = { onPick(null); onDismiss() }) { Text(btnLabel("Без даты")) }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
            }
        },
    ) { DatePicker(state = state, showModeToggle = false) }
}

@Composable
fun TimePickDialog(initial: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit, allowClear: Boolean = true) {
    val m = initial ?: (9 * 60)
    val state = rememberTimePickerState(initialHour = m / 60, initialMinute = m % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute); onDismiss() }) { Text(btnLabel("Готово")) } },
        dismissButton = {
            com.dasein.poryadok.ui.common.ButtonsRow {
                if (allowClear) TextButton(onClick = { onPick(null); onDismiss() }) { Text(btnLabel("Без времени")) }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
            }
        },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Удалить", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(btnLabel(confirm), color = LocalExtra.current.danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

@Composable
fun FieldButton(label: String, value: String, modifier: Modifier = Modifier, glyph: String? = null, onClick: () -> Unit) {
    val holder = androidx.compose.runtime.remember { LabelHolder() }
    androidx.compose.runtime.CompositionLocalProvider(LocalLabelHolder provides holder) { FieldButtonBody(label, value, (if (LocalIconEditable.current) modifier.labelLongPress(holder) else modifier), glyph, onClick) }
}

@Composable
private fun FieldButtonBody(label: String, value: String, modifier: Modifier, glyph: String?, onClick: () -> Unit) {
    Row(
        // Та же высота, скругление и рамка, что у полей ввода: форма читается одним столбцом.
        modifier.heightIn(min = Space.field).clip(RoundedCornerShape(Space.fieldRound))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Space.fieldRound))
            .clickable(onClick = onClick).padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) { Glyph(glyph, 22.dp); Spacer(Modifier.width(Space.m)) }
        Column {
            Text(btnLabel(label), fontSize = 12.sp, color = LocalExtra.current.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Tile(modifier, padding = Space.m) {
        FitText(value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, color = color, textAlign = TextAlign.Center)
        Text(label, fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.fillMaxWidth().padding(top = Space.xxs), textAlign = TextAlign.Center)
    }
}

@Composable
fun Bar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(LocalExtra.current.line)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height)).background(color))
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp = 12.dp) = Spacer(Modifier.width(w))

@Composable
fun FullCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
