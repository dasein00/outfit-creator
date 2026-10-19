package com.dasein.poryadok.ui.common

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.dasein.poryadok.data.UiTexts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Сколько держать текст в режиме редактора — дольше обычного удержания (0,5 с), чтобы не мешать меню. */
private const val EDIT_HOLD_MS = 1000L

/**
 * Удержание текста 1 секунду без движения — окно правки. Касание не забираем, пока не прошла секунда:
 * нажатия, прокрутка и обычные удержания работают как всегда; после открытия окна отпускание пальца гасится.
 */
fun Modifier.textEditPress(raw: String): Modifier = pointerInput(raw) {
    if (raw.none { it.isLetter() }) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val released = withTimeoutOrNull(EDIT_HOLD_MS) {
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!c.pressed) return@withTimeoutOrNull true
                if (abs(c.position.x - down.position.x) > slop || abs(c.position.y - down.position.y) > slop) return@withTimeoutOrNull true
            }
        }
        if (released == null && UiTexts.editTarget.value == null) {
            UiTexts.editTarget.value = raw
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                ev.changes.forEach { it.consume() }
                if (ev.changes.none { it.pressed }) break
            }
        }
    }
}

/**
 * Text приложения: тот же material3 Text, но надпись проходит через «Тексты интерфейса» (Оформление) —
 * свои названия кнопок, плашек и подсказок. Своё содержимое (заметки, задачи) совпадает с ними редко и не трогается,
 * если такой надписи в интерфейсе нет.
 */
/**
 * Цвет надписи, различимый на текущем фоне ([com.dasein.poryadok.ui.theme.LocalBg]). Цвет по умолчанию уже подстроен
 * в [com.dasein.poryadok.ui.theme.OnBackground]; цвета темы (основной, бледный, акцент), заданные явно, подстраиваются
 * здесь. Свои цвета плашек (метки статуса на цветном фоне и т. п.) не трогаем — у них свой фон.
 */
@Composable
internal fun readableColor(color: Color): Color {
    val bg = com.dasein.poryadok.ui.theme.LocalBg.current
    if (bg == Color.Unspecified || color == Color.Unspecified || color.alpha < .05f) return color
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val dim = com.dasein.poryadok.ui.theme.LocalExtra.current.dim
    val min = when (color) {
        scheme.onSurface, scheme.onBackground -> 4.5f
        dim, scheme.onSurfaceVariant, scheme.primary -> 3f
        else -> return color
    }
    return com.dasein.poryadok.ui.theme.Look.readable(color, bg, min)
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    UiTexts.version.intValue
    var shown = UiTexts.tr(text)
    var decoration = textDecoration
    if (com.dasein.poryadok.data.UiTextEngine.isHide(shown)) {
        // Скрытый текст не показывается; в режиме редактора виден зачёркнутым — удержанием его можно вернуть.
        if (!UiTexts.editorMode.value) return
        shown = text
        decoration = TextDecoration.LineThrough
    }
    // Одно слово в узком месте раньше рвалось посреди («Описани-е»). Короткую надпись из одного слова
    // не переносим: если места мало, она сокращается многоточием. Абзацы и подписи из нескольких слов
    // переносятся как обычно — по словам.
    val oneWord = maxLines == Int.MAX_VALUE && softWrap && shown.length in 2..24 && shown.none { it.isWhitespace() } && shown.any { it.isLetter() }
    androidx.compose.material3.Text(
        text = shown, modifier = if (UiTexts.editorMode.value) modifier.textEditPress(text) else modifier, color = readableColor(color), fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = decoration,
        textAlign = textAlign, lineHeight = lineHeight,
        overflow = if (oneWord && overflow == TextOverflow.Clip) TextOverflow.Ellipsis else overflow,
        softWrap = if (oneWord) false else softWrap, maxLines = if (oneWord) 1 else maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    UiTexts.version.intValue
    // Текст с выделениями (поиск, ссылки) — содержимое, его не подменяем.
    val plain = text.spanStyles.isEmpty() && text.paragraphStyles.isEmpty()
    val shown = if (plain) AnnotatedString(UiTexts.tr(text.text)) else text
    androidx.compose.material3.Text(
        text = shown, modifier = if (plain && UiTexts.editorMode.value) modifier.textEditPress(text.text) else modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, inlineContent = inlineContent, onTextLayout = onTextLayout, style = style,
    )
}
