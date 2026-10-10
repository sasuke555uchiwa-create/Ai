package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SasukeDarkCardBorder
import com.example.ui.theme.SasukeElectricViolet
import com.example.ui.theme.SasukeGreenSuccess
import com.example.ui.theme.SasukeLightViolet
import com.example.ui.theme.SasukeNeonCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FormattedAiContent(
    text: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    CodeSnippetCard(
                        code = block.code,
                        language = block.language
                    )
                }
                is MarkdownBlock.Header -> {
                    Text(
                        text = block.text,
                        style = if (block.level == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildAnnotatedParagraph(block.text, MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun CodeSnippetCard(
    code: String,
    language: String
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F111E),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SasukeDarkCardBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161828))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifEmpty { "CODE" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SasukeNeonCyan,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (copied) {
                        Text(
                            text = "Copied!",
                            style = MaterialTheme.typography.labelSmall,
                            color = SasukeGreenSuccess,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Code", code)
                            clipboard.setPrimaryClip(clip)
                            copied = true
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (copied) SasukeGreenSuccess else Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Code content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class Header(val level: Int, val text: String) : MarkdownBlock()
}

fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var inCodeBlock = false
    var codeLang = ""
    val codeAccumulator = StringBuilder()
    val textAccumulator = StringBuilder()

    fun flushText() {
        if (textAccumulator.isNotEmpty()) {
            val content = textAccumulator.toString().trim()
            if (content.isNotEmpty()) {
                result.add(MarkdownBlock.Paragraph(content))
            }
            textAccumulator.clear()
        }
    }

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                result.add(MarkdownBlock.CodeBlock(codeLang, codeAccumulator.toString().trimEnd()))
                codeAccumulator.clear()
                codeLang = ""
                inCodeBlock = false
            } else {
                flushText()
                codeLang = line.trim().removePrefix("```").trim()
                inCodeBlock = true
            }
        } else if (inCodeBlock) {
            codeAccumulator.append(line).append("\n")
        } else {
            if (line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ")) {
                flushText()
                val level = if (line.startsWith("# ")) 1 else if (line.startsWith("## ")) 2 else 3
                val headerText = line.replace(Regex("^#+\\s*"), "")
                result.add(MarkdownBlock.Header(level, headerText))
            } else {
                textAccumulator.append(line).append("\n")
            }
        }
    }

    if (inCodeBlock) {
        result.add(MarkdownBlock.CodeBlock(codeLang, codeAccumulator.toString().trimEnd()))
    } else {
        flushText()
    }

    return result
}

fun buildAnnotatedParagraph(text: String, primaryColor: Color): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        val codeRegex = Regex("`([^`]+)`")

        // Simple bold / inline code processor
        val tokens = Regex("(\\*\\*.*?\\*\\*|`[^`]+`)").findAll(text)

        tokens.forEach { match ->
            val range = match.range
            if (range.first > currentIndex) {
                append(text.substring(currentIndex, range.first))
            }

            val value = match.value
            if (value.startsWith("**") && value.endsWith("**")) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primaryColor)) {
                    append(value.substring(2, value.length - 2))
                }
            } else if (value.startsWith("`") && value.endsWith("`")) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x338B5CF6),
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(value.substring(1, value.length - 1))
                }
            }
            currentIndex = range.last + 1
        }

        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
