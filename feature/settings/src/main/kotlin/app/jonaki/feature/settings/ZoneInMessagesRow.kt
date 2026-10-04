package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** How much of the time zone each message tells the model, as Settings > Answers offers it. */
enum class ZoneChoice {
    NONE,
    OFFSET,
    NAME,
}

/**
 * The time zone choice on Settings > Answers. The second line says what is
 * sent in every case, so that "None" is not read as "no time at all".
 */
@Composable
internal fun ZoneInMessagesRow(selected: ZoneChoice, onSelect: (ZoneChoice) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.settings_zone_in_messages), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.settings_zone_in_messages_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ZoneChoice.entries.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = choice == selected,
                    onClick = { onSelect(choice) },
                    shape = SegmentedButtonDefaults.itemShape(index, ZoneChoice.entries.size),
                    icon = {},
                    label = { Text(zoneChoiceLabel(choice), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
private fun zoneChoiceLabel(choice: ZoneChoice): String = stringResource(
    when (choice) {
        ZoneChoice.NONE -> R.string.settings_zone_none
        ZoneChoice.OFFSET -> R.string.settings_zone_offset
        ZoneChoice.NAME -> R.string.settings_zone_name
    },
)
