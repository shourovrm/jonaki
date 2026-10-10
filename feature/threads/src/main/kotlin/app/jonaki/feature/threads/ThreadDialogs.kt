package app.jonaki.feature.threads

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.jonaki.core.ui.JonakiTheme

/**
 * Renames a thread (D-029). Shared by the thread list and the chat screen, so
 * the app shows it for either. The current name starts selected, so typing
 * replaces it.
 */
@Composable
fun RenameThreadDialog(currentTitle: String, onSave: (newTitle: String) -> Unit, onDismiss: () -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(currentTitle, selection = TextRange(0, currentTitle.length))) }
    val focusRequester = remember { FocusRequester() }
    val trimmed = field.text.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.threads_rename_title)) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed) }, enabled = trimmed.isNotEmpty()) {
                Text(stringResource(R.string.threads_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.threads_cancel)) }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Asks once before several selected threads are deleted. One thread uses [DeleteThreadDialog], which shows its name. */
@Composable
internal fun DeleteThreadsDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.threads_delete_many_title, count, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.threads_delete), color = JonakiTheme.colors.deny)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.threads_cancel)) }
        },
    )
}

/** Asks before a thread, its messages and its folder are deleted. */
@Composable
internal fun DeleteThreadDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.threads_delete_title)) },
        text = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.threads_delete), color = JonakiTheme.colors.deny)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.threads_cancel)) }
        },
    )
}
