package com.dasein.poryadok.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dasein.poryadok.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Подписка на поток из базы, созданный один раз для данного ключа. */
@Composable
fun <T> observe(initial: T, key: Any? = Unit, create: () -> Flow<T>): State<T> {
    val flow = remember(key) { create() }
    return flow.collectAsStateWithLifecycle(initial)
}

/** Запись в базу в фоне приложения: не отменяется при уходе с экрана. */
fun io(block: suspend CoroutineScope.() -> Unit): Job = Graph.scope.launch(block = block)
