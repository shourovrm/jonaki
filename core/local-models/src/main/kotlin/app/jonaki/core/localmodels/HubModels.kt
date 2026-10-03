package app.jonaki.core.localmodels

/**
 * One Hugging Face repository from the model search (D-133). The numbers
 * come from the repository's GGUF metadata (`expand[]=gguf`), which
 * Hugging Face reads from one of its .gguf files; any of them can be
 * missing.
 */
data class HubRepo(
    /** "unsloth/Qwen3.5-2B-GGUF". */
    val id: String,
    val downloads: Long,
    /** True when Hugging Face asks the user to accept terms before a download ("auto" or "manual"). */
    val isGated: Boolean,
    /** llama.cpp's name for the model family, for example "qwen35" or "gemma3". */
    val architecture: String?,
    /** All weights of the model, for example 1,881,825,088 for Qwen3.5-2B. */
    val totalParameters: Long?,
    /** The longest context the model was trained for, in tokens. */
    val contextLength: Long?,
    /** The model card's licence id, for example "apache-2.0". */
    val license: String?,
)

/** One file in a repository's main branch, from the tree listing. */
data class HubFile(
    /** Path inside the repository, for example "Qwen3.5-2B-Q4_0.gguf". */
    val path: String,
    val sizeBytes: Long,
    /** The file's SHA-256 in lower-case hex: Git LFS's oid. Null for small files kept in Git itself. */
    val sha256: String?,
) {
    /** The name without any folder, which is also the local model's id once downloaded. */
    val fileName: String get() = path.substringAfterLast('/')
}
