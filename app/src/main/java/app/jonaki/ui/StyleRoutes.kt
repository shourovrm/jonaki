package app.jonaki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.jonaki.JonakiApplication
import app.jonaki.core.agent.AnswerStyle
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.PersonaEntity
import app.jonaki.core.storage.ThreadEntity
import app.jonaki.core.ui.AnswerStyleChoice
import app.jonaki.feature.chat.PersonaChoiceUi
import app.jonaki.feature.chat.ThreadStyleSheet
import app.jonaki.feature.chat.ThreadStyleUi
import app.jonaki.feature.settings.InstructionsEditorScreen
import app.jonaki.feature.settings.PersonaRowUi
import app.jonaki.settings.AnswerStyles
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * A thread's answer style, persona and instructions (D-STY-1 to D-STY-3).
 * A new thread keeps them here until its first message creates the row.
 */
data class ThreadStyleDraft(
    val answerStyle: AnswerStyleChoice = AnswerStyleChoice.DEFAULT,
    val personaId: String? = null,
    val instructions: String = "",
)

val ThreadStyleDraftSaver: Saver<ThreadStyleDraft, Any> = listSaver(
    save = { draft -> listOf(draft.answerStyle.name, draft.personaId, draft.instructions) },
    restore = { saved ->
        ThreadStyleDraft(
            answerStyle = AnswerStyleChoice.valueOf(saved[0] as String),
            personaId = saved[1] as String?,
            instructions = saved[2] as String,
        )
    },
)

fun styleDraftOf(thread: ThreadEntity?): ThreadStyleDraft {
    if (thread == null) {
        return ThreadStyleDraft()
    }
    return ThreadStyleDraft(
        answerStyle = answerStyleChoiceOf(AnswerStyles.fromName(thread.answerStyle)),
        personaId = thread.personaId,
        instructions = thread.instructions,
    )
}

/** Writes all three; each changes the next run's system prompt once (D-005). */
suspend fun saveThreadStyle(database: JonakiDatabase, threadId: String, draft: ThreadStyleDraft) {
    val threadDao = database.threadDao()
    threadDao.setAnswerStyle(threadId, answerStyleOf(draft.answerStyle)?.name)
    threadDao.setPersona(threadId, draft.personaId)
    threadDao.setInstructions(threadId, draft.instructions)
}

/** DEFAULT is "no entry": the thread follows Settings. */
fun answerStyleOf(choice: AnswerStyleChoice): AnswerStyle? = when (choice) {
    AnswerStyleChoice.DEFAULT -> null
    AnswerStyleChoice.CONCISE -> AnswerStyle.CONCISE
    AnswerStyleChoice.NORMAL -> AnswerStyle.NORMAL
    AnswerStyleChoice.DETAILED -> AnswerStyle.DETAILED
}

fun answerStyleChoiceOf(style: AnswerStyle?): AnswerStyleChoice = when (style) {
    null -> AnswerStyleChoice.DEFAULT
    AnswerStyle.CONCISE -> AnswerStyleChoice.CONCISE
    AnswerStyle.NORMAL -> AnswerStyleChoice.NORMAL
    AnswerStyle.DETAILED -> AnswerStyleChoice.DETAILED
}

fun personaRowsOf(personas: List<PersonaEntity>): List<PersonaRowUi> =
    personas.map { persona -> PersonaRowUi(persona.id, persona.name) }

/** The chat's Style and persona sheet; [onChange] receives the whole draft after each change. */
@Composable
fun ThreadStyleSheetRoute(
    application: JonakiApplication,
    draft: ThreadStyleDraft,
    onChange: (ThreadStyleDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val personas by remember { application.database.personaDao().observeAll() }.collectAsState(initial = emptyList())
    ThreadStyleSheet(
        state = ThreadStyleUi(
            answerStyle = draft.answerStyle,
            // A persona deleted in Settings shows as none, as the prompt treats it.
            personaId = draft.personaId?.takeIf { id -> personas.any { persona -> persona.id == id } },
            personas = personas.map { persona -> PersonaChoiceUi(persona.id, persona.name) },
            instructions = draft.instructions,
        ),
        onAnswerStyleChange = { choice -> onChange(draft.copy(answerStyle = choice)) },
        onPersonaChange = { personaId -> onChange(draft.copy(personaId = personaId)) },
        onInstructionsSave = { text ->
            onChange(draft.copy(instructions = text))
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun CustomInstructionsRoute(application: JonakiApplication, onBack: () -> Unit) {
    val snapshot by application.settings.snapshot.collectAsState()
    InstructionsEditorScreen(
        title = stringResource(app.jonaki.feature.settings.R.string.settings_custom_instructions),
        initialName = null,
        initialInstructions = snapshot.customInstructions,
        onSave = { _, instructions ->
            application.settings.update { current -> current.copy(customInstructions = instructions) }
            onBack()
        },
        onBack = onBack,
    )
}

/** Edits the persona with [personaId], or a new one when it is null. */
@Composable
fun PersonaEditorRoute(application: JonakiApplication, personaId: String?, onBack: () -> Unit) {
    val personaDao = application.database.personaDao()
    val scope = rememberCoroutineScope()
    // The editor's fields start from the saved row, so it waits until the row is read.
    var loaded by remember(personaId) { mutableStateOf(personaId == null) }
    var existing by remember(personaId) { mutableStateOf<PersonaEntity?>(null) }
    LaunchedEffect(personaId) {
        if (personaId != null) {
            existing = personaDao.find(personaId)
            loaded = true
        }
    }
    if (!loaded) {
        return
    }
    val current = existing
    val titleId = if (current == null) {
        app.jonaki.feature.settings.R.string.settings_persona_new
    } else {
        app.jonaki.feature.settings.R.string.settings_persona_edit
    }
    InstructionsEditorScreen(
        title = stringResource(titleId),
        initialName = current?.name.orEmpty(),
        initialInstructions = current?.instructions.orEmpty(),
        onSave = { name, instructions ->
            val saved = PersonaEntity(
                id = current?.id ?: UUID.randomUUID().toString(),
                name = name.orEmpty(),
                instructions = instructions,
                createdAtMillis = current?.createdAtMillis ?: System.currentTimeMillis(),
            )
            scope.launch {
                personaDao.upsert(saved)
                onBack()
            }
        },
        onBack = onBack,
        onDelete = current?.let { persona ->
            {
                scope.launch {
                    personaDao.delete(persona.id)
                    onBack()
                }
            }
        },
    )
}
