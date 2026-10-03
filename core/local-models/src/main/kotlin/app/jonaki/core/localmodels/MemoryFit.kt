package app.jonaki.core.localmodels

import kotlin.math.max
import kotlin.math.min

/** Whether a model can be loaded on this phone now (D-133). */
enum class FitLabel {
    FITS,

    /** Needs more than 85 % of the budget: it may load, but other apps can push it out. */
    TIGHT,
    TOO_BIG,
}

/**
 * The memory one loaded model takes, in bytes: the weights (the file is
 * mapped as it is), the KV cache for [MemoryFit.CONTEXT_TOKENS] tokens,
 * and llama.cpp's compute buffer.
 */
data class MemoryNeed(
    val fileBytes: Long,
    val kvCacheBytes: Long,
    val computeBufferBytes: Long,
) {
    /** The three parts plus 10 % for everything else llama.cpp allocates (PocketPal's margin). */
    val requiredBytes: Long
        get() = ((fileBytes + kvCacheBytes + computeBufferBytes) * SAFETY_FACTOR).toLong()

    private companion object {
        const val SAFETY_FACTOR = 1.1
    }
}

/**
 * The device-fit rule from docs/research/local-models-2026-10-03.md:
 * required = (file + KV cache + compute buffer) × 1.1, against a budget of
 * min(available memory, 0.6 × total RAM). Example: Qwen3.5-4B Q4_0 needs
 * (2,583 + 268 + 514 MB) × 1.1 = 3.70 GB; the A059 had 2.7 GB available,
 * so it is too big there, while Qwen3.5-2B at 2.01 GB fits.
 */
object MemoryFit {
    /** The context local models get (D-133). */
    const val CONTEXT_TOKENS = 8_192

    /** Android's low-memory killer acts long before RAM is full, so a model never plans on more than 60 % of it. */
    private const val SHARE_OF_TOTAL_RAM = 0.6

    /** Within the last 15 % of the budget the label is Tight. */
    private const val TIGHT_SHARE = 0.85

    fun budgetBytes(availableBytes: Long, totalBytes: Long): Long =
        min(availableBytes, (totalBytes * SHARE_OF_TOTAL_RAM).toLong())

    fun label(need: MemoryNeed, budgetBytes: Long): FitLabel {
        val required = need.requiredBytes
        if (required > budgetBytes) {
            return FitLabel.TOO_BIG
        }
        if (required > budgetBytes * TIGHT_SHARE) {
            return FitLabel.TIGHT
        }
        return FitLabel.FITS
    }
}

/**
 * Memory estimates for models known only by their search-result metadata:
 * total parameters and architecture. The search API has no per-layer data
 * (layer count, KV heads, head size, vocabulary), so every estimate leans
 * high: a model may get a worse label than it deserves, never a better one.
 */
object MemoryEstimate {
    /**
     * KV-cache bytes per token for a model of up to 8 B parameters: 36
     * attention layers × 1,024 KV values per layer (8 KV heads × 128) × 2
     * (keys and values) × 2 bytes (16-bit). That is Qwen3-4B and Qwen3-8B,
     * the largest among common small models with grouped-query attention
     * (Llama 3.2 3B has 28 layers, Qwen2.5-7B 28 layers with 4 KV heads).
     * Older models without grouped-query attention, such as Phi-3-mini
     * (393,216 bytes per token), need more than this.
     */
    private const val KV_BYTES_PER_TOKEN_UP_TO_8B = 147_456L

    /** Above 8 B parameters the per-token cache grows in step with the parameters. */
    private const val REFERENCE_PARAMETERS = 8_000_000_000.0

    /**
     * Architectures where only some layers keep a full-length KV cache.
     * Qwen3.5 has one attention layer in four (the rest are linear
     * attention with a small fixed state); Gemma 3, 3n and 4 keep a short
     * sliding window in most layers (5 of 6 in Gemma 3, 12 of 15 cache
     * layers in Gemma 4 E2B); Gemma 2 alternates sliding and
     * full layers, and LFM2 has 6 attention layers of 16. The shares are
     * rounded up.
     */
    private val fullCacheShare: Map<String, Double> = mapOf(
        "qwen35" to 0.25,
        "qwen35moe" to 0.25,
        "qwen3next" to 0.25,
        "gemma3" to 0.25,
        "gemma3n" to 0.25,
        "gemma4" to 0.25,
        "gemma2" to 0.5,
        "lfm2" to 0.5,
        "lfm2moe" to 0.5,
    )

    /**
     * The compute buffer is (vocabulary + embedding width) × 512 × 4 bytes
     * (PocketPal's rule). The vocabulary is taken as 262,144 tokens, the
     * largest among current small models (Gemma's; Qwen3.5 has 248,320).
     */
    private const val LARGEST_VOCABULARY = 262_144L
    private const val EMBEDDING_WIDTH_UP_TO_8B = 4_096L
    private const val EMBEDDING_WIDTH_ABOVE_8B = 8_192L
    private const val COMPUTE_BYTES_PER_ROW = 512L * 4

    /**
     * A 4-bit file's size per parameter. Embedding and output tables stay at
     * higher precision, so small models have more bits per weight: the
     * recommended Q4_K_M files range from 5.21 (Qwen3.5-4B) to 5.66 bits
     * (Qwen3.5-0.8B). 5.7 bits covers all four.
     */
    private const val FOUR_BIT_FILE_BITS_PER_PARAMETER = 5.7

    fun kvCacheBytes(totalParameters: Long, architecture: String?, contextTokens: Int = MemoryFit.CONTEXT_TOKENS): Long {
        val sizeFactor = max(1.0, totalParameters / REFERENCE_PARAMETERS)
        val share = fullCacheShare[architecture] ?: 1.0
        return (KV_BYTES_PER_TOKEN_UP_TO_8B * contextTokens * sizeFactor * share).toLong()
    }

    fun computeBufferBytes(totalParameters: Long): Long {
        val embeddingWidth = if (totalParameters <= REFERENCE_PARAMETERS) EMBEDDING_WIDTH_UP_TO_8B else EMBEDDING_WIDTH_ABOVE_8B
        return (LARGEST_VOCABULARY + embeddingWidth) * COMPUTE_BYTES_PER_ROW
    }

    /** For a search result, before its file list is known: the size of a typical 4-bit file. */
    fun fourBitFileBytes(totalParameters: Long): Long = (totalParameters * FOUR_BIT_FILE_BITS_PER_PARAMETER / 8).toLong()

    /** The memory one file of a repository needs, with the cache and buffer estimated from the repository's metadata. */
    fun need(fileBytes: Long, totalParameters: Long, architecture: String?): MemoryNeed = MemoryNeed(
        fileBytes = fileBytes,
        kvCacheBytes = kvCacheBytes(totalParameters, architecture),
        computeBufferBytes = computeBufferBytes(totalParameters),
    )
}
