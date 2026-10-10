package app.jonaki.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModelsTest {
    private val empty = ChatModels(addedServices = emptyList(), modelsByService = emptyMap(), defaultModelKey = null)

    @Test
    fun theFirstModelAddedBecomesTheDefaultAndAddsItsService() {
        val models = empty.addModel(ChatService.OPENROUTER, "z-ai/glm-5.3-flash")

        assertEquals(listOf(ChatService.OPENROUTER), models.addedServices)
        assertEquals("openrouter:z-ai/glm-5.3-flash", models.defaultModelKey)
    }

    @Test
    fun laterModelsDoNotTakeOverTheDefault() {
        val models = empty
            .addModel(ChatService.OPENROUTER, "z-ai/glm-5.3-flash")
            .addModel(ChatService.DEEPSEEK, "deepseek-flash")

        assertEquals("openrouter:z-ai/glm-5.3-flash", models.defaultModelKey)
        assertEquals(listOf("openrouter:z-ai/glm-5.3-flash", "deepseek:deepseek-flash"), models.allModelKeys)
    }

    @Test
    fun blankOrDuplicateModelIdsAreIgnored() {
        val models = empty
            .addModel(ChatService.OPENROUTER, "  ")
            .addModel(ChatService.OPENROUTER, "a")
            .addModel(ChatService.OPENROUTER, " a ")

        assertEquals(listOf("a"), models.modelsByService[ChatService.OPENROUTER])
    }

    @Test
    fun starringMovesTheDefault() {
        val models = empty.addModel(ChatService.OPENROUTER, "a").addModel(ChatService.GEMINI, "g")

        assertEquals("gemini:g", models.setDefault("gemini:g").defaultModelKey)
    }

    @Test
    fun starringAModelThatIsNotListedChangesNothing() {
        val models = empty.addModel(ChatService.OPENROUTER, "a")

        assertEquals(models, models.setDefault("gemini:g"))
    }

    @Test
    fun removingTheDefaultModelStarsTheFirstRemainingOne() {
        val models = empty
            .addModel(ChatService.OPENROUTER, "a")
            .addModel(ChatService.GEMINI, "g")
            .removeModel("openrouter:a")

        assertEquals("gemini:g", models.defaultModelKey)
        assertEquals(emptyList<String>(), models.modelsByService[ChatService.OPENROUTER])
    }

    @Test
    fun removingAServiceRemovesItsModels() {
        val models = empty
            .addModel(ChatService.OPENROUTER, "a")
            .addModel(ChatService.GEMINI, "g")
            .removeService(ChatService.OPENROUTER)

        assertEquals(listOf(ChatService.GEMINI), models.addedServices)
        assertEquals(listOf("gemini:g"), models.allModelKeys)
        assertEquals("gemini:g", models.defaultModelKey)
    }

    @Test
    fun removingTheLastModelLeavesNoDefault() {
        assertNull(empty.addModel(ChatService.OPENROUTER, "a").removeModel("openrouter:a").defaultModelKey)
    }

    @Test
    fun addingAServiceTwiceKeepsOneCard() {
        val models = empty.addService(ChatService.GLM).addService(ChatService.GLM)

        assertEquals(listOf(ChatService.GLM), models.addedServices)
    }

    @Test
    fun legacySettingsBecomeOneServiceWithItsModelStarred() {
        val models = ChatModels.fromLegacy(
            chatServiceName = "DEEPSEEK",
            savedModels = mapOf("OPENROUTER" to "", "DEEPSEEK" to "deepseek-pro"),
            presetDefaultModel = { service -> "default-of-${service.key}" },
        )

        assertEquals(listOf(ChatService.DEEPSEEK), models.addedServices)
        assertEquals("deepseek:deepseek-pro", models.defaultModelKey)
    }

    @Test
    fun legacySettingsWithABlankModelUseThePresetDefaultOnce() {
        val models = ChatModels.fromLegacy(
            chatServiceName = null,
            savedModels = emptyMap(),
            presetDefaultModel = { service -> "default-of-${service.key}" },
        )

        assertEquals("openrouter:default-of-openrouter", models.defaultModelKey)
    }

    @Test
    fun aFreshInstallHasNoLegacySettings() {
        assertFalse(ChatModels.hasLegacySettings(chatServiceName = null, savedModels = emptyMap()))
        assertFalse(ChatModels.hasLegacySettings(chatServiceName = " ", savedModels = mapOf("OPENROUTER" to "", "GEMINI" to "  ")))
    }

    @Test
    fun aSavedLegacyServiceNameMeansALegacyInstall() {
        assertTrue(ChatModels.hasLegacySettings(chatServiceName = "DEEPSEEK", savedModels = emptyMap()))
    }

    @Test
    fun aNonBlankLegacyModelTextMeansALegacyInstall() {
        assertTrue(ChatModels.hasLegacySettings(chatServiceName = null, savedModels = mapOf("GEMINI" to "gemini-pro")))
    }

    @Test
    fun keyPreviewShowsThreeCharactersAtMost() {
        assertEquals("sk-••••", maskedKeyPreview("sk-or-v1-abcdef"))
        assertEquals("ab••••", maskedKeyPreview("ab"))
    }
}
