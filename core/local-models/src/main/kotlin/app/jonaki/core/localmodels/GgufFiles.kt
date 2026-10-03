package app.jonaki.core.localmodels

/**
 * Which files of a repository Jonaki offers, and which one a Download
 * button on a search result takes (D-133).
 */
object GgufFiles {
    /** "-00001-of-00003": one part of a model split over several files, useless alone. */
    private val splitPart = Regex("""-\d{5}-of-\d{5}\.gguf$""", RegexOption.IGNORE_CASE)

    /**
     * The quantization in a file name, for example "Q4_K_M", "IQ4_XS",
     * "Q4_0" or "BF16". The last match wins, because model names can hold
     * look-alikes earlier in the name.
     */
    private val quantization = Regex("""(?<![A-Za-z0-9])(I?Q\d(?:_[A-Z0-9]+)*|BF16|F16|F32|MXFP4)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)

    private val unquantized = setOf("BF16", "F16", "F32")

    /**
     * A .gguf file llama.cpp can load as a chat model by itself. Left out:
     * vision projectors (mmproj), importance matrices (imatrix), split
     * parts, and draft heads for speculative decoding (mtp, eagle).
     */
    fun isLoadableAlone(file: HubFile): Boolean {
        val name = file.fileName.lowercase()
        if (!name.endsWith(".gguf")) return false
        if (name.startsWith("mmproj")) return false
        if ("imatrix" in name) return false
        if (splitPart.containsMatchIn(name)) return false
        if (name.startsWith("mtp-") || name.startsWith("eagle")) return false
        return true
    }

    /** The files the file list shows, smallest first. */
    fun loadable(files: List<HubFile>): List<HubFile> = files.filter(::isLoadableAlone).sortedBy { file -> file.sizeBytes }

    /** "Q4_K_M" from "Qwen3.5-2B-Q4_K_M.gguf"; null when the name has none. */
    fun quantizationOf(file: HubFile): String? {
        val stem = file.fileName.removeSuffix(".gguf").removeSuffix(".GGUF")
        return quantization.findAll(stem).lastOrNull()?.value?.uppercase()
    }

    /**
     * Q4_K_M, else Q4_0, else the smallest other Q4 file. 16- and 32-bit
     * files are never the default: they are three to four times larger for
     * a quality difference a phone user would not notice. Null when the
     * repository has no 4-bit file; the user then picks from the list.
     */
    fun defaultFile(files: List<HubFile>): HubFile? {
        val candidates = loadable(files).filter { file -> quantizationOf(file) !in unquantized }
        return candidates.firstOrNull { file -> quantizationOf(file) == "Q4_K_M" }
            ?: candidates.firstOrNull { file -> quantizationOf(file) == "Q4_0" }
            ?: candidates.firstOrNull { file -> quantizationOf(file)?.startsWith("Q4") == true }
    }
}
