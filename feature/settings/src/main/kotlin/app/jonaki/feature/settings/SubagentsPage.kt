package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat

/** The limits on Settings > Subagents (D-138). */
enum class SubagentLimit {
    /** Per message, before a delegate call waits for the user (D-137). */
    WITHOUT_ASKING,

    /** In one delegate call (D-060). */
    PER_CALL,

    /** Per message, above which the approval card warns about the cost (D-137). */
    WARN_ABOVE,

    /** Each subagent's tool steps (D-061). */
    TOOL_STEPS,

    /** Each subagent's cost cap, in US cents (D-061). */
    COST_CENTS,

    /** Each subagent's time limit (D-061). */
    MINUTES,
}

/** One limit with its range; [step] is how far one tap moves it. */
@Immutable
data class SubagentLimitUi(
    val limit: SubagentLimit,
    val value: Int,
    val min: Int,
    val max: Int,
    val step: Int = 1,
) {
    val canLower: Boolean
        get() = value - step >= min

    val canRaise: Boolean
        get() = value + step <= max

    companion object {
        /** The defaults, for previews and the summary tests. */
        val SAMPLE: List<SubagentLimitUi> = listOf(
            SubagentLimitUi(SubagentLimit.WITHOUT_ASKING, value = 2, min = 0, max = 10),
            SubagentLimitUi(SubagentLimit.PER_CALL, value = 3, min = 1, max = 6),
            SubagentLimitUi(SubagentLimit.WARN_ABOVE, value = 5, min = 2, max = 20),
            SubagentLimitUi(SubagentLimit.TOOL_STEPS, value = 10, min = 1, max = 30),
            SubagentLimitUi(SubagentLimit.COST_CENTS, value = 10, min = 5, max = 100, step = 5),
            SubagentLimitUi(SubagentLimit.MINUTES, value = 10, min = 1, max = 30),
        )
    }
}

/** A subagent type the user made, as its row in the list shows it. */
@Immutable
data class CustomSubagentRowUi(
    val name: String,
    val description: String,
)

/** The limits that decide how many subagents start, then those that bound each one. */
private val PER_MESSAGE_LIMITS = listOf(SubagentLimit.WITHOUT_ASKING, SubagentLimit.PER_CALL, SubagentLimit.WARN_ABOVE)
private val EACH_SUBAGENT_LIMITS = listOf(SubagentLimit.TOOL_STEPS, SubagentLimit.COST_CENTS, SubagentLimit.MINUTES)

/** Settings > Subagents: limits, the built-in types' models, and the user's own types (D-138). */
@Composable
internal fun SubagentsPage(state: SettingsUiState, actions: SettingsActions) {
    SectionLabel(stringResource(R.string.settings_subagents_section_limits))
    LimitRows(state.subagentLimits.filter { limit -> limit.limit in PER_MESSAGE_LIMITS }, actions.onSubagentLimitChange)
    SectionLabel(stringResource(R.string.settings_subagents_section_each))
    LimitRows(state.subagentLimits.filter { limit -> limit.limit in EACH_SUBAGENT_LIMITS }, actions.onSubagentLimitChange)
    if (state.subagentModels.isNotEmpty()) {
        SectionLabel(stringResource(R.string.settings_root_models))
        Group {
            SubagentModelRows(state.subagentModels, state.subagentModelOptions, actions.onSubagentModelChange)
        }
    }
    SectionLabel(stringResource(R.string.settings_subagents_section_custom))
    Group {
        for (subagent in state.customSubagents) {
            CustomSubagentRow(subagent, onClick = { actions.onOpenCustomSubagent(subagent.name) })
            GroupDivider()
        }
        AddSubagentRow(onClick = { actions.onOpenCustomSubagent(null) })
    }
}

@Composable
private fun LimitRows(limits: List<SubagentLimitUi>, onChange: (SubagentLimit, Int) -> Unit) {
    Group {
        limits.forEachIndexed { index, limit ->
            if (index > 0) GroupDivider()
            LimitRow(limit, onChange)
        }
    }
}

/** The title wraps rather than cut, so the number and its buttons always fit at 360 dp and font scale 1.3. */
@Composable
private fun LimitRow(limit: SubagentLimitUi, onChange: (SubagentLimit, Int) -> Unit) {
    val title = limitTitle(limit.limit)
    val scope = limitScope(limit.limit)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (scope != null) {
                Text(scope, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { onChange(limit.limit, limit.value - limit.step) }, enabled = limit.canLower) {
            Icon(JonakiIcons.Remove, contentDescription = stringResource(R.string.settings_subagents_lower, title))
        }
        Text(
            limitValue(limit),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = MonospaceFamily,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 44.dp),
        )
        IconButton(onClick = { onChange(limit.limit, limit.value + limit.step) }, enabled = limit.canRaise) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_subagents_raise, title))
        }
    }
}

@Composable
private fun limitTitle(limit: SubagentLimit): String = when (limit) {
    SubagentLimit.WITHOUT_ASKING -> stringResource(R.string.settings_subagents_without_asking)
    SubagentLimit.PER_CALL -> stringResource(R.string.settings_subagents_per_call)
    SubagentLimit.WARN_ABOVE -> stringResource(R.string.settings_subagents_warn_above)
    SubagentLimit.TOOL_STEPS -> stringResource(R.string.settings_subagents_tool_steps)
    SubagentLimit.COST_CENTS -> stringResource(R.string.settings_subagents_cost)
    SubagentLimit.MINUTES -> stringResource(R.string.settings_subagents_minutes)
}

/** The per-message limits say what they count; each subagent's budgets need no second line. */
@Composable
private fun limitScope(limit: SubagentLimit): String? = when (limit) {
    SubagentLimit.WITHOUT_ASKING -> stringResource(R.string.settings_subagents_per_message)
    SubagentLimit.PER_CALL -> stringResource(R.string.settings_subagents_in_one_call)
    SubagentLimit.WARN_ABOVE -> stringResource(R.string.settings_subagents_per_message)
    SubagentLimit.TOOL_STEPS -> null
    SubagentLimit.COST_CENTS -> null
    SubagentLimit.MINUTES -> null
}

private fun limitValue(limit: SubagentLimitUi): String {
    if (limit.limit == SubagentLimit.COST_CENTS) {
        return UsageFormat.limit(limit.value / 100.0)
    }
    return limit.value.toString()
}

/** The name and the description keep one line each and end in "…" (D-029). */
@Composable
private fun CustomSubagentRow(subagent: CustomSubagentRowUi, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                subagent.name,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = MonospaceFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subagent.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddSubagentRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.settings_subagents_add),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
