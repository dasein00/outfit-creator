@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import android.widget.Toast
import com.dasein.poryadok.ui.common.RichText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Recipe
import com.dasein.poryadok.data.ShoppingItem
import com.dasein.poryadok.logic.Fridge
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch

const val WHAT_TO_COOK_ROUTE = "whatToCook"

/** Текст рецепта, который нужно открыть в новом рецепте (поделились из Instagram, придумал ИИ). */
object RecipeInbox {
    private var pending: String? = null
    fun put(text: String) { pending = text }
    fun take(): String? = pending.also { pending = null }
}

/**
 * «Что приготовить из того, что есть» (как в SuperCook): пишете или отмечаете продукты, которые лежат дома, —
 * приложение показывает блюда, для которых всё есть, и те, где не хватает одного-двух продуктов.
 * Список «есть дома» общий с фильтром «Из холодильника» в каталоге.
 */
@Composable
fun WhatToCookScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val book = rememberRecipeBook()
    val settings by observe(null) { Graph.prefs.settings }
    val spec = remember(settings?.fridge) { Fridge.decode(settings?.fridge.orEmpty()) }
    val have = spec.have
    fun save(h: Set<String>) = io { Graph.prefs.update { it.copy(fridge = Fridge.encode(spec.copy(have = h, onlyHave = false))) } }
    val names: (Recipe) -> List<String> = { r -> book.ingredients[r.id].orEmpty().map { it.name } }
    // Продукты из рецептов: по частоте.
    val products = remember(book) {
        book.recipes.flatMap { names(it).distinct() }.filter { !Fridge.isStaple(it) }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }
    var input by remember { mutableStateOf("") }
    val nq = Fridge.norm(input.substringAfterLast(','))
    val suggest = remember(nq, products, have) {
        if (nq.length < 2) emptyList() else products.filter { nq in Fridge.norm(it) && it !in have }.take(8)
    }
    /** «курица, рис, помидоры» → продукты из базы (по совпадению начала слова), остальное — как написано. */
    fun addTyped() {
        val parts = input.split(',', ';', '\n').map { it.trim() }.filter { it.length >= 2 }
        val found = parts.map { p ->
            val n = Fridge.norm(p)
            products.firstOrNull { Fridge.norm(it) == n } ?: products.firstOrNull { Fridge.norm(it).startsWith(n) }
                ?: products.firstOrNull { n in Fridge.norm(it) } ?: p.replaceFirstChar { it.uppercase() }
        }
        if (found.isNotEmpty()) save(have + found)
        input = ""
    }
    val fits = remember(book, have) {
        if (have.isEmpty()) emptyList()
        else book.recipes.map { it to Fridge.fit(names(it), Fridge.Spec(have = have)) }.filter { it.second.used > 0 }
    }
    val ready = fits.filter { it.second.lacking.isEmpty() }.sortedByDescending { it.second.used }
    val one = fits.filter { it.second.lacking.size == 1 }.sortedByDescending { it.second.used }
    val two = fits.filter { it.second.lacking.size in 2..3 }.sortedWith(compareBy<Pair<Recipe, Fridge.Fit>> { it.second.lacking.size }.thenByDescending { it.second.used })
    val aiOn = remember { com.dasein.poryadok.system.Ai.enabled(ctx) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiText by remember { mutableStateOf<String?>(null) }

    Screen("Что приготовить", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Text("Напишите или отметьте, что есть дома, — покажу блюда, для которых всё есть, и где не хватает пары продуктов. Соль, масло и специи считаются имеющимися.", fontSize = 13.sp, color = extra.dim)
                Gap(8.dp)
                OutlinedTextField(
                    input, { input = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("Например: курица, рис, помидоры") },
                    trailingIcon = { if (input.isNotBlank()) TextButton(onClick = { addTyped() }) { Text("Добавить") } },
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addTyped() }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                )
                if (suggest.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    suggest.forEach { p -> Pill("+ $p", false) { save(have + p); input = input.substringBeforeLast(',', "").let { if (it.isBlank()) "" else "$it, " } } }
                }
                Gap(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Есть дома · ${have.size}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (have.isNotEmpty()) TextButton(onClick = { save(emptySet()) }) { Text("Очистить") }
                }
                if (have.isEmpty()) Text("Пока пусто. Начните вводить продукт или нажмите на частые ниже.", fontSize = 13.sp, color = extra.dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    have.sorted().forEach { p -> Pill("$p  ✕", true) { save(have - p) } }
                }
                Gap(8.dp)
                Text("Частые продукты", fontSize = 12.sp, color = extra.dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    products.filter { it !in have }.take(24).forEach { p -> Pill(p, false) { save(have + p) } }
                }
                if (have.isNotEmpty() && aiOn) {
                    Gap(10.dp)
                    OutlinedButton(enabled = !aiBusy, modifier = Modifier.fillMaxWidth(), onClick = {
                        aiBusy = true
                        scope.launch {
                            aiText = runCatching { aiIdea(ctx, have.toList()) }.getOrElse { "Не получилось: ${it.message}" }
                            aiBusy = false
                        }
                    }) { Text(if (aiBusy) "ИИ придумывает…" else "✨ ИИ: придумать блюдо из этих продуктов") }
                    aiText?.let { t ->
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(extra.card).padding(12.dp)) {
                            RichText(t)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                                Button(onClick = { RecipeInbox.put(t); nav.navigate(Routes.recipeEdit(0)) }) { Text("Сохранить как рецепт") }
                                TextButton(onClick = { aiText = null }) { Text("Скрыть") }
                            }
                        }
                    }
                }
                Gap(6.dp)
            }
            fun section(title: String, list: List<Pair<Recipe, Fridge.Fit>>) {
                if (list.isEmpty()) return
                item(key = "h_$title") { SectionTitle("$title · ${list.size}") }
                items(list.take(60), key = { "${title}_${it.first.id}" }) { (r, fit) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(extra.card)
                            .clickable { nav.navigate(Routes.recipe(r.id)) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RecipeThumb(r, 44.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(r.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(
                                "из ваших продуктов: ${fit.used}" + if (r.totalMin() > 0) " · ${r.totalMin()} мин" else "",
                                fontSize = 12.sp, color = extra.dim,
                            )
                            if (fit.lacking.isNotEmpty()) Text("Не хватает: " + fit.lacking.joinToString(", "), fontSize = 12.sp, color = extra.warn)
                        }
                        if (fit.lacking.isNotEmpty()) TextButton(onClick = {
                            io { fit.lacking.forEach { n -> Graph.extra.upsertShopping(ShoppingItem(name = n, note = r.name, key = Fridge.norm(n))) } }
                            Toast.makeText(ctx, "Добавлено в покупки: ${fit.lacking.joinToString(", ")}", Toast.LENGTH_SHORT).show()
                        }) { Text("В покупки") }
                    }
                }
            }
            section("Можно приготовить сейчас", ready)
            section("Не хватает одного продукта", one)
            section("Не хватает 2–3 продуктов", two)
            if (have.isNotEmpty() && fits.isEmpty()) item { Text("Блюд с этими продуктами в книге нет. Добавьте ещё продуктов или попросите ИИ придумать блюдо.", color = extra.dim, modifier = Modifier.padding(vertical = 12.dp)) }
            item { Gap(40.dp) }
        }
    }
}

private suspend fun aiIdea(ctx: android.content.Context, have: List<String>): String {
    val system = "Ты шеф-повар домашней кухни. Предложи ОДНО простое вкусное блюдо из продуктов пользователя " +
        "(можно добавить соль, перец, масло, специи, воду; другие продукты — только если без них никак, и отметь их). " +
        "Оформи красиво в Markdown: «# Название», короткое описание одной строкой, «## Ингредиенты» списком с граммовками, " +
        "«## Приготовление» нумерованными шагами с огнём и временем, «## Совет». Пиши по-русски."
    return com.dasein.poryadok.system.Ai.ask(ctx, system, "Есть дома: " + have.joinToString(", ")).trim()
}
