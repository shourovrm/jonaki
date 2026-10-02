package app.jonaki.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundModelTest {
    /** Input plus output price per million tokens. */
    private val prices = mapOf(
        "openrouter:z-ai/glm-5.3" to 0.6 + 2.2,
        "deepseek:deepseek-chat" to 0.27 + 1.1,
        "gemini:gemini-3.8-flash" to 0.3 + 2.5,
    )

    private fun choose(
        scoped: List<String>,
        servicesWithKey: Set<String> = setOf("openrouter", "deepseek", "gemini"),
        threadModel: String? = "openrouter:z-ai/glm-5.3",
    ) = BackgroundModel.choose(
        scopedModelKeys = scoped,
        hasKey = { modelKey -> modelKey.substringBefore(':') in servicesWithKey },
        pricePerMillion = { modelKey -> prices[modelKey] },
        threadModelKey = threadModel,
    )

    @Test
    fun picksTheCheapestScopedModelByInputPlusOutputPrice() {
        assertEquals("deepseek:deepseek-chat", choose(prices.keys.toList()))
    }

    @Test
    fun skipsModelsWhoseServiceHasNoKey() {
        assertEquals("gemini:gemini-3.8-flash", choose(prices.keys.toList(), servicesWithKey = setOf("openrouter", "gemini")))
    }

    @Test
    fun skipsModelsWithoutAKnownPrice() {
        assertEquals("openrouter:z-ai/glm-5.3", choose(listOf("ollama-local:qwen3", "openrouter:z-ai/glm-5.3")))
    }

    @Test
    fun fallsBackToTheThreadModelWhenNoScopedModelQualifies() {
        assertEquals("ollama-local:qwen3", choose(listOf("ollama-local:qwen3"), threadModel = "ollama-local:qwen3"))
    }

    @Test
    fun noModelAtAllGivesNull() {
        assertNull(choose(emptyList(), threadModel = null))
    }

    @Test
    fun equalPricesKeepTheScopedOrder() {
        val samePrice = mapOf("a:one" to 1.0, "b:two" to 1.0)
        val chosen = BackgroundModel.choose(
            scopedModelKeys = listOf("b:two", "a:one"),
            hasKey = { true },
            pricePerMillion = { samePrice[it] },
            threadModelKey = null,
        )
        assertEquals("b:two", chosen)
    }
}
