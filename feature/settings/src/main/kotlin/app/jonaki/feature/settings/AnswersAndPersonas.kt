package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.AnswerStyleChoice
import app.jonaki.core.ui.AnswerStyleChoiceRow

/** One saved persona in the Settings list (D-109). */
@Immutable
data class PersonaRowUi(
    val id: String,
    val name: String,
)

/** Settings > Answers: the answer style, the custom instructions and the personas (D-128). */
@Composable
internal fun AnswersAndPersonasSections(state: SettingsUiState, actions: SettingsActions) {
    // The page's title names the style row, so it needs no label of its own.
    Spacer(Modifier.size(16.dp))
    AnswerStyleChoiceRow(
        choices = listOf(AnswerStyleChoice.CONCISE, AnswerStyleChoice.NORMAL, AnswerStyleChoice.DETAILED),
        selected = state.answerStyle,
        onSelect = actions.onAnswerStyleChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    Spacer(Modifier.size(12.dp))
    Group {
        CustomInstructionsRow(state.customInstructions, actions.onOpenCustomInstructions)
    }
    Spacer(Modifier.size(12.dp))
    Group {
        ZoneInMessagesRow(state.zoneInMessages, actions.onZoneInMessagesChange)
    }
    SectionLabel(stringResource(R.string.settings_section_personas))
    Group {
        for (persona in state.personas) {
            // A long name keeps one line and ends in "…" (D-029).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { actions.onOpenPersona(persona.id) }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    persona.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GroupDivider()
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { actions.onOpenPersona(null) }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.settings_add_persona),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CustomInstructionsRow(instructions: String, onClick: () -> Unit) {
    val firstLine = instructions.trim().lineSequence().firstOrNull().orEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_custom_instructions), style = MaterialTheme.typography.titleSmall)
            Text(
                firstLine.ifEmpty { stringResource(R.string.settings_custom_instructions_none) },
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
