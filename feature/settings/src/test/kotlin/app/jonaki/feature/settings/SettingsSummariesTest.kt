package app.jonaki.feature.settings

import app.jonaki.core.ui.AnswerStyleChoice
import app.jonaki.core.ui.ApprovalModeChoice
import app.jonaki.core.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSummariesTest {
    private val texts = EnglishTexts()

    private val emptyState = SettingsUiState(
        geminiKey = KeySlot("GEMINI", isSet = true),
        searchServices = emptyList(),
        webSearchOffInNewThreads = false,
        themeMode = ThemeMode.SYSTEM,
    )

    private fun summary(page: SettingsPage, state: SettingsUiState): String = SettingsSummaries.of(page, state, texts).text

    private fun models(count: Int, service: String): List<ServiceModelUi> =
        (1..count).map { number -> ServiceModelUi("$service:model-$number", "Model $number") }

    private fun chatService(name: String, keySet: Boolean, modelCount: Int, balance: String? = null) = ChatServiceCardUi(
        serviceKey = name.lowercase(),
        displayName = name,
        apiKey = KeySlot(name.uppercase(), isSet = keySet, balance = balance),
        models = models(modelCount, name.lowercase()),
    )

    private fun searchService(name: String, keySet: Boolean) = SearchServiceRow(name.uppercase(), name, KeySlot(name.uppercase(), keySet))

    @Test
    fun modelsNamesTheFirstServiceWithAKeyItsModelsAndBalance() {
        val state = emptyState.copy(
            chatServices = listOf(
                chatService("DeepSeek", keySet = false, modelCount = 1),
                chatService("OpenRouter", keySet = true, modelCount = 3, balance = "$12.82 left"),
            ),
        )
        assertEquals("OpenRouter, 3 models · $12.82 left", summary(SettingsPage.MODELS, state))
    }

    @Test
    fun modelsWithoutABalanceLeaveItOut() {
        val state = emptyState.copy(chatServices = listOf(chatService("DeepSeek", keySet = true, modelCount = 1)))
        assertEquals("DeepSeek, 1 model", summary(SettingsPage.MODELS, state))
    }

    @Test
    fun aServiceThatNeedsNoKeyCounts() {
        val local = ChatServiceCardUi("ollama-local", "Ollama on this network", apiKey = null, models = models(2, "ollama"))
        assertEquals("Ollama on this network, 2 models", summary(SettingsPage.MODELS, emptyState.copy(chatServices = listOf(local))))
    }

    @Test
    fun modelsSaysNoChatServiceWhenNoneIsAdded() {
        assertEquals("No chat service", summary(SettingsPage.MODELS, emptyState))
    }

    @Test
    fun modelsSaysNoKeyWhenNoAddedServiceHasOne() {
        val state = emptyState.copy(chatServices = listOf(chatService("DeepSeek", keySet = false, modelCount = 1)))
        assertEquals("No key", summary(SettingsPage.MODELS, state))
    }

    @Test
    fun webListsSearchServicesWithAKeyInOrder() {
        val state = emptyState.copy(
            searchServices = listOf(
                searchService("Tavily", keySet = true),
                searchService("Exa", keySet = false),
                searchService("Ollama", keySet = true),
            ),
        )
        assertEquals("Tavily, then Ollama", summary(SettingsPage.WEB, state))
    }

    @Test
    fun webWithOneServiceNamesIt() {
        val state = emptyState.copy(searchServices = listOf(searchService("Tavily", keySet = true)))
        assertEquals("Tavily", summary(SettingsPage.WEB, state))
    }

    @Test
    fun webChainsThreeServices() {
        val state = emptyState.copy(
            searchServices = listOf(
                searchService("Tavily", keySet = true),
                searchService("Ollama", keySet = true),
                searchService("Exa", keySet = true),
            ),
        )
        assertEquals("Tavily, then Ollama, then Exa", summary(SettingsPage.WEB, state))
    }

    @Test
    fun webSaysSearchOffWithoutAnyKey() {
        val state = emptyState.copy(searchServices = listOf(searchService("Tavily", keySet = false)))
        assertEquals("Search off", summary(SettingsPage.WEB, state))
    }

    @Test
    fun webAddsAMissingGeminiKey() {
        val state = emptyState.copy(
            geminiKey = KeySlot("GEMINI", isSet = false),
            searchServices = listOf(searchService("Tavily", keySet = true), searchService("Ollama", keySet = true)),
        )
        assertEquals("Tavily, then Ollama · No Gemini key", summary(SettingsPage.WEB, state))
        assertEquals("Search off · No Gemini key", summary(SettingsPage.WEB, state.copy(searchServices = emptyList())))
    }

    @Test
    fun toolsCountsGroupsOnAndNamesTheApprovalMode() {
        val state = emptyState.copy(toolGroupsOn = 12, toolGroupCount = 12, approvalMode = ApprovalModeChoice.ASK)
        assertEquals("12 of 12 tools on · Ask", summary(SettingsPage.TOOLS, state))
        val someOff = state.copy(toolGroupsOn = 9, approvalMode = ApprovalModeChoice.AUTO)
        assertEquals("9 of 12 tools on · Auto", summary(SettingsPage.TOOLS, someOff))
    }

    @Test
    fun answersNamesTheStyleAndCountsPersonas() {
        val normal = emptyState.copy(answerStyle = AnswerStyleChoice.NORMAL)
        assertEquals("Normal · no personas", summary(SettingsPage.ANSWERS, normal))
        val one = normal.copy(personas = listOf(PersonaRowUi("1", "Thesis supervisor")))
        assertEquals("Normal · 1 persona", summary(SettingsPage.ANSWERS, one))
        val three = normal.copy(personas = (1..3).map { number -> PersonaRowUi("$number", "Persona $number") })
        assertEquals("Normal · 3 personas", summary(SettingsPage.ANSWERS, three))
    }

    @Test
    fun memoryAndSkillsCountsBoth() {
        assertEquals("3 facts · 5 skills", summary(SettingsPage.MEMORY_SKILLS, emptyState.copy(factCount = 3, skillCount = 5)))
        assertEquals("1 fact · 0 skills", summary(SettingsPage.MEMORY_SKILLS, emptyState.copy(factCount = 1, skillCount = 0)))
    }

    @Test
    fun filesAndScheduleNamesTheFolderAndCountsScheduledItems() {
        val state = emptyState.copy(
            linkedFolderName = "Jonaki",
            scheduledItems = listOf(ScheduledItemUi("r1", "Call Abba", "Daily · 08:00")),
        )
        assertEquals("Jonaki folder · 1 scheduled", summary(SettingsPage.FILES_SCHEDULE, state))
        assertEquals("No folder · nothing scheduled", summary(SettingsPage.FILES_SCHEDULE, emptyState))
    }

    @Test
    fun localModelsCountsDownloadsAndNamesTheLargestThatFits() {
        val two = emptyState.copy(localModels = LocalModelsSummaryUi(downloadedCount = 2, largestFittingName = "Qwen3.5-2B"))
        assertEquals("2 downloaded · Qwen3.5-2B fits", summary(SettingsPage.LOCAL_MODELS, two))
        assertEquals("None downloaded · No model fits", summary(SettingsPage.LOCAL_MODELS, emptyState))
    }

    @Test
    fun themeNamesTheChoice() {
        assertEquals("Follows the phone", summary(SettingsPage.THEME, emptyState))
        assertEquals("Dark", summary(SettingsPage.THEME, emptyState.copy(themeMode = ThemeMode.DARK)))
        assertEquals("Light", summary(SettingsPage.THEME, emptyState.copy(themeMode = ThemeMode.LIGHT)))
    }

    @Test
    fun permissionsNamesOneBlockedRowInTheErrorColour() {
        val state = emptyState.copy(
            permissions = listOf(
                PermissionRowUi(PermissionRow.NOTIFICATIONS, PermissionStatus.ALLOWED),
                PermissionRowUi(PermissionRow.CALENDAR, PermissionStatus.BLOCKED),
                PermissionRowUi(PermissionRow.PHOTOS, PermissionStatus.SELECTED_PHOTOS),
            ),
        )
        val result = SettingsSummaries.of(SettingsPage.PERMISSIONS, state, texts)
        assertEquals("Calendar blocked", result.text)
        assertTrue(result.needsAttention)
    }

    @Test
    fun permissionsNamesOneNotAllowedRowWithoutTheErrorColour() {
        val state = emptyState.copy(
            permissions = listOf(
                PermissionRowUi(PermissionRow.NOTIFICATIONS, PermissionStatus.ALLOWED),
                PermissionRowUi(PermissionRow.CALENDAR, PermissionStatus.NOT_ALLOWED),
            ),
        )
        val result = SettingsSummaries.of(SettingsPage.PERMISSIONS, state, texts)
        assertEquals("Calendar not allowed", result.text)
        assertFalse(result.needsAttention)
    }

    @Test
    fun alarmsOffAreNotAllowed() {
        val state = emptyState.copy(permissions = listOf(PermissionRowUi(PermissionRow.ALARMS, PermissionStatus.OFF)))
        assertEquals("Alarms & reminders not allowed", summary(SettingsPage.PERMISSIONS, state))
    }

    @Test
    fun permissionsCountsSeveralRowsWithoutAccess() {
        val state = emptyState.copy(
            permissions = listOf(
                PermissionRowUi(PermissionRow.CALENDAR, PermissionStatus.NOT_ALLOWED),
                PermissionRowUi(PermissionRow.ALARMS, PermissionStatus.OFF),
            ),
        )
        val result = SettingsSummaries.of(SettingsPage.PERMISSIONS, state, texts)
        assertEquals("2 not allowed", result.text)
        assertFalse(result.needsAttention)
        val withBlocked = state.copy(permissions = state.permissions + PermissionRowUi(PermissionRow.PHOTOS, PermissionStatus.BLOCKED))
        val blockedResult = SettingsSummaries.of(SettingsPage.PERMISSIONS, withBlocked, texts)
        assertEquals("3 not allowed", blockedResult.text)
        assertTrue(blockedResult.needsAttention)
    }

    @Test
    fun permissionsWithNothingDeniedAreAllAllowedAndNotRed() {
        val state = emptyState.copy(
            permissions = listOf(
                PermissionRowUi(PermissionRow.NOTIFICATIONS, PermissionStatus.ALLOWED),
                PermissionRowUi(PermissionRow.PHOTOS, PermissionStatus.SELECTED_PHOTOS),
            ),
        )
        val result = SettingsSummaries.of(SettingsPage.PERMISSIONS, state, texts)
        assertEquals("All allowed", result.text)
        assertFalse(result.needsAttention)
    }

    private fun withoutAsking(count: Int) = SubagentLimitUi(SubagentLimit.WITHOUT_ASKING, value = count, min = 0, max = 10)

    @Test
    fun subagentsCountsThoseThatStartWithoutAskingAndTheCustomOnes() {
        val defaults = emptyState.copy(subagentLimits = listOf(withoutAsking(2)))
        val custom = emptyState.copy(
            subagentLimits = listOf(withoutAsking(1)),
            customSubagents = listOf(CustomSubagentRowUi("price-checker", "Checks prices."), CustomSubagentRowUi("digest", "Sums up.")),
        )

        assertEquals("2 without asking · no custom", summary(SettingsPage.SUBAGENTS, defaults))
        assertEquals("1 without asking · 2 custom", summary(SettingsPage.SUBAGENTS, custom))
    }

    @Test
    fun aboutShowsTheVersion() {
        assertEquals("Version 1.0.0", summary(SettingsPage.ABOUT, emptyState.copy(appVersion = "1.0.0")))
    }

    @Test
    fun onlyPermissionsCanNeedAttention() {
        for (page in SettingsPage.entries - SettingsPage.PERMISSIONS) {
            assertFalse(page.name, SettingsSummaries.of(page, SettingsSample.state, texts).needsAttention)
        }
    }
}
