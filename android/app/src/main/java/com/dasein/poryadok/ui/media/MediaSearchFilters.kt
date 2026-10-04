package com.dasein.poryadok.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.logic.MediaDiscover
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra

/** Панель фильтров поиска в каталогах: годы, страна, жанр, режиссёр или актёр, рейтинг, сортировка. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaFilterPanel(kind: Int, f: MediaDiscover.Filter, onChange: (MediaDiscover.Filter) -> Unit) {
    val extra = LocalExtra.current
    val book = kind == MediaKind.BOOK
    var allCountries by remember { mutableStateOf(false) }
    Tile(color = extra.cardHigh) {
        Label(if (book) "Год издания" else "Годы выхода")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                NumberField(f.yearFrom?.toString().orEmpty(), { v -> onChange(f.copy(yearFrom = v.filter { it.isDigit() }.take(4).toIntOrNull()?.takeIf { it > 0 })) }, "с", decimal = false)
            }
            HGap(8.dp)
            Box(Modifier.weight(1f)) {
                NumberField(f.yearTo?.toString().orEmpty(), { v -> onChange(f.copy(yearTo = v.filter { it.isDigit() }.take(4).toIntOrNull()?.takeIf { it > 0 })) }, "по", decimal = false)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            MediaDiscover.DECADES.forEach { (t, r) ->
                val on = f.yearFrom == r.first && f.yearTo == r.second
                Pill(t, on) { onChange(if (on) f.copy(yearFrom = null, yearTo = null) else f.copy(yearFrom = r.first, yearTo = r.second)) }
            }
        }

        if (!book) {
            Label("Страна")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val shown = if (allCountries || MediaDiscover.COUNTRIES.indexOfFirst { it.title == f.country } >= 10) MediaDiscover.COUNTRIES else MediaDiscover.COUNTRIES.take(10)
                shown.forEach { c -> Pill(c.title, f.country == c.title) { onChange(f.copy(country = if (f.country == c.title) null else c.title)) } }
                if (!allCountries && shown.size < MediaDiscover.COUNTRIES.size) Pill("Ещё ${MediaDiscover.COUNTRIES.size - shown.size}…", false) { allCountries = true }
            }
            Label("Жанр")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MediaDiscover.GENRES.forEach { g -> Pill(g.title, f.genre == g.title) { onChange(f.copy(genre = if (f.genre == g.title) null else g.title)) } }
            }
        }

        Label(if (book) "Автор" else "Режиссёр или актёр")
        TextInput(f.person, { onChange(f.copy(person = it)) }, if (book) "Например, Ремарк" else "Например, Пак Чхан-ук или Сон Ган Хо")
        if (!book) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            MediaDiscover.Role.entries.forEach { r -> Pill(r.title, f.role == r) { onChange(f.copy(role = r)) } }
        }

        Label("Рейтинг")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(null to "Любой", 6.0 to "от 6", 7.0 to "от 7", 7.5 to "от 7,5", 8.0 to "от 8").forEach { (v, t) -> Pill(t, f.minRating == v) { onChange(f.copy(minRating = v)) } }
        }

        Label("Сортировка")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MediaDiscover.Sort.entries.forEach { s -> Pill(s.title, f.sort == s) { onChange(f.copy(sort = s)) } }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp).clickable { onChange(f.copy(hideOwned = !f.hideOwned)) }) {
            Checkbox(f.hideOwned, { onChange(f.copy(hideOwned = it)) })
            Text("Скрыть то, что уже есть в коллекции", fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (f.count == 0) "Фильтры не выбраны" else "Выбрано фильтров: ${f.count}",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.weight(1f),
            )
            if (f.count > 0 || f.sort != MediaDiscover.Sort.RELEVANCE) TextButton(onClick = { onChange(MediaDiscover.Filter()) }) { Text("Сбросить") }
        }
    }
}

/** «Уточнить»: страны и жанры среди найденного с количеством — одним касанием. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaFacets(countries: List<Pair<String, Int>>, genres: List<Pair<String, Int>>, f: MediaDiscover.Filter, onChange: (MediaDiscover.Filter) -> Unit) {
    if (countries.size < 2 && genres.size < 2) return
    Column(Modifier.padding(top = 6.dp)) {
        Text("Уточнить", fontSize = 12.sp, color = LocalExtra.current.dim)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            if (countries.size >= 2) countries.take(5).forEach { (c, n) -> Pill("$c · $n", f.country == c) { onChange(f.copy(country = if (f.country == c) null else c)) } }
            if (genres.size >= 2) genres.take(6).forEach { (g, n) -> Pill("$g · $n", f.genre == g) { onChange(f.copy(genre = if (f.genre == g) null else g)) } }
        }
    }
}

@Composable
private fun Label(s: String) = Text(s, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
