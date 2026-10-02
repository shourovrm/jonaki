package app.jonaki.core.ui

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow

/**
 * The thinking level the user picks for a model in Settings or for one
 * thread in the chat (D-057). DEFAULT leaves the choice to the next level up:
 * the thread follows the model's setting, the model follows its own default.
 */
enum class ThinkingChoice {
    DEFAULT,
    OFF,
    LOW,
    MEDIUM,
    HIGH,
}

@Composable
fun thinkingChoiceLabel(choice: ThinkingChoice): String = stringResource(
    when (choice) {
        ThinkingChoice.DEFAULT -> R.string.ui_thinking_default
        ThinkingChoice.OFF -> R.string.ui_thinking_off
        ThinkingChoice.LOW -> R.string.ui_thinking_low
        ThinkingChoice.MEDIUM -> R.string.ui_thinking_medium
        ThinkingChoice.HIGH -> R.string.ui_thinking_high
    },
)

/** Five segments, one per choice, used in the chat's model sheet. */
@Composable
fun ThinkingChoiceRow(selected: ThinkingChoice, onSelect: (ThinkingChoice) -> Unit, modifier: Modifier = Modifier) {
    val choices = ThinkingChoice.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                icon = {},
                label = { Text(thinkingChoiceLabel(choice), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}
