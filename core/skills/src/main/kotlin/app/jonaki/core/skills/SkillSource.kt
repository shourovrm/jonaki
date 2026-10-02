package app.jonaki.core.skills

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where a link the user pasted points, for import (D-041). */
sealed interface SkillSource {
    /** A folder in a GitHub repository; [ref] null means the default branch, [path] "" the root. */
    data class GitHubFolder(val owner: String, val repository: String, val ref: String?, val path: String) : SkillSource

    /** Any other link: the file it returns is taken as a SKILL.md with no other files. */
    data class SingleFile(val url: String) : SkillSource

    data object NotALink : SkillSource

    companion object {
        fun parse(text: String): SkillSource {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.any { character -> character.isWhitespace() }) {
                return NotALink
            }
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: return NotALink
            return when (url.host) {
                "github.com", "www.github.com" -> fromGitHubPage(url)
                "raw.githubusercontent.com" -> fromGitHubRaw(url)
                else -> SingleFile(url.toString())
            }
        }

        /** github.com/owner/repo[/tree|blob/ref/path] */
        private fun fromGitHubPage(url: HttpUrl): SkillSource {
            val segments = url.pathSegments.filter { segment -> segment.isNotEmpty() }
            if (segments.size < 2) {
                return NotALink
            }
            val owner = segments[0]
            val repository = segments[1].removeSuffix(".git")
            if (segments.size == 2) {
                return GitHubFolder(owner, repository, ref = null, path = "")
            }
            val kind = segments[2]
            if ((kind != "tree" && kind != "blob") || segments.size < 4) {
                return NotALink
            }
            val ref = segments[3]
            val path = segments.drop(4)
            return folderOrFile(owner, repository, ref, path, fileUrl = url.toString())
        }

        /** raw.githubusercontent.com/owner/repo/ref/path */
        private fun fromGitHubRaw(url: HttpUrl): SkillSource {
            val segments = url.pathSegments.filter { segment -> segment.isNotEmpty() }
            if (segments.size < 4) {
                return SingleFile(url.toString())
            }
            return folderOrFile(segments[0], segments[1], segments[2], segments.drop(3), fileUrl = url.toString())
        }

        /** A link to a SKILL.md brings its whole folder, since a skill's other files sit next to it. */
        private fun folderOrFile(owner: String, repository: String, ref: String, path: List<String>, fileUrl: String): SkillSource {
            val lastSegment = path.lastOrNull()
            if (lastSegment == SkillLibrary.SKILL_FILE) {
                return GitHubFolder(owner, repository, ref, path.dropLast(1).joinToString("/"))
            }
            val looksLikeFile = lastSegment != null && lastSegment.contains('.')
            if (looksLikeFile && !fileUrl.contains("/tree/")) {
                return SingleFile(rawUrlOf(owner, repository, ref, path, fileUrl))
            }
            return GitHubFolder(owner, repository, ref, path.joinToString("/"))
        }

        private fun rawUrlOf(owner: String, repository: String, ref: String, path: List<String>, fileUrl: String): String {
            if (fileUrl.contains("raw.githubusercontent.com")) {
                return fileUrl
            }
            return "https://raw.githubusercontent.com/$owner/$repository/$ref/" + path.joinToString("/")
        }
    }
}
