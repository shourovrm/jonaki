package app.jonaki.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.DotStyle
import app.jonaki.core.ui.GlowDot
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat

/**
 * One subagent as one row (D-126): its task on one line, its name and what
 * it does now under it, steps of the limit and the cost on the right. The
 * run's delegate step and the work sheet show the same row.
 */
@Composable
internal fun SubagentRowView(subagent: SubagentUi, onOpen: (subagentId: String) -> Unit, modifier: Modifier = Modifier) {
    val row = SubagentRows.rowOf(subagent)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.subagents_open)) { onOpen(subagent.id) }) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.padding(vertical = 8.dp)) {
            Box(Modifier.padding(top = 2.dp).size(16.dp), contentAlignment = Alignment.Center) {
                SubagentGlyph(row.icon)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                // D-029: a name in a list keeps one line and ends in "…"; the page shows all of it.
                Text(
                    oneLine(subagent.task),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SubagentNowLine(subagent, row)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${subagent.steps.size}/${subagent.stepLimit}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                    color = muted,
                    maxLines = 1,
                )
                val cost = subagent.costUsd
                if (cost != null) {
                    Text(
                        UsageFormat.cost(cost),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                        color = muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** "Researcher 2  Reading …": the name in ink-2, then the state; full ink when the row needs a look. */
@Composable
private fun SubagentNowLine(subagent: SubagentUi, row: SubagentRow) {
    val nameColor = if (row.needsLook) MaterialTheme.colorScheme.onSurface else JonakiTheme.colors.inkSoft
    val textColor = if (row.needsLook) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val nowText = subagentNowText(row.now)
    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Medium)) {
            append(subagentName(subagent.label))
        }
        append("  ")
        append(nowText)
    }
    Text(
        line,
        style = MaterialTheme.typography.bodySmall,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Only a working subagent is lit; every other mark is in ink (D-123). */
@Composable
internal fun SubagentGlyph(icon: SubagentRowIcon) {
    val tint = JonakiTheme.colors.inkSoft
    val glyphModifier = Modifier.size(14.dp)
    when (icon) {
        SubagentRowIcon.LIVE -> GlowDot(JonakiTheme.colors.live, DotStyle.GLOWING, dotSize = 7.dp)
        SubagentRowIcon.WAITING_FOR_YOU -> Icon(JonakiIcons.Hand, contentDescription = null, tint = tint, modifier = glyphModifier)
        SubagentRowIcon.ASKING_MAIN_AGENT -> Icon(JonakiIcons.ChatBubble, contentDescription = null, tint = tint, modifier = glyphModifier)
        SubagentRowIcon.DONE -> Icon(Icons.Filled.Check, contentDescription = null, tint = tint, modifier = glyphModifier)
        SubagentRowIcon.WARNING -> Icon(Icons.Filled.Warning, contentDescription = null, tint = tint, modifier = glyphModifier)
    }
}

@Composable
internal fun subagentNowText(now: SubagentNow): String = when (now) {
    is SubagentNow.Working -> listOf(stepLabel(now.step.toolName), now.step.query ?: now.step.detail)
        .filter { part -> part.isNotBlank() }
        .joinToString(" ")
    is SubagentNow.Wrote -> now.line
    SubagentNow.Thinking -> stringResource(R.string.subagents_now_thinking)
    SubagentNow.WaitingForYou -> stringResource(R.string.subagents_now_waiting)
    SubagentNow.AskingMainAgent -> stringResource(R.string.subagents_now_asking)
    is SubagentNow.Done -> doneText(now)
    is SubagentNow.Ended -> subagentStatusLabel(now.status)
}

/** What went wrong first: skipped parts, then files written, then the time it took. */
@Composable
private fun doneText(done: SubagentNow.Done): String {
    if (done.skippedParts > 0) {
        return pluralStringResource(R.plurals.subagents_now_done_skipped, done.skippedParts, done.skippedParts)
    }
    if (done.filesWritten > 0) {
        return pluralStringResource(R.plurals.subagents_now_done_files, done.filesWritten, done.filesWritten)
    }
    val duration = done.durationMillis ?: return stringResource(R.string.chat_subagent_done)
    return stringResource(R.string.subagents_now_done_in, formatStepDuration(duration))
}

@Composable
internal fun subagentStatusLabel(status: SubagentUiStatus): String = stringResource(
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

/** "researcher 2" reads "Researcher 2". */
internal fun subagentName(label: String): String = label.replaceFirstChar { first -> first.uppercaseChar() }

/** A task the model wrote over several lines still fits one row. */
private fun oneLine(text: String): String = text.trim().replace(Regex("\\s+"), " ")

/** The delegate step's own line in the track: "3 subagents" and "1 of 3 done". */
@Composable
internal fun subagentGroupLabel(subagents: List<SubagentUi>): String =
    pluralStringResource(R.plurals.subagents_count, subagents.size, subagents.size)

@Composable
internal fun subagentGroupProgress(subagents: List<SubagentUi>): String {
    val progress = SubagentRows.progressOf(subagents)
    return stringResource(R.string.subagents_progress, progress.done, progress.total)
}

/** The rows of one delegate step, under its station in the run's track. */
@Composable
internal fun SubagentGroupRows(subagents: List<SubagentUi>, onOpenSubagent: (String) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        for (subagent in subagents) {
            SubagentRowView(subagent, onOpenSubagent)
        }
    }
}

/**
 * Under the answer of a turn that used delegate: "Work from 3 subagents" and
 * a line that names only what went wrong and the notes (D-126).
 */
@Composable
internal fun SubagentWorkRow(work: ChatItem.SubagentWork, onOpen: (workId: String) -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = line)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onOpen(work.id) }.padding(vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.subagents_work_title, work.subagents.size, work.subagents.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    workSummaryText(SubagentRows.workSummaryOf(work.subagents)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = JonakiTheme.colors.inkSoft)
        }
        HorizontalDivider(color = line)
    }
}

/** "1 over budget, 1 part skipped, 6 notes", or "All done, 6 notes". */
@Composable
private fun workSummaryText(summary: WorkSummary): String {
    val parts = mutableListOf<String>()
    if (summary.allDone) {
        parts += stringResource(R.string.subagents_work_all_done)
    }
    for ((status, count) in summary.earlyStops) {
        parts += earlyStopText(status, count)
    }
    if (summary.skippedParts > 0) {
        parts += pluralStringResource(R.plurals.subagents_work_skipped, summary.skippedParts, summary.skippedParts)
    }
    if (summary.notes > 0) {
        parts += pluralStringResource(R.plurals.subagents_work_notes, summary.notes, summary.notes)
    }
    return parts.joinToString(", ")
}

@Composable
private fun earlyStopText(status: SubagentUiStatus, count: Int): String {
    val plural = when (status) {
        SubagentUiStatus.COST_LIMIT -> R.plurals.subagents_work_over_budget
        SubagentUiStatus.STEP_LIMIT -> R.plurals.subagents_work_out_of_steps
        SubagentUiStatus.TIME_LIMIT -> R.plurals.subagents_work_timed_out
        SubagentUiStatus.FAILED -> R.plurals.subagents_work_failed
        // Running and Done are never early stops; Stopped is what is left.
        SubagentUiStatus.STOPPED, SubagentUiStatus.RUNNING, SubagentUiStatus.DONE -> R.plurals.subagents_work_stopped
    }
    return pluralStringResource(plural, count, count)
}
