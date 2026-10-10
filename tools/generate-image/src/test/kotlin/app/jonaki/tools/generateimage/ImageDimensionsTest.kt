package app.jonaki.tools.generateimage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageDimensionsTest {
    @Test
    fun readsAPngHeader() {
        assertEquals(ImageDimensions(1024, 576), ImageDimensions.of(png(1024, 576)))
    }

    @Test
    fun readsAJpegFrameHeaderAfterOtherSegments() {
        assertEquals(ImageDimensions(640, 480), ImageDimensions.of(jpeg(640, 480)))
    }

    @Test
    fun readsThreeKindsOfWebp() {
        assertEquals(ImageDimensions(300, 200), ImageDimensions.of(webpLossy(300, 200)))
        assertEquals(ImageDimensions(300, 200), ImageDimensions.of(webpLossless(300, 200)))
        assertEquals(ImageDimensions(1920, 1080), ImageDimensions.of(webpExtended(1920, 1080)))
    }

    @Test
    fun unknownOrCutDataGivesNull() {
        assertNull(ImageDimensions.of(ByteArray(0)))
        assertNull(ImageDimensions.of("hello".toByteArray()))
        assertNull(ImageDimensions.of(png(8, 8).copyOf(12)))
        assertNull(ImageDimensions.of(jpeg(8, 8).copyOf(10)))
    }

    @Test
    fun describesAsWidthByHeight() {
        assertEquals("1024x576 px", ImageDimensions(1024, 576).describe())
    }

    companion object {
        private fun bigEndian(value: Int, bytes: Int): ByteArray =
            ByteArray(bytes) { index -> (value shr (8 * (bytes - 1 - index))).toByte() }

        private fun littleEndian(value: Int, bytes: Int): ByteArray = bigEndian(value, bytes).reversedArray()

        fun png(width: Int, height: Int): ByteArray =
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
                bigEndian(13, 4) + "IHDR".toByteArray() + bigEndian(width, 4) + bigEndian(height, 4) + ByteArray(5)

        fun jpeg(width: Int, height: Int): ByteArray {
            val start = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
            val comment = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + bigEndian(6, 2) + "abcd".toByteArray()
            val frame = byteArrayOf(0xFF.toByte(), 0xC0.toByte()) + bigEndian(17, 2) + byteArrayOf(8) +
                bigEndian(height, 2) + bigEndian(width, 2) + ByteArray(10)
            return start + comment + frame
        }

        private fun riff(chunk: ByteArray): ByteArray =
            "RIFF".toByteArray() + littleEndian(chunk.size + 4, 4) + "WEBP".toByteArray() + chunk

        fun webpLossy(width: Int, height: Int): ByteArray {
            val data = byteArrayOf(0, 0, 0, 0x9D.toByte(), 0x01, 0x2A) + littleEndian(width, 2) + littleEndian(height, 2)
            return riff("VP8 ".toByteArray() + littleEndian(data.size, 4) + data)
        }

        fun webpLossless(width: Int, height: Int): ByteArray {
            val packed = (width - 1) or ((height - 1) shl 14)
            val data = byteArrayOf(0x2F) + littleEndian(packed, 4)
            return riff("VP8L".toByteArray() + littleEndian(data.size, 4) + data)
        }

        fun webpExtended(width: Int, height: Int): ByteArray {
            val data = ByteArray(4) + littleEndian(width - 1, 3) + littleEndian(height - 1, 3)
            return riff("VP8X".toByteArray() + littleEndian(data.size, 4) + data)
        }
    }
}
