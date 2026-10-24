package app.jonaki.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-187: the Settings home has six titled groups, and every page sits in exactly one. */
class SettingsPageGroupsTest {
    @Test
    fun `six groups in the order of the mockup`() {
        assertEquals(
            listOf(
                SettingsGroup.MODELS,
                SettingsGroup.CAPABILITIES,
                SettingsGroup.MEMORY,
                SettingsGroup.SAFETY,
                SettingsGroup.LOOK,
                SettingsGroup.ABOUT,
            ),
            SettingsPage.GROUPS.map { group -> group.first },
        )
    }

    @Test
    fun `every page is in exactly one group`() {
        val placed = SettingsPage.GROUPS.flatMap { group -> group.second }
        assertEquals(SettingsPage.entries.toList().sortedBy { page -> page.key }, placed.sortedBy { page -> page.key })
        assertEquals(placed.size, placed.toSet().size)
    }

    @Test
    fun `a page's group matches the list it sits in`() {
        for ((group, pages) in SettingsPage.GROUPS) {
            for (page in pages) {
                assertEquals(group, page.group, "page ${page.key}")
            }
        }
    }

    @Test
    fun `safety, files and reminders are pages of their own`() {
        assertTrue(SettingsPage.entries.map { page -> page.key }.containsAll(listOf("safety", "files", "reminders", "memory", "skills")))
    }
}
