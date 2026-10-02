package app.jonaki.core.toolapi

import java.io.File

/**
 * Saved versions of the HTML files in a thread's artifacts/ folder (D-047).
 * Version n of artifacts/report.html is artifacts/.versions/report/vn.html.
 * Lives in core because the artifact tool writes versions and the viewer
 * screen lists them, and neither may depend on the other (D-007).
 */
class ArtifactVersions(threadFolder: File) {
    private val versionsRoot = File(threadFolder, "$ARTIFACTS_FOLDER/$VERSIONS_FOLDER")

    data class Version(val number: Int, val file: File)

    /** Versions of [artifactPath] (relative to the thread folder), oldest first. */
    fun list(artifactPath: String): List<Version> {
        val folder = folderFor(artifactPath)
        val files = folder.listFiles().orEmpty()
        return files.mapNotNull { file ->
            val number = versionNumber.matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            Version(number, file)
        }.sortedBy { version -> version.number }
    }

    /**
     * Copies [artifact] as the next version unless it equals the latest one,
     * and returns the version that now matches the file.
     */
    fun record(artifactPath: String, artifact: File): Version {
        val latest = list(artifactPath).lastOrNull()
        if (latest != null && latest.file.readBytes().contentEquals(artifact.readBytes())) {
            return latest
        }
        val number = (latest?.number ?: 0) + 1
        val folder = folderFor(artifactPath)
        folder.mkdirs()
        val copy = File(folder, "v$number.html")
        artifact.copyTo(copy, overwrite = true)
        return Version(number, copy)
    }

    private fun folderFor(artifactPath: String): File {
        val insideArtifacts = artifactPath.removePrefix("$ARTIFACTS_FOLDER/").removeSuffix(".html")
        return File(versionsRoot, insideArtifacts)
    }

    companion object {
        const val ARTIFACTS_FOLDER = "artifacts"
        const val VERSIONS_FOLDER = ".versions"
        private val versionNumber = Regex("""v(\d+)\.html""")
    }
}
