package com.dasein.poryadok.data

import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock
import com.dasein.poryadok.Graph
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.DoneSet
import com.dasein.poryadok.logic.Progression
import com.dasein.poryadok.logic.Target
import kotlin.math.roundToInt

/** Итог упражнения после тренировки: что сделано и какая цель на следующий раз. */
data class ProgressNote(val exercise: String, val done: String, val next: String, val reason: String)

object TrainingRepo {
    private data class SeedEx(val key: String, val name: String, val muscle: String, val equipment: String, val kind: Int, val glyph: String, val about: String)

    private val EXERCISES = listOf(
        SeedEx("squat_bb", "Приседания со штангой", "Ноги", "Штанга", ExKind.WEIGHT, "sport/15",
            "Штанга на верхней части трапеций, стопы на ширине плеч. Опускайтесь, отводя таз назад, до параллели бедра с полом; колени смотрят по направлению носков. Вставайте, толкаясь всей стопой, спина нейтральная."),
        SeedEx("goblet", "Гоблет-присед с гантелью", "Ноги", "Гантель", ExKind.WEIGHT, "sport/15",
            "Держите гантель вертикально у груди. Приседайте глубоко, локти проходят между коленями, корпус вертикальный. Отличное упражнение, чтобы освоить технику приседа."),
        SeedEx("bw_squat", "Приседания без веса", "Ноги", "Без инвентаря", ExKind.REPS, "sport/12",
            "Руки вперёд для баланса, таз назад, пятки не отрываются. Опускайтесь как можно ниже с ровной спиной."),
        SeedEx("lunges", "Выпады с гантелями", "Ноги", "Гантели", ExKind.WEIGHT, "sport/15",
            "Шаг вперёд, опуститесь до угла 90° в обоих коленях, заднее колено почти касается пола. Оттолкнитесь передней ногой. Чередуйте ноги; повторы считайте на каждую."),
        SeedEx("rdl", "Румынская тяга", "Ноги", "Штанга", ExKind.WEIGHT, "sport/15",
            "Ноги чуть согнуты, штанга скользит по бёдрам. Отводите таз назад до натяжения задней поверхности бедра, спина прямая. Возвращайтесь, сжимая ягодицы."),
        SeedEx("deadlift", "Становая тяга", "Спина", "Штанга", ExKind.WEIGHT, "sport/15",
            "Гриф над серединой стопы, хват чуть шире ног. Спина нейтральная, лопатки над грифом. Поднимайте, разгибая ноги и таз одновременно, гриф ведите вплотную к ногам."),
        SeedEx("hip_thrust", "Ягодичный мост со штангой", "Ягодицы", "Штанга", ExKind.WEIGHT, "sport/15",
            "Лопатки на скамье, штанга на тазе (с подкладкой). Поднимайте таз до прямой линии от плеч до колен, задержитесь на секунду наверху."),
        SeedEx("glute_bridge", "Ягодичный мост", "Ягодицы", "Без инвентаря", ExKind.REPS, "sport/12",
            "Лёжа на спине, стопы у таза. Поднимайте таз, сжимая ягодицы, не прогибая поясницу. Задержка 1–2 секунды наверху."),
        SeedEx("leg_press", "Жим ногами", "Ноги", "Тренажёр", ExKind.WEIGHT, "sport/15",
            "Стопы на середине платформы на ширине плеч. Опускайте платформу, пока таз не начнёт отрываться от спинки, выжимайте без полного выпрямления коленей."),
        SeedEx("calf", "Подъёмы на носки", "Ноги", "Без инвентаря", ExKind.REPS, "sport/12",
            "Встаньте носками на ступеньку. Опускайте пятки ниже уровня опоры и поднимайтесь максимально высоко, медленно."),
        SeedEx("wall_sit", "Стульчик у стены", "Ноги", "Без инвентаря", ExKind.TIME, "sport/12",
            "Спина прижата к стене, бёдра параллельны полу, колени над пятками. Держите положение."),
        SeedEx("bench", "Жим штанги лёжа", "Грудь", "Штанга", ExKind.WEIGHT, "sport/15",
            "Лопатки сведены, стопы в пол. Опускайте штангу на низ груди, локти под углом около 45° к корпусу, выжимайте вверх. Работайте со страхующим."),
        SeedEx("db_bench", "Жим гантелей лёжа", "Грудь", "Гантели", ExKind.WEIGHT, "sport/15",
            "Гантели над грудью, опускайте по дуге до уровня груди, выжимайте, сводя гантели наверху."),
        SeedEx("incline_db", "Жим гантелей на наклонной скамье", "Грудь", "Гантели", ExKind.WEIGHT, "sport/15",
            "Скамья под углом 30°. Опускайте гантели к верхней части груди, выжимайте вверх."),
        SeedEx("pushups", "Отжимания", "Грудь", "Без инвентаря", ExKind.REPS, "sport/12",
            "Тело прямое от макушки до пяток, руки чуть шире плеч. Опускайтесь до касания грудью почти пола. Облегчённый вариант — с колен или от скамьи."),
        SeedEx("dips", "Отжимания на брусьях", "Грудь", "Брусья", ExKind.REPS, "sport/13",
            "Опускайтесь, пока плечи не окажутся чуть ниже локтей, слегка наклонив корпус вперёд. Выжимайте до прямых рук."),
        SeedEx("pullups", "Подтягивания", "Спина", "Турник", ExKind.REPS, "sport/13",
            "Хват чуть шире плеч. Тянитесь грудью к перекладине, сводя лопатки, опускайтесь полностью. Облегчение — с резиной или негативные повторы."),
        SeedEx("inverted_row", "Австралийские подтягивания", "Спина", "Низкая перекладина", ExKind.REPS, "sport/13",
            "Под низкой перекладиной или столом, тело прямое. Подтягивайте грудь к перекладине, сводя лопатки."),
        SeedEx("lat_pulldown", "Тяга верхнего блока", "Спина", "Тренажёр", ExKind.WEIGHT, "sport/15",
            "Широкий хват, тяните рукоять к верху груди, отводя локти вниз и назад. Не раскачивайтесь корпусом."),
        SeedEx("bb_row", "Тяга штанги в наклоне", "Спина", "Штанга", ExKind.WEIGHT, "sport/15",
            "Наклон корпуса ~45°, спина прямая. Тяните штангу к низу живота, сводя лопатки, опускайте подконтрольно."),
        SeedEx("db_row", "Тяга гантели одной рукой", "Спина", "Гантель", ExKind.WEIGHT, "sport/15",
            "Колено и рука на скамье, спина параллельна полу. Тяните гантель к поясу, локоть вдоль корпуса. Повторы на каждую руку."),
        SeedEx("ohp", "Жим штанги стоя", "Плечи", "Штанга", ExKind.WEIGHT, "sport/15",
            "Штанга на передних дельтах, пресс напряжён. Выжимайте вверх, убирая голову назад и возвращая под гриф наверху."),
        SeedEx("db_press", "Жим гантелей сидя", "Плечи", "Гантели", ExKind.WEIGHT, "sport/15",
            "Спина прижата к спинке, гантели у плеч. Выжимайте вверх, не касаясь гантелями наверху."),
        SeedEx("lateral", "Махи гантелями в стороны", "Плечи", "Гантели", ExKind.WEIGHT, "sport/15",
            "Руки чуть согнуты, поднимайте гантели в стороны до уровня плеч без рывков, опускайте медленно."),
        SeedEx("face_pull", "Тяга каната к лицу", "Плечи", "Блок или резина", ExKind.WEIGHT, "sport/15",
            "Тяните канат к лицу, разводя руки и выворачивая плечи наружу. Полезно для осанки и здоровья плечевых суставов."),
        SeedEx("biceps", "Сгибания рук с гантелями", "Руки", "Гантели", ExKind.WEIGHT, "sport/12",
            "Локти прижаты к корпусу, сгибайте руки без раскачки, опускайте медленно до почти прямых рук."),
        SeedEx("triceps", "Разгибания рук на блоке", "Руки", "Блок или резина", ExKind.WEIGHT, "sport/12",
            "Локти прижаты и неподвижны, разгибайте руки вниз до конца, возвращайте подконтрольно."),
        SeedEx("plank", "Планка", "Пресс", "Без инвентаря", ExKind.TIME, "sport/14",
            "Упор на предплечья, тело прямое, ягодицы и пресс напряжены, поясница не провисает. Дышите ровно."),
        SeedEx("side_plank", "Боковая планка", "Пресс", "Без инвентаря", ExKind.TIME, "sport/14",
            "Упор на предплечье, тело на одной линии, таз не опускается. Время на каждую сторону."),
        SeedEx("crunch", "Скручивания", "Пресс", "Без инвентаря", ExKind.REPS, "sport/14",
            "Лёжа, колени согнуты. Отрывайте лопатки от пола, скручивая корпус, поясница прижата."),
        SeedEx("leg_raise", "Подъёмы ног лёжа", "Пресс", "Без инвентаря", ExKind.REPS, "sport/14",
            "Руки под тазом, поднимайте прямые ноги до вертикали, опускайте медленно, не касаясь пола."),
        SeedEx("burpee", "Бёрпи", "Всё тело", "Без инвентаря", ExKind.REPS, "sport/22",
            "Присед, упор лёжа, отжимание, прыжком вернуть ноги к рукам и выпрыгнуть вверх с хлопком."),
        SeedEx("mountain", "Скалолаз", "Кардио", "Без инвентаря", ExKind.TIME, "sport/22",
            "Упор лёжа, поочерёдно быстро подтягивайте колени к груди, таз не поднимается."),
        SeedEx("jump_rope", "Скакалка", "Кардио", "Скакалка", ExKind.TIME, "sport/03",
            "Прыгайте на носках невысоко, вращайте скакалку кистями, локти у корпуса."),
        SeedEx("kb_swing", "Махи гирей", "Всё тело", "Гиря", ExKind.WEIGHT, "sport/15",
            "Движение от таза: отведите таз назад, гиря между ногами, резко разогните таз — гиря взлетает до уровня груди. Руки только направляют."),
    )

    private data class SeedPlanEx(val key: String, val sets: Int, val reps: Int, val repMin: Int, val repMax: Int, val weight: Double = 0.0, val step: Double = 2.5, val seconds: Int = 0, val rest: Int = 90)
    private data class SeedPlan(val key: String, val name: String, val about: String, val glyph: String, val days: Int, val items: List<SeedPlanEx>)

    private val PLANS = listOf(
        SeedPlan(
            "full_body", "Всё тело для начинающих",
            "Три тренировки в неделю на все группы мышц. Работайте в диапазоне повторений; когда во всех подходах получилось верхнее число — приложение само добавит вес.",
            "sport/15", 0b0010101,
            listOf(
                SeedPlanEx("goblet", 3, 10, 8, 12, 12.0, 2.0), SeedPlanEx("db_bench", 3, 10, 8, 12, 10.0, 2.0),
                SeedPlanEx("db_row", 3, 10, 8, 12, 12.0, 2.0), SeedPlanEx("db_press", 3, 10, 8, 12, 8.0, 2.0),
                SeedPlanEx("glute_bridge", 3, 12, 10, 20, rest = 60), SeedPlanEx("plank", 3, 0, 0, 0, seconds = 30, rest = 60),
            ),
        ),
        SeedPlan(
            "home_bw", "Дома без инвентаря",
            "Собственный вес: сначала растут повторения, затем добавляется подход. Подходит для тренировок дома или в отпуске.",
            "sport/12", 0b0101010,
            listOf(
                SeedPlanEx("bw_squat", 3, 15, 12, 25, rest = 60), SeedPlanEx("pushups", 3, 8, 6, 15, rest = 75),
                SeedPlanEx("inverted_row", 3, 8, 6, 15, rest = 75), SeedPlanEx("glute_bridge", 3, 15, 12, 25, rest = 60),
                SeedPlanEx("burpee", 3, 8, 6, 15, rest = 60), SeedPlanEx("plank", 3, 0, 0, 0, seconds = 30, rest = 45),
                SeedPlanEx("wall_sit", 2, 0, 0, 0, seconds = 30, rest = 45),
            ),
        ),
        SeedPlan(
            "upper", "Верх тела",
            "Сплит «верх/низ» для тех, кто тренируется 4 раза в неделю: жимы, тяги и руки.",
            "sport/13", 0b0001001,
            listOf(
                SeedPlanEx("bench", 4, 8, 6, 10, 40.0, 2.5, rest = 120), SeedPlanEx("bb_row", 4, 8, 6, 10, 40.0, 2.5, rest = 120),
                SeedPlanEx("ohp", 3, 8, 6, 10, 25.0, 2.5, rest = 120), SeedPlanEx("pullups", 3, 6, 4, 10, rest = 120),
                SeedPlanEx("biceps", 3, 10, 8, 12, 8.0, 1.0, rest = 60), SeedPlanEx("triceps", 3, 10, 8, 12, 15.0, 2.5, rest = 60),
            ),
        ),
        SeedPlan(
            "lower", "Низ тела",
            "Сплит «верх/низ»: приседания, тяги и ягодицы.",
            "sport/15", 0b0010010,
            listOf(
                SeedPlanEx("squat_bb", 4, 6, 5, 8, 50.0, 2.5, rest = 150), SeedPlanEx("rdl", 3, 8, 8, 10, 40.0, 2.5, rest = 120),
                SeedPlanEx("lunges", 3, 10, 8, 12, 10.0, 2.0, rest = 90), SeedPlanEx("hip_thrust", 3, 10, 8, 12, 50.0, 5.0, rest = 90),
                SeedPlanEx("calf", 3, 15, 12, 20, rest = 60), SeedPlanEx("plank", 3, 0, 0, 0, seconds = 40, rest = 60),
            ),
        ),
    )

    private val seedLock = kotlinx.coroutines.sync.Mutex()

    suspend fun seed() = seedLock.withLock { seedLocked() }

    private suspend fun seedLocked() {
        val t = Graph.training
        val now = System.currentTimeMillis()
        val have = t.exerciseSeedKeys().toSet()
        EXERCISES.filter { it.key !in have }.forEach { e ->
            t.upsertExercise(Exercise(name = e.name, muscle = e.muscle, equipment = e.equipment, description = e.about, kind = e.kind, glyph = e.glyph, custom = false, seedKey = e.key, createdAt = now))
        }
        val byKey = t.exercisesNow().filter { it.seedKey != null }.associateBy { it.seedKey!! }
        val havePlans = t.planSeedKeys().toSet()
        PLANS.filter { it.key !in havePlans }.forEachIndexed { i, p ->
            val id = t.upsertPlan(WorkoutPlan(name = p.name, description = p.about, glyph = p.glyph, daysMask = p.days, sort = i, seedKey = p.key, createdAt = now))
            t.upsertPlanExercises(p.items.mapIndexedNotNull { pos, it ->
                val ex = byKey[it.key] ?: return@mapIndexedNotNull null
                PlanExercise(
                    planId = id, exerciseId = ex.id, pos = pos, sets = it.sets, reps = it.reps, weight = it.weight, seconds = it.seconds,
                    restSec = it.rest, repMin = it.repMin, repMax = it.repMax, weightStep = it.step,
                )
            })
        }
    }

    fun target(p: PlanExercise, kind: Int) = Target(kind, p.sets, p.reps, p.weight, p.seconds, p.repMin, p.repMax, p.weightStep, p.maxSets)

    /** Создаёт тренировку по программе: подходы заполнены целями. Возвращает id тренировки. */
    suspend fun start(planId: Long?): Long {
        val t = Graph.training
        val now = System.currentTimeMillis()
        val plan = planId?.let { t.planNow(it) }
        val sid = t.upsertSession(WorkoutSession(planId = planId, title = plan?.name ?: "Свободная тренировка", day = Dates.today(), startedAt = now))
        if (planId != null) {
            val sets = t.planExercisesOfNow(planId).flatMap { pe ->
                (0 until pe.sets).map { i -> SetLog(sessionId = sid, exerciseId = pe.exerciseId, planExerciseId = pe.id, setIndex = i, reps = pe.reps, weight = pe.weight, seconds = pe.seconds, at = now + i) }
            }
            t.upsertSets(sets)
        }
        return sid
    }

    suspend fun addExercise(sessionId: Long, exerciseId: Long, sets: Int = 3) {
        val t = Graph.training
        val now = System.currentTimeMillis()
        val last = t.allSets().filter { it.exerciseId == exerciseId && it.done }.maxByOrNull { it.at }
        t.upsertSets((0 until sets).map { i ->
            SetLog(sessionId = sessionId, exerciseId = exerciseId, setIndex = i, reps = last?.reps ?: 10, weight = last?.weight ?: 0.0, seconds = last?.seconds ?: 30, at = now + i)
        })
    }

    suspend fun addSet(sessionId: Long, exerciseId: Long) {
        val t = Graph.training
        val same = t.setsOfNow(sessionId).filter { it.exerciseId == exerciseId }
        val last = same.maxByOrNull { it.setIndex }
        t.upsertSet(
            SetLog(
                sessionId = sessionId, exerciseId = exerciseId, planExerciseId = last?.planExerciseId, setIndex = (last?.setIndex ?: -1) + 1,
                reps = last?.reps ?: 10, weight = last?.weight ?: 0.0, seconds = last?.seconds ?: 30, at = System.currentTimeMillis(),
            )
        )
    }

    private fun fmtW(w: Double) = if (w == Math.floor(w)) w.toInt().toString() else w.toString().replace('.', ',')

    fun describe(kind: Int, sets: Int, reps: Int, weight: Double, seconds: Int): String = when (kind) {
        ExKind.TIME -> "$sets × $seconds с"
        ExKind.REPS -> "$sets × $reps"
        else -> "$sets × $reps × ${fmtW(weight)} кг"
    }

    fun describeDone(kind: Int, sets: List<SetLog>): String {
        val done = sets.filter { it.done }
        if (done.isEmpty()) return "не выполнено"
        return when (kind) {
            ExKind.TIME -> done.joinToString(" / ") { "${it.seconds} с" }
            ExKind.REPS -> done.joinToString(" / ") { "${it.reps}" }
            else -> done.joinToString(" / ") { "${it.reps}×${fmtW(it.weight)}" }
        }
    }

    /**
     * Завершает тренировку: записывает её в общий журнал (минуты и калории по MET) и пересчитывает цели программы
     * по правилу двойной прогрессии. Возвращает итоги по упражнениям.
     */
    suspend fun finish(sessionId: Long, feel: Int, note: String, at: Long = System.currentTimeMillis()): List<ProgressNote> {
        val t = Graph.training
        val s = t.sessionNow(sessionId) ?: return emptyList()
        val now = at
        val sets = t.setsOfNow(sessionId)
        val exercises = t.exercisesNow().associateBy { it.id }
        val minutes = ((now - s.startedAt) / 60000).toInt().coerceIn(1, 300)
        val weight = Graph.dao.lastWeight()?.kg ?: Graph.dao.profileNow()?.startWeight ?: 70.0
        val doneSets = sets.count { it.done }
        val vigorous = sets.any { it.done && exercises[it.exerciseId]?.muscle in setOf("Кардио", "Всё тело") }
        val kcal = Progression.kcal(minutes, weight, vigorous)
        val notes = mutableListOf<ProgressNote>()

        Graph.extraDb.withTransaction {
            s.planId?.let { planId ->
                val all = t.allSets()
                val sessions = t.sessionsNow().associateBy { it.id }
                t.planExercisesOfNow(planId).forEach { pe ->
                    val ex = exercises[pe.exerciseId] ?: return@forEach
                    val mine = sets.filter { it.planExerciseId == pe.id || (it.planExerciseId == null && it.exerciseId == pe.exerciseId) }
                    if (mine.none { it.done }) return@forEach
                    // Предыдущая тренировка этой программы — для правила разгрузки.
                    val prev = all.filter { it.planExerciseId == pe.id && it.sessionId != sessionId }
                        .groupBy { it.sessionId }.maxByOrNull { (sid, _) -> sessions[sid]?.startedAt ?: 0 }?.value.orEmpty()
                    val target = target(pe, ex.kind)
                    val failedBefore = Progression.failed(target, prev.map { DoneSet(it.reps, it.weight, it.seconds, it.done) })
                    val n = Progression.next(target, mine.map { DoneSet(it.reps, it.weight, it.seconds, it.done) }, failedBefore)
                    val nt = n.target
                    t.upsertPlanExercise(pe.copy(sets = nt.sets, reps = nt.reps, weight = nt.weight, seconds = nt.seconds, repMax = nt.repMax))
                    notes += ProgressNote(ex.name, describeDone(ex.kind, mine), describe(ex.kind, nt.sets, nt.reps, nt.weight, nt.seconds), n.reason)
                }
            }
            val names = sets.filter { it.done }.map { it.exerciseId }.distinct().mapNotNull { exercises[it]?.name }
            val summary = names.joinToString(", ")
            val wid = Graph.dao.upsertWorkout(
                Workout(
                    id = s.workoutId ?: 0, day = s.day, type = "Силовая", minutes = minutes, kcal = kcal,
                    exercises = summary, note = listOf(s.title, note).filter { it.isNotBlank() }.joinToString(". ") + " · подходов: $doneSets",
                )
            )
            t.upsertSession(s.copy(finishedAt = now, feel = feel, note = note, workoutId = s.workoutId ?: wid))
        }
        return notes
    }

    suspend fun discard(sessionId: Long) {
        val t = Graph.training
        t.deleteSetsOf(sessionId)
        t.sessionNow(sessionId)?.let { s ->
            s.workoutId?.let { wid -> runCatching { Graph.dao.deleteWorkout(Workout(id = wid, day = s.day, type = "")) } }
            t.deleteSession(s)
        }
    }

    /** История упражнения по тренировкам: день, подходы, лучший расчётный максимум (Эпли), тоннаж. */
    data class ExerciseDay(val day: Long, val sets: List<SetLog>, val best1rm: Double, val volume: Double, val bestReps: Int, val bestSeconds: Int)

    fun history(sets: List<SetLog>, sessions: List<WorkoutSession>): List<ExerciseDay> {
        val byId = sessions.associateBy { it.id }
        return sets.filter { it.done }.groupBy { it.sessionId }.mapNotNull { (sid, list) ->
            val day = byId[sid]?.day ?: return@mapNotNull null
            ExerciseDay(
                day, list.sortedBy { it.setIndex },
                list.maxOf { Progression.oneRepMax(it.weight, it.reps) },
                list.sumOf { it.weight * it.reps },
                list.maxOf { it.reps }, list.maxOf { it.seconds },
            )
        }.sortedBy { it.day }
    }

    fun e1rmText(v: Double) = if (v <= 0) "—" else "${(v * 10).roundToInt() / 10.0}".replace('.', ',') + " кг"
}
