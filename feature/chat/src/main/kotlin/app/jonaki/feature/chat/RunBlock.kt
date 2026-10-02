package app.jonaki.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.jonaki.core.ui.DotStyle
import app.jonaki.core.ui.GlowDot
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily

private val TrackColumnWidth = 24.dp

/**
 * One agent turn's tool steps as a rail track (D-024): a station per step,
 * the line between stations filled where the work is done. A finished run
 * folds to its summary line; an active run is always open.
 */
@Composable
internal fun RunBlock(run: ChatItem.Run, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(run.id) { mutableStateOf(false) }
    val open = run.isActive || expanded
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.animateContentSize().padding(horizontal = 14.dp, vertical = 6.dp)) {
            RunHeader(run, open, onToggle = { expanded = !expanded })
            if (open) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    run.steps.forEachIndexed { index, step ->
                        StepRow(
                            step = step,
                            isFirst = index == 0,
                            isLast = index == run.steps.lastIndex,
                            nextIsDone = run.steps.getOrNull(index + 1)?.status == StepUiStatus.DONE,
                        )
                    }
                }
            }
        }
    }
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
            Text(
                stringResource(R.string.chat_run_working),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.live,
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
private fun StepRow(step: StepUi, isFirst: Boolean, isLast: Boolean, nextIsDone: Boolean) {
    val colors = JonakiTheme.colors
    val doneColor = colors.done
    val trackColor = colors.track
    // The segment below a station is "travelled" when this step and the next are done.
    val segmentBelowColor = if (step.status == StepUiStatus.DONE && nextIsDone) doneColor else trackColor
    val segmentAboveColor = if (step.status == StepUiStatus.DONE) doneColor else trackColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = TrackColumnWidth.toPx() / 2
                val stationY = 16.dp.toPx()
                val stroke = 2.dp.toPx()
                if (!isFirst) {
                    drawLine(segmentAboveColor, Offset(x, 0f), Offset(x, stationY), stroke)
                }
                if (!isLast) {
                    drawLine(segmentBelowColor, Offset(x, stationY), Offset(x, size.height), stroke)
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
                    step.toolName.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.06.em),
                    fontWeight = FontWeight.Bold,
                    color = if (step.status == StepUiStatus.RUNNING) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
            StepDetail(step)
        }
    }
}

@Composable
private fun StationDot(status: StepUiStatus) {
    val colors = JonakiTheme.colors
    val outline = MaterialTheme.colorScheme.outline
    val (color, style) = when (status) {
        StepUiStatus.DONE -> colors.done to DotStyle.QUIET
        StepUiStatus.RUNNING -> colors.live to DotStyle.GLOWING
        StepUiStatus.WAITING_FOR_APPROVAL -> colors.deny to DotStyle.RING
        StepUiStatus.FAILED -> colors.deny to DotStyle.QUIET
        StepUiStatus.DENIED -> outline to DotStyle.QUIET
        StepUiStatus.STOPPED -> outline to DotStyle.RING
    }
    // GlowDot's canvas is 2.4x the dot; 10 dp dot -> 24 dp box, centred on the track.
    GlowDot(color, style, dotSize = 10.dp)
}

@Composable
private fun StepDetail(step: StepUi) {
    val statusWord = when (step.status) {
        StepUiStatus.WAITING_FOR_APPROVAL -> stringResource(R.string.chat_step_waiting)
        StepUiStatus.FAILED -> stringResource(R.string.chat_step_failed)
        StepUiStatus.DENIED -> stringResource(R.string.chat_step_denied)
        StepUiStatus.STOPPED -> stringResource(R.string.chat_step_stopped)
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
}
