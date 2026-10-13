package com.dasein.poryadok.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.system.MediaFacts
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch

/**
 * «Интересные факты» в карточке: сами подгружаются из Кинопоиска и Википедии, ещё — от ИИ по кнопке.
 * Спойлеры скрыты, пока не нажмёте. Факты без спойлеров попадают и в Small Talks.
 */
@Composable
fun MediaFactsSection(m: MediaItem) {
    if (m.id == 0L) return
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val ver = MediaFacts.version.intValue
    val saved = remember(m.id, ver) { MediaFacts.saved(ctx, m.id) }
    var busy by remember { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAll by remember(m.id) { mutableStateOf(false) }
    val aiOn = remember { com.dasein.poryadok.system.Ai.enabled(ctx) }
    fun load() {
        if (busy) return
        busy = true; error = null
        scope.launch {
            runCatching { MediaFacts.fetch(ctx, m) }
                .onSuccess { if (it.facts.isEmpty()) error = "В Кинопоиске и Википедии фактов не нашлось" + if (aiOn) " — попросите ИИ." else "." }
                .onFailure { error = "Не удалось загрузить: ${it.message}" }
            busy = false
        }
    }
    // Первый раз открыли карточку — подгружаем сами, если есть интернет.
    LaunchedEffect(m.id) {
        if (saved == null && m.title.isNotBlank() && com.dasein.poryadok.system.Offline.hasNetwork(ctx)) load()
    }
    val facts = saved?.facts.orEmpty()
    SectionTitle(if (facts.isEmpty()) "Интересные факты" else "Интересные факты · ${facts.size}")
    if (busy && facts.isEmpty()) Text("Ищу факты в Кинопоиске и Википедии…", fontSize = 13.sp, color = extra.dim)
    error?.let { Text(it, fontSize = 12.sp, color = extra.dim) }
    val shown = if (showAll) facts else facts.take(6)
    shown.forEach { f ->
        var open by remember(f.text) { mutableStateOf(!f.spoiler) }
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(extra.card)
                .clickable { open = true }.padding(12.dp),
        ) {
            if (open) Text(f.text, fontSize = 14.sp, lineHeight = 20.sp)
            else Text("Спойлер — нажмите, чтобы прочитать", fontSize = 14.sp, color = extra.warn)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(f.source + if (f.source == "ИИ") " · проверяйте" else "", fontSize = 11.sp, color = extra.dim, modifier = Modifier.weight(1f))
                Text(
                    "убрать", fontSize = 11.sp, color = extra.dim,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { MediaFacts.remove(ctx, m.id, f) }.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
    if (facts.size > 6) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Свернуть" else "Показать все ${facts.size}") }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(enabled = !busy, onClick = { load() }) { Text(if (busy) "Загружаю…" else if (facts.isEmpty()) "Найти факты" else "Обновить") }
        if (aiOn) TextButton(enabled = !aiBusy, onClick = {
            aiBusy = true; error = null
            scope.launch {
                runCatching { MediaFacts.askAi(ctx, m) }.onFailure { error = it.message }
                aiBusy = false
            }
        }) { Text(if (aiBusy) "ИИ вспоминает…" else "✨ Ещё факты от ИИ") }
    }
    saved?.premiere?.let { p ->
        Text(
            "Премьера: ${p.substring(8, 10)}.${p.substring(5, 7)}.${p.take(4)} — в этот день ${if (m.kind == MediaKind.BOOK) "книга" else "фильм"} появится в «В этот день в культуре».",
            fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp),
        )
    }
    if (facts.isNotEmpty()) Text("Факты без спойлеров добавлены и в Small Talks.", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = .8f))
}
