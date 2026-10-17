package com.dasein.poryadok.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.dasein.poryadok.ui.theme.LocalExtra

/**
 * Единая сетка отступов приложения (шаг 4 dp). Общие компоненты берут размеры отсюда,
 * чтобы все экраны дышали одинаково.
 */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Поле экрана слева и справа. */
    val gutter = 16.dp
    /** Внутренний отступ карточки. */
    val card = 16.dp
    /** Минимальная высота нажимаемого элемента (кнопки, поля). */
    val touch = 48.dp
    /** Высота поля ввода и поля-кнопки — одна на всё приложение. */
    val field = 56.dp
    /** Скругление полей ввода, полей-кнопок и подсказок. */
    val fieldRound = 14.dp
}

/** Тонкая рамка карточки: в светлой теме отделяет карточку от фона, в тёмной не нужна. */
@Composable
fun cardBorder(): BorderStroke? {
    val extra = LocalExtra.current
    return if (extra.dark) null else BorderStroke(1.dp, extra.line)
}
