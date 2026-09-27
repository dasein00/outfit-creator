package com.dasein.poryadok.ui.common

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.ui.theme.LocalExtra

/** Мелкие настройки интерфейса на этом телефоне: свёрнутые подсказки, скрытый вес, набор метрик на главной. */
object UiState {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    fun bool(ctx: Context, key: String, def: Boolean): Boolean = sp(ctx).getBoolean(key, def)
    fun setBool(ctx: Context, key: String, v: Boolean) = sp(ctx).edit().putBoolean(key, v).apply()
    fun str(ctx: Context, key: String, def: String): String = sp(ctx).getString(key, def) ?: def
    fun setStr(ctx: Context, key: String, v: String) = sp(ctx).edit().putString(key, v).apply()

    /** Подсказки, которые ещё не трогали, открыты или закрыты по этому значению. */
    fun infoDefault(ctx: Context): Boolean = bool(ctx, "info_default", true)

    /** Развернуть или свернуть все подсказки во всех разделах. */
    fun setAllInfo(ctx: Context, open: Boolean) {
        val e = sp(ctx).edit()
        sp(ctx).all.keys.filter { it.startsWith("info_") }.forEach { e.remove(it) }
        e.putBoolean("info_default", open).apply()
        version.value++
    }

    /** Меняется при «свернуть/развернуть всё», чтобы открытые экраны перечитали состояние. */
    val version = mutableStateOf(0)
}

/** Флаг, который переживает перезапуск приложения. */
@Composable
fun rememberUiFlag(key: String, default: Boolean): MutableState<Boolean> {
    val ctx = LocalContext.current
    val v = UiState.version.value
    val state = remember(key, v) { mutableStateOf(UiState.bool(ctx, key, default)) }
    return remember(state) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = state.value
                set(x) { state.value = x; UiState.setBool(ctx, key, x) }
            override fun component1() = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

/** Сворачиваемый блок с пояснениями или инструкцией. Состояние запоминается для каждого [key]. */
@Composable
fun InfoBox(
    key: String,
    title: String,
    modifier: Modifier = Modifier,
    glyph: String? = "ui:bulb",
    content: @Composable ColumnScope.() -> Unit,
) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var open by rememberUiFlag("info_$key", UiState.infoDefault(ctx))
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = .07f)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) { Glyph(glyph, 18.dp, badge = false); HGap(8.dp) }
            Text(title, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "Свернуть" else "Развернуть", tint = extra.dim)
        }
        AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), content = content)
        }
    }
}

/** Короткая сворачиваемая подсказка из одного абзаца. */
@Composable
fun Hint(key: String, text: String, modifier: Modifier = Modifier, title: String = "Подсказка") {
    val extra = LocalExtra.current
    InfoBox(key, title, modifier) { Text(text, fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp) }
}
