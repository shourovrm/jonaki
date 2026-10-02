package app.jonaki.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSearchTest {
    private val models = listOf(
        AddableModelUi("deepseek/deepseek-v4", "DeepSeek V4"),
        AddableModelUi("deepseek/deepseek-v4-flash", "DeepSeek V4 Flash"),
        AddableModelUi("z-ai/glm-5.3-flash", "GLM 5.3 Flash"),
        AddableModelUi("anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5"),
    )

    private fun ids(query: String) = filterModels(models, query).map { it.id }

    @Test
    fun blankQueryKeepsEveryModel() {
        assertEquals(4, ids("  ").size)
    }

    @Test
    fun matchesNameOrIdIgnoringCase() {
        assertEquals(listOf("anthropic/claude-sonnet-5.5"), ids("ANTHROPIC"))
        assertEquals(listOf("anthropic/claude-sonnet-5.5"), ids("sonnet"))
    }

    @Test
    fun everyWordMustMatchSomewhere() {
        assertEquals(listOf("deepseek/deepseek-v4-flash"), ids("deepseek flash"))
        assertEquals(listOf("deepseek/deepseek-v4-flash", "z-ai/glm-5.3-flash"), ids("flash"))
    }

    @Test
    fun noMatchGivesAnEmptyList() {
        assertEquals(emptyList<String>(), ids("llama"))
    }

    @Test
    fun freeTextIdIsOfferedOnlyWhenNothingMatchesExactly() {
        assertEquals("my/custom-model", freeTextModelId(models, " my/custom-model "))
        assertEquals(null, freeTextModelId(models, "z-ai/glm-5.3-flash"))
        assertEquals(null, freeTextModelId(models, "   "))
    }
}
