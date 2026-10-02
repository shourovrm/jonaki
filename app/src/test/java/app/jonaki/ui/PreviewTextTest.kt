package app.jonaki.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewTextTest {
    @Test
    fun userMessageLosesItsTimeLine() {
        assertEquals("hello", PreviewText.of("[Friday 2 October 2026, 21:47 Asia/Dhaka]\nhello", isUserMessage = true))
    }

    @Test
    fun markdownMarksAreRemoved() {
        assertEquals(
            "Here's the forecast for Dhaka tomorrow (via timeanddate.com):",
            PreviewText.of("Here's the forecast for **Dhaka tomorrow** (via [timeanddate.com](https://x.y)):", isUserMessage = false),
        )
    }

    @Test
    fun headingsListsAndCodeShowTheirText() {
        assertEquals("Summary", PreviewText.of("## Summary\n- one", isUserMessage = false))
        assertEquals("one", PreviewText.of("\n- one", isUserMessage = false))
        assertEquals("run gradle", PreviewText.of("`run` _gradle_", isUserMessage = false))
    }

    @Test
    fun blankTextGivesAnEmptyLine() {
        assertEquals("", PreviewText.of("  \n ", isUserMessage = false))
    }
}
