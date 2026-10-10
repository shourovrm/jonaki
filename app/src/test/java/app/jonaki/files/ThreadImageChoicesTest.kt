package app.jonaki.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadImageChoicesTest {
    private val file = File(Files.createTempDirectory("image-choices").toFile(), "thread-image-models.json")

    private val flux = "openrouter:black-forest-labs/flux.2-klein-4b"
    private val gemini = "gemini:gemini-2.5-flash-image"
    private val sourceful = "openrouter:sourceful/riverflow-v2"
    private val added = listOf(flux, gemini, sourceful)

    private fun reopened() = ThreadImageChoices(file)

    @Test
    fun `with no choice the thread follows the starred default`() {
        val choices = ThreadImageChoices(file)

        assertEquals(flux, choices.effectiveModelKey("thread-1", added, starredDefault = flux))
        assertEquals(gemini, choices.effectiveModelKey("thread-1", added, starredDefault = gemini))
    }

    @Test
    fun `a valid choice wins over the starred default`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", gemini, starredDefault = flux)

        assertEquals(gemini, choices.effectiveModelKey("thread-1", added, starredDefault = flux))
        assertEquals(flux, choices.effectiveModelKey("thread-2", added, starredDefault = flux))
    }

    @Test
    fun `a choice survives a restart`() {
        ThreadImageChoices(file).choose("thread-1", gemini, starredDefault = flux)

        assertEquals(gemini, reopened().choiceFor("thread-1"))
    }

    @Test
    fun `a choice whose model was removed falls back to the starred default`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", sourceful, starredDefault = flux)

        val afterRemoval = listOf(flux, gemini)
        assertEquals(flux, choices.effectiveModelKey("thread-1", afterRemoval, starredDefault = flux))
    }

    @Test
    fun `choosing the starred default clears the choice`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", gemini, starredDefault = flux)
        choices.choose("thread-1", flux, starredDefault = flux)

        assertNull(choices.choiceFor("thread-1"))
        assertNull(reopened().choiceFor("thread-1"))
    }

    @Test
    fun `a later change of the starred default is followed after choosing the default`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", gemini, starredDefault = flux)
        choices.choose("thread-1", flux, starredDefault = flux)

        assertEquals(sourceful, choices.effectiveModelKey("thread-1", added, starredDefault = sourceful))
    }

    @Test
    fun `with no image models there is no effective model`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", gemini, starredDefault = flux)

        assertNull(choices.effectiveModelKey("thread-1", emptyList(), starredDefault = null))
    }

    @Test
    fun `a default that is not listed falls back to the first listed model`() {
        val choices = ThreadImageChoices(file)

        assertEquals(flux, choices.effectiveModelKey("thread-1", added, starredDefault = "openrouter:gone"))
    }

    @Test
    fun `clearing a deleted thread removes its choice from the file`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", gemini, starredDefault = flux)
        choices.choose("thread-2", sourceful, starredDefault = flux)
        choices.clear("thread-1")

        assertNull(reopened().choiceFor("thread-1"))
        assertEquals(sourceful, reopened().choiceFor("thread-2"))
    }

    @Test
    fun `pruning drops the choices of threads that no longer exist`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", gemini, starredDefault = flux)
        choices.choose("thread-2", sourceful, starredDefault = flux)
        choices.pruneTo(setOf("thread-2"))

        assertEquals(mapOf("thread-2" to sourceful), reopened().byThread.value)
    }

    @Test
    fun `a choice kept in memory only is not written to the file`() {
        val choices = ThreadImageChoices(file)
        choices.choose("incognito-1", gemini, starredDefault = flux, keepOnDisk = false)
        choices.choose("thread-1", sourceful, starredDefault = flux)

        assertEquals(gemini, choices.choiceFor("incognito-1"))
        assertNull(reopened().choiceFor("incognito-1"))
        assertEquals(sourceful, reopened().choiceFor("thread-1"))
    }

    @Test
    fun `an unreadable file gives no choices`() {
        file.writeText("not json at all")

        assertTrue(ThreadImageChoices(file).byThread.value.isEmpty())
    }

    @Test
    fun `clearing a thread that has no choice writes nothing`() {
        val choices = ThreadImageChoices(file)
        choices.clear("thread-1")

        assertFalse(file.exists())
    }
}
