package com.dasein.poryadok.system

import android.content.Context
import com.dasein.poryadok.logic.Tutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** «Репетитор»: прогресс по словам, настройки, свои слова, серия и очки. Лежит в files/tutor/ — попадает в резервную копию. */
object TutorStore {
    @Serializable
    data class MyWord(val en: String, val ru: String, val ex: String = "", val exRu: String = "")

    @Serializable
    data class State(
        val settings: Tutor.Settings = Tutor.Settings(),
        val progress: Map<String, Tutor.Progress> = emptyMap(),
        val custom: List<MyWord> = emptyList(),
        /** Дни, когда цель дня выполнена. */
        val goalDays: List<Long> = emptyList(),
        val xp: Int = 0,
        /** Очки по дням: день → очки. */
        val xpByDay: Map<Long, Int> = emptyMap(),
        val onboarded: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow<State?>(null)
    private var builtIn: List<Tutor.Word>? = null

    private fun file(ctx: Context) = File(File(ctx.filesDir, "tutor").apply { mkdirs() }, "state.json")

    fun builtIn(ctx: Context): List<Tutor.Word> = builtIn ?: runCatching {
        Tutor.parse(ctx.assets.open("tutor/words.json").bufferedReader().use { it.readText() })
    }.getOrDefault(emptyList()).also { builtIn = it }

    /** Все слова: встроенные + свои. */
    fun words(ctx: Context, s: State = now(ctx)): List<Tutor.Word> =
        builtIn(ctx) + s.custom.map { Tutor.Word(it.en, it.ru, it.ex, it.exRu, "B1", 1, "my", custom = true) }

    fun flow(ctx: Context): StateFlow<State?> { if (state.value == null) state.value = load(ctx); return state }
    fun now(ctx: Context): State = state.value ?: load(ctx).also { state.value = it }
    fun reload(ctx: Context) { state.value = load(ctx) }

    private fun load(ctx: Context): State = runCatching { json.decodeFromString(State.serializer(), file(ctx).readText()) }.getOrDefault(State())

    suspend fun update(ctx: Context, f: (State) -> State) = withContext(Dispatchers.IO) {
        val n = f(now(ctx))
        state.value = n
        val tmp = File(file(ctx).parentFile, "state.tmp")
        tmp.writeText(json.encodeToString(State.serializer(), n))
        tmp.renameTo(file(ctx))
    }

    /** Ответ в упражнении: прогресс слова + очки (+10 за верный, +2 за попытку). */
    suspend fun answer(ctx: Context, w: Tutor.Word, correct: Boolean, today: Long) = update(ctx) { s ->
        val gain = if (correct) 10 else 2
        s.copy(
            progress = s.progress + (w.id to Tutor.answer(s.progress[w.id], correct, today)),
            xp = s.xp + gain, xpByDay = s.xpByDay + (today to ((s.xpByDay[today] ?: 0) + gain)),
        )
    }

    suspend fun known(ctx: Context, w: Tutor.Word, today: Long) = update(ctx) { s -> s.copy(progress = s.progress + (w.id to Tutor.known(s.progress[w.id], today))) }

    /** Добавить слово в изучение (повторить сегодня). */
    suspend fun startLearning(ctx: Context, w: Tutor.Word, today: Long) = update(ctx) { s ->
        if (w.id in s.progress) s else s.copy(progress = s.progress + (w.id to Tutor.Progress(box = 0, due = today, added = today)))
    }

    suspend fun goalDone(ctx: Context, today: Long) = update(ctx) { s -> if (today in s.goalDays) s else s.copy(goalDays = s.goalDays + today) }

    suspend fun reset(ctx: Context, w: Tutor.Word) = update(ctx) { s -> s.copy(progress = s.progress - w.id) }
}
