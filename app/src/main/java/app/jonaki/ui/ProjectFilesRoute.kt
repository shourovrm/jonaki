package app.jonaki.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.jonaki.JonakiApplication
import app.jonaki.R
import app.jonaki.core.toolapi.IncomingFiles
import app.jonaki.core.toolapi.ThreadPaths
import app.jonaki.feature.threads.ProjectFileUi
import app.jonaki.feature.threads.ProjectFilesActions
import app.jonaki.feature.threads.ProjectFilesScreen
import app.jonaki.feature.threads.ProjectFilesUiState
import app.jonaki.files.ThreadFileViewer
import app.jonaki.run.ProjectFolders
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The files a project's threads share (D-135): add from the phone, open, delete. */
@Composable
internal fun ProjectFilesRoute(application: JonakiApplication, projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val project by remember(projectId) {
        application.database.projectDao().observe(projectId)
    }.collectAsState(initial = null)
    val folder = remember(projectId) { ProjectFolders.folderOf(application, projectId) }
    // The folder is not observed; each change made here bumps this to list it again.
    var changeCount by remember { mutableIntStateOf(0) }
    var files by remember { mutableStateOf<List<ProjectFileUi>>(emptyList()) }
    LaunchedEffect(changeCount) {
        files = withContext(Dispatchers.IO) { ProjectFilesList.of(folder) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val refused = withContext(Dispatchers.IO) { copyIn(context, uris, ProjectFolders.create(application, projectId)) }
            if (refused.isNotEmpty()) {
                Toast.makeText(context, context.getString(R.string.files_refused_unreadable, refused.joinToString(", ")), Toast.LENGTH_LONG).show()
            }
            changeCount++
        }
    }
    ProjectFilesScreen(
        state = ProjectFilesUiState(projectName = project?.name.orEmpty(), files = files),
        actions = ProjectFilesActions(
            onBack = onBack,
            onAdd = { picker.launch(arrayOf("*/*")) },
            onOpen = { path ->
                val file = ThreadPaths(folder, folder).resolve(path) ?: return@ProjectFilesActions
                val message = when (ThreadFileViewer.open(context, file)) {
                    ThreadFileViewer.Result.OPENED -> null
                    ThreadFileViewer.Result.MISSING -> R.string.files_open_missing
                    ThreadFileViewer.Result.NO_APP -> R.string.files_open_no_app
                }
                if (message != null) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
            onDelete = { path ->
                scope.launch {
                    withContext(Dispatchers.IO) { ThreadPaths(folder, folder).resolve(path)?.delete() }
                    changeCount++
                }
            },
        ),
    )
}

/** Copies picked files into the project folder; returns the names that could not be read. */
private fun copyIn(context: Context, uris: List<Uri>, folder: File): List<String> {
    val refused = mutableListOf<String>()
    for (uri in uris) {
        val name = IncomingFiles.safeName(displayNameOf(context, uri))
        val target = IncomingFiles.freeFileIn(folder, name)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
            input.use { stream -> IncomingFiles.copyWithLimit(stream, target) }
        } catch (error: IOException) {
            // copyWithLimit also throws when a file is over the import limit; nothing half-copied stays.
            target.delete()
            refused += name
        }
    }
    return refused
}

private fun displayNameOf(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

/** The project folder's files in path order, as the screen lists them. */
internal object ProjectFilesList {
    fun of(folder: File): List<ProjectFileUi> =
        ProjectFolders.filePaths(folder).sorted().map { path ->
            val file = File(folder, path.removePrefix(ThreadPaths.PROJECT_ROOT + "/"))
            ProjectFileUi(
                path = path,
                name = path.removePrefix(ThreadPaths.PROJECT_ROOT + "/"),
                sizeText = IncomingFiles.describeSize(file.length()),
            )
        }
}
