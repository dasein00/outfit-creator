package com.dasein.poryadok.ui.health

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.system.BodyAccounts
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch

/**
 * Аккаунты весов: у каждого человека свои пол, рост, возраст, взвешивания и замеры — и своя модель телосложения
 * (мужская или женская). Касание другого аккаунта переключает весь раздел, повторное касание открытого — управление.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BodyAccountsBar() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { BodyAccounts.reload(ctx); BodyAccounts.recover(ctx) }
    val st by BodyAccounts.flow(ctx).collectAsState()
    val s = st ?: return
    var adding by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Gap(8.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.accounts.sortedBy { it.createdAt }.forEach { a ->
            Pill(a.name, a.id == s.active) {
                when {
                    a.id == s.active -> managing = true
                    !busy -> { busy = true; scope.launch { runCatching { BodyAccounts.switchTo(ctx, a.id) }; busy = false } }
                }
            }
        }
        Pill(if (s.accounts.isEmpty()) "+ Аккаунт" else "+", false) { adding = true }
    }
    val owner = s.owner
    if (owner != null && s.active != owner.id) {
        Text(
            "Health Connect подтягивает взвешивания только в «${owner.name}». Здесь — вручную или по скриншоту Fitdays.",
            fontSize = 11.sp, color = extra.dim, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
    if (adding) AddAccountDialog(first = s.accounts.isEmpty(), onDismiss = { adding = false }) { name, male, height, age, current ->
        adding = false
        busy = true
        scope.launch { runCatching { BodyAccounts.create(ctx, name, male, height, age, current) }; busy = false }
    }
    if (managing) ManageAccountsDialog(s) { managing = false }
}

@Composable
private fun AddAccountDialog(first: Boolean, onDismiss: () -> Unit, onCreate: (String, Boolean, Double, Int, String?) -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var current by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var male by remember { mutableStateOf(false) }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Новый аккаунт")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "У каждого аккаунта свои взвешивания, замеры, рост, возраст и модель телосложения. Переключаются касанием по имени.",
                    fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
                )
                if (first) {
                    Gap(8.dp)
                    TextInput(current, { current = it }, "Как назвать ваши текущие данные")
                }
                Gap(8.dp)
                TextInput(name, { name = it }, "Имя нового аккаунта")
                Gap(8.dp)
                Segments(listOf(false to "Женщина", true to "Мужчина"), male, { male = it })
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField(age, { age = it }, "Возраст", Modifier.weight(1f), "лет", decimal = false)
                    NumberField(height, { height = it }, "Рост", Modifier.weight(1f), "см")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = age.toIntOrNull()?.takeIf { it in 10..110 }
                val h = height.num()?.takeIf { it in 100.0..250.0 }
                when {
                    name.isBlank() -> Toast.makeText(ctx, "Впишите имя", Toast.LENGTH_SHORT).show()
                    a == null || h == null -> Toast.makeText(ctx, "Проверьте возраст (10–110) и рост (100–250 см)", Toast.LENGTH_SHORT).show()
                    else -> onCreate(name, male, h, a, current.takeIf { first })
                }
            }) { Text(btnLabel("Создать")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

@Composable
private fun ManageAccountsDialog(s: BodyAccounts.State, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val names = remember { s.accounts.associate { it.id to mutableStateOf(it.name) } }
    var deleting by remember { mutableStateOf<BodyAccounts.Account?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Аккаунты весов")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                s.accounts.sortedBy { it.createdAt }.forEach { a ->
                    val n = names.getValue(a.id)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        TextInput(n.value, { n.value = it }, if (a.id == s.active) "Открыт сейчас" else if (a.id == s.owner?.id) "Владелец телефона" else "Имя", Modifier.weight(1f))
                        if (a.id != s.active && a.id != s.owner?.id) TextButton(onClick = { deleting = a }) { Text(btnLabel("Удалить"), color = extra.danger) }
                    }
                }
                Text(
                    "Первый аккаунт — владельца телефона: в него приходят взвешивания из Health Connect. Его и открытый аккаунт удалить нельзя. " +
                        "Если в аккаунт попало чужое взвешивание, откройте его («Изменить») и перенесите в нужный аккаунт.",
                    fontSize = 11.sp, color = extra.dim, lineHeight = 15.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                names.forEach { (id, n) -> if (n.value.isNotBlank() && s.accounts.firstOrNull { it.id == id }?.name != n.value.trim()) com.dasein.poryadok.ui.common.io { BodyAccounts.rename(ctx, id, n.value) } }
                onDismiss()
            }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${a.name}»?") },
            text = { Text("Все взвешивания, замеры и фото прогресса этого аккаунта будут удалены.", fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    com.dasein.poryadok.ui.common.io { BodyAccounts.delete(ctx, a.id) }
                    deleting = null
                    onDismiss()
                }) { Text(btnLabel("Удалить"), color = extra.danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(btnLabel("Отмена")) } },
        )
    }
}

/** В диалоге взвешивания: перенести чужое взвешивание в другой аккаунт. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoveReadingRow(m: com.dasein.poryadok.data.BodyMetric, onMoved: () -> Unit) {
    val ctx = LocalContext.current
    val st by BodyAccounts.flow(ctx).collectAsState()
    val s = st ?: return
    val others = s.accounts.filter { it.id != s.active }.sortedBy { it.createdAt }
    if (others.isEmpty()) return
    Text(
        btnLabel("Это взвешивание другого человека? Перенести в аккаунт:"),
        fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        others.forEach { a ->
            Pill("→ ${a.name}", false) {
                com.dasein.poryadok.ui.common.io { BodyAccounts.moveReading(Graph.app, m, a.id) }
                Toast.makeText(ctx, "Перенесено в «${a.name}»", Toast.LENGTH_SHORT).show()
                onMoved()
            }
        }
    }
}
