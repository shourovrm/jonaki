package app.jonaki.feature.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily

/** One file in a project's shared folder (D-135). */
@Immutable
data class ProjectFileUi(
    /** As the model names it, for example "/project/data/rows.csv". */
    val path: String,
    /** The path without "/project/", as the user reads it. */
    val name: String,
    val sizeText: String,
)

@Immutable
data class ProjectFilesUiState(
    val projectName: String,
    val files: List<ProjectFileUi>,
)

class ProjectFilesActions(
    val onBack: () -> Unit,
    /** Opens the system file picker; the app copies the picked files in. */
    val onAdd: () -> Unit,
    val onOpen: (path: String) -> Unit,
    /** Called after the user confirms. */
    val onDelete: (path: String) -> Unit,
)

/**
 * The files the threads of a project share (D-135): the user adds files
 * here, and the model reads and writes them as /project/... in any of the
 * project's threads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFilesScreen(state: ProjectFilesUiState, actions: ProjectFilesActions, modifier: Modifier = Modifier) {
    var deletingPath by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.threads_project_files), fontWeight = FontWeight.SemiBold)
                        Text(
                            state.projectName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.threads_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.threads_project_files_add)) },
            )
        },
    ) { padding ->
        if (state.files.isEmpty()) {
            Text(
                stringResource(R.string.threads_project_files_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(20.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(state.files, key = { file -> file.path }) { file ->
                ProjectFileRow(file, onOpen = { actions.onOpen(file.path) }, onDelete = { deletingPath = file.path })
            }
        }
    }
    val deleting = state.files.firstOrNull { file -> file.path == deletingPath }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deletingPath = null },
            title = { Text(stringResource(R.string.threads_project_files_delete_title)) },
            text = { Text(deleting.name, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingPath = null
                        actions.onDelete(deleting.path)
                    },
                ) {
                    Text(stringResource(R.string.threads_delete), color = JonakiTheme.colors.deny)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPath = null }) { Text(stringResource(R.string.threads_cancel)) }
            },
        )
    }
}

/** A path wraps to two lines, then "…"; the size gets its own line (D-029). */
@Composable
private fun ProjectFileRow(file: ProjectFileUi, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                file.sizeText,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = MonospaceFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.threads_delete))
        }
    }
}
