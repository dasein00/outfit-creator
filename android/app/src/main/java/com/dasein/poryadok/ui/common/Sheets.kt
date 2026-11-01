package com.dasein.poryadok.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Шторка не сворачивается от инерции списка: резкий бросок вверх доезжает до начала и останавливается.
 * Свернуть шторку можно, только когда список уже в самом начале и пальцем продолжаешь тянуть вниз.
 */
private object SheetFlingGuard : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput) Offset.Zero else available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * Нижняя шторка приложения — ModalBottomSheet со спокойным сворачиванием (см. [SheetFlingGuard]).
 * [contentWindowInsets] по умолчанию — отступ от кнопок Android; если содержимое само делает safeBottom(),
 * передайте пустые отступы, чтобы снизу не было двойного пустого места.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
    ) {
        Column(Modifier.fillMaxWidth().nestedScroll(SheetFlingGuard), content = content)
    }
}

/** Пустые отступы для шторки, чьё содержимое само отступает от системных полос. */
val NoSheetInsets: @Composable () -> WindowInsets = { WindowInsets(0, 0, 0, 0) }
