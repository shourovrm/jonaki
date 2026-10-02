package app.jonaki.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentModelChoiceTest {
    private val scoped = listOf("openrouter:a", "openrouter:b", "gemini:c")

    private fun choose(
        agentType: String,
        requested: String? = null,
        saved: Map<String, String> = emptyMap(),
        background: String? = null,
    ) = SubagentModelChoice.choose(agentType, requested, saved, scoped, threadModelKey = "openrouter:a", backgroundModelKey = background)

    @Test
    fun aTypeWithoutASettingUsesTheThreadsModel() {
        assertEquals("openrouter:a", choose("researcher", background = "gemini:c"))
    }

    @Test
    fun theScoutUsesTheBackgroundModelWhenThereIsOne() {
        assertEquals("gemini:c", choose("scout", background = "gemini:c"))
        assertEquals("openrouter:a", choose("scout", background = null))
    }

    @Test
    fun theSettingWins() {
        assertEquals("openrouter:b", choose("scout", saved = mapOf("scout" to "openrouter:b"), background = "gemini:c"))
    }

    @Test
    fun aSettingForAModelNoLongerScopedIsIgnored() {
        assertEquals("openrouter:a", choose("writer", saved = mapOf("writer" to "openrouter:gone")))
    }

    @Test
    fun aModelNamedInTheCallWinsOverAll() {
        assertEquals("gemini:c", choose("writer", requested = "gemini:c", saved = mapOf("writer" to "openrouter:b")))
    }

    @Test
    fun settingsSurviveTheirTextForm() {
        val saved = mapOf("scout" to "ollama-local:qwen3:8b", "writer" to "openrouter:b")
        assertEquals(saved, SubagentModelChoice.fromText(SubagentModelChoice.toText(saved)))
    }
}
