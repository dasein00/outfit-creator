package com.dasein.poryadok.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.dasein.poryadok.Graph

/**
 * Мои настройки упражнения: подходы, повторы (или секунды) и чем утяжеляю — свой вес, гантели, утяжелители.
 * Спрашиваются при первом старте упражнения и дальше подставляются сами; поправить можно в карточке.
 */
data class ExerciseSetup(
    val sets: Int = 3,
    val reps: Int = 12,
    val seconds: Int = 30,
    /** body — свой вес, dumbbells — гантели, wear — утяжелители, both — гантели и утяжелители. */
    val load: String = BODY,
    /** Вес одной гантели, кг. */
    val dumbbellKg: Double = 5.0,
    /** Утяжелители всего, кг. */
    val wearKg: Double = 5.0,
) {
    val usesDumbbells get() = load == DUMBBELLS || load == BOTH
    val usesWear get() = load == WEAR || load == BOTH

    fun encode() = listOf(sets, reps, seconds, load, dumbbellKg, wearKg).joinToString("|")

    companion object {
        const val BODY = "body"
        const val DUMBBELLS = "dumbbells"
        const val WEAR = "wear"
        const val BOTH = "both"
        val LOADS = listOf(BODY to "Свой вес", DUMBBELLS to "Гантели", WEAR to "Утяжелители", BOTH to "Гантели + утяж.")

        fun decode(s: String): ExerciseSetup? = runCatching {
            val p = s.split('|')
            ExerciseSetup(
                p[0].toInt().coerceIn(1, 20), p[1].toInt().coerceIn(0, 500), p[2].toInt().coerceIn(0, 7200),
                p[3].takeIf { it in setOf(BODY, DUMBBELLS, WEAR, BOTH) } ?: BODY, p[4].toDouble().coerceIn(0.0, 100.0), p[5].toDouble().coerceIn(0.0, 50.0),
            )
        }.getOrNull()
    }
}

object ExercisePrefs {
    private fun setupSp() = Graph.app.getSharedPreferences("training_setup", Context.MODE_PRIVATE)
    private fun ui() = Graph.app.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    /** Меняется при правке настроек — экраны перечитывают. */
    val version = mutableIntStateOf(0)

    fun setup(exerciseId: Long): ExerciseSetup? = runCatching { setupSp().getString("ex_$exerciseId", null)?.let { ExerciseSetup.decode(it) } }.getOrNull()

    fun saveSetup(exerciseId: Long, s: ExerciseSetup) {
        runCatching { setupSp().edit().putString("ex_$exerciseId", s.encode()).apply() }
        version.intValue++
    }

    // ---------- Архив упражнений ----------
    private const val ARCHIVE = "training_archived_exercises"
    private fun readArchive(): Set<Long> = runCatching { ui().getString(ARCHIVE, "").orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet() }.getOrDefault(emptySet())
    val archived = mutableStateOf(readArchive())
    private fun saveArchive(v: Set<Long>) { archived.value = v; runCatching { ui().edit().putString(ARCHIVE, v.joinToString(",")).apply() } }
    fun archive(id: Long) = saveArchive(archived.value + id)
    fun restore(id: Long) = saveArchive(archived.value - id)
    fun reload() { archived.value = readArchive(); version.intValue++ }

    /** Удалить упражнение насовсем (из архива, в том числе встроенное): из программ тоже; прошлые подходы остаются в истории. */
    suspend fun deleteForever(e: Exercise) {
        val t = Graph.training
        t.allPlanExercises().filter { it.exerciseId == e.id }.forEach { t.deletePlanExercise(it) }
        t.deleteExercise(e)
        restore(e.id)
    }

    // ---------- Мой инвентарь ----------
    /** Инвентарь дома. По умолчанию — степпер, дорожка, велотренажёр, колесо для пресса, гантели, утяжелители, коврик и стул. */
    val ALL_GEAR = listOf("Степпер", "Беговая дорожка", "Велотренажёр", "Колесо для пресса", "Гантели", "Утяжелители", "Фитнес-коврик", "Стул", "Скакалка", "Турник", "Штанга", "Гиря", "Резинка")
    private val DEFAULT_GEAR = setOf("Степпер", "Беговая дорожка", "Велотренажёр", "Колесо для пресса", "Гантели", "Утяжелители", "Фитнес-коврик", "Стул")
    private const val GEAR = "training_home_gear"
    private const val ONLY_MINE = "training_only_my_gear"
    val myGear = mutableStateOf(runCatching { ui().getString(GEAR, null)?.split('|')?.filter { it.isNotBlank() }?.toSet() }.getOrNull() ?: DEFAULT_GEAR)
    val onlyMine = mutableStateOf(runCatching { ui().getBoolean(ONLY_MINE, true) }.getOrDefault(true))
    fun setGear(v: Set<String>) { myGear.value = v; runCatching { ui().edit().putString(GEAR, v.joinToString("|")).apply() } }
    fun setOnlyMine(v: Boolean) { onlyMine.value = v; runCatching { ui().edit().putBoolean(ONLY_MINE, v).apply() } }
}
