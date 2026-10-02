package app.jonaki.tools.youtubesummarize

import java.net.URI

/** Reads the video id out of the link shapes YouTube uses. */
internal object YouTubeLinks {
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")

    fun videoId(link: String): String? {
        val withScheme = if (link.startsWith("http://") || link.startsWith("https://")) link else "https://$link"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val pathParts = uri.path.orEmpty().split("/").filter { it.isNotEmpty() }

        val candidate = when {
            host == "youtu.be" -> pathParts.firstOrNull()
            host !in youtubeHosts -> null
            pathParts.firstOrNull() == "watch" -> queryParameter(uri.rawQuery, "v")
            pathParts.firstOrNull() in setOf("shorts", "live", "embed") -> pathParts.getOrNull(1)
            else -> null
        }
        return candidate?.takeIf { videoIdPattern.matches(it) }
    }

    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    private fun queryParameter(rawQuery: String?, name: String): String? =
        rawQuery.orEmpty()
            .split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.first() == name }
            ?.getOrNull(1)
}
