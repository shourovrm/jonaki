package app.jonaki.core.agent

import app.jonaki.core.model.ImagePart
import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.toolapi.ImageSource
import app.jonaki.core.toolapi.ViewedImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMessagesTest {
    private val loadedSources = mutableListOf<ImageSource>()

    /** Encodes the reference itself, so a test can see which image went where. */
    private val loader = ImageLoader { source ->
        loadedSources += source
        if (source.path.endsWith(".heic")) null else ImagePart("image/jpeg", "data-of-" + source.reference)
    }

    private val photoMessage = Message(Role.USER, "[Friday]\nWhat is this?\n\nAttached: inbox/photo.jpg, inbox/notes.pdf")

    @Test
    fun attachmentLineRoundTripsNamesWithCommas() {
        val text = AttachmentLine.appendTo("Look", listOf("inbox/a, b.jpg", "inbox/c.pdf"))
        assertEquals("Look\n\nAttached: inbox/a, b.jpg, inbox/c.pdf", text)
        assertEquals(listOf("inbox/a, b.jpg", "inbox/c.pdf"), AttachmentLine.pathsIn(text))
    }

    @Test
    fun attachedImagesGoWithTheirMessageWhenTheModelSeesImages() {
        val prepared = ImageMessages(loader, modelAcceptsImages = true).prepare(listOf(photoMessage))

        assertEquals(1, prepared.size)
        assertEquals(photoMessage.text, prepared[0].text)
        assertEquals(listOf(ImagePart("image/jpeg", "data-of-inbox/photo.jpg")), prepared[0].images)
    }

    @Test
    fun aModelWithoutImagesIsToldWhereTheImageIs() {
        val prepared = ImageMessages(loader, modelAcceptsImages = false).prepare(listOf(photoMessage))

        assertTrue(prepared[0].images.isEmpty())
        assertTrue(prepared[0].text.endsWith("[inbox/photo.jpg is an image; this model cannot see images.]"))
        assertTrue(loadedSources.isEmpty())
    }

    @Test
    fun anUndecodableImageBecomesANote() {
        val heic = Message(Role.USER, "Attached: inbox/IMG_1.heic")
        val prepared = ImageMessages(loader, modelAcceptsImages = true).prepare(listOf(heic))

        assertTrue(prepared[0].images.isEmpty())
        assertTrue(prepared[0].text.endsWith("[inbox/IMG_1.heic could not be opened as an image.]"))
    }

    @Test
    fun viewImageResultsAreFollowedByOneUserMessageWithTheImages() {
        val history = listOf(
            Message(Role.USER, "Compare the charts"),
            Message(
                Role.ASSISTANT,
                "",
                toolCalls = listOf(
                    ToolCall("c1", ViewedImages.TOOL_NAME, """{"path":"work/a.png"}"""),
                    ToolCall("c2", ViewedImages.TOOL_NAME, """{"path":"docs/scan.pdf","page":2}"""),
                ),
            ),
            Message(Role.TOOL, ViewedImages.resultText(ImageSource("work/a.png")), toolCallId = "c1"),
            Message(Role.TOOL, ViewedImages.resultText(ImageSource("docs/scan.pdf", 2)), toolCallId = "c2"),
        )
        val prepared = ImageMessages(loader, modelAcceptsImages = true).prepare(history)

        assertEquals(5, prepared.size)
        val follow = prepared[4]
        assertEquals(Role.USER, follow.role)
        assertEquals("[view_image: work/a.png]\n[view_image: docs/scan.pdf#page=2]", follow.text)
        assertEquals(
            listOf("data-of-work/a.png", "data-of-docs/scan.pdf#page=2"),
            follow.images.map { image -> image.base64Data },
        )
    }

    @Test
    fun theSameTextFromAnotherToolIsNotTakenForAnImage() {
        val history = listOf(
            Message(Role.ASSISTANT, "", toolCalls = listOf(ToolCall("c1", "read_file", "{}"))),
            Message(Role.TOOL, ViewedImages.resultText(ImageSource("work/a.png")), toolCallId = "c1"),
            Message(Role.ASSISTANT, "done"),
        )
        val prepared = ImageMessages(loader, modelAcceptsImages = true).prepare(history)

        assertEquals(history, prepared)
    }

    @Test
    fun preparingTwiceGivesEqualMessagesAndLoadsEachImageOnce() {
        val imageMessages = ImageMessages(loader, modelAcceptsImages = true)
        val first = imageMessages.prepare(listOf(photoMessage))
        val second = imageMessages.prepare(listOf(photoMessage))

        assertEquals(first, second)
        assertEquals(1, loadedSources.size)
    }
}
