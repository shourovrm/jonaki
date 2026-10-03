package app.jonaki.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val entries = SettingsSearch.entries(EnglishTexts(), SettingsSample.state)

    private fun search(query: String): List<SettingsSearchEntry> = SettingsSearch.filter(entries, query)

    @Test
    fun everyPageTitleIsAnEntry() {
        val titles = entries.map { entry -> entry.title to entry.page }
        assertTrue(("Models" to SettingsPage.MODELS) in titles)
        assertTrue(("Web and YouTube" to SettingsPage.WEB) in titles)
        assertTrue(("About" to SettingsPage.ABOUT) in titles)
        assertEquals(SettingsPage.entries.toSet(), entries.map { entry -> entry.page }.toSet())
    }

    @Test
    fun pythonOpensToolsAndApprovals() {
        assertEquals(listOf(SettingsSearchEntry("Python", SettingsPage.TOOLS)), search("python"))
    }

    @Test
    fun aWordFindsEveryTitleThatHoldsIt() {
        val found = search("persona")
        assertEquals(listOf("Personas", "Add persona"), found.map { entry -> entry.title })
        assertTrue(found.all { entry -> entry.page == SettingsPage.ANSWERS })
    }

    @Test
    fun aSearchServiceNameOpensWebAndYouTube() {
        assertEquals(listOf(SettingsSearchEntry("Tavily", SettingsPage.WEB)), search("Tavily"))
    }

    @Test
    fun aChatServiceNameOpensModels() {
        assertEquals(listOf(SettingsSearchEntry("OpenRouter", SettingsPage.MODELS)), search("openrouter"))
    }

    @Test
    fun huggingFaceOpensLocalModels() {
        assertEquals(listOf(SettingsSearchEntry("Search Hugging Face", SettingsPage.LOCAL_MODELS)), search("hugging"))
        assertEquals(listOf(SettingsSearchEntry("Local models", SettingsPage.LOCAL_MODELS)), search("local"))
    }

    @Test
    fun permissionRowsAreFound() {
        assertEquals(listOf(SettingsSearchEntry("Calendar", SettingsPage.PERMISSIONS)), search("calendar"))
    }

    @Test
    fun matchingIgnoresCaseAndSurroundingSpace() {
        assertEquals(search("Status icons"), search("  STATUS ICONS "))
        assertEquals(SettingsPage.THEME, search("status icons").single().page)
    }

    @Test
    fun aBlankQueryFindsNothing() {
        assertEquals(emptyList<SettingsSearchEntry>(), search(""))
        assertEquals(emptyList<SettingsSearchEntry>(), search("   "))
    }

    @Test
    fun anUnknownWordFindsNothing() {
        assertEquals(emptyList<SettingsSearchEntry>(), search("bluetooth"))
    }

    @Test
    fun noEntryRepeats() {
        assertEquals(entries.size, entries.distinct().size)
    }
}
