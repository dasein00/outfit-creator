package com.dasein.poryadok.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

object ExKind {
    /** Повторения с весом (штанга, гантели, тренажёр). */
    const val WEIGHT = 0
    /** Повторения без веса (отжимания, подтягивания). */
    const val REPS = 1
    /** Упражнение на время (планка, бег). */
    const val TIME = 2
    val names = listOf("Вес × повторы", "Повторы", "Время")
}

/**
 * Упражнение из библиотеки. media — картинки, GIF и видео (пути к файлам), по одному в строке.
 */
@Serializable
@Entity(tableName = "exercises", indices = [Index("seedKey")])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscle: String = "",
    val equipment: String = "",
    val description: String = "",
    val media: String = "",
    val kind: Int = ExKind.WEIGHT,
    val glyph: String = "sport/15",
    val custom: Boolean = true,
    val seedKey: String? = null,
    val createdAt: Long = 0,
)

/** Программа тренировки: список упражнений с целями. daysMask — дни недели (пн = бит 0). */
@Serializable
@Entity(tableName = "workout_plans")
data class WorkoutPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val glyph: String = "sport/15",
    val cover: String = "",
    val daysMask: Int = 0,
    val sort: Int = 0,
    val archived: Boolean = false,
    val seedKey: String? = null,
    val createdAt: Long = 0,
)

/**
 * Упражнение в программе и его текущая цель. Цель меняется после каждой тренировки по правилу двойной прогрессии:
 * сначала растут повторения до repMax, затем вес (или подходы для упражнений без веса).
 */
@Serializable
@Entity(tableName = "plan_exercises", indices = [Index("planId")])
data class PlanExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val exerciseId: Long,
    val pos: Int = 0,
    val sets: Int = 3,
    val reps: Int = 10,
    val weight: Double = 0.0,
    val seconds: Int = 0,
    val restSec: Int = 90,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val weightStep: Double = 2.5,
    val maxSets: Int = 5,
    val note: String = "",
)

@Serializable
@Entity(tableName = "workout_sessions", indices = [Index("day")])
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long? = null,
    val title: String = "",
    val day: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val note: String = "",
    /** Как прошла тренировка, 1..5. */
    val feel: Int = 0,
    /** Запись в общем журнале тренировок (для калорий и истории здоровья). */
    val workoutId: Long? = null,
)

/** Один подход. Планируемые значения заполняются из цели, фактические правит пользователь. */
@Serializable
@Entity(tableName = "set_logs", indices = [Index("sessionId"), Index("exerciseId")])
data class SetLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val planExerciseId: Long? = null,
    val setIndex: Int = 0,
    val reps: Int = 0,
    val weight: Double = 0.0,
    val seconds: Int = 0,
    val done: Boolean = false,
    val at: Long = 0,
)

@Dao
interface TrainingDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE") fun exercises(): Flow<List<Exercise>>
    @Query("SELECT * FROM exercises") suspend fun exercisesNow(): List<Exercise>
    @Query("SELECT * FROM exercises WHERE id = :id") fun exercise(id: Long): Flow<Exercise?>
    @Query("SELECT * FROM exercises WHERE id = :id") suspend fun exerciseNow(id: Long): Exercise?
    @Query("SELECT seedKey FROM exercises WHERE seedKey IS NOT NULL") suspend fun exerciseSeedKeys(): List<String>
    @Upsert suspend fun upsertExercise(e: Exercise): Long
    @Delete suspend fun deleteExercise(e: Exercise)

    @Query("SELECT * FROM workout_plans WHERE archived = 0 ORDER BY sort, id") fun plans(): Flow<List<WorkoutPlan>>
    @Query("SELECT * FROM workout_plans") suspend fun plansNow(): List<WorkoutPlan>
    @Query("SELECT * FROM workout_plans WHERE id = :id") fun plan(id: Long): Flow<WorkoutPlan?>
    @Query("SELECT * FROM workout_plans WHERE id = :id") suspend fun planNow(id: Long): WorkoutPlan?
    @Query("SELECT seedKey FROM workout_plans WHERE seedKey IS NOT NULL") suspend fun planSeedKeys(): List<String>
    @Upsert suspend fun upsertPlan(p: WorkoutPlan): Long
    @Delete suspend fun deletePlan(p: WorkoutPlan)

    @Query("SELECT * FROM plan_exercises ORDER BY planId, pos") fun planExercises(): Flow<List<PlanExercise>>
    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY pos") fun planExercisesOf(planId: Long): Flow<List<PlanExercise>>
    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY pos") suspend fun planExercisesOfNow(planId: Long): List<PlanExercise>
    @Query("SELECT * FROM plan_exercises") suspend fun allPlanExercises(): List<PlanExercise>
    @Upsert suspend fun upsertPlanExercise(p: PlanExercise): Long
    @Upsert suspend fun upsertPlanExercises(p: List<PlanExercise>)
    @Delete suspend fun deletePlanExercise(p: PlanExercise)
    @Query("DELETE FROM plan_exercises WHERE planId = :planId") suspend fun deletePlanExercisesOf(planId: Long)

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC") fun sessions(): Flow<List<WorkoutSession>>
    @Query("SELECT * FROM workout_sessions") suspend fun sessionsNow(): List<WorkoutSession>
    @Query("SELECT * FROM workout_sessions WHERE id = :id") fun session(id: Long): Flow<WorkoutSession?>
    @Query("SELECT * FROM workout_sessions WHERE id = :id") suspend fun sessionNow(id: Long): WorkoutSession?
    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1") fun activeSession(): Flow<WorkoutSession?>
    @Upsert suspend fun upsertSession(s: WorkoutSession): Long
    @Delete suspend fun deleteSession(s: WorkoutSession)

    @Query("SELECT * FROM set_logs ORDER BY at") fun setLogs(): Flow<List<SetLog>>
    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY exerciseId, setIndex") fun setsOf(sessionId: Long): Flow<List<SetLog>>
    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY exerciseId, setIndex") suspend fun setsOfNow(sessionId: Long): List<SetLog>
    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId ORDER BY at") fun setsOfExercise(exerciseId: Long): Flow<List<SetLog>>
    @Query("SELECT * FROM set_logs") suspend fun allSets(): List<SetLog>
    @Upsert suspend fun upsertSet(s: SetLog): Long
    @Upsert suspend fun upsertSets(s: List<SetLog>)
    @Delete suspend fun deleteSet(s: SetLog)
    @Query("DELETE FROM set_logs WHERE sessionId = :sessionId") suspend fun deleteSetsOf(sessionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putExercises(items: List<Exercise>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPlans(items: List<WorkoutPlan>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPlanExercises(items: List<PlanExercise>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSessions(items: List<WorkoutSession>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSets(items: List<SetLog>)
    @Query("DELETE FROM exercises") suspend fun wipeExercises()
    @Query("DELETE FROM workout_plans") suspend fun wipePlans()
    @Query("DELETE FROM plan_exercises") suspend fun wipePlanExercises()
    @Query("DELETE FROM workout_sessions") suspend fun wipeSessions()
    @Query("DELETE FROM set_logs") suspend fun wipeSets()
}
