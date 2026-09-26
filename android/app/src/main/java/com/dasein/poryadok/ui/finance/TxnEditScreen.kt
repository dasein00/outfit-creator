@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.finance

import androidx.activity.compose.BackHandler
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Ic
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.plain
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import kotlinx.coroutines.flow.first

/** «120+45-10» → 155. Удобно считать чек прямо в поле суммы. */
fun evalSum(s: String): Double? {
    val clean = s.replace(" ", "").replace(',', '.')
    if (clean.isEmpty()) return null
    var total = 0.0
    var sign = 1
    val num = StringBuilder()
    for (ch in "$clean+") {
        if (ch == '+' || ch == '-') {
            if (num.isNotEmpty()) total += sign * (num.toString().toDoubleOrNull() ?: return null)
            num.clear()
            sign = if (ch == '-') -1 else 1
        } else num.append(ch)
    }
    return total
}

@Composable
fun TxnEditScreen(nav: NavHostController, id: Long, income: Boolean, settings: Settings) {
    val dao = Graph.dao
    val extra = LocalExtra.current
    val cats by observe(emptyList()) { dao.categories() }
    val accounts by observe(emptyList()) { dao.accounts() }
    val recentNotes by observe(emptyList()) { dao.txns() }
    var t by remember {
        mutableStateOf(Txn(type = if (income) TxnType.INCOME else TxnType.EXPENSE, amount = 0.0, accountId = 0, day = Dates.today()))
    }
    var amountText by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(id == 0L) }
    var pickDate by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(id) {
        if (id != 0L) {
            dao.txns().first().firstOrNull { it.id == id }?.let { t = it; amountText = it.amount.plain() }
            loaded = true
        } else {
            focus.requestFocus()
        }
    }
    LaunchedEffect(accounts) {
        if (t.accountId == 0L && accounts.isNotEmpty()) t = t.copy(accountId = accounts.first().id)
    }
    val amount = evalSum(amountText)
    fun save() {
        val a = amount
        if (loaded && a != null && a > 0 && t.accountId != 0L && (t.type != TxnType.TRANSFER || t.toAccountId != null)) {
            val cur = t.copy(amount = a, createdAt = if (t.createdAt == 0L) System.currentTimeMillis() else t.createdAt)
            io { dao.upsertTxn(cur) }
        }
        nav.popBackStack()
    }
    BackHandler { save() }
    val typeColor = when (t.type) { TxnType.INCOME -> extra.ok; TxnType.EXPENSE -> extra.danger; else -> MaterialTheme.colorScheme.primary }

    Screen(
        title = if (id == 0L) "Новая операция" else "Операция",
        onBack = { nav.popBackStack() },
        actions = {
            if (id != 0L) IconAction(Ic.trash, "Удалить") { confirm = true }
            IconButton(onClick = { save() }) { Icon(Icons.Default.Check, "Сохранить") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Segments(
                listOf(TxnType.EXPENSE to "Расход", TxnType.INCOME to "Доход", TxnType.TRANSFER to "Перевод"),
                t.type, { t = t.copy(type = it, categoryId = null) },
            )
            Gap()
            OutlinedTextField(
                value = amountText,
                onValueChange = { v -> amountText = v.filter { it.isDigit() || it in ".,+- " } },
                textStyle = TextStyle(fontSize = 34.sp, textAlign = TextAlign.Center, color = typeColor),
                placeholder = { Text("0", fontSize = 34.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                suffix = { Text(settings.currency, fontSize = 22.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                shape = RoundedCornerShape(18.dp),
            )
            if (amountText.any { it == '+' || it == '-' } && amount != null) Text(
                "= ${amount.plain()}", color = extra.dim, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
            )

            if (t.type != TxnType.TRANSFER) {
                SectionTitle("Категория")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.filter { it.income == (t.type == TxnType.INCOME) }.forEach { c ->
                        val sel = t.categoryId == c.id
                        Column(
                            Modifier.width(78.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (sel) Palette.item(c.color).copy(alpha = .35f) else extra.card)
                                .clickable { t = t.copy(categoryId = c.id) }.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(c.emoji, fontSize = 22.sp)
                            Text(c.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            SectionTitle(if (t.type == TxnType.TRANSFER) "Откуда" else "Счёт")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a -> Pill("${a.emoji} ${a.name}", t.accountId == a.id) { t = t.copy(accountId = a.id) } }
            }
            if (t.type == TxnType.TRANSFER) {
                SectionTitle("Куда")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.filter { it.id != t.accountId }.forEach { a ->
                        Pill("${a.emoji} ${a.name}", t.toAccountId == a.id) { t = t.copy(toAccountId = a.id) }
                    }
                }
            }

            SectionTitle("Дата")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = Dates.today()
                Pill("Сегодня", t.day == today) { t = t.copy(day = today) }
                Pill("Вчера", t.day == today - 1) { t = t.copy(day = today - 1) }
                Pill(if (t.day < today - 1 || t.day > today) Dates.label(t.day) else "Другая…", t.day < today - 1 || t.day > today) { pickDate = true }
            }
            SectionTitle("Комментарий")
            TextInput(t.note, { t = t.copy(note = it) }, "Например: обед с коллегами")
            val suggestions = remember(recentNotes, t.type) {
                recentNotes.filter { it.type == t.type && it.note.isNotBlank() }.map { it.note }.distinct().take(6)
            }
            if (suggestions.isNotEmpty() && t.note.isBlank()) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) { suggestions.forEach { s -> Pill(s, false) { t = t.copy(note = s) } } }
            Gap(20.dp)
            Button(
                onClick = { save() }, modifier = Modifier.fillMaxWidth(),
                enabled = amount != null && amount > 0,
            ) { Text("Сохранить", color = if (amount != null && amount > 0) MaterialTheme.colorScheme.onPrimary else Color.Unspecified) }
            Gap(40.dp)
        }
    }
    if (pickDate) DatePickDialog(t.day, onDismiss = { pickDate = false }, onPick = { it?.let { d -> t = t.copy(day = d) } }, allowClear = false)
    if (confirm) ConfirmDialog("Удалить операцию?", "", onDismiss = { confirm = false }) {
        val cur = t
        io { dao.deleteTxn(cur) }
        nav.popBackStack()
    }
}
