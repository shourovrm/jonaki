package app.jonaki.core.toolapi

import org.junit.Assert.assertEquals
import org.junit.Test

class OutsideSourcesTest {
    @Test
    fun aLinkGivesItsHost() {
        assertEquals("example.com", OutsideSources.hostOf("https://example.com/a/b?c=1"))
    }

    @Test
    fun textThatIsNotALinkGivesNothing() {
        assertEquals("", OutsideSources.hostOf("not a link"))
        assertEquals("", OutsideSources.hostOf(null))
    }

    @Test
    fun aPathGivesItsFileName() {
        assertEquals("report.pdf", OutsideSources.fileNameOf("inbox/docs/report.pdf"))
        assertEquals("report.pdf", OutsideSources.fileNameOf("report.pdf"))
        assertEquals("", OutsideSources.fileNameOf(null))
    }

    /** view_image's result may sit under the outside-content wrapper's opening line. */
    @Test
    fun aViewedImageIsFoundInsideTheWrapper() {
        val plain = ViewedImages.resultText(ImageSource("inbox/cat.jpg"))
        val wrapped = "<outside-content source=\"view_image cat.jpg\">\n$plain\n</outside-content>"

        assertEquals(ImageSource("inbox/cat.jpg"), ViewedImages.sourceIn(plain))
        assertEquals(ImageSource("inbox/cat.jpg"), ViewedImages.sourceIn(wrapped))
        assertEquals(null, ViewedImages.sourceIn("Some\nother text\nImage shown: inbox/cat.jpg"))
    }
}
