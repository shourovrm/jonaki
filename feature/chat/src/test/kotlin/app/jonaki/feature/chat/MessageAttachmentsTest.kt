package app.jonaki.feature.chat

import app.jonaki.core.agent.AttachmentLine
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageAttachmentsTest {
    @Test
    fun aMessageWithoutFilesIsUnchanged() {
        val split = MessageAttachments.split("Hello there")

        assertEquals("Hello there", split.shownText)
        assertEquals(emptyList<String>(), split.imagePaths)
    }

    @Test
    fun imagesLeaveTheShownTextAndKeepTheirOrder() {
        val stored = AttachmentLine.appendTo("What is this?", listOf("inbox/a.jpg", "inbox/b.PNG"))

        val split = MessageAttachments.split(stored)

        assertEquals("What is this?", split.shownText)
        assertEquals(listOf("inbox/a.jpg", "inbox/b.PNG"), split.imagePaths)
    }

    @Test
    fun otherFilesStayInTheAttachmentLine() {
        val stored = AttachmentLine.appendTo("Compare", listOf("inbox/sales.csv", "inbox/a.jpg", "inbox/scan.pdf"))

        val split = MessageAttachments.split(stored)

        assertEquals("Compare\n\nAttached: inbox/sales.csv, inbox/scan.pdf", split.shownText)
        assertEquals(listOf("inbox/a.jpg"), split.imagePaths)
    }

    @Test
    fun aMessageOfOnlyImagesShowsNoText() {
        val stored = AttachmentLine.appendTo("", listOf("inbox/a.jpg"))

        val split = MessageAttachments.split(stored)

        assertEquals("", split.shownText)
        assertEquals(listOf("inbox/a.jpg"), split.imagePaths)
    }

    @Test
    fun aFileNameWithACommaIsOneImage() {
        val stored = AttachmentLine.appendTo("Look", listOf("inbox/day, night.webp", "inbox/b.jpg"))

        val split = MessageAttachments.split(stored)

        assertEquals(listOf("inbox/day, night.webp", "inbox/b.jpg"), split.imagePaths)
        assertEquals("Look", split.shownText)
    }

    @Test
    fun theStoredTextIsNotChangedByShowingIt() {
        // The model reads the stored text with its paths (D-005, D-049); only the screen drops them.
        val stored = AttachmentLine.appendTo("Hi", listOf("inbox/a.jpg"))

        MessageAttachments.split(stored)

        assertEquals("Hi\n\nAttached: inbox/a.jpg", stored)
    }
}
