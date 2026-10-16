package com.dasein.poryadok.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.data.CustomNames
import com.dasein.poryadok.data.UiTexts
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Кнопки приложения: удержание любой кнопки открывает правку — сменить иконку (если она есть) и название.
 * Название хранится в CustomNames под ключом «btn:<исходный текст>» и меняется везде, где стоит такая надпись.
 */
class LabelHolder {
    /** Исходная надпись кнопки (первый текст внутри). */
    var label: String? = null
    /** Иконка кнопки (исходный ключ, до замены). */
    var icon: String? = null
}

/** Кнопка, внутри которой рисуется содержимое: надпись и иконка сообщают ей о себе. */
val LocalLabelHolder = compositionLocalOf<LabelHolder?> { null }

/** Надпись с учётом переименования. Внутри кнопки — ещё и запоминает исходный текст для окна правки. */
@Composable
fun btnLabel(text: String): String {
    if (text.none { it.isLetter() }) return text
    LocalLabelHolder.current?.let { if (it.label == null) it.label = text }
    // Сам перевод делает Text — иначе надпись подменялась бы дважды.
    return text
}

object LabelEdit {
    data class Target(val label: String?, val icon: String?)
    val target = mutableStateOf<Target?>(null)
    /** Касание, которое уже взял самый внутренний редактируемый элемент (кнопка внутри карточки) — карточка его не трогает. */
    internal var claimed: androidx.compose.ui.input.pointer.PointerId? = null
}

/**
 * Несколько кнопок в ряд (низ диалога и т.п.): если не помещаются — кнопка целиком переходит на следующую строку,
 * а не сжимается так, что слово ломается посередине («Разобр-ать»).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ButtonsRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
    ) { content() }
}

/** Текст на кнопках: по центру, переносы по словам и ровными строками, без «висящего» слова. */
private val ButtonText = androidx.compose.ui.text.TextStyle(
    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    lineBreak = androidx.compose.ui.text.style.LineBreak.Heading,
)

@Composable
private fun ButtonContent(holder: LabelHolder, scope: RowScope, content: @Composable RowScope.() -> Unit) {
    CompositionLocalProvider(
        LocalLabelHolder provides holder,
        androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.merge(ButtonText),
    ) { scope.content() }
}

/** Любой блок, который можно править удержанием: первая надпись (btnLabel) и первая иконка внутри него. */
@Composable
fun Editable(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val holder = remember { LabelHolder() }
    Box(if (LocalIconEditable.current) modifier.labelLongPress(holder) else modifier) {
        CompositionLocalProvider(LocalLabelHolder provides holder) { content() }
    }
}

/** Удержание кнопки: если есть что править — открываем окно, а нажатие на саму кнопку не срабатывает. */
fun Modifier.labelLongPress(holder: LabelHolder): Modifier = pointerInput(holder) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Внутренний элемент получает касание раньше внешнего: если он уже взял его, внешний молчит.
        if (holder.label == null && holder.icon == null) return@awaitEachGesture
        if (LabelEdit.claimed == down.id) return@awaitEachGesture
        LabelEdit.claimed = down.id
        val slop = viewConfiguration.touchSlop
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!c.pressed) return@withTimeoutOrNull true
                if (abs(c.position.x - down.position.x) > slop || abs(c.position.y - down.position.y) > slop) return@withTimeoutOrNull true
            }
        }
        if (released == null && (holder.label != null || holder.icon != null)) {
            LabelEdit.target.value = LabelEdit.Target(holder.label, holder.icon)
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                ev.changes.forEach { it.consume() }
                if (ev.changes.none { it.pressed }) break
            }
        }
    }
}

@Composable
private fun rememberHolderModifier(modifier: Modifier, enabled: Boolean): Pair<LabelHolder, Modifier> {
    val holder = remember { LabelHolder() }
    val editable = LocalIconEditable.current
    return holder to if (editable && enabled) modifier.labelLongPress(holder) else modifier
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val (holder, m) = rememberHolderModifier(modifier, enabled)
    androidx.compose.material3.Button(onClick = onClick, modifier = m, enabled = enabled, shape = shape, colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource) {
        ButtonContent(holder, this, content)
    }
}

/** Рамка «как у обычной OutlinedButton» — тогда передаём её не задавая. */
private val DefaultOutline = BorderStroke(0.dp, Color.Unspecified)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = DefaultOutline,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val (holder, m) = rememberHolderModifier(modifier, enabled)
    val inner: @Composable RowScope.() -> Unit = {
        ButtonContent(holder, this, content)
    }
    if (border === DefaultOutline) androidx.compose.material3.OutlinedButton(
        onClick = onClick, modifier = m, enabled = enabled, shape = shape, colors = colors, elevation = elevation,
        contentPadding = contentPadding, interactionSource = interactionSource, content = inner,
    ) else androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = m, enabled = enabled, shape = shape, colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = inner)
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val (holder, m) = rememberHolderModifier(modifier, enabled)
    androidx.compose.material3.TextButton(onClick = onClick, modifier = m, enabled = enabled, shape = shape, colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource) {
        ButtonContent(holder, this, content)
    }
}

/** Окно правки кнопки: иконка (если есть) и название. Ставится один раз в корне приложения. */
@Composable
fun LabelEditHost() {
    val t = LabelEdit.target.value ?: return
    val extra = LocalExtra.current
    CompositionLocalProvider(LocalIconEditable provides false) {
        val label = t.label
        var name by remember(t) { mutableStateOf(label?.let { UiTexts.tr(it) }.orEmpty()) }
        val renamed = label != null && UiTexts.tr(label) != label
        AlertDialog(
            onDismissRequest = { LabelEdit.target.value = null },
            title = { Text("Кнопка") },
            text = {
                Column {
                    t.icon?.let { icon ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(extra.card), contentAlignment = Alignment.Center) { Glyph(icon, 34.dp) }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { LabelEdit.target.value = null; IconEdit.target.value = icon },
                                modifier = Modifier.padding(start = 12.dp).weight(1f),
                            ) { Text("Сменить или изменить иконку") }
                        }
                    }
                    if (label != null) {
                        TextInput(name, { name = it.take(40) }, "Название кнопки")
                        Text("Исходное: «$label». Новое название появится везде, где стоит такая надпись.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
                    } else Text("У этой кнопки нет постоянной надписи — можно поменять только иконку.", fontSize = 13.sp, color = extra.dim)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (label != null) {
                        val v = name.trim()
                        // Совпало с вариантом по умолчанию — своя правка не нужна.
                        CustomNames.set("btn:$label", null)
                        if (v.isNotEmpty() && v != UiTexts.tr(label)) CustomNames.set("btn:$label", v)
                    }
                    LabelEdit.target.value = null
                }) { Text(if (label != null) "Сохранить" else "Закрыть") }
            },
            dismissButton = {
                if (renamed) androidx.compose.material3.TextButton(onClick = { CustomNames.set("btn:$label", label); LabelEdit.target.value = null }) { Text("Вернуть исходное") }
                else androidx.compose.material3.TextButton(onClick = { LabelEdit.target.value = null }) { Text("Отмена") }
            },
        )
    }
}
