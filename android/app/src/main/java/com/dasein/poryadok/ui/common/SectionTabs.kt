package com.dasein.poryadok.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.ui.theme.LocalExtra

/**
 * Вкладки раздела, которые всегда помещаются на экран целиком: равные ячейки, иконка и подпись в одну строку.
 * Счётчик показывается маленьким значком у иконки, чтобы не удлинять подпись.
 */
@Composable
fun SectionTabs(labels: List<String>, glyphs: List<String>, selected: Int, counts: List<Int> = emptyList(), onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).clickable { onSelect(i) }.padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Glyph(glyphs[i], 22.dp, dimmed = !on)
                        val n = counts.getOrElse(i) { 0 }
                        if (n > 0) Text(
                            if (n > 99) "99+" else "$n", fontSize = 10.sp, color = scheme.onPrimary, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-4).dp)
                                .background(scheme.primary, RoundedCornerShape(8.dp)).padding(horizontal = 4.dp),
                        )
                    }
                    Text(
                        label, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                        color = if (on) scheme.primary else extra.dim,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                    )
                    Box(Modifier.width(36.dp).height(3.dp).background(if (on) scheme.primary else scheme.background, RoundedCornerShape(2.dp)))
                }
            }
        }
        HorizontalDivider(color = scheme.outline.copy(alpha = .25f))
    }
}
