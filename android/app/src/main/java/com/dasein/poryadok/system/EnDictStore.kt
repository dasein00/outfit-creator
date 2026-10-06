package com.dasein.poryadok.system

import android.content.Context
import com.dasein.poryadok.logic.EnDict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Встроенный англо-русский словарь (assets/tutor/dict.tsv): читается один раз и держится в памяти. */
object EnDictStore {
    @Volatile private var cache: List<EnDict.Entry>? = null
    private val lock = Mutex()

    suspend fun all(ctx: Context): List<EnDict.Entry> = cache ?: lock.withLock {
        cache ?: withContext(Dispatchers.IO) {
            runCatching {
                ctx.assets.open("tutor/dict.tsv").bufferedReader().useLines { lines -> lines.mapNotNull(EnDict::parseLine).toList() }
            }.getOrDefault(emptyList())
        }.also { cache = it }
    }
}
