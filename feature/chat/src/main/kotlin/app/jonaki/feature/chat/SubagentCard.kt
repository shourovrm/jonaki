package app.jonaki.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.DotStyle
import app.jonaki.core.ui.GlowDot
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MarkdownText
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat

/**
 * One subagent (M7): folded, its name, status, steps and cost, then its
 * task and its latest line; open, its steps and its answer.
 */
@Composable
internal fun SubagentCard(subagent: ChatItem.Subagent, modifier: Modifier = Modifier) {
    var open by rememberSaveable(subagent.id) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .animateContentSize()
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            SubagentHeader(subagent, open)
            Text(
                subagent.task,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (open) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            val latest = subagent.latestText
            if (!open && !latest.isNullOrBlank()) {
                Text(
                    latest.lineSequence().first { line -> line.isNotBlank() }.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (open) {
                SubagentDetails(subagent)
            }
        }
    }
}

@Composable
private fun SubagentHeader(subagent: ChatItem.Subagent, open: Boolean) {
    val colors = JonakiTheme.colors
    val isRunning = subagent.status == SubagentUiStatus.RUNNING
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)) {
        GlowDot(
            color = if (subagent.status == SubagentUiStatus.DONE || isRunning) colors.live else colors.deny,
            style = if (isRunning) DotStyle.GLOWING else DotStyle.QUIET,
            dotSize = 8.dp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            subagent.label.replaceFirstChar { first -> first.uppercase() },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            statusLabel(subagent.status),
            style = MaterialTheme.typography.labelMedium,
            color = if (isRunning) colors.live else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            pluralStringResource(R.plurals.chat_run_steps, subagent.steps.size, subagent.steps.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        val cost = subagent.costUsd
        if (cost != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                UsageFormat.cost(cost),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(if (open) R.string.chat_subagent_hide else R.string.chat_subagent_show),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubagentDetails(subagent: ChatItem.Subagent) {
    if (subagent.steps.isNotEmpty()) {
        Column(Modifier.padding(top = 6.dp)) {
            subagent.steps.forEachIndexed { index, step ->
                StepRow(
                    step = step,
                    isFirst = index == 0,
                    isLast = index == subagent.steps.lastIndex,
                    nextIsDone = subagent.steps.getOrNull(index + 1)?.status == StepUiStatus.DONE,
                )
            }
        }
    }
    val answer = subagent.answer
    if (!answer.isNullOrBlank()) {
        Text(
            stringResource(R.string.chat_subagent_answer),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        MarkdownText(answer, showCaret = false)
    }
}

@Composable
private fun statusLabel(status: SubagentUiStatus): String = stringResource(
    when (status) {
        SubagentUiStatus.RUNNING -> R.string.chat_subagent_running
        SubagentUiStatus.DONE -> R.string.chat_subagent_done
        SubagentUiStatus.STEP_LIMIT -> R.string.chat_subagent_step_limit
        SubagentUiStatus.COST_LIMIT -> R.string.chat_subagent_cost_limit
        SubagentUiStatus.TIME_LIMIT -> R.string.chat_subagent_time_limit
        SubagentUiStatus.FAILED -> R.string.chat_subagent_failed
        SubagentUiStatus.STOPPED -> R.string.chat_subagent_stopped
    },
)
