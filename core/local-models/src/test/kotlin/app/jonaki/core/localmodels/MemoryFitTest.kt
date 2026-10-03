package app.jonaki.core.localmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryFitTest {
    private val gigabyte = 1_000_000_000L

    /** The A059 on 2026-10-03: 7.6 GB visible to Android, 2.7 GB available (research doc). */
    private val a059Budget = MemoryFit.budgetBytes(availableBytes = 2_700_000_000, totalBytes = 7_600_000_000)

    /** An 8 GB phone with most of its memory free: the 0.6 × RAM cap applies. */
    private val eightGigabyteBudget = MemoryFit.budgetBytes(availableBytes = 6 * gigabyte, totalBytes = 8 * gigabyte)

    @Test
    fun theResearchDocsWorkedExample() {
        // (2,583 + 268 + 514 MB) × 1.1 ≈ 3.7 GB.
        val need = MemoryNeed(fileBytes = 2_583_221_408, kvCacheBytes = 268_435_456, computeBufferBytes = 513_802_240)
        assertEquals(3_702_005_014.0, need.requiredBytes.toDouble(), 1.0)
        assertEquals(need, RecommendedModels.QWEN35_4B.memoryNeed)
        assertEquals(4_800_000_000, eightGigabyteBudget)
        assertEquals(FitLabel.FITS, MemoryFit.label(need, eightGigabyteBudget))
    }

    @Test
    fun theBudgetIsTheSmallerOfAvailableMemoryAndSixtyPercentOfRam() {
        assertEquals(2_700_000_000, a059Budget)
        assertEquals(4_800_000_000, MemoryFit.budgetBytes(availableBytes = 7 * gigabyte, totalBytes = 8 * gigabyte))
    }

    @Test
    fun onTheA059TwoBillionFitsAndFourBillionIsTooBig() {
        // The research doc: "Qwen3.5-2B needs about 2.0 GB".
        assertEquals(2.01, RecommendedModels.QWEN35_2B.memoryNeed.requiredBytes / 1e9, 0.01)
        assertEquals(FitLabel.FITS, MemoryFit.label(RecommendedModels.QWEN35_2B.memoryNeed, a059Budget))
        assertEquals(FitLabel.TOO_BIG, MemoryFit.label(RecommendedModels.QWEN35_4B.memoryNeed, a059Budget))
        assertEquals(RecommendedModels.QWEN35_2B, RecommendedModels.largestThatFits(a059Budget))
    }

    @Test
    fun theLastFifteenPercentOfTheBudgetIsTight() {
        // Required = 1,000 × 1.1 = 1,100 bytes.
        val need = MemoryNeed(fileBytes = 1_000, kvCacheBytes = 0, computeBufferBytes = 0)
        assertEquals(FitLabel.FITS, MemoryFit.label(need, budgetBytes = 1_295))
        assertEquals(FitLabel.TIGHT, MemoryFit.label(need, budgetBytes = 1_290))
        assertEquals(FitLabel.TIGHT, MemoryFit.label(need, budgetBytes = 1_100))
        assertEquals(FitLabel.TOO_BIG, MemoryFit.label(need, budgetBytes = 1_099))
    }

    @Test
    fun noRecommendedModelFitsATinyBudget() {
        assertNull(RecommendedModels.largestThatFits(500_000_000))
    }

    @Test
    fun theEstimateIsNeverBelowTheExactFiguresOfTheRecommendedModels() {
        val parameters = mapOf(
            RecommendedModels.QWEN35_0_8B to Pair(752_393_024L, "qwen35"),
            RecommendedModels.QWEN35_2B to Pair(1_881_825_088L, "qwen35"),
            RecommendedModels.QWEN35_4B to Pair(4_205_751_296L, "qwen35"),
            RecommendedModels.GEMMA4_E2B to Pair(4_647_450_147L, "gemma4"),
        )
        for ((model, metadata) in parameters) {
            val (totalParameters, architecture) = metadata
            val kvCache = MemoryEstimate.kvCacheBytes(totalParameters, architecture)
            val computeBuffer = MemoryEstimate.computeBufferBytes(totalParameters)
            assertTrue("${model.name} KV cache", kvCache >= model.kvCacheBytes)
            assertTrue("${model.name} compute buffer", computeBuffer >= model.computeBufferBytes)
            assertTrue("${model.name} file", MemoryEstimate.fourBitFileBytes(totalParameters) >= model.sizeBytes)
        }
    }

    @Test
    fun theEstimateForTheWorkedExampleModel() {
        // 147,456 bytes × 8,192 tokens × 1/4 (one attention layer in four) = 301,989,888, against the exact 268,435,456.
        assertEquals(301_989_888L, MemoryEstimate.kvCacheBytes(4_205_751_296, "qwen35"))
        // (262,144 + 4,096) × 2,048 = 545,259,520, against the exact 513,802,240.
        assertEquals(545_259_520L, MemoryEstimate.computeBufferBytes(4_205_751_296))
    }

    @Test
    fun aDenseModelOfUnknownLayoutGetsTheFullCacheAndLargerModelsMore() {
        assertEquals(1_207_959_552L, MemoryEstimate.kvCacheBytes(3_000_000_000, "llama"))
        assertEquals(2_415_919_104L, MemoryEstimate.kvCacheBytes(16_000_000_000, "llama"))
    }
}
