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

    private val vectorV4 = "openrouter:recraft/recraft-v4-vector"
    private val vectorPro = "openrouter:recraft/recraft-v4-pro-vector"
    private val rasterKeys = listOf(flux, gemini)
    private val vectorKeys = listOf(vectorV4, vectorPro)

    @Test
    fun `a starred vector model is not the raster default and the first raster model takes over`() {
        val choices = ThreadImageChoices(file)

        // AgentRunner passes only the raster keys; the star is a vector model, so it is not among them.
        assertEquals(flux, choices.effectiveModelKey("thread-1", rasterKeys, starredDefault = vectorV4))
    }

    @Test
    fun `a thread's vector pick never becomes the raster model and the other way round`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", vectorPro, starredDefault = vectorV4, isVector = true)

        assertEquals(flux, choices.effectiveModelKey("thread-1", rasterKeys, starredDefault = flux))
        assertEquals(vectorPro, choices.effectiveVectorModelKey("thread-1", vectorKeys))
    }

    @Test
    fun `a raster choice found among the vector models is ignored by the vector tool`() {
        val choices = ThreadImageChoices(file)
        // A pick stored by an older version for a model that is now known to be vector, or the wrong kind by any cause.
        choices.choose("thread-1", gemini, starredDefault = flux)

        assertEquals(vectorV4, choices.effectiveVectorModelKey("thread-1", vectorKeys))
        assertEquals(gemini, choices.effectiveModelKey("thread-1", rasterKeys, starredDefault = flux))
    }

    @Test
    fun `a wrong-kind choice falls back to the default of the right kind`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", vectorPro, starredDefault = flux)

        // The stored raster slot holds a vector model: the raster tool does not list it, so it falls back to the star.
        assertEquals(flux, choices.effectiveModelKey("thread-1", rasterKeys, starredDefault = flux))
    }

    @Test
    fun `both picks of a thread live side by side and survive a restart`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", gemini, starredDefault = flux)
        choices.choose("thread-1", vectorPro, starredDefault = vectorV4, isVector = true)

        val restored = reopened()
        assertEquals(gemini, restored.effectiveModelKey("thread-1", rasterKeys, starredDefault = flux))
        assertEquals(vectorPro, restored.effectiveVectorModelKey("thread-1", vectorKeys))
    }

    @Test
    fun `choosing the first vector model returns to following the default`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", vectorPro, starredDefault = vectorV4, isVector = true)
        choices.choose("thread-1", vectorV4, starredDefault = vectorV4, isVector = true)

        assertNull(choices.vectorChoiceFor("thread-1"))
        assertEquals(vectorV4, choices.effectiveVectorModelKey("thread-1", vectorKeys))
    }

    @Test
    fun `a vector pick whose model was removed falls back to the first vector model`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", vectorPro, starredDefault = vectorV4, isVector = true)

        assertEquals(vectorV4, choices.effectiveVectorModelKey("thread-1", listOf(vectorV4)))
        assertNull(choices.effectiveVectorModelKey("thread-1", emptyList()))
    }

    @Test
    fun `clearing and pruning treat both picks of a thread`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", gemini, starredDefault = flux)
        choices.choose("thread-1", vectorPro, starredDefault = vectorV4, isVector = true)
        choices.choose("thread-2", vectorPro, starredDefault = vectorV4, isVector = true)

        choices.pruneTo(setOf("thread-1"))
        assertEquals(setOf("thread-1", ThreadImageChoices.vectorKeyOf("thread-1")), choices.byThread.value.keys)

        choices.clear("thread-1")
        assertTrue(choices.byThread.value.isEmpty())
        assertTrue(reopened().byThread.value.isEmpty())
    }

    @Test
    fun `an incognito vector pick stays in memory only`() {
        val choices = ThreadImageChoices(file)
        choices.choose("thread-1", vectorPro, starredDefault = vectorV4, keepOnDisk = false, isVector = true)

        assertEquals(vectorPro, choices.vectorChoiceFor("thread-1"))
        assertTrue(reopened().byThread.value.isEmpty())
    }
}
