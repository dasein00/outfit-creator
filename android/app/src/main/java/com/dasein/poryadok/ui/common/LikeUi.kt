package com.dasein.poryadok.ui.common

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.system.Likes
import com.dasein.poryadok.ui.theme.LocalExtra

/** Зелёная подсветка понравившегося. */
val LikeGreen = Color(0xFF4CAF50)

@Composable
fun rememberLikes(): List<Likes.Like> {
    val ctx = LocalContext.current
    val l by Likes.flow(ctx).collectAsState()
    return l
}

/** Кнопка «+»: отметить понравившееся (зелёная галочка) или снять отметку. */
@Composable
fun LikeButton(like: Likes.Like, liked: Boolean, size: Int = 34) {
    val ctx = LocalContext.current
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(if (liked) LikeGreen else Color.Transparent)
            .border(1.5.dp, if (liked) LikeGreen else LocalExtra.current.dim, CircleShape)
            .clickable { Likes.toggle(ctx, like) },
        contentAlignment = Alignment.Center,
    ) { Text(if (liked) "✓" else "+", fontSize = (size / 1.9).sp, fontWeight = FontWeight.Bold, color = if (liked) Color.White else MaterialTheme.colorScheme.onSurface) }
}

/** Цвет карточки: зелёная подсветка, если понравилось. */
@Composable
fun likedColor(liked: Boolean): Color = if (liked) LikeGreen.copy(alpha = .16f) else LocalExtra.current.card

/**
 * Номер показанной записи на плашке, который сохраняется: открыли главное снова — та же новость,
 * на следующий день — с начала.
 */
@Composable
fun rememberDayPos(name: String, day: Long): DayPos {
    val ctx = LocalContext.current
    return remember(name, day) { DayPos(ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE), name, day) }
}

class DayPos(private val sp: android.content.SharedPreferences, private val name: String, private val day: Long) {
    private val state = mutableIntStateOf(
        sp.getString("pos_$name", "").orEmpty().let { raw -> if (raw.substringBefore('|') == day.toString()) raw.substringAfter('|').toIntOrNull() ?: 0 else 0 },
    )
    var value: Int
        get() = state.intValue
        set(v) { state.intValue = v; sp.edit().putString("pos_$name", "$day|$v").apply() }
}

/** Стрелки «‹ предыдущая» и «следующая ›» с номером посередине. */
@Composable
fun RowScope.PrevNext(pos: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val extra = LocalExtra.current
    Arrow("‹", pos > 0, onPrev)
    Text("${pos + 1} / $total", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(horizontal = 10.dp))
    Arrow("›", pos < total - 1, onNext)
}

@Composable
private fun Arrow(t: String, enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(if (enabled) c.copy(alpha = .15f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (enabled) c else LocalExtra.current.dim) }
}
