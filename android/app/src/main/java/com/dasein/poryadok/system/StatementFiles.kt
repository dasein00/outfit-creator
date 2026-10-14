package com.dasein.poryadok.system

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.dasein.poryadok.logic.BankSms
import com.dasein.poryadok.logic.Statement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Чтение выписок из банковских приложений: CSV, Excel (.xlsx) и PDF.
 * Всё разбирается на телефоне; ИИ — только по кнопке, если формат не распознан.
 */
object StatementFiles {
    data class Read(val ops: List<Statement.Op>, val text: String, val kind: String, val header: Statement.Header = Statement.Header())

    private fun guess(t: String, income: Boolean) = BankSms.guessCategory(t, income)

    fun fileName(ctx: Context, uri: Uri): String = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull().orEmpty()

    suspend fun read(ctx: Context, uri: Uri): Read = withContext(Dispatchers.IO) {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось открыть файл")
        val name = fileName(ctx, uri).lowercase()
        when {
            bytes.size > 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte() -> {
                val text = pdfText(ctx, bytes)
                Read(Statement.fromText(text, ::guess), text, "PDF", Statement.header(text))
            }
            bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> {
                val rows = xlsxRows(bytes)
                Read(Statement.fromRows(rows, ::guess), rows.joinToString("\n") { it.joinToString(" | ") }, "Excel")
            }
            name.endsWith(".xls") -> error("Старый формат .xls не поддерживается — сохраните выписку в .xlsx, CSV или PDF.")
            else -> {
                val text = decode(bytes)
                val rows = Statement.parseCsv(text)
                Read(Statement.fromRows(rows, ::guess), text, "CSV")
            }
        }
    }

    /** UTF-8, а если в нём битые символы — Windows-1251 (так выгружают многие банки). */
    private fun decode(bytes: ByteArray): String {
        val utf = bytes.decodeToString()
        return if ('�' in utf) String(bytes, charset("windows-1251")) else utf
    }

    private fun pdfText(ctx: Context, bytes: ByteArray): String {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx.applicationContext)
        return com.tom_roush.pdfbox.pdmodel.PDDocument.load(bytes).use { doc ->
            com.tom_roush.pdfbox.text.PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }
    }

    /** Первый лист .xlsx как таблица строк. */
    private fun xlsxRows(bytes: ByteArray): List<List<String>> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == "xl/sharedStrings.xml" || e.name.startsWith("xl/worksheets/sheet")) files[e.name] = z.readBytes()
                e = z.nextEntry
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val sheet = files.keys.filter { it.startsWith("xl/worksheets/sheet") }.minByOrNull { it.length * 100 + (it.filter(Char::isDigit).toIntOrNull() ?: 0) }
            ?: error("В файле Excel нет листов")
        return sheetRows(files.getValue(sheet), shared)
    }

    private fun sharedStrings(b: ByteArray): List<String> {
        val p = Xml.newPullParser().apply { setInput(ByteArrayInputStream(b), "UTF-8") }
        val out = mutableListOf<String>()
        var cur: StringBuilder? = null
        var inT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) { "si" -> cur = StringBuilder(); "t" -> inT = true }
                XmlPullParser.TEXT -> if (inT) cur?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) { "si" -> { out += cur.toString(); cur = null }; "t" -> inT = false }
            }
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        ref.takeWhile { it.isLetter() }.forEach { n = n * 26 + (it.uppercaseChar() - 'A' + 1) }
        return n - 1
    }

    private fun sheetRows(b: ByteArray, shared: List<String>): List<List<String>> {
        val p = Xml.newPullParser().apply { setInput(ByteArrayInputStream(b), "UTF-8") }
        val rows = mutableListOf<List<String>>()
        var row: MutableMap<Int, String>? = null
        var col = 0
        var type = ""
        var value = StringBuilder()
        var inV = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = mutableMapOf()
                    "c" -> { col = p.getAttributeValue(null, "r")?.let(::colIndex) ?: (row?.size ?: 0); type = p.getAttributeValue(null, "t").orEmpty(); value = StringBuilder() }
                    "v", "t" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inV = false
                    "c" -> row?.put(col, if (type == "s") shared.getOrNull(value.toString().trim().toIntOrNull() ?: -1).orEmpty() else value.toString())
                    "row" -> row?.let { r -> if (r.isNotEmpty()) rows += (0..r.keys.max()).map { r[it].orEmpty() } }
                }
            }
        }
        return rows
    }

    /** ИИ разбирает текст выписки, если формат не узнали (только по кнопке). */
    suspend fun aiParse(ctx: Context, text: String): List<Statement.Op> {
        val system = "Ты разбираешь банковскую выписку. Найди все операции. Ответ строго JSON: " +
            "{\"ops\":[{\"date\":\"ДД.ММ.ГГГГ\",\"amount\":123.45,\"income\":false,\"text\":\"описание\"}]}. " +
            "amount — положительное число; income=true для зачислений, переводов вам, зарплаты, возвратов, кэшбэка. " +
            "Не включай остатки, итоги и заголовки."
        val o = Ai.extractJson(Ai.ask(ctx, system, text.take(14000))) ?: throw Ai.AiError("ИИ ответил не по формату — попробуйте ещё раз.")
        return (o["ops"] as? JsonArray).orEmpty().mapNotNull { e ->
            val j = e as? JsonObject ?: return@mapNotNull null
            val day = (j["date"] as? JsonPrimitive)?.contentOrNull?.let(Statement::parseDate) ?: return@mapNotNull null
            val amount = (j["amount"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.let(Statement::parseNumber) }?.let { kotlin.math.abs(it) } ?: return@mapNotNull null
            val income = (j["income"] as? JsonPrimitive)?.contentOrNull == "true"
            val t = (j["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (amount <= 0) null else Statement.Op(day, amount, income, t.take(120), guess(t, income))
        }
    }
}
