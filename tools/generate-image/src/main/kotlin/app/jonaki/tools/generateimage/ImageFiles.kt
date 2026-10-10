package app.jonaki.tools.generateimage

import app.jonaki.core.toolapi.ImageFormats
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

    private fun extensionFromHeader(bytes: ByteArray): String? = when (ImageFormats.mediaTypeOfHeader(bytes)) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        else -> null
    }
}
