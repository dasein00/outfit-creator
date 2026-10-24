package com.dasein.poryadok.ui.more

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import com.dasein.poryadok.ui.common.Text
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
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Sections
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.openTab
import com.dasein.poryadok.ui.theme.LocalExtra

/** Раздел или подраздел, куда можно перейти из поиска. [parent] — где он находится («Финансы»), [keys] — другие слова для поиска. */
data class Dest(val title: String, val route: String, val icon: String, val parent: String = "", val keys: String = "")

/** Все разделы и подразделы приложения для поиска. */
object SectionIndex {
    private val TABS = setOf(Routes.TODAY, Routes.RECIPES, Routes.HEALTH_HUB, Routes.PLAN, Routes.TOPS_HUB, Routes.MORE)

    val ALL: List<Dest> by lazy {
        val fin = "Финансы"
        val cal = "Календарь"
        val health = "Здоровье"
        val rec = "Рецепты"
        val tutor = "Репетитор"
        val train = "Тренировки"
        val tops = "Топы"
        val craft = "Рукоделие"
        buildList {
            add(Dest("Главное", Routes.TODAY, "d01/00", keys = "главная сегодня домой"))
            add(Dest("Настроить главное", com.dasein.poryadok.ui.today.HOME_EDIT, "ui:sliders", "Главное", "плашки порядок главная страница"))
            add(Dest("Рецепты", Routes.RECIPES, "d12/00", keys = "кулинария блюда еда готовить"))
            add(Dest("Здоровье", Routes.HEALTH_HUB, "d01/02", keys = "самочувствие"))
            add(Dest("График", Routes.PLAN, "d01/01", keys = "план расписание"))
            add(Dest("Топы", Routes.TOPS_HUB, "d17/03", keys = "фильмы сериалы книги"))
            add(Dest("Все разделы", Routes.MORE, "d01/04", keys = "ещё меню"))
            HUB.forEach { h -> add(Dest(h.title, h.route, h.icon, keys = h.sub)) }

            // Финансы
            listOf("Операции" to "траты список", "Обзор и аналитика" to "категории месяц графики статистика", "Расходы" to "траты за период неделя месяц год",
                "Доходы" to "зарплата поступления за период", "Календарь трат" to "по дням календарь расходов", "Бюджеты" to "лимиты",
                "Регулярные" to "подписки платежи", "Счета" to "остаток баланс карты вклад сбережения", "Калькуляторы" to "кредит ипотека вклад процент")
                .forEachIndexed { i, (t, k) -> add(Dest(t, Routes.finance(i), "ui:wallet", fin, k)) }
            add(Dest("Импорт выписки", com.dasein.poryadok.ui.finance.STATEMENT_ROUTE, "ui:document", fin, "выписка банк сбер тинькофф т-банк pdf csv excel загрузить операции"))
            add(Dest("ИИ для непонятных SMS и пушей", Routes.SBER, "ui:bank", fin, "смс 900 уведомления сбербанк онлайн автоматически ии разбор"))
            add(Dest("Инфографика финансов", com.dasein.poryadok.ui.finance.FIN_INFO_ROUTE, "ui:pie", fin, "графики метрики капитал подписки места календарь трат статистика"))
            add(Dest("ИИ-анализ финансов", com.dasein.poryadok.ui.finance.FIN_AI_ROUTE, "ui:stats", fin, "дубликаты дубли категории советы ии"))
            add(Dest("Выровнять остатки", Routes.finance(7), "ui:bank", fin, "остаток на счету сберегательный процент"))

            // Календарь и график
            listOf(0 to "Месяц", 1 to "Напоминалки", 2 to "Праздники", 3 to "История", 4 to "Культура").forEach { (i, t) ->
                add(Dest(t, Routes.calendar(i), "d05/00", cal, if (i == 3) "в этот день события" else if (i == 4) "в этот день кино музыка" else ""))
            }
            listOf(0 to "Календарь", 1 to "Задачи", 2 to "Заметки").forEach { (i, t) -> add(Dest(t, Routes.plan(i), "d01/01", "График")) }

            // Топы
            listOf("Фильмы", "Сериалы", "Книги", "Мои топы").forEachIndexed { i, t -> add(Dest(t, Routes.topsHub(i), "d17/03", tops, "кино коллекция рейтинг")) }
            add(Dest("Найти фильм или сериал", Routes.mediaSearch(0), "ui:search", tops, "кинопоиск добавить"))
            add(Dest("Карточка по ссылке", com.dasein.poryadok.ui.media.MEDIA_LINK_ROUTE, "ui:globe", tops, "кинопоиск шикимори ссылка"))
            add(Dest("Заметки и цитаты из книг", com.dasein.poryadok.ui.reader.READER_NOTES_ROUTE, "d18/00", "Читалка", "выделения закладки цитаты книги читалка"))
            add(Dest("Читалка", com.dasein.poryadok.ui.reader.READER_ROUTE, "books/08", tops, "читать книги epub fb2 mobi azw3 pdf djvu txt rtf docx библиотека электронные"))

            // Здоровье
            listOf(0 to "Питание", 1 to "Вес", 2 to "Замеры", 5 to "Фото прогресса").forEach { (i, t) ->
                add(Dest(t, Routes.health(i), "d08/02", health, if (i == 0) "калории кбжу еда" else if (i == 2) "талия бёдра обхваты" else "до после"))
            }
            add(Dest("Сон", Routes.wellbeing(1), "d08/06", health))
            add(Dest("Вода", Routes.wellbeing(2), "d08/05", health))
            add(Dest("Тренд веса", Routes.WEIGHT_TREND, "d08/02", health, "график веса"))
            add(Dest("Сравнение замеров тела", Routes.BODY_COMPARE, "d08/02", health))
            add(Dest("Научный анализ тела", Routes.BODY_SCIENCE, "d08/02", health, "имт телосложение жир талия рост"))
            listOf("Программы", "Упражнения", "История тренировок", "Прогресс и рекорды", "Журнал").forEachIndexed { i, t ->
                add(Dest(t, Routes.training(i), "d08/09", train, "тренировки спорт"))
            }

            // Рецепты
            listOf("Каталог", "Меню на неделю", "Избранное", "Мои рецепты", "Архив").forEachIndexed { i, t -> add(Dest(t, Routes.recipes(i), "d12/00", rec)) }
            add(Dest("Что приготовить", com.dasein.poryadok.ui.recipes.WHAT_TO_COOK_ROUTE, "d12/00", rec, "холодильник продукты дома"))
            add(Dest("Новый рецепт", Routes.recipeEdit(0), "d12/00", rec, "добавить рецепт видео instagram ссылка"))
            add(Dest("Список покупок", Routes.SHOPPING, "food/24", rec, "магазин продукты"))
            add(Dest("История готовки", Routes.COOK_HISTORY, "d12/00", rec))
            add(Dest("Шаблоны меню", Routes.PRESETS, "food/28", rec))

            // Репетитор
            add(Dest("Слова", com.dasein.poryadok.ui.tutor.TutorRoutes.WORDS, "tutor/00", tutor, "английский"))
            add(Dest("Словарь", com.dasein.poryadok.ui.tutor.TutorRoutes.DICT, "tutor/05", tutor, "английский"))
            add(Dest("Идиомы и фразы", com.dasein.poryadok.ui.tutor.TutorRoutes.TRANSLATE + "?tab=1", "tutor/06", tutor, "английский"))
            add(Dest("Карточки", com.dasein.poryadok.ui.tutor.TUTOR_CARDS_ROUTE, "tutor/02", tutor, "anki повторение"))
            add(Dest("Разговор с ИИ", com.dasein.poryadok.ui.tutor.tutorChat(0), "tutor/13", tutor, "чат английский практика"))
            add(Dest("Проверка текста", com.dasein.poryadok.ui.tutor.tutorChat(1), "tutor/14", tutor, "ошибки грамматика"))

            // Рукоделие
            add(Dest("Мои схемы", Routes.CRAFT_MINE, "d21/00", craft, "мозаика вышивка"))
            add(Dest("Картина по номерам", Routes.CRAFT_PBN, "d21/00", craft, "раскраска"))
            add(Dest("Карандаши", Routes.CRAFT_PENCILS, "d21/00", craft))
            add(Dest("Запасы", Routes.CRAFT_STASH, "d21/00", craft, "стразы нитки"))

            // Прочее
            add(Dest("Оформление", APPEARANCE_ROUTE, "ui:sliders", "Настройки", "цвет фон тема шрифт размер текста иконки контраст акцент тёмная светлая"))
            add(Dest("Погода", Routes.WEATHER, "ui:sun", keys = "прогноз дождь температура"))
            add(Dest("Колесо баланса", Routes.WHEEL, "ui:wheel", keys = "сферы жизни"))
            add(Dest("Поиск по записям", Routes.SEARCH, "ui:search", keys = "задачи заметки события операции найти"))
        }.distinctBy { it.route + "|" + it.title }
    }

    private fun norm(s: String) = s.lowercase().replace('ё', 'е')

    /** Подходящие разделы: все слова запроса есть в названии, родителе или ключевых словах. Своё название раздела тоже ищется. */
    fun search(ctx: Context, q: String, limit: Int = 12): List<Dest> {
        val words = norm(q).split(' ', ',').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return ALL.mapNotNull { d ->
            val shown = Sections.name(ctx, d.route, d.title)
            val title = norm("$shown ${d.title}")
            val hay = "$title ${norm(d.parent)} ${norm(d.keys)}"
            if (words.any { it !in hay }) return@mapNotNull null
            val score = when {
                norm(shown).startsWith(words.first()) -> 0
                words.all { it in title } -> 1
                else -> 2
            }
            score to d
        }.sortedWith(compareBy({ it.first }, { it.second.parent.isNotEmpty() }, { it.second.title.length })).take(limit).map { it.second }
    }

    fun open(nav: NavHostController, d: Dest) {
        if (d.route in TABS) nav.openTab(d.route) else nav.navigate(d.route)
    }
}

/** Строка результата: иконка, название (своё, если переименовали) и где находится. */
@Composable
fun DestRow(nav: NavHostController, d: Dest, onOpen: () -> Unit = {}) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val name = Sections.name(ctx, d.route, d.title)
    val icon = Sections.icon(ctx, d.route, d.icon)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(); SectionIndex.open(nav, d) }.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, 26.dp, badge = false)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (d.parent.isNotEmpty()) "${d.parent} › $name" else "раздел", fontSize = 12.sp, color = extra.dim, maxLines = 1)
        }
    }
}

/** Поле поиска разделов с выпадающим списком, который обновляется по мере ввода. */
@Composable
fun SectionSearchBar(nav: NavHostController, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var q by remember { mutableStateOf("") }
    val found = remember(q) { SectionIndex.search(ctx, q) }
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            q, { q = it }, singleLine = true, shape = RoundedCornerShape(16.dp),
            placeholder = { Text(btnLabel("Найти раздел: счета, сон, словарь…")) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "Очистить") } },
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(q.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                if (found.isEmpty()) Text(btnLabel("Такого раздела нет"), color = extra.dim, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                found.forEach { d -> DestRow(nav, d) { q = "" } }
            }
        }
    }
}
