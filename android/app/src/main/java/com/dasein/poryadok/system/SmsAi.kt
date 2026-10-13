package com.dasein.poryadok.system

import android.content.Context
import com.dasein.poryadok.Graph
import com.dasein.poryadok.logic.BankSms
import com.dasein.poryadok.system.Ai.num
import com.dasein.poryadok.system.Ai.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * SMS и пуши банка, которые встроенный разбор не понял, хотя в них есть сумма. Хранятся на телефоне.
 * В ИИ уходят только по кнопке и только выбранные сообщения.
 */
object SmsAi {
    @Serializable
    data class Item(val key: String, val text: String, val at: Long, val channel: String)

    /** Ответ ИИ по одному сообщению. [kind] — «expense», «income» или «skip». */
    data class Guess(val item: Item, val kind: String, val amount: Double, val merchant: String, val category: String?)

    private val json = Json { ignoreUnknownKeys = true }
    private fun file(ctx: Context) = File(ctx.filesDir, "sms_unparsed.json")
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun list(ctx: Context): List<Item> = runCatching { json.decodeFromString(ListSerializer(Item.serializer()), file(ctx).readText()) }.getOrDefault(emptyList())

    private fun write(ctx: Context, l: List<Item>) {
        runCatching { file(ctx).writeText(json.encodeToString(ListSerializer(Item.serializer()), l.takeLast(300))) }
        version.intValue++
    }

    private val MONEY = Regex("""\d[\d\s  ]*(?:[.,]\d{1,2})?\s?(?:руб|р\b|р\.|₽|RUB|RUR)""", RegexOption.IGNORE_CASE)
    private val CODE = Regex("""(?<![\p{L}])(?:код|кодом|пароль)(?![\p{L}])""", RegexOption.IGNORE_CASE)

    /** Стоит ли показывать сообщение как «не распознано»: есть сумма и это не код подтверждения. */
    fun looksLikeMoney(text: String): Boolean {
        val t = BankSms.normalize(text)
        return MONEY.containsMatchIn(t) && !CODE.containsMatchIn(t) && "никому не сообщайте" !in t.lowercase()
    }

    /** Запомнить нераспознанное сообщение (без повторов). */
    fun remember(text: String, at: Long, key: String, channel: String) {
        if (!looksLikeMoney(text)) return
        val ctx = Graph.app
        val l = list(ctx)
        val norm = BankSms.normalize(text)
        if (l.any { it.key == key || BankSms.normalize(it.text) == norm }) return
        write(ctx, l + Item(key, text, at, channel))
    }

    fun dismiss(ctx: Context, keys: Collection<String>) = write(ctx, list(ctx).filter { it.key !in keys })

    /** Разобрать выбранные сообщения бесплатным ИИ. */
    suspend fun ask(ctx: Context, items: List<Item>): List<Guess> = withContext(Dispatchers.IO) {
        val cats = Graph.dao.categoriesNow().map { it.name }.distinct()
        val system = "Ты разбираешь SMS и уведомления российских банков для личного учёта финансов. Для каждого сообщения определи: " +
            "это операция по счёту или нет (коды, реклама, предложения кредита, информирование — не операции); тип — расход или доход; " +
            "сумму операции в рублях (не остаток на счёте!); получателя или отправителя (магазин, человек, организация) коротко; " +
            "подходящую категорию из списка: ${cats.joinToString(", ")}. " +
            "Ответ строго JSON: {\"items\":[{\"n\":номер,\"type\":\"expense|income|skip\",\"amount\":число,\"merchant\":\"…\",\"category\":\"…\"}]}"
        val user = items.mapIndexed { i, it -> "${i + 1}. ${BankSms.normalize(it.text)}" }.joinToString("\n")
        val obj = Ai.extractJson(Ai.ask(ctx, system, user)) ?: throw Ai.AiError("ИИ ответил не по формату — попробуйте ещё раз.")
        val arr = (obj["items"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val n = o.num("n")?.toInt() ?: return@mapNotNull null
            val item = items.getOrNull(n - 1) ?: return@mapNotNull null
            Guess(item, o.str("type")?.lowercase() ?: "skip", o.num("amount") ?: 0.0, o.str("merchant").orEmpty().take(60), o.str("category"))
        }
    }

    /** Записать подтверждённые операции и убрать их из списка нераспознанных. */
    suspend fun apply(ctx: Context, guesses: List<Guess>): Int {
        var added = 0
        guesses.filter { it.kind == "expense" || it.kind == "income" }.filter { it.amount > 0 }.forEach { g ->
            val op = BankSms.Op(
                if (g.kind == "income") BankSms.Kind.INCOME else BankSms.Kind.EXPENSE,
                g.amount, g.merchant, "", g.category, BankSms.normalize(g.item.text),
            )
            val channel = Sber.Channel.entries.firstOrNull { it.prefix == g.item.channel } ?: Sber.Channel.SMS
            if (Sber.record(g.item.text, g.item.at, g.item.key, channel, opOverride = op)) added++
        }
        dismiss(ctx, guesses.map { it.item.key })
        return added
    }
}
