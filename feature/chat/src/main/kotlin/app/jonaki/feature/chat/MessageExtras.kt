package app.jonaki.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.DotStyle
import app.jonaki.core.ui.GlowDot
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import kotlinx.coroutines.delay

/**
 * The pulsing firefly at the end of the chat while the agent works, with
 * what it is doing and for how long (D-055).
 */
@Composable
internal fun WorkingRow(working: ChatItem.Working) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(working.sinceMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = ((now - working.sinceMillis) / 1_000).coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        GlowDot(JonakiTheme.colors.live, DotStyle.GLOWING, dotSize = 10.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            activityLabel(working.activity),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // Only after a few seconds, so quick answers do not flash a counter.
        if (seconds >= SHOW_SECONDS_FROM && working.sinceMillis > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.chat_working_seconds, seconds),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val SHOW_SECONDS_FROM = 3L

@Composable
private fun activityLabel(activity: WorkingActivity): String = when (activity) {
    WorkingActivity.Thinking -> stringResource(R.string.chat_working_thinking)
    WorkingActivity.Writing -> stringResource(R.string.chat_working_writing)
    is WorkingActivity.Tool -> when (activity.toolName) {
        "web_search" -> stringResource(R.string.chat_working_searching)
        "web_fetch" -> stringResource(R.string.chat_working_reading_page)
        "youtube_summarize" -> stringResource(R.string.chat_working_watching)
        "read_file", "read_document", "find_files", "search_files" -> stringResource(R.string.chat_working_reading_files)
        "write_file", "edit_file" -> stringResource(R.string.chat_working_writing_file)
        "memory" -> stringResource(R.string.chat_working_memory)
        "search_chats" -> stringResource(R.string.chat_working_search_chats)
        "delegate" -> stringResource(R.string.chat_working_subagents)
        else -> stringResource(R.string.chat_working_tool, activity.toolName)
    }
}

/**
 * The model's reasoning (D-054): open and following the newest lines while
 * it streams, then folded to one "Thinking" line that opens on tap.
 */
@Composable
internal fun ReasoningBlock(reasoning: ChatItem.Reasoning) {
    var open by rememberSaveable(reasoning.id) { mutableStateOf(false) }
    val showText = reasoning.isStreaming || open
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(enabled = !reasoning.isStreaming) { open = !open }
                .padding(vertical = 4.dp),
        ) {
            Text(
                stringResource(R.string.chat_reasoning),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!reasoning.isStreaming) {
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (showText) {
            // While streaming, only the newest lines show, so the chat does not jump with long reasoning.
            val text = if (reasoning.isStreaming) reasoning.text.lines().takeLast(STREAMING_LINES).joinToString("\n") else reasoning.text
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                )
            }
        }
    }
}

private const val STREAMING_LINES = 6

/** Copy, and for a prompt also Edit, under a message. */
@Composable
internal fun MessageActions(text: String, alignEnd: Boolean, onEdit: (() -> Unit)?) {
    val clipboard = LocalClipboardManager.current
    Row(
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.size(36.dp)) {
            Icon(
                JonakiIcons.ContentCopy,
                contentDescription = stringResource(R.string.chat_copy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.chat_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Above the field while a sent prompt is being edited; sending replaces it and what followed. */
@Composable
internal fun EditingBanner(onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Text(
                stringResource(R.string.chat_editing),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.chat_edit_cancel),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/** A message sent during a run, waiting for the agent's next safe point; one line, ending in "…" (D-029). */
@Composable
internal fun QueuedMessageRow(message: QueuedMessageUi, onEdit: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Text(
                stringResource(R.string.chat_queued),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.chat_queued_edit),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.chat_queued_cancel),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
