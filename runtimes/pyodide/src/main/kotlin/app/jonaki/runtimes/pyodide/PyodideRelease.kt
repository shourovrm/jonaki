package app.jonaki.runtimes.pyodide

/**
 * One Pyodide release: where it is downloaded from and the SHA-256 of every
 * core file. Packages are checked against the sha256 in the release's
 * pyodide-lock.json, which is itself a pinned core file, so every byte the
 * app runs traces back to the hashes in this file.
 */
data class PyodideRelease(
    val version: String,
    /** Ends with "/"; core files and packages are both under it. */
    val baseUrl: String,
    val coreFiles: List<PinnedFile>,
) {
    val coreDownloadBytes: Long = coreFiles.sumOf { file -> file.sizeBytes }

    companion object {
        const val LOCK_FILE_NAME = "pyodide-lock.json"

        /** numpy and pandas; their dependencies come from the lock file. */
        val DATA_ADD_ON: List<String> = listOf("numpy", "pandas")

        /**
         * Pyodide 314.0.7 (Python 3.14.2), the latest stable release on
         * 2026-10-03. The hashes match the CDN files, the npm package
         * pyodide@314.0.7 (its sha512 integrity checked against the npm
         * registry) and the GitHub release archive pyodide-core-314.0.7.tar.bz2
         * (D-069).
         */
        val PINNED: PyodideRelease = PyodideRelease(
            version = "314.0.7",
            baseUrl = "https://cdn.jsdelivr.net/pyodide/v314.0.7/full/",
            coreFiles = listOf(
                PinnedFile(
                    name = "pyodide.js",
                    sha256 = "3141b814715a72e59b51b1b18b9ceae5bf19f7c852417e431bb0a34feadf825c",
                    sizeBytes = 18_912,
                ),
                PinnedFile(
                    name = "pyodide.asm.mjs",
                    sha256 = "f7cdc8ece80678ceb712f8e65ebe6d3a83203a180c399865f49612a051693635",
                    sizeBytes = 1_250_344,
                ),
                PinnedFile(
                    name = "pyodide.asm.wasm",
                    sha256 = "cc36e3cab04fdfc9a63ff13eb52eae2b911bf46c025cc7b281f394bd3de1d5e6",
                    sizeBytes = 9_598_218,
                ),
                PinnedFile(
                    name = "python_stdlib.zip",
                    sha256 = "fa1957e5777068fc4f7437f96d860ae2fbe9c19732ba06c84e004ec16dd7dd7a",
                    sizeBytes = 2_545_637,
                ),
                PinnedFile(
                    name = LOCK_FILE_NAME,
                    sha256 = "5dc2fc119108bc148c7457dc86e7675b5c87e1cafd420b9c34c1eaef7b36c010",
                    sizeBytes = 119_077,
                ),
            ),
        )
    }
}

data class PinnedFile(
    val name: String,
    /** Lowercase hexadecimal. */
    val sha256: String,
    val sizeBytes: Long,
)
