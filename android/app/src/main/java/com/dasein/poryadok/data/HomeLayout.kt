package com.dasein.poryadok.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.dasein.poryadok.Graph

/**
 * Какие плашки на «Главном», в каком порядке и какого размера. Хранится в ui_state строкой
 * «id|размер;id|размер…». Ярлыки на любой раздел — id «go:маршрут».
 */
object HomeLayout {
    data class Entry(val id: String, val size: Int = 1)

    /** Плашка из каталога: [sizes] — подписи размеров (если пусто — размер один). */
    data class Block(val id: String, val title: String, val glyph: String, val about: String, val sizes: List<String> = emptyList(), val defaultSize: Int = 1)

    val SIZES3 = listOf("Компактно", "Обычно", "Крупно")
    val SHORTCUT_SIZES = listOf("Треть", "Половина", "Во всю ширину")

    val CATALOG = listOf(
        Block("greeting", "Приветствие и дата", "ui:smile", "Доброе утро, дата, поиск и настройки"),
        Block("activity", "Шаги и калории", "sport/19", "Кольца шагов и сожжённых калорий"),
        Block("weight", "Вес за неделю", "sport/11", "График веса и изменение"),
        Block("weather", "Погода", "ui:sun", "Сейчас и прогноз"),
        Block("holiday", "Праздники", "ui:gift", "Ближайший праздник"),
        Block("rings", "Задачи, привычки, вода", "ui:check", "Три кольца прогресса дня"),
        Block("metrics", "Показатели дня", "analytics/00", "Сон, питание, тренировки, траты"),
        Block("health", "Здоровье: главное", "pressure/01", "Давление, пульс, сон, шаги, вода, вес — с инфографикой", SIZES3),
        Block("pressure", "Давление и пульс", "pressure/00", "Последний замер, вывод, средние и график", SIZES3),
        Block("tasks", "Задачи на сегодня", "ui:notebook", "Список задач", SIZES3),
        Block("habits", "Привычки", "sport/24", "Отметить привычки одним касанием"),
        Block("calendar", "Календарь", "cal/23", "События и напоминания на сегодня"),
        Block("wellbeing", "Вода и настроение", "ui:drop", "Добавить воду, отметить настроение"),
        Block("menu", "Меню на сегодня", "food/05", "Что есть сегодня"),
        Block("money", "Деньги", "ui:wallet", "Баланс, месяц, бюджет, ближайший платёж"),
        Block("goals", "Цели", "sport/21", "Прогресс целей", SIZES3),
        Block("talk", "Факт для разговора", "ui:people", "Small Talks"),
        Block("quote", "Цитата дня", "ui:bulb", "Цитата и итог дня"),
    )

    val DEFAULT = listOf(
        "activity", "weight", "greeting", "weather", "holiday", "rings", "metrics", "health", "pressure",
        "tasks", "habits", "calendar", "wellbeing", "menu", "money", "goals", "talk", "quote",
    ).map { Entry(it, CATALOG.first { b -> b.id == it }.defaultSize) }

    fun block(id: String): Block? = CATALOG.firstOrNull { it.id == id }
    fun isShortcut(id: String) = id.startsWith("go:")
    fun sizesOf(id: String): List<String> = if (isShortcut(id)) SHORTCUT_SIZES else block(id)?.sizes.orEmpty()

    private const val KEY = "home_layout"
    private fun sp() = runCatching { Graph.app.getSharedPreferences("ui_state", Context.MODE_PRIVATE) }.getOrNull()

    fun decode(s: String?): List<Entry>? {
        if (s.isNullOrBlank()) return null
        return s.split(';').mapNotNull { p ->
            val id = p.substringBefore('|').trim()
            if (id.isEmpty() || (!isShortcut(id) && block(id) == null)) return@mapNotNull null
            Entry(id, p.substringAfter('|', "1").toIntOrNull()?.coerceIn(0, 2) ?: 1)
        }.distinctBy { it.id }
    }

    fun encode(l: List<Entry>) = l.joinToString(";") { "${it.id}|${it.size}" }

    /** Текущая раскладка; экраны перерисовываются при изменении. */
    val state = mutableStateOf(decode(sp()?.getString(KEY, null)) ?: DEFAULT)
    private val history = ArrayDeque<List<Entry>>()
    val canUndo = mutableStateOf(false)

    fun set(l: List<Entry>) {
        history.addLast(state.value); if (history.size > 30) history.removeFirst()
        canUndo.value = true
        apply(l)
    }

    private fun apply(l: List<Entry>) {
        state.value = l
        sp()?.edit()?.putString(KEY, encode(l))?.apply()
    }

    fun undo() {
        val prev = history.removeLastOrNull() ?: return
        apply(prev); canUndo.value = history.isNotEmpty()
    }

    fun reset() = set(DEFAULT)
    val isDefault get() = state.value == DEFAULT

    fun move(i: Int, d: Int) {
        val l = state.value.toMutableList(); val j = i + d
        if (i !in l.indices || j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t; set(l)
    }
    fun remove(id: String) = set(state.value.filter { it.id != id })
    fun add(id: String, size: Int = if (isShortcut(id)) 1 else block(id)?.defaultSize ?: 1) { if (state.value.none { it.id == id }) set(state.value + Entry(id, size)) }
    fun resize(id: String, size: Int) = set(state.value.map { if (it.id == id) it.copy(size = size) else it })

    /**
     * Раскладка ярлыков по рядам: подряд идущие маленькие ярлыки (треть, половина) собираются в один ряд,
     * пока помещаются. Возвращает группы индексов [entries].
     */
    fun rows(entries: List<Entry>): List<List<Int>> {
        val out = mutableListOf<MutableList<Int>>()
        var room = 0.0
        entries.forEachIndexed { i, e ->
            val w = if (isShortcut(e.id)) when (e.size) { 0 -> 1.0 / 3; 1 -> 0.5; else -> 1.0 } else 1.0
            val last = out.lastOrNull()
            if (w < 1.0 && last != null && isShortcut(entries[last.first()].id) && room + 1e-6 >= w) { last += i; room -= w }
            else { out += mutableListOf(i); room = 1.0 - w }
        }
        return out
    }
}
