package app.jonaki.tools.generatevideo

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFilesTest {
    private val mp4Header = byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray() + ByteArray(4)

    @Test
    fun theEndingComesFromTheMediaTypeThenFromTheFirstBytes() {
        assertEquals("mp4", VideoFiles.extensionFor("video/mp4", ByteArray(0)))
        assertEquals("webm", VideoFiles.extensionFor("video/webm; codecs=vp9", ByteArray(0)))
        assertEquals("mov", VideoFiles.extensionFor("video/quicktime", ByteArray(0)))
        assertEquals("mp4", VideoFiles.extensionFor("application/octet-stream", mp4Header))
        assertEquals("webm", VideoFiles.extensionFor("", byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())))
        assertNull(VideoFiles.extensionFor("text/html", "<html>".toByteArray()))
    }

    @Test
    fun aRequestedNameLosesFoldersAndAKnownEnding() {
        assertEquals("boat", VideoFiles.baseName("boat.mp4", "ignored prompt"))
        assertEquals("passwd", VideoFiles.baseName("../../etc/passwd", null))
        assertEquals("my-boat", VideoFiles.baseName("my boat!", null))
    }

    @Test
    fun withoutAUsableNameTheFirstWordsOfThePromptNameTheFile() {
        assertEquals("a-boat-at-dawn-on-a", VideoFiles.baseName(null, "A boat at dawn on a calm lake, mist rising"))
        assertEquals("video", VideoFiles.baseName("///", "!!!"))
        assertEquals("video", VideoFiles.baseName(null, null))
    }

    @Test
    fun banglaNamesKeepTheirVowelSigns() {
        assertEquals("নদীর-ধারে", VideoFiles.baseName("নদীর ধারে", null))
    }

    @Test
    fun nothingIsOverwritten() {
        val folder = Files.createTempDirectory("videos").toFile()
        VideoFiles.freeFile(folder, "boat", "mp4").writeText("one")
        VideoFiles.freeFile(folder, "boat", "mp4").also { assertEquals("boat (2).mp4", it.name) }.writeText("two")

        assertEquals("boat (3).mp4", VideoFiles.freeFile(folder, "boat", "mp4").name)
        assertEquals("one", folder.resolve("boat.mp4").readText())
    }

    @Test
    fun pendingJobsSurviveReadingAndWritingAndTheFileDisappearsWhenEmpty() {
        val thread = Files.createTempDirectory("thread").toFile()
        val jobs = PendingVideoJobs(thread)
        assertTrue(jobs.all().isEmpty())

        jobs.add(PendingVideoJob("t1", "job-1", "openrouter:a/b", 5L, 4, "720p", "boat"))
        jobs.add(PendingVideoJob("t1", "job-2", "openrouter:a/c", 6L, null, null, "lake"))
        jobs.markCostRecorded("job-1")

        val read = PendingVideoJobs(thread).all()
        assertEquals(listOf("job-1", "job-2"), read.map { it.jobId })
        assertEquals(PendingVideoJob("t1", "job-1", "openrouter:a/b", 5L, 4, "720p", "boat", costRecorded = true), read[0])
        assertEquals(false, read[1].costRecorded)
        assertNull(read[1].durationSeconds)

        jobs.remove("job-1")
        jobs.remove("job-2")
        assertTrue(thread.resolve("videos/pending-jobs.json").exists().not())
    }

    @Test
    fun aDamagedPendingFileReadsAsEmpty() {
        val thread = Files.createTempDirectory("thread").toFile()
        thread.resolve("videos").mkdirs()
        thread.resolve("videos/pending-jobs.json").writeText("{not json")

        assertTrue(PendingVideoJobs(thread).all().isEmpty())
    }
}
