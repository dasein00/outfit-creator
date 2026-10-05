package com.dasein.poryadok.system

import android.content.Context
import com.dasein.poryadok.logic.Pressure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Дневник давления: люди (вы и родственники) и их замеры. Лежит в files/pressure/diary.json —
 * попадает в резервную копию разделом «Давление и пульс».
 */
object PressureStore {
    @Serializable
    data class Diary(val people: List<Pressure.Person> = emptyList(), val readings: List<Pressure.Reading> = emptyList(), val current: Long = 0)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow<Diary?>(null)

    private fun file(ctx: Context) = File(File(ctx.filesDir, "pressure").apply { mkdirs() }, "diary.json")

    fun flow(ctx: Context): StateFlow<Diary?> {
        if (state.value == null) state.value = load(ctx)
        return state
    }

    private fun load(ctx: Context): Diary = runCatching { json.decodeFromString(Diary.serializer(), file(ctx).readText()) }.getOrDefault(Diary())

    fun now(ctx: Context): Diary = state.value ?: load(ctx).also { state.value = it }

    private suspend fun save(ctx: Context, d: Diary) = withContext(Dispatchers.IO) {
        state.value = d
        val f = file(ctx)
        val tmp = File(f.parentFile, "diary.tmp")
        tmp.writeText(json.encodeToString(Diary.serializer(), d))
        tmp.renameTo(f)
    }

    /** После восстановления копии — перечитать файл. */
    fun reload(ctx: Context) { state.value = load(ctx) }

    private fun newId(d: Diary) = maxOf(System.currentTimeMillis(), (d.people.maxOfOrNull { it.id } ?: 0) + 1, (d.readings.maxOfOrNull { it.id } ?: 0) + 1)

    suspend fun upsertPerson(ctx: Context, p: Pressure.Person): Long {
        val d = now(ctx)
        val id = if (p.id == 0L) newId(d) else p.id
        val item = p.copy(id = id, createdAt = if (p.createdAt == 0L) System.currentTimeMillis() else p.createdAt)
        save(ctx, d.copy(people = d.people.filter { it.id != id } + item, current = if (d.current == 0L) id else d.current))
        return id
    }

    suspend fun deletePerson(ctx: Context, id: Long) {
        val d = now(ctx)
        val left = d.people.filter { it.id != id }
        save(ctx, d.copy(people = left, readings = d.readings.filter { it.personId != id }, current = if (d.current == id) left.firstOrNull()?.id ?: 0 else d.current))
    }

    suspend fun select(ctx: Context, id: Long) { val d = now(ctx); if (d.current != id) save(ctx, d.copy(current = id)) }

    suspend fun upsertReading(ctx: Context, r: Pressure.Reading): Long {
        val d = now(ctx)
        val id = if (r.id == 0L) newId(d) else r.id
        save(ctx, d.copy(readings = d.readings.filter { it.id != id } + r.copy(id = id)))
        return id
    }

    suspend fun deleteReading(ctx: Context, id: Long) {
        val d = now(ctx)
        save(ctx, d.copy(readings = d.readings.filter { it.id != id }))
    }

    /** CSV для врача или таблицы: дата;время;верхнее;нижнее;пульс;рука;положение;аритмия;метки;симптомы;заметка. */
    fun csv(p: Pressure.Person, rs: List<Pressure.Reading>): String {
        val z = java.time.ZoneId.systemDefault()
        val df = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy;HH:mm")
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        return "﻿Дата;Время;Верхнее;Нижнее;Пульс;Рука;Положение;Аритмия;Метки;Симптомы;Заметка\n" +
            rs.sortedBy { it.time }.joinToString("\n") { r ->
                listOf(
                    java.time.Instant.ofEpochMilli(r.time).atZone(z).format(df), r.sys.toString(), r.dia.toString(), r.pulse?.toString().orEmpty(),
                    if (r.arm == 1) "правая" else "левая", listOf("сидя", "лёжа", "стоя")[r.position.coerceIn(0, 2)], if (r.irregular) "да" else "",
                    q(r.tags.joinToString(", ")), q(r.symptoms.joinToString(", ")), q(r.note),
                ).joinToString(";")
            } + "\n# ${p.name}\n"
    }
}
