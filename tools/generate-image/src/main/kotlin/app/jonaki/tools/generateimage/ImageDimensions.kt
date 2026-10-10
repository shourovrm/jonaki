package app.jonaki.tools.generateimage

/**
 * The size of a picture read from its first bytes, so that the tool can tell
 * the model the pixel size without decoding the picture (a JVM module has no
 * bitmap decoder). Knows PNG, JPEG and WebP; anything else gives null.
 */
data class ImageDimensions(val width: Int, val height: Int) {
    fun describe(): String = "${width}x$height px"

    companion object {
        fun of(bytes: ByteArray): ImageDimensions? = when {
            isPng(bytes) -> pngSize(bytes)
            isJpeg(bytes) -> jpegSize(bytes)
            isWebp(bytes) -> webpSize(bytes)
            else -> null
        }

        private fun isPng(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()

        private fun isJpeg(bytes: ByteArray) = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

        private fun isWebp(bytes: ByteArray) =
            bytes.size >= 12 && ascii(bytes, 0, 4) == "RIFF" && ascii(bytes, 8, 4) == "WEBP"

        private fun pngSize(bytes: ByteArray): ImageDimensions? {
            // The IHDR chunk comes first: width and height are 4 bytes each, after the 8 byte signature and 8 byte chunk head.
            if (bytes.size < 24) return null
            return sizeOrNull(bigEndian(bytes, 16, 4), bigEndian(bytes, 20, 4))
        }

        private fun jpegSize(bytes: ByteArray): ImageDimensions? {
            var position = 2
            while (position + 4 <= bytes.size) {
                if (bytes[position] != 0xFF.toByte()) return null
                val marker = bytes[position + 1].toInt() and 0xFF
                val segmentLength = bigEndian(bytes, position + 2, 2)
                if (isFrameMarker(marker)) {
                    if (position + 9 > bytes.size) return null
                    return sizeOrNull(bigEndian(bytes, position + 7, 2), bigEndian(bytes, position + 5, 2))
                }
                position += 2 + segmentLength
            }
            return null
        }

        /** SOF0 to SOF15 carry the size; C4 (tables), C8 (reserved) and CC (arithmetic coding table) do not. */
        private fun isFrameMarker(marker: Int): Boolean = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC

        private fun webpSize(bytes: ByteArray): ImageDimensions? {
            if (bytes.size < 25) return null
            return when (ascii(bytes, 12, 4)) {
                "VP8 " -> if (bytes.size < 30) null else sizeOrNull(littleEndian(bytes, 26, 2) and 0x3FFF, littleEndian(bytes, 28, 2) and 0x3FFF)
                "VP8L" -> {
                    val packed = littleEndian(bytes, 21, 4)
                    sizeOrNull((packed and 0x3FFF) + 1, ((packed shr 14) and 0x3FFF) + 1)
                }
                "VP8X" -> if (bytes.size < 30) null else sizeOrNull(littleEndian(bytes, 24, 3) + 1, littleEndian(bytes, 27, 3) + 1)
                else -> null
            }
        }

        private fun sizeOrNull(width: Int, height: Int): ImageDimensions? =
            if (width > 0 && height > 0) ImageDimensions(width, height) else null

        private fun ascii(bytes: ByteArray, start: Int, length: Int) = String(bytes, start, length, Charsets.US_ASCII)

        private fun bigEndian(bytes: ByteArray, start: Int, length: Int): Int {
            var value = 0
            for (index in start until start + length) {
                value = (value shl 8) or (bytes[index].toInt() and 0xFF)
            }
            return value
        }

        private fun littleEndian(bytes: ByteArray, start: Int, length: Int): Int {
            var value = 0
            for (index in start + length - 1 downTo start) {
                value = (value shl 8) or (bytes[index].toInt() and 0xFF)
            }
            return value
        }
    }
}
