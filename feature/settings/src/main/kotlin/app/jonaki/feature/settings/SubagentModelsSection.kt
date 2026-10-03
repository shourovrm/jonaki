package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One subagent type and the model it runs on (D-065). */
@Immutable
data class SubagentModelRowUi(
    /** "researcher", "scout", "writer" or "worker". */
    val agentType: String,
    /** Null uses the type's default. */
    val selectedKey: String?,
    /** The scout's default is the cheapest model; every other type's is the thread's. */
    val defaultIsCheapest: Boolean,
)

/** A scoped model offered for a subagent type. */
@Immutable
data class ModelOptionUi(
    val key: String,
    val name: String,
)

/** One row per type; a tap opens the list of the user's models. */
@Composable
internal fun SubagentModelRows(
    rows: List<SubagentModelRowUi>,
    options: List<ModelOptionUi>,
    onChange: (agentType: String, modelKey: String?) -> Unit,
) {
    Column {
        rows.forEach { row -> SubagentModelRow(row, options, onChange) }
    }
}

@Composable
private fun SubagentModelRow(
    row: SubagentModelRowUi,
    options: List<ModelOptionUi>,
    onChange: (agentType: String, modelKey: String?) -> Unit,
) {
    val defaultLabel = stringResource(
        if (row.defaultIsCheapest) R.string.settings_subagent_cheapest_model else R.string.settings_subagent_thread_model,
    )
    ModelChoiceRow(
        title = agentTypeLabel(row.agentType),
        selectedKey = row.selectedKey,
        defaultLabel = defaultLabel,
        options = options,
        onSelect = { modelKey -> onChange(row.agentType, modelKey) },
    )
}

/**
 * A title over the chosen model's name; a tap lists [defaultLabel] first,
 * then the user's models. [onSelect] gets null for the default.
 */
@Composable
internal fun ModelChoiceRow(
    title: String,
    selectedKey: String?,
    defaultLabel: String,
    options: List<ModelOptionUi>,
    onSelect: (modelKey: String?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { option -> option.key == selectedKey }?.name ?: defaultLabel
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    selectedName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(defaultLabel) },
                onClick = {
                    menuOpen = false
                    onSelect(null)
                },
            )
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        menuOpen = false
                        onSelect(option.key)
                    },
                )
            }
        }
    }
}

@Composable
private fun agentTypeLabel(agentType: String): String = when (agentType) {
    "researcher" -> stringResource(R.string.settings_subagent_researcher)
    "scout" -> stringResource(R.string.settings_subagent_scout)
    "writer" -> stringResource(R.string.settings_subagent_writer)
    "worker" -> stringResource(R.string.settings_subagent_worker)
    else -> agentType
}
