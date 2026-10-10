package app.jonaki.tools.generatevectorimage

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Decides whether the bytes a service sent are an SVG document, before anything is saved. */
object SvgCheck {
    /** Over this size an SVG is refused; a logo or icon is a few kilobytes, so a huge one is a mistake or an attack. */
    const val MAX_BYTES: Int = 2 * 1024 * 1024

    /** The bytes as text, or null when they are not valid UTF-8 (which also rules out a PNG or JPEG). */
    fun decode(bytes: ByteArray): String? {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (notUtf8: CharacterCodingException) {
            null
        }
    }

    /**
     * True when, after an optional byte order mark, white space, an XML
     * declaration or other processing instruction, comments and a DOCTYPE,
     * the document's first element is `<svg`. A DOCTYPE is skipped only so
     * that the sanitiser can refuse it with its own reason.
     */
    fun looksLikeSvg(text: String): Boolean {
        var position = if (text.startsWith(BYTE_ORDER_MARK)) 1 else 0
        while (position < text.length) {
            val character = text[position]
            position = when {
                character.isWhitespace() -> position + 1
                text.startsWith("<?", position) -> endAfter(text, "?>", position)
                text.startsWith("<!--", position) -> endAfter(text, "-->", position + 4)
                text.startsWith("<!DOCTYPE", position, ignoreCase = true) -> endOfDoctype(text, position)
                else -> return startsRootElement(text, position)
            }
            if (position < 0) {
                return false
            }
        }
        return false
    }

    private const val BYTE_ORDER_MARK = '﻿'

    private fun startsRootElement(text: String, position: Int): Boolean {
        if (!text.startsWith("<svg", position)) {
            return false
        }
        val after = text.getOrNull(position + 4) ?: return false
        return after.isWhitespace() || after == '>' || after == '/'
    }

    /** The index after [marker], or -1 when the marker never comes. */
    private fun endAfter(text: String, marker: String, from: Int): Int {
        val found = text.indexOf(marker, from)
        return if (found < 0) -1 else found + marker.length
    }

    /** A DOCTYPE with an internal subset ends at "]>", one without at the first ">". */
    private fun endOfDoctype(text: String, position: Int): Int {
        val firstClose = text.indexOf('>', position)
        val subsetOpen = text.indexOf('[', position)
        val hasSubset = subsetOpen >= 0 && (firstClose < 0 || subsetOpen < firstClose)
        if (hasSubset) {
            return endAfter(text, "]>", subsetOpen)
        }
        return if (firstClose < 0) -1 else firstClose + 1
    }
}
