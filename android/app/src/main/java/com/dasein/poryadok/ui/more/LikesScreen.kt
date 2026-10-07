package com.dasein.poryadok.ui.more

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.system.Likes
import com.dasein.poryadok.ui.common.LikeButton
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.likedColor
import com.dasein.poryadok.ui.common.rememberLikes
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch

const val LIKES_ROUTE = "likes"

/** «Понравившееся»: отмеченные «+» события, факты и праздники; выгрузка одним файлом. */
@Composable
fun LikesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val all = rememberLikes()
    var kind by rememberSaveable { mutableStateOf("") }
    val shown = all.filter { kind.isEmpty() || it.kind == kind }
    Screen("Понравившееся", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Tile {
                    Text("Отмечено: ${all.size}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Нажимайте «+» на плашках «В этот день в истории», «В этот день в культуре», «Интересный факт» и в праздниках. " +
                            "Потом выгрузите файл и отправьте его Claude — он подберёт больше новостей и фактов на любимые темы.",
                        fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
                    )
                    Button(onClick = { scope.launch { Likes.share(ctx) } }, enabled = all.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Выгрузить понравившееся (файл)")
                    }
                }
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Всё · ${all.size}", kind.isEmpty()) { kind = "" }
                    Likes.KIND_NAMES.forEach { (k, n) ->
                        val c = all.count { it.kind == k }
                        if (c > 0) Pill("$n · $c", kind == k) { kind = if (kind == k) "" else k }
                    }
                }
            }
            if (shown.isEmpty()) item { Text("Пока ничего не отмечено.", color = extra.dim, modifier = Modifier.padding(top = 12.dp)) }
            items(shown, key = { it.key }) { l ->
                Tile(color = likedColor(true)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                listOfNotNull(Likes.KIND_NAMES[l.kind], l.year.takeIf { it != 0 }?.toString(), l.tags.joinToString(", ").ifBlank { null }).joinToString(" · "),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                            )
                            Text(l.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                            if (l.text.isNotBlank() && l.text != l.title) Text(l.text, fontSize = 13.sp, color = extra.dim, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        LikeButton(l, true)
                    }
                }
            }
        }
    }
}
