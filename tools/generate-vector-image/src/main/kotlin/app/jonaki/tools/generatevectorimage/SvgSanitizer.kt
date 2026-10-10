package app.jonaki.tools.generatevectorimage

import java.io.StringReader
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException

/** What [SvgSanitizer.sanitize] made of a document. */
sealed interface SvgSanitizing {
    /**
     * [svg] is the rewritten document. [removed] names, in plain words, what
     * was taken out, for example "script element"; it is empty for a clean
     * document. [width] and [height] come from the root element's width and
     * height, else from its viewBox; null when it states neither.
     */
    data class Clean(
        val svg: String,
        val width: Double?,
        val height: Double?,
        val removed: List<String>,
    ) : SvgSanitizing

    /** [reason] is short and plain; the document must not be saved or shown. */
    data class Rejected(val reason: String) : SvgSanitizing
}

/**
 * Rewrites an SVG so that it cannot run code or reach outside the document,
 * before it is saved and shown. It parses with the JDK's XML parser and
 * writes the tree back out; it does not edit the raw text.
 *
 * It removes: script, foreignObject and other active or HTML elements;
 * elements of any other XML namespace; every attribute whose name starts with
 * "on"; href, xlink:href and src attributes that are not `#id` or a
 * `data:image/` URI (a `use` element may point only to `#id`); a `use`,
 * `image` or `feImage` element that loses its address; animation that sets an
 * address or an event handler; `url(...)` outside the document, `@import` and
 * calls of file-loading CSS functions in style elements and attributes; comments,
 * processing instructions (such as xml-stylesheet) and `xml:base`.
 *
 * It refuses (does not rewrite) a document with a DOCTYPE or an ENTITY
 * declaration, one that is not well-formed, one whose root is not an SVG
 * element, and one nested deeper than [MAX_DEPTH].
 */
object SvgSanitizer {
    const val MAX_DEPTH = 100

    private const val SVG_NAMESPACE = "http://www.w3.org/2000/svg"

    private val blockedElements = setOf(
        "script", "foreignobject", "iframe", "embed", "object", "applet", "link", "meta", "base",
        "audio", "video", "canvas", "form", "input", "button", "textarea", "select", "html", "head", "body",
        "frame", "frameset", "handler", "listener",
    )

    /** Elements that draw or show a file by address; with no allowed address they have nothing to draw. */
    private val elementsWithAddress = setOf("use", "image", "feimage")

    private val animationElements = setOf("set", "animate", "animatetransform", "animatemotion")

    fun sanitize(svgText: String): SvgSanitizing {
        // A text check before the parser: a DOCTYPE starts with this literal text in any well-formed document,
        // and refusing it here does not depend on which XML parser the phone has.
        if (svgText.contains("<!DOCTYPE", ignoreCase = true) || svgText.contains("<!ENTITY", ignoreCase = true)) {
            return SvgSanitizing.Rejected("it has a DOCTYPE or ENTITY declaration, which can expand into huge text or read files")
        }
        return try {
            rewrite(svgText)
        } catch (problem: SAXException) {
            SvgSanitizing.Rejected("it is not well-formed XML (${problem.message?.take(MAX_PROBLEM_CHARACTERS)})")
        } catch (problem: TooDeep) {
            SvgSanitizing.Rejected("its elements are nested more than $MAX_DEPTH levels deep")
        } catch (problem: StackOverflowError) {
            SvgSanitizing.Rejected("its elements are nested too deeply")
        } catch (problem: java.io.IOException) {
            SvgSanitizing.Rejected("it could not be read")
        } catch (problem: javax.xml.parsers.ParserConfigurationException) {
            SvgSanitizing.Rejected("the XML parser could not be set up safely")
        }
    }

    private const val MAX_PROBLEM_CHARACTERS = 120

    private class TooDeep : RuntimeException()

    private fun rewrite(svgText: String): SvgSanitizing {
        // A byte order mark or white space before the XML declaration is a parse error, though SvgCheck accepts it.
        val document = parse(svgText.removePrefix("\uFEFF").trimStart())
        val root = document.documentElement
        val isSvgRoot = localNameOf(root) == "svg" && (root.namespaceURI == null || root.namespaceURI == SVG_NAMESPACE)
        if (!isSvgRoot) {
            return SvgSanitizing.Rejected("its root element is not an svg element")
        }
        val removed = mutableListOf<String>()
        val output = StringBuilder()
        val usedPrefixes = mutableMapOf<String, String>()
        writeElement(root, output, removed, usedPrefixes, depth = 0)
        declareMissingPrefixes(output, root, usedPrefixes)
        val width = lengthOf(root.getAttribute("width"))
        val height = lengthOf(root.getAttribute("height"))
        val viewBox = viewBoxSize(root.getAttribute("viewBox"))
        return SvgSanitizing.Clean(
            svg = output.toString(),
            width = width ?: viewBox?.first,
            height = height ?: viewBox?.second,
            removed = removed,
        )
    }

    private fun parse(svgText: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        // Each setting below is also made by the DOCTYPE check above; a parser that does not know one of
        // them (Android's does not know most) must not stop the feature, so a refusal is ignored here.
        hardenWith { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        hardenWith { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        hardenWith { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        hardenWith { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        hardenWith { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        hardenWith { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
        hardenWith { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        hardenWith { factory.isXIncludeAware = false }
        hardenWith { factory.isExpandEntityReferences = false }
        val builder = factory.newDocumentBuilder()
        // Anything the parser would fetch from outside is refused, not skipped.
        builder.setEntityResolver { _, _ -> throw SAXException("an external resource was requested") }
        builder.setErrorHandler(FailOnEveryProblem)
        return builder.parse(InputSource(StringReader(svgText)))
    }

    private fun hardenWith(setting: () -> Unit) {
        try {
            setting()
        } catch (unsupported: Exception) {
            // See parse(): the text check already refuses what this setting would refuse.
        }
    }

    private object FailOnEveryProblem : ErrorHandler {
        override fun warning(problem: SAXParseException) = Unit

        override fun error(problem: SAXParseException): Unit = throw problem

        override fun fatalError(problem: SAXParseException): Unit = throw problem
    }

    private fun writeNode(node: Node, output: StringBuilder, removed: MutableList<String>, usedPrefixes: MutableMap<String, String>, depth: Int) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> writeElement(node as Element, output, removed, usedPrefixes, depth)
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> output.append(escapedText(node.nodeValue))
            // Comments and processing instructions carry nothing that draws; xml-stylesheet can load a file.
            else -> Unit
        }
    }

    private fun writeElement(
        element: Element,
        output: StringBuilder,
        removed: MutableList<String>,
        usedPrefixes: MutableMap<String, String>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) {
            throw TooDeep()
        }
        val name = localNameOf(element)
        val reasonToDrop = reasonToDropElement(element, name)
        if (reasonToDrop != null) {
            removed += reasonToDrop
            return
        }
        val attributes = keptAttributes(element, name, removed, usedPrefixes)
        if (attributes == null) {
            removed += "$name element without a usable address"
            return
        }
        rememberPrefix(element.prefix, element.namespaceURI, usedPrefixes)
        output.append('<').append(element.nodeName)
        if (depth == 0 && attributes.none { (attributeName, _) -> attributeName == "xmlns" }) {
            // A file with no xmlns is not an image to a browser; the root is an svg element by the check above.
            output.append(" xmlns=\"").append(SVG_NAMESPACE).append('"')
        }
        for ((attributeName, attributeValue) in attributes) {
            output.append(' ').append(attributeName).append("=\"").append(escapedAttribute(attributeValue)).append('"')
        }
        output.append('>')
        if (name == "style") {
            output.append(escapedText(SvgStyles.cleanCss(element.textContent.orEmpty())))
        } else {
            var child = element.firstChild
            while (child != null) {
                writeNode(child, output, removed, usedPrefixes, depth + 1)
                child = child.nextSibling
            }
        }
        output.append("</").append(element.nodeName).append('>')
    }

    private fun reasonToDropElement(element: Element, name: String): String? {
        val namespace = element.namespaceURI
        if (namespace != null && namespace != SVG_NAMESPACE) {
            return "${element.nodeName} element (not part of SVG)"
        }
        if (name in blockedElements) {
            return "$name element"
        }
        if (name in animationElements && animatesAddressOrHandler(element)) {
            return "$name element that sets an address or a handler"
        }
        return null
    }

    private fun animatesAddressOrHandler(element: Element): Boolean {
        val target = element.getAttribute("attributeName").substringAfterLast(':').trim().lowercase(Locale.ROOT)
        return target == "href" || target == "src" || target.startsWith("on")
    }

    /**
     * The attributes to write, as name and value pairs. Null when the element
     * is a `use`, `image` or `feImage` that had an address and lost it.
     */
    private fun keptAttributes(
        element: Element,
        elementName: String,
        removed: MutableList<String>,
        usedPrefixes: MutableMap<String, String>,
    ): List<Pair<String, String>>? {
        val kept = mutableListOf<Pair<String, String>>()
        var lostAddress = false
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            val attributeName = attribute.nodeName
            val local = localNameOf(attribute)
            val value = attribute.nodeValue.orEmpty()
            rememberPrefix(attribute.prefix, attribute.namespaceURI, usedPrefixes)
            when {
                attributeName == "xmlns" || attributeName.startsWith("xmlns:") -> kept += attributeName to value
                local.startsWith("on") -> removed += "event handler $attributeName"
                attributeName == "xml:base" -> removed += "xml:base attribute"
                local == "href" || local == "src" -> {
                    if (isAllowedAddress(value, elementName)) {
                        kept += attributeName to value
                    } else {
                        removed += "$attributeName pointing outside the document"
                        lostAddress = true
                    }
                }
                local == "style" -> {
                    val cleaned = SvgStyles.cleanCss(value)
                    if (cleaned != value) {
                        removed += "outside address in style attribute"
                    }
                    kept += attributeName to cleaned
                }
                else -> {
                    val cleaned = SvgStyles.neutraliseUrls(value)
                    if (cleaned != value) {
                        removed += "outside address in $attributeName attribute"
                    }
                    kept += attributeName to cleaned
                }
            }
        }
        if (lostAddress && elementName in elementsWithAddress) {
            return null
        }
        return kept
    }

    private fun rememberPrefix(prefix: String?, namespaceUri: String?, usedPrefixes: MutableMap<String, String>) {
        if (prefix == null || namespaceUri == null || prefix == "xmlns" || prefix == "xml") {
            return
        }
        usedPrefixes[prefix] = namespaceUri
    }

    /**
     * Writes a declaration on the root for each prefix the output uses and the
     * root does not declare. A parser that keeps every xmlns attribute (the
     * JDK's does) makes this a no-op for the usual file; one that drops them
     * would otherwise leave `xlink:href` with an unbound prefix, which is not
     * well-formed XML.
     */
    private fun declareMissingPrefixes(output: StringBuilder, root: Element, usedPrefixes: Map<String, String>) {
        val insertAt = 1 + root.nodeName.length
        val missing = usedPrefixes.filterKeys { prefix -> !root.hasAttribute("xmlns:$prefix") }
        val declarations = missing.entries.joinToString("") { (prefix, uri) -> " xmlns:$prefix=\"${escapedAttribute(uri)}\"" }
        output.insert(insertAt, declarations)
    }

    /** `use` may point only inside the document; other elements may also carry a data:image/ URI. */
    private fun isAllowedAddress(value: String, elementName: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.startsWith("#")) {
            return true
        }
        return elementName != "use" && SvgStyles.isInsideDocument(trimmed)
    }

    private fun localNameOf(node: Node): String {
        val local = node.localName ?: node.nodeName.substringAfter(':')
        return local.lowercase(Locale.ROOT)
    }

    private fun escapedText(text: String?): String =
        text.orEmpty().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapedAttribute(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace("\"", "&quot;")
        .replace("\n", "&#10;")
        .replace("\r", "&#13;")
        .replace("\t", "&#9;")

    private val lengthPattern = Regex("^\\s*([0-9]*\\.?[0-9]+)\\s*(px|pt|pc|mm|cm|in|em|ex)?\\s*$", RegexOption.IGNORE_CASE)

    /** A plain number or a length with a unit; a percentage or nothing gives null, since it has no size of its own. */
    private fun lengthOf(text: String): Double? {
        val match = lengthPattern.matchEntire(text) ?: return null
        return match.groupValues[1].toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    private fun viewBoxSize(text: String): Pair<Double, Double>? {
        val numbers = text.trim().split(Regex("[\\s,]+")).mapNotNull { part -> part.toDoubleOrNull() }
        if (numbers.size != 4 || numbers[2] <= 0.0 || numbers[3] <= 0.0) {
            return null
        }
        return numbers[2] to numbers[3]
    }
}
