package com.dasein.poryadok.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.ui.theme.LocalExtra

/**
 * Простое оформление текста (как Markdown): «# Заголовок», «## Раздел», списки «- …» и «1. …»,
 * **жирный** и *курсив*. Так ИИ оформляет описание блюда, а вы можете править его обычным текстом.
 */
@Composable
fun RichText(text: String, modifier: Modifier = Modifier) {
    val extra = LocalExtra.current
    val accent = MaterialTheme.colorScheme.primary
    SelectionContainer(modifier) {
        Column {
            text.lines().forEach { raw ->
                val line = raw.trimEnd()
                val t = line.trimStart()
                when {
                    t.isEmpty() -> Text("", fontSize = 6.sp)
                    t.startsWith("### ") -> Text(inline(t.removePrefix("### ")), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    t.startsWith("## ") -> Text(inline(t.removePrefix("## ")), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = accent, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                    t.startsWith("# ") -> Text(inline(t.removePrefix("# ")), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
                    t.startsWith("- ") || t.startsWith("• ") || t.startsWith("* ") || t.startsWith("— ") ->
                        Row(Modifier.padding(start = if (line.startsWith("  ")) 16.dp else 0.dp, top = 2.dp)) {
                            Text("•", color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                            Text(inline(t.drop(2)), lineHeight = 21.sp)
                        }
                    Regex("""^\d{1,2}[.)]\s""").containsMatchIn(t) -> {
                        val n = t.substringBefore(' ')
                        Row(Modifier.padding(top = 3.dp)) {
                            Text(n, color = accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(26.dp))
                            Text(inline(t.substringAfter(' ')), lineHeight = 21.sp)
                        }
                    }
                    t.startsWith(">") -> Text(inline(t.removePrefix(">").trim()), fontStyle = FontStyle.Italic, color = extra.dim, lineHeight = 20.sp)
                    else -> Text(inline(t), lineHeight = 21.sp)
                }
            }
        }
    }
}

/** **жирный** и *курсив* внутри строки. */
private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        if (s.startsWith("**", i)) {
            val end = s.indexOf("**", i + 2)
            if (end > i + 2) { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(s.substring(i + 2, end)) }; i = end + 2; continue }
        }
        if (s[i] == '*' && i + 1 < s.length && s[i + 1] != ' ') {
            val end = s.indexOf('*', i + 1)
            if (end > i + 1) { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }; i = end + 1; continue }
        }
        append(s[i]); i++
    }
}
