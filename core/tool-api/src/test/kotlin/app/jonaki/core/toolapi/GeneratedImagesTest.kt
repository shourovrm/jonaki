package app.jonaki.core.toolapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneratedImagesTest {
    @Test
    fun theFirstLineNamesTheFileAndIsReadBack() {
        val text = GeneratedImages.firstLine("images/cat.png") + "\nSize: 1 px"
        assertEquals("images/cat.png", GeneratedImages.pathIn(text))
    }

    @Test
    fun errorsAndOtherTextNameNoFile() {
        assertNull(GeneratedImages.pathIn("Error: no image model is added. Tell the user."))
        assertNull(GeneratedImages.pathIn(""))
        assertNull(GeneratedImages.pathIn("Image saved: "))
    }
}
