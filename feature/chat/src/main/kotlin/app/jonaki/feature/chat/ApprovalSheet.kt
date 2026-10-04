package app.jonaki.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.ApprovalModeChoice
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.approvalModeHelp
import app.jonaki.core.ui.approvalModeLabel

/**
 * The approval chip's sheet: the three modes with the one that applies ticked,
 * and, while "Allow all in this thread" is on, a row to withdraw it. A choice
 * applies to this thread only (D-058); "Default" gives it back to Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApprovalSheet(
    threadMode: ApprovalModeChoice?,
    defaultMode: ApprovalModeChoice,
    allowAllInThread: Boolean,
    onSelect: (ApprovalModeChoice?) -> Unit,
    onWithdrawAllowAll: () -> Unit,
    onDismiss: () -> Unit,
    guardState: GuardState = GuardState.OFF,
) {
    val appliedMode = threadMode ?: defaultMode
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            SheetTitle(stringResource(R.string.chat_menu_approvals))
            Column(Modifier.selectableGroup()) {
                for (choice in ApprovalModeChoice.entries) {
                    ModeRow(
                        title = approvalModeLabel(choice),
                        help = approvalModeHelp(choice),
                        isSelected = choice == appliedMode,
                        onClick = { onSelect(choice) },
                    )
                }
            }
            if (threadMode != null) {
                TextButton(onClick = { onSelect(null) }) {
                    Text(stringResource(app.jonaki.core.ui.R.string.ui_approval_default, approvalModeLabel(defaultMode)))
                }
            }
            if (allowAllInThread) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Icon(JonakiIcons.DoneAll, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.chat_approval_all_active),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onWithdrawAllowAll) {
                        Text(stringResource(R.string.chat_approval_withdraw))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            GuardRow(guardState)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The Jev guard's state, so a user can see whether it is working; it is switched in Settings. */
@Composable
private fun GuardRow(guardState: GuardState) {
    val stateText = when (guardState) {
        GuardState.ON -> stringResource(R.string.chat_guard_on)
        GuardState.OFF -> stringResource(R.string.chat_guard_off)
        GuardState.ON_WITHOUT_KEY -> stringResource(R.string.chat_guard_no_key)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
        Text(stringResource(R.string.chat_guard_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(stateText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModeRow(title: String, help: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp)
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
