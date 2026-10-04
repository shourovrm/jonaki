package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.MonospaceFamily

/**
 * How an unanswered reminder rings again. The app owns the allowed values
 * and passes them in, so this screen never holds a second copy of them.
 */
@Immutable
data class ReminderSettingsUi(
    val intervalMinutes: Int = 10,
    /** The minutes the interval can take, smallest first. */
    val intervalOptions: List<Int> = listOf(5, 10, 15, 30, 60),
    val maxRepeats: Int = 5,
    val maxRepeatsLimit: Int = 10,
) {
    /** The next smaller interval, or null at the smallest. */
    val shorterInterval: Int?
        get() = intervalOptions.lastOrNull { minutes -> minutes < intervalMinutes }

    val longerInterval: Int?
        get() = intervalOptions.firstOrNull { minutes -> minutes > intervalMinutes }
}

/** Two stepper rows in the look of the subagent limits: a name, the value, and minus and plus buttons. */
@Composable
internal fun ReminderSettingsSection(settings: ReminderSettingsUi, actions: SettingsActions) {
    SectionLabel(stringResource(R.string.settings_section_reminders))
    Group {
        val intervalTitle = stringResource(R.string.settings_reminders_interval)
        StepperRow(
            title = intervalTitle,
            line = null,
            value = stringResource(R.string.settings_reminders_minutes, settings.intervalMinutes),
            onLower = settings.shorterInterval?.let { minutes -> { actions.onReminderIntervalChange(minutes) } },
            onRaise = settings.longerInterval?.let { minutes -> { actions.onReminderIntervalChange(minutes) } },
        )
        GroupDivider()
        val repeatsTitle = stringResource(R.string.settings_reminders_repeats)
        StepperRow(
            title = repeatsTitle,
            line = stringResource(R.string.settings_reminders_repeats_zero),
            value = settings.maxRepeats.toString(),
            onLower = if (settings.maxRepeats > 0) {
                { actions.onReminderMaxRepeatsChange(settings.maxRepeats - 1) }
            } else {
                null
            },
            onRaise = if (settings.maxRepeats < settings.maxRepeatsLimit) {
                { actions.onReminderMaxRepeatsChange(settings.maxRepeats + 1) }
            } else {
                null
            },
        )
    }
}

/** The title wraps rather than cut, so the value and its buttons always fit at 360 dp and font scale 1.3 (D-029). A null callback disables its button. */
@Composable
private fun StepperRow(title: String, line: String?, value: String, onLower: (() -> Unit)?, onRaise: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (line != null) {
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { onLower?.invoke() }, enabled = onLower != null) {
            Icon(JonakiIcons.Remove, contentDescription = stringResource(R.string.settings_subagents_lower, title))
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = MonospaceFamily,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        IconButton(onClick = { onRaise?.invoke() }, enabled = onRaise != null) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_subagents_raise, title))
        }
    }
}
