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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme

/** A fact is one sentence; the same limit as the memory tool's. */
private const val MAX_FACT_LENGTH = 500

/**
 * Adds or edits a fact. [offerScope] shows the This thread / All threads
 * choice, which only adding from a thread needs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FactDialog(
    titleId: Int,
    initialText: String,
    offerScope: Boolean,
    onSave: (text: String, isGlobal: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    // Opened from Settings there is no thread, so a new fact is global.
    var isGlobal by rememberSaveable { mutableStateOf(!offerScope) }
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
                if (offerScope) {
                    ScopeChoice(isGlobal = isGlobal, onChange = { isGlobal = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed, isGlobal) }, enabled = trimmed.isNotEmpty()) {
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
private fun ScopeChoice(isGlobal: Boolean, onChange: (Boolean) -> Unit) {
    val options = listOf(false to R.string.memory_scope_thread, true to R.string.memory_scope_global)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        options.forEachIndexed { index, (global, labelId) ->
            SegmentedButton(
                selected = global == isGlobal,
                onClick = { onChange(global) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(labelId), maxLines = 1)
            }
        }
    }
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
