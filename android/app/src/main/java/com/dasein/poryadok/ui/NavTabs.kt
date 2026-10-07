package com.dasein.poryadok.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.SectionEditDialog
import com.dasein.poryadok.ui.common.rememberSection
import com.dasein.poryadok.ui.more.HUB
import com.dasein.poryadok.ui.theme.LocalExtra

/**
 * Нижняя панель разделов: «Главное» и «Ещё» на месте, остальные кнопки можно заменить любым разделом
 * (долгое нажатие). Хранится в ui_state строкой маршрутов через «;».
 */
internal object NavTabs {
    private const val KEY = "nav_tabs"

    val DEFAULT = listOf(
        Tab(Routes.TODAY, "Главное", "ui:home"),
        Tab(Routes.RECIPES, "Рецепты", "food/00"),
        Tab(Routes.HEALTH_HUB, "Здоровье", "train/11"),
        Tab(Routes.PLAN, "График", "cal/00"),
        Tab(Routes.TOPS_HUB, "Топы", "sport/27"),
        Tab(Routes.MORE, "Ещё", "cal/40"),
    )

    /** Все разделы, которые можно поставить в нижнюю панель. */
    val CHOICES: List<Tab> = (
        DEFAULT.drop(1).dropLast(1) + listOf(
            Tab(Routes.calendar(0), "Календарь", "cal/23"),
            Tab(Routes.calendar(2), "Праздники", "fest/03"),
            Tab(Routes.calendar(3), "История", "cal/23"),
        ) + HUB.map { Tab(it.route, it.title, it.icon) }
        ).distinctBy { it.route }

    /** Кнопки, которые нельзя заменить: без них не попасть на главный экран и ко всем разделам. */
    fun fixed(i: Int) = i == 0 || i == DEFAULT.lastIndex

    private fun sp() = runCatching { Graph.app.getSharedPreferences("ui_state", Context.MODE_PRIVATE) }.getOrNull()

    private fun load(): List<Tab> {
        val saved = sp()?.getString(KEY, null)?.split(';')?.filter { it.isNotBlank() } ?: return DEFAULT
        if (saved.size != DEFAULT.size) return DEFAULT
        return saved.mapIndexed { i, r -> if (fixed(i)) DEFAULT[i] else (DEFAULT + CHOICES).firstOrNull { it.route == r } ?: DEFAULT[i] }
    }

    val state = mutableStateOf(load())

    private fun save(l: List<Tab>) {
        state.value = l
        sp()?.edit()?.putString(KEY, l.joinToString(";") { it.route })?.apply()
    }

    /** Поставить раздел [t] на место [i]. Если он уже есть в панели — кнопки меняются местами. */
    fun replace(i: Int, t: Tab) {
        val l = state.value.toMutableList()
        val j = l.indexOfFirst { it.route == t.route }
        if (j >= 0 && !fixed(j)) l[j] = l[i]
        l[i] = t
        save(l)
    }

    fun reset() = save(DEFAULT)
    val isDefault get() = state.value == DEFAULT
}

/** Меню по долгому нажатию на кнопку нижней панели: заменить раздел, переименовать, вернуть как было. */
@Composable
internal fun TabMenu(nav: NavHostController, index: Int, tab: Tab, onDismiss: () -> Unit) {
    val extra = LocalExtra.current
    var mode by remember { mutableStateOf(0) }
    when (mode) {
        2 -> SectionEditDialog(tab.route, tab.label, tab.icon, onDismiss)
        1 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Вместо «${tab.label}»") },
            text = {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(NavTabs.CHOICES, key = { it.route }) { c ->
                        val look = rememberSection(c.route, c.label, c.icon)
                        val inBar = NavTabs.state.value.any { it.route == c.route }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { NavTabs.replace(index, c); onDismiss(); nav.openTab(c.route) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            Glyph(look.icon, 28.dp, badge = false)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(look.name, fontWeight = if (c.route == tab.route) FontWeight.Bold else FontWeight.Normal)
                                if (inBar) Text(if (c.route == tab.route) "сейчас здесь" else "уже в панели — поменяются местами", fontSize = 11.sp, color = extra.dim)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(tab.label) },
            text = {
                Column {
                    if (!NavTabs.fixed(index)) TextButton(onClick = { mode = 1 }, Modifier.fillMaxWidth()) { Text("Заменить на другой раздел") }
                    TextButton(onClick = { mode = 2 }, Modifier.fillMaxWidth()) { Text("Изменить название и иконку") }
                    if (!NavTabs.isDefault) TextButton(onClick = { NavTabs.reset(); onDismiss() }, Modifier.fillMaxWidth()) {
                        Text("Вернуть нижнюю панель как было")
                    }
                    Text(
                        "«Главное» и «Ещё» всегда на месте, остальные четыре кнопки можно заменить любым разделом.",
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        )
    }
}
