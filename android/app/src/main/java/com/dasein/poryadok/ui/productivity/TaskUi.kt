@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.productivity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.Repo
import com.dasein.poryadok.data.Project
import com.dasein.poryadok.data.TaskItem
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.QuickAdd
import com.dasein.poryadok.logic.Repeat
import com.dasein.poryadok.ui.common.CheckDot
import com.dasein.poryadok.ui.common.Dot
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.priorityColor
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import kotlinx.coroutines.flow.first

fun TaskItem.whenText(today: Long = Dates.today()): String? {
    val d = dueDay ?: return null
    val base = Dates.label(d, today)
    return if (dueMin != null) "$base, ${Dates.time(dueMin)}" else base
}

fun TaskItem.overdue(today: Long = Dates.today()): Boolean {
    val d = dueDay ?: return false
    if (done) return false
    if (d < today) return true
    return d == today && dueMin != null && dueMin < Dates.nowMinutes()
}

@Composable
fun TaskRow(
    t: TaskItem,
    project: Project?,
    subDone: Int,
    subTotal: Int,
    onOpen: () -> Unit,
    showProject: Boolean = true,
) {
    val extra = LocalExtra.current
    val today = Dates.today()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckDot(t.done, priorityColor(t.priority).let { if (t.priority == 0) extra.dim else it }, onClick = { io { Repo.toggleTask(t) } })
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                color = if (t.done) extra.dim else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
            )
            val meta = buildList {
                t.whenText(today)?.let { add(it) }
                if (subTotal > 0) add("☑ $subDone/$subTotal")
                if (showProject && project != null) add("${project.emoji} ${project.name}")
                if (t.tags.isNotBlank()) add(t.tags.split(',').filter { it.isNotBlank() }.joinToString(" ") { "@$it" })
            }
            if (meta.isNotEmpty() || t.repeat != "none" || t.remind) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    meta.joinToString(" · "), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (t.overdue(today)) extra.danger else extra.dim, modifier = Modifier.weight(1f, fill = false),
                )
                if (t.repeat != "none") Icon(Icons.Default.Repeat, null, Modifier.padding(start = 4.dp).size(13.dp), tint = extra.dim)
                if (t.remind) Icon(Icons.Default.Notifications, null, Modifier.padding(start = 4.dp).size(13.dp), tint = extra.dim)
            }
        }
        if (t.priority > 0 && !t.done) Icon(Icons.Default.Flag, null, tint = priorityColor(t.priority), modifier = Modifier.size(18.dp))
    }
}

/** Быстрое добавление с разбором естественного языка: «Купить молоко завтра в 18 !2 #дом». */
@Composable
fun QuickAddSheet(onDismiss: () -> Unit, defaultDay: Long? = null, defaultProject: Long? = null, goalId: Long? = null) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val today = Dates.today()
    val parsed = remember(text) { QuickAdd.parse(text, today) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        if (text.isBlank()) return
        val p = parsed
        io {
            val dao = Graph.dao
            var projectId = defaultProject
            if (p.project != null) {
                val existing = dao.projects().first().firstOrNull { it.name.equals(p.project, true) }
                projectId = existing?.id ?: dao.upsertProject(Project(name = p.project, color = (0..11).random()))
            }
            dao.upsertTask(
                TaskItem(
                    title = p.title, dueDay = p.dueDay ?: defaultDay, dueMin = p.dueMin, priority = p.priority,
                    projectId = projectId, goalId = goalId, repeat = p.repeat.code, tags = p.tags.joinToString(","),
                    remind = p.dueMin != null, createdAt = System.currentTimeMillis(),
                )
            )
        }
        text = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp).navigationBarsPadding().imePadding()) {
            Text("Новая задача", style = MaterialTheme.typography.titleLarge)
            Text(
                "Пишите как говорите: «завтра в 18», «в пятницу», «каждый день», «!2» — приоритет, «#дом» — список, «@тег»",
                fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("Что нужно сделать?") },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    maxLines = 3,
                )
                IconButton(onClick = { submit() }) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Добавить", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (text.isNotBlank()) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                val day = parsed.dueDay ?: defaultDay
                if (day != null) AssistChip(onClick = {}, label = { Text("📅 " + Dates.label(day, today)) })
                parsed.dueMin?.let { AssistChip(onClick = {}, label = { Text("⏰ " + Dates.time(it)) }) }
                if (parsed.priority > 0) AssistChip(onClick = {}, label = { Text("⚑ " + listOf("", "низкий", "средний", "высокий")[parsed.priority]) })
                parsed.project?.let { AssistChip(onClick = {}, label = { Text("# $it") }) }
                parsed.tags.forEach { AssistChip(onClick = {}, label = { Text("@$it") }) }
                if (parsed.repeat != Repeat.NONE) AssistChip(onClick = {}, label = { Text("🔁 " + parsed.repeat.label) })
            }
            Text(
                "Enter — добавить и продолжить",
                fontSize = 11.sp, color = LocalExtra.current.dim, fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
fun ProjectBadge(p: Project?) {
    if (p == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(Palette.item(p.color), 8.dp)
        Text(" ${p.name}", fontSize = 12.sp, color = LocalExtra.current.dim)
    }
}
