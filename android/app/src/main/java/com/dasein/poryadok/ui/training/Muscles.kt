package com.dasein.poryadok.ui.training

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.dasein.poryadok.Graph
import com.dasein.poryadok.ui.common.ButtonsRow
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.GlyphField
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra

/**
 * Группы мышц и их иконки. Встроенные можно переименовать, сменить им иконку или убрать, свои — добавить
 * (с иконкой из набора «Мышцы», любой другой или своей картинкой). Хранится в ui_state — уходит в файл оформления.
 */
object Muscles {
    private const val KEY = "muscles_v1"

    data class Muscle(val name: String, val glyph: String)

    val DEFAULT = listOf(
        Muscle("Ноги", "muscle/07"), Muscle("Ягодицы", "muscle/08"), Muscle("Грудь", "muscle/00"), Muscle("Спина", "muscle/01"),
        Muscle("Плечи", "muscle/02"), Muscle("Руки", "muscle/03"), Muscle("Пресс", "muscle/06"), Muscle("Всё тело", "d08/11"),
        Muscle("Кардио", "d08/01"),
    )

    /** Иконки набора «Мышцы» по порядку файлов — подсказки для новой группы. */
    val SUGGEST = listOf(
        "Грудь", "Спина", "Плечи", "Бицепс", "Трицепс", "Предплечья", "Пресс", "Квадрицепсы", "Ягодицы", "Бицепс бедра",
        "Икры", "Трапеции", "Широчайшие", "Поясница", "Косые мышцы", "Приводящие", "Средняя дельта", "Руки целиком",
        "Ягодицы и бедро", "Задняя дельта", "Спина и плечо", "Разгибатели спины", "Нижняя грудь", "Икры сзади",
    ).mapIndexed { i, n -> Muscle(n, "muscle/%02d".format(i)) }

    private var cache: List<Muscle>? = null
    private val version = androidx.compose.runtime.mutableIntStateOf(0)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Muscle> = version.intValue.let { cache } ?: run {
        val raw = prefs(ctx).getString(KEY, null)
        val l = raw?.lines()?.mapNotNull { line ->
            val p = line.split('\t')
            p.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() }?.let { Muscle(it, p.getOrNull(1).orEmpty()) }
        } ?: DEFAULT
        cache = l
        l
    }

    /** Иконка группы: своя, из списка, или null (тогда показываем иконку упражнения). */
    fun glyphOf(ctx: Context, name: String): String? = all(ctx).firstOrNull { it.name == name }?.glyph?.takeIf { it.isNotBlank() }

    fun save(ctx: Context, list: List<Muscle>) {
        val clean = list.filter { it.name.isNotBlank() }.distinctBy { it.name.lowercase() }
        cache = clean
        version.intValue++
        prefs(ctx).edit().putString(KEY, clean.joinToString("\n") { "${it.name.replace('\t', ' ').replace('\n', ' ')}\t${it.glyph}" }).apply()
    }

    /** После загрузки файла оформления список перечитывается из настроек. */
    fun reload() { cache = null; version.intValue++ }
}

/** Ряд групп мышц с иконками и кнопкой «Мышцы…» — добавить свою группу или поменять иконку. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MusclePills(selected: String?, onSelect: (String) -> Unit, leading: @Composable () -> Unit = {}) {
    val ctx = LocalContext.current
    var edit by remember { mutableStateOf(false) }
    val list = Muscles.all(ctx)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        leading()
        // Группа из упражнения, которой уже нет в списке, всё равно видна.
        val names = list.map { it.name }
        list.forEach { m -> Pill(m.name, selected == m.name, glyph = m.glyph.ifBlank { null }) { onSelect(m.name) } }
        if (selected != null && selected.isNotBlank() && selected !in names) Pill(selected, true) { onSelect(selected) }
        Pill("+ Мышцы", false) { edit = true }
    }
    if (edit) MusclesDialog { edit = false }
}

/** Список групп мышц: нажмите на группу, чтобы сменить иконку или название; «+ Новая группа» — своя. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusclesDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var list by remember { mutableStateOf(Muscles.all(ctx)) }
    var editing by remember { mutableStateOf<Int?>(null) } // -1 — новая группа
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Группы мышц") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Нажмите на группу, чтобы сменить иконку или название. Иконку можно взять из набора «Мышцы», любого другого или свою картинку.", fontSize = 13.sp, color = extra.dim)
                list.forEachIndexed { i, m ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable { editing = i }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(m.glyph.ifBlank { "d13/00" }, 36.dp, badge = false)
                        HGap(10.dp)
                        Text(m.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("›", color = extra.dim, fontSize = 18.sp)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable { editing = -1 }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(btnLabel("+ Новая группа мышц"), fontWeight = FontWeight.SemiBold) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Готово")) } },
        dismissButton = {
            TextButton(onClick = { list = Muscles.DEFAULT; Muscles.save(ctx, list) }) { Text(btnLabel("Как было")) }
        },
    )
    editing?.let { i ->
        val cur = list.getOrNull(i)
        MuscleEditDialog(
            initial = cur,
            onDismiss = { editing = null },
            onDelete = if (cur != null) ({ list = list.filterIndexed { j, _ -> j != i }; Muscles.save(ctx, list); editing = null }) else null,
            onSave = { m ->
                val old = cur?.name
                list = if (cur == null) list + m else list.mapIndexed { j, x -> if (j == i) m else x }
                Muscles.save(ctx, list)
                // Переименовали — упражнения с прежним названием переходят на новое.
                if (old != null && old != m.name) io {
                    val t = Graph.training
                    t.exercisesNow().filter { it.muscle == old }.forEach { t.upsertExercise(it.copy(muscle = m.name)) }
                }
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleEditDialog(initial: Muscles.Muscle?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Muscles.Muscle) -> Unit) {
    val extra = LocalExtra.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var glyph by remember { mutableStateOf(initial?.glyph?.ifBlank { null } ?: "muscle/00") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая группа мышц" else "Группа мышц") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphField(glyph, { glyph = it })
                    HGap(12.dp)
                    TextInput(name, { name = it }, "Название", Modifier.weight(1f))
                }
                Text("Готовые иконки мышц", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muscles.SUGGEST.forEach { s ->
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                glyph = s.glyph
                                if (name.isBlank()) name = s.name
                            }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Glyph(s.glyph, 44.dp, badge = false, dimmed = glyph != s.glyph)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(Muscles.Muscle(name.trim(), glyph)) }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = {
            ButtonsRow {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(btnLabel("Убрать"), color = extra.danger) }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
            }
        },
    )
}
