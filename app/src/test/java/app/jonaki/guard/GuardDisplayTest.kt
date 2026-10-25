package app.jonaki.guard

import org.junit.Assert.assertEquals
import org.junit.Test

class GuardDisplayTest {
    @Test
    fun theSafetyCheckNamesJevThroughOpenRouter() {
        assertEquals(GuardDisplay(name = "Jev", provider = "OpenRouter"), activeGuardDisplay())
    }
}
