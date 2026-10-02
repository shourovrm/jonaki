package app.jonaki.tools.webfetch

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * Turns an HTML element into plain text that keeps the page's structure:
 * headings as "#" lines, list items as "-" lines, links as [text](url).
 * The model reads structure far better than one run-on line.
 */
internal object HtmlToText {
    private val blockTags = setOf(
        "p", "div", "section", "article", "main", "header", "footer", "aside", "blockquote",
        "ul", "ol", "table", "tr", "figure", "figcaption", "pre", "dl", "dt", "dd", "hr",
    )
    private val headingLevels = mapOf("h1" to 1, "h2" to 2, "h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6)
    private val skippedTags = setOf("script", "style", "noscript", "svg", "nav", "form", "button", "iframe")

    fun convert(root: Element): String {
        val builder = StringBuilder()
        NodeTraversor.traverse(TextCollector(builder), root)
        return tidy(builder.toString())
    }

    private class TextCollector(private val builder: StringBuilder) : NodeVisitor {
        private var skipDepth = 0
        private var preDepth = 0

        override fun head(node: Node, depth: Int) {
            if (node is Element) {
                val tag = node.normalName()
                if (tag in skippedTags) skipDepth += 1
                if (skipDepth > 0) return
                if (tag == "pre") preDepth += 1
                when {
                    tag in headingLevels -> builder.append("\n\n").append("#".repeat(headingLevels.getValue(tag))).append(' ')
                    tag == "li" -> builder.append("\n- ")
                    tag == "br" -> builder.append('\n')
                    tag == "td" || tag == "th" -> builder.append(" | ")
                    tag in blockTags -> builder.append("\n\n")
                }
                return
            }
            if (skipDepth > 0 || node !is TextNode) return
            val text = if (preDepth > 0) node.wholeText else node.text()
            if (text.isNotBlank()) builder.append(text) else if (text.isNotEmpty()) builder.append(' ')
        }

        override fun tail(node: Node, depth: Int) {
            if (node !is Element) return
            val tag = node.normalName()
            if (tag in skippedTags) {
                skipDepth -= 1
                return
            }
            if (skipDepth > 0) return
            if (tag == "pre") preDepth -= 1
            if (tag == "a") appendLinkTarget(node)
            if (tag in headingLevels || tag in blockTags) builder.append("\n\n")
        }

        private fun appendLinkTarget(link: Element) {
            val target = link.absUrl("href")
            val label = link.text()
            if (target.startsWith("http") && label.isNotBlank() && label != target) {
                builder.append(" (").append(target).append(')')
            }
        }
    }

    private fun tidy(text: String): String {
        val lines = text.lines().map { line -> line.replace(Regex("[ \\t]+"), " ").trim() }
        return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
