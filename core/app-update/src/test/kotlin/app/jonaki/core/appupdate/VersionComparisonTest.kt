package app.jonaki.core.appupdate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {
    @Test
    fun `a higher part makes a version newer`() {
        assertTrue(isNewerVersion(candidate = "1.4.5", installed = "1.4.4"))
        assertTrue(isNewerVersion(candidate = "2.0.0", installed = "1.9.9"))
    }

    @Test
    fun `parts are compared as numbers, not text`() {
        assertTrue(isNewerVersion(candidate = "1.4.10", installed = "1.4.9"))
        assertFalse(isNewerVersion(candidate = "1.4.9", installed = "1.4.10"))
    }

    @Test
    fun `the same version is not newer`() {
        assertFalse(isNewerVersion(candidate = "1.4.4", installed = "1.4.4"))
    }

    @Test
    fun `a leading v is ignored on either side`() {
        assertFalse(isNewerVersion(candidate = "v1.4.4", installed = "1.4.4"))
        assertTrue(isNewerVersion(candidate = "V1.5.0", installed = "v1.4.4"))
    }

    @Test
    fun `a missing part counts as zero`() {
        assertFalse(isNewerVersion(candidate = "1.4", installed = "1.4.0"))
        assertTrue(isNewerVersion(candidate = "1.4.1", installed = "1.4"))
    }

    @Test
    fun `a text that is not a version is never newer`() {
        assertFalse(isNewerVersion(candidate = "nightly", installed = "1.4.4"))
        assertFalse(isNewerVersion(candidate = "1.4.5", installed = ""))
    }

    @Test
    fun `a suffix after a dash is dropped`() {
        assertEquals(listOf(1, 5, 0), parseVersionParts("v1.5.0-beta1"))
        assertNull(parseVersionParts("1.x.0"))
    }
}
