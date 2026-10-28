package com.dasein.poryadok.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.data.Account
import com.dasein.poryadok.data.Category
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.FinanceStats
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.FitText
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.LineChart
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Series
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.cardViewport
import com.dasein.poryadok.ui.common.plain
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.YearMonth

/**
 * «Счета»: капитал сейчас и его динамика за год, подушка безопасности (на сколько месяцев хватит), доля сбережений
 * и проценты; карточки счетов — остаток, доля в капитале, приход и расход за месяц, последняя операция;
 * выравнивание остатков (вручную или по выписке), новый счёт и перевод; архивные счета — отдельно.
 */
@Composable
fun AccountsTab(
    nav: NavHostController, accounts: List<Account>, txns: List<Txn>, cats: List<Category>, cur: String,
    onAlign: () -> Unit, onStatement: () -> Unit, onEdit: (Account) -> Unit,
) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    AccountMeta.version.value
    val active = accounts.filter { !it.archived }
    val archived = accounts.filter { it.archived }
    val balances = remember(accounts, txns) { accounts.associate { it.id to balanceOf(it, txns) } }
    val total = active.sumOf { balances[it.id] ?: 0.0 }
    val saving = active.filter { AccountMeta.savings(ctx, it.id) }
    val savingSum = saving.sumOf { balances[it.id] ?: 0.0 }
    val interest = saving.sumOf { AccountMeta.monthly(balances[it.id] ?: 0.0, AccountMeta.rate(ctx, it.id)) }
    val ops = remember(txns, cats) { opsOf(txns, cats) }
    val avgExpense = FinanceStats.averages(ops, YearMonth.now()).expense
    val runway = FinanceStats.runwayMonths(total, avgExpense)
    val month = Dates.monthRange(YearMonth.now())
    var showArchive by remember { mutableStateOf(false) }

    LazyColumn(Modifier.cardViewport(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            // ---------- Капитал ----------
            Tile(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                Text(btnLabel("Капитал сейчас").uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                FitText(Money.format(total, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (saving.isNotEmpty()) {
                    val share = if (total > 0) (savingSum / total).toFloat().coerceIn(0f, 1f) else 0f
                    Bar(share, extra.ok, Modifier.padding(top = 6.dp), height = 8.dp)
                    Text(
                        "в сбережениях ${Money.format(savingSum, cur)} (${(share * 100).toInt()}%) · на картах ${Money.format(total - savingSum, cur)}" +
                            if (interest > 0) " · проценты ≈ +${Money.format(interest, cur)}/мес" else "",
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                val capital = remember(txns, accounts) { capitalByMonth(active, txns, 12) }
                if (capital.size >= 2) {
                    LineChart(
                        listOf(Series(capital.map { it.second.toFloat() }, MaterialTheme.colorScheme.primary, name = "капитал")),
                        Modifier.padding(top = 8.dp), height = 100.dp,
                        labels = listOf(capital.first(), capital[capital.size / 2], capital.last()).map { Dates.monthTitle(it.first).take(3) },
                        pointLabels = capital.map { Dates.monthTitle(it.first) }, format = { Money.format(it.toDouble(), cur) },
                    )
                    val ch = capital.last().second - capital.first().second
                    Text(
                        "${if (ch >= 0) "▲ +" else "▼ −"}${Money.format(kotlin.math.abs(ch), cur)} за ${capital.size} мес.",
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (ch >= 0) extra.ok else extra.danger,
                    )
                }
            }
            // ---------- Подушка безопасности ----------
            if (avgExpense > 0) Tile(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(btnLabel("Подушка безопасности"), fontWeight = FontWeight.SemiBold)
                        Text("Ориентир — 3–6 месячных расходов: ${Money.format(avgExpense * 3, cur)} – ${Money.format(avgExpense * 6, cur)}", fontSize = 12.sp, color = extra.dim)
                    }
                    Text(
                        runway?.let { "%.1f мес.".format(it).replace('.', ',') } ?: "—", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = when { runway == null -> MaterialTheme.colorScheme.onSurface; runway >= 6 -> extra.ok; runway >= 3 -> extra.warn; else -> extra.danger },
                    )
                }
                Bar(((runway ?: 0.0) / 6).toFloat(), if ((runway ?: 0.0) >= 3) extra.ok else extra.warn, Modifier.padding(top = 8.dp))
            }
            // ---------- Действия ----------
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAlign, modifier = Modifier.weight(1f)) { Text(btnLabel("Вписать остатки")) }
                OutlinedButton(onClick = onStatement, modifier = Modifier.weight(1f)) { Text(btnLabel("По выписке")) }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onEdit(Account(name = "")) }, modifier = Modifier.weight(1f)) { Text(btnLabel("+ Новый счёт")) }
                OutlinedButton(onClick = { nav.navigate(Routes.txn(0)) }, modifier = Modifier.weight(1f)) { Text(btnLabel("Перевод / операция")) }
            }
            if (accounts.size > 2) ConsolidateAccountsButton(cur)
            SectionTitle("Счета · ${active.size}")
        }
        items(active.sortedByDescending { balances[it.id] ?: 0.0 }, key = { it.id }) { a -> AccountCard(a, balances[a.id] ?: 0.0, total, txns, month, cur) { onEdit(a) } }
        if (archived.isNotEmpty()) item {
            TextButton(onClick = { showArchive = !showArchive }, modifier = Modifier.padding(top = 6.dp)) {
                Text(if (showArchive) "Скрыть архивные счета" else "Архивные счета (${archived.size})")
            }
        }
        if (showArchive) items(archived, key = { "a" + it.id }) { a -> AccountCard(a, balances[a.id] ?: 0.0, 0.0, txns, month, cur) { onEdit(a) } }
        item { com.dasein.poryadok.ui.common.HowTo("finance") }
    }
}

@Composable
private fun AccountCard(a: Account, balance: Double, total: Double, txns: List<Txn>, month: LongRange, cur: String, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val mine = remember(txns, a.id) { txns.filter { it.accountId == a.id || it.toAccountId == a.id } }
    val inMonth = mine.filter { it.day in month }
    val cameIn = inMonth.filter { (it.type == TxnType.INCOME && it.accountId == a.id) || (it.type == TxnType.TRANSFER && it.toAccountId == a.id) }.sumOf { it.amount }
    val wentOut = inMonth.filter { (it.type == TxnType.EXPENSE && it.accountId == a.id) || (it.type == TxnType.TRANSFER && it.accountId == a.id) }.sumOf { it.amount }
    val last = mine.maxOfOrNull { it.day }
    val color = Palette.item(a.color)
    Tile(Modifier.padding(top = 8.dp), onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Glyph(a.emoji, 24.dp) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(a.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (a.archived) extra.dim else MaterialTheme.colorScheme.onSurface)
                val sub = buildList {
                    if (AccountMeta.savings(ctx, a.id)) {
                        val r = AccountMeta.rate(ctx, a.id)
                        add("сберегательный" + if (r > 0) " · ${r.plain()}% · ≈ +${Money.format(AccountMeta.monthly(balance, r), cur)}/мес" else "")
                    }
                    last?.let { add("последняя операция ${Dates.label(it)}") }
                }
                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), fontSize = 11.sp, color = extra.dim, maxLines = 2)
            }
            Text(Money.format(balance, cur), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (balance < 0) extra.danger else MaterialTheme.colorScheme.onSurface)
        }
        if (total > 0 && balance > 0) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(extra.line)) {
                Box(Modifier.fillMaxWidth((balance / total).toFloat().coerceIn(.02f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
        if (cameIn > 0 || wentOut > 0) Row(Modifier.padding(top = 6.dp)) {
            Text("за месяц ", fontSize = 12.sp, color = extra.dim)
            if (cameIn > 0) Text("+${Money.format(cameIn, cur)}  ", fontSize = 12.sp, color = extra.ok, fontWeight = FontWeight.Medium)
            if (wentOut > 0) Text("−${Money.format(wentOut, cur)}", fontSize = 12.sp, color = extra.danger, fontWeight = FontWeight.Medium)
            if (total > 0 && balance > 0) Text("  · ${(balance / total * 100).toInt()}% капитала", fontSize = 12.sp, color = extra.dim)
        }
        Gap(0.dp)
    }
}
