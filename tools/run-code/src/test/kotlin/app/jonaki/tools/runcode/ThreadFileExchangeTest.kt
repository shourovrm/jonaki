package app.jonaki.tools.runcode

import app.jonaki.core.runtimeapi.CodeRunLimits
import app.jonaki.core.runtimeapi.OutputFile
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadFileExchangeTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val exchange = ThreadFileExchange(threadFolder)

    private fun threadFile(path: String, content: String): File {
        val file = File(threadFolder, path)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    private fun bigFile(path: String, sizeBytes: Long): File {
        val file = File(threadFolder, path)
        file.parentFile.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(sizeBytes) }
        return file
    }

    private fun readyInputs(vararg paths: String): List<String> {
        val selection = exchange.inputsFor(paths.toList())
        assertTrue(selection.toString(), selection is InputSelection.Ready)
        return (selection as InputSelection.Ready).files.map { it.relativePath }
    }

    private fun refusal(vararg paths: String): String {
        val selection = exchange.inputsFor(paths.toList())
        assertTrue(selection.toString(), selection is InputSelection.Refused)
        return (selection as InputSelection.Refused).problem
    }

    @Test
    fun offersNamedFilesAtTheirThreadPaths() {
        threadFile("inbox/sales.csv", "a,b")

        assertEquals(listOf("inbox/sales.csv"), readyInputs("inbox/sales.csv"))
    }

    @Test
    fun aFolderOffersEveryFileInside() {
        threadFile("inbox/a.csv", "1")
        threadFile("inbox/deep/b.csv", "2")

        assertEquals(listOf("inbox/a.csv", "inbox/deep/b.csv"), readyInputs("inbox"))
    }

    @Test
    fun aFileNamedTwiceIsOfferedOnce() {
        threadFile("inbox/a.csv", "1")

        assertEquals(listOf("inbox/a.csv"), readyInputs("inbox/a.csv", "./inbox/a.csv", "inbox"))
    }

    @Test
    fun refusesPathsOutsideTheThread() {
        assertTrue(refusal("../other/secret.txt").contains("outside the thread folder"))
    }

    @Test
    fun refusesMissingFiles() {
        assertTrue(refusal("inbox/nothing.csv").contains("inbox/nothing.csv does not exist"))
    }

    @Test
    fun refusesAFileOverTheSizeCap() {
        bigFile("inbox/huge.bin", CodeRunLimits.MAX_FILE_BYTES + 1)

        assertTrue(refusal("inbox/huge.bin").contains("inbox/huge.bin is over 25 MB"))
    }

    @Test
    fun refusesFilesOverTheTotalCap() {
        bigFile("inbox/one.bin", CodeRunLimits.MAX_FILE_BYTES)
        bigFile("inbox/two.bin", CodeRunLimits.MAX_FILE_BYTES)
        bigFile("inbox/three.bin", 1)

        assertTrue(refusal("inbox").contains("over 50 MB together"))
    }

    @Test
    fun savesNewFilesUnderWorkAndArtifacts() {
        val report = exchange.save(
            listOf(
                OutputFile("work/summary.csv", "north,15".toByteArray()),
                OutputFile("artifacts/chart.svg", "<svg/>".toByteArray()),
            ),
        )

        assertEquals(listOf("work/summary.csv", "artifacts/chart.svg"), report.saved.map { it.relativePath })
        assertTrue(report.saved.all { it.wasNew })
        assertEquals("north,15", File(threadFolder, "work/summary.csv").readText())
        assertEquals("<svg/>", File(threadFolder, "artifacts/chart.svg").readText())
    }

    @Test
    fun replacesChangedFilesAndSkipsUnchangedOnes() {
        threadFile("work/changed.txt", "old")
        threadFile("work/same.txt", "same")

        val report = exchange.save(
            listOf(
                OutputFile("work/changed.txt", "new".toByteArray()),
                OutputFile("work/same.txt", "same".toByteArray()),
            ),
        )

        assertEquals(listOf("work/changed.txt"), report.saved.map { it.relativePath })
        assertFalse(report.saved.single().wasNew)
        assertEquals("new", File(threadFolder, "work/changed.txt").readText())
    }

    @Test
    fun neverChangesTheInbox() {
        threadFile("inbox/sales.csv", "original")

        val report = exchange.save(listOf(OutputFile("inbox/sales.csv", "edited".toByteArray())))

        assertTrue(report.saved.isEmpty())
        assertEquals("inbox/sales.csv", report.refused.single().relativePath)
        assertEquals("original", File(threadFolder, "inbox/sales.csv").readText())
    }

    @Test
    fun refusesFilesAtTheThreadRootAndTricksWithDots() {
        threadFile("inbox/sales.csv", "original")

        val report = exchange.save(
            listOf(
                OutputFile("notes.md", "x".toByteArray()),
                OutputFile("work/../inbox/sales.csv", "edited".toByteArray()),
                OutputFile("work/../../escape.txt", "x".toByteArray()),
            ),
        )

        assertTrue(report.saved.isEmpty())
        assertEquals(3, report.refused.size)
        assertEquals("original", File(threadFolder, "inbox/sales.csv").readText())
        assertFalse(File(threadFolder.parentFile, "escape.txt").exists())
    }

    @Test
    fun refusesAnOutputFileOverTheSizeCap() {
        val report = exchange.save(
            listOf(OutputFile("work/huge.bin", ByteArray((CodeRunLimits.MAX_FILE_BYTES + 1).toInt()))),
        )

        assertTrue(report.saved.isEmpty())
        assertTrue(report.refused.single().reason.contains("over 25 MB"))
        assertFalse(File(threadFolder, "work/huge.bin").exists())
    }

    @Test
    fun refusesToReplaceAFolderWithAFile() {
        File(threadFolder, "work/data").mkdirs()

        val report = exchange.save(listOf(OutputFile("work/data", "x".toByteArray())))

        assertTrue(report.refused.single().reason.contains("folder"))
        assertTrue(File(threadFolder, "work/data").isDirectory)
    }
}
