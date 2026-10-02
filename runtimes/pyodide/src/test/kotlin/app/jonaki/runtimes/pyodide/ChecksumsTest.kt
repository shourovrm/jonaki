package app.jonaki.runtimes.pyodide

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ChecksumsTest {
    @Test
    fun hashesAFileAsLowercaseHex() {
        val file = File(Files.createTempDirectory("hash").toFile(), "abc.txt")
        file.writeText("abc")

        // The SHA-256 test vector for "abc" from FIPS 180-2.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksums.sha256Of(file))
    }

    @Test
    fun hashesBytes() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Checksums.sha256Of(ByteArray(0)),
        )
    }
}
