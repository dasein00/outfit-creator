package com.dasein.poryadok.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.logic.Dates

private const val DAY_MS = 86_400_000L

/**
 * Свой период: в календаре — нажать начальный день, потом конечный; или ввести обе даты вручную (дд.мм.гггг).
 * Даты — номера дней от 1970-01-01, конец включительно.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(start: Long?, end: Long?, onDismiss: () -> Unit, title: String = "Свой период", onPick: (Long, Long) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start?.let { it * DAY_MS },
        initialSelectedEndDateMillis = end?.let { it * DAY_MS },
    )
    val s = state.selectedStartDateMillis?.let { it / DAY_MS }
    val e = state.selectedEndDateMillis?.let { it / DAY_MS }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = s != null, onClick = { if (s != null) onPick(minOf(s, e ?: s), maxOf(s, e ?: s)) }) { Text(btnLabel("Готово")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("В календаре", state.displayMode == DisplayMode.Picker) { state.displayMode = DisplayMode.Picker }
                Pill("Ввести даты", state.displayMode == DisplayMode.Input) { state.displayMode = DisplayMode.Input }
            }
            Text(
                when {
                    s == null -> if (state.displayMode == DisplayMode.Picker) "Нажмите первый день периода" else "Введите даты в формате дд.мм.гггг"
                    e == null -> "Начало: ${Dates.full(s)}. Теперь нажмите последний день"
                    else -> "${Dates.full(s)} — ${Dates.full(e)} · ${e - s + 1} дн."
                },
                fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            DateRangePicker(
                state = state, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                title = { Text(btnLabel(title), modifier = Modifier.padding(start = 24.dp, top = 8.dp)) },
                headline = null, showModeToggle = false,
            )
        }
    }
}
