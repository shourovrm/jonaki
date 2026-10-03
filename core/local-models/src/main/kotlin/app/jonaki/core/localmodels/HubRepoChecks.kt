package app.jonaki.core.localmodels

/** Why a search result has no Download button. */
enum class RepoBlock {
    /** The repository asks the user to accept terms on the website first. */
    GATED,

    /** llama.cpp b11366 has no such architecture, or the repository gives none. */
    NOT_SUPPORTED,
}

/** The checks a search result and its files go through before a Download button is shown (D-133). */
object HubRepoChecks {
    /** A file's size in bytes over this is taken as the parameter count when the repository gives none: 4 bits a weight. */
    private const val PARAMETERS_PER_FILE_BYTE = 2

    fun blockOf(repo: HubRepo): RepoBlock? {
        if (repo.isGated) {
            return RepoBlock.GATED
        }
        if (!SupportedArchitectures.isSupported(repo.architecture)) {
            return RepoBlock.NOT_SUPPORTED
        }
        return null
    }

    /** For the search row: a typical 4-bit file of the model; null when the repository gives no parameter count. */
    fun estimatedNeed(repo: HubRepo): MemoryNeed? {
        val parameters = repo.totalParameters ?: return null
        return MemoryEstimate.need(MemoryEstimate.fourBitFileBytes(parameters), parameters, repo.architecture)
    }

    /** For one file of the repository: exact for a recommended file, estimated for any other. */
    fun fileNeed(repo: HubRepo, file: HubFile): MemoryNeed {
        val recommended = RecommendedModels.find(repo.id, file.fileName)
        if (recommended != null) {
            return recommended.memoryNeed
        }
        val parameters = repo.totalParameters ?: (file.sizeBytes * PARAMETERS_PER_FILE_BYTE)
        return MemoryEstimate.need(file.sizeBytes, parameters, repo.architecture)
    }
}
