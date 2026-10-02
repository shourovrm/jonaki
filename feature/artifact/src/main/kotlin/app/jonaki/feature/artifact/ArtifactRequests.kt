package app.jonaki.feature.artifact

import java.io.File
import java.net.URI
import java.net.URISyntaxException

/** What the viewer answers to one request from the page. */
sealed interface ArtifactResponse {
    data class File(val file: java.io.File, val mimeType: String, val headers: Map<String, String>) : ArtifactResponse

    /** The Chart.js copy bundled with the app (D-048). */
    data object ChartLibrary : ArtifactResponse

    /** Anything on the internet or outside artifacts/ gets an empty 403. */
    data object Blocked : ArtifactResponse
}

/**
 * Decides what an artifact page may load (D-018, D-047). Pages are served
 * from a made-up https host, so relative links work, and only files inside
 * the thread's artifacts/ folder and the bundled Chart.js are answered;
 * every other request is blocked, which keeps the page off the network.
 */
class ArtifactRequests(threadFolder: java.io.File) {
    private val artifactsRoot: java.io.File = java.io.File(threadFolder, ARTIFACTS_FOLDER).canonicalFile
    private val threadRoot: java.io.File = threadFolder.canonicalFile

    fun resolve(url: String): ArtifactResponse {
        val uri = try {
            URI(url)
        } catch (badAddress: URISyntaxException) {
            return ArtifactResponse.Blocked
        }
        if (uri.scheme != SCHEME || uri.host != HOST) {
            return ArtifactResponse.Blocked
        }
        // URI.path is already percent-decoded, so "%2e%2e" arrives as ".." and is caught below.
        val path = uri.path.orEmpty().removePrefix("/")
        if (path.endsWith(CHART_LIBRARY_PATH)) {
            return ArtifactResponse.ChartLibrary
        }
        val file = java.io.File(threadRoot, path).canonicalFile
        val isInsideArtifacts = file.path.startsWith(artifactsRoot.path + java.io.File.separator)
        if (!isInsideArtifacts || !file.isFile) {
            return ArtifactResponse.Blocked
        }
        val mimeType = mimeTypeOf(file.name)
        val headers = if (mimeType == "text/html") mapOf("Content-Security-Policy" to CONTENT_POLICY) else emptyMap()
        return ArtifactResponse.File(file, mimeType, headers)
    }

    private fun mimeTypeOf(fileName: String): String =
        MIME_TYPES[fileName.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

    companion object {
        const val SCHEME = "https"
        const val HOST = "artifact.jonaki"
        private const val ARTIFACTS_FOLDER = "artifacts"
        private const val CHART_LIBRARY_PATH = "/lib/chart.js"

        /**
         * A second wall behind the request filter: no fetch, XHR or WebSocket
         * at all, and scripts, styles and images only from the page's own host.
         */
        const val CONTENT_POLICY =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' data: blob:; connect-src 'none'; " +
                "frame-src 'none'; form-action 'none'"

        private val MIME_TYPES = mapOf(
            "html" to "text/html",
            "css" to "text/css",
            "js" to "text/javascript",
            "json" to "application/json",
            "svg" to "image/svg+xml",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "woff2" to "font/woff2",
            "woff" to "font/woff",
            "ttf" to "font/ttf",
            "csv" to "text/csv",
            "txt" to "text/plain",
        )

        /** The address the viewer opens for a path relative to the thread folder. */
        fun urlOf(relativePath: String): String = "$SCHEME://$HOST/$relativePath"
    }
}
