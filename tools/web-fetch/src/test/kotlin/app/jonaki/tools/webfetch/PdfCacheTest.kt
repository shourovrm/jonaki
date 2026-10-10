package app.jonaki.tools.webfetch

import java.io.File
import java.nio.file.Files
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfCacheTest {
    private val folder: File = Files.createTempDirectory("pdf-cache").toFile()
    private var now = 1_000_000L
    private val cache = PdfCache(folder) { now }

    private fun save(url: String, content: String, byteLimit: Long = 1_000): File =
        cache.save(url, Buffer().writeUtf8(content), byteLimit)

    @Test
    fun aSavedCopyIsFoundByItsAddressAndNamedByAHash() {
        val copy = save("https://example.com/../../secret.pdf", "%PDF-one")

        assertEquals(copy, cache.find("https://example.com/../../secret.pdf"))
        assertEquals("%PDF-one", copy.readText())
        assertEquals(folder, copy.parentFile)
        assertEquals(true, Regex("[0-9a-f]{64}\\.pdf").matches(copy.name))
        assertNull(cache.find("https://example.com/other.pdf"))
    }

    @Test
    fun aCopyOlderThanAnHourIsNotUsed() {
        save("https://example.com/a.pdf", "%PDF-one")

        now += 61L * 60 * 1000

        assertNull(cache.find("https://example.com/a.pdf"))
    }

    @Test
    fun aPdfOverTheLimitLeavesNoFile() {
        assertThrows(PdfTooLargeException::class.java) {
            save("https://example.com/big.pdf", "x".repeat(200_000), byteLimit = 100_000)
        }

        assertEquals(emptyList<File>(), folder.listFiles().orEmpty().toList())
    }

    @Test
    fun onlyTheNewestFourCopiesStay() {
        for (number in 1..6) {
            now += 1_000
            save("https://example.com/$number.pdf", "%PDF-$number")
        }

        assertEquals(4, folder.listFiles().orEmpty().size)
        assertNull(cache.find("https://example.com/2.pdf"))
        assertEquals("%PDF-6", cache.find("https://example.com/6.pdf")?.readText())
    }
}
