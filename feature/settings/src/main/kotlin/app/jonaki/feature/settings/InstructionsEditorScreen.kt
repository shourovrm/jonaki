package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.InstructionsField

/** A persona's name is one line in lists, so it gets a short limit. */
private const val PERSONA_NAME_MAX_CHARACTERS = 60

/**
 * Edits the general custom instructions ([initialName] null) or one persona
 * (D-STY-1, D-STY-3). [onDelete] null hides Delete, as for a new persona.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsEditorScreen(
    title: String,
    initialName: String?,
    initialInstructions: String,
    onSave: (name: String?, instructions: String) -> Unit,
    onBack: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initialName.orEmpty()) }
    var instructions by rememberSaveable { mutableStateOf(initialInstructions) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val hasName = initialName != null
    // A persona without a name could not be told apart in the lists.
    val canSave = !hasName || name.isNotBlank()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(if (hasName) name.trim() else null, instructions.trim()) },
                        enabled = canSave,
                    ) {
                        Text(stringResource(R.string.settings_editor_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (hasName) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { text -> name = text.replace('\n', ' ').take(PERSONA_NAME_MAX_CHARACTERS) },
                    label = { Text(stringResource(R.string.settings_editor_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(12.dp))
            }
            InstructionsField(
                value = instructions,
                onValueChange = { text -> instructions = text },
                label = stringResource(R.string.settings_editor_instructions),
                minHeight = 240.dp,
            )
            if (onDelete != null) {
                TextButton(
                    onClick = { confirmingDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.settings_editor_delete))
                }
            }
        }
    }
    if (confirmingDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.settings_persona_delete_title, name.ifBlank { initialName.orEmpty() })) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.settings_editor_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}
