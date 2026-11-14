package com.dasein.poryadok.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/** Блоки ленты главной: те же `item`/`items`, что у ленивого списка. */
class HomeListScope {
    internal val entries = ArrayList<Pair<Any?, @Composable () -> Unit>>()
    fun item(key: Any? = null, content: @Composable () -> Unit) { entries += key to content }
    fun <T> items(list: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) {
        list.forEach { v -> entries += key(v) to { content(v) } }
    }
}

/** Сколько блоков строится в первом кадре (то, что видно на экране) и сколько добавляется в каждом следующем. */
private const val FIRST_BLOCKS = 7
private const val BLOCKS_PER_FRAME = 2

/**
 * Лента главной. Блоков немного, но они тяжёлые (графики, запросы к базе, расчёт праздников), поэтому лента
 * не ленивая: каждый блок строится один раз и при прокрутке только сдвигается — без пересоздания карточек,
 * повторных запросов и скачков высоты. При запуске сначала строится видимая часть, остальное — в следующих кадрах.
 */
@Composable
fun HomeList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: HomeListScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val entries = HomeListScope().apply(content).entries
    // Вернулись на главную с сохранённой прокруткой — строим всё сразу, иначе прокрутка сбросится.
    val restored = remember { scroll.value }
    var shown by remember { mutableIntStateOf(if (restored > 0) Int.MAX_VALUE else FIRST_BLOCKS) }
    val total by rememberUpdatedState(entries.size)
    LaunchedEffect(Unit) {
        while (shown < total) { withFrameNanos { }; shown += BLOCKS_PER_FRAME }
        shown = Int.MAX_VALUE
    }
    // Данные из базы приходят чуть позже первого кадра, лента сначала короче — возвращаем прокрутку, когда она дорастёт.
    LaunchedEffect(Unit) {
        if (restored <= 0) return@LaunchedEffect
        kotlinx.coroutines.withTimeoutOrNull(1500) {
            // До первого измерения длина ленты неизвестна (Int.MAX_VALUE) — такие значения пропускаем.
            snapshotFlow { scroll.maxValue }.filter { it != Int.MAX_VALUE }.first { max ->
                if (scroll.isScrollInProgress) return@first true   // человек уже листает сам — не мешаем
                if (scroll.value < restored) scroll.scrollTo(minOf(restored, max))
                max >= restored
            }
        }
    }
    Column(modifier.verticalScroll(scroll).padding(contentPadding), verticalArrangement = verticalArrangement) {
        entries.forEachIndexed { i, (k, block) ->
            if (i < shown) key(k ?: i) { block() }
        }
    }
}
