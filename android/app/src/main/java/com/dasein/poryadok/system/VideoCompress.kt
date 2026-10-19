package com.dasein.poryadok.system

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Сжатие видео для рецептов и тренировок — как можно компактнее (картинка вторична):
 * короткая сторона 360 точек, 24 кадра/с, видео H.265 около 250 кбит/с (если телефон его не кодирует — H.264 400 кбит/с),
 * звук AAC моно 32 кбит/с. Минута весит около 2 МБ. Не получилось с этими настройками — пробуем проще (H.264, звук как есть).
 */
object VideoCompress {
    const val SHORT_SIDE = 360
    private const val FPS = 24f
    private const val HEVC_BITRATE = 250_000
    private const val AVC_BITRATE = 400_000
    private const val AUDIO_BITRATE = 32_000
    /** Общий битрейт сжатого ролика с запасом: файлы заметно выше него стоит пережать. */
    private const val TARGET_TOTAL = 450_000

    /** Размеры кадра с учётом поворота и средний битрейт. */
    private data class Info(val w: Int, val h: Int, val bitrate: Int)

    private fun info(path: String): Info? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            Info(w, h, r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0)
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()

    /** Стоит ли сжимать: кадр крупнее 360p или битрейт заметно выше целевого (в том числе ролики, сжатые по-старому). */
    fun worth(path: String): Boolean {
        val i = info(path) ?: return false
        return minOf(i.w, i.h) > SHORT_SIDE + 40 || i.bitrate > TARGET_TOTAL * 16 / 10
    }

    /** Настройки одной попытки: кодек, битрейт, звук в моно и 24 кадра/с. */
    private data class Codec(val mime: String, val bitrate: Int, val compact: Boolean)

    @Volatile private var hevcCached: Boolean? = null

    /** Умеет ли телефон кодировать H.265 (почти все с 2016 года). */
    @OptIn(UnstableApi::class)
    private fun hevc(): Boolean = hevcCached ?: runCatching { androidx.media3.transformer.EncoderUtil.getSupportedEncoders(MimeTypes.VIDEO_H265).isNotEmpty() }
        .getOrDefault(false).also { hevcCached = it }

    /** Сначала самое компактное; если телефон не справился — H.264 без лишних эффектов. */
    private fun attempts(): List<Codec> = listOf(
        if (hevc()) Codec(MimeTypes.VIDEO_H265, HEVC_BITRATE, true) else Codec(MimeTypes.VIDEO_H264, AVC_BITRATE, true),
        Codec(MimeTypes.VIDEO_H264, AVC_BITRATE, false),
    )

    /** Звук: скорость без «мультяшного» голоса (Sonic) и моно. */
    @OptIn(UnstableApi::class)
    private fun audioFx(speed: Float, mono: Boolean): List<androidx.media3.common.audio.AudioProcessor> = buildList {
        if (speed != 1f) add(androidx.media3.common.audio.SonicAudioProcessor().apply { setSpeed(speed); setPitch(1f) })
        if (mono) add(androidx.media3.common.audio.ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(1, 1, floatArrayOf(1f)))
            putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(2, 1, floatArrayOf(.5f, .5f)))
        })
    }

    /** Кодирует [composition] в [out] попытками из [attempts]. true — получилось. */
    @OptIn(UnstableApi::class)
    private suspend fun export(ctx: Context, out: File, composition: (Codec) -> Composition): Boolean {
        for (codec in attempts()) {
            out.delete()
            val ok = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val transformer = Transformer.Builder(ctx)
                        .setVideoMimeType(codec.mime)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(
                            DefaultEncoderFactory.Builder(ctx.applicationContext)
                                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(codec.bitrate).build())
                                .apply { if (codec.compact) setRequestedAudioEncoderSettings(androidx.media3.transformer.AudioEncoderSettings.Builder().setBitrate(AUDIO_BITRATE).build()) }
                                .build(),
                        )
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(true)
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (cont.isActive) cont.resume(false)
                            }
                        })
                        .build()
                    cont.invokeOnCancellation { runCatching { transformer.cancel() } }
                    runCatching { transformer.start(composition(codec), out.absolutePath) }.onFailure { if (cont.isActive) cont.resume(false) }
                }
            }
            if (ok && out.length() > 0) return true
        }
        out.delete()
        return false
    }

    /**
     * Сжимает [input] в новый файл рядом с ним. Возвращает путь к сжатому файлу или null, если не получилось
     * (тогда остаётся исходный). Исходный файл не удаляется — это решает вызывающий.
     */
    @OptIn(UnstableApi::class)
    suspend fun compress(ctx: Context, input: File): File? = transform(ctx, input, null, 1f)

    /**
     * Монтаж: куски [clips] из файлов [sources] склеиваются по порядку, у каждого своя скорость.
     * [aspect] — кадр (ширина/высота) с обрезкой краёв, null — как у видео. [mute] — без звука. Итог сжимается. null — не получилось.
     */
    @OptIn(UnstableApi::class)
    suspend fun editClips(ctx: Context, sources: List<File>, clips: List<com.dasein.poryadok.logic.VideoCut.Clip>, aspect: Float?, mute: Boolean): File? {
        if (sources.isEmpty() || clips.isEmpty()) return null
        val first = withContext(Dispatchers.IO) { info(sources[0].absolutePath) } ?: return null
        val out = File(sources[0].parentFile, "v_${System.currentTimeMillis()}.mp4")
        val mixed = clips.any { it.src != 0 }
        // Размер кадра: по заданной пропорции или по первому видео; короткая сторона — не больше SHORT_SIDE.
        val a = aspect ?: (first.w.toFloat() / first.h.coerceAtLeast(1))
        val short = if (aspect != null) SHORT_SIDE else minOf(minOf(first.w, first.h), SHORT_SIDE)
        val (outW, outH) = if (a < 1f) short to (short / a).roundToInt() else (short * a).roundToInt() to short
        val w2 = (outW / 2) * 2
        val h2 = (outH / 2) * 2
        val needSize = aspect != null || mixed || minOf(first.w, first.h) > SHORT_SIDE
        val ok = export(ctx, out) { codec ->
            val items = clips.map { c ->
                val src = sources.getOrElse(c.src) { sources[0] }
                val media = MediaItem.Builder().setUri(Uri.fromFile(src)).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build(),
                ).build()
                val video = buildList<androidx.media3.common.Effect> {
                    if (c.speed != 1f) add(androidx.media3.effect.SpeedChangeEffect(c.speed))
                    // Кадр с обрезкой краёв (9:16, 1:1…) или общий размер для кусков из разных видео.
                    if (needSize) add(Presentation.createForWidthAndHeight(w2, h2, if (aspect != null) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT))
                    if (codec.compact) add(androidx.media3.effect.FrameDropEffect.createDefaultFrameDropEffect(FPS))
                }
                EditedMediaItem.Builder(media).setRemoveAudio(mute).setEffects(Effects(if (mute) emptyList() else audioFx(c.speed, codec.compact), video)).build()
            }
            Composition.Builder(listOf(androidx.media3.transformer.EditedMediaItemSequence(items)))
                // Куски из разных видео: если у какого-то нет звука, вместо него — тишина, а не ошибка.
                .apply { if (mixed && !mute) experimentalSetForceAudioTrack(true) }
                .build()
        }
        return if (ok) out else null
    }

    @OptIn(UnstableApi::class)
    private suspend fun transform(ctx: Context, input: File, segments: List<LongRange>?, speed: Float): File? {
        val edited = segments != null || speed != 1f
        val i = withContext(Dispatchers.IO) { info(input.absolutePath) } ?: return null
        val out = File(input.parentFile, "v_${System.currentTimeMillis()}.mp4")
        // Высота итогового кадра так, чтобы короткая сторона стала SHORT_SIDE (вертикальное видео — выше).
        val short = minOf(i.w, i.h).coerceAtLeast(1)
        val scale = (SHORT_SIDE.toFloat() / short).coerceAtMost(1f)
        val height = ((i.h * scale) / 2).roundToInt() * 2
        val ok = export(ctx, out) { codec ->
            // Каждый кусок — отдельный элемент последовательности со своей обрезкой; эффекты у всех одинаковые.
            fun item(clip: LongRange?): EditedMediaItem {
                val media = MediaItem.Builder().setUri(Uri.fromFile(input)).apply {
                    if (clip != null) setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.first).setEndPositionMs(clip.last).build(),
                    )
                }.build()
                val video = buildList<androidx.media3.common.Effect> {
                    if (speed != 1f) add(androidx.media3.effect.SpeedChangeEffect(speed))
                    if (scale < 1f) add(Presentation.createForHeight(height))
                    if (codec.compact) add(androidx.media3.effect.FrameDropEffect.createDefaultFrameDropEffect(FPS))
                }
                return EditedMediaItem.Builder(media).setEffects(Effects(audioFx(speed, codec.compact), video)).build()
            }
            Composition.Builder(listOf(androidx.media3.transformer.EditedMediaItemSequence((segments ?: listOf(null)).map { item(it) }))).build()
        }
        // Если сжатый файл не меньше исходного — оставляем исходный (кроме обрезки и скорости: там важен сам результат).
        return if (ok && (edited || out.length() < input.length())) out else { out.delete(); null }
    }

    /** Сжимает файл на месте: при успехе исходный удаляется. Возвращает путь к итоговому файлу. */
    suspend fun replace(ctx: Context, path: String): String {
        val f = File(path)
        if (!f.exists()) return path
        val c = compress(ctx, f) ?: return path
        withContext(Dispatchers.IO) { f.delete() }
        return c.absolutePath
    }

    /** Монтаж уже добавленного файла: при успехе исходный удаляется. Возвращает путь к итоговому (или прежний). */
    suspend fun replaceEdited(ctx: Context, path: String, sources: List<String>, clips: List<com.dasein.poryadok.logic.VideoCut.Clip>, aspect: Float?, mute: Boolean): String {
        val f = File(path)
        if (!f.exists()) return path
        val c = editClips(ctx, sources.map { File(it) }, clips, aspect, mute) ?: return path
        withContext(Dispatchers.IO) { f.delete() }
        return c.absolutePath
    }

    private val compactLock = kotlinx.coroutines.sync.Mutex()
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("video_compact", Context.MODE_PRIVATE)
    private const val OLD_KEY = "old_files"
    /** Ролики, которые уже пробовали и меньше они не стали (путь|размер), — не мучаем их при каждом запуске. */
    private const val SKIP_KEY = "skip"

    /**
     * Пережать все видео рецептов и тренировок, которые крупнее нынешних настроек (в том числе сжатые по-старому).
     * Пути обновляются в рецептах и упражнениях. Прежний файл удаляется не сразу, а при следующем запуске и только если
     * на него больше ничего не ссылается: открытый черновик рецепта или упражнения со старым путём ничего не потеряет.
     * Возвращает (сколько роликов пережато, сколько байт освобождено).
     */
    suspend fun recompressAll(ctx: Context, onProgress: (String) -> Unit = {}): Pair<Int, Long> = compactLock.withLock {
        val g = com.dasein.poryadok.Graph
        fun isLocalVideo(p: String) = p.startsWith("/") && com.dasein.poryadok.ui.common.Media.isVideo(p) && File(p).exists()
        suspend fun referenced(): List<String> =
            (g.extra.recipesNow().flatMap { r -> r.videos.lines().map { it.trim() } } + g.training.exercisesNow().flatMap { e -> e.media.lines().map { it.trim() } })
                .filter { it.isNotEmpty() }
        // Прежние файлы прошлых сжатий: удаляем те, на которые уже никто не ссылается.
        val sp = prefs(ctx)
        val used = referenced().toSet()
        val old = sp.getStringSet(OLD_KEY, emptySet()).orEmpty()
        val keep = old.filter { it in used }.toSet()
        withContext(Dispatchers.IO) { (old - keep).forEach { File(it).delete() } }
        sp.edit().putStringSet(OLD_KEY, keep).apply()

        val skip = sp.getStringSet(SKIP_KEY, emptySet()).orEmpty()
        val todo = withContext(Dispatchers.IO) { used.filter { isLocalVideo(it) && "$it|${File(it).length()}" !in skip && worth(it) } }
        var n = 0
        var saved = 0L
        todo.forEachIndexed { i, p ->
            onProgress("Сжимаю видео ${i + 1} из ${todo.size}… Не закрывайте приложение")
            val src = File(p)
            val before = src.length()
            val out = compress(ctx, src)?.absolutePath
            if (out == null) {
                sp.edit().putStringSet(SKIP_KEY, sp.getStringSet(SKIP_KEY, emptySet()).orEmpty() + "$p|$before").apply()
                return@forEachIndexed
            }
            n++
            saved += before - File(out).length()
            com.dasein.poryadok.system.VideoSpeech.carry(ctx, p, out)
            g.extra.recipesNow().filter { r -> r.videos.lines().any { it.trim() == p } }.forEach { r ->
                g.extra.setVideos(r.id, r.videos.lines().map { if (it.trim() == p) out else it }.joinToString("\n"))
            }
            g.training.exercisesNow().filter { e -> e.media.lines().any { it.trim() == p } }.forEach { e ->
                g.training.upsertExercise(e.copy(media = e.media.lines().map { if (it.trim() == p) out else it }.joinToString("\n")))
            }
            sp.edit().putStringSet(OLD_KEY, sp.getStringSet(OLD_KEY, emptySet()).orEmpty() + p).apply()
        }
        n to saved.coerceAtLeast(0)
    }

    /** При запуске, в фоне: всё уже загруженное сжимается само; что не успело — продолжится при следующем запуске. */
    fun autoCompact(ctx: Context) {
        val app = ctx.applicationContext
        com.dasein.poryadok.Graph.scope.launch {
            kotlinx.coroutines.delay(20_000) // не мешаем первым секундам после запуска
            if (compactLock.isLocked) return@launch
            val (n, saved) = runCatching { recompressAll(app) }.getOrDefault(0 to 0L)
            if (n > 0) withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(app, "Видео сжаты: $n · освобождено ${mb(saved)}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun mb(bytes: Long) = if (bytes < 1024 * 1024) "${bytes / 1024} КБ" else String.format(java.util.Locale.US, "%.1f МБ", bytes / 1048576.0)
}
