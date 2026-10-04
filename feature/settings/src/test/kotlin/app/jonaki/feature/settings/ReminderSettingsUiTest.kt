package app.jonaki.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderSettingsUiTest {
    @Test
    fun theIntervalStepsThroughTheOptionsInBothDirections() {
        val settings = ReminderSettingsUi(intervalMinutes = 15)

        assertEquals(10, settings.shorterInterval)
        assertEquals(30, settings.longerInterval)
    }

    @Test
    fun theSmallestAndLargestIntervalsHaveNowhereToGo() {
        assertNull(ReminderSettingsUi(intervalMinutes = 5).shorterInterval)
        assertNull(ReminderSettingsUi(intervalMinutes = 60).longerInterval)
    }

    @Test
    fun theRemindersSettingsCanBeFoundBySearch() {
        val entries = SettingsSearch.entries(EnglishTexts(), SettingsSample.state)

        val found = SettingsSearch.filter(entries, "repeat")

        assertEquals(listOf("Repeat every", "Repeats"), found.map { it.title })
        assertEquals(setOf(SettingsPage.FILES_SCHEDULE), found.map { it.page }.toSet())
    }
}
