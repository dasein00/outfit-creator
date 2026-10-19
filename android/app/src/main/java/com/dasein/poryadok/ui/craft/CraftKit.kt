@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.craft

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.logic.PaintByNumbers
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.OnBackground

/*
 * Общие элементы «Рукоделия»: одинаковые карточки выбора, строки-переходы и карточки настроек —
 * чтобы все разделы (мозаика, вышивка, бисер, картины по номерам) выглядели и работали одинаково.
 */

/** Карточка выбора (техника, вид материала): значок, название и короткая подпись. Выбранная — с рамкой акцента. */
@Composable
internal fun ChoiceCard(icon: String, title: String, sub: String?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    val bg = if (selected) accent.copy(alpha = .14f) else extra.card
    Column(
        modifier.clip(shape).background(bg).border(if (selected) 2.dp else 1.dp, if (selected) accent else extra.line, shape)
            .clickable(onClick = onClick).heightIn(min = 76.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        OnBackground(bg) {
            Column {
                Text(icon, fontSize = 22.sp)
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                if (sub != null) Text(sub, fontSize = 11.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Строка-переход в карточке: значок, название, подпись и стрелка. */
@Composable
internal fun NavRow(icon: String, title: String, sub: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Tile(modifier, onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (sub != null) Text(sub, fontSize = 12.sp, color = extra.dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", fontSize = 24.sp, color = extra.dim, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Карточка группы настроек с заголовком. */
@Composable
internal fun SettingsCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Tile(modifier.padding(top = 10.dp), padding = 14.dp) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

/** Подпись поля в карточке настроек. */
@Composable
internal fun FieldLabel(text: String, value: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(btnLabel(text), fontSize = 13.sp, color = LocalExtra.current.dim, modifier = Modifier.weight(1f))
        if (value != null) Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun variantKey(k: PaintByNumbers.Kind) = "pbn_variant_${k.name}"

/**
 * Чем раскрашивать — в два шага: сначала вид (карандаши, маркеры, краски), потом какие именно
 * (цветные или акварельные карандаши; акриловые или спиртовые маркеры; акварельные или из наборов картин по номерам).
 * Для каждого вида запоминается последний выбор.
 */
@Composable
internal fun MaterialPicker(selected: Int, counts: Map<PaintByNumbers.Medium, Int>? = null, onSelect: (Int) -> Unit) {
    val ctx = LocalContext.current
    val sp = androidx.compose.runtime.remember { ctx.getSharedPreferences("ui_state", android.content.Context.MODE_PRIVATE) }
    val cur = PaintByNumbers.Medium.of(selected)
    fun pick(m: PaintByNumbers.Medium) {
        sp.edit().putInt(variantKey(m.kind), m.ordinal).apply()
        onSelect(m.ordinal)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaintByNumbers.Kind.entries.forEach { k ->
            ChoiceCard(k.icon, k.title, null, k == cur.kind, Modifier.weight(1f)) {
                if (k != cur.kind) pick(PaintByNumbers.Medium.of(sp.getInt(variantKey(k), k.media.first().ordinal)).takeIf { it.kind == k } ?: k.media.first())
            }
        }
    }
    Text(
        btnLabel("Какие ${cur.kind.title.lowercase()}:"), fontSize = 13.sp, color = LocalExtra.current.dim,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cur.kind.media.forEach { m ->
            val n = counts?.get(m)
            Pill(if (n != null) "${m.variant} · $n" else m.variant, m == cur) { if (m != cur) pick(m) }
        }
    }
}

/** Цветной квадратик образца. */
internal fun swatchColor(rgb: Int) = Color(rgb or (0xFF shl 24))
