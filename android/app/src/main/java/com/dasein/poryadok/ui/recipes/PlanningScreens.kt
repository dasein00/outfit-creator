@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MealPreset
import com.dasein.poryadok.data.MealPresetItem
import com.dasein.poryadok.data.MealRepeat
import com.dasein.poryadok.data.PlanStatus
import com.dasein.poryadok.data.RecipeRepo
import com.dasein.poryadok.data.ShoppingItem
import com.dasein.poryadok.logic.Cooking
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.MealType
import com.dasein.poryadok.logic.SHOP_CATEGORIES
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.CheckDot
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Stat
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val WEEK = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

// ---------- Шаблоны и повторы ----------

@Composable
fun PresetsScreen(nav: NavHostController) {
    val x = Graph.extra
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val book = rememberRecipeBook()
    val presets by observe(emptyList()) { x.presets() }
    val items by observe(emptyList()) { x.presetItems() }
    val repeats by observe(emptyList()) { x.repeats() }
    var rename by remember { mutableStateOf<MealPreset?>(null) }
    var apply by remember { mutableStateOf<MealPreset?>(null) }
    var delete by remember { mutableStateOf<MealPreset?>(null) }
    var newPreset by remember { mutableStateOf(false) }
    var editRepeat by remember { mutableStateOf<MealRepeat?>(null) }

    Screen("Шаблоны и повторы", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                Text("Шаблон — готовый день или неделя меню. Применяйте к любому периоду.", color = extra.dim, fontSize = 13.sp)
                Gap(8.dp)
                Button(onClick = { newPreset = true }, Modifier.fillMaxWidth()) { Text("+ Новый шаблон") }
            }
            if (presets.isEmpty()) item { Empty(Ic.folder, "Шаблонов нет", "Сохраните день или неделю из меню как шаблон — или создайте пустой и наполните.") }
            items(presets, key = { "p${it.id}" }) { p ->
                val list = items.filter { it.presetId == p.id }
                Tile(Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { nav.navigate(Routes.preset(p.id)) }) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                (if (p.days == 7) "Неделя" else "День") + " · ${list.size} блюд · ${list.sumOf { book.macros(it.recipeId).kcal * it.servings }.let { k -> (if (p.days == 7) k / 7 else k).roundToInt() }} ккал/день",
                                fontSize = 12.sp, color = extra.dim,
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { apply = p }) { Text("Применить") }
                        TextButton(onClick = { nav.navigate(Routes.preset(p.id)) }) { Text("Изменить") }
                        TextButton(onClick = { rename = p }) { Text("Переименовать") }
                        TextButton(onClick = { io { RecipeRepo.duplicatePreset(p) } }) { Text("Дублировать") }
                        TextButton(onClick = { delete = p }) { Text("Удалить", color = extra.danger) }
                    }
                }
            }
            item {
                SectionTitle("Повторяющиеся блюда", action = "+ Добавить") {
                    editRepeat = MealRepeat(recipeId = 0, meal = MealType.BREAKFAST, fromDay = Dates.today())
                }
                Text("Например, овсянка на завтрак по будням. Блюдо само появится в меню на 5 недель вперёд.", fontSize = 12.sp, color = extra.dim)
            }
            items(repeats, key = { "r${it.id}" }) { r ->
                Tile(Modifier.padding(top = 8.dp), onClick = { editRepeat = r }) {
                    Text(book.byId(r.recipeId)?.name ?: "Рецепт удалён", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${MealType.name(r.meal)} · ${maskLabel(r.daysMask)} · ${Cooking.amount(r.servings)} порц. · с ${Dates.label(r.fromDay)}" + (r.toDay?.let { " по ${Dates.label(it)}" } ?: ""),
                        fontSize = 12.sp, color = extra.dim,
                    )
                }
            }
        }
    }
    rename?.let { p -> PresetNameDialog(p.name, onDismiss = { rename = null }) { n -> io { x.upsertPreset(p.copy(name = n)) } } }
    delete?.let { p ->
        ConfirmDialog("Удалить шаблон «${p.name}»?", "Уже составленное меню не изменится.", onDismiss = { delete = null }) { io { RecipeRepo.deletePreset(p.id) } }
    }
    if (newPreset) NewPresetDialog(onDismiss = { newPreset = false }) { name, days ->
        scope.launch {
            val id = x.upsertPreset(MealPreset(name = name, days = days, createdAt = System.currentTimeMillis()))
            nav.navigate(Routes.preset(id))
        }
    }
    apply?.let { p -> ApplyPresetDialog(p, onDismiss = { apply = null }) { Toast.makeText(ctx, "Шаблон «${p.name}» применён", Toast.LENGTH_SHORT).show() } }
    editRepeat?.let { r -> RepeatDialog(r, book, onDismiss = { editRepeat = null }) }
}

@Composable
private fun NewPresetDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var week by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый шаблон") },
        text = {
            Column {
                TextInput(name, { name = it }, "Название")
                Gap(8.dp)
                Row { Pill("День", !week) { week = false }; HGap(6.dp); Pill("Неделя", week) { week = true } }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { onCreate(name.trim(), if (week) 7 else 1); onDismiss() } }) { Text("Создать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ApplyPresetDialog(p: MealPreset, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf(if (p.days == 7) Dates.weekStart(Dates.today()) else Dates.today()) }
    var to by remember { mutableStateOf(from + (if (p.days == 7) 6 else 0)) }
    var replace by remember { mutableStateOf(false) }
    var weekdaysOnly by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Применить «${p.name}»") },
        text = {
            Column {
                Row {
                    FieldButton("С", Dates.label(from), Modifier.weight(1f)) { pick = 1 }
                    HGap(8.dp)
                    FieldButton("По", Dates.label(to), Modifier.weight(1f)) { pick = 2 }
                }
                if (p.days == 1) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Только будни (Пн–Пт)", Modifier.weight(1f)); Switch(weekdaysOnly, { weekdaysOnly = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Заменить запланированное", Modifier.weight(1f)); Switch(replace, { replace = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (weekdaysOnly) {
                        var d = from
                        while (d <= to) {
                            if (Dates.day(d).dayOfWeek.value <= 5) RecipeRepo.applyPreset(p.id, d, d, replace)
                            d++
                        }
                    } else RecipeRepo.applyPreset(p.id, from, maxOf(from, to), replace)
                    onDone(); onDismiss()
                }
            }) { Text("Применить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    if (pick == 1) DatePickDialog(from, { pick = 0 }, { it?.let { d -> from = d; if (to < d) to = d } }, allowClear = false)
    if (pick == 2) DatePickDialog(to, { pick = 0 }, { it?.let { d -> to = d } }, allowClear = false)
}

@Composable
private fun RepeatDialog(r0: MealRepeat, book: RecipeBook, onDismiss: () -> Unit) {
    var r by remember { mutableStateOf(r0) }
    var servings by remember { mutableStateOf(Cooking.amount(r0.servings)) }
    var pickRecipe by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (r0.id == 0L) "Повторяющееся блюдо" else "Изменить повтор") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FieldButton("Блюдо", book.byId(r.recipeId)?.name ?: "Выбрать…", Modifier.fillMaxWidth()) { pickRecipe = true }
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealType.order.forEach { m -> Pill(MealType.name(m), m == r.meal) { r = r.copy(meal = m) } }
                }
                Gap(8.dp)
                WeekdayMask(r.daysMask) { r = r.copy(daysMask = it) }
                Gap(8.dp)
                Row {
                    FieldButton("С", Dates.label(r.fromDay), Modifier.weight(1f)) { pick = 1 }
                    HGap(8.dp)
                    FieldButton("По", r.toDay?.let { Dates.label(it) } ?: "без конца", Modifier.weight(1f)) { pick = 2 }
                }
                Gap(8.dp)
                NumberField(servings, { servings = it }, "Порций")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (r.recipeId == 0L || r.daysMask == 0) return@TextButton
                io { RecipeRepo.saveRepeat(r.copy(servings = servings.num()?.takeIf { it > 0 } ?: 1.0)) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (r0.id != 0L) TextButton(onClick = { confirm = true }) { Text("Удалить", color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (pickRecipe) RecipePickerDialog(book, mealHint = r.meal, onDismiss = { pickRecipe = false }) { rec, s -> r = r.copy(recipeId = rec.id); servings = Cooking.amount(s) }
    if (pick == 1) DatePickDialog(r.fromDay, { pick = 0 }, { it?.let { d -> r = r.copy(fromDay = d) } }, allowClear = false)
    if (pick == 2) DatePickDialog(r.toDay, { pick = 0 }, { d -> r = r.copy(toDay = d) }, allowClear = true)
    if (confirm) ConfirmDialog("Удалить повтор?", "Будущие блюда этого повтора уберутся из меню, прошлые останутся.", onDismiss = { confirm = false }) {
        io { RecipeRepo.deleteRepeat(r0.id) }; onDismiss()
    }
}

@Composable
fun PresetEditScreen(nav: NavHostController, id: Long) {
    val x = Graph.extra
    val extra = LocalExtra.current
    val book = rememberRecipeBook()
    val presets by observe(emptyList()) { x.presets() }
    val allItems by observe(emptyList()) { x.presetItems() }
    val p = presets.firstOrNull { it.id == id }
    val items = allItems.filter { it.presetId == id }
    var addFor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    Screen(p?.name ?: "Шаблон", onBack = { nav.popBackStack() }) { pad ->
        if (p == null) { Text("Шаблон не найден", Modifier.padding(pad).padding(16.dp), color = extra.dim); return@Screen }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val days = if (p.days == 7) (0 until 7).toList() else listOf(0)
            days.forEach { di ->
                val dayItems = items.filter { it.dayIndex == di }
                if (p.days == 7) Text(WEEK[di], style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
                Text("${dayItems.sumOf { book.macros(it.recipeId).kcal * it.servings }.roundToInt()} ккал", fontSize = 12.sp, color = extra.dim)
                MealType.order.forEach { m ->
                    val ms = dayItems.filter { it.meal == m }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(MealType.name(m), Modifier.weight(1f), color = extra.dim, fontSize = 13.sp)
                        Text("+", Modifier.clip(RoundedCornerShape(8.dp)).clickable { addFor = di to m }.padding(horizontal = 10.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                    }
                    ms.forEach { it2 -> PresetItemRow(it2, book) }
                }
            }
            Gap(40.dp)
        }
    }
    addFor?.let { (di, m) ->
        RecipePickerDialog(book, mealHint = m, onDismiss = { addFor = null }) { r, s ->
            io { x.upsertPresetItem(MealPresetItem(presetId = id, dayIndex = di, meal = m, recipeId = r.id, servings = s, timeMin = MealType.defaultTime[m])) }
        }
    }
}

@Composable
private fun PresetItemRow(item: MealPresetItem, book: RecipeBook) {
    val extra = LocalExtra.current
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(book.byId(item.recipeId)?.name ?: "Рецепт удалён", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${Cooking.amount(item.servings)} порц.", fontSize = 12.sp, color = extra.dim)
        Text("✕", Modifier.clip(RoundedCornerShape(8.dp)).clickable { io { Graph.extra.deletePresetItem(item) } }.padding(8.dp), color = extra.dim)
    }
}

// ---------- Список покупок ----------

@Composable
fun ShoppingScreen(nav: NavHostController) {
    val x = Graph.extra
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by observe<List<ShoppingItem>?>(null) { x.shopping() }
    var add by remember { mutableStateOf(false) }
    var generate by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<ShoppingItem?>(null) }
    Screen(
        "Список покупок",
        onBack = { nav.popBackStack() },
        actions = { if (items.orEmpty().any { it.bought }) IconAction(Ic.trash, "Убрать купленное") { io { x.clearBought() } } },
    ) { pad ->
        val list = items
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                Row {
                    Button(onClick = { generate = true }, Modifier.weight(1f)) { Text("Из меню") }
                    HGap(8.dp)
                    OutlinedButton(onClick = { add = true }, Modifier.weight(1f)) { Text("+ Добавить") }
                }
                Gap(6.dp)
                if (list != null && list.isNotEmpty()) Text("Куплено ${list.count { it.bought }} из ${list.size}", fontSize = 12.sp, color = extra.dim)
            }
            if (list == null) return@LazyColumn
            if (list.isEmpty()) item { Empty(Ic.cart, "Список пуст", "Соберите его из меню — одинаковые продукты сложатся — или добавьте позиции вручную.") }
            val groups = list.groupBy { it.category }.toSortedMap(compareBy<String>({ c -> SHOP_CATEGORIES.indexOf(c).let { if (it < 0) 99 else it } }, { it }))
            groups.forEach { (cat, rows) ->
                item(key = "h$cat") { SectionTitle(cat) }
                items(rows, key = { it.id }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { edit = s }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckDot(s.bought, extra.ok, onClick = { io { x.upsertShopping(s.copy(bought = !s.bought)) } }, size = 24.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(s.name, textDecoration = if (s.bought) TextDecoration.LineThrough else null, color = if (s.bought) extra.dim else MaterialTheme.colorScheme.onSurface)
                            if (s.note.isNotBlank() || s.fromMenu) Text(if (s.fromMenu) "из меню" else s.note, fontSize = 11.sp, color = extra.dim)
                        }
                        if (s.amount > 0) Text("${Cooking.amount(s.amount)} ${s.unit}", color = extra.dim, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    if (generate) GenerateShoppingDialog(onDismiss = { generate = false }) { from, to ->
        scope.launch {
            val n = RecipeRepo.shoppingFromMenu(from, to)
            Toast.makeText(ctx, if (n > 0) "Собрано позиций: $n" else "В меню за период нет блюд из рецептов", Toast.LENGTH_SHORT).show()
        }
    }
    if (add) ShoppingItemDialog(ShoppingItem(name = ""), onDismiss = { add = false })
    edit?.let { ShoppingItemDialog(it, onDismiss = { edit = null }) }
}

@Composable
private fun GenerateShoppingDialog(onDismiss: () -> Unit, onGo: (Long, Long) -> Unit) {
    var from by remember { mutableStateOf(Dates.today()) }
    var to by remember { mutableStateOf(Dates.today() + 6) }
    var pick by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Покупки по меню") },
        text = {
            Column {
                Text("Ингредиенты блюд, которые ещё не съедены, сложатся по продуктам. Ручные позиции и отметки «куплено» сохранятся.", fontSize = 13.sp, color = LocalExtra.current.dim)
                Gap(8.dp)
                Row {
                    FieldButton("С", Dates.label(from), Modifier.weight(1f)) { pick = 1 }
                    HGap(8.dp)
                    FieldButton("По", Dates.label(to), Modifier.weight(1f)) { pick = 2 }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onGo(from, maxOf(from, to)); onDismiss() }) { Text("Собрать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    if (pick == 1) DatePickDialog(from, { pick = 0 }, { it?.let { d -> from = d; if (to < d) to = d } }, allowClear = false)
    if (pick == 2) DatePickDialog(to, { pick = 0 }, { it?.let { d -> to = d } }, allowClear = false)
}

@Composable
private fun ShoppingItemDialog(s0: ShoppingItem, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(s0) }
    var amount by remember { mutableStateOf(if (s0.amount > 0) Cooking.amount(s0.amount) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (s0.id == 0L) "Новая позиция" else "Позиция") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextInput(s.name, { s = s.copy(name = it) }, "Что купить")
                Gap(6.dp)
                Row {
                    NumberField(amount, { amount = it }, "Количество", Modifier.weight(1f))
                    HGap(6.dp)
                    TextInput(s.unit, { s = s.copy(unit = it) }, "Ед.", Modifier.weight(0.7f))
                }
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SHOP_CATEGORIES.forEach { c -> Pill(c, c == s.category) { s = s.copy(category = c) } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (s.name.isBlank()) return@TextButton
                io { Graph.extra.upsertShopping(s.copy(name = s.name.trim(), amount = amount.num() ?: 0.0, key = Cooking.key(s.name, s.unit))) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (s0.id != 0L) TextButton(onClick = { io { Graph.extra.deleteShopping(s0) }; onDismiss() }) { Text("Удалить", color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

// ---------- История и аналитика ----------

@Composable
fun CookHistoryScreen(nav: NavHostController) {
    val x = Graph.extra
    val extra = LocalExtra.current
    val history by observe(emptyList()) { x.history() }
    val plan by observe(emptyList()) { x.plan() }
    val food by observe(emptyList()) { Graph.dao.food() }
    var delete by remember { mutableStateOf<com.dasein.poryadok.data.CookingLog?>(null) }
    val today = Dates.today()
    val last30 = plan.filter { it.day in today - 29..today && it.status != PlanStatus.SKIPPED }
    val days = last30.map { it.day }.distinct()
    Screen("История и аналитика", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                SectionTitle("Меню за 30 дней")
                val avgK = if (days.isEmpty()) 0 else (last30.sumOf { it.kcal * it.servings } / days.size).roundToInt()
                val avgP = if (days.isEmpty()) 0 else (last30.sumOf { it.protein * it.servings } / days.size).roundToInt()
                val eaten = last30.count { it.status == PlanStatus.EATEN }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("$avgK", "ккал/день в плане", Modifier.weight(1f))
                    Stat("$avgP г", "белка/день", Modifier.weight(1f))
                    Stat(if (last30.isEmpty()) "—" else "${eaten * 100 / last30.size}%", "плана съедено", Modifier.weight(1f))
                }
                Gap(8.dp)
                val factDays = food.filter { it.day in days }.groupBy { it.day }
                val factAvg = if (factDays.isEmpty()) 0 else factDays.values.sumOf { l -> l.sumOf { it.kcal } } / factDays.size
                Text("План/факт по дням с меню: $avgK / $factAvg ккал в среднем. Запланировано ${last30.size} блюд, съедено $eaten.", fontSize = 12.sp, color = extra.dim)
                SectionTitle("Что готовили")
            }
            if (history.isEmpty()) item { Empty(Ic.book, "История пуста", "Нажмите «Приготовить» в рецепте — здесь появятся дата, порции и КБЖУ.") }
            items(history, key = { it.id }) { h ->
                Tile(Modifier.padding(bottom = 8.dp), onClick = { h.recipeId?.let { nav.navigate(Routes.recipe(it)) } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.recipeName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${Dates.label(h.day)} · приготовлено ${Cooking.amount(h.servings)} порц. · съедено ${Cooking.amount(h.eaten)} · ${h.kcal.roundToInt()} ккал",
                                fontSize = 12.sp, color = extra.dim,
                            )
                        }
                        if (h.foodEntryId == null && h.eaten > 0) TextButton(onClick = { io { RecipeRepo.historyToFood(h) } }) { Text("В питание") }
                        else if (h.foodEntryId != null) Text("в питании ✓", fontSize = 12.sp, color = extra.ok)
                        Text("✕", Modifier.clip(RoundedCornerShape(8.dp)).clickable { delete = h }.padding(8.dp), color = extra.dim)
                    }
                }
            }
        }
    }
    delete?.let { h -> ConfirmDialog("Удалить запись?", "Запись в дневнике питания, если была, останется.", onDismiss = { delete = null }) { io { x.deleteHistory(h) } } }
}
