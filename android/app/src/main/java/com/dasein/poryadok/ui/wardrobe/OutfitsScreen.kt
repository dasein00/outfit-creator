package com.dasein.poryadok.ui.wardrobe

import androidx.compose.foundation.Image
import com.dasein.poryadok.ui.common.Ic
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Outfit
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.rememberImage
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette

private fun Outfit.worn(): List<Long> = wornDays.split(",").mapNotNull { it.toLongOrNull() }.sorted()

@Composable
fun OutfitsScreen(nav: NavHostController) {
    val extra = LocalExtra.current
    val outfits by observe(emptyList()) { Graph.dao.outfits() }
    var season by remember { mutableStateOf("все") }
    var open by remember { mutableStateOf<Outfit?>(null) }
    val today = Dates.today()
    val shown = outfits.filter { season == "все" || it.season == season }
    val forgotten = outfits.filter { o -> o.worn().lastOrNull()?.let { today - it > 30 } ?: (today - Dates.dayOf(o.createdAt) > 14) }.take(6)

    Screen(title = "Мои луки", onBack = { nav.popBackStack() }) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad),
        ) {
            item(span = { GridItemSpan(2) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems(SEASONS) { (id, label) -> Pill(label, season == id) { season = id } }
                }
            }
            if (forgotten.isNotEmpty() && season == "все") item(span = { GridItemSpan(2) }) {
                Column {
                    Text("Давно не надевали — может, сегодня?", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(vertical = 4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems(forgotten, key = { "f" + it.id }) { o ->
                            val img by rememberImage(o.preview.ifBlank { null }, 300)
                            Box(Modifier.height(120.dp).aspectRatio(.5f).clip(RoundedCornerShape(12.dp)).background(Palette.Stage).clickable { open = o }) {
                                img?.let { Image(it, o.name, Modifier.fillMaxWidth(), contentScale = ContentScale.Crop) }
                            }
                        }
                    }
                }
            }
            if (shown.isEmpty()) item(span = { GridItemSpan(2) }) {
                Empty(Ic.hanger, "Здесь пока пусто", "Соберите образ в гардеробе и нажмите «Сохранить лук».")
            }
            items(shown, key = { it.id }) { o ->
                val img by rememberImage(o.preview.ifBlank { null }, 500)
                val worn = o.worn()
                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(extra.card).clickable { open = o }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(.62f).background(Palette.Stage)) {
                        img?.let { Image(it, o.name, Modifier.fillMaxWidth().aspectRatio(.62f), contentScale = ContentScale.Crop) }
                        if (today in worn) Text(
                            "сегодня", fontSize = 10.sp, color = androidx.compose.ui.graphics.Color(0xFF241C0D),
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Brass).padding(horizontal = 5.dp),
                        )
                    }
                    Column(Modifier.padding(10.dp)) {
                        Text(o.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${o.season} · " + if (worn.isEmpty()) "ещё не надевали" else "${worn.size} ${plural(worn.size, "раз", "раза", "раз")}",
                            fontSize = 12.sp, color = extra.dim,
                        )
                    }
                }
            }
        }
    }

    open?.let { o ->
        var confirm by remember { mutableStateOf(false) }
        val big by rememberImage(o.preview.ifBlank { null }, 900)
        val worn = o.worn()
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(o.name) },
            text = {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(.62f).clip(RoundedCornerShape(12.dp)).background(Palette.Stage)) {
                        big?.let { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
                    }
                    Text(
                        if (worn.isEmpty()) "Ещё не надевали" else "Надевали ${worn.size} ${plural(worn.size, "раз", "раза", "раз")}, последний — ${Dates.label(worn.last())}",
                        fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(Modifier.padding(top = 6.dp)) {
                        TextButton(onClick = {
                            val days = if (today in worn) worn - today else worn + today
                            val upd = o.copy(wornDays = days.joinToString(","))
                            io { Graph.dao.upsertOutfit(upd) }
                            open = upd
                        }) { Text(if (today in worn) "Не ношу сегодня" else "👕 Ношу сегодня", fontWeight = FontWeight.SemiBold) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    WardrobeBus.pendingOutfit = o.id
                    open = null
                    nav.popBackStack()
                }) { Text("Открыть в редакторе") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirm = true }) { Text("Удалить", color = extra.danger) }
                    TextButton(onClick = { open = null }) { Text("Закрыть") }
                }
            },
        )
        if (confirm) ConfirmDialog("Удалить лук «${o.name}»?", "", onDismiss = { confirm = false }) {
            io { if (o.preview.isNotBlank()) java.io.File(o.preview).delete(); Graph.dao.deleteOutfit(o) }
            open = null
        }
    }
}
