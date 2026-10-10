package app.jonaki.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncodedJpegTest {
    private fun bytesOf(vararg values: Int) = ByteArray(values.size) { index -> values[index].toByte() }

    @Test
    fun anEmptyEncodeIsNotAJpeg() {
        assertFalse(EncodedJpeg.isComplete(ByteArray(0)))
    }

    @Test
    fun aCutOffFileIsNotAJpeg() {
        assertFalse(EncodedJpeg.isComplete(bytesOf(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10)))
    }

    @Test
    fun aPngIsNotAJpeg() {
        assertFalse(EncodedJpeg.isComplete(bytesOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0xFF, 0xD9)))
    }

    @Test
    fun aWholeJpegPasses() {
        assertTrue(EncodedJpeg.isComplete(bytesOf(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0xFF, 0xD9)))
    }
}
