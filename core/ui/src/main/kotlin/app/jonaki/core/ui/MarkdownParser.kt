package app.jonaki.core.ui

/** The block structure of an assistant answer. */
sealed interface MarkdownBlock {
    data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownBlock

    data class Heading(val level: Int, val inlines: List<MarkdownInline>) : MarkdownBlock

    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock

    data class BulletList(val items: List<List<MarkdownInline>>) : MarkdownBlock

    data class OrderedList(val start: Int, val items: List<List<MarkdownInline>>) : MarkdownBlock
}

sealed interface MarkdownInline {
    data class Text(val text: String) : MarkdownInline

    data class Strong(val children: List<MarkdownInline>) : MarkdownInline

    data class Emphasis(val children: List<MarkdownInline>) : MarkdownInline

    data class Code(val code: String) : MarkdownInline

    data class Link(val text: String, val url: String) : MarkdownInline
}

/**
 * A small Markdown subset: the parts models use in chat answers. It never
 * fails; anything it does not recognise stays as plain text, which matters
 * because answers are parsed again on every streamed chunk.
 */
object MarkdownParser {
    private val headingPattern = Regex("^(#{1,6})\\s+(.*)$")
    private val bulletPattern = Regex("^\\s*[-*+]\\s+(.*)$")
    private val orderedPattern = Regex("^\\s*(\\d{1,9})[.)]\\s+(.*)$")

    fun parse(markdown: String): List<MarkdownBlock> {
        val builder = BlockBuilder()
        val lines = markdown.replace("\r\n", "\n").split("\n")
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.trimStart().startsWith("```")) {
                builder.flush()
                val language = line.trimStart().removePrefix("```").trim().ifEmpty { null }
                val codeLines = mutableListOf<String>()
                index += 1
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    codeLines += lines[index]
                    index += 1
                }
                builder.blocks += MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language)
                index += 1
                continue
            }
            builder.addLine(line)
            index += 1
        }
        builder.flush()
        return builder.blocks
    }

    private class BlockBuilder {
        val blocks = mutableListOf<MarkdownBlock>()
        private val paragraphLines = mutableListOf<String>()
        private val bulletItems = mutableListOf<List<MarkdownInline>>()
        private val orderedItems = mutableListOf<List<MarkdownInline>>()
        private var orderedStart = 1

        fun addLine(line: String) {
            if (line.isBlank()) {
                flush()
                return
            }
            val heading = headingPattern.matchEntire(line)
            if (heading != null) {
                flush()
                blocks += MarkdownBlock.Heading(heading.groupValues[1].length, parseInline(heading.groupValues[2].trim()))
                return
            }
            val bullet = bulletPattern.matchEntire(line)
            if (bullet != null) {
                flushParagraph()
                flushOrdered()
                bulletItems += parseInline(bullet.groupValues[1].trim())
                return
            }
            val ordered = orderedPattern.matchEntire(line)
            if (ordered != null) {
                flushParagraph()
                flushBullets()
                if (orderedItems.isEmpty()) {
                    orderedStart = ordered.groupValues[1].toInt()
                }
                orderedItems += parseInline(ordered.groupValues[2].trim())
                return
            }
            flushBullets()
            flushOrdered()
            paragraphLines += line.trim()
        }

        fun flush() {
            flushParagraph()
            flushBullets()
            flushOrdered()
        }

        private fun flushParagraph() {
            if (paragraphLines.isEmpty()) return
            blocks += MarkdownBlock.Paragraph(parseInline(paragraphLines.joinToString(" ")))
            paragraphLines.clear()
        }

        private fun flushBullets() {
            if (bulletItems.isEmpty()) return
            blocks += MarkdownBlock.BulletList(bulletItems.toList())
            bulletItems.clear()
        }

        private fun flushOrdered() {
            if (orderedItems.isEmpty()) return
            blocks += MarkdownBlock.OrderedList(orderedStart, orderedItems.toList())
            orderedItems.clear()
        }
    }

    fun parseInline(text: String): List<MarkdownInline> {
        val inlines = mutableListOf<MarkdownInline>()
        val plain = StringBuilder()

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                inlines += MarkdownInline.Text(plain.toString())
                plain.clear()
            }
        }

        var index = 0
        while (index < text.length) {
            val span = spanAt(text, index)
            if (span == null) {
                plain.append(text[index])
                index += 1
                continue
            }
            flushPlain()
            inlines += span.inline
            index = span.end
        }
        flushPlain()
        return inlines
    }

    private class Span(val inline: MarkdownInline, val end: Int)

    private fun spanAt(text: String, index: Int): Span? {
        return when {
            text[index] == '`' -> codeSpan(text, index)
            text.startsWith("**", index) -> strongSpan(text, index)
            text[index] == '*' -> emphasisSpan(text, index)
            text[index] == '[' -> linkSpan(text, index)
            else -> null
        }
    }

    private fun codeSpan(text: String, index: Int): Span? {
        val close = text.indexOf('`', index + 1)
        if (close <= index + 1) return null
        return Span(MarkdownInline.Code(text.substring(index + 1, close)), close + 1)
    }

    private fun strongSpan(text: String, index: Int): Span? {
        val close = text.indexOf("**", index + 2)
        if (close <= index + 2) return null
        val inner = text.substring(index + 2, close)
        if (!hasTightEdges(inner)) return null
        return Span(MarkdownInline.Strong(parseInline(inner)), close + 2)
    }

    private fun emphasisSpan(text: String, index: Int): Span? {
        var close = text.indexOf('*', index + 1)
        // A '*' that starts a '**' pair belongs to bold, not to this emphasis.
        while (close != -1 && text.startsWith("**", close)) {
            close = text.indexOf('*', close + 2)
        }
        if (close <= index + 1) return null
        val inner = text.substring(index + 1, close)
        if (!hasTightEdges(inner)) return null
        return Span(MarkdownInline.Emphasis(parseInline(inner)), close + 1)
    }

    private fun linkSpan(text: String, index: Int): Span? {
        val textEnd = text.indexOf("](", index + 1)
        if (textEnd == -1) return null
        val urlEnd = text.indexOf(')', textEnd + 2)
        if (urlEnd == -1) return null
        val label = text.substring(index + 1, textEnd)
        val url = text.substring(textEnd + 2, urlEnd).trim()
        if (label.isEmpty() || url.isEmpty() || url.contains(' ')) return null
        return Span(MarkdownInline.Link(label, url), urlEnd + 1)
    }

    /** "2 * 3 * 4" is arithmetic, not emphasis: markers must hug their text. */
    private fun hasTightEdges(inner: String): Boolean =
        inner.isNotEmpty() && !inner.first().isWhitespace() && !inner.last().isWhitespace()
}
