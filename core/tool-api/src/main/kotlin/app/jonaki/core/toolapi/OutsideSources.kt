package app.jonaki.core.toolapi

import java.net.URI

/**
 * Short source labels for [Tool.outsideContentSourceOf]: the host of a link
 * or the name of a file. An empty label means the source is not known, and
 * the wrapper then names only the tool.
 */
object OutsideSources {
    /** "example.com" for "https://example.com/a?b=1"; empty for text that is not a link with a host. */
    fun hostOf(link: String?): String {
        val trimmed = link?.trim().orEmpty()
        val host = runCatching { URI(trimmed).host }.getOrNull()
        return host.orEmpty()
    }

    /** "report.pdf" for "inbox/docs/report.pdf"; empty for a missing path. */
    fun fileNameOf(path: String?): String =
        path?.trim().orEmpty().trimEnd('/').substringAfterLast('/')
}
