package com.dasein.poryadok.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** Знаки валют, которых нет в шрифте с засечками (вместо них рисуется прямоугольник). */
private const val CURRENCY = "₽₸₴֏₼₾₺"

/** Знаки валют — обычным шрифтом: в шрифте с засечками «₽» нет, и на его месте был прямоугольник. */
fun moneySafe(s: String): AnnotatedString = if (s.none { it in CURRENCY }) AnnotatedString(s) else buildAnnotatedString {
    s.forEach { ch ->
        if (ch in CURRENCY) { pushStyle(SpanStyle(fontFamily = FontFamily.SansSerif)); append(ch); pop() } else append(ch)
    }
}

/**
 * Текст в одну строку, который сам уменьшается, пока не поместится по ширине, — суммы, числа, итоги.
 * Ничего не обрезается многоточием, пока не дойдём до [minFontSize].
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    minFontSize: TextUnit = 9.sp,
) {
    var base = style.merge(TextStyle(color = color, fontWeight = fontWeight, fontSize = fontSize))
    if (textAlign != null) base = base.copy(textAlign = textAlign)
    val size0 = if (base.fontSize.isSpecified) base.fontSize else 14.sp
    var scale by remember(text, size0) { mutableFloatStateOf(1f) }
    var ready by remember(text, size0) { mutableStateOf(false) }
    var atMin by remember(text, size0) { mutableStateOf(false) }
    Text(
        moneySafe(text),
        modifier.drawWithContent { if (ready) drawContent() },
        style = base.copy(fontSize = size0 * scale),
        maxLines = 1, softWrap = false,
        overflow = if (atMin) TextOverflow.Ellipsis else TextOverflow.Clip,
        onTextLayout = { r ->
            if (r.didOverflowWidth && !atMin) {
                if (size0.value * scale * .9f >= minFontSize.value) scale *= .9f else atMin = true
            } else ready = true
        },
    )
}
