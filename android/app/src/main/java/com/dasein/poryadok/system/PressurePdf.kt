package com.dasein.poryadok.system

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.dasein.poryadok.logic.Pressure
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Отчёт для врача (как в Qardio и SmartBP): A4, средние за период — общие, утренние и вечерние,
 * график, таблица по дням (утро / вечер) и все замеры с отметками.
 */
object PressurePdf {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private val zone = ZoneId.systemDefault()
    private val dFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru"))
    private val dShort = DateTimeFormatter.ofPattern("dd.MM", Locale("ru"))
    private val tFmt = DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale("ru"))

    fun write(p: Pressure.Person, all: List<Pressure.Reading>, days: Int, out: OutputStream) {
        val to = LocalDate.now()
        val from = to.minusDays(days.toLong() - 1)
        val rs = all.filter { it.personId == p.id && Pressure.day(it) in from..to }.sortedBy { it.time }
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val body = paint(10f); val bold = paint(10f, true); val small = paint(8.5f, color = Color.DKGRAY)
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M
            page!!.canvas.drawText("DASEIN · дневник давления · ${p.name} · стр. $pageNo", M, H - 20f, small)
        }
        fun ensure(h: Float) { if (page == null || y + h > H - 40f) newPage() }
        fun line(text: String, pt: Paint = body, gap: Float = 4f) { ensure(pt.textSize + gap); y += pt.textSize; page!!.canvas.drawText(text, M, y, pt); y += gap }

        newPage()
        line("Отчёт об артериальном давлении", paint(18f, true), 6f)
        line("${p.name}" + listOfNotNull(p.age()?.let { "$it лет" }, if (p.sex == 1) "женщина" else "мужчина", p.heightCm?.let { "рост $it см" }, p.weightKg?.let { "вес ${fmt(it)} кг" }).joinToString(", ", ", "), bold)
        line("Период: ${from.format(dFmt)} — ${to.format(dFmt)} ($days дн.), замеров: ${rs.size}")
        val t = Pressure.target(p)
        line("Целевое давление: ${t.sysLow}–${t.sysHigh} / ${t.diaLow}–${t.diaHigh} мм рт. ст." + if (t.custom) " (назначено врачом)" else "")
        val diag = listOfNotNull(
            "гипертония".takeIf { p.hypertension }, "получает лечение".takeIf { p.treated }, "диабет".takeIf { p.diabetes },
            "болезнь почек".takeIf { p.kidney }, "ИБС / инфаркт / инсульт".takeIf { p.heart }, "курит".takeIf { p.smoker },
        )
        if (diag.isNotEmpty()) line("Отмечено: " + diag.joinToString(", "))
        if (p.meds.isNotBlank()) line("Лекарства: " + p.meds.replace('\n', ' '))
        y += 6f

        // Средние.
        val all7 = Pressure.avg(rs)
        val morning = Pressure.avg(rs.filter { Pressure.isMorning(it) })
        val evening = Pressure.avg(rs.filter { Pressure.isEvening(it) })
        line("Средние значения", paint(13f, true), 6f)
        fun avgRow(label: String, a: Pressure.Avg?) {
            ensure(16f); y += 11f
            val c = page!!.canvas
            c.drawText(label, M, y, bold)
            c.drawText(a?.let { "${it.sys} / ${it.dia}" } ?: "—", M + 170, y, paint(11f, true))
            c.drawText(a?.pulse?.let { "пульс $it" } ?: "", M + 270, y, body)
            c.drawText(a?.let { "замеров ${it.n} · ${Pressure.category(it.sys, it.dia).title}" } ?: "", M + 340, y, small)
            y += 6f
        }
        avgRow("За весь период", all7)
        avgRow("Утро (4:00–11:59)", morning)
        avgRow("Вечер (17:00–23:59)", evening)
        if (rs.isNotEmpty()) {
            val hi = rs.maxBy { it.sys }; val lo = rs.minBy { it.sys }
            line("Максимум: ${hi.sys}/${hi.dia} (${fmtT(hi.time)}) · минимум: ${lo.sys}/${lo.dia} (${fmtT(lo.time)})", small)
            val above = rs.count { it.sys > t.sysHigh || it.dia > t.diaHigh }
            line("Выше цели: $above из ${rs.size} (${above * 100 / rs.size}%)" + if (rs.any { it.irregular }) " · отмечен нерегулярный пульс: ${rs.count { it.irregular }}" else "", small)
        }
        y += 8f

        // График.
        if (rs.size >= 2) {
            ensure(190f)
            val c = page!!.canvas
            val left = M + 28; val right = W - M; val top = y + 6; val bottom = y + 170
            val lo = minOf(50, rs.minOf { it.dia } - 5); val hi = maxOf(180, rs.maxOf { it.sys } + 5)
            fun yy(v: Int) = bottom - (v - lo).toFloat() / (hi - lo) * (bottom - top)
            val t0 = rs.first().time; val t1 = maxOf(rs.last().time, t0 + 1)
            fun xx(tm: Long) = left + (tm - t0).toFloat() / (t1 - t0) * (right - left)
            c.drawRect(left, yy(t.sysHigh), right, yy(t.sysLow), Paint().apply { color = Color.argb(30, 60, 160, 90) })
            c.drawRect(left, yy(t.diaHigh), right, yy(t.diaLow), Paint().apply { color = Color.argb(22, 60, 120, 200) })
            val grid = Paint().apply { color = Color.LTGRAY; strokeWidth = .5f }
            listOf(60, 80, 100, 120, 140, 160, 180).filter { it in lo..hi }.forEach { v -> c.drawLine(left, yy(v), right, yy(v), grid); c.drawText("$v", M, yy(v) + 3, small) }
            fun series(get: (Pressure.Reading) -> Int, col: Int) {
                val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; strokeWidth = 1.6f }
                rs.zipWithNext().forEach { (a, b) -> c.drawLine(xx(a.time), yy(get(a)), xx(b.time), yy(get(b)), pt) }
                rs.forEach { r -> c.drawCircle(xx(r.time), yy(get(r)), 2.2f, pt) }
            }
            series({ it.sys }, Color.rgb(200, 70, 50))
            series({ it.dia }, Color.rgb(50, 100, 190))
            c.drawText(rs.first().let { fmtD(it.time) }, left, bottom + 12, small)
            c.drawText(rs.last().let { fmtD(it.time) }, right - 30, bottom + 12, small)
            c.drawText("— верхнее   — нижнее   зелёная и синяя полосы — цель", left + 80, bottom + 12, small)
            y = bottom + 22
        }

        // По дням: утро и вечер.
        line("По дням (средние утром и вечером)", paint(13f, true), 6f)
        fun header(cols: List<Pair<String, Float>>) {
            ensure(16f); y += 10f
            cols.forEach { (s, x) -> page!!.canvas.drawText(s, M + x, y, bold) }
            y += 5f
        }
        val dayCols = listOf("Дата" to 0f, "Утро" to 80f, "пульс" to 150f, "Вечер" to 210f, "пульс" to 280f, "Замеров" to 340f)
        header(dayCols)
        rs.groupBy { Pressure.day(it) }.toSortedMap().forEach { (d, list) ->
            if (page == null || y + 14f > H - 40f) { newPage(); header(dayCols) }
            y += 10f
            val m = Pressure.avg(list.filter { Pressure.isMorning(it) }); val e = Pressure.avg(list.filter { Pressure.isEvening(it) })
            val c = page!!.canvas
            c.drawText(d.format(dShort), M, y, body)
            c.drawText(m?.let { "${it.sys}/${it.dia}" } ?: "—", M + 80, y, body)
            c.drawText(m?.pulse?.toString() ?: "", M + 150, y, body)
            c.drawText(e?.let { "${it.sys}/${it.dia}" } ?: "—", M + 210, y, body)
            c.drawText(e?.pulse?.toString() ?: "", M + 280, y, body)
            c.drawText("${list.size}", M + 340, y, body)
            y += 3f
        }
        y += 10f

        // Все замеры.
        line("Все замеры", paint(13f, true), 6f)
        val allCols = listOf("Когда" to 0f, "Давление" to 80f, "Пульс" to 150f, "Отметки" to 195f)
        header(allCols)
        rs.forEach { r ->
            if (page == null || y + 14f > H - 40f) { newPage(); header(allCols) }
            y += 10f
            val c = page!!.canvas
            val over = r.sys > t.sysHigh || r.dia > t.diaHigh
            c.drawText(fmtT(r.time), M, y, body)
            c.drawText("${r.sys}/${r.dia}", M + 80, y, if (over) paint(10f, true, Color.rgb(180, 40, 30)) else body)
            c.drawText(r.pulse?.toString().orEmpty() + if (r.irregular) " !" else "", M + 150, y, body)
            val marks = (r.tags + r.symptoms + listOfNotNull(r.note.takeIf { it.isNotBlank() })).joinToString(", ")
            c.drawText(marks.take(70), M + 195, y, small)
            y += 3f
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }

    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else String.format(Locale("ru"), "%.1f", v)
    private fun fmtT(t: Long) = Instant.ofEpochMilli(t).atZone(zone).format(tFmt)
    private fun fmtD(t: Long) = Instant.ofEpochMilli(t).atZone(zone).format(dShort)
}
