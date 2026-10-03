package app.jonaki.providers.localllama

/**
 * The ARM features libjonaki_llama.so is compiled for
 * (armv8.2-a+dotprod+fp16+i8mm), by their names in /proc/cpuinfo.
 */
object CpuFeatures {
    private val required = listOf(
        "asimddp", // dot product
        "asimdhp", // half-precision arithmetic
        "i8mm", // 8-bit integer matrix multiply
    )

    private val whitespace = Regex("\\s+")

    /** The required features absent from [cpuinfo]; empty when the library can run. */
    fun missing(cpuinfo: String): List<String> {
        val featureLines = cpuinfo.lines().filter { line -> line.startsWith("Features") }
        if (featureLines.isEmpty()) {
            return required
        }
        // Every core must have them, because a thread can run on any core.
        return required.filter { feature ->
            featureLines.any { line -> feature !in line.substringAfter(':').trim().split(whitespace) }
        }
    }
}
