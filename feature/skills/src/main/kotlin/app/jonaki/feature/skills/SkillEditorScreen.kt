package app.jonaki.feature.skills

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily

/** Edits a skill's SKILL.md as plain text; Save checks the front matter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillEditorScreen(state: SkillEditorUiState, actions: SkillEditorActions, modifier: Modifier = Modifier) {
    var text by rememberSaveable(state.name) { mutableStateOf("") }
    var loadedText by rememberSaveable(state.name) { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    // The saved text arrives after the file is read, and again after a reset.
    LaunchedEffect(state.savedText) {
        val saved = state.savedText
        if (saved != null && saved != loadedText) {
            text = saved
            loadedText = saved
        }
    }
    val hasChanges = loadedText != null && text != loadedText

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                // A view of one item wraps its name rather than cutting it (D-029).
                title = { Text(state.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.skills_back))
                    }
                },
                actions = {
                    TextButton(onClick = { actions.onSave(text) }, enabled = hasChanges) {
                        Text(stringResource(R.string.skills_save))
                    }
                    EditorMenu(
                        offerReset = state.isBuiltIn && state.isEdited,
                        onReset = actions.onReset,
                        onDelete = { deleting = true },
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val error = state.error
            if (error != null) {
                Text(
                    stringResource(R.string.skills_save_failed, error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JonakiTheme.colors.deny,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { changed -> text = changed },
                enabled = loadedText != null,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFamily),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
    if (deleting) {
        DeleteSkillDialog(
            name = state.name,
            onConfirm = {
                deleting = false
                actions.onDelete()
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun EditorMenu(offerReset: Boolean, onReset: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.skills_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (offerReset) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.skills_reset)) },
                    onClick = {
                        open = false
                        onReset()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.skills_delete), color = JonakiTheme.colors.deny) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}
