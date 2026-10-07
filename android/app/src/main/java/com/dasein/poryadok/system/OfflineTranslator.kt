package com.dasein.poryadok.system

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Перевод фраз и предложений на телефоне (Google ML Kit). Модели английского и русского (~30 МБ каждая)
 * скачиваются один раз, дальше переводчик работает без интернета.
 */
object OfflineTranslator {
    private var enRu: Translator? = null
    private var ruEn: Translator? = null

    private fun client(enToRu: Boolean): Translator {
        val cur = if (enToRu) enRu else ruEn
        if (cur != null) return cur
        val o = TranslatorOptions.Builder()
            .setSourceLanguage(if (enToRu) TranslateLanguage.ENGLISH else TranslateLanguage.RUSSIAN)
            .setTargetLanguage(if (enToRu) TranslateLanguage.RUSSIAN else TranslateLanguage.ENGLISH)
            .build()
        return Translation.getClient(o).also { if (enToRu) enRu = it else ruEn = it }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { if (c.isActive) c.resume(it) }
        addOnFailureListener { if (c.isActive) c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }

    /** Скачаны ли обе модели (английская и русская). */
    suspend fun ready(): Boolean = runCatching {
        val m = RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java).await()
        val langs = m.map { it.language }.toSet()
        TranslateLanguage.ENGLISH in langs && TranslateLanguage.RUSSIAN in langs
    }.getOrDefault(false)

    /** Скачать модели. [wifiOnly] — только по Wi-Fi. */
    suspend fun download(wifiOnly: Boolean): Result<Unit> = runCatching {
        val cond = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        client(true).downloadModelIfNeeded(cond).await()
        client(false).downloadModelIfNeeded(cond).await()
        Unit
    }

    suspend fun translate(text: String, enToRu: Boolean): Result<String> = runCatching { client(enToRu).translate(text).await() }
}
