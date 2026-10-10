package app.jonaki.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadDraftsTest {
    private val folder = Files.createTempDirectory("drafts").toFile()
    private val file = File(folder, "drafts.json")

    private fun reopened() = ThreadDrafts(file)

    @Test
    fun `a saved draft is read back by a new store`() {
        ThreadDrafts(file).set("thread-1", "half a thought")

        assertEquals("half a thought", reopened().textFor("thread-1"))
    }

    @Test
    fun `each thread keeps its own draft`() {
        val drafts = ThreadDrafts(file)
        drafts.set("thread-1", "one")
        drafts.set("thread-2", "two")

        assertEquals("one", reopened().textFor("thread-1"))
        assertEquals("two", reopened().textFor("thread-2"))
    }

    @Test
    fun `a thread without a draft has empty text`() {
        assertEquals("", ThreadDrafts(file).textFor("thread-1"))
    }

    @Test
    fun `text is kept exactly as typed, including a trailing space`() {
        ThreadDrafts(file).set("thread-1", "hello ")

        assertEquals("hello ", reopened().textFor("thread-1"))
    }

    @Test
    fun `a blank draft is stored as no draft`() {
        val drafts = ThreadDrafts(file)
        drafts.set("thread-1", "  \n\t ")

        assertFalse("thread-1" in drafts.byThread.value)
        assertFalse("thread-1" in reopened().byThread.value)
    }

    @Test
    fun `a blank draft replaces a saved one`() {
        val drafts = ThreadDrafts(file)
        drafts.set("thread-1", "something")
        drafts.set("thread-1", " ")

        assertEquals("", reopened().textFor("thread-1"))
    }

    @Test
    fun `clear removes the draft from memory and from the file`() {
        val drafts = ThreadDrafts(file)
        drafts.set("thread-1", "something")
        drafts.clear("thread-1")

        assertEquals("", drafts.textFor("thread-1"))
        assertEquals("", reopened().textFor("thread-1"))
    }

    @Test
    fun `a draft longer than the limit is cut to the limit`() {
        val drafts = ThreadDrafts(file)
        drafts.set("thread-1", "a".repeat(ThreadDrafts.MAX_LENGTH + 500))

        assertEquals(ThreadDrafts.MAX_LENGTH, drafts.textFor("thread-1").length)
        assertEquals(ThreadDrafts.MAX_LENGTH, reopened().textFor("thread-1").length)
    }

    @Test
    fun `pruning drops drafts of threads that no longer exist`() {
        val drafts = ThreadDrafts(file)
        drafts.set("kept", "stays")
        drafts.set("deleted", "goes")

        drafts.pruneTo(setOf("kept"))

        assertEquals("stays", reopened().textFor("kept"))
        assertEquals("", reopened().textFor("deleted"))
    }

    @Test
    fun `pruning keeps the draft of the new thread, which has no row`() {
        val drafts = ThreadDrafts(file)
        drafts.set(ThreadDrafts.NEW_THREAD_KEY, "first message")

        drafts.pruneTo(emptySet())

        assertEquals("first message", reopened().textFor(ThreadDrafts.NEW_THREAD_KEY))
    }

    @Test
    fun `a damaged file gives no drafts instead of a crash`() {
        file.writeText("{ not json")

        assertTrue(ThreadDrafts(file).byThread.value.isEmpty())
    }

    @Test
    fun `entries that are not text are ignored`() {
        file.writeText("""{"thread-1": 5, "thread-2": "ok"}""")

        val drafts = ThreadDrafts(file)

        assertEquals("", drafts.textFor("thread-1"))
        assertEquals("ok", drafts.textFor("thread-2"))
    }

    @Test
    fun `the preview line is the first line that has text`() {
        assertEquals("second", ThreadDrafts.previewLine("\n  \n  second  \nthird"))
    }

    @Test
    fun `the preview line of a blank text is empty`() {
        assertEquals("", ThreadDrafts.previewLine(" \n "))
    }

    @Test
    fun `a change shows in the observable map at once`() {
        val drafts = ThreadDrafts(file)
        drafts.set("thread-1", "typed")

        assertEquals(mapOf("thread-1" to "typed"), drafts.byThread.value)
    }
}
