package com.dasein.poryadok.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dasein.poryadok.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Последние значения подписок: место вызова (класс лямбды) + ключ → значение. */
private val lastValues = java.util.concurrent.ConcurrentHashMap<Any, Any>()
private object NullValue

/**
 * Подписка на поток из базы, созданный один раз для данного ключа.
 * Начальное значение — последнее, что этот же экран уже получал: плашки на главном при прокрутке назад
 * сразу нужной высоты, а не пустые на долю секунды (из-за этого список «прыгал»).
 */
@Composable
fun <T> observe(initial: T, key: Any? = Unit, create: () -> Flow<T>): State<T> {
    val cacheKey = remember(key) { create::class.java to key }
    val flow = remember(key) { create() }
    @Suppress("UNCHECKED_CAST")
    val start = remember(cacheKey) { lastValues[cacheKey]?.let { if (it === NullValue) null else it as T } ?: initial }
    val state = flow.collectAsStateWithLifecycle(start)
    val v = state.value
    SideEffect { lastValues[cacheKey] = v ?: NullValue }
    return state
}

/** Запись в базу в фоне приложения: не отменяется при уходе с экрана. */
fun io(block: suspend CoroutineScope.() -> Unit): Job = Graph.scope.launch(block = block)
