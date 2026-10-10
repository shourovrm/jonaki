package app.jonaki.files

/**
 * A check that bytes are a whole JPEG before they go to a model or into the
 * image cache. Bitmap.compress reports failure only through its return
 * value, and a failed encode leaves an empty byte array; sent as it is, that
 * becomes a data URL with no data, which a model reports as a blank picture.
 */
object EncodedJpeg {
    private val START_MARKER = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val END_MARKER = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    fun isComplete(bytes: ByteArray): Boolean {
        if (bytes.size < START_MARKER.size + END_MARKER.size) {
            return false
        }
        val startsRight = START_MARKER.indices.all { index -> bytes[index] == START_MARKER[index] }
        val endsRight = END_MARKER.indices.all { index -> bytes[bytes.size - END_MARKER.size + index] == END_MARKER[index] }
        return startsRight && endsRight
    }
}
