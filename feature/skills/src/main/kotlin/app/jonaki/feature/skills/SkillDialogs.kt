package app.jonaki.feature.skills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme

/** Adds a skill from a link or from a file the user picks. */
@Composable
internal fun ImportDialog(import: ImportUi, actions: SkillsActions) {
    var link by rememberSaveable { mutableStateOf("") }
    val trimmed = link.trim()
    AlertDialog(
        onDismissRequest = { if (!import.isWorking) actions.onCloseImport() },
        title = { Text(stringResource(R.string.skills_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = link,
                    onValueChange = { changed -> link = changed },
                    label = { Text(stringResource(R.string.skills_import_link)) },
                    placeholder = { Text(stringResource(R.string.skills_import_link_hint)) },
                    singleLine = true,
                    enabled = !import.isWorking,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = actions.onChooseFile,
                    enabled = !import.isWorking,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.skills_import_file))
                }
                ImportStatus(import)
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.onImportLink(trimmed) }, enabled = trimmed.isNotEmpty() && !import.isWorking) {
                Text(stringResource(R.string.skills_import_add))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onCloseImport, enabled = !import.isWorking) {
                Text(stringResource(R.string.skills_cancel))
            }
        },
    )
}

@Composable
private fun ImportStatus(import: ImportUi) {
    if (import.isWorking) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.skills_import_working), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val error = import.error ?: return
    Text(
        stringResource(R.string.skills_import_failed, error),
        style = MaterialTheme.typography.bodyMedium,
        color = JonakiTheme.colors.deny,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
internal fun ReplaceDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skills_replace_title, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.skills_replace)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.skills_cancel)) }
        },
    )
}

@Composable
internal fun DeleteSkillDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skills_delete_title, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.skills_delete), color = JonakiTheme.colors.deny)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.skills_cancel)) }
        },
    )
}

/** Asked on Back when the editor holds text that was not saved (D-113). */
@Composable
internal fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.skills_discard_title)) },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.skills_discard), color = JonakiTheme.colors.deny)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.skills_keep_editing)) }
        },
    )
}
