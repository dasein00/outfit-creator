package com.dasein.poryadok.ui.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.EnDict
import com.dasein.poryadok.logic.Tutor
import com.dasein.poryadok.system.EnDictStore
import com.dasein.poryadok.system.TutorStore
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SearchField
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Большой англо-русский словарь без интернета: 32 тысячи слов и выражений с транскрипцией,
 * частями речи и озвучкой. Любое слово можно добавить в «Мои слова» и учить.
 */
@Composable
fun TutorDictScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val sp = rememberSpeaker()
    val scope = rememberCoroutineScope()
    val all by produceState<List<EnDict.Entry>?>(null) { value = EnDictStore.all(ctx) }
    val st by TutorStore.flow(ctx).collectAsState()
    val mine = remember(st?.custom) { st?.custom.orEmpty().map { it.en.lowercase() }.toSet() }
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<EnDict.Entry>>(emptyList()) }
    LaunchedEffect(q, all) {
        val list = all ?: return@LaunchedEffect
        delay(150)
        results = if (q.isBlank()) EnDict.frequent(list, 60) else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { EnDict.search(list, q) }
    }
    val offline by sp.offline

    Screen("Словарь английского", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SearchField(q, { q = it }, "Слово по-английски или по-русски")
                if (offline == false) Tile(Modifier.padding(top = 8.dp), color = extra.warn.copy(alpha = .15f)) {
                    Text("Озвучка без интернета", fontWeight = FontWeight.SemiBold)
                    Text(
                        "На телефоне нет английского голоса — без интернета произношение может не работать. Установите голос «Английский (США)» в синтезе речи Google: он весит около 30–50 МБ и дальше работает офлайн.",
                        fontSize = 13.sp, color = extra.dim,
                    )
                    TextButton(onClick = { Speaker.installVoice(ctx) }) { Text("Установить голос") }
                }
                Text(
                    when {
                        all == null -> "Открываю словарь…"
                        q.isBlank() -> "${all?.size ?: 0} слов и выражений, работает без интернета. Самые частые слова:"
                        results.isEmpty() -> "Ничего не нашлось"
                        else -> "Найдено: ${results.size}" + if (results.size >= 80) "+ — уточните запрос" else ""
                    },
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (all == null) item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            items(results, key = { it.en }) { e ->
                DictCard(e, sp, startOpen = results.size <= 3 || e.en.equals(q.trim(), true), added = e.en.lowercase() in mine) {
                    val ru = e.shortRu
                    scope.launch {
                        val w = TutorStore.MyWord(e.en, ru)
                        TutorStore.update(ctx) { it.copy(custom = it.custom.filter { c -> !c.en.equals(w.en, true) } + w) }
                        TutorStore.startLearning(ctx, Tutor.Word(w.en, w.ru, "", "", "B1", 1, "my", true), Dates.today())
                    }
                }
            }
            item {
                Text(
                    "Источник: англо-русский словарь Викисловаря (en.wiktionary.org), лицензия CC BY-SA 3.0. Частотность — по субтитрам OpenSubtitles. Озвучка — голосом телефона.",
                    fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
internal fun DictCard(e: EnDict.Entry, sp: Speaker, startOpen: Boolean, added: Boolean, onAdd: () -> Unit) {
    val extra = LocalExtra.current
    var open by remember(e.en) { mutableStateOf(startOpen) }
    Tile(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.en, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (e.common) Text(
                        "частое", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                if (e.ipa.isNotBlank()) Text("[${e.ipa}]", fontSize = 13.sp, color = extra.dim)
                if (!open) Text(e.shortRu, fontSize = 14.sp, maxLines = 1)
            }
            SpeakButton(sp, e.en)
        }
        if (open) {
            e.senses.groupBy { it.pos }.forEach { (pos, list) ->
                Text(pos, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                list.forEachIndexed { i, s ->
                    Text("${i + 1}. ${s.ru}", fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
                    if (s.gloss.isNotBlank()) Text(s.gloss, fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp)
                }
            }
            if (added) Text("✓ В моих словах", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
            else OutlinedButton(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) { Text("+ В мои слова и учить") }
        }
    }
}
