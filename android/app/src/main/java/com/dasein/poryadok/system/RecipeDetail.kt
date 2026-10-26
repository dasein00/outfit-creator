package com.dasein.poryadok.system

import android.content.Context
import com.dasein.poryadok.system.Ai.arr
import com.dasein.poryadok.system.Ai.num
import com.dasein.poryadok.system.Ai.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Подробные шаги рецепта для новичка: ИИ расписывает каждый этап — огонь, посуду, время, как понять, что готово.
 * Ответ сохраняется на телефоне и дальше показывается без интернета. Если шаги рецепта поменяли — расписывается заново.
 */
object RecipeDetail {
    @Serializable
    data class Step(val text: String, val minutes: Int = 0)

    @Serializable
    private data class Saved(val src: Int, val steps: List<Step>)

    private val json = Json { ignoreUnknownKeys = true }
    private fun file(ctx: Context, id: Long) = File(ctx.filesDir, "recipe_detail/$id.json")

    private fun srcHash(steps: List<String>, ingredients: List<String>) = (steps + "|" + ingredients).joinToString("\n").hashCode()

    /** Сохранённые подробные шаги, если они сделаны для текущих шагов рецепта. */
    fun saved(ctx: Context, id: Long, steps: List<String>, ingredients: List<String>): List<Step>? = runCatching {
        val s = json.decodeFromString(Saved.serializer(), file(ctx, id).readText())
        s.steps.takeIf { s.src == srcHash(steps, ingredients) && it.isNotEmpty() }
    }.getOrNull()

    fun forget(ctx: Context, id: Long) { file(ctx, id).delete() }

    suspend fun make(ctx: Context, id: Long, name: String, servings: Int, ingredients: List<String>, steps: List<String>): List<Step> = withContext(Dispatchers.IO) {
        val system = "Ты опытный повар и терпеливый учитель для человека, который почти не умеет готовить. " +
            "Перепиши шаги рецепта подробно и понятно для полного новичка. Для каждого шага укажи: что именно сделать и в какой посуде; " +
            "какой огонь (слабый, средний, сильный) или температуру духовки; сколько минут; как понять, что пора переходить дальше " +
            "(цвет, запах, консистенция); если есть — частую ошибку и как её избежать. Объясняй кулинарные термины простыми словами. " +
            "Не меняй ингредиенты и их количество, не добавляй новых продуктов. Можно разбить длинный шаг на несколько. " +
            "Добавь в начало шаг подготовки (что достать, помыть, нарезать), если его нет. Пиши по-русски, обращайся на «вы». " +
            "Каждый шаг начинай с глагола в повелительном наклонении («Нарежьте», «Обжарьте»), температуру пиши в °C, без нумерации и эмодзи. " +
            "Ответ строго JSON: {\"steps\":[{\"text\":\"…\",\"minutes\":0}]}, где minutes — время ожидания на этом шаге (0, если не нужно)."
        val user = buildString {
            appendLine("Блюдо: $name")
            appendLine("Порций: $servings")
            appendLine("Ингредиенты:"); ingredients.forEach { appendLine("- $it") }
            appendLine("Шаги:"); steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
        }
        val obj: JsonObject = Ai.extractJson(Ai.ask(ctx, system, user)) ?: throw Ai.AiError("ИИ ответил не по формату — попробуйте ещё раз.")
        val out = obj.arr("steps").orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val t = o.str("text")?.trim().orEmpty()
            if (t.isEmpty()) null else Step(com.dasein.poryadok.logic.RecipeStyle.step(t), o.num("minutes")?.toInt()?.coerceIn(0, 600) ?: 0)
        }
        if (out.isEmpty()) throw Ai.AiError("ИИ не прислал шаги — попробуйте ещё раз.")
        file(ctx, id).apply { parentFile?.mkdirs() }.writeText(json.encodeToString(Saved.serializer(), Saved(srcHash(steps, ingredients), out)))
        out
    }
}
