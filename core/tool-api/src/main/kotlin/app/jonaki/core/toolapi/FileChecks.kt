package app.jonaki.core.toolapi

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher

/** True when the file's start holds a zero byte, which text files never contain. */
fun looksBinary(file: File): Boolean {
    val start = ByteArray(8_000)
    val count = file.inputStream().use { stream -> stream.read(start) }
    if (count <= 0) {
        return false
    }
    return (0 until count).any { index -> start[index] == 0.toByte() }
}

/**
 * Matches paths against a glob such as `work/` + `*.md` or `report-*.html`.
 * A pattern without a slash also matches the file name alone, so `*.md`
 * finds Markdown files in every folder, as users expect.
 */
class GlobFilter(pattern: String) {
    private val matcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
    private val matchesNameAlone = !pattern.contains('/')

    fun matches(relativePath: Path): Boolean {
        if (matcher.matches(relativePath)) {
            return true
        }
        val fileName = relativePath.fileName ?: return false
        return matchesNameAlone && matcher.matches(fileName)
    }
}
