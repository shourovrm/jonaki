package app.jonaki.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily

/**
 * Turns the result text MemoryExtractor writes onto a background row into the line
 * under a turn. The extractor writes "N added", "N to review", "N added, M to review",
 * "Nothing new" or "Answer unreadable: why"; a failed call writes its failure text.
 */
object MemoryLineText {
    /** Starts every line about a call that saved nothing because it failed; the chat offers Fix on such a line. */
    const val NOT_UPDATED_PREFIX = "Memory not updated: "

    private val addedPattern = Regex("""(\d+) added""")
    private val reviewPattern = Regex("""(\d+) to review""")
    private val addedAndReviewPattern = Regex("""(\d+) added, (\d+) to review""")

    fun from(result: String?): String {
        val text = result?.trim().orEmpty()
        if (text == "Nothing new") {
            return "Nothing new to remember"
        }
        val addedAndReview = addedAndReviewPattern.matchEntire(text)
        if (addedAndReview != null) {
            val added = addedAndReview.groupValues[1].toInt()
            val toReview = addedAndReview.groupValues[2].toInt()
            return "Remembered ${factCount(added)}, $toReview to review"
        }
        val added = addedPattern.matchEntire(text)?.groupValues?.get(1)?.toInt()
        if (added != null) {
            return "Remembered ${factCount(added)}"
        }
        val toReview = reviewPattern.matchEntire(text)?.groupValues?.get(1)?.toInt()
        if (toReview != null) {
            return "${factCount(toReview)} to review"
        }
        return NOT_UPDATED_PREFIX + text
    }

    private fun factCount(count: Int): String = if (count == 1) "1 fact" else "$count facts"
}

/** The action at the end of a memory line: See opens Memory, Why the log, Fix the place that needs a key. */
enum class MemoryLineAction { SEE, WHY, FIX }

@Composable
private fun MemoryLineAction.label(): String = when (this) {
    MemoryLineAction.SEE -> stringResource(R.string.chat_memory_see)
    MemoryLineAction.WHY -> stringResource(R.string.chat_memory_why)
    MemoryLineAction.FIX -> stringResource(R.string.chat_memory_fix)
}

/** A muted system line with a short action at its end. */
@Composable
private fun SystemLine(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
    ) {
        Icon(
            JonakiIcons.Memory,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            actionLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onAction).heightIn(min = 40.dp).padding(horizontal = 4.dp),
        )
    }
}

@Composable
internal fun MemoryLineRow(line: ChatItem.MemoryLine, onMemoryLine: () -> Unit) {
    SystemLine(line.text, line.action.label(), onMemoryLine)
}

@Composable
internal fun SkillProposalLineRow(line: ChatItem.SkillProposalLine, onOpenSkills: () -> Unit) {
    SystemLine(
        stringResource(R.string.chat_skill_proposed, line.skillName),
        stringResource(R.string.chat_skill_review),
        onOpenSkills,
    )
}

/** A failure in a turn: a short sentence, the raw details folded away, and a model that would work. */
@Composable
internal fun ErrorCardRow(card: ChatItem.ErrorCard, onUseModel: (modelKey: String) -> Unit) {
    var detailsOpen by rememberSaveable(card.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SelectionContainer {
            Text(card.text, style = MaterialTheme.typography.bodyMedium, color = JonakiTheme.colors.deny)
        }
        if (card.details != null) {
            TextButton(onClick = { detailsOpen = !detailsOpen }) {
                Text(stringResource(if (detailsOpen) R.string.chat_error_hide_details else R.string.chat_error_details))
            }
            if (detailsOpen) {
                SelectionContainer {
                    Text(
                        card.details,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonospaceFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (card.suggestedModelKey != null && card.suggestedModelName != null) {
            Button(onClick = { onUseModel(card.suggestedModelKey) }) {
                Text(stringResource(R.string.chat_error_use_model, card.suggestedModelName))
            }
        }
    }
}

/** Stands in for the message box while no model can answer. */
@Composable
internal fun NoModelBlock(onSetUp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(stringResource(R.string.chat_no_model_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.chat_no_model_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSetUp, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.chat_no_model_action))
        }
    }
}
