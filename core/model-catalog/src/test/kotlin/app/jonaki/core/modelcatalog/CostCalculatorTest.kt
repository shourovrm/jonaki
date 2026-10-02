package app.jonaki.core.modelcatalog

import app.jonaki.core.providerapi.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CostCalculatorTest {
    private val glm = ModelInfo(
        serviceKey = "openrouter",
        modelId = "z-ai/glm-5.3-flash",
        displayName = "GLM 5.3 Flash",
        contextWindowTokens = 1_048_576,
        inputUsdPerMillion = 0.15,
        outputUsdPerMillion = 0.50,
        cachedInputUsdPerMillion = 0.03,
    )

    @Test
    fun reportedCostWins() {
        val usage = Usage(1_000_000, 1_000_000, costUsd = 0.0042)

        assertEquals(0.0042, CostCalculator.costUsd(usage, glm)!!, 1e-12)
    }

    @Test
    fun uncachedInputAndOutputArePricedPerMillion() {
        // 2,000 in at 0.15 per million plus 1,000 out at 0.50 per million.
        val usage = Usage(inputTokens = 2_000, outputTokens = 1_000)

        assertEquals(0.0008, CostCalculator.costUsd(usage, glm)!!, 1e-12)
    }

    @Test
    fun cachedInputIsPricedAtTheCachedRate() {
        // 10,000 in of which 8,000 cached: 2,000 x 0.15 + 8,000 x 0.03, per million.
        val usage = Usage(inputTokens = 10_000, outputTokens = 0, cachedInputTokens = 8_000)

        assertEquals(0.00054, CostCalculator.costUsd(usage, glm)!!, 1e-12)
    }

    @Test
    fun cachedInputFallsBackToTheInputRateWhenNoCachedRateIsKnown() {
        val usage = Usage(inputTokens = 10_000, outputTokens = 0, cachedInputTokens = 8_000)

        val cost = CostCalculator.costUsd(usage, glm.copy(cachedInputUsdPerMillion = null))

        assertEquals(0.0015, cost!!, 1e-12)
    }

    @Test
    fun unknownModelOrPriceGivesNoCost() {
        val usage = Usage(1_000, 1_000)

        assertNull(CostCalculator.costUsd(usage, null))
        assertNull(CostCalculator.costUsd(usage, glm.copy(inputUsdPerMillion = null)))
    }
}
