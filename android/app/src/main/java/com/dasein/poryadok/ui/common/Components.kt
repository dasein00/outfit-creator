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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = fab,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun Tile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = LocalExtra.current.card,
    padding: Dp = 14.dp,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).let {
            when {
                onLongClick != null -> it.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                onClick != null -> it.clickable(onClick = onClick)
                else -> it
            }
        },
        color = color,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp,
            color = LocalExtra.current.dim, modifier = Modifier.weight(1f),
        )
        if (action != null) Text(
            action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
        )
    }
}

@Composable
fun Empty(emoji: String, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(emoji, 44.dp, badge = true)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, color = LocalExtra.current.dim, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
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
        TextButton(onClick = { all = true }) { Text("Все иконки…") }
    }
    if (all) GlyphPickerDialog(selected, { all = false }, onPick = onSelect)
}

@Composable
fun <T> Segments(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label, maxLines = 1, fontSize = 13.sp) },
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) scheme.onSurface else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, scheme.outline),
    ) {
        Row(
            Modifier.padding(start = if (glyph != null) 8.dp else 14.dp, end = 14.dp, top = if (glyph != null) 5.dp else 8.dp, bottom = if (glyph != null) 5.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) { Glyph(glyph, 18.dp); Spacer(Modifier.width(6.dp)) }
            // Подпись в одну строку: слово не должно переноситься по слогам («Последн-яя»).
            Text(
                text, fontSize = 14.sp, maxLines = 1, softWrap = false,
                color = if (selected) scheme.surface else LocalExtra.current.dim,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
        shape = RoundedCornerShape(14.dp),
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
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
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
            TextButton(onClick = { onPick(state.selectedDateMillis?.let { it / 86_400_000L }); onDismiss() }) { Text("Готово") }
        },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onPick(null); onDismiss() }) { Text("Без даты") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
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
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute); onDismiss() }) { Text("Готово") } },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onPick(null); onDismiss() }) { Text("Без времени") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
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
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = LocalExtra.current.danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun FieldButton(label: String, value: String, modifier: Modifier = Modifier, glyph: String? = null, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) { Glyph(glyph, 20.dp); Spacer(Modifier.width(10.dp)) }
        Column {
            Text(label, fontSize = 12.sp, color = LocalExtra.current.dim)
            Text(value, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Tile(modifier, padding = 12.dp) {
        Text(
            value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
        Text(label, fontSize = 11.sp, color = LocalExtra.current.dim, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
