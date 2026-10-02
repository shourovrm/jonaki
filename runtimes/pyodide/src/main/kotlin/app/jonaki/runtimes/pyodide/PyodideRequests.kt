package app.jonaki.runtimes.pyodide

import app.jonaki.core.runtimeapi.InputFile
import java.net.URI
import java.net.URISyntaxException

/** What the hidden WebView gets for one request. */
sealed interface PyodideResponse {
    /** A page or script of the harness, shipped in this module's resources. */
    data class Resource(val name: String, val mimeType: String) : PyodideResponse

    data class File(val file: java.io.File, val mimeType: String) : PyodideResponse

    /** Everything else, the internet included, gets an empty 403. */
    data object Blocked : PyodideResponse
}

/**
 * Decides what the Python WebView may load, the first of three walls that
 * keep programs offline: this filter, the Content-Security-Policy, and
 * Python running in a worker with no frames or WebRTC. It answers only the
 * harness, the installed Pyodide files and the run's input files, all from
 * a made-up host, as the artifact viewer does (D-047).
 */
class PyodideRequests(
    private val folder: PyodideFolder,
    installedPackages: List<LockPackage>,
    inputFiles: List<InputFile>,
) {
    private val packageFiles: Map<String, java.io.File> =
        installedPackages.associate { lockPackage -> lockPackage.fileName to folder.packageFile(lockPackage) }
    private val coreNames: Set<String> = folder.release.coreFiles.map { pinned -> pinned.name }.toSet()
    private val inputs: Map<String, java.io.File> = inputFiles.associate { input -> input.relativePath to input.file }

    fun resolve(url: String): PyodideResponse {
        val uri = try {
            URI(url)
        } catch (badAddress: URISyntaxException) {
            return PyodideResponse.Blocked
        }
        if (uri.scheme != SCHEME || uri.host != HOST) {
            return PyodideResponse.Blocked
        }
        // URI.path is percent-decoded; names are matched exactly, so ".." never reaches a file.
        val path = uri.path.orEmpty().removePrefix("/")
        return when {
            path in HARNESS_FILES -> PyodideResponse.Resource(path, mimeTypeOf(path))
            path.startsWith(PYODIDE_PREFIX) -> pyodideFile(path.removePrefix(PYODIDE_PREFIX))
            path.startsWith(INPUT_PREFIX) -> inputFile(path.removePrefix(INPUT_PREFIX))
            else -> PyodideResponse.Blocked
        }
    }

    private fun pyodideFile(name: String): PyodideResponse {
        val file = when (name) {
            in coreNames -> folder.coreFile(name)
            in packageFiles -> packageFiles.getValue(name)
            else -> return PyodideResponse.Blocked
        }
        return PyodideResponse.File(file, mimeTypeOf(name))
    }

    private fun inputFile(relativePath: String): PyodideResponse {
        val file = inputs[relativePath] ?: return PyodideResponse.Blocked
        return PyodideResponse.File(file, "application/octet-stream")
    }

    private fun mimeTypeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        // WebAssembly.instantiateStreaming refuses any other type.
        "wasm" -> "application/wasm"
        "json" -> "application/json"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    companion object {
        const val SCHEME = "https"
        const val HOST = "python.jonaki"
        const val HARNESS_URL = "$SCHEME://$HOST/harness.html"
        private const val PYODIDE_PREFIX = "pyodide/"
        private const val INPUT_PREFIX = "input/"
        val HARNESS_FILES = setOf("harness.html", "harness.js", "python-worker.js")

        /**
         * Sent with every response, the worker script included, which makes
         * it the worker's policy too. Pyodide needs eval and WebAssembly;
         * fetch, XHR and WebSocket may reach only this host.
         */
        const val CONTENT_POLICY =
            "default-src 'none'; script-src 'self' 'unsafe-eval' 'wasm-unsafe-eval'; connect-src 'self'; " +
                "worker-src 'self'; frame-src 'none'; form-action 'none'"
    }
}
