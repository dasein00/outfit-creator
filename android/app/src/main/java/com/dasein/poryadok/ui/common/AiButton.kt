package com.dasein.poryadok.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Кнопка ИИ: волшебная палочка с блёстками и надпись. Удержание — сменить иконку и название (как у всех кнопок).
 * Пока ИИ думает — крутилка и [busyText].
 */
@Composable
fun AiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    busyText: String = "ИИ думает…",
    outlined: Boolean = false,
) {
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(busyText)
        } else {
            Glyph(Glyphs.AI, 22.dp, badge = false)
            Spacer(Modifier.width(8.dp))
            Text(btnLabel(text))
        }
    }
    if (outlined) OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled && !busy, content = content)
    else Button(onClick = onClick, modifier = modifier, enabled = enabled && !busy, content = content)
}

/** Текстовая кнопка ИИ (в диалогах и строках). */
@Composable
fun AiTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, busyText: String = "ИИ думает…") {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled && !busy) {
        if (busy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)); Text(busyText) }
        else { Glyph(Glyphs.AI, 20.dp, badge = false); Spacer(Modifier.width(6.dp)); Text(btnLabel(text)) }
    }
}

/** Содержимое кнопки ИИ: палочка с блёстками и надпись (или «ИИ думает…»). */
@Composable
fun AiLabel(text: String, busy: Boolean = false, busyText: String = "ИИ думает…") {
    if (busy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)); Text(busyText) }
    else { Glyph(Glyphs.AI, 20.dp, badge = false); Spacer(Modifier.width(6.dp)); Text(btnLabel(text)) }
}
