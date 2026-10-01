@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.logic.Fridge
import com.dasein.poryadok.ui.common.FilterSheet
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.theme.LocalExtra

private val MODES = listOf("★ Основной", "✓ Есть", "✕ Нет")

/**
 * Лист «Из холодильника»: выбираете режим (основной / есть / нет) и отмечаете продукты из списка.
 * Кнопка внизу сразу показывает, сколько блюд получится.
 */
@Composable
fun FridgeSheet(
    products: List<Pair<String, Int>>,
    spec: Fridge.Spec,
    countFor: (Fridge.Spec) -> Int,
    onDismiss: () -> Unit,
    onApply: (Fridge.Spec) -> Unit,
) {
    var main by remember { mutableStateOf(spec.main) }
    var have by remember { mutableStateOf(spec.have) }
    var missing by remember { mutableStateOf(spec.missing) }
    var onlyHave by remember { mutableStateOf(spec.onlyHave) }
    var mode by remember { mutableIntStateOf(if (spec.main.isEmpty()) 0 else 1) }
    var q by remember { mutableStateOf("") }
    val cur = Fridge.Spec(main, have, missing, onlyHave)
    val count = remember(cur) { countFor(cur) }
    val extra = LocalExtra.current
    val scheme = MaterialTheme.colorScheme

    fun toggle(name: String) {
        val inMode = when (mode) { 0 -> name in main; 1 -> name in have; else -> name in missing }
        main -= name; have -= name; missing -= name
        if (!inMode) when (mode) {
            0 -> main += name
            1 -> have += name
            else -> missing += name
        }
    }

    FilterSheet(
        "Из холодильника", onDismiss,
        onReset = if (cur.isEmpty && !onlyHave) null else ({ main = emptySet(); have = emptySet(); missing = emptySet(); onlyHave = false }),
        applyLabel = if (cur.isEmpty) "Показать все блюда" else "Показать блюда: $count",
        onApply = { onApply(cur) },
    ) {
        Text(
            "Выберите, что отмечаете, и нажимайте на продукты. Основной — блюдо обязательно с ним. " +
                "Есть — что лежит дома. Нет — блюда с этим продуктом не покажутся.",
            fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
        )
        Gap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MODES.forEachIndexed { i, m -> Pill(m, mode == i) { mode = i } }
        }
        if (!cur.isEmpty) {
            Gap(10.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                main.sorted().forEach { n -> Pill("★ $n  ✕", true) { main -= n } }
                have.sorted().forEach { n -> Pill("✓ $n  ✕", false) { have -= n } }
                missing.sorted().forEach { n -> Pill("✕ $n", false) { missing -= n } }
            }
        }
        Gap(8.dp)
        Row(Modifier.fillMaxWidth().clickable { onlyHave = !onlyHave }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Только если всё есть", fontSize = 15.sp)
                Text("Соль, вода, сахар, масло и специи считаются имеющимися", fontSize = 12.sp, color = extra.dim)
            }
            Switch(onlyHave, { onlyHave = it })
        }
        Gap(8.dp)
        OutlinedTextField(
            q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Найти продукт") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(14.dp),
        )
        val nq = Fridge.norm(q)
        val shown = products.filter { nq.isEmpty() || nq in Fridge.norm(it.first) }
        LazyColumn(Modifier.heightIn(max = 300.dp).padding(top = 4.dp)) {
            items(shown, key = { it.first }) { (name, n) ->
                val mark = when (name) { in main -> "★"; in have -> "✓"; in missing -> "✕"; else -> "" }
                Row(
                    Modifier.fillMaxWidth().clickable { toggle(name) }.padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        mark, Modifier.width(26.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = when (mark) { "✕" -> extra.warn; "" -> extra.dim; else -> scheme.primary },
                    )
                    Text(name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("$n блюд", fontSize = 12.sp, color = extra.dim)
                }
            }
            if (shown.isEmpty()) item { Text("Такого продукта нет в рецептах", color = extra.dim, modifier = Modifier.padding(vertical = 12.dp)) }
        }
    }
}
