package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import app.jonaki.core.ui.InstructionsField
import app.jonaki.core.ui.MonospaceFamily

/** A custom subagent as its editor opens and saves it (D-138). */
@Immutable
data class CustomSubagentEditorUi(
    val name: String,
    val description: String,
    val instructions: String,
    val tools: List<String>,
    /** Null runs it on the thread's model. */
    val modelKey: String?,
)

/**
 * Makes or edits one custom subagent: its name, the description the
 * thread's model reads, its instructions, its model and the tools it starts
 * with. [initial] null makes a new one; only an existing one has Delete
 * (D-128: Delete for what the user made).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSubagentEditorScreen(
    initial: CustomSubagentEditorUi?,
    /** The built-in types and every custom one, this one included. */
    takenNames: Set<String>,
    toolOptions: List<String>,
    modelOptions: List<ModelOptionUi>,
    onSave: (CustomSubagentEditorUi) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var instructions by rememberSaveable { mutableStateOf(initial?.instructions.orEmpty()) }
    var tools by rememberSaveable { mutableStateOf(initial?.tools.orEmpty()) }
    var modelKey by rememberSaveable { mutableStateOf(initial?.modelKey) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val problem = CustomSubagentForm.problem(name, description, takenNames, initial?.name)
    val title = stringResource(if (initial == null) R.string.settings_subagents_new else R.string.settings_subagents_edit)
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
                        onClick = {
                            val saved = CustomSubagentEditorUi(name, description.trim(), instructions.trim(), tools, modelKey)
                            onSave(saved)
                        },
                        enabled = problem == null,
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
                .padding(vertical = 8.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                NameField(name, problem, onChange = { text -> name = text })
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = description,
                    // One line of delegate's guidelines, so line breaks become spaces.
                    onValueChange = { text -> description = text.replace('\n', ' ').take(CustomSubagentForm.MAX_DESCRIPTION_LENGTH) },
                    label = { Text(stringResource(R.string.settings_subagents_description)) },
                    supportingText = { Text(stringResource(R.string.settings_subagents_description_help)) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                InstructionsField(
                    value = instructions,
                    onValueChange = { text -> instructions = text },
                    label = stringResource(R.string.settings_editor_instructions),
                    minHeight = 200.dp,
                )
            }
            Group {
                ModelChoiceRow(
                    title = stringResource(R.string.settings_subagents_model),
                    selectedKey = modelKey,
                    defaultLabel = stringResource(R.string.settings_subagent_thread_model),
                    options = modelOptions,
                    onSelect = { key -> modelKey = key },
                )
            }
            SectionLabel(stringResource(R.string.settings_section_tools))
            Group {
                ToolRows(toolOptions, chosen = tools, onToggle = { toolName ->
                    tools = if (toolName in tools) tools - toolName else tools + toolName
                })
            }
            if (initial != null) {
                TextButton(
                    onClick = { confirmingDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.settings_editor_delete))
                }
            }
        }
    }
    if (confirmingDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.settings_persona_delete_title, initial.name)) },
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

/** Typed capitals and spaces become a valid name as the user types: "Price Checker" turns into "price-checker". */
@Composable
private fun NameField(name: String, problem: CustomSubagentForm.Problem?, onChange: (String) -> Unit) {
    val nameProblem = problem == CustomSubagentForm.Problem.NAME_INVALID || problem == CustomSubagentForm.Problem.NAME_TAKEN
    val help = if (problem == CustomSubagentForm.Problem.NAME_TAKEN) {
        stringResource(R.string.settings_subagents_name_taken)
    } else {
        stringResource(R.string.settings_subagents_name_rule)
    }
    OutlinedTextField(
        value = name,
        onValueChange = { text -> onChange(text.lowercase().replace(' ', '-').take(CustomSubagentForm.MAX_NAME_LENGTH)) },
        label = { Text(stringResource(R.string.settings_editor_name)) },
        supportingText = { Text(help) },
        isError = nameProblem,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonospaceFamily),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToolRows(toolOptions: List<String>, chosen: List<String>, onToggle: (String) -> Unit) {
    toolOptions.forEachIndexed { index, toolName ->
        if (index > 0) GroupDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(toolName) }
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 8.dp),
        ) {
            Text(
                toolName,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = MonospaceFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Checkbox(checked = toolName in chosen, onCheckedChange = { onToggle(toolName) })
        }
    }
}
