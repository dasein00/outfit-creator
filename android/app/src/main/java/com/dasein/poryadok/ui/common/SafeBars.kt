package com.dasein.poryadok.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/*
 * ПРАВИЛО ПРОЕКТА: ни одна кнопка, панель или поле не должны заходить под системные полосы Android —
 * строку состояния сверху и кнопки «Назад / Домой / Недавние» (или полоску жестов) снизу.
 *  • Обычные экраны — в Scaffold, и содержимое получает его `pad` (Modifier.padding(pad)).
 *  • Полноэкранные окна (Dialog с decorFitsSystemWindows = false) и всё, что прижато к низу экрана вне Scaffold, —
 *    через Modifier.safeBars() отсюда. Внутри Dialog обычный systemBarsPadding() на части телефонов даёт 0,
 *    поэтому отступы берутся ещё и из окна приложения и выбирается больший.
 *  • Последний элемент списка — с запасом снизу, чтобы кнопку не закрывала полоса жестов.
 */

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Отступы системных полос в px: сверху, снизу, слева, справа. Берётся больший из окна и окна приложения. */
@Composable
private fun barsPx(withIme: Boolean): IntArray {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val own = WindowInsets.systemBars
    val ime = if (withIme) WindowInsets.ime.getBottom(density) else 0
    val act = LocalContext.current.activity()
    val app = runCatching {
        act?.window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
    }.getOrNull()
    val left = maxOf(own.getLeft(density, dir), if (dir == LayoutDirection.Ltr) app?.left ?: 0 else app?.right ?: 0)
    val right = maxOf(own.getRight(density, dir), if (dir == LayoutDirection.Ltr) app?.right ?: 0 else app?.left ?: 0)
    val top = maxOf(own.getTop(density), app?.top ?: 0)
    val bottom = maxOf(own.getBottom(density), app?.bottom ?: 0, ime)
    return intArrayOf(top, bottom, left, right)
}

/**
 * Отступ от строки состояния и кнопок Android — для полноэкранных окон и панелей у нижнего края вне Scaffold.
 * [extraBottom] — небольшой запас над кнопками, чтобы палец не задевал их; [withIme] — ещё и над клавиатурой.
 */
@Composable
fun Modifier.safeBars(top: Boolean = true, extraBottom: Dp = 8.dp, withIme: Boolean = false): Modifier {
    val p = barsPx(withIme)
    val d = LocalDensity.current
    return with(d) {
        padding(
            start = p[2].toDp(), end = p[3].toDp(),
            top = if (top) p[0].toDp() else 0.dp,
            bottom = p[1].toDp() + extraBottom,
        )
    }
}

/** Только снизу: над кнопками Android (и клавиатурой, если [withIme]). */
@Composable
fun Modifier.safeBottom(extra: Dp = 8.dp, withIme: Boolean = false): Modifier {
    val p = barsPx(withIme)
    return with(LocalDensity.current) { padding(bottom = p[1].toDp() + extra) }
}
