package app.jonaki.core.toolapi

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputLimiterTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val limiter = OutputLimiter(threadFolder)

    @Test
    fun shortOutputIsReturnedUnchangedAndNothingIsSaved() {
        val limited = limiter.limit(text = "one\ntwo", maxCharacters = 100, sourceName = "web_fetch")
        assertEquals("one\ntwo", limited)
        assertFalse(File(threadFolder, OutputLimiter.SPILL_FOLDER).exists())
    }

    @Test
    fun longOutputIsCutAtALineAndTheRestIsSavedToAFile() {
        val lines = (1..100).map { number -> "line $number" }
        val text = lines.joinToString("\n")

        val limited = limiter.limit(text = text, maxCharacters = 50, sourceName = "web_fetch")

        val spillFile = File(threadFolder, "work/tool-output/web_fetch-1.txt")
        assertEquals(text, spillFile.readText())
        assertTrue(limited.startsWith("line 1\nline 2\n"))
        // The visible part ends on a whole line, never in the middle of one.
        val visibleLines = limited.substringBefore("\n\n[").lines()
        assertTrue(visibleLines.all { line -> line in lines })
        val nextLine = visibleLines.size + 1
        assertTrue(limited.contains("lines 1-${visibleLines.size} of 100"))
        assertTrue(
            limited.contains("Use read_file path=\"work/tool-output/web_fetch-1.txt\" offset=$nextLine to continue."),
        )
    }

    @Test
    fun eachSpillGetsItsOwnFile() {
        val text = "x\n".repeat(200)
        limiter.limit(text = text, maxCharacters = 10, sourceName = "search_files")
        val second = limiter.limit(text = text, maxCharacters = 10, sourceName = "search_files")
        assertTrue(File(threadFolder, "work/tool-output/search_files-2.txt").exists())
        assertTrue(second.contains("search_files-2.txt"))
    }

    @Test
    fun aSingleHugeLineIsCutAtTheCharacterLimit() {
        val text = "a".repeat(1_000)
        val limited = limiter.limit(text = text, maxCharacters = 100, sourceName = "web_fetch")
        assertEquals("a".repeat(100), limited.substringBefore("\n\n["))
        assertTrue(limited.contains("100 of 1000 characters"))
    }

    @Test
    fun limitIntoSavesTheWholeTextAtTheGivenPath() {
        val folder = java.nio.file.Files.createTempDirectory("thread").toFile()
        val text = (1..100).joinToString("\n") { number -> "line $number" }

        val limited = OutputLimiter(folder).limitInto(text, maxCharacters = 50, spillPath = "work/delegations/d1/scout.md")

        assertEquals(text, java.io.File(folder, "work/delegations/d1/scout.md").readText())
        assertTrue(limited.contains("read_file path=\"work/delegations/d1/scout.md\" offset="))
    }

    @Test
    fun theSpillPathAndTheKeptPartCanBeReadBackFromACutText() {
        val text = (1..100).joinToString("\n") { number -> "line $number" }
        val byLines = limiter.limit(text = text, maxCharacters = 50, sourceName = "run_code")
        val byCharacters = limiter.limit(text = "a".repeat(1_000), maxCharacters = 100, sourceName = "run_code")

        assertEquals("work/tool-output/run_code-1.txt", OutputLimiter.spillPathOf(byLines))
        assertEquals("work/tool-output/run_code-2.txt", OutputLimiter.spillPathOf(byCharacters))
        assertTrue(text.startsWith(OutputLimiter.visiblePartOf(byLines)))
        assertEquals("a".repeat(100), OutputLimiter.visiblePartOf(byCharacters))
    }

    @Test
    fun aTextThatWasNotCutHasNoSpillPath() {
        assertNull(OutputLimiter.spillPathOf("Printed:\nFull output saved to nowhere. Fine"))
        assertEquals("one\ntwo", OutputLimiter.visiblePartOf("one\ntwo"))
    }
}
