package app.jonaki.feature.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily

/** One waiting proposal in the list: name, whether it is new or a change, and its description. */
@Composable
internal fun ProposalRow(proposal: ProposalRowUi, onOpen: () -> Unit) {
    Surface(color = Color.Transparent, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onOpen).padding(horizontal = 4.dp, vertical = 12.dp)) {
            // A name keeps one line in a list (D-029); the kind gets its own line.
            Text(
                proposal.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ProposalKind(proposal.replaces)
            Text(
                proposal.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ProposalKind(replaces: String?, maxLines: Int = 1) {
    val label = if (replaces == null) {
        stringResource(R.string.skills_proposal_new)
    } else {
        stringResource(R.string.skills_proposal_changes, replaces)
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A proposal's whole SKILL.md, read-only, with Add and Discard. Tapping outside
 * only closes it, so a proposal is never discarded by accident.
 */
@Composable
internal fun ProposalDialog(proposal: ProposalDetailUi, actions: SkillsActions) {
    AlertDialog(
        onDismissRequest = actions.onCloseProposal,
        // The full name wraps in a view that shows one item (D-029).
        title = { Text(proposal.name) },
        text = {
            Column {
                ProposalKind(proposal.replaces, maxLines = 2)
                SelectionContainer {
                    Text(
                        proposal.skillText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonospaceFamily),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                val error = proposal.error
                if (error != null) {
                    Text(
                        stringResource(R.string.skills_proposal_failed, error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = JonakiTheme.colors.deny,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.onAddProposal(proposal.name) }) {
                Text(stringResource(R.string.skills_proposal_add))
            }
        },
        dismissButton = {
            TextButton(onClick = { actions.onDiscardProposal(proposal.name) }) {
                Text(stringResource(R.string.skills_proposal_discard), color = JonakiTheme.colors.deny)
            }
        },
    )
}
