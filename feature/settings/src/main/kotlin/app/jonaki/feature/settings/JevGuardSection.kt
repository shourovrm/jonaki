package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.MonospaceFamily
import kotlin.math.roundToInt

/**
 * The Jev guard on Settings > Guardrails: its two jobs, how strict it is and
 * what it cost this month. Without an OpenRouter key nothing can be switched,
 * and one line says why.
 */
@Composable
internal fun JevGuardSection(options: JevOptionsUi, isAvailable: Boolean, onChange: (JevOptionsUi) -> Unit) {
    Group {
        OptionSwitchRow(
            text = stringResource(R.string.settings_jev_skip_cards),
            isOn = options.skipsCards && isAvailable,
            onChange = { on -> onChange(options.copy(skipsCards = on)) },
            enabled = isAvailable,
        )
        GroupDivider()
        OptionSwitchRow(
            text = stringResource(R.string.settings_jev_screen_outside_content),
            isOn = options.screensOutsideContent && isAvailable,
            onChange = { on -> onChange(options.copy(screensOutsideContent = on)) },
            enabled = isAvailable,
        )
        GroupDivider()
        StrictnessRow(
            selected = options.strictness,
            enabled = isAvailable && (options.skipsCards || options.screensOutsideContent),
            onSelect = { strictness -> onChange(options.copy(strictness = strictness)) },
        )
        if (options.costThisMonth != null) {
            GroupDivider()
            CostRow(options.costThisMonth)
        }
    }
    Text(
        stringResource(if (isAvailable) R.string.settings_jev_guard_description else R.string.settings_jev_guard_needs_key),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Three fixed stops; the names under the slider are its scale, so no number is shown. */
@Composable
private fun StrictnessRow(selected: JevStrictnessChoice, enabled: Boolean, onSelect: (JevStrictnessChoice) -> Unit) {
    val stops = JevStrictnessChoice.entries
    val selectedName = stringResource(nameOf(selected))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.settings_jev_strictness), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = stops.indexOf(selected).toFloat(),
            onValueChange = { position -> onSelect(stops[position.roundToInt().coerceIn(0, stops.lastIndex)]) },
            valueRange = 0f..stops.lastIndex.toFloat(),
            // Steps are the stops between the two ends.
            steps = stops.size - 2,
            enabled = enabled,
            modifier = Modifier.semantics { stateDescription = selectedName },
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            for (stop in stops) {
                Text(
                    stringResource(nameOf(stop)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stop == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun nameOf(strictness: JevStrictnessChoice): Int = when (strictness) {
    JevStrictnessChoice.CAREFUL -> R.string.settings_jev_strictness_careful
    JevStrictnessChoice.BALANCED -> R.string.settings_jev_strictness_balanced
    JevStrictnessChoice.RELAXED -> R.string.settings_jev_strictness_relaxed
}

@Composable
private fun CostRow(cost: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.settings_jev_cost_month),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Text(cost, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFamily), maxLines = 1)
    }
}
