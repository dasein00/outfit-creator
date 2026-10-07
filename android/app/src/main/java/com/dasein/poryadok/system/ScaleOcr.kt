package com.dasein.poryadok.system

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Распознавание текста на скриншоте приложения весов (ML Kit, на телефоне). */
object ScaleOcr {
    /** Скриншот, присланный через «Поделиться» из Fitdays, — откроется в разделе «Вес». */
    val pending = mutableStateOf<Uri?>(null)

    /** Строки текста сверху вниз, слева направо. */
    suspend fun lines(ctx: Context, uri: Uri): List<String> = suspendCancellableCoroutine { c ->
        val image = runCatching { InputImage.fromFilePath(ctx, uri) }.getOrElse { if (c.isActive) c.resume(emptyList()); return@suspendCancellableCoroutine }
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        rec.process(image)
            .addOnSuccessListener { t ->
                val ls = t.textBlocks.flatMap { it.lines }
                    .sortedWith(compareBy({ (it.boundingBox?.centerY() ?: 0) / 24 }, { it.boundingBox?.left ?: 0 }))
                // Строки на одной высоте (подпись слева, число справа) склеиваем в одну.
                val merged = mutableListOf<Pair<Int, String>>()
                ls.forEach { l ->
                    val y = l.boundingBox?.centerY() ?: 0
                    val last = merged.lastOrNull()
                    if (last != null && kotlin.math.abs(last.first - y) < 18) merged[merged.lastIndex] = last.first to (last.second + " " + l.text)
                    else merged += y to l.text
                }
                if (c.isActive) c.resume(merged.map { it.second })
                rec.close()
            }
            .addOnFailureListener { if (c.isActive) c.resume(emptyList()); rec.close() }
    }
}
