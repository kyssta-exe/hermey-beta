package com.kyssta.hermey.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyssta.hermey.ui.theme.Hermes
import com.kyssta.hermey.ui.theme.HermesMono

/**
 * Minimal assistant-message markdown renderer (DESIGN.md §12). Supports
 * headings, code blocks, lists, blockquotes, horizontal rules, bold, italic,
 * inline code, and links. No external dependency — ponytail: covers the common
 * cases; add a full markdown lib (e.g. Markwon) only if output quality needs it.
 */
@Composable
fun BasicMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Float = 15f,
) {
    val p = Hermes
    Column(modifier = modifier.fillMaxWidth()) {
        val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("```") -> {
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].startsWith("```")) {
                        codeLines.add(lines[i]); i++
                    }
                    i++ // skip closing fence
                    if (codeLines.isNotEmpty()) {
                        MarkdownCodeBlock(
                            codeLines.joinToString("\n"),
                            fontSize = fontSize,
                        )
                    }
                }
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# ").trim(),
                        fontSize = (fontSize + 4).sp,
                        fontWeight = FontWeight.Bold,
                        color = p.textPrimary,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    i++
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## ").trim(),
                        fontSize = (fontSize + 2).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.textPrimary,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    i++
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### ").trim(),
                        fontSize = (fontSize + 1).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.textPrimary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    i++
                }
                line.trim().matches(Regex("^-{3,}$")) || line.trim().matches(Regex("^\\*{3,}$")) -> {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(p.strokeTertiary))
                    Spacer(Modifier.height(6.dp))
                    i++
                }
                line.startsWith("> ") -> {
                    MarkdownQuoteBlock(line.removePrefix("> ").trim(), fontSize = fontSize)
                    i++
                }
                line.matches(Regex("^\\s*[-*+]\\s+.+")) -> {
                    val content = line.replaceFirst(Regex("^\\s*[-*+]\\s+"), "")
                    val indent = line.takeWhile { it == ' ' || it == '\t' }.length / 2
                    Row(Modifier.padding(start = (indent * 12).dp, top = 2.dp, bottom = 2.dp)) {
                        Text("•", color = p.textTertiary, fontSize = fontSize.sp, modifier = Modifier.padding(end = 6.dp))
                        InlineMarkdown(text = content, fontSize = fontSize)
                    }
                    i++
                }
                line.matches(Regex("^\\s*\\d+\\.\\s+.+")) -> {
                    val numMatch = Regex("^\\s*(\\d+)\\.").find(line) ?: continue
                    val content = line.removePrefix(numMatch.value).trimStart()
                    val indent = line.takeWhile { it == ' ' || it == '\t' }.length / 2
                    Row(Modifier.padding(start = (indent * 12).dp, top = 2.dp, bottom = 2.dp)) {
                        Text("${numMatch.groupValues[1]}.", color = p.textTertiary, fontSize = fontSize.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
                        InlineMarkdown(text = content, fontSize = fontSize)
                    }
                    i++
                }
                line.isBlank() -> {
                    Spacer(Modifier.height(6.dp))
                    i++
                }
                else -> {
                    Text(
                        text = "",
                        modifier = Modifier,
                    )
                    // Render paragraph with inline markdown
                    InlineMarkdown(text = line, fontSize = fontSize)
                    i++
                }
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(content: String, fontSize: Float) {
    val p = Hermes
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(p.background.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Text(
                text = content,
                color = p.textSecondary,
                fontSize = (fontSize - 2).sp,
                fontFamily = HermesMono,
        lineHeight = (fontSize + 2).sp,
            )
        }
    }
}

@Composable
private fun MarkdownQuoteBlock(text: String, fontSize: Float) {
    val p = Hermes
    Column(
        Modifier
            .padding(top = 4.dp, bottom = 4.dp, start = 4.dp)
            .background(p.accent.copy(alpha = 0.08f), shape = RoundedCornerShape(4.dp))
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        InlineMarkdown(text = text, fontSize = fontSize, colorOverride = p.textSecondary, fontStyle = FontStyle.Italic)
    }
}

@Composable
private fun InlineMarkdown(
    text: String,
    fontSize: Float,
    colorOverride: Color = Color.Unspecified,
    fontStyle: FontStyle = FontStyle.Normal,
) {
    val p = Hermes
    val annotated = parseInlineMarkdown(text, p, fontSize)
    val resolvedColor = if (colorOverride == Color.Unspecified) p.textPrimary else colorOverride
    Text(
        text = annotated,
        fontSize = fontSize.sp,
        color = resolvedColor,
        fontStyle = fontStyle,
        lineHeight = (fontSize * 1.5).sp,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

private fun parseInlineMarkdown(
    text: String,
    p: com.kyssta.hermey.ui.theme.HermesPalette,
    fontSizeSp: Float,
): AnnotatedString {
    return buildAnnotatedString {
        val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
        val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
        val codeStyle = SpanStyle(
            background = p.background,
            fontFamily = HermesMono,
            fontSize = (fontSizeSp - 2).sp,
            color = p.accent,
        )
        val linkStyle = SpanStyle(color = p.accent, textDecoration = TextDecoration.Underline)

        var i = 0
        while (i < text.length) {
            // Inline code `...`
            if (text.startsWith("`", i)) {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    pushStyle(codeStyle)
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }
            // Bold **...**
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    pushStyle(boldStyle)
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                    continue
                }
            }
            // Italic *...*
            if (text[i] == '*' && !text.startsWith("**", i)) {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    pushStyle(italicStyle)
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }
            // Link [text](url)
            if (text.startsWith("[", i)) {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket > i && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 1) {
                        val linkText = text.substring(i + 1, closeBracket)
                        pushStyle(linkStyle)
                        append(linkText)
                        pop()
                        i = closeParen + 1
                        continue
                    }
                }
            }
            append(text[i])
            i++
        }
    }
}
