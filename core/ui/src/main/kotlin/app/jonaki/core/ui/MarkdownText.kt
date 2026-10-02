package app.jonaki.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private const val CARET_ID = "caret"

/**
 * Renders an assistant answer. [showCaret] draws the live caret after the
 * last block while the answer is still streaming.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier, showCaret: Boolean = false) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val inlineColors = InlineColors(
        codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
        link = MaterialTheme.colorScheme.primary,
    )
    val caretColor = JonakiTheme.colors.live
    val caretContent = mapOf(
        CARET_ID to InlineTextContent(Placeholder(0.5.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            Box(Modifier.fillMaxSize().background(caretColor, RoundedCornerShape(2.dp)))
        },
    )
    val content = @Composable {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (blocks.isEmpty() && showCaret) {
                Text(buildAnnotatedString { appendInlineContent(CARET_ID) }, inlineContent = caretContent)
            }
            blocks.forEachIndexed { index, block ->
                val caretHere = showCaret && index == blocks.lastIndex
                MarkdownBlockView(block, inlineColors, caretHere, caretContent)
            }
        }
    }
    // A finished answer can be selected with a long press; a streaming one changes under the finger.
    if (showCaret) {
        content()
    } else {
        SelectionContainer { content() }
    }
}

private class InlineColors(val codeBackground: Color, val link: Color)

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    colors: InlineColors,
    showCaret: Boolean,
    caretContent: Map<String, InlineTextContent>,
) {
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
    when (block) {
        is MarkdownBlock.Paragraph -> InlineText(block.inlines, bodyStyle, colors, showCaret, caretContent)
        is MarkdownBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            InlineText(block.inlines, style.copy(fontWeight = FontWeight.SemiBold), colors, showCaret, caretContent)
        }
        is MarkdownBlock.CodeBlock -> CodeBlockView(block.code, block.language)
        is MarkdownBlock.BulletList -> ListView(
            markers = block.items.map { "•" },
            items = block.items,
            style = bodyStyle,
            colors = colors,
            showCaret = showCaret,
            caretContent = caretContent,
        )
        is MarkdownBlock.OrderedList -> ListView(
            markers = block.items.indices.map { position -> "${block.start + position}." },
            items = block.items,
            style = bodyStyle,
            colors = colors,
            showCaret = showCaret,
            caretContent = caretContent,
        )
    }
}

@Composable
private fun InlineText(
    inlines: List<MarkdownInline>,
    style: TextStyle,
    colors: InlineColors,
    showCaret: Boolean,
    caretContent: Map<String, InlineTextContent>,
) {
    val text = buildAnnotatedString {
        appendInlines(inlines, colors)
        if (showCaret) {
            append(' ')
            appendInlineContent(CARET_ID)
        }
    }
    Text(text, style = style, inlineContent = caretContent)
}

@Composable
private fun ListView(
    markers: List<String>,
    items: List<List<MarkdownInline>>,
    style: TextStyle,
    colors: InlineColors,
    showCaret: Boolean,
    caretContent: Map<String, InlineTextContent>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, item ->
            Row {
                Text(markers[index], style = style, modifier = Modifier.width(28.dp))
                InlineText(item, style, colors, showCaret && index == items.lastIndex, caretContent)
            }
        }
    }
}

/** A code block with its language, if given, and a copy button above the code. */
@Composable
private fun CodeBlockView(code: String, language: String?) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                Text(
                    language.orEmpty(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        JonakiIcons.ContentCopy,
                        contentDescription = stringResource(R.string.ui_copy_code),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFamily, fontSize = 13.sp),
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}

private fun AnnotatedString.Builder.appendInlines(inlines: List<MarkdownInline>, colors: InlineColors) {
    for (inline in inlines) {
        when (inline) {
            is MarkdownInline.Text -> append(inline.text)
            is MarkdownInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                appendInlines(inline.children, colors)
            }
            is MarkdownInline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlines(inline.children, colors)
            }
            is MarkdownInline.Code -> withStyle(
                SpanStyle(fontFamily = MonospaceFamily, background = colors.codeBackground, fontSize = 0.9.em),
            ) {
                append(inline.code)
            }
            is MarkdownInline.Link -> {
                val linkStyle = TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline))
                withLink(LinkAnnotation.Url(inline.url, linkStyle)) { append(inline.text) }
            }
        }
    }
}
