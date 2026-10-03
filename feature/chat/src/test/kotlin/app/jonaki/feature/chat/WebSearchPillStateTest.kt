package app.jonaki.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchPillStateTest {
    @Test
    fun onShowsAPlainGlobeAndATapTurnsItOff() {
        val pill = WebSearchPillState.of(webSearchEnabled = true)

        assertEquals(R.string.chat_status_web_on, pill.descriptionResource)
        assertFalse(pill.crossedOut)
        assertFalse(pill.enabledAfterTap)
    }

    @Test
    fun offShowsACrossedGlobeAndATapTurnsItOn() {
        val pill = WebSearchPillState.of(webSearchEnabled = false)

        assertEquals(R.string.chat_status_web_off, pill.descriptionResource)
        assertTrue(pill.crossedOut)
        assertTrue(pill.enabledAfterTap)
    }

    @Test
    fun twoTapsComeBackToTheStart() {
        val afterOneTap = WebSearchPillState.of(webSearchEnabled = true).enabledAfterTap
        val afterTwoTaps = WebSearchPillState.of(afterOneTap).enabledAfterTap

        assertTrue(afterTwoTaps)
    }
}
