package app.jonaki.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * When a tool asks before it changes something (user ruling 2026-10-03):
 * the default for all threads in Settings, and one thread's own choice in
 * the chat's menu.
 */
enum class ApprovalModeChoice {
    ASK,
    AUTO,
    BYPASS,
}

@Composable
fun approvalModeLabel(choice: ApprovalModeChoice): String = stringResource(
    when (choice) {
        ApprovalModeChoice.ASK -> R.string.ui_approval_ask
        ApprovalModeChoice.AUTO -> R.string.ui_approval_auto
        ApprovalModeChoice.BYPASS -> R.string.ui_approval_bypass
    },
)

@Composable
private fun approvalModeHelp(choice: ApprovalModeChoice): String = stringResource(
    when (choice) {
        ApprovalModeChoice.ASK -> R.string.ui_approval_ask_help
        ApprovalModeChoice.AUTO -> R.string.ui_approval_auto_help
        ApprovalModeChoice.BYPASS -> R.string.ui_approval_bypass_help
    },
)

/**
 * One radio row per mode. With [defaultChoice] set, a first row "Default
 * (mode)" stands for null: the thread follows Settings.
 */
@Composable
fun ApprovalModeOptions(
    selected: ApprovalModeChoice?,
    onSelect: (ApprovalModeChoice?) -> Unit,
    modifier: Modifier = Modifier,
    defaultChoice: ApprovalModeChoice? = null,
) {
    Column(modifier.selectableGroup()) {
        if (defaultChoice != null) {
            ApprovalOptionRow(
                title = stringResource(R.string.ui_approval_default, approvalModeLabel(defaultChoice)),
                help = null,
                isSelected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        for (choice in ApprovalModeChoice.entries) {
            ApprovalOptionRow(
                title = approvalModeLabel(choice),
                help = approvalModeHelp(choice),
                isSelected = selected == choice,
                onClick = { onSelect(choice) },
            )
        }
    }
}

@Composable
private fun ApprovalOptionRow(title: String, help: String?, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (help != null) {
                Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
