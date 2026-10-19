package com.dasein.poryadok.system

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

/**
 * Речь из видео рецепта → текст.
 *  1) Звук достаётся из видео прямо на телефоне (16 кГц, моно).
 *  2) Распознаётся бесплатным Whisper от Groq (нужен бесплатный ключ console.groq.com) —
 *     или, если ключа нет, распознаванием речи самого телефона (Android 13 и новее).
 */
object VideoSpeech {
    class NoSpeech(msg: String) : Exception(msg)

    private fun sp(ctx: Context) = ctx.getSharedPreferences("ai", Context.MODE_PRIVATE)
    fun groqKey(ctx: Context) = sp(ctx).getString("groq", "").orEmpty().trim()
    fun setGroqKey(ctx: Context, k: String) = sp(ctx).edit().putString("groq", k.trim()).apply()

    /** Есть ли в видео звуковая дорожка. */
    fun hasAudio(ctx: Context, uri: Uri): Boolean = runCatching {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            (0 until ex.trackCount).any { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        } finally { ex.release() }
    }.getOrDefault(false)

    /** Звук видео в 16 кГц моно, 16 бит (не больше [maxSec] секунд). */
    suspend fun pcm16k(ctx: Context, uri: Uri, maxSec: Int = 600): ShortArray = withContext(Dispatchers.Default) {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: run { ex.release(); throw NoSpeech("В видео нет звука") }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val out = ShortArrayBuilder()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var pos = 0.0 // позиция для ресемплинга
        val limit = maxSec * 16000
        try {
            while (!outputDone && out.size < limit) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = Build.VERSION.SDK_INT >= 24 && f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        // Сводим каналы в моно и прореживаем до 16 кГц.
                        val frames = if (floatPcm) info.size / 4 / channels else info.size / 2 / channels
                        val step = rate / 16000.0
                        val mono = FloatArray(frames)
                        for (fr in 0 until frames) {
                            var acc = 0f
                            for (ch in 0 until channels) acc += if (floatPcm) buf.float else buf.short / 32768f
                            mono[fr] = acc / channels
                        }
                        while (pos < frames) {
                            out.add((mono[pos.toInt()].coerceIn(-1f, 1f) * 32767).toInt().toShort())
                            pos += step
                        }
                        pos -= frames
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); ex.release()
        }
        out.toArray()
    }

    private class ShortArrayBuilder {
        private var a = ShortArray(16000 * 30)
        var size = 0; private set
        fun add(v: Short) { if (size == a.size) a = a.copyOf(a.size * 2); a[size++] = v }
        fun toArray() = a.copyOf(size)
    }

    /** Почти тишина — речи нет. */
    fun isSilent(pcm: ShortArray): Boolean {
        if (pcm.size < 16000) return true
        var loud = 0
        for (i in pcm.indices step 160) if (kotlin.math.abs(pcm[i].toInt()) > 1200) loud++
        return loud < pcm.size / 160 / 50
    }

    private fun wav(pcm: ShortArray): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { pcm.forEach { putShort(it) } }.array()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + data.size); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(16000); h.putInt(32000); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(data.size)
        return h.array() + data
    }

    /** Whisper (Groq, бесплатно по ключу). */
    private fun groq(key: String, pcm: ShortArray): String {
        val boundary = "dasein" + System.currentTimeMillis()
        val body = ByteArrayOutputStream()
        fun field(name: String, value: String) {
            body.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
        }
        field("model", "whisper-large-v3-turbo")
        field("language", "ru")
        field("response_format", "text")
        body.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
        body.write(wav(pcm))
        body.write("\r\n--$boundary--\r\n".toByteArray())
        val c = URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 20_000; c.readTimeout = 180_000
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
            if (code == 401) throw Ai.AiError("Ключ Groq не подходит — проверьте его в «Настройки» → «ИИ-помощник».")
            if (code !in 200..299) throw Ai.AiError("Groq ответил $code: ${text.take(120)}")
            return text.trim()
        } finally { c.disconnect() }
    }

    /** Распознавание речи самим телефоном (Android 13+): звук подаётся из файла, без микрофона. */
    private suspend fun onDevice(ctx: Context, pcm: ShortArray): String {
        if (Build.VERSION.SDK_INT < 33) throw Ai.AiError("На этом телефоне распознать речь из файла можно только через бесплатный ключ Groq («Настройки» → «ИИ-помощник»).")
        val pipe = ParcelFileDescriptor.createPipe()
        val parts = StringBuilder()
        val seconds = pcm.size / 16000
        val result = withTimeoutOrNull((seconds + 60) * 1000L) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<String?> { cont ->
                    val rec = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                    else SpeechRecognizer.createSpeechRecognizer(ctx)
                    fun finish(v: String?) { runCatching { rec.destroy() }; runCatching { pipe[0].close() }; if (cont.isActive) cont.resume(v) }
                    rec.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) { finish(parts.toString().ifBlank { null }) }
                        override fun onResults(results: Bundle?) {
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { parts.append(it).append(". ") }
                            finish(parts.toString())
                        }
                        override fun onSegmentResults(segmentResults: Bundle) {
                            segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { parts.append(it).append(". ") }
                        }
                        override fun onEndOfSegmentedSession() { finish(parts.toString()) }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                        .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                    rec.startListening(intent)
                    // Подаём звук в «трубу» в фоне, примерно в реальном времени x4.
                    Thread {
                        runCatching {
                            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { os ->
                                val bb = ByteBuffer.allocate(3200).order(ByteOrder.LITTLE_ENDIAN)
                                var i = 0
                                while (i < pcm.size) {
                                    bb.clear()
                                    val n = minOf(1600, pcm.size - i)
                                    for (k in 0 until n) bb.putShort(pcm[i + k])
                                    os.write(bb.array(), 0, n * 2)
                                    i += n
                                    Thread.sleep(25)
                                }
                            }
                        }
                    }.start()
                    cont.invokeOnCancellation { runCatching { rec.destroy() } }
                }
            }
        }
        return result?.trim().orEmpty()
    }

    /**
     * Видео → текст речи. Бросает [NoSpeech], если звука нет или в нём нет слов —
     * тогда карточку можно заполнить описанием или ссылкой.
     */
    suspend fun transcribe(ctx: Context, uri: Uri, onStage: (String) -> Unit = {}): String {
        if (!hasAudio(ctx, uri)) throw NoSpeech("В видео нет звука")
        onStage("Достаю звук из видео…")
        val pcm = pcm16k(ctx, uri)
        if (isSilent(pcm)) throw NoSpeech("В видео нет речи — только тишина или музыка")
        onStage("Распознаю речь…")
        val key = groqKey(ctx)
        val text = if (key.isNotBlank()) withContext(Dispatchers.IO) { groq(key, pcm) } else onDevice(ctx, pcm)
        if (text.count { it.isLetter() } < 20) throw NoSpeech("Речь в видео не распознана")
        return text
    }

    // ---------- Видео рецепта: распознаём один раз ----------
    // Текст речи запоминается для файла (путь, размер, время изменения): «Рецепт из видео» берёт готовый,
    // а не просит выбрать то же видео снова. Видео, добавленное к рецепту, распознаётся сразу в фоне.

    private fun cache(ctx: Context) = ctx.applicationContext.getSharedPreferences("speech_cache", Context.MODE_PRIVATE)
    private fun keyOf(f: java.io.File) = f.absolutePath + "|" + f.length() + "|" + f.lastModified()
    private val running = HashMap<String, kotlinx.coroutines.Deferred<String>>()
    /** Что сейчас делает фоновое распознавание файла — для подписи на кнопке. */
    val stage = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())

    /** Уже распознанная речь из файла, или null. */
    fun cached(ctx: Context, path: String): String? = java.io.File(path).takeIf { it.exists() }?.let { cache(ctx).getString(keyOf(it), null) }

    /** Файл заменили сжатой копией (то же видео) — речь переносится к новому файлу. */
    fun carry(ctx: Context, from: String, to: String) {
        val text = cache(ctx).all.entries.firstOrNull { it.key.substringBefore('|') == from }?.value as? String ?: return
        java.io.File(to).takeIf { it.exists() }?.let { cache(ctx).edit().putString(keyOf(it), text).apply() }
    }

    /** Речь из файла: из памяти или распознаётся (одновременные запросы ждут одно распознавание). */
    suspend fun transcribeFile(ctx: Context, path: String, onStage: (String) -> Unit = {}): String {
        val f = java.io.File(path)
        cached(ctx, path)?.let { return it }
        val key = keyOf(f)
        val app = ctx.applicationContext
        val job = synchronized(running) {
            running[key] ?: com.dasein.poryadok.Graph.scope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    val text = transcribe(app, Uri.fromFile(f)) { st -> stage.value = stage.value + (path to st) }
                    // Записи о файлах, которых больше нет, убираем.
                    val c = cache(app)
                    val gone = c.all.keys.filter { !java.io.File(it.substringBefore('|')).exists() }
                    c.edit().apply { gone.forEach { remove(it) } }.putString(key, text).apply()
                    text
                } finally {
                    synchronized(running) { running.remove(key) }
                    stage.value = stage.value - path
                }
            }.also { running[key] = it }
        }
        val relay = com.dasein.poryadok.Graph.scope.launch { stage.collect { m -> m[path]?.let(onStage) } }
        return try { job.await() } finally { relay.cancel() }
    }

    /** Распознать речь нового видео рецепта в фоне (ошибки молча: потом можно распознать вручную). */
    fun prefetch(ctx: Context, path: String) {
        if (path.startsWith("http")) return
        val app = ctx.applicationContext
        com.dasein.poryadok.Graph.scope.launch { runCatching { transcribeFile(app, path) } }
    }
}
