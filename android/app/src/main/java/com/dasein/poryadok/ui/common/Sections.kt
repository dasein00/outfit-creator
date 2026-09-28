package com.dasein.poryadok.ui.common

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.ui.theme.LocalExtra

/**
 * Свои названия и иконки разделов. Ключ — маршрут раздела, поэтому одно и то же имя
 * видно и в нижней панели, и в «Ещё», и на «Здоровье».
 */
object Sections {
    /** Меняется при каждом сохранении, чтобы панель и плитки перерисовались. */
    val version = mutableIntStateOf(0)

    fun name(ctx: Context, key: String, def: String): String = UiState.str(ctx, "sec_name_$key", "").ifBlank { def }
    fun icon(ctx: Context, key: String, def: String): String = UiState.str(ctx, "sec_icon_$key", "").ifBlank { def }

    fun save(ctx: Context, key: String, name: String, icon: String) {
        UiState.setStr(ctx, "sec_name_$key", name.trim())
        UiState.setStr(ctx, "sec_icon_$key", icon)
        version.intValue++
    }

    fun reset(ctx: Context, key: String) {
        UiState.setStr(ctx, "sec_name_$key", "")
        UiState.setStr(ctx, "sec_icon_$key", "")
        version.intValue++
    }
}

data class SectionLook(val name: String, val icon: String)

/** Текущие название и иконка раздела с учётом правок пользователя. */
@Composable
fun rememberSection(key: String, defName: String, defIcon: String): SectionLook {
    val ctx = LocalContext.current
    val v = Sections.version.intValue
    return remember(key, defName, defIcon, v) { SectionLook(Sections.name(ctx, key, defName), Sections.icon(ctx, key, defIcon)) }
}

/** Диалог по долгому нажатию на раздел: новое название и иконка из базы (или своя картинка). */
@Composable
fun SectionEditDialog(key: String, defName: String, defIcon: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cur = rememberSection(key, defName, defIcon)
    var name by remember { mutableStateOf(cur.name) }
    var icon by remember { mutableStateOf(cur.icon) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить раздел") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).clickable { pick = true }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Glyph(icon, 36.dp)
                        Text("Иконка", fontSize = 11.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 4.dp))
                    }
                    HGap(12.dp)
                    Column(Modifier.weight(1f)) { TextInput(name, { name = it.take(24) }, "Название") }
                }
                OutlinedButton(onClick = { pick = true }, modifier = Modifier.padding(top = 10.dp)) { Text("Выбрать иконку из базы") }
                Text(
                    "Было: «$defName». Изменения видны в нижней панели и на плитках разделов.",
                    fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { Sections.save(ctx, key, name.ifBlank { defName }, icon); onDismiss() }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Sections.reset(ctx, key); onDismiss() }) { Text("Как было") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (pick) GlyphPickerDialog(icon, { pick = false }) { icon = it }
}
