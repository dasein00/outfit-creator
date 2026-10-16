package com.dasein.poryadok.ui.tutor

import android.content.ClipData
import com.dasein.poryadok.ui.common.btnLabel
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dasein.poryadok.ui.common.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.dasein.poryadok.ui.common.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.EnDict
import com.dasein.poryadok.logic.Phrases
import com.dasein.poryadok.logic.Tutor
import com.dasein.poryadok.system.EnDictStore
import com.dasein.poryadok.system.OfflineTranslator
import com.dasein.poryadok.system.TutorStore
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private fun hasCyrillic(s: String) = s.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("DASEIN", text))
    Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
}

/** Репетитор → «Переводчик»: слова с подсказками из словаря, фразы и предложения, идиомы и устойчивые фразы. */
@Composable
fun TranslatorScreen(nav: NavHostController, initialTab: Int) {
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    val sp = rememberSpeaker()
    Screen("Переводчик", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text(btnLabel("Перевод")) })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text(btnLabel("Идиомы и фразы")) })
            }
            if (tab == 0) TranslateTab(sp) else PhrasesTab(sp)
        }
    }
}

@Composable
private fun TranslateTab(sp: Speaker) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val dict by produceState<List<EnDict.Entry>?>(null) { value = EnDictStore.all(ctx) }
    val st by TutorStore.flow(ctx).collectAsState()
    val mine = remember(st?.custom) { st?.custom.orEmpty().map { it.en.lowercase() }.toSet() }
    var text by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf<EnDict.Entry?>(null) }
    var ready by remember { mutableStateOf<Boolean?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var translating by remember { mutableStateOf(false) }
    // Откуда перевод: «ИИ» (в сети, если подключён) или «офлайн» (модель на телефоне).
    var via by remember { mutableStateOf("") }
    val aiOn = remember { com.dasein.poryadok.system.Ai.enabled(ctx) }
    LaunchedEffect(Unit) { ready = OfflineTranslator.ready() }

    val q = text.trim()
    val ruToEn = hasCyrillic(q)
    val isPhrase = q.contains(' ') && q.split(Regex("\\s+")).size > 1
    // Подсказки по мере набора: по последнему слову для слов, по всей строке для коротких выражений.
    val suggestions = remember(q, dict) {
        val d = dict ?: return@remember emptyList()
        if (q.isEmpty() || q.length > 40) emptyList() else EnDict.search(d, q, 8)
    }
    val exact = suggestions.firstOrNull { it.en.equals(q, true) }

    // Перевод фраз и предложений (и слов, которых нет в словаре) — офлайн-моделью, с паузой после набора.
    LaunchedEffect(q, ready) {
        result = null; via = ""
        if (q.isEmpty()) return@LaunchedEffect
        val online = aiOn && com.dasein.poryadok.system.Offline.hasNetwork(ctx)
        // В сети — ИИ переводит всё (и слова — с учётом контекста); без сети — словарь и модель на телефоне.
        if (!online && (ready != true || (!isPhrase && exact != null))) return@LaunchedEffect
        delay(if (online) 900 else 600)
        translating = true
        var r: Result<String> = Result.failure(Exception())
        if (online) {
            r = runCatching { aiTranslate(ctx, q, enToRu = !ruToEn) }
            if (r.isSuccess) via = "ИИ"
        }
        if (r.isFailure && ready == true) { r = OfflineTranslator.translate(q, enToRu = !ruToEn); if (r.isSuccess) via = "офлайн" }
        translating = false
        result = r.getOrNull()
        r.getOrNull()?.let { out ->
            delay(1500)
            TutorStore.addTranslation(ctx, TutorStore.Translation(q, out, !ruToEn, System.currentTimeMillis()))
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (q.isEmpty()) "Английский ⇄ русский" else if (ruToEn) "Русский → английский" else "Английский → русский", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (text.isNotEmpty()) TextButton(onClick = { text = ""; picked = null }) { Text(btnLabel("Очистить")) }
            }
            OutlinedTextField(
                text, { text = it; picked = null }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 6,
                placeholder = { Text("Введите слово, фразу или предложение — по-английски или по-русски") },
                shape = RoundedCornerShape(14.dp),
            )
        }
        if (q.isEmpty()) item {
            Text(
                if (aiOn) "В сети переводит ИИ (точнее, с учётом смысла), без интернета — словарь и модель на телефоне."
                else "Сейчас перевод — без интернета. Чтобы в сети переводил ИИ, добавьте бесплатный ключ в «Настройки» → «ИИ-помощник».",
                fontSize = 12.sp, color = extra.dim,
            )
        }
        if (ready == false) item {
            Tile(color = extra.warn.copy(alpha = .14f)) {
                Text(btnLabel("Перевод фраз и предложений без интернета"), fontWeight = FontWeight.SemiBold)
                Text(
                    "Слова переводятся встроенным словарём уже сейчас. Чтобы переводить целые фразы и предложения, скачайте модель Google (английский и русский, около 60 МБ) — один раз, дальше всё работает офлайн.",
                    fontSize = 13.sp, color = extra.dim,
                )
                if (downloading) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text(btnLabel("  Скачиваю…"), fontSize = 13.sp)
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = {
                        downloading = true
                        scope.launch {
                            val r = OfflineTranslator.download(wifiOnly = false)
                            downloading = false
                            ready = r.isSuccess && OfflineTranslator.ready()
                            Toast.makeText(ctx, if (ready == true) "Переводчик готов и работает без интернета" else "Не удалось скачать. Проверьте интернет и сервисы Google", Toast.LENGTH_LONG).show()
                        }
                    }) { Text(btnLabel("Скачать")) }
                    OutlinedButton(onClick = {
                        downloading = true
                        scope.launch {
                            val r = OfflineTranslator.download(wifiOnly = true)
                            downloading = false
                            ready = r.isSuccess && OfflineTranslator.ready()
                        }
                    }) { Text(btnLabel("Только по Wi-Fi")) }
                }
            }
        }
        // Перевод фразы/предложения
        if (q.isNotEmpty() && (isPhrase || exact == null || result != null || translating)) item {
            Tile {
                Text((if (ruToEn) "По-английски" else "По-русски") + if (via.isNotEmpty()) " · $via" else "", fontSize = 12.sp, color = extra.dim)
                when {
                    translating && result == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(btnLabel("  Перевожу…"), fontSize = 13.sp, color = extra.dim)
                    }
                    result != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(result.orEmpty(), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp, modifier = Modifier.weight(1f).padding(top = 4.dp))
                            SpeakButton(sp, if (ruToEn) result.orEmpty() else q)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { copy(ctx, result.orEmpty()) }) { Text(btnLabel("Копировать")) }
                            val en = if (ruToEn) result.orEmpty() else q
                            val ru = if (ruToEn) q else result.orEmpty()
                            if (en.split(' ').size <= 6 && en.lowercase() !in mine) TextButton(onClick = {
                                scope.launch {
                                    val w = TutorStore.MyWord(en, ru)
                                    TutorStore.update(ctx) { it.copy(custom = it.custom.filter { c -> !c.en.equals(w.en, true) } + w) }
                                    TutorStore.startLearning(ctx, Tutor.Word(w.en, w.ru, "", "", "B1", 1, "my", true), Dates.today())
                                    Toast.makeText(ctx, "Добавлено в «Мои слова»", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text(btnLabel("+ В мои слова")) }
                        }
                    }
                    ready == true -> Text("Перевод появится через секунду после набора", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
                    else -> Text(
                        if (suggestions.isEmpty()) "Этого нет во встроенном словаре. Для фраз и предложений скачайте модель перевода выше." else "Выберите слово из подсказок ниже.",
                        fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        // Точное слово из словаря — сразу полная карточка
        val card = picked ?: exact
        if (card != null && !isPhrase) item(key = "card_${card.en}") {
            DictCard(card, sp, startOpen = true, added = card.en.lowercase() in mine) { addWord(ctx, scope, card) }
        }
        // Живые примеры употребления (ИИ): для слова из словаря или для переведённой фразы.
        val exWord = when {
            card != null && !isPhrase -> card.en
            result != null && !translating -> if (ruToEn) result.orEmpty() else q
            else -> null
        }
        if (exWord != null && exWord.split(' ').size <= 6) item(key = "ex_$exWord") { AiExamples(exWord, sp) }
        if (suggestions.isNotEmpty() && (card == null || suggestions.size > 1)) {
            item { Text(if (ruToEn) "Подходящие слова" else "Подсказки из словаря", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp)) }
            items(suggestions.filter { it != card }, key = { "s_" + it.en }) { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(extra.card).clickable { picked = e; if (!ruToEn) text = e.en }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.en, fontWeight = FontWeight.SemiBold)
                        Text(e.shortRu, fontSize = 13.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (e.ipa.isNotBlank()) Text("[${e.ipa}]", fontSize = 12.sp, color = extra.dim, maxLines = 1)
                }
            }
        }
        // История
        val hist = st?.translations.orEmpty()
        if (q.isEmpty() && hist.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(btnLabel("Недавние переводы"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { TutorStore.clearTranslations(ctx) } }) { Text(btnLabel("Очистить")) }
                }
            }
            items(hist, key = { "h_" + it.src + it.at }) { h ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(extra.card).clickable { text = h.src }.padding(12.dp)) {
                    Text(h.src, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(h.dst, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (q.isEmpty() && hist.isEmpty()) item {
            Text(
                "Начните вводить слово — снизу появятся подсказки из словаря на 32 тысячи слов. Фразы и целые предложения переводятся моделью на телефоне. Озвучка — кнопка 🔊.",
                fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun addWord(ctx: Context, scope: kotlinx.coroutines.CoroutineScope, e: EnDict.Entry) {
    scope.launch {
        val w = TutorStore.MyWord(e.en, e.shortRu)
        TutorStore.update(ctx) { it.copy(custom = it.custom.filter { c -> !c.en.equals(w.en, true) } + w) }
        TutorStore.startLearning(ctx, Tutor.Word(w.en, w.ru, "", "", "B1", 1, "my", true), Dates.today())
    }
}

/** Идиомы и устойчивые фразы: список с примерами и озвучкой, тренировка «выбери значение» с интервальным повторением. */
@Composable
private fun PhrasesTab(sp: Speaker) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val all = remember { runCatching { Phrases.parse(ctx.assets.open("tutor/phrases.json").bufferedReader().use { it.readText() }) }.getOrDefault(emptyList()) }
    val st by TutorStore.flow(ctx).collectAsState()
    val progress = st?.phraseProgress.orEmpty()
    var cat by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    var quiz by remember { mutableStateOf<List<Phrases.Phrase>?>(null) }
    val today = Dates.today()
    val pool = all.filter { cat.isEmpty() || it.cat == cat }

    quiz?.let { list -> PhraseQuiz(list, all, sp) { quiz = null }; return }

    val learned = pool.count { (progress[it.key]?.box ?: 0) >= Tutor.LEARNED_BOX }
    val due = pool.count { p -> progress[p.key]?.let { it.due <= today } == true }
    val shown = Phrases.search(pool, q)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Все · ${all.size}", cat.isEmpty()) { cat = "" }
                Phrases.CATS.forEach { (k, n) -> Pill("$n · ${all.count { it.cat == k }}", cat == k) { cat = if (cat == k) "" else k } }
            }
            Tile(Modifier.padding(top = 8.dp)) {
                Text("Выучено $learned из ${pool.size}" + if (due > 0) " · пора повторить: $due" else "", fontSize = 13.sp, color = extra.dim)
                Button(
                    onClick = { quiz = Phrases.session(pool, progress, today, 10, Random(System.nanoTime())) },
                    enabled = pool.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text(if (due > 0) "Повторить и выучить новые (10)" else "Учить 10 выражений") }
                Text("Покажем фразу и пример — выберите правильное значение. Верные ответы возвращаются через 1, 2, 4, 7, 15, 30 дней.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            }
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, placeholder = { Text(btnLabel("Поиск по-английски или по-русски")) }, shape = RoundedCornerShape(14.dp))
        }
        items(shown, key = { it.key }) { p ->
            val box = progress[p.key]?.box
            Tile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when { box == null -> "○"; box >= Tutor.LEARNED_BOX -> "●"; else -> "◐" }, fontSize = 14.sp,
                        color = if (box != null && box >= Tutor.LEARNED_BOX) extra.ok else extra.dim, modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(p.en, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    SpeakButton(sp, p.en, 32)
                }
                Text(p.ru, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                if (p.ex.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("“${p.ex}”", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        SpeakButton(sp, p.ex, 28)
                    }
                    Text(p.exRu, fontSize = 12.sp, color = extra.dim)
                }
            }
        }
    }
}

@Composable
private fun PhraseQuiz(list: List<Phrases.Phrase>, all: List<Phrases.Phrase>, sp: Speaker, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var i by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Phrases.Phrase?>(null) }
    var right by remember { mutableIntStateOf(0) }
    val today = Dates.today()
    if (i >= list.size) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(btnLabel("Готово!"), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Верно: $right из ${list.size}", fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onDone, modifier = Modifier.padding(top = 16.dp)) { Text(btnLabel("К списку")) }
        }
        return
    }
    val p = list[i]
    val opts = remember(p) { Phrases.options(p, all, Random(p.key.hashCode() + i)) }
    LaunchedEffect(p) { sp.say(p.en) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1} / ${list.size}", fontSize = 13.sp, color = extra.dim, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text(btnLabel("Закончить")) }
        }
        Tile {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.en, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SpeakButton(sp, p.en)
            }
            if (p.ex.isNotBlank()) Text("“${p.ex}”", fontSize = 14.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
        }
        Text(btnLabel("Что это значит?"), fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(vertical = 8.dp))
        opts.forEach { o ->
            val c = chosen
            val bg = when {
                c == null -> extra.card
                o == p -> extra.ok.copy(alpha = .25f)
                o == c -> extra.danger.copy(alpha = .25f)
                else -> extra.card
            }
            Box(
                Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                    .clickable(enabled = c == null) {
                        chosen = o
                        val ok = o == p
                        if (ok) right++
                        scope.launch { TutorStore.answerPhrase(ctx, p.key, ok, today) }
                    }.padding(14.dp),
            ) { Text(o.ru, fontSize = 15.sp) }
        }
        if (chosen != null) {
            Text(if (chosen == p) "Верно!" else "Правильно: ${p.ru}", fontWeight = FontWeight.SemiBold, color = if (chosen == p) extra.ok else extra.danger, modifier = Modifier.padding(top = 4.dp))
            if (p.exRu.isNotBlank()) Text(p.exRu, fontSize = 13.sp, color = extra.dim)
            Button(onClick = { chosen = null; i++ }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(if (i + 1 < list.size) "Дальше" else "Итоги") }
        }
    }
}


/** Перевод через бесплатный ИИ: естественно, с сохранением смысла и стиля. */
private suspend fun aiTranslate(ctx: Context, text: String, enToRu: Boolean): String {
    val (from, to) = if (enToRu) "английского" to "русский" else "русского" to "английский"
    val system = "Ты профессиональный переводчик. Переведи текст с $from на $to язык естественно и точно, сохраняя смысл, тон и стиль. " +
        "Если это одно слово — дай основной перевод и через запятую 1–2 частых варианта. " +
        "Ответь только переводом, без пояснений, кавычек и транслитерации."
    return com.dasein.poryadok.system.Ai.ask(ctx, system, text).trim().trim('"', '«', '»').trim()
}
