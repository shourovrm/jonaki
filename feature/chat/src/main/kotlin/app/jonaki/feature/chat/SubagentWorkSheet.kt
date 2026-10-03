package app.jonaki.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat

/** The work sheet of one turn (D-126): the rows from the run in their final state, the notes boards and the files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubagentWorkSheet(
    work: ChatItem.SubagentWork,
    onOpenSubagent: (subagentId: String) -> Unit,
    onOpenFile: (path: String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SubagentWorkContent(work, onOpenSubagent, onOpenFile)
    }
}

/** The sheet's body, apart from the sheet so that previews can show it. */
@Composable
internal fun SubagentWorkContent(
    work: ChatItem.SubagentWork,
    onOpenSubagent: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Text(
                pluralStringResource(R.plurals.subagents_count, work.subagents.size, work.subagents.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            val costs = work.subagents.mapNotNull { subagent -> subagent.costUsd }
            if (costs.isNotEmpty()) {
                Text(
                    UsageFormat.cost(costs.sum()),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (subagent in work.subagents) {
            SubagentRowView(subagent, onOpenSubagent)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        for (board in work.notesBoards) {
            NotesBoardRow(board, onOpenFile)
        }
        val files = work.subagents.flatMap { subagent -> subagent.filesWritten }.distinct()
        if (files.isNotEmpty()) {
            Text(
                stringResource(R.string.subagents_files_written),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            for (path in files) {
                FileRow(path, onOpenFile)
            }
        }
    }
}

@Composable
private fun NotesBoardRow(board: NotesBoardUi, onOpenFile: (String) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpenFile(board.path) },
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = JonakiTheme.colors.inkSoft, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.subagents_notes_board), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.subagents_work_notes, board.notes, board.notes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
