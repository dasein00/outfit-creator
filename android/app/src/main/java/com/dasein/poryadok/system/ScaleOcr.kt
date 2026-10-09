package com.dasein.poryadok.system

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BodyMetric
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.ScaleScreenParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Распознавание текста на скриншоте приложения весов (ML Kit, на телефоне). */
object ScaleOcr {
    /**
     * Распознанное взвешивание, ждущее проверки: окно «Взвешивание» в разделе «Вес» показывается, пока оно не null.
     * Живёт вне экрана — раньше результат терялся, если экран пересоздавался во время распознавания
     * (так было при «Поделиться» из Fitdays: цифры распознавались, а окно не появлялось).
     */
    val draft = mutableStateOf<BodyMetric?>(null)
    val busy = mutableStateOf(false)
    private val handled = HashMap<String, Long>()

    /** Распознать скриншот и открыть окно проверки. Один и тот же присланный файл в течение минуты — один раз. */
    fun process(ctx: Context, uri: Uri) {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        synchronized(handled) {
            if ((handled[uri.toString()] ?: 0L) > now - 60_000) return
            handled[uri.toString()] = now
        }
        busy.value = true
        Graph.scope.launch {
            val r = runCatching { ScaleScreenParse.parse(lines(app, uri)) }.getOrNull()
            val m = r?.takeIf { it.weight != null || it.found > 0 }?.let { runCatching { toMetric(it) }.getOrNull() }
            withContext(Dispatchers.Main) {
                busy.value = false
                if (r == null || m == null) {
                    Toast.makeText(app, "Не удалось распознать показатели. Нужен скриншот экрана с результатами взвешивания.", Toast.LENGTH_LONG).show()
                    return@withContext
                }
                val extra = listOfNotNull(r.idealWeight?.let { "идеальный вес ${it.toString().replace('.', ',')} кг" })
                Toast.makeText(app, "Распознано показателей: ${r.found + listOfNotNull(r.heartRate, r.cardiacIndex).size}. Проверьте и сохраните." + extra.joinToString(prefix = " ", separator = ", "), Toast.LENGTH_LONG).show()
                draft.value = m
            }
        }
    }

    /**
     * Взвешивание из распознанного. Если это же взвешивание уже пришло через Health Connect (то же время ±10 минут
     * и тот же вес), дополняем его, а не создаём второе.
     */
    private suspend fun toMetric(r: ScaleScreenParse.Result): BodyMetric {
        val at = r.at ?: System.currentTimeMillis()
        val all = Graph.extra.bodyMetricsNow()
        val same = r.weight?.let { w -> all.filter { kotlin.math.abs(it.at - at) <= 10 * 60_000 && kotlin.math.abs(it.weight - w) < 0.3 }.minByOrNull { kotlin.math.abs(it.at - at) } }
        val base = same ?: BodyMetric(
            at = at, day = Dates.dayOf(at),
            weight = r.weight ?: all.lastOrNull()?.weight ?: Graph.dao.profileNow()?.startWeight ?: 70.0,
            source = "Fitdays",
        )
        return base.copy(
            weight = r.weight ?: base.weight,
            fatPct = r.fatPct ?: base.fatPct, musclePct = r.musclePct ?: base.musclePct, muscleKg = r.muscleKg ?: base.muscleKg,
            waterPct = r.waterPct ?: base.waterPct, proteinPct = r.proteinPct ?: base.proteinPct, boneKg = r.boneKg ?: base.boneKg,
            visceral = r.visceral ?: base.visceral, bmr = r.bmr ?: base.bmr, metabolicAge = r.metabolicAge ?: base.metabolicAge,
            subcutaneousPct = r.subcutaneousPct ?: base.subcutaneousPct, leanKg = r.leanKg ?: base.leanKg,
            heartRate = r.heartRate ?: base.heartRate, cardiacIndex = r.cardiacIndex ?: base.cardiacIndex,
        )
    }

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
