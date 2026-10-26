package com.dasein.poryadok.ui.talk

import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.btnLabel
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Settings as AppSettings
import com.dasein.poryadok.logic.Fact
import com.dasein.poryadok.logic.SmallTalk
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.random.Random
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.AnnotatedString
import com.dasein.poryadok.logic.Paging
import com.dasein.poryadok.logic.TextQuery
import com.dasein.poryadok.ui.common.FilterOption
import com.dasein.poryadok.ui.common.PageBar
import com.dasein.poryadok.ui.common.PageInfo
import com.dasein.poryadok.ui.common.SearchField
import com.dasein.poryadok.ui.common.highlighted
import kotlinx.coroutines.launch

/** База Small Talks: факты (включая старую картотеку о здоровье), истории, вопросы и приёмы. */
object TalkRepo {
    class Data(
        val facts: List<Fact>,
        val stories: List<SmallTalk.Story>,
        val questions: List<SmallTalk.Question>,
        val tips: List<SmallTalk.Tip>,
        val phrases: List<SmallTalk.Question>,
        val topics: List<SmallTalk.Topic>,
        val months: Map<Int, String>,
        val opinions: List<String>,
        val exercises: List<SmallTalk.Exercise>,
    )

    @Volatile private var cache: Data? = null

    private fun read(ctx: Context, dir: String, filter: (String) -> Boolean): String = runCatching {
        ctx.assets.list(dir).orEmpty().filter(filter).sorted().joinToString("\n") { f ->
            ctx.assets.open("$dir/$f").bufferedReader().use { it.readText() }
        }
    }.getOrDefault("")

    /** Из сети пришли новые факты — перечитать базу. */
    fun invalidate() { cache = null; version.intValue++ }
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun load(ctx: Context): Data = cache ?: run {
        val facts = SmallTalk.parseFacts(read(ctx, "smalltalk") { it.startsWith("facts") && it.endsWith(".txt") }) +
            SmallTalk.parseFacts(read(ctx, "facts") { it.endsWith(".txt") }) +
            com.dasein.poryadok.system.TalkOnline.facts(ctx)
        Data(
            facts.distinctBy { it.id },
            SmallTalk.parseStories(read(ctx, "smalltalk") { it.startsWith("stories") && it.endsWith(".txt") }),
            SmallTalk.parseQuestions(read(ctx, "smalltalk") { it == "questions.txt" }),
            SmallTalk.parseGuide(read(ctx, "smalltalk") { it == "guide.txt" }),
            SmallTalk.parseQuestions(read(ctx, "smalltalk") { it == "phrases.txt" }),
            SmallTalk.parseTopics(read(ctx, "smalltalk") { it == "topics.txt" }),
            SmallTalk.parseMonths(read(ctx, "smalltalk") { it == "months.txt" }),
            SmallTalk.parseLines(read(ctx, "smalltalk") { it == "opinions.txt" }),
            SmallTalk.parseExercises(read(ctx, "smalltalk") { it == "exercises.txt" }),
        ).also { cache = it }
    }

    fun share(ctx: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { ctx.startActivity(Intent.createChooser(send, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Колода и избранное хранятся в настройках; эти функции читают и пишут их. */
private fun deckOf(s: AppSettings) = SmallTalk.Deck(SmallTalk.decodeIds(s.factsHistory), s.factsPos)

private fun saveDeck(d: SmallTalk.Deck) = io {
    Graph.prefs.update { it.copy(factsHistory = SmallTalk.encodeIds(d.history), factsPos = d.pos) }
}

private fun toggleFavorite(id: Int) = io {
    Graph.prefs.update { it.copy(talkFavorites = SmallTalk.encodeIds(SmallTalk.toggle(SmallTalk.decodeIds(it.talkFavorites), id))) }
}

private var lastTalkSettings: com.dasein.poryadok.data.Settings? = null

/**
 * Плашка «Интересный факт» на главной: ← и → листают факты выбранных в Small Talks сфер,
 * нажатие на сам факт открывает раздел Small Talks.
 */
@Composable
fun TalkFactCard(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val data = remember(TalkRepo.version.intValue) { TalkRepo.load(ctx) }
    // Последние настройки — чтобы при прокрутке назад плашка сразу была на месте, без «прыжка».
    val settings by observe(lastTalkSettings) { Graph.prefs.settings }
    val s = settings ?: return
    lastTalkSettings = s
    val spheres = SmallTalk.decodeSet(s.talkSpheres)
    val facts = remember(data, s.talkSpheres) { SmallTalk.filter(data.facts, spheres) }
    if (facts.isEmpty()) return
    val deck = remember(s.factsHistory, s.factsPos, facts) { SmallTalk.ensure(facts, deckOf(s), Random.Default) }
    // Первый показ: запоминаем факт, чтобы он не менялся при каждом открытии главной.
    LaunchedEffect(deck) { if (deck != deckOf(s)) saveDeck(deck) }
    val fact = SmallTalk.current(facts, deck) ?: return
    val read = SmallTalk.read(facts, deck)

    var aiOpen by remember { mutableStateOf(false) }
    val like = com.dasein.poryadok.system.Likes.Like(com.dasein.poryadok.system.Likes.FACT, fact.text, tags = listOf(fact.tag))
    val liked = com.dasein.poryadok.system.Likes.isLiked(com.dasein.poryadok.ui.common.rememberLikes(), like)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(com.dasein.poryadok.ui.common.likedColor(liked)).padding(14.dp)) {
        Row(Modifier.clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Glyph(SmallTalk.glyph(fact.tag), 22.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(btnLabel("Интересный факт"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${fact.tag} · Small Talks", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text("$read из ${facts.size}  ", fontSize = 12.sp, color = extra.dim)
            com.dasein.poryadok.ui.common.LikeButton(like, liked)
            // Волшебная палочка: 3 факта от ИИ — случайных или на тему.
            Box(
                Modifier.padding(start = 6.dp).size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { aiOpen = true },
                contentAlignment = Alignment.Center,
            ) { Glyph(com.dasein.poryadok.ui.common.Glyphs.AI, 26.dp, badge = false) }
        }
        if (aiOpen) AiFactsDialog { aiOpen = false }
        AnimatedContent(fact, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "fact") { f ->
            Text(
                f.text, fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).clickable(onClick = onOpen).padding(top = 10.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(onClick = { saveDeck(SmallTalk.prev(facts, deck, Random.Default)) }, enabled = SmallTalk.hasPrev(facts, deck)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Предыдущий факт")
            }
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Bar(read / facts.size.toFloat(), MaterialTheme.colorScheme.primary, height = 4.dp)
            }
            FilledIconButton(
                onClick = {
                    val n = SmallTalk.next(facts, deck, Random.Default)
                    if (read >= facts.size && n.pos == n.history.lastIndex) Toast.makeText(ctx, "Вы прочитали все ${facts.size} фактов! Начинаем новый круг.", Toast.LENGTH_LONG).show()
                    saveDeck(n)
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следующий факт") }
        }
    }
}

private val TABS = listOf("Факты", "Поиск", "Истории", "Вопросы", "Темы", "Школа", "Тренажёр", "Фразы", "Избранное", "Слово дня")
private const val FAV_TAB = 8
/** Вкладка «Слово дня» — её открывает плашка на главной. */
const val WORD_TAB = 9
/** С какой вкладки открыть Small Talks (плашка «Слово дня» на главной); сбрасывается после открытия. */
object TalkNav { var openTab: Int? = null }
private val TAB_GLYPHS = listOf("ui:bulb", "ui:search", "ui:book", "ui:people", "ui:globe", "ui:cap", "ui:target", "ui:notebook", "ui:heart", "ui:book")
private val TAB_HINTS = listOf(
    "Факт дня по выбранным сферам — те же, что на главной. Ниже весь каталог фактов: поиск, страницы, переход на страницу по номеру.",
    "Поиск по всей базе сразу: факты, истории, вопросы, приёмы, фразы и темы. Можно искать точные выражения в кавычках.",
    "Короткие истории, которые можно пересказать за минуту. Нажмите на историю, чтобы раскрыть.",
    "Вопросы для разговора по ситуациям: знакомство, работа, свидание, нетворкинг и другие.",
    "Повод месяца и темы с ассоциациями — когда не за что зацепиться.",
    "Приёмы из книг о смолтоке: как начать, поддержать, слушать и красиво закончить разговор.",
    "Упражнения: упражнение дня, светофор реплики, мнение за 3 секунды, конструктор историй.",
    "Готовые фразы для трудных моментов. Нажмите на фразу — она скопируется.",
    "Сохранённые факты и истории — освежите их перед встречей.",
    "Редкие красивые слова — определение, этимология и история. С ключом ИИ каждый день новое слово.",
)

/** Как сортировать каталог. */
private enum class TalkSort(val label: String) { ORDER("По порядку"), RELEVANCE("По совпадению"), ALPHA("А–Я"), SHUFFLE("Вперемешку"), FAV("Сначала ★") }

@Composable
fun SmallTalkScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val data = remember(TalkRepo.version.intValue) { TalkRepo.load(ctx) }
    val settings by observe(null) { Graph.prefs.settings }
    var tab by rememberSaveable { mutableIntStateOf(TalkNav.openTab ?: 0) }
    LaunchedEffect(Unit) { TalkNav.openTab = null }
    Screen("Small Talks", onBack = { nav.popBackStack() }) { pad ->
        val s = settings ?: return@Screen
        val favorites = SmallTalk.decodeIds(s.talkFavorites)
        Column(Modifier.padding(pad)) {
            // Все разделы видны сразу — сеткой 3×3, без прокрутки вбок.
            TabGrid(tab, favorites.size) { tab = it }
            androidx.compose.runtime.key(tab) {
                when (tab) {
                    0 -> FactsTab(data, s, favorites)
                    1 -> SearchTab(data, favorites)
                    2 -> StoriesTab(data, s, favorites)
                    3 -> QuestionsTab(data)
                    4 -> TabList(4) { topicsTab(data) }
                    5 -> TabList(5) { guideTab(data) }
                    6 -> TabList(6) { trainerTab(data, s) }
                    7 -> TabList(7) { phrasesTab(data) }
                    FAV_TAB -> TabList(FAV_TAB) { favoritesTab(data, favorites) { tab = it } }
                    else -> RareWordsTab()
                }
            }
        }
    }
}

/** Сетка разделов: все девять видны сразу, выбранный подсвечен. */
@Composable
private fun TabGrid(tab: Int, favCount: Int, onTab: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TABS.indices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { i ->
                    val selected = tab == i
                    androidx.compose.material3.Surface(
                        onClick = { onTab(i) }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) scheme.primary else scheme.surface,
                        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, scheme.outline.copy(alpha = .5f)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Glyph(TAB_GLYPHS[i], 20.dp)
                            Text(
                                if (i == FAV_TAB && favCount > 0) "${TABS[i]} $favCount" else TABS[i],
                                Modifier.padding(start = 6.dp), fontSize = 13.sp, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                                color = if (selected) scheme.onPrimary else scheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Общая обёртка вкладки: подсказка сверху, инструкция и отступ снизу. */
@Composable
private fun TabList(
    tab: Int, state: LazyListState = rememberLazyListState(),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(Modifier.padding(horizontal = 16.dp), state = state) {
        item {
            Text(TAB_HINTS[tab], fontSize = 13.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))
        }
        content()
        item { HowTo("small_talk") }
        item { Gap(32.dp) }
    }
}

/** Состояние постраничного каталога: страница, размер страницы и прокрутка к его началу. */
private class PagedState(val list: LazyListState, val scope: kotlinx.coroutines.CoroutineScope, val headIndex: Int) {
    var page by mutableIntStateOf(0)
    var size by mutableIntStateOf(20)
    fun go(p: Int) {
        page = p
        scope.launch { list.animateScrollToItem(headIndex) }
    }
}

@Composable
private fun rememberPaged(list: LazyListState, headIndex: Int): PagedState {
    val scope = rememberCoroutineScope()
    return remember { PagedState(list, scope, headIndex) }
}

private fun <T> sorted(items: List<T>, sort: TalkSort, q: TextQuery.Query, seed: Int, favorites: List<Int>, title: (T) -> String, text: (T) -> String, id: (T) -> Int): List<T> =
    when (sort) {
        TalkSort.ORDER -> items
        TalkSort.RELEVANCE -> if (q.isEmpty) items else items.sortedByDescending { TextQuery.score(title(it), text(it), q) }
        TalkSort.ALPHA -> items.sortedBy { TextQuery.norm(title(it).ifBlank { text(it) }) }
        TalkSort.SHUFFLE -> items.shuffled(Random(seed))
        TalkSort.FAV -> items.sortedByDescending { id(it) in favorites }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortChips(sort: TalkSort, onSort: (TalkSort) -> Unit) {
    com.dasein.poryadok.ui.common.SortButton(TalkSort.entries, sort, { it.label }, onSort)
}

@Composable
private fun FactsTab(data: TalkRepo.Data, s: AppSettings, favorites: List<Int>) {
    val listState = rememberLazyListState()
    val paged = rememberPaged(listState, 2)
    var q by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TalkSort.ORDER) }
    var seed by rememberSaveable { mutableIntStateOf(Random.nextInt()) }
    var onlyFav by rememberSaveable { mutableStateOf(false) }
    val spheres = SmallTalk.decodeSet(s.talkSpheres)
    val query = remember(q) { TextQuery.parse(q) }
    val pool = remember(data, s.talkSpheres) { SmallTalk.filter(data.facts, spheres) }
    val found = remember(pool, query, sort, seed, onlyFav, favorites) {
        sorted(
            pool.filter { (!onlyFav || it.id in favorites) && TextQuery.matches(it.text + " " + it.tag, query) },
            sort, query, seed, favorites, { "" }, { it.text }, { it.id },
        )
    }
    LaunchedEffect(q, sort, onlyFav, s.talkSpheres, seed) { paged.page = 0 }
    val page = Paging.clamp(paged.page, found.size, paged.size)
    val hl = MaterialTheme.colorScheme.primary.copy(alpha = .22f)
    TabList(0, listState) {
        factOfTheDay(data, s, favorites)
        item(key = "facts_head") {
            AiFactsPanel(Modifier.padding(bottom = 10.dp))
            TopicOfDay()
            SectionTitle("Каталог фактов · ${pool.size}")
            OnlineFactsLine()
            SearchField(q, { q = it }, "Слова или \"точное выражение\"")
            Gap(8.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("Только ★", onlyFav) { onlyFav = !onlyFav }
                com.dasein.poryadok.ui.common.HGap(8.dp)
                Pill("Перемешать", false) { sort = TalkSort.SHUFFLE; seed = Random.nextInt() }
            }
            Gap(6.dp)
            SortChips(sort) { sort = it }
            PageInfo(found.size, page, paged.size, { paged.size = it; paged.page = 0 }, "фактов")
            PageBar(page, Paging.pages(found.size, paged.size), { paged.go(it) })
            Gap(8.dp)
            if (found.isEmpty()) Text("Ничего не нашлось. Попробуйте другие слова или сбросьте фильтр сфер.", color = LocalExtra.current.dim, fontSize = 13.sp)
        }
        val slice = Paging.slice(found, page, paged.size)
        itemsIndexed(slice, key = { _, f -> "f${f.id}" }) { i, f ->
            FactRow(page * paged.size + i + 1, f, f.id in favorites, highlighted(f.text, query, hl)) {
                saveDeck(SmallTalk.jump(deckOf(s), f.id)); paged.scope.launch { listState.animateScrollToItem(1) }
            }
        }
        item { Gap(6.dp); PageBar(page, Paging.pages(found.size, paged.size), { paged.go(it) }) }
    }
}

@Composable
private fun FactRow(n: Int, f: Fact, favorite: Boolean, text: AnnotatedString, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    Tile(Modifier.padding(bottom = 8.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("№$n", fontSize = 11.sp, color = extra.dim)
            Glyph(SmallTalk.glyph(f.tag), 18.dp, Modifier.padding(start = 8.dp))
            Text(f.tag, Modifier.weight(1f).padding(start = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { TalkRepo.share(ctx, f.text) }) { Icon(Icons.Filled.Share, "Поделиться") }
            IconButton(onClick = { toggleFavorite(f.id) }) {
                if (favorite) Icon(Icons.Filled.Star, "Убрать из избранного", tint = MaterialTheme.colorScheme.primary)
                else Icon(Icons.Outlined.StarOutline, "В избранное")
            }
        }
        Text(text, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.factOfTheDay(data: TalkRepo.Data, s: AppSettings, favorites: List<Int>) {
    item(key = "fact_day") {
        val ctx = LocalContext.current
        val extra = LocalExtra.current
        val spheres = SmallTalk.decodeSet(s.talkSpheres)
        val counts = remember(data) { data.facts.groupingBy { it.tag }.eachCount() }
        val facts = remember(data, s.talkSpheres) { SmallTalk.filter(data.facts, spheres) }
        val deck = remember(s.factsHistory, s.factsPos, facts) { SmallTalk.ensure(facts, deckOf(s), Random.Default) }
        LaunchedEffect(deck) { if (deck != deckOf(s)) saveDeck(deck) }
        val fact = SmallTalk.current(facts, deck)
        val read = SmallTalk.read(facts, deck)

        Text(btnLabel("Сферы — они же показываются на главной"), fontSize = 12.sp, color = extra.dim)
        Gap(6.dp)
        SphereChips(counts, spheres) { saveSpheres(it) }
        Gap(12.dp)
        if (fact != null) Tile {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(SmallTalk.glyph(fact.tag), 26.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(fact.tag, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("Прочитано $read из ${facts.size}", fontSize = 12.sp, color = extra.dim)
                }
                IconButton(onClick = { toggleFavorite(fact.id) }) {
                    if (fact.id in favorites) Icon(Icons.Filled.Star, "Убрать из избранного", tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Outlined.StarOutline, "В избранное")
                }
                IconButton(onClick = { TalkRepo.share(ctx, fact.text) }) { Icon(Icons.Filled.Share, "Поделиться") }
            }
            AnimatedContent(fact, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "talkFact") { f ->
                Column(Modifier.fillMaxWidth().heightIn(min = 130.dp).padding(top = 10.dp)) {
                    Text(f.text, fontSize = 17.sp, lineHeight = 24.sp)
                    Gap(10.dp)
                    Text("Как начать: «${SmallTalk.bridge(f.tag)}»", fontSize = 13.sp, fontStyle = FontStyle.Italic, color = extra.dim)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(onClick = { saveDeck(SmallTalk.prev(facts, deck, Random.Default)) }, enabled = SmallTalk.hasPrev(facts, deck)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Предыдущий")
                }
                Box(Modifier.weight(1f).padding(horizontal = 12.dp)) { Bar(read / facts.size.toFloat(), MaterialTheme.colorScheme.primary, height = 4.dp) }
                OutlinedIconButton(onClick = { saveDeck(SmallTalk.random(facts, deck, Random.Default)) }) { Icon(Icons.Filled.Refresh, "Случайный") }
                Box(Modifier.padding(start = 8.dp)) {
                    FilledIconButton(
                        onClick = { saveDeck(SmallTalk.next(facts, deck, Random.Default)) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следующий") }
                }
            }
        }
        Hint(
            "talk_facts", "Выберите одну или несколько сфер — факты на главной и в каталоге будут только из них. ★ сохраняет факт в «Избранное», " +
                "↻ — случайный факт. В каталоге ниже можно искать по словам, листать страницы и перейти на страницу по номеру; " +
                "нажмите на факт в каталоге — он станет фактом дня.",
            Modifier.padding(top = 12.dp), title = "Как пользоваться",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SphereChips(counts: Map<String, Int>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    // Сферы — кнопкой со списком снизу (можно отметить несколько), а не длинной лентой плашек.
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.dasein.poryadok.ui.common.MultiFilter(
            "Сферы", SmallTalk.SPHERES.filter { (counts[it.first] ?: 0) > 0 }.map { (name, _) -> FilterOption(name, name, counts[name] ?: 0) },
            selected, onChange,
        )
        if (selected.isNotEmpty()) Text(
            "Сбросить", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp).clickable { onChange(emptySet()) }.padding(6.dp),
        )
    }
}

private fun saveSpheres(set: Set<String>) = io { Graph.prefs.update { it.copy(talkSpheres = SmallTalk.encodeSet(set)) } }

/** Одна запись общей базы для поиска. */
private data class Hit(val kind: String, val id: Int, val title: String, val text: String, val sphere: String, val glyph: String)

private val KINDS = listOf("Факты", "Истории", "Вопросы", "Приёмы", "Фразы", "Темы")

private fun allHits(data: TalkRepo.Data): List<Hit> = buildList {
    data.facts.forEach { add(Hit("Факты", it.id, "", it.text, it.tag, SmallTalk.glyph(it.tag))) }
    data.stories.forEach { add(Hit("Истории", it.id, it.title, it.text, it.sphere, SmallTalk.glyph(it.sphere))) }
    data.questions.forEach { add(Hit("Вопросы", it.id, "", it.text, it.situation, "ui:people")) }
    data.tips.forEach { add(Hit("Приёмы", it.id, it.title, it.text + if (it.source.isNotBlank()) "\nИсточник: ${it.source}" else "", it.section, "ui:cap")) }
    data.phrases.forEach { add(Hit("Фразы", it.id, "", it.text, it.situation, "ui:notebook")) }
    data.topics.forEachIndexed { i, t -> add(Hit("Темы", -1 - i, t.name, t.words + "\n" + t.questions.joinToString("\n") { "• $it" }, "Темы", "ui:globe")) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchTab(data: TalkRepo.Data, favorites: List<Int>) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val listState = rememberLazyListState()
    val paged = rememberPaged(listState, 1)
    var q by rememberSaveable { mutableStateOf("") }
    var kinds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var sphere by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TalkSort.RELEVANCE) }
    var seed by rememberSaveable { mutableIntStateOf(Random.nextInt()) }
    val all = remember(data) { allHits(data) }
    val query = remember(q) { TextQuery.parse(q) }
    val inKinds = remember(all, kinds) { all.filter { kinds.isEmpty() || it.kind in kinds } }
    val found = remember(inKinds, query, sphere, sort, seed, favorites) {
        sorted(
            inKinds.filter { (sphere.isEmpty() || it.sphere == sphere) && TextQuery.matches(it.title + " " + it.text + " " + it.sphere, query) },
            sort, query, seed, favorites, { it.title }, { it.text }, { it.id },
        )
    }
    val spheres = remember(inKinds, query) {
        inKinds.filter { TextQuery.matches(it.title + " " + it.text + " " + it.sphere, query) }.groupingBy { it.sphere }.eachCount()
            .entries.sortedByDescending { it.value }
    }
    LaunchedEffect(q, kinds, sphere, sort, seed) { paged.page = 0 }
    val page = Paging.clamp(paged.page, found.size, paged.size)
    val hl = MaterialTheme.colorScheme.primary.copy(alpha = .22f)
    TabList(1, listState) {
        item(key = "search_head") {
            SearchField(q, { q = it }, "Например: Ван Гог, \"чёрная дыра\", -кофе")
            Gap(8.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.dasein.poryadok.ui.common.MultiFilter("Где искать", KINDS.map { k -> FilterOption(k, k, all.count { it.kind == k }) }, kinds) { kinds = it }
                if (spheres.size > 1) com.dasein.poryadok.ui.common.SingleFilter(
                    "Сфера", spheres.map { (name, n) -> FilterOption(name, name, n) }, sphere, allLabel = "Все сферы",
                ) { sphere = it }
            }
            SortChips(sort) { sort = it; if (it == TalkSort.SHUFFLE) seed = Random.nextInt() }
            PageInfo(found.size, page, paged.size, { paged.size = it; paged.page = 0 }, "найдено")
            PageBar(page, Paging.pages(found.size, paged.size), { paged.go(it) })
            Gap(8.dp)
            if (found.isEmpty()) Text("Ничего не нашлось. Уберите часть слов или фильтров.", color = extra.dim, fontSize = 13.sp)
        }
        val slice = Paging.slice(found, page, paged.size)
        itemsIndexed(slice, key = { _, h -> h.kind + h.id }) { i, h ->
            var open by rememberSaveable(h.kind + h.id) { mutableStateOf(false) }
            Tile(Modifier.padding(bottom = 8.dp), onClick = { open = !open }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("№${page * paged.size + i + 1}", fontSize = 11.sp, color = extra.dim)
                    Glyph(h.glyph, 18.dp, Modifier.padding(start = 8.dp))
                    Text("${h.kind} · ${h.sphere}", Modifier.weight(1f).padding(start = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    IconButton(onClick = { TalkRepo.share(ctx, listOf(h.title, h.text).filter { it.isNotBlank() }.joinToString("\n\n")) }) {
                        Icon(Icons.Filled.Share, "Поделиться")
                    }
                    if (h.kind == "Факты" || h.kind == "Истории") IconButton(onClick = { toggleFavorite(h.id) }) {
                        if (h.id in favorites) Icon(Icons.Filled.Star, "Убрать из избранного", tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Outlined.StarOutline, "В избранное")
                    }
                }
                if (h.title.isNotBlank()) Text(highlighted(h.title, query, hl), fontWeight = FontWeight.SemiBold)
                Text(highlighted(h.text, query, hl), fontSize = 14.sp, lineHeight = 20.sp, maxLines = if (open || h.kind == "Факты") Int.MAX_VALUE else 4)
            }
        }
        item { Gap(6.dp); PageBar(page, Paging.pages(found.size, paged.size), { paged.go(it) }) }
    }
}

@Composable
private fun StoriesTab(data: TalkRepo.Data, s: AppSettings, favorites: List<Int>) {
    val listState = rememberLazyListState()
    val paged = rememberPaged(listState, 1)
    var q by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TalkSort.ORDER) }
    var seed by rememberSaveable { mutableIntStateOf(Random.nextInt()) }
    val spheres = SmallTalk.decodeSet(s.talkSpheres)
    val query = remember(q) { TextQuery.parse(q) }
    val pool = remember(data, s.talkSpheres) { data.stories.filter { spheres.isEmpty() || it.sphere in spheres }.ifEmpty { data.stories } }
    val found = remember(pool, query, sort, seed, favorites) {
        sorted(pool.filter { TextQuery.matches(it.title + " " + it.text + " " + it.sphere, query) }, sort, query, seed, favorites, { it.title }, { it.text }, { it.id })
    }
    LaunchedEffect(q, sort, s.talkSpheres, seed) { paged.page = 0 }
    val page = Paging.clamp(paged.page, found.size, paged.size)
    val hl = MaterialTheme.colorScheme.primary.copy(alpha = .22f)
    TabList(2, listState) {
        item(key = "stories_head") {
            val counts = remember(data) { data.stories.groupingBy { it.sphere }.eachCount() }
            SphereChips(counts, spheres) { saveSpheres(it) }
            Gap(10.dp)
            SearchField(q, { q = it }, "Поиск по историям")
            Gap(6.dp)
            SortChips(sort) { sort = it; if (it == TalkSort.SHUFFLE) seed = Random.nextInt() }
            PageInfo(found.size, page, paged.size, { paged.size = it; paged.page = 0 }, "историй")
            PageBar(page, Paging.pages(found.size, paged.size), { paged.go(it) })
            Gap(8.dp)
        }
        items(Paging.slice(found, page, paged.size), key = { it.id }) { st -> StoryCard(st, st.id in favorites, query, hl, forceOpen = !query.isEmpty) }
        item { Gap(6.dp); PageBar(page, Paging.pages(found.size, paged.size), { paged.go(it) }) }
    }
}

@Composable
private fun StoryCard(
    st: SmallTalk.Story, favorite: Boolean, query: TextQuery.Query = TextQuery.Query(),
    hl: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent, forceOpen: Boolean = false,
) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var open by rememberSaveable(st.id) { mutableStateOf(false) }
    Tile(Modifier.padding(bottom = 8.dp), onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(SmallTalk.glyph(st.sphere), 22.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(highlighted(st.title, query, hl), fontWeight = FontWeight.SemiBold)
                Text(st.sphere, fontSize = 12.sp, color = extra.dim)
            }
            IconButton(onClick = { toggleFavorite(st.id) }) {
                if (favorite) Icon(Icons.Filled.Star, "Убрать из избранного", tint = MaterialTheme.colorScheme.primary)
                else Icon(Icons.Outlined.StarOutline, "В избранное")
            }
        }
        if (open || forceOpen) {
            Text(highlighted(st.text, query, hl), fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { TalkRepo.share(ctx, "${st.title}\n\n${st.text}") }) { Icon(Icons.Filled.Share, "Поделиться") }
            }
        } else Text(st.text, fontSize = 13.sp, color = extra.dim, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionsTab(data: TalkRepo.Data) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val listState = rememberLazyListState()
    val paged = rememberPaged(listState, 1)
    val situations = remember(data) { data.questions.map { it.situation }.distinct() }
    var situation by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    val query = remember(q) { TextQuery.parse(q) }
    val pool = data.questions.filter { situation.isEmpty() || it.situation == situation }
    val found = remember(pool, query) { pool.filter { TextQuery.matches(it.text + " " + it.situation, query) } }
    var pick by rememberSaveable(situation) { mutableIntStateOf(if (pool.isEmpty()) -1 else Random.nextInt(pool.size)) }
    LaunchedEffect(q, situation) { paged.page = 0 }
    val page = Paging.clamp(paged.page, found.size, paged.size)
    val hl = MaterialTheme.colorScheme.primary.copy(alpha = .22f)
    TabList(3, listState) {
        item(key = "q_head") {
            com.dasein.poryadok.ui.common.SingleFilter(
                "Ситуация", situations.map { t -> FilterOption(t, t, data.questions.count { it.situation == t }) }, situation, allLabel = "Все ситуации",
            ) { situation = it }
            Gap(10.dp)
            pool.getOrNull(pick)?.let { qq ->
                Tile(color = MaterialTheme.colorScheme.primary.copy(alpha = .14f)) {
                    Text(qq.situation, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text(qq.text, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Спросите и слушайте: уточняйте детали из ответа.", Modifier.weight(1f), fontSize = 12.sp, color = extra.dim)
                        IconButton(onClick = { TalkRepo.share(ctx, qq.text) }) { Icon(Icons.Filled.Share, "Поделиться") }
                        FilledIconButton(
                            onClick = { if (pool.size > 1) { var n: Int; do { n = Random.nextInt(pool.size) } while (n == pick); pick = n } },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) { Icon(Icons.Filled.Refresh, "Другой вопрос") }
                    }
                }
            }
            SectionTitle(if (situation.isEmpty()) "Все вопросы · ${pool.size}" else "$situation · ${pool.size}")
            SearchField(q, { q = it }, "Поиск по вопросам")
            PageInfo(found.size, page, paged.size, { paged.size = it; paged.page = 0 }, "вопросов")
            PageBar(page, Paging.pages(found.size, paged.size), { paged.go(it) })
        }
        itemsIndexed(Paging.slice(found, page, paged.size), key = { _, it -> it.id }) { i, qq ->
            Row(Modifier.fillMaxWidth().clickable { TalkRepo.share(ctx, qq.text) }.padding(vertical = 6.dp)) {
                Text("${page * paged.size + i + 1}.", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(end = 8.dp))
                Text(highlighted(qq.text, query, hl), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        item { Gap(6.dp); PageBar(page, Paging.pages(found.size, paged.size), { paged.go(it) }) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.favoritesTab(data: TalkRepo.Data, favorites: List<Int>, goTab: (Int) -> Unit) {
    val facts = data.facts.associateBy { it.id }
    val stories = data.stories.associateBy { it.id }
    val favFacts = favorites.mapNotNull { facts[it] }
    val favStories = favorites.mapNotNull { stories[it] }
    if (favFacts.isEmpty() && favStories.isEmpty()) item {
        Text("Пока пусто. Нажмите ☆ у факта или истории — они появятся здесь, чтобы освежить их перед встречей.", color = LocalExtra.current.dim, fontSize = 14.sp)
    }
    if (favFacts.isNotEmpty()) item { SectionTitle("Факты · ${favFacts.size}") }
    items(favFacts, key = { "f${it.id}" }) { f ->
        val ctx = LocalContext.current
        Tile(Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(SmallTalk.glyph(f.tag), 20.dp)
                Text(f.tag, Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { TalkRepo.share(ctx, f.text) }) { Icon(Icons.Filled.Share, "Поделиться") }
                IconButton(onClick = { toggleFavorite(f.id) }) { Icon(Icons.Filled.Star, "Убрать из избранного", tint = MaterialTheme.colorScheme.primary) }
            }
            Text(f.text, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
    if (favStories.isNotEmpty()) item { SectionTitle("Истории · ${favStories.size}") }
    items(favStories, key = { "s${it.id}" }) { st -> StoryCard(st, true) }
}


/** Сколько фактов пришло из сети и кнопка «загрузить новые» (Википедия «Знаете ли вы» и ИИ, если подключён). */
@Composable
private fun OnlineFactsLine() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ver = TalkRepo.version.intValue
    val n = remember(ver) { com.dasein.poryadok.system.TalkOnline.count(ctx) }
    var busy by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            if (n > 0) "Из сети: $n — новые факты подгружаются сами, когда есть интернет" else "Новые факты подгружаются из сети сами, когда есть интернет",
            fontSize = 12.sp, color = extra.dim, modifier = Modifier.weight(1f),
        )
        com.dasein.poryadok.ui.common.TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                if (!com.dasein.poryadok.system.Offline.hasNetwork(ctx)) {
                    android.widget.Toast.makeText(ctx, "Нет интернета", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    // Принудительно: сбрасываем «уже загружали сегодня».
                    ctx.getSharedPreferences("talk_online", android.content.Context.MODE_PRIVATE).edit().remove("wiki_day").remove("ai_day").apply()
                    val got = com.dasein.poryadok.system.TalkOnline.sync(ctx)
                    android.widget.Toast.makeText(ctx, if (got > 0) "Новых фактов: $got" else "Новых фактов пока нет", android.widget.Toast.LENGTH_SHORT).show()
                }
                busy = false
            }
        }) { Text(if (busy) "Загружаю…" else "Загрузить новые", fontSize = 13.sp) }
    }
}


/**
 * «Тема дня»: напишите любую тему (праздник, новость, место, хобби) или выберите подсказку —
 * ИИ подберёт интересные факты, фразы, чтобы начать разговор, и вопросы собеседнику. Факты можно сохранить в каталог.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TopicOfDay() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val aiOn = remember { com.dasein.poryadok.system.Ai.enabled(ctx) }
    val today = remember { java.time.LocalDate.now() }
    val holidays = remember { runCatching { com.dasein.poryadok.data.HolidayRepo.entries(ctx, today, emptyList(), emptyList()).map { it.name }.take(4) }.getOrDefault(emptyList()) }
    val ideas = holidays + listOf("Кофе", "Путешествия", "Космос", "Кино этого года", "Осень", "Еда разных стран")
    var topic by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var res by remember { mutableStateOf<com.dasein.poryadok.system.TalkOnline.Topic?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<Set<String>>(emptySet()) }
    fun go(t: String) {
        if (t.isBlank()) return
        topic = t; busy = true; error = null; res = null
        scope.launch {
            runCatching { com.dasein.poryadok.system.TalkOnline.topic(ctx, t.trim()) }.onSuccess { res = it }.onFailure { error = it.message }
            busy = false
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(LocalExtra.current.card).padding(14.dp)) {
        Text("Тема дня", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 16.sp)
        if (!aiOn) {
            Text("Напишите тему — ИИ подберёт факты и вопросы для разговора. Нужен бесплатный ключ: «Настройки» → «ИИ-помощник».", fontSize = 12.sp, color = extra.dim)
            return@Column
        }
        Text("Напишите тему или выберите подсказку — ИИ подберёт факты, как начать разговор и что спросить.", fontSize = 12.sp, color = extra.dim)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            ideas.forEach { t -> Pill(t, topic == t) { go(t) } }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                topic, { topic = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp),
                placeholder = { Text("Например: Хэллоуин, чемпионат мира, Армения") },
            )
            com.dasein.poryadok.ui.common.Button(onClick = { go(topic) }, enabled = topic.isNotBlank() && !busy, modifier = Modifier.padding(start = 8.dp)) { com.dasein.poryadok.ui.common.AiLabel("Найти", busy, "…") }
        }
        if (busy) Text("ИИ подбирает факты…", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
        error?.let { Text(it, fontSize = 12.sp, color = extra.danger) }
        res?.let { r ->
            if (r.facts.isNotEmpty()) {
                Text("Факты", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                r.facts.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                        Text("• $f", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        val on = f in saved
                        Text(
                            if (on) "✓" else "+", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !on) {
                                com.dasein.poryadok.system.TalkOnline.addFacts(ctx, com.dasein.poryadok.system.TalkOnline.SPHERE_WIKI, listOf(f)); saved = saved + f
                            }.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                com.dasein.poryadok.ui.common.TextButton(onClick = {
                    val n = com.dasein.poryadok.system.TalkOnline.addFacts(ctx, com.dasein.poryadok.system.TalkOnline.SPHERE_WIKI, r.facts); saved = saved + r.facts
                    android.widget.Toast.makeText(ctx, "В каталог добавлено: $n", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Сохранить все факты в каталог") }
            }
            if (r.openers.isNotEmpty()) {
                Text("Как начать разговор", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                r.openers.forEach { Text("— $it", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
            if (r.questions.isNotEmpty()) {
                Text("Что спросить", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                r.questions.forEach { Text("? $it", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}
