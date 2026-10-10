package app.jonaki.feature.memory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme

/** A fact is one sentence; the same limit as the memory tool's. */
private const val MAX_FACT_LENGTH = 500

/**
 * Adds or edits a fact. [scopes] are where a new fact can go, the first
 * chosen at the start; the choice shows only when there are two or more,
 * which editing never needs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FactDialog(
    titleId: Int,
    initialText: String,
    scopes: List<FactScopeUi>,
    onSave: (text: String, scope: FactScopeUi) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var scope by rememberSaveable { mutableStateOf(scopes.firstOrNull() ?: FactScopeUi.GLOBAL) }
    val focusRequester = remember { FocusRequester() }
    val trimmed = text.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleId)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { changed -> text = changed.take(MAX_FACT_LENGTH) },
                    minLines = 2,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                if (scopes.size > 1) {
                    ScopeChoice(scopes = scopes, selected = scope, onChange = { scope = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed, scope) }, enabled = trimmed.isNotEmpty()) {
                Text(stringResource(R.string.memory_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_cancel)) }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeChoice(scopes: List<FactScopeUi>, selected: FactScopeUi, onChange: (FactScopeUi) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        scopes.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, scopes.size),
            ) {
                // Three choices share the dialog's width; a label too long for its third wraps rather than cut (D-029).
                Text(stringResource(scopeLabelOf(option)), maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun scopeLabelOf(scope: FactScopeUi): Int = when (scope) {
    FactScopeUi.THREAD -> R.string.memory_scope_thread
    FactScopeUi.PROJECT -> R.string.memory_scope_project
    FactScopeUi.GLOBAL -> R.string.memory_scope_global
}

/** Asks once before several selected facts are deleted. One fact uses [DeleteFactDialog], which shows its text. */
@Composable
internal fun DeleteFactsDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.memory_delete_many_title, count, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.memory_delete), color = JonakiTheme.colors.deny)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_cancel)) }
        },
    )
}

@Composable
internal fun DeleteFactDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_delete_title)) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.memory_delete), color = JonakiTheme.colors.deny)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_cancel)) }
        },
    )
}
