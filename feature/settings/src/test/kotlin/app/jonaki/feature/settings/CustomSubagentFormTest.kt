package app.jonaki.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomSubagentFormTest {
    private val taken = setOf("researcher", "scout", "writer", "worker", "price-checker")

    @Test
    fun aShortLowercaseNameWithADescriptionIsValid() {
        assertNull(CustomSubagentForm.problem("summarizer", "Summarises files.", taken, editingName = null))
        assertNull(CustomSubagentForm.problem("bn-translator2", "Translates to Bangla.", taken, editingName = null))
    }

    @Test
    fun namesTheModelCouldNotWriteSafelyAreRefused() {
        for (name in listOf("Summarizer", "price checker", "-lead", "trail-", "two--hyphens", "2fast", "naïve", "a_b")) {
            assertFalse(name, CustomSubagentForm.isValidName(name))
        }
        assertTrue(CustomSubagentForm.isValidName("a"))
        assertEquals(
            CustomSubagentForm.Problem.NAME_INVALID,
            CustomSubagentForm.problem("Price Checker", "x", taken, editingName = null),
        )
    }

    @Test
    fun aNameLongerThanTheLimitIsRefused() {
        val longName = "a".repeat(CustomSubagentForm.MAX_NAME_LENGTH + 1)

        assertFalse(CustomSubagentForm.isValidName(longName))
    }

    @Test
    fun aBlankNameAndABlankDescriptionAreMissing() {
        assertEquals(CustomSubagentForm.Problem.NAME_MISSING, CustomSubagentForm.problem("  ", "x", taken, editingName = null))
        assertEquals(
            CustomSubagentForm.Problem.DESCRIPTION_MISSING,
            CustomSubagentForm.problem("summarizer", " ", taken, editingName = null),
        )
    }

    @Test
    fun aBuiltInOrAnotherCustomNameIsTaken() {
        assertEquals(CustomSubagentForm.Problem.NAME_TAKEN, CustomSubagentForm.problem("scout", "x", taken, editingName = null))
        assertEquals(CustomSubagentForm.Problem.NAME_TAKEN, CustomSubagentForm.problem("price-checker", "x", taken, editingName = null))
    }

    @Test
    fun anEditedSubagentMayKeepItsOwnName() {
        assertNull(CustomSubagentForm.problem("price-checker", "x", taken, editingName = "price-checker"))
        assertEquals(CustomSubagentForm.Problem.NAME_TAKEN, CustomSubagentForm.problem("scout", "x", taken, editingName = "price-checker"))
    }
}
