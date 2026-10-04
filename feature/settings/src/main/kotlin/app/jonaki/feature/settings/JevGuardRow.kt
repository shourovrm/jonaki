package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The Jev guard switch on Settings > Tools and approvals. Without an
 * OpenRouter key the switch is off and disabled, and the second line says why.
 */
@Composable
internal fun JevGuardRow(isOn: Boolean, isAvailable: Boolean, onChange: (Boolean) -> Unit) {
    val rowModifier = if (isAvailable) Modifier.clickable { onChange(!isOn) } else Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(R.string.settings_jev_guard), style = MaterialTheme.typography.titleSmall)
            val descriptionId = if (isAvailable) R.string.settings_jev_guard_description else R.string.settings_jev_guard_needs_key
            Text(
                stringResource(descriptionId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = isOn && isAvailable, onCheckedChange = onChange, enabled = isAvailable)
    }
}
