package app.jonaki.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.DotStyle
import app.jonaki.core.ui.GlowDot
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MarkdownText
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat
import java.util.Locale

/**
 * One subagent's own page (D-126): its whole task, steps and cost against
 * the limits, its step track, what it skipped, the files it wrote and its
 * answer. While it runs the steps come first; once it ends they fold to one
 * line, because the answer and the files are what the user came to check.
 *
 * @param siblings the subagents of the same delegate call, in order; the arrows step through them.
 * @param onStop null when this subagent cannot be stopped alone, which hides the Stop bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubagentPage(
    subagent: SubagentUi,
    siblings: List<SubagentUi>,
    onSelect: (subagentId: String) -> Unit,
    onBack: () -> Unit,
    onOpenFile: (path: String) -> Unit,
    onOpenStep: (stepId: String) -> Unit,
    onStop: ((subagentId: String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val index = siblings.indexOfFirst { sibling -> sibling.id == subagent.id }
    val previous = siblings.getOrNull(index - 1)
    val next = siblings.getOrNull(index + 1)
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                        }
                    },
                    title = { PageTitle(subagent) },
                    actions = {
                        if (siblings.size > 1) {
                            IconButton(onClick = { previous?.let { onSelect(it.id) } }, enabled = previous != null) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = stringResource(R.string.subagents_previous),
                                )
                            }
                            Text(
                                "${index + 1}/${siblings.size}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(onClick = { next?.let { onSelect(it.id) } }, enabled = next != null) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.subagents_next),
                                )
                            }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            if (subagent.isRunning && onStop != null) {
                StopBar(subagent, onStop)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // An opened view wraps the whole task (D-029).
            SelectionContainer {
                Text(subagent.task, style = MaterialTheme.typography.bodyLarge)
            }
            Meters(subagent)
            if (subagent.isRunning) {
                StepsSection(subagent, onOpenStep)
                AnswerSection(subagent)
            } else {
                FoldedSteps(subagent, onOpenStep)
                SkippedSection(subagent.skippedSteps)
                FilesSection(subagent.filesWritten, onOpenFile)
                AnswerSection(subagent)
            }
        }
    }
}

/** The name, and under it the model while it runs or how it ended. */
@Composable
private fun PageTitle(subagent: SubagentUi) {
    Column {
        Text(
            subagentName(subagent.label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        val subtitle = if (subagent.isRunning) {
            subagent.modelName ?: subagentStatusLabel(subagent.status)
        } else {
            subagentNowText(SubagentRows.rowOf(subagent).now)
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Steps and cost against D-061's limits, drawn in ink: a budget is not live work. */
@Composable
private fun Meters(subagent: SubagentUi) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val stepsUsed = subagent.steps.size
        Meter(
            label = stringResource(R.string.subagents_steps),
            amount = stringResource(R.string.subagents_used_of_limit, stepsUsed.toString(), subagent.stepLimit.toString()),
            share = SubagentRows.share(stepsUsed.toDouble(), subagent.stepLimit.toDouble()),
        )
        // A call with no known cost adds nothing (D-061), so an unknown cost reads as none spent.
        val cost = subagent.costUsd ?: 0.0
        Meter(
            label = stringResource(R.string.subagents_cost),
            amount = stringResource(R.string.subagents_used_of_limit, UsageFormat.cost(cost), limitText(subagent.costLimitUsd)),
            share = SubagentRows.share(cost, subagent.costLimitUsd),
        )
    }
}

/** "$0.10": a limit is a round sum, so it keeps two decimals. */
private fun limitText(limitUsd: Double): String = "$" + String.format(Locale.ENGLISH, "%.2f", limitUsd)

@Composable
private fun Meter(label: String, amount: String, share: Float) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                amount,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                color = JonakiTheme.colors.inkSoft,
            )
        }
        Box(
            Modifier
                .padding(top = 5.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(JonakiTheme.colors.track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepsSection(subagent: SubagentUi, onOpenStep: (String) -> Unit) {
    Column {
        SectionLabel(stringResource(R.string.subagents_steps), formatStepDuration(totalDurationMillis(subagent.steps)))
        StepTrack(subagent.steps, onOpenStep)
    }
}

@Composable
private fun StepTrack(steps: List<StepUi>, onOpenStep: (String) -> Unit) {
    if (steps.isEmpty()) {
        Text(
            stringResource(R.string.subagents_no_steps),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        return
    }
    Column(Modifier.padding(top = 6.dp)) {
        steps.forEachIndexed { index, step ->
            StepRow(step = step, isFirst = index == 0, isLast = index == steps.lastIndex, onOpen = onOpenStep)
        }
    }
}

/** After the end: "7 steps  1:52", opened on a tap. */
@Composable
private fun FoldedSteps(subagent: SubagentUi, onOpenStep: (String) -> Unit) {
    var open by rememberSaveable(subagent.id) { mutableStateOf(false) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { open = !open },
        ) {
            Text(
                pluralStringResource(R.plurals.chat_run_steps, subagent.steps.size, subagent.steps.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            val duration = SubagentRows.durationOf(subagent)
            if (duration != null) {
                Text(
                    formatStepDuration(duration),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (open) R.string.chat_run_hide else R.string.chat_run_show),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            StepTrack(subagent.steps, onOpenStep)
        }
    }
}

@Composable
private fun SkippedSection(skipped: List<StepUi>) {
    if (skipped.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.subagents_skipped))
        for (step in skipped) {
            Row {
                Icon(
                    JonakiIcons.SkipNext,
                    contentDescription = null,
                    tint = JonakiTheme.colors.inkSoft,
                    modifier = Modifier.padding(top = 2.dp).size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    val target = step.query ?: step.detail
                    Text(
                        listOf(stepLabel(step.toolName), target).filter { part -> part.isNotBlank() }.joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        // D-029: paths wrap to two lines, then "…".
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.subagents_skipped_why),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilesSection(paths: List<String>, onOpenFile: (String) -> Unit) {
    if (paths.isEmpty()) {
        return
    }
    Column {
        SectionLabel(stringResource(R.string.subagents_files))
        for (path in paths) {
            FileRow(path, onOpenFile)
        }
    }
}

/** A file a subagent wrote, with Open; the work sheet lists them the same way. */
@Composable
internal fun FileRow(path: String, onOpenFile: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(JonakiIcons.Document, contentDescription = null, tint = JonakiTheme.colors.inkSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            path,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onOpenFile(path) }) {
            Text(stringResource(R.string.subagents_open))
        }
    }
}

@Composable
private fun AnswerSection(subagent: SubagentUi) {
    Column {
        SectionLabel(stringResource(R.string.chat_subagent_answer))
        val answer = subagent.answer
        if (answer.isNullOrBlank()) {
            Text(
                stringResource(R.string.subagents_answer_pending, subagentName(subagent.label)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Box(Modifier.padding(top = 4.dp)) {
                MarkdownText(answer, showCaret = false)
            }
        }
    }
}

/** While it runs: "Working" with the live mark, and a Stop for this subagent alone. */
@Composable
private fun StopBar(subagent: SubagentUi, onStop: (String) -> Unit) {
    Column(Modifier.navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            GlowDot(JonakiTheme.colors.live, DotStyle.GLOWING, dotSize = 8.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                subagentStatusLabel(SubagentUiStatus.RUNNING),
                style = MaterialTheme.typography.labelLarge,
                color = JonakiTheme.colors.inkSoft,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { onStop(subagent.id) }) {
                Icon(JonakiIcons.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.subagents_stop, subagentName(subagent.label)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
