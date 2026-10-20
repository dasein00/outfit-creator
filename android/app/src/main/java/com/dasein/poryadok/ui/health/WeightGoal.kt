package com.dasein.poryadok.ui.health

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.dasein.poryadok.data.BodyMetric
import com.dasein.poryadok.data.BodyProfile
import com.dasein.poryadok.logic.CalorieGoal
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.plain
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Цель по весу: исходный вес и цель. Пока своя цель не задана, берётся из профиля питания:
 * вес на старте и «изменить за 3 месяца». Хранится в настройках «weight_goal» (попадает в резервную копию).
 */
object WeightGoal {
    private const val PREFS = "weight_goal"
    /** Меняется при сохранении — карточки на главной и в разделе весов перечитывают цель. */
    val version = mutableIntStateOf(0)

    data class Goal(val start: Double, val target: Double, val current: Double?) {
        val losing get() = target <= start
        /** Доля пути от исходного веса к цели, 0…1. */
        val fraction: Float get() {
            val c = current ?: return 0f
            if (abs(target - start) < 0.05) return if (abs(c - target) < 0.05) 1f else 0f
            return ((c - start) / (target - start)).toFloat().coerceIn(0f, 1f)
        }
        /** Сколько уже сброшено (набрано) и сколько осталось, кг. */
        val done get() = current?.let { if (losing) start - it else it - start } ?: 0.0
        val left get() = current?.let { (if (losing) it - target else target - it).coerceAtLeast(0.0) } ?: abs(start - target)
        val reached get() = current != null && left < 0.05
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(ctx: Context, profile: BodyProfile, current: Double?): Goal {
        val p = sp(ctx)
        val start = p.getString("start", null)?.toDoubleOrNull() ?: profile.startWeight
        val goal = runCatching { CalorieGoal.valueOf(profile.goal) }.getOrDefault(CalorieGoal.DEFICIT)
        val fallback = if (goal == CalorieGoal.SURPLUS) start + abs(profile.changeKg) else start - abs(profile.changeKg)
        val target = p.getString("target", null)?.toDoubleOrNull() ?: fallback
        return Goal(start, target, current)
    }

    fun save(ctx: Context, start: Double, target: Double) {
        sp(ctx).edit().putString("start", start.toString()).putString("target", target.toString()).apply()
        version.intValue++
    }
}

@Composable
private fun rememberGoal(profile: BodyProfile, readings: List<BodyMetric>): WeightGoal.Goal {
    val ctx = LocalContext.current
    val v = WeightGoal.version.intValue
    val current = readings.lastOrNull()?.weight
    return remember(profile, current, v) { WeightGoal.get(ctx, profile, current) }
}

/** Полоса прогресса к цели: процент в «облачке» над ползунком, исходный вес слева, цель справа. */
@Composable
fun WeightGoalBar(goal: WeightGoal.Goal, modifier: Modifier = Modifier) {
    val extra = LocalExtra.current
    val accent = MaterialTheme.colorScheme.primary
    val f = goal.fraction
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(58.dp)) {
            val w = maxWidth
            val bubbleW = 64.dp
            val thumb = 16.dp
            val x = w * f
            // Облачко с процентом — над ползунком, но не вылезает за края карточки.
            val bx = (x - bubbleW / 2).coerceIn(0.dp, maxOf(0.dp, w - bubbleW))
            Column(Modifier.offset(x = bx).width(bubbleW), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(accent).padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(pct(f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, softWrap = false)
                }
                // Хвостик облачка указывает на ползунок.
                Box(
                    Modifier.offset(x = (x - bx - bubbleW / 2).coerceIn(-bubbleW / 2 + 8.dp, bubbleW / 2 - 8.dp), y = (-4).dp)
                        .size(8.dp).clip(RoundedCornerShape(2.dp)).background(accent),
                )
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(thumb), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(extra.line))
                Box(Modifier.width(x.coerceAtLeast(8.dp)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                Box(
                    Modifier.offset(x = (x - thumb / 2).coerceIn(0.dp, maxOf(0.dp, w - thumb))).size(thumb).clip(CircleShape).background(accent)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(btnLabel("Исходный: ") + fmt(goal.start) + " кг", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(btnLabel("Цель: ") + fmt(goal.target) + " кг", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun pct(f: Float) = "%.1f%%".format(f * 100).replace('.', ',')

/** Подпись под полосой: сколько уже сброшено и сколько осталось. */
@Composable
private fun GoalSummary(g: WeightGoal.Goal) {
    val extra = LocalExtra.current
    val c = g.current
    val text = when {
        c == null -> "Добавьте взвешивание — здесь появится прогресс"
        g.reached -> "Цель достигнута — сейчас ${fmt(c)} кг. Можно поставить новую."
        g.done < 0.05 -> "Сейчас ${fmt(c)} кг · до цели ${fmt(g.left)} кг"
        else -> (if (g.losing) "Сброшено " else "Набрано ") + "${fmt(g.done)} кг · осталось ${fmt(g.left)} кг"
    }
    Text(text, fontSize = 12.sp, color = if (g.reached) extra.ok else extra.dim, modifier = Modifier.padding(top = 4.dp))
}

/** Компактный блок цели для карточки веса на главной — под графиком. */
@Composable
fun WeightGoalInline(profile: BodyProfile, readings: List<BodyMetric>, modifier: Modifier = Modifier) {
    val g = rememberGoal(profile, readings)
    Column(modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("d03/11", 18.dp)
            HGap(6.dp)
            Text(btnLabel(if (g.losing) "Цель похудения" else "Цель по весу"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Gap(4.dp)
        WeightGoalBar(g)
        GoalSummary(g)
    }
}

/** Карточка «Цель похудения» в разделе весов: прогресс и изменение исходного веса и цели. */
@Composable
fun WeightGoalCard(profile: BodyProfile, readings: List<BodyMetric>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val g = rememberGoal(profile, readings)
    var editing by remember { mutableStateOf(false) }
    Tile(modifier, onClick = { editing = true }, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("d03/11", 26.dp)
            HGap(10.dp)
            Text(btnLabel(if (g.losing) "Цель похудения" else "Цель по весу"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = true }) { Text(btnLabel("Изменить"), fontSize = 12.sp) }
        }
        Gap(4.dp)
        WeightGoalBar(g)
        GoalSummary(g)
    }
    if (editing) {
        var start by remember { mutableStateOf(g.start.plain()) }
        var target by remember { mutableStateOf(g.target.plain()) }
        val s = start.num()
        val t = target.num()
        val ok = s != null && t != null && s in 20.0..400.0 && t in 20.0..400.0
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(btnLabel("Цель по весу")) },
            text = {
                Column {
                    NumberField(start, { start = it }, "Исходный вес", suffix = "кг")
                    g.current?.let { c ->
                        TextButton(onClick = { start = ((c * 10).roundToInt() / 10.0).plain() }) { Text(btnLabel("Взять текущий: ") + fmt(c) + " кг", fontSize = 12.sp) }
                    }
                    Gap(4.dp)
                    NumberField(target, { target = it }, "Цель", suffix = "кг")
                    Text(
                        "Прогресс считается от исходного веса к цели по последнему взвешиванию.",
                        fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = { Button(onClick = { if (ok) { WeightGoal.save(ctx, s!!, t!!); editing = false } }, enabled = ok) { Text(btnLabel("Сохранить")) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(btnLabel("Отмена")) } },
        )
    }
}
