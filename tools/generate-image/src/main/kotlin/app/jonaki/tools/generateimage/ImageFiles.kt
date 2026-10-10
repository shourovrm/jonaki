package app.jonaki.tools.generateimage

import java.util.Locale

/** The file ending of a picture generate_image saves; names and places are in core/tool-api (ImageFileNames). */
object ImageFiles {
    /**
     * The file ending for a picture: from the media type the service named,
     * else from the first bytes of the file. Null when it is not a picture
     * type the chat can show.
     */
    fun extensionFor(mediaType: String, bytes: ByteArray): String? {
        val type = mediaType.substringBefore(';').trim().lowercase(Locale.ROOT)
        return when (type) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> extensionFromHeader(bytes)
        }
    }

    private fun extensionFromHeader(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
        else -> null
    }
}
