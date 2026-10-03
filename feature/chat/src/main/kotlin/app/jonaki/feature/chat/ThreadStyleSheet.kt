package app.jonaki.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.AnswerStyleChoice
import app.jonaki.core.ui.AnswerStyleChoiceRow
import app.jonaki.core.ui.InstructionsField

/** What the Style and persona sheet shows for one thread (D-107 to D-109). */
@Immutable
data class ThreadStyleUi(
    /** DEFAULT follows the answer style in Settings. */
    val answerStyle: AnswerStyleChoice,
    /** Null for no persona. */
    val personaId: String?,
    val personas: List<PersonaChoiceUi>,
    val instructions: String,
)

@Immutable
data class PersonaChoiceUi(
    val id: String,
    val name: String,
)

/**
 * The thread's answer style, persona and own instructions, opened from the
 * chat's ⋮ menu. Style and persona apply on tap; the instructions apply on
 * Save, which also closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadStyleSheet(
    state: ThreadStyleUi,
    onAnswerStyleChange: (AnswerStyleChoice) -> Unit,
    onPersonaChange: (personaId: String?) -> Unit,
    onInstructionsSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var instructions by rememberSaveable { mutableStateOf(state.instructions) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            SheetTitle(stringResource(R.string.chat_style_title))
            PartLabel(stringResource(R.string.chat_style_answer))
            AnswerStyleChoiceRow(
                choices = AnswerStyleChoice.entries,
                selected = state.answerStyle,
                onSelect = onAnswerStyleChange,
                modifier = Modifier.fillMaxWidth(),
            )
            PartLabel(stringResource(R.string.chat_style_persona))
            PersonaRow(stringResource(R.string.chat_style_persona_none), isSelected = state.personaId == null) {
                onPersonaChange(null)
            }
            for (persona in state.personas) {
                PersonaRow(persona.name, isSelected = persona.id == state.personaId) { onPersonaChange(persona.id) }
            }
            if (state.personas.isEmpty()) {
                Text(
                    stringResource(R.string.chat_style_no_personas),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            InstructionsField(
                value = instructions,
                onValueChange = { text -> instructions = text },
                label = stringResource(R.string.chat_style_instructions),
                minHeight = 96.dp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onInstructionsSave(instructions.trim()) },
                    enabled = instructions.trim() != state.instructions.trim(),
                ) {
                    Text(stringResource(R.string.chat_style_save))
                }
            }
        }
    }
}

@Composable
private fun PartLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

/** A persona's name keeps one line and ends in "…" (D-029). */
@Composable
private fun PersonaRow(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(Modifier.width(14.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
