package app.jonaki.core.localmodels

/**
 * One model the Local models page offers before any search (D-133). The
 * file is pinned to a commit, so its size and SHA-256 cannot change under
 * the app. Unlike search results, the KV cache and compute buffer are exact,
 * worked out from the model's config.json at the context of
 * [MemoryFit.CONTEXT_TOKENS].
 */
data class RecommendedModel(
    /** "Qwen3.5-2B", as the list shows it. */
    val name: String,
    val repoId: String,
    val fileName: String,
    /** The commit of the repository the file was read from. */
    val revision: String,
    val sizeBytes: Long,
    val sha256: String,
    val kvCacheBytes: Long,
    val computeBufferBytes: Long,
) {
    val memoryNeed: MemoryNeed get() = MemoryNeed(sizeBytes, kvCacheBytes, computeBufferBytes)

    val downloadUrl: String get() = HuggingFaceClient.downloadUrl(repoId, revision, fileName)
}

/**
 * Values read with curl on 2026-10-03 from huggingface.co/api/models/<repo>
 * (sha) and /tree/main (size, lfs.oid); the resolve address's
 * x-linked-etag header carries the same SHA-256. KV cache = layers with a
 * full cache × 8,192 tokens × KV heads × head size × 2 × 2 bytes; compute
 * buffer = (vocabulary + embedding width) × 512 × 4 bytes.
 */
object RecommendedModels {
    /**
     * Qwen3.5-0.8B: 6 full-attention layers of 24, 2 KV heads of 256:
     * 6 × 8,192 × 2 × 256 × 4 = 100,663,296. Buffer (248,320 + 1,024) × 2,048.
     */
    val QWEN35_0_8B = RecommendedModel(
        name = "Qwen3.5-0.8B",
        repoId = "unsloth/Qwen3.5-0.8B-GGUF",
        fileName = "Qwen3.5-0.8B-Q4_0.gguf",
        revision = "6ab461498e2023f6e3c1baea90a8f0fe38ab64d0",
        sizeBytes = 507_154_688,
        sha256 = "444406ddd926550c724ec18d5120a9d40ded44908a063b0e66e9a7e5464c652c",
        kvCacheBytes = 100_663_296,
        computeBufferBytes = 510_656_512,
    )

    /** Qwen3.5-2B: the same cache as 0.8B (6 of 24 layers, 2 KV heads); buffer (248,320 + 2,048) × 2,048. */
    val QWEN35_2B = RecommendedModel(
        name = "Qwen3.5-2B",
        repoId = "unsloth/Qwen3.5-2B-GGUF",
        fileName = "Qwen3.5-2B-Q4_0.gguf",
        revision = "f6d5376be1edb4d416d56da11e5397a961aca8ae",
        sizeBytes = 1_214_873_856,
        sha256 = "cd70221bebaee0503e0f6717e174250cd7825aa88438b3aabec9ad55731d9bb1",
        kvCacheBytes = 100_663_296,
        computeBufferBytes = 512_753_664,
    )

    /** Qwen3.5-4B: 8 of 32 layers, 4 KV heads of 256: 268,435,456; buffer (248,320 + 2,560) × 2,048 (the research doc's example). */
    val QWEN35_4B = RecommendedModel(
        name = "Qwen3.5-4B",
        repoId = "unsloth/Qwen3.5-4B-GGUF",
        fileName = "Qwen3.5-4B-Q4_0.gguf",
        revision = "e87f176479d0855a907a41277aca2f8ee7a09523",
        sizeBytes = 2_583_221_408,
        sha256 = "298fcb5fe7a77ccc79745ae24751560c5ac56874caff4bb39b1f2055bd72b8bb",
        kvCacheBytes = 268_435_456,
        computeBufferBytes = 513_802_240,
    )

    /**
     * Gemma 4 E2B: 15 of 35 layers keep a cache (the other 20 share it), 1 KV
     * head; 3 full layers of head size 512 (3 × 8,192 × 512 × 4 =
     * 50,331,648) and 12 sliding layers of head size 256 over 1,024 cells
     * (the 512-token window plus a 512-token batch: 12,582,912). Buffer
     * (262,144 + 1,536) × 2,048.
     */
    val GEMMA4_E2B = RecommendedModel(
        name = "Gemma 4 E2B",
        repoId = "unsloth/gemma-4-E2B-it-GGUF",
        fileName = "gemma-4-E2B-it-Q4_0.gguf",
        revision = "0314792d7f1f7e229411f620751375812bb9faf2",
        sizeBytes = 3_041_378_400,
        sha256 = "31d3a3c630d4e71a7416498c42660dd3805066948acaec76a47e1ffac7010132",
        kvCacheBytes = 62_914_560,
        computeBufferBytes = 540_016_640,
    )

    /** Smallest first, the order the page lists them in. */
    val ALL: List<RecommendedModel> = listOf(QWEN35_0_8B, QWEN35_2B, QWEN35_4B, GEMMA4_E2B)

    /** The recommended entry for a file of a repository, so its exact memory figures replace the estimate. */
    fun find(repoId: String, fileName: String): RecommendedModel? =
        ALL.firstOrNull { model -> model.repoId.equals(repoId, ignoreCase = true) && model.fileName == fileName }

    /**
     * The largest recommended model that fits [budgetBytes], for the
     * Settings summary ("Qwen3.5-2B fits"); null when none does.
     */
    fun largestThatFits(budgetBytes: Long): RecommendedModel? = ALL
        .filter { model -> MemoryFit.label(model.memoryNeed, budgetBytes) == FitLabel.FITS }
        .maxByOrNull { model -> model.memoryNeed.requiredBytes }
}
