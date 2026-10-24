package com.dasein.poryadok.ui.notes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.PagesRepo
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Связь задач и целей с заметками. У задачи ссылка хранится в её тексте «Заметка» как [page:N]
 * (так же её ставят задачи, созданные из пунктов заметки), у цели — в настройках ui_state «goal_page_<id>».
 */
object NoteLinks {
    private val RE = Regex("""\[page:(\d+)]""")
    fun pageOf(text: String): Long? = RE.find(text)?.groupValues?.get(1)?.toLongOrNull()
    fun tag(id: Long) = "[page:$id]"

    private fun sp() = Graph.app.getSharedPreferences("ui_state", android.content.Context.MODE_PRIVATE)
    fun goalPage(goalId: Long): Long? = sp().getLong("goal_page_$goalId", 0L).takeIf { it > 0 }
    fun setGoalPage(goalId: Long, pageId: Long) = sp().edit().putLong("goal_page_$goalId", pageId).apply()
}

/** Кнопка «Открыть заметку» (если связана и не в корзине) или «Создать заметку» — новая страница с этим названием. */
@Composable
fun NoteLinkButton(nav: NavHostController, linked: Long?, title: String, modifier: Modifier = Modifier, onCreated: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val pages by observe(emptyList()) { Graph.pages.pages() }
    val page = linked?.let { id -> pages.firstOrNull { it.id == id && !it.archived } }
    OutlinedButton(
        onClick = {
            if (page != null) nav.navigate(Routes.page(page.id))
            else scope.launch {
                val pid = withContext(Dispatchers.IO) { PagesRepo.create(null, title.trim()) }
                onCreated(pid)
                nav.navigate(Routes.page(pid))
            }
        },
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
    ) { Text(if (page != null) "📝 Открыть заметку «${page.title.ifBlank { "Без названия" }}»" else btnLabel("📝 Создать заметку")) }
}
