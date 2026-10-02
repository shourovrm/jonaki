package app.jonaki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.jonaki.JonakiApplication
import app.jonaki.core.toolapi.ArtifactVersions
import app.jonaki.feature.artifact.ArtifactScreen
import app.jonaki.feature.artifact.ArtifactUiState
import app.jonaki.run.ThreadFolders
import java.io.File

/** The artifact viewer for one file of one thread (D-047). */
@Composable
fun ArtifactRoute(application: JonakiApplication, threadId: String, path: String, onBack: () -> Unit) {
    val state = remember(threadId, path) {
        val threadFolder = ThreadFolders.create(application, threadId)
        ArtifactUiState(
            threadFolder = threadFolder,
            path = path,
            versionNumbers = ArtifactVersions(threadFolder).list(path).map { version -> version.number },
            fileExists = File(threadFolder, path).isFile,
        )
    }
    ArtifactScreen(state = state, onBack = onBack)
}
