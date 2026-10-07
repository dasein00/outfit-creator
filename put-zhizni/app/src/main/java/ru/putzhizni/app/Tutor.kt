package ru.putzhizni.app

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Произношение слов: системный синтезатор речи телефона (голоса работают без интернета, если установлены). */
object Speech : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Triple<String, String, Float>? = null

    private fun init(ctx: Context) { if (tts == null) tts = TextToSpeech(ctx.applicationContext, this) }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        pending?.let { (t, l, r) -> say(t, l, r) }
        pending = null
    }

    private fun say(text: String, lang: String, rate: Float) {
        val t = tts ?: return
        t.language = Locale.forLanguageTag(lang)
        t.setSpeechRate(rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pz")
    }

    fun speak(ctx: Context, text: String, lang: String, rate: Float) {
        init(ctx)
        if (!ready) { pending = Triple(text, lang, rate); return }
        say(text, lang, rate)
    }

    /** ≥ 0 — язык доступен; -1 — нет голоса; -2 — нет поддержки; -3 — синтезатор ещё не готов. */
    fun check(ctx: Context, lang: String): Int {
        init(ctx)
        val t = tts ?: return -3
        if (!ready) return -3
        return t.isLanguageAvailable(Locale.forLanguageTag(lang))
    }

    fun installVoices(ctx: Context) {
        try { ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { try { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }
    }
}

/** Перевод фраз на устройстве (Google ML Kit): языковой пакет ~30 МБ скачивается один раз, потом перевод работает офлайн. */
object OnDeviceTranslate {
    private fun lang(tag: String) = TranslateLanguage.fromLanguageTag(tag) ?: TranslateLanguage.ENGLISH

    fun translate(text: String, from: String, to: String, allowDownload: Boolean, done: (String) -> Unit) {
        val tr = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(lang(from)).setTargetLanguage(lang(to)).build())
        val run = {
            tr.translate(text)
                .addOnSuccessListener { done(JSONObject().put("ok", true).put("text", it).toString()); tr.close() }
                .addOnFailureListener { done(JSONObject().put("ok", false).put("err", it.message ?: "translate").toString()); tr.close() }
        }
        if (!allowDownload) {
            models { list ->
                if (lang(from) in list && lang(to) in list) run()
                else { done(JSONObject().put("ok", false).put("err", "no-model").toString()); tr.close() }
            }
            return
        }
        tr.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { run() }
            .addOnFailureListener { done(JSONObject().put("ok", false).put("err", "download").toString()); tr.close() }
    }

    fun models(done: (Set<String>) -> Unit) {
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { set -> done(set.map { it.language }.toSet()) }
            .addOnFailureListener { done(emptySet()) }
    }

    fun modelsJson(done: (String) -> Unit) = models { done(JSONArray(it.toList()).toString()) }
}
