package com.dasein.poryadok.ui.media

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.dasein.poryadok.ui.common.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.data.MediaStatus
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.MediaCatalog
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import java.time.LocalDate

/** Журнал чтения: сколько страниц прочитано по дням, и цель на год (как в Goodreads). */
object ReadingLog {
    val version = mutableIntStateOf(0)
    private fun sp(ctx: Context = Graph.app) = ctx.getSharedPreferences("reading", Context.MODE_PRIVATE)

    fun add(pages: Int, day: Long = Dates.today()) {
        if (pages == 0) return
        val p = sp()
        p.edit().putInt("d$day", (p.getInt("d$day", 0) + pages).coerceAtLeast(0)).apply()
        version.intValue++
    }

    fun pages(day: Long): Int = sp().getInt("d$day", 0)
    fun pagesBetween(from: Long, to: Long): Int = (from..to).sumOf { pages(it) }

    fun goal(year: Int): Int = sp().getInt("goal_$year", 0)
    fun setGoal(year: Int, n: Int) { sp().edit().putInt("goal_$year", n).apply(); version.intValue++ }

    /** Сколько дней подряд (до сегодня или вчера) вы читали. */
    fun streak(today: Long = Dates.today()): Int {
        var d = if (pages(today) > 0) today else today - 1
        var n = 0
        while (pages(d) > 0) { n++; d-- }
        return n
    }
}

/**
 * Карточка «Чтение»: цель на год (книг), опережение или отставание от плана, страницы за день, неделю и месяц,
 * серия дней с чтением и книги «Читаю» с быстрым «+10 страниц» и прогнозом, когда дочитаете.
 */
@Composable
fun ReadingGoalCard(books: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    val extra = LocalExtra.current
    val accent = MaterialTheme.colorScheme.primary
    val ver = ReadingLog.version.intValue
    val today = Dates.today()
    val now = LocalDate.now()
    val year = now.year
    val goal = remember(ver, year) { ReadingLog.goal(year) }
    val done = books.count { it.status == MediaStatus.DONE && it.finishedDay?.let { d -> LocalDate.ofEpochDay(d).year == year } == true }
    val week = remember(ver) { ReadingLog.pagesBetween(today - 6, today) }
    val month = remember(ver) { ReadingLog.pagesBetween(today - 29, today) }
    val todayPages = remember(ver) { ReadingLog.pages(today) }
    val streak = remember(ver) { ReadingLog.streak(today) }
    val pace = month / 30.0
    var editGoal by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(extra.card).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Чтение $year", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { editGoal = true }) { Text(if (goal > 0) "Цель: $goal" else "Поставить цель") }
        }
        if (goal > 0) {
            Text("$done из $goal книг", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
            Bar((done / goal.toFloat()).coerceIn(0f, 1f), accent, height = 8.dp)
            val expected = goal * now.dayOfYear / now.lengthOfYear().toDouble()
            val diff = done - expected
            Text(
                when {
                    done >= goal -> "Цель выполнена! Можно поднять планку."
                    diff >= 0.5 -> "Опережаете план на ${diff.toInt().coerceAtLeast(1)} кн."
                    diff <= -0.5 -> "Отстаёте от плана на ${(-diff).toInt().coerceAtLeast(1)} кн. — до конца года ${goal - done} кн., это ~${((now.lengthOfYear() - now.dayOfYear) / (goal - done).coerceAtLeast(1))} дн. на книгу"
                    else -> "Идёте точно по плану"
                },
                fontSize = 12.sp, color = if (diff <= -0.5 && done < goal) extra.warn else extra.dim, modifier = Modifier.padding(top = 4.dp),
            )
        } else Text("Прочитано в этом году: $done. Поставьте цель — покажу, успеваете ли.", fontSize = 13.sp, color = extra.dim)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("$todayPages", "стр. сегодня")
            Stat("$week", "за неделю")
            Stat("$month", "за 30 дней")
            Stat("$streak", "дней подряд")
        }
        val reading = books.filter { it.status == MediaStatus.IN_PROGRESS }.sortedByDescending { it.startedDay ?: 0L }
        reading.take(4).forEach { m ->
            val total = MediaCatalog.total(m)
            Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(m) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.title, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(
                            (if (total > 0) "стр. ${m.progress} из $total" else "стр. ${m.progress}") +
                                if (total > 0 && pace >= 1) " · дочитаете за ~${((total - m.progress) / pace).toInt().coerceAtLeast(1)} дн." else "",
                            fontSize = 12.sp, color = extra.dim,
                        )
                    }
                    listOf(10, 25).forEach { n ->
                        Text(
                            "+$n", fontWeight = FontWeight.Bold, color = accent, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { io { Graph.extra.upsertMedia(stepProgress(m, n)) } }.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                MediaCatalog.progressShare(m)?.let { Bar(it, accent, height = 4.dp) }
            }
        }
        if (reading.isEmpty()) Text("Отметьте книгу как «Читаю» — здесь появится быстрый «+10 страниц».", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
    }
    if (editGoal) {
        var v by remember { mutableStateOf(if (goal > 0) "$goal" else "12") }
        AlertDialog(
            onDismissRequest = { editGoal = false },
            title = { Text("Цель на $year год") },
            text = { NumberField(v, { v = it }, "Сколько книг прочитать", decimal = false) },
            confirmButton = { TextButton(onClick = { ReadingLog.setGoal(year, v.toIntOrNull()?.coerceIn(0, 1000) ?: 0); editGoal = false }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { editGoal = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(label, fontSize = 11.sp, color = LocalExtra.current.dim)
    }
}
