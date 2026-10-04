package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One "always allow" rule: one action of one tool, applying in every thread. */
@Immutable
data class ApprovalRuleUi(
    val toolName: String,
    val action: String?,
    /** For the mcp tool "server/tool"; null for the other tools. */
    val detail: String?,
)

/** One action Settings offers a rule for, taken from what the tools declare. */
@Immutable
data class ApprovalRuleChoiceUi(
    val toolName: String,
    val action: String?,
    /** Set when the rule must also name something, for example "server/tool"; the dialog then asks for the parts. */
    val detailName: String?,
)

/** The rule's own words: "phone, reminder" or "mcp, call notes/add". */
internal fun ruleLabel(toolName: String, action: String?, detail: String?): String =
    listOfNotNull(toolName, listOfNotNull(action, detail).joinToString(" ").ifEmpty { null }).joinToString(", ")

/** The rows of the "Always allow" group: one per rule with a remove mark, then "Add rule". */
@Composable
internal fun ApprovalRuleRows(rules: List<ApprovalRuleUi>, choices: List<ApprovalRuleChoiceUi>, actions: SettingsActions) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    for (rule in rules) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
        ) {
            Text(
                ruleLabel(rule.toolName, rule.action, rule.detail),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { actions.onApprovalRuleRemove(rule) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.settings_approvals_remove))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialogOpen = true }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Text(stringResource(R.string.settings_approvals_add), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
    if (dialogOpen) {
        ApprovalRuleDialog(
            choices = choices.filter { choice -> rules.none { rule -> rule.matches(choice) } },
            onAdd = { rule ->
                actions.onApprovalRuleAdd(rule)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

private fun ApprovalRuleUi.matches(choice: ApprovalRuleChoiceUi): Boolean =
    toolName == choice.toolName && action == choice.action && choice.detailName == null

/** Pick one action from the list; an action that needs a server and a tool asks for both. */
@Composable
private fun ApprovalRuleDialog(
    choices: List<ApprovalRuleChoiceUi>,
    onAdd: (ApprovalRuleUi) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by rememberSaveable { mutableStateOf(-1) }
    var server by rememberSaveable { mutableStateOf("") }
    var tool by rememberSaveable { mutableStateOf("") }
    val choice = choices.getOrNull(picked)
    val detail = if (choice?.detailName == null) null else "${server.trim()}/${tool.trim()}"
    val isComplete = choice != null && (choice.detailName == null || (server.isNotBlank() && tool.isNotBlank()))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_approvals_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.selectableGroup()) {
                    choices.forEachIndexed { index, option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = index == picked, onClick = { picked = index }, role = Role.RadioButton),
                        ) {
                            RadioButton(selected = index == picked, onClick = null)
                            Text(
                                ruleLabel(option.toolName, option.action, null),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
                if (choice?.detailName != null) {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text(stringResource(R.string.settings_approvals_server)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = tool,
                        onValueChange = { tool = it },
                        label = { Text(stringResource(R.string.settings_approvals_tool)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isComplete,
                onClick = { choice?.let { onAdd(ApprovalRuleUi(it.toolName, it.action, detail)) } },
            ) {
                Text(stringResource(R.string.settings_approvals_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
