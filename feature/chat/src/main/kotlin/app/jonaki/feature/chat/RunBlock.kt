package app.jonaki.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.DotStyle
import app.jonaki.core.ui.GlowDot
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat

private val TrackColumnWidth = 24.dp

/**
 * One agent turn's tool steps as a rail track (D-024) in a thin bordered
 * panel (D-123): a station per step, and only the running station is lit.
 * A finished run folds to its summary line; an active run is always open.
 */
@Composable
internal fun RunBlock(
    run: ChatItem.Run,
    onOpenStep: (stepId: String) -> Unit,
    modifier: Modifier = Modifier,
    /** A subagent's row was tapped: the chat opens its page (D-126). */
    onOpenSubagent: (subagentId: String) -> Unit = {},
) {
    var expanded by rememberSaveable(run.id) { mutableStateOf(false) }
    val open = run.isActive || expanded
    RunPanel(modifier) {
        Column(Modifier.animateContentSize().padding(horizontal = 14.dp, vertical = 4.dp)) {
            RunHeader(run, open, onToggle = { expanded = !expanded })
            if (open) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    run.steps.forEachIndexed { index, step ->
                        val group = run.subagents.filter { subagent -> subagent.delegateStepId == step.id }
                        if (group.isEmpty()) {
                            StepRow(
                                step = step,
                                isFirst = index == 0,
                                isLast = index == run.steps.lastIndex,
                                onOpen = onOpenStep,
                            )
                        } else {
                            // A delegate step becomes its group: "3 subagents", "1 of 3 done", a row each (D-126).
                            StepRow(
                                step = step,
                                isFirst = index == 0,
                                isLast = index == run.steps.lastIndex,
                                onOpen = onOpenStep,
                                label = subagentGroupLabel(group),
                                detailOverride = subagentGroupProgress(group),
                            ) {
                                SubagentGroupRows(group, onOpenSubagent)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The run block's frame: a hairline border, no fill at night. */
@Composable
internal fun RunPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = JonakiTheme.colors.runPanel,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun RunHeader(run: ChatItem.Run, open: Boolean, onToggle: () -> Unit) {
    val colors = JonakiTheme.colors
    val headerModifier = if (run.isActive) Modifier else Modifier.clickable(onClick = onToggle)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = headerModifier.fillMaxWidth().heightIn(min = 40.dp),
    ) {
        if (run.isActive) {
            GlowDot(colors.live, DotStyle.GLOWING, dotSize = 8.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.chat_run_working),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = colors.inkSoft,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.chat_run_step, run.steps.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                pluralStringResource(R.plurals.chat_run_steps, run.steps.size, run.steps.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                formatStepDuration(totalDurationMillis(run.steps)),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val cost = run.costUsd
            if (cost != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    UsageFormat.cost(cost),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (!run.isActive) {
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (open) R.string.chat_run_hide else R.string.chat_run_show),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun StepRow(
    step: StepUi,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: (stepId: String) -> Unit,
    label: String = stepLabel(step.toolName),
    /** Replaces the step's own detail line, for example a subagent group's progress. */
    detailOverride: String? = null,
    /** Shown under the step's lines, beside the rail: a delegate step's subagent rows. */
    below: (@Composable () -> Unit)? = null,
) {
    // One plain rail: the lit station alone marks where the work is.
    val trackColor = JonakiTheme.colors.track
    val openModifier = if (step.opensDetail) Modifier.clickable { onOpen(step.id) } else Modifier
    Row(
        modifier = openModifier
            .fillMaxWidth()
            .drawBehind {
                val x = TrackColumnWidth.toPx() / 2
                val stationY = 16.dp.toPx()
                val stroke = 2.dp.toPx()
                if (!isFirst) {
                    drawLine(trackColor, Offset(x, 0f), Offset(x, stationY), stroke)
                }
                if (!isLast) {
                    drawLine(trackColor, Offset(x, stationY), Offset(x, size.height), stroke)
                }
            }
            .padding(vertical = 4.dp),
    ) {
        Box(Modifier.width(TrackColumnWidth), contentAlignment = Alignment.TopCenter) {
            StationDot(step.status)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                val duration = step.durationMillis
                if (duration != null) {
                    Text(
                        formatStepDuration(duration),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (detailOverride == null) {
                StepDetail(step)
            } else {
                Text(
                    detailOverride,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (below != null) {
                below()
            }
        }
    }
}

@Composable
private fun StationDot(status: StepUiStatus) {
    val colors = JonakiTheme.colors
    val outline = MaterialTheme.colorScheme.outline
    if (status == StepUiStatus.DONE) {
        DoneStation()
        return
    }
    val (color, style) = when (status) {
        StepUiStatus.DONE -> colors.track to DotStyle.QUIET
        StepUiStatus.RUNNING -> colors.live to DotStyle.GLOWING
        StepUiStatus.WAITING_FOR_APPROVAL -> colors.deny to DotStyle.RING
        StepUiStatus.FAILED -> colors.deny to DotStyle.QUIET
        StepUiStatus.DENIED -> outline to DotStyle.QUIET
        StepUiStatus.STOPPED -> outline to DotStyle.RING
        StepUiStatus.SKIPPED -> outline to DotStyle.RING
    }
    // GlowDot's canvas is 2.4x the dot; 10 dp dot -> 24 dp box, centred on the track.
    GlowDot(color, style, dotSize = 10.dp)
}

/** A finished station: a check on the rail's colour, unlit. Its box matches GlowDot's 24 dp. */
@Composable
private fun DoneStation() {
    val colors = JonakiTheme.colors
    Box(Modifier.size(TrackColumnWidth), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(16.dp).background(colors.track, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.inkSoft, modifier = Modifier.size(11.dp))
        }
    }
}

@Composable
private fun StepDetail(step: StepUi) {
    val statusWord = when (step.status) {
        StepUiStatus.WAITING_FOR_APPROVAL -> stringResource(R.string.chat_step_waiting)
        StepUiStatus.FAILED -> stringResource(R.string.chat_step_failed)
        StepUiStatus.DENIED -> stringResource(R.string.chat_step_denied)
        StepUiStatus.STOPPED -> stringResource(R.string.chat_step_stopped)
        StepUiStatus.SKIPPED -> stringResource(R.string.chat_step_skipped)
        StepUiStatus.DONE, StepUiStatus.RUNNING -> null
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val deny = JonakiTheme.colors.deny
    val text = buildAnnotatedString {
        if (statusWord != null) {
            withStyle(SpanStyle(color = deny, fontWeight = FontWeight.SemiBold)) { append(statusWord) }
            if (step.query != null || step.detail.isNotEmpty()) append(" · ")
        }
        if (step.query != null) {
            withStyle(SpanStyle(color = onSurface, fontStyle = FontStyle.Italic)) {
                append("“")
                append(step.query)
                append("”")
            }
            if (step.detail.isNotEmpty()) append(" · ")
        }
        append(step.detail)
    }
    if (text.isNotEmpty()) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
    if (step.guardNote != null) {
        // The stored note is English only, like other stored tool text; it has at most two lines.
        Text(
            step.guardNote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}
