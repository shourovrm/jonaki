package app.jonaki.tools.readdocument

import java.io.Closeable
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/**
 * A .docx, .xlsx or .pptx file: a zip of XML parts linked by relationship
 * parts (Office Open XML). Read with java.util.zip and the platform's SAX
 * parser, so no Office library is needed (D-052).
 */
internal class OfficePackage(private val file: File, private val kindName: String) : Closeable {
    private val zip: ZipFile = try {
        ZipFile(file)
    } catch (notZip: ZipException) {
        if (isCompoundFile(file)) {
            // Office saves password-protected files, and the old formats, as one of these containers.
            throw UnreadableDocumentException(
                "${file.name} is locked with a password, or is an old Office file under a new ending",
                "Ask the user for a copy without a password, saved as .docx, .xlsx, .pptx or PDF.",
            )
        }
        throw UnreadableDocumentException(
            "${file.name} could not be read as a $kindName file",
            "The file may be damaged or have the wrong ending; ask the user for another copy.",
        )
    }

    fun has(partName: String): Boolean = zip.getEntry(partName) != null

    /** Streams one XML part through [handler]; a missing part is an error the model can explain. */
    fun parse(partName: String, handler: DefaultHandler) {
        val entry = zip.getEntry(partName)
            ?: throw UnreadableDocumentException(
                "${file.name} has no $partName, so it is not a complete $kindName file",
                "Ask the user to save it again from the program that made it.",
            )
        try {
            zip.getInputStream(entry).use { stream -> parser().parse(LimitedStream(stream, MAX_PART_BYTES), handler) }
        } catch (badXml: SAXException) {
            throw UnreadableDocumentException(
                "${file.name} has broken XML in $partName",
                "Ask the user to save it again from the program that made it.",
            )
        }
    }

    /** Relationship id to the part it points at, for example "rId2" to "xl/worksheets/sheet1.xml". */
    fun relationships(partName: String): Map<String, Relationship> {
        val folder = partName.substringBeforeLast('/', missingDelimiterValue = "")
        val relationshipsPart = (if (folder.isEmpty()) "" else "$folder/") + "_rels/" + partName.substringAfterLast('/') + ".rels"
        if (!has(relationshipsPart)) {
            return emptyMap()
        }
        val found = mutableMapOf<String, Relationship>()
        parse(
            relationshipsPart,
            object : DefaultHandler() {
                override fun startElement(uri: String, localName: String, qName: String, attributes: org.xml.sax.Attributes) {
                    if (localName != "Relationship" || attributes.getValue("TargetMode") == "External") {
                        return
                    }
                    val id = attributes.getValue("Id") ?: return
                    val target = attributes.getValue("Target") ?: return
                    found[id] = Relationship(attributes.getValue("Type").orEmpty(), resolve(folder, target))
                }
            },
        )
        return found
    }

    override fun close() {
        zip.close()
    }

    private fun parser() = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        // Office parts never declare a DOCTYPE; refusing one rules out entity tricks. Not every parser knows the feature.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }.newSAXParser()

    /** Targets are relative to the part's folder ("../slides/slide1.xml") or absolute from the zip's root. */
    private fun resolve(folder: String, target: String): String {
        if (target.startsWith("/")) {
            return target.removePrefix("/")
        }
        val segments = if (folder.isEmpty()) mutableListOf() else folder.split('/').toMutableList()
        for (segment in target.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull()
                else -> segments += segment
            }
        }
        return segments.joinToString("/")
    }

    /** Stops a zip bomb: a small file whose parts unpack to gigabytes. */
    private class LimitedStream(input: InputStream, private val limitBytes: Long) : FilterInputStream(input) {
        private var readBytes = 0L

        override fun read(): Int {
            val byte = super.read()
            if (byte >= 0) {
                count(1)
            }
            return byte
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) {
                count(count)
            }
            return count
        }

        private fun count(bytes: Int) {
            readBytes += bytes
            if (readBytes > limitBytes) {
                throw IOException("a part unpacks to more than ${limitBytes / (1024 * 1024)} MB")
            }
        }
    }

    companion object {
        private const val MAX_PART_BYTES = 64L * 1024 * 1024

        /** The first bytes of an OLE compound file, the container of .doc, .xls and .ppt. */
        private val COMPOUND_FILE_SIGNATURE = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
        )

        private fun isCompoundFile(file: File): Boolean {
            val start = ByteArray(COMPOUND_FILE_SIGNATURE.size)
            val count = file.inputStream().use { stream -> stream.read(start) }
            return count == start.size && start.contentEquals(COMPOUND_FILE_SIGNATURE)
        }
    }
}

/**
 * An attribute by its name without prefix: Office writes w:val, r:id and so
 * on, and a namespace-aware parser reports them by local name.
 */
internal fun org.xml.sax.Attributes.valueOf(localName: String): String? {
    for (index in 0 until length) {
        if (getLocalName(index) == localName) {
            return getValue(index)
        }
    }
    return null
}

/**
 * The r:id of an element that points at another part. Not [valueOf]: a
 * slide entry has both id="256" and r:id="rId7", and only the second has a
 * namespace.
 */
internal fun org.xml.sax.Attributes.relationshipId(): String? {
    for (index in 0 until length) {
        if (getLocalName(index) == "id" && getURI(index).isNotEmpty()) {
            return getValue(index)
        }
    }
    return null
}

internal data class Relationship(
    /** A URL ending in the kind, for example ".../relationships/worksheet". */
    val type: String,
    /** Path inside the zip. */
    val target: String,
)
