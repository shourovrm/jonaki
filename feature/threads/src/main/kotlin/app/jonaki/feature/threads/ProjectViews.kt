package app.jonaki.feature.threads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import app.jonaki.core.ui.JonakiIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme

/** The Incognito filter chip; shown while at least one incognito thread exists (D-123). */
internal data class IncognitoChip(val selected: Boolean, val onSelect: () -> Unit)

/**
 * All, one chip per project, Incognito, then "+ Project" (D-110, D-123).
 * The row scrolls sideways.
 */
@Composable
internal fun ProjectChips(
    projects: List<ProjectUi>,
    selectedProjectId: String?,
    onSelect: (projectId: String?) -> Unit,
    incognitoChip: IncognitoChip?,
    onNewProject: () -> Unit,
) {
    val incognitoSelected = incognitoChip?.selected == true
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 6.dp),
    ) {
        FilterChip(
            selected = selectedProjectId == null && !incognitoSelected,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.threads_all)) },
            shape = ChipShape,
            colors = filterChipColours(),
            border = filterChipBorder(selectedProjectId == null && !incognitoSelected),
        )
        for (project in projects) {
            FilterChip(
                selected = project.id == selectedProjectId,
                onClick = { onSelect(project.id) },
                // A long name keeps one line and ends in "…" (D-029); the header shows it whole.
                label = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = ChipShape,
                colors = filterChipColours(),
                border = filterChipBorder(project.id == selectedProjectId),
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
        if (incognitoChip != null) {
            FilterChip(
                selected = incognitoChip.selected,
                onClick = incognitoChip.onSelect,
                label = { Text(stringResource(R.string.threads_incognito)) },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                shape = ChipShape,
                colors = filterChipColours(),
                border = filterChipBorder(incognitoChip.selected),
            )
        }
        AssistChip(
            onClick = onNewProject,
            label = { Text(stringResource(R.string.threads_project_chip)) },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
            shape = ChipShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = AssistChipDefaults.assistChipColors(
                labelColor = JonakiTheme.colors.inkSoft,
                leadingIconContentColor = JonakiTheme.colors.inkSoft,
            ),
        )
    }
}

private val ChipShape = RoundedCornerShape(16.dp)

/** The selected chip is filled (ink at night, the accent by day); the others are outlined. */
@Composable
private fun filterChipColours(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    labelColor = JonakiTheme.colors.inkSoft,
    iconColor = JonakiTheme.colors.inkSoft,
    selectedContainerColor = JonakiTheme.colors.selectedChip,
    selectedLabelColor = JonakiTheme.colors.onSelectedChip,
    selectedLeadingIconColor = JonakiTheme.colors.onSelectedChip,
)

@Composable
private fun filterChipBorder(selected: Boolean): BorderStroke? = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outlineVariant,
    selectedBorderColor = Color.Transparent,
)

/** The selected project's name, model and ⋮ menu, above its threads. */
@Composable
internal fun ProjectHeader(
    project: ProjectUi,
    onEdit: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenFiles: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val modelName = project.modelName
            if (modelName != null) {
                Text(
                    modelName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.threads_project_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.threads_project_edit)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                // What the project's threads share (D-135).
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.threads_project_memory)) },
                    leadingIcon = { Icon(JonakiIcons.Lightbulb, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onOpenMemory()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.threads_project_files)) },
                    leadingIcon = { Icon(JonakiIcons.Folder, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onOpenFiles()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.threads_project_delete), color = JonakiTheme.colors.deny) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = JonakiTheme.colors.deny) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** New project and Edit project: name, the model new threads start with, instructions. */
@Composable
internal fun ProjectDialog(
    project: ProjectUi?,
    modelOptions: List<ProjectModelOption>,
    onSave: (ProjectDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(project?.name.orEmpty()) }
    var instructions by rememberSaveable { mutableStateOf(project?.instructions.orEmpty()) }
    var modelKey by rememberSaveable { mutableStateOf(project?.modelKey) }
    val title = if (project == null) R.string.threads_project_new_title else R.string.threads_project_edit
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.threads_project_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ModelField(modelKey, modelOptions, onSelect = { key -> modelKey = key })
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text(stringResource(R.string.threads_project_instructions)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val trimmedName = name.trim()
            TextButton(
                onClick = { onSave(ProjectDraft(trimmedName, instructions.trim(), modelKey)) },
                enabled = trimmedName.isNotEmpty(),
            ) {
                Text(stringResource(R.string.threads_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.threads_cancel)) }
        },
    )
}

@Composable
private fun ModelField(modelKey: String?, options: List<ProjectModelOption>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val defaultLabel = stringResource(R.string.threads_project_model_default)
    // A model removed from Settings since it was picked still shows by its key.
    val selectedName = if (modelKey == null) {
        defaultLabel
    } else {
        options.firstOrNull { option -> option.key == modelKey }?.name ?: modelKey
    }
    Box {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.threads_project_model)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only field takes no taps, so a cover over it opens the menu.
        Box(Modifier.matchParentSize().clickable(role = Role.DropdownList) { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(defaultLabel) },
                onClick = {
                    open = false
                    onSelect(null)
                },
            )
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        onSelect(option.key)
                    },
                )
            }
        }
    }
}

/** Long-press, then Move to project: a tap on a choice moves the thread. */
@Composable
internal fun MoveToProjectDialog(
    projects: List<ProjectUi>,
    currentProjectId: String?,
    onMove: (projectId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.threads_move)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProjectChoiceRow(stringResource(R.string.threads_no_project), currentProjectId == null) { onMove(null) }
                for (project in projects) {
                    ProjectChoiceRow(project.name, project.id == currentProjectId) { onMove(project.id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.threads_cancel)) }
        },
    )
}

@Composable
private fun ProjectChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Deleting a project never deletes its threads; they stay without a project. */
@Composable
internal fun DeleteProjectDialog(name: String, onConfirm: (keepFacts: Boolean) -> Unit, onDismiss: () -> Unit) {
    // Facts often hold what was learned in the project; keeping them is the safe default (D-135).
    var keepFacts by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.threads_project_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.threads_project_delete_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { keepFacts = !keepFacts },
                ) {
                    Checkbox(checked = keepFacts, onCheckedChange = { checked -> keepFacts = checked })
                    Text(stringResource(R.string.threads_project_delete_keep_facts), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(keepFacts) }) {
                Text(stringResource(R.string.threads_delete), color = JonakiTheme.colors.deny)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.threads_cancel)) }
        },
    )
}
