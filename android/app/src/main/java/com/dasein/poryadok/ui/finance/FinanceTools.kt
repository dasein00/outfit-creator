package com.dasein.poryadok.ui.finance

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Account
import com.dasein.poryadok.data.Category
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.logic.Statement
import com.dasein.poryadok.system.Ai
import com.dasein.poryadok.system.StatementFiles
import com.dasein.poryadok.system.StatementImport
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.plain
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs

const val STATEMENT_ROUTE = "finImport"
const val FIN_AI_ROUTE = "finAi"

// ---------- Тип счёта и процент ----------

/** Обычный счёт или сберегательный (вклад, накопительный) с процентом годовых. Хранится на телефоне и в резервной копии. */
object AccountMeta {
    const val PREFS = "fin_accounts"
    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val version = mutableStateOf(0)

    fun savings(ctx: Context, id: Long): Boolean { version.value; return sp(ctx).getBoolean("sav_$id", false) }
    fun rate(ctx: Context, id: Long): Double { version.value; return sp(ctx).getFloat("rate_$id", 0f).toDouble() }

    fun set(ctx: Context, id: Long, savings: Boolean, rate: Double) {
        sp(ctx).edit().putBoolean("sav_$id", savings).putFloat("rate_$id", rate.toFloat()).apply()
        version.value++
    }

    /** Доход по процентам за месяц при простом начислении. */
    fun monthly(balance: Double, rate: Double) = if (balance > 0 && rate > 0) balance * rate / 100 / 12 else 0.0
}

/** Выровнять остатки: вписать, сколько сейчас на каждом счёте, — разница уходит в начальный остаток, история не меняется. */
@Composable
fun BalanceAlignDialog(accounts: List<Account>, txns: List<Txn>, cur: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val values = remember { mutableStateMapOf<Long, String>().apply { accounts.forEach { put(it.id, balanceOf(it, txns).plain()) } } }
    val kinds = remember { mutableStateMapOf<Long, Boolean>().apply { accounts.forEach { put(it.id, AccountMeta.savings(ctx, it.id)) } } }
    val rates = remember { mutableStateMapOf<Long, String>().apply { accounts.forEach { a -> AccountMeta.rate(ctx, a.id).takeIf { it > 0 }?.let { put(a.id, it.plain()) } } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Выровнять остатки")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Впишите, сколько денег на каждом счёте прямо сейчас (как в приложении банка). Разница учтётся в начальном остатке — операции и аналитика не изменятся.",
                    fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
                )
                if (accounts.isEmpty()) Text(btnLabel("Счетов пока нет — добавьте их кнопкой «+» на вкладке «Счета»."), modifier = Modifier.padding(top = 8.dp))
                accounts.forEach { a ->
                    val now = balanceOf(a, txns)
                    Text(a.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                    Text("по записям: ${Money.format(now, cur)}", fontSize = 12.sp, color = extra.dim)
                    NumberField(values[a.id].orEmpty(), { values[a.id] = it }, "Остаток сейчас", suffix = cur)
                    Segments(listOf(false to "Обычный", true to "Сберегательный"), kinds[a.id] == true, { kinds[a.id] = it }, Modifier.padding(top = 6.dp))
                    if (kinds[a.id] == true) {
                        NumberField(rates[a.id].orEmpty(), { rates[a.id] = it }, "Процент годовых", suffix = "%")
                        val r = rates[a.id]?.num() ?: 0.0
                        val b = values[a.id]?.num() ?: now
                        if (r > 0 && b > 0) Text(
                            "≈ +${Money.format(AccountMeta.monthly(b, r), cur)} в месяц, +${Money.format(b * r / 100, cur)} в год",
                            fontSize = 12.sp, color = extra.ok, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    accounts.forEach { a ->
                        AccountMeta.set(ctx, a.id, kinds[a.id] == true, rates[a.id]?.num() ?: 0.0)
                        val v = values[a.id]?.num() ?: return@forEach
                        val delta = v - balanceOf(a, txns)
                        if (abs(delta) >= 0.005) Graph.dao.upsertAccount(a.copy(initial = a.initial + delta))
                    }
                    onDismiss()
                }
            }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/** Оставить два счёта: карта Сбера и накопительный; остальные слить в карту и пересчитать остатки. */
@Composable
fun ConsolidateAccountsButton(cur: String) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = { ask = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(btnLabel("Только карта Сбера и накопительный")) }
    result?.let { Text(it, fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp)) }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(btnLabel("Оставить два счёта?")) },
        text = {
            Text(
                "Останутся «Сбер» (дебетовая карта — все доходы и расходы) и «Накопительный счёт». Операции остальных счетов перейдут на карту, " +
                    "переводы между ними и картой исчезнут, записи, совпавшие с выпиской, уберутся, остатки пересчитаются по выпискам.",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                ask = false
                scope.launch {
                    result = runCatching { com.dasein.poryadok.system.AccountsCleanup.consolidate(ctx) }.fold(
                        { r -> "Готово: слито счетов ${r.merged}, перенесено операций ${r.moved}, убрано переводов ${r.removedTransfers}, дублей ${r.removedDuplicates}. Карта: ${Money.format(r.card, cur)}, накопительный: ${Money.format(r.savings, cur)}." },
                        { "Не получилось: ${it.message}" },
                    )
                    AccountMeta.version.value++
                }
            }) { Text(btnLabel("Оставить два")) }
        },
        dismissButton = { TextButton(onClick = { ask = false }) { Text(btnLabel("Отмена")) } },
    )
}

// ---------- Импорт выписки ----------

/** Импорт выписки из приложения банка: CSV, Excel или PDF → предпросмотр с отметками → операции без дублей. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatementImportScreen(nav: NavHostController, settings: Settings) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val cur = settings.currency
    val accounts by observe(emptyList()) { Graph.dao.accounts() }
    /** null — новый счёт с названием из выписки. */
    var account by remember { mutableStateOf<Long?>(null) }
    var accountPicked by remember { mutableStateOf(false) }
    var linked by remember { mutableStateOf<Long?>(null) }
    var transfers by remember { mutableStateOf(true) }
    var ops by remember { mutableStateOf<List<Statement.Op>>(emptyList()) }
    var header by remember { mutableStateOf(Statement.Header()) }
    val plans = remember { mutableStateListOf<StatementImport.Plan>() }
    val picked = remember { mutableStateMapOf<Int, Boolean>() }
    var rawText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    suspend fun replan() {
        val acc = account
        val link = linked ?: StatementImport.defaultLinked(ctx, Graph.dao.accountsNow(), acc, header)
        val p = StatementImport.plan(ops, header, acc?.toString() ?: header.title, transfers, link)
        plans.clear(); plans.addAll(p)
        picked.clear(); p.indices.forEach { picked[it] = true }
    }

    suspend fun load(r: List<Statement.Op>, h: Statement.Header) {
        ops = r; header = h
        if (!accountPicked) account = Graph.dao.accountsNow().firstOrNull { it.name.equals(if (h.savings) com.dasein.poryadok.system.AccountsCleanup.SAVINGS else com.dasein.poryadok.system.Sber.ACCOUNT, true) }?.id
        replan()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = "Читаю выписку…"; message = null; done = false
            runCatching { StatementFiles.read(ctx, uri) }.fold(
                { r ->
                    rawText = r.text
                    load(r.ops, r.header)
                    message = if (r.ops.isEmpty()) "В файле (${r.kind}) не нашлось операций. Попробуйте «Разобрать с ИИ» или другой формат выписки."
                    else "${r.kind}" + (if (r.header.title.isNotBlank()) " · «${r.header.title}»" else "") + ": операций ${r.ops.size}."
                },
                { message = "Не удалось прочитать: ${it.message}" },
            )
            busy = null
        }
    }

    Screen("Импорт выписки", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                Hint(
                    "fin_import_how",
                    "Сбер: «Выписки и справки» → «Выписка по счёту» (карта, накопительный счёт, вклад) → PDF → «Поделиться» → сохранить файл. " +
                        "Т-Банк: «Выписка» (PDF) или в веб-версии «Выгрузить» в CSV/Excel. Альфа, ВТБ, Озон: «Выписка» в PDF или Excel. " +
                        "Загружайте выписки сколько угодно раз и с пересечением дат: уже записанные операции обновятся, а пришедшие SMS 900 и пуши заменятся версией из выписки — ничего не задвоится. " +
                        "Файл разбирается на телефоне, в интернет ничего не уходит.",
                    title = "Как получить выписку",
                )
                Gap(8.dp)
                Button(
                    onClick = { picker.launch(arrayOf("application/pdf", "text/*", "text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "application/octet-stream", "*/*")) },
                    enabled = busy == null, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) { Text(btnLabel("Выбрать файл выписки")) }
                busy?.let {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("  $it", fontSize = 14.sp)
                    }
                }
                message?.let { Tile(Modifier.padding(top = 10.dp)) { Text(it, fontSize = 14.sp) } }
                if (done) SberFollowUp(nav)
                if (rawText.isNotBlank() && ops.isEmpty() && Ai.enabled(ctx)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = "ИИ разбирает выписку…"
                                runCatching { StatementFiles.aiParse(ctx, rawText) }.fold(
                                    { load(it, header); message = "ИИ нашёл операций: ${it.size}. Проверьте суммы перед импортом." },
                                    { message = "ИИ не помог: ${it.message}" },
                                )
                                busy = null
                            }
                        },
                        enabled = busy == null, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { com.dasein.poryadok.ui.common.AiLabel("Разобрать с ИИ") }
                    Text("Отправит текст выписки бесплатной модели (OpenRouter) — только по нажатию.", fontSize = 11.sp, color = extra.dim)
                }
                if (plans.isNotEmpty()) {
                    if (header.title.isNotBlank() || header.closing != null) Tile(Modifier.padding(top = 10.dp)) {
                        if (header.title.isNotBlank()) Text("«${header.title}»" + if (header.number.isNotBlank()) " ·•${header.number.takeLast(4)}" else "", fontWeight = FontWeight.SemiBold)
                        header.rate?.let { Text("Ставка ${it.plain()}% годовых" + if (header.savings) " · сберегательный счёт" else "", fontSize = 13.sp, color = extra.dim) }
                        if (header.closing != null && header.closingDay != null) Text("Остаток на ${Dates.label(header.closingDay!!)}: ${Money.format(header.closing!!, cur)} — счёт выровняется по нему", fontSize = 13.sp, color = extra.dim)
                    }
                    SectionTitle("На какой счёт")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val newName = if (header.savings) com.dasein.poryadok.system.AccountsCleanup.SAVINGS else com.dasein.poryadok.system.Sber.ACCOUNT
                        if (accounts.none { it.name.equals(newName, true) }) Pill("＋ $newName", account == null) {
                            account = null; accountPicked = true; scope.launch { replan() }
                        }
                        accounts.forEach { a -> Pill(a.name, a.id == account) { account = a.id; accountPicked = true; scope.launch { replan() } } }
                    }
                    val trCount = ops.count { it.transfer }
                    if (trCount > 0) {
                        SectionTitle("Переводы между своими счетами ($trCount)")
                        Segments(listOf(true to "Перевод", false to "Доход / расход"), transfers, { transfers = it; scope.launch { replan() } })
                        Text(
                            if (transfers) "Не считаются ни доходом, ни расходом — деньги просто переходят между вашими счетами. Второй счёт:"
                            else "Каждый перевод станет доходом или расходом этого счёта (может задвоить траты с карты).",
                            fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                        )
                        if (transfers) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val def = StatementImport.defaultLinked(ctx, accounts, account, header)
                            if (def == null) Pill("＋ " + if (header.savings) com.dasein.poryadok.system.Sber.ACCOUNT else "Накопительный счёт", linked == null) { linked = null; scope.launch { replan() } }
                            accounts.filter { it.id != account }.forEach { a ->
                                Pill(a.name, linked == a.id || (linked == null && a.id == def)) { linked = a.id; scope.launch { replan() } }
                            }
                        }
                    }
                    val n = picked.count { it.value }
                    val sel = plans.indices.filter { picked[it] == true }.map { plans[it] }
                    val inc = sel.filter { it.op.income && !(it.op.transfer && transfers) }.sumOf { it.op.amount }
                    val exp = sel.filter { !it.op.income && !(it.op.transfer && transfers) }.sumOf { it.op.amount }
                    Tile(Modifier.padding(top = 10.dp)) {
                        Text("Отмечено $n из ${plans.size}", fontWeight = FontWeight.SemiBold)
                        Text("доходы ${Money.format(inc, cur)} · расходы ${Money.format(exp, cur)}", fontSize = 13.sp)
                        Text(
                            "новых ${sel.count { it.status == StatementImport.Status.NEW }} · обновятся ${sel.count { it.status == StatementImport.Status.UPDATE }} · " +
                                "заменят записи из SMS/пушей ${sel.count { it.status == StatementImport.Status.REPLACE }}",
                            fontSize = 12.sp, color = extra.dim,
                        )
                    }
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { plans.indices.forEach { picked[it] = true } }) { Text(btnLabel("Все")) }
                        TextButton(onClick = { plans.indices.forEach { picked[it] = plans[it].status == StatementImport.Status.NEW } }) { Text(btnLabel("Только новые")) }
                        TextButton(onClick = { plans.indices.forEach { picked[it] = false } }) { Text(btnLabel("Снять")) }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                busy = "Сохраняю…"
                                val r = runCatching {
                                    val accId = StatementImport.account(ctx, account, header)
                                    val linkId = StatementImport.linkedAccount(ctx, linked, accId, header)
                                    // План пересчитываем для итогового счёта, чтобы дубли искались верно.
                                    val fresh = StatementImport.plan(ops, header, accId.toString(), transfers, linkId)
                                    val keys = sel.map { it.key }.toSet()
                                    StatementImport.apply(ctx, fresh.filter { it.key in keys }, accId, linkId, transfers, header)
                                }
                                busy = null
                                r.onSuccess { res ->
                                    message = "Готово: новых ${res.added}, обновлено ${res.updated}, заменено дублей ${res.replaced}." +
                                        (res.aligned?.let { " Остаток счёта выровнен по выписке: ${Money.format(it, cur)}." } ?: "")
                                    AccountMeta.version.value++
                                    plans.clear(); picked.clear(); ops = emptyList(); rawText = ""; done = true
                                }.onFailure { message = "Не получилось: ${it.message}" }
                            }
                        },
                        enabled = n > 0 && busy == null, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 50.dp),
                    ) { Text(btnLabel("Импортировать") + " ($n)") }
                }
            }
            itemsIndexed(plans) { i, p ->
                val r = p.op
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Checkbox(picked[i] == true, { picked[i] = it })
                    Column(Modifier.weight(1f)) {
                        Text(r.text.ifBlank { "Без описания" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        Text(
                            Dates.label(r.day) + (r.category?.let { " · $it" } ?: "") + when (p.status) {
                                StatementImport.Status.UPDATE -> " · уже загружена — обновится"
                                StatementImport.Status.REPLACE -> " · заменит запись из SMS/пуша"
                                else -> ""
                            },
                            fontSize = 12.sp, color = if (p.status == StatementImport.Status.NEW) extra.dim else extra.warn,
                        )
                    }
                    val tr = r.transfer && transfers
                    Text(
                        (if (tr) "⇄ " else if (r.income) "+" else "−") + Money.format(r.amount, cur), fontWeight = FontWeight.SemiBold,
                        color = if (tr) extra.dim else if (r.income) extra.ok else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** После выписки: дальше записи ведутся сами — из SMS 900 и пушей СберБанк Онлайн, непонятные разбирает ИИ. */
@Composable
private fun SberFollowUp(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(com.dasein.poryadok.system.SmsAi.autoOn(ctx)) }
    val listener = com.dasein.poryadok.system.Sber.listenerEnabled(ctx)
    val sms = com.dasein.poryadok.system.Sber.smsAllowed(ctx)
    Tile(Modifier.padding(top = 10.dp)) {
        Text(btnLabel("Дальше — автоматически"), fontWeight = FontWeight.SemiBold)
        Text(
            "Новые операции после выписки приложение записывает само: из пушей СберБанк Онлайн (${if (listener) "включено ✓" else "выключено"}) " +
                "и SMS с номера 900 (${if (sms) "доступ есть ✓" else "нет доступа"}). Когда загрузите следующую выписку, эти записи заменятся её версией.",
            fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp),
        )
        if (!listener || !sms) OutlinedButton(onClick = { nav.navigate(com.dasein.poryadok.ui.Routes.SBER) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(btnLabel("Включить пуши и SMS"))
        }
        if (sms) OutlinedButton(
            onClick = {
                scope.launch {
                    info = "Читаю SMS…"
                    val last = StatementImport.lastDay()
                    val days = last?.let { (Dates.today() - it + 2).toInt().coerceIn(1, 400) } ?: 90
                    val (seen, added) = com.dasein.poryadok.system.Sber.importSms(ctx, days)
                    info = "SMS за $days дн.: просмотрено $seen, новых операций $added (совпавшие с выпиской не добавлены)."
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) { Text(btnLabel("Догрузить SMS 900 после выписки")) }
        info?.let { Text(it, fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(btnLabel("Непонятные SMS и пуши — разбирать ИИ сам"), fontSize = 14.sp)
                Text(
                    if (Ai.enabled(ctx)) "Сообщения, которые программа не поняла, отправляются бесплатной модели (OpenRouter) и записываются без вашего участия."
                    else "Нужен бесплатный ключ OpenRouter в настройках ИИ.",
                    fontSize = 11.sp, color = extra.dim,
                )
            }
            androidx.compose.material3.Switch(auto, { auto = it; com.dasein.poryadok.system.SmsAi.setAuto(ctx, it) }, enabled = Ai.enabled(ctx))
        }
    }
}

// ---------- ИИ-анализ финансов ----------

private data class Suggestion(val txnId: Long, val kind: Int, val text: String, val category: String? = null, val income: Boolean? = null, val twinId: Long? = null) {
    companion object { const val DUP = 0; const val CAT = 1; const val TYPE = 2 }
}

/** Возможные дубли без ИИ: та же сумма и тип, день ±1, тот же счёт. */
private fun localDuplicates(txns: List<Txn>): List<Pair<Txn, Txn>> {
    val list = txns.filter { it.type != TxnType.TRANSFER }.sortedBy { it.day }
    val out = mutableListOf<Pair<Txn, Txn>>()
    val used = mutableSetOf<Long>()
    list.forEachIndexed { i, a ->
        if (a.id in used) return@forEachIndexed
        for (j in i + 1 until list.size) {
            val b = list[j]
            if (b.day - a.day > 1) break
            if (b.id !in used && b.type == a.type && b.accountId == a.accountId && abs(b.amount - a.amount) < 0.005) {
                out += a to b; used += b.id; break
            }
        }
    }
    return out
}

/** ИИ-анализ: дубли, неверные категории, доходы под видом расходов, итоги и советы. */
@Composable
fun FinanceAiScreen(nav: NavHostController, settings: Settings) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val cur = settings.currency
    val txns by observe(emptyList()) { Graph.dao.txns() }
    val cats by observe(emptyList()) { Graph.dao.categories() }
    val accounts by observe(emptyList()) { Graph.dao.accounts() }
    var months by remember { mutableStateOf(3) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf("") }
    var advice by remember { mutableStateOf<List<String>>(emptyList()) }
    val suggestions = remember { mutableStateListOf<Suggestion>() }
    val since = Dates.today() - months * 31L
    val period = txns.filter { it.day >= since }
    val dups = remember(period) { localDuplicates(period) }

    fun analyze() = scope.launch {
        busy = true; error = null
        runCatching {
            val catName = cats.associate { it.id to it.name }
            val accName = accounts.associate { it.id to it.name }
            val lines = period.sortedByDescending { it.day }.take(400).joinToString("\n") { t ->
                val sign = when (t.type) { TxnType.INCOME -> "+"; TxnType.EXPENSE -> "-"; else -> "⇄" }
                "${t.id}|${Dates.label(t.day)}|$sign${t.amount.plain()}|${catName[t.categoryId] ?: "-"}|${accName[t.accountId] ?: "-"}|${t.note.take(60)}"
            }
            val system = "Ты финансовый аналитик. Тебе дан список операций в формате id|дата|±сумма|категория|счёт|подпись " +
                "(+ доход, - расход, ⇄ перевод между своими счетами). Категории расходов: ${cats.filter { !it.income }.joinToString { it.name }}. " +
                "Категории доходов: ${cats.filter { it.income }.joinToString { it.name }}. Найди: 1) дубликаты — одна и та же операция записана дважды " +
                "(одинаковая сумма, близкие даты, похожие подписи); 2) операции с явно неверной категорией — предложи категорию строго из списка; " +
                "3) операции, где доход записан расходом или наоборот (переводы от людей, зарплата, возвраты, кэшбэк — доходы); " +
                "4) короткий итог по-русски: куда уходят деньги, крупные и регулярные траты, что изменилось; 5) 3–5 конкретных советов. " +
                "Не выдумывай операции и id. Ответ строго JSON: {\"summary\":\"…\",\"duplicates\":[{\"id\":1,\"twin\":2,\"why\":\"…\"}]," +
                "\"recategorize\":[{\"id\":1,\"category\":\"…\",\"why\":\"…\"}],\"type_fix\":[{\"id\":1,\"income\":true,\"why\":\"…\"}],\"advice\":[\"…\"]}"
            val o = Ai.extractJson(Ai.ask(ctx, system, lines)) ?: throw Ai.AiError("ИИ ответил не по формату — попробуйте ещё раз.")
            val ids = period.map { it.id }.toSet()
            fun arr(k: String) = (o[k] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            fun str(j: JsonObject, k: String) = (j[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            fun id(j: JsonObject, k: String) = (j[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
            suggestions.clear()
            arr("duplicates").forEach { j -> val i = id(j, "id"); val t = id(j, "twin"); if (i != null && t != null && i in ids && t in ids && i != t) suggestions += Suggestion(i, Suggestion.DUP, str(j, "why"), twinId = t) }
            arr("recategorize").forEach { j -> val i = id(j, "id"); val c = str(j, "category"); if (i != null && i in ids && cats.any { it.name.equals(c, true) }) suggestions += Suggestion(i, Suggestion.CAT, str(j, "why"), category = c) }
            arr("type_fix").forEach { j -> val i = id(j, "id"); if (i != null && i in ids) suggestions += Suggestion(i, Suggestion.TYPE, str(j, "why"), income = str(j, "income") == "true") }
            summary = (o["summary"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            advice = (o["advice"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        }.onFailure { error = it.message }
        busy = false
    }

    suspend fun apply(s: Suggestion) {
        val dao = Graph.dao
        val t = dao.txnById(s.txnId) ?: return
        when (s.kind) {
            Suggestion.DUP -> dao.deleteTxn(t)
            Suggestion.CAT -> {
                val c = dao.categoriesNow().firstOrNull { it.name.equals(s.category, true) } ?: return
                dao.upsertTxn(t.copy(categoryId = c.id, type = if (c.income) TxnType.INCOME else TxnType.EXPENSE))
            }
            Suggestion.TYPE -> {
                val inc = s.income == true
                val c = dao.categoriesNow().filter { it.income == inc }.let { l -> l.firstOrNull { it.name == "Другое" } ?: l.firstOrNull() }
                dao.upsertTxn(t.copy(type = if (inc) TxnType.INCOME else TxnType.EXPENSE, categoryId = c?.id ?: t.categoryId))
            }
        }
        suggestions.remove(s)
    }

    Screen("ИИ-анализ финансов", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Проверка записей: дубли, неверные категории и доходы, записанные расходами, плюс итог и советы. " +
                    "Дубли по сумме и дате ищутся на телефоне сразу; ИИ-анализ отправляет операции за выбранный период бесплатной модели (OpenRouter) только по кнопке.",
                fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
            )
            Segments(listOf(1 to "Месяц", 3 to "3 месяца", 6 to "Полгода", 12 to "Год"), months, { months = it }, Modifier.padding(top = 10.dp))
            Text("Операций за период: ${period.size}", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))

            SectionTitle("Похожие на дубли (без ИИ)")
            if (dups.isEmpty()) Text(btnLabel("Явных дублей нет: нет пар с одинаковой суммой на одном счёте в соседние дни."), fontSize = 13.sp, color = extra.dim)
            dups.take(30).forEach { (a, b) ->
                Tile(Modifier.padding(bottom = 6.dp), padding = 10.dp) {
                    Text("${Money.format(a.amount, cur)} · ${Dates.label(a.day)} и ${Dates.label(b.day)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("«${a.note.ifBlank { "—" }}» и «${b.note.ifBlank { "—" }}»", fontSize = 12.sp, color = extra.dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row {
                        TextButton(onClick = { scope.launch { Graph.dao.deleteTxn(b) } }) { Text(btnLabel("Удалить вторую")) }
                        TextButton(onClick = { nav.navigate(com.dasein.poryadok.ui.Routes.txn(b.id)) }) { Text(btnLabel("Открыть")) }
                    }
                }
            }

            SectionTitle("ИИ-анализ")
            if (!Ai.enabled(ctx)) Text(btnLabel("Нужен бесплатный ключ OpenRouter: «Ещё» → «Настройки» → «ИИ-помощник»."), fontSize = 13.sp, color = extra.warn)
            else Button(onClick = { analyze() }, enabled = !busy && period.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                com.dasein.poryadok.ui.common.AiLabel("Проанализировать с ИИ", busy, "ИИ анализирует…")
            }
            if (busy) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text(btnLabel("  Это занимает до минуты"), fontSize = 13.sp) }
            error?.let { Text(it, color = extra.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (summary.isNotBlank()) Tile(Modifier.padding(top = 10.dp)) {
                Text(btnLabel("Итог"), fontWeight = FontWeight.SemiBold)
                Text(summary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
            }
            if (suggestions.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Исправления (${suggestions.size})", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { suggestions.toList().forEach { apply(it) } } }) { Text(btnLabel("Применить все")) }
                }
                suggestions.toList().forEach { s ->
                    val t = txns.firstOrNull { it.id == s.txnId } ?: return@forEach
                    Tile(Modifier.padding(top = 6.dp), padding = 10.dp) {
                        Text("${if (t.type == TxnType.INCOME) "+" else "−"}${Money.format(t.amount, cur)} · ${Dates.label(t.day)} · ${t.note.ifBlank { "без подписи" }}", fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (s.kind) {
                                Suggestion.DUP -> "Дубль" + (txns.firstOrNull { it.id == s.twinId }?.let { " операции от ${Dates.label(it.day)}" } ?: "")
                                Suggestion.CAT -> "Категория → «${s.category}»"
                                else -> if (s.income == true) "Это доход, а не расход" else "Это расход, а не доход"
                            },
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
                        )
                        if (s.text.isNotBlank()) Text(s.text, fontSize = 12.sp, color = extra.dim)
                        Row {
                            TextButton(onClick = { scope.launch { apply(s) } }) { Text(if (s.kind == Suggestion.DUP) "Удалить дубль" else "Применить") }
                            TextButton(onClick = { suggestions.remove(s) }) { Text(btnLabel("Пропустить")) }
                        }
                    }
                }
            }
            if (advice.isNotEmpty()) Tile(Modifier.padding(top = 10.dp)) {
                Text(btnLabel("Советы"), fontWeight = FontWeight.SemiBold)
                advice.forEach { Text("• $it", fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
            Gap(40.dp)
        }
    }
}
