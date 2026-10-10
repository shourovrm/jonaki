package app.jonaki.core.toolapi

/** Recognises the picture types image models take, from the first bytes of a file. */
object ImageFormats {
    /** "image/png", "image/jpeg" or "image/webp"; null for anything else, so a wrong file ending proves nothing. */
    fun mediaTypeOfHeader(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size >= 12 && ascii(bytes, 0) == "RIFF" && ascii(bytes, 8) == "WEBP" -> "image/webp"
        else -> null
    }

    /** True when the start of the file is the start of an SVG document: an XML declaration or an svg tag. */
    fun looksLikeSvg(bytes: ByteArray): Boolean {
        val start = String(bytes, 0, minOf(bytes.size, SVG_SNIFF_BYTES), Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r', '\t')
        return start.startsWith("<svg") || start.startsWith("<?xml") || start.startsWith("<!DOCTYPE svg")
    }

    private fun ascii(bytes: ByteArray, offset: Int): String = String(bytes, offset, 4, Charsets.US_ASCII)

    private const val SVG_SNIFF_BYTES = 512
}
