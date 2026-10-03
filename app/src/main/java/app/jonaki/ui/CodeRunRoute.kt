package app.jonaki.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import app.jonaki.JonakiApplication
import app.jonaki.R
import app.jonaki.core.storage.StepEntity
import app.jonaki.feature.chat.CodeRunUi
import app.jonaki.files.ThreadFileViewer
import java.io.File

/**
 * The code sheet of [step], read again whenever the step row changes, so a
 * sheet opened while the program runs fills in when it ends (D-090). Null
 * while no step is open.
 */
@Composable
fun codeRunOf(application: JonakiApplication, threadId: String, step: StepEntity?): State<CodeRunUi?> =
    produceState<CodeRunUi?>(initialValue = null, step) {
        value = if (step == null) {
            null
        } else {
            CodeRunDetails.load(application.database.messageDao(), application.runner.threadFolder(threadId), step)
        }
    }

/**
 * A file a program saved, tapped in the code sheet: a page in artifacts/
 * opens in the artifact viewer, which only serves that folder (D-047);
 * anything else opens in another app.
 */
fun openSavedFile(context: Context, threadFolder: File, path: String, onOpenArtifact: (path: String) -> Unit) {
    val isArtifactPage = path.startsWith(ARTIFACTS_FOLDER) &&
        (path.endsWith(".html", ignoreCase = true) || path.endsWith(".htm", ignoreCase = true))
    if (isArtifactPage) {
        onOpenArtifact(path)
        return
    }
    val message = when (ThreadFileViewer.open(context, File(threadFolder, path))) {
        ThreadFileViewer.Result.OPENED -> return
        ThreadFileViewer.Result.MISSING -> R.string.files_open_missing
        ThreadFileViewer.Result.NO_APP -> R.string.files_open_no_app
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

private const val ARTIFACTS_FOLDER = "artifacts/"
