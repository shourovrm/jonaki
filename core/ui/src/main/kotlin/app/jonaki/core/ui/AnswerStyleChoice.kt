package app.jonaki.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import java.text.NumberFormat
import java.util.Locale

/**
 * The answer style the user picks in Settings or for one thread (D-108).
 * DEFAULT is offered only in a thread, where it follows Settings.
 */
enum class AnswerStyleChoice {
    DEFAULT,
    CONCISE,
    NORMAL,
    DETAILED,
}

@Composable
fun answerStyleChoiceLabel(choice: AnswerStyleChoice): String = stringResource(
    when (choice) {
        AnswerStyleChoice.DEFAULT -> R.string.ui_style_default
        AnswerStyleChoice.CONCISE -> R.string.ui_style_concise
        AnswerStyleChoice.NORMAL -> R.string.ui_style_normal
        AnswerStyleChoice.DETAILED -> R.string.ui_style_detailed
    },
)

/** One segment per choice in [choices]. */
@Composable
fun AnswerStyleChoiceRow(
    choices: List<AnswerStyleChoice>,
    selected: AnswerStyleChoice,
    onSelect: (AnswerStyleChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                icon = {},
                label = { Text(answerStyleChoiceLabel(choice), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/**
 * Longest text one instructions field takes: about 2,000 tokens at 4
 * characters per token, sent with every request of a thread (D-107).
 */
const val INSTRUCTIONS_MAX_CHARACTERS = 8_000

/** A many-line field for instructions that stops at [INSTRUCTIONS_MAX_CHARACTERS] and shows the count. */
@Composable
fun InstructionsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { text -> onValueChange(text.take(INSTRUCTIONS_MAX_CHARACTERS)) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
        )
        val numbers = NumberFormat.getIntegerInstance(Locale.ENGLISH)
        Text(
            stringResource(R.string.ui_instructions_count, numbers.format(value.length), numbers.format(INSTRUCTIONS_MAX_CHARACTERS)),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
    }
}
