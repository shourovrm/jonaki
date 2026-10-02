package app.jonaki.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentDraftsTest {
    private val stagingRoot = Files.createTempDirectory("staging").toFile()
    private val threadFolder = Files.createTempDirectory("thread").toFile()
    private val drafts = AttachmentDrafts(stagingRoot)

    private fun staged(name: String, content: String): StagedFile =
        drafts.stage(name) { target -> target.writeText(content) }

    @Test
    fun stagedFilesWaitPerThreadInTheOrderAdded() {
        val first = staged("a.csv", "1")
        val second = staged("b.pdf", "2")
        drafts.add("thread-1", listOf(first, second))
        assertEquals(listOf("a.csv", "b.pdf"), drafts.byThread.value["thread-1"]?.map { it.name })
        assertEquals(null, drafts.byThread.value["thread-2"])
    }

    @Test
    fun twoFilesWithTheSameNameCanWaitTogether() {
        val first = staged("sales.csv", "1")
        val second = staged("sales.csv", "2")
        assertFalse(first.file == second.file)
        assertEquals("1", first.file.readText())
        assertEquals("2", second.file.readText())
    }

    @Test
    fun removingAChipDeletesItsStagedFile() {
        val file = staged("a.csv", "1")
        drafts.add("thread-1", listOf(file))
        drafts.remove("thread-1", file.id)
        assertEquals(null, drafts.byThread.value["thread-1"])
        assertFalse(file.file.exists())
    }

    @Test
    fun movingIntoTheInboxKeepsExistingFilesAndNamesTheNewPaths() {
        File(threadFolder, "inbox").mkdirs()
        File(threadFolder, "inbox/sales.csv").writeText("old")
        drafts.add("new", listOf(staged("sales.csv", "new"), staged("notes.txt", "n")))

        val paths = drafts.moveIntoInbox("new", threadFolder)

        assertEquals(listOf("inbox/sales (2).csv", "inbox/notes.txt"), paths)
        assertEquals("old", File(threadFolder, "inbox/sales.csv").readText())
        assertEquals("new", File(threadFolder, "inbox/sales (2).csv").readText())
        assertEquals(null, drafts.byThread.value["new"])
    }

    @Test
    fun movingWithNothingWaitingReturnsNoPaths() {
        assertTrue(drafts.moveIntoInbox("thread-1", threadFolder).isEmpty())
    }

    @Test
    fun failedStagingLeavesNothingBehind() {
        val outcome = runCatching { drafts.stage("broken.bin") { throw java.io.IOException("gone") } }
        assertTrue(outcome.isFailure)
        assertTrue(stagingRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun waitingChipsComeBackAfterTheProcessRestarts() {
        val first = staged("a.csv", "1")
        val second = staged("b.pdf", "2")
        drafts.add("thread-1", listOf(first))
        drafts.add("new", listOf(second))

        val afterRestart = AttachmentDrafts(stagingRoot)
        afterRestart.restore()

        assertEquals(listOf(first), afterRestart.byThread.value["thread-1"])
        assertEquals(listOf(second), afterRestart.byThread.value["new"])
        assertEquals("1", first.file.readText())
    }

    @Test
    fun removedAndSentChipsStayGoneAfterTheProcessRestarts() {
        val removed = staged("a.csv", "1")
        val sent = staged("b.pdf", "2")
        drafts.add("thread-1", listOf(removed))
        drafts.add("thread-2", listOf(sent))
        drafts.remove("thread-1", removed.id)
        drafts.moveIntoInbox("thread-2", threadFolder)

        val afterRestart = AttachmentDrafts(stagingRoot)
        afterRestart.restore()

        assertTrue(afterRestart.byThread.value.isEmpty())
    }

    @Test
    fun restoreDropsChipsWhoseFileIsGoneAndReturnsUnlistedCopies() {
        val kept = staged("a.csv", "1")
        val lost = staged("b.pdf", "2")
        drafts.add("thread-1", listOf(kept, lost))
        lost.file.parentFile?.deleteRecursively()
        // A share whose thread was never picked leaves a staged copy that no chip names.
        val unlisted = staged("c.txt", "3")

        val afterRestart = AttachmentDrafts(stagingRoot)
        val leftovers = afterRestart.restore()

        assertEquals(listOf(kept), afterRestart.byThread.value["thread-1"])
        assertEquals(listOf(unlisted.file.parentFile), leftovers)
    }

    @Test
    fun discardingAThreadDeletesItsFilesAndKeepsOthers() {
        val gone = staged("a.csv", "1")
        val kept = staged("b.pdf", "2")
        drafts.add("thread-1", listOf(gone))
        drafts.add("thread-2", listOf(kept))

        drafts.discardAll("thread-1")

        assertFalse(gone.file.exists())
        assertEquals(null, drafts.byThread.value["thread-1"])
        assertEquals(listOf(kept), drafts.byThread.value["thread-2"])
    }

    @Test
    fun restoreWithNothingSavedStartsEmpty() {
        assertTrue(drafts.restore().isEmpty())
        assertTrue(drafts.byThread.value.isEmpty())
    }

    @Test
    fun messageListsTheInboxPathsAfterTheText() {
        assertEquals(
            "Make a chart\n\nAttached: inbox/sales.csv, inbox/notes.txt",
            AttachmentDrafts.messageWith("Make a chart", listOf("inbox/sales.csv", "inbox/notes.txt")),
        )
    }

    @Test
    fun messageWithoutTextIsTheAttachedLineAlone() {
        assertEquals("Attached: inbox/sales.csv", AttachmentDrafts.messageWith("  ", listOf("inbox/sales.csv")))
    }

    @Test
    fun messageWithoutFilesIsTheTextAsTyped() {
        assertEquals("Hello", AttachmentDrafts.messageWith("Hello", emptyList()))
    }
}
