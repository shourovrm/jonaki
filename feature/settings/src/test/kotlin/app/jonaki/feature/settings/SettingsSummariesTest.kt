package app.jonaki.feature.settings

import app.jonaki.core.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** D-187: a row on the Settings home has a second line only when the user must act, and then in red. */
class SettingsSummariesTest {
    private val texts = EnglishTexts()

    private val emptyState = SettingsUiState(
        geminiKey = KeySlot("GEMINI", isSet = true),
        searchServices = emptyList(),
        webSearchOffInNewThreads = false,
        themeMode = ThemeMode.SYSTEM,
    )

    private fun summary(page: SettingsPage, state: SettingsUiState): PageSummary? = SettingsSummaries.of(page, state, texts)

    private fun chatService(name: String, keySet: Boolean) = ChatServiceCardUi(
        serviceKey = name.lowercase(),
        displayName = name,
        apiKey = KeySlot(name.uppercase(), isSet = keySet),
        models = listOf(ServiceModelUi("${name.lowercase()}:model-1", "Model 1")),
    )

    @Test
    fun modelsWithAKeyHaveNoSecondLine() {
        val state = emptyState.copy(chatServices = listOf(chatService("DeepSeek", keySet = false), chatService("OpenRouter", keySet = true)))
        assertNull(summary(SettingsPage.MODELS, state))
    }

    @Test
    fun modelsSayNoKeyWhenNoAddedServiceHasOne() {
        val state = emptyState.copy(chatServices = listOf(chatService("DeepSeek", keySet = false)))
        assertEquals(PageSummary("No key"), summary(SettingsPage.MODELS, state))
    }

    @Test
    fun aServiceWithoutAKeySlotDoesNotCountAsAKey() {
        val free = ChatServiceCardUi("jonaki-free", "Jonaki Free", apiKey = null, models = listOf(ServiceModelUi("jonaki-free:free", "Jonaki Free")))
        assertEquals(PageSummary("No key"), summary(SettingsPage.MODELS, emptyState.copy(chatServices = listOf(free))))
    }

    @Test
    fun webWarnsOnlyWithoutAGeminiKey() {
        assertNull(summary(SettingsPage.WEB, emptyState))
        val withoutKey = emptyState.copy(geminiKey = KeySlot("GEMINI", isSet = false))
        assertEquals(PageSummary("YouTube needs a Gemini key"), summary(SettingsPage.WEB, withoutKey))
    }

    @Test
    fun pagesWithNothingToFixHaveNoSecondLine() {
        for (page in SettingsPage.entries - setOf(SettingsPage.MODELS, SettingsPage.WEB, SettingsPage.PERMISSIONS)) {
            assertNull(page.name, summary(page, emptyState))
        }
    }

    @Test
    fun permissionsNameTheOneBlockedRow() {
        val rows = listOf(
            PermissionRowUi(PermissionRow.NOTIFICATIONS, PermissionStatus.ALLOWED),
            PermissionRowUi(PermissionRow.CALENDAR, PermissionStatus.BLOCKED),
        )
        assertEquals(PageSummary("Calendar blocked"), summary(SettingsPage.PERMISSIONS, emptyState.copy(permissions = rows)))
    }

    @Test
    fun permissionsCountSeveralWithoutAccessOnceOneIsBlocked() {
        val rows = listOf(
            PermissionRowUi(PermissionRow.NOTIFICATIONS, PermissionStatus.NOT_ALLOWED),
            PermissionRowUi(PermissionRow.CALENDAR, PermissionStatus.BLOCKED),
        )
        assertEquals(PageSummary("2 not allowed"), summary(SettingsPage.PERMISSIONS, emptyState.copy(permissions = rows)))
    }

    @Test
    fun permissionsStayQuietWhenNoneIsBlocked() {
        val rows = listOf(PermissionRowUi(PermissionRow.CALENDAR, PermissionStatus.NOT_ALLOWED))
        assertNull(summary(SettingsPage.PERMISSIONS, emptyState.copy(permissions = rows)))
    }
}
