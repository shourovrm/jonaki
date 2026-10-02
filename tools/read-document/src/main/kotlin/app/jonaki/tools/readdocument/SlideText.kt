package app.jonaki.tools.readdocument

import java.io.File
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Reads a .pptx presentation slide by slide: the text of every shape, one
 * line per paragraph, then the speaker notes. Pictures and charts give no
 * text.
 */
internal object SlideText {
    private const val PRESENTATION_PART = "ppt/presentation.xml"
    private const val NOTES_RELATIONSHIP_SUFFIX = "/notesSlide"

    fun read(file: File, firstSlide: Int, slideLimit: Int): DocumentWindow {
        OfficePackage(file, "PowerPoint").use { officePackage ->
            val slideParts = slidePartsOf(officePackage)
            val lastSlide = minOf(slideParts.size, firstSlide + slideLimit - 1)
            val sections = mutableListOf<Section>()
            for (slideNumber in firstSlide..lastSlide) {
                val slidePart = slideParts[slideNumber - 1]
                sections += Section(slideNumber, "Slide $slideNumber", slideText(officePackage, slidePart))
            }
            return DocumentWindow("PowerPoint presentation", "slide", slideParts.size, sections)
        }
    }

    /** Slides in the order the presentation lists them, which need not match their file names. */
    private fun slidePartsOf(officePackage: OfficePackage): List<String> {
        val relationships = officePackage.relationships(PRESENTATION_PART)
        val parts = mutableListOf<String>()
        officePackage.parse(
            PRESENTATION_PART,
            object : DefaultHandler() {
                override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                    if (localName == "sldId") {
                        relationships[attributes.relationshipId()]?.let { relationship -> parts += relationship.target }
                    }
                }
            },
        )
        return parts
    }

    private fun slideText(officePackage: OfficePackage, slidePart: String): String {
        val slideHandler = ParagraphHandler(onlyBodyText = false)
        officePackage.parse(slidePart, slideHandler)
        val notesPart = officePackage.relationships(slidePart).values
            .firstOrNull { relationship -> relationship.type.endsWith(NOTES_RELATIONSHIP_SUFFIX) }
            ?.target
        val notesLines = if (notesPart != null && officePackage.has(notesPart)) {
            // A notes page also holds the slide picture and its number; only the body is the notes.
            val notesHandler = ParagraphHandler(onlyBodyText = true)
            officePackage.parse(notesPart, notesHandler)
            notesHandler.lines
        } else {
            emptyList()
        }
        if (notesLines.isEmpty()) {
            return slideHandler.lines.joinToString("\n")
        }
        return (slideHandler.lines + listOf("", "Notes:") + notesLines).joinToString("\n").trim()
    }

    /** Collects paragraphs ("a:p") shape by shape ("p:sp"); with [onlyBodyText], only body placeholders count. */
    private class ParagraphHandler(private val onlyBodyText: Boolean) : DefaultHandler() {
        val lines = mutableListOf<String>()
        private val shapeLines = mutableListOf<String>()
        private var shapeIsBody = false
        private var shapeDepth = 0
        private val paragraph = StringBuilder()
        private var insideText = false

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            when (localName) {
                "sp" -> {
                    shapeDepth += 1
                    if (shapeDepth == 1) {
                        shapeLines.clear()
                        shapeIsBody = false
                    }
                }
                "ph" -> if (attributes.valueOf("type") == "body") shapeIsBody = true
                "p" -> paragraph.setLength(0)
                "t" -> insideText = true
                "br" -> paragraph.append(' ')
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            when (localName) {
                "t" -> insideText = false
                "p" -> {
                    val text = paragraph.toString().trim()
                    if (text.isEmpty()) {
                        return
                    }
                    // Table cells and other text outside shapes go straight to the slide.
                    if (shapeDepth == 0) lines += text else shapeLines += text
                }
                "sp" -> {
                    shapeDepth -= 1
                    if (shapeDepth == 0 && (!onlyBodyText || shapeIsBody)) {
                        lines += shapeLines
                    }
                }
            }
        }

        override fun characters(characters: CharArray, start: Int, length: Int) {
            if (insideText) {
                paragraph.appendRange(characters, start, start + length)
            }
        }
    }
}
