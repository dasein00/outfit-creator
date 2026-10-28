package com.dasein.poryadok.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.BarChart
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.Donut
import com.dasein.poryadok.ui.common.FitText
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.LineChart
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Series
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val FIN_INFO_ROUTE = "finInfo"

/** Подпись операции без служебного: «Сбер: Пятёрочка 1234 · Моск» → «пятёрочка». */
internal fun merchantOf(note: String): String =
    note.removePrefix("Сбер:").substringBefore(" · ").substringBefore(':').replace(Regex("[0-9*•#№]+"), " ")
        .replace(Regex("\\s+"), " ").trim().lowercase().take(32)

/**
 * Инфографика финансов: капитал, поток денег, структура трат и доходов, места, подписки, календарь, проценты по вкладам.
 * Всё считается на телефоне и обновляется сразу, как только появляется новая операция (выписка, SMS, пуш, вручную).
 */
private fun monthShort(ym: YearMonth) = MONTHS[ym.monthValue - 1]

/** Доходы и расходы по последним [n] месяцам. */
private fun flowByMonth(txns: List<Txn>, n: Int): List<Triple<YearMonth, Double, Double>> {
    val now = YearMonth.now()
    return (n - 1 downTo 0).map { k ->
        val ym = now.minusMonths(k.toLong())
        val r = ym.atDay(1).toEpochDay()..ym.atEndOfMonth().toEpochDay()
        val l = txns.filter { it.day in r }
        Triple(ym, l.filter { it.type == TxnType.INCOME }.sumOf { it.amount }, l.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount })
    }
}

/** Капитал на конец каждого из последних [n] месяцев (переводы между своими счетами его не меняют). */
internal fun capitalByMonth(accounts: List<Account>, txns: List<Txn>, n: Int): List<Pair<YearMonth, Double>> {
    if (txns.isEmpty()) return emptyList()
    val ids = accounts.map { it.id }.toSet()
    val start = accounts.sumOf { it.initial }
    val first = YearMonth.from(LocalDate.ofEpochDay(txns.minOf { it.day }))
    val now = YearMonth.now()
    return (n - 1 downTo 0).map { now.minusMonths(it.toLong()) }.filter { !it.isBefore(first) }.map { ym ->
        val end = ym.atEndOfMonth().toEpochDay()
        var b = start
        txns.filter { it.day <= end }.forEach { t ->
            when (t.type) {
                TxnType.INCOME -> if (t.accountId in ids) b += t.amount
                TxnType.EXPENSE -> if (t.accountId in ids) b -= t.amount
                TxnType.TRANSFER -> { if (t.accountId in ids) b -= t.amount; if (t.toAccountId in ids) b += t.amount }
            }
        }
        ym to b
    }
}

/** Регулярные траты: одно место 3+ месяца подряд с похожей суммой (разброс до 20%). (название, в месяц, месяцев) */
internal fun recurringSpends(txns: List<Txn>): List<Triple<String, Double, Int>> {
    val since = LocalDate.now().minusMonths(6).toEpochDay()
    return txns.filter { it.type == TxnType.EXPENSE && it.day >= since }.groupBy { merchantOf(it.note) }.filterKeys { it.length >= 3 }
        .mapNotNull { (name, l) ->
            val byMonth = l.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.day)) }.mapValues { e -> e.value.sumOf { it.amount } }
            if (byMonth.size < 3) return@mapNotNull null
            val v = byMonth.values
            val avg = v.average()
            val sd = sqrt(v.sumOf { (it - avg) * (it - avg) } / v.size)
            if (avg <= 0 || sd / avg > 0.2) null else Triple(name, avg, byMonth.size)
        }.sortedByDescending { it.second }
}
