package app.jonaki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.jonaki.JonakiApplication
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.feature.settings.CustomSubagentEditorScreen
import app.jonaki.feature.settings.CustomSubagentEditorUi
import app.jonaki.feature.settings.ModelOptionUi
import app.jonaki.settings.CustomSubagent
import app.jonaki.settings.CustomSubagents
import app.jonaki.settings.SettingsSnapshot

/**
 * The editor of one custom subagent (D-138); [subagentName] null, or a name
 * no longer saved, makes a new one. Save and Delete go back to Settings >
 * Subagents.
 */
@Composable
fun CustomSubagentEditorRoute(application: JonakiApplication, subagentName: String?, onBack: () -> Unit) {
    val settings = application.settings
    // Read once: after a rename is saved the old name is gone, and the screen must not turn into "New subagent".
    val snapshot = remember(subagentName) { settings.snapshot.value }
    val existing = remember(subagentName) {
        snapshot.customSubagents.firstOrNull { subagent -> subagent.name == subagentName }
    }
    CustomSubagentEditorScreen(
        initial = existing?.let { subagent -> editorUiOf(subagent, snapshot) },
        takenNames = CustomSubagents.BUILT_IN_NAMES + snapshot.customSubagents.map { subagent -> subagent.name },
        toolOptions = CustomSubagents.CHOOSABLE_TOOLS,
        modelOptions = subagentModelOptionsOf(snapshot, application),
        onSave = { edited ->
            val saved = CustomSubagent(edited.name, edited.description, edited.instructions, edited.tools, edited.modelKey)
            settings.update { current ->
                current.copy(customSubagents = CustomSubagents.saved(current.customSubagents, saved, existing?.name))
            }
            onBack()
        },
        onBack = onBack,
        onDelete = {
            settings.update { current ->
                current.copy(customSubagents = current.customSubagents.filterNot { subagent -> subagent.name == existing?.name })
            }
            onBack()
        },
    )
}

/** The user's models, offered to every subagent type. */
internal fun subagentModelOptionsOf(snapshot: SettingsSnapshot, application: JonakiApplication): List<ModelOptionUi> =
    snapshot.chatModels.allModelKeys.map { key ->
        ModelOptionUi(key, application.catalog.find(key)?.displayName ?: ModelKey.modelOf(key))
    }

/** A model the user has since removed shows, and saves, as the thread's model, which is what a run would use. */
private fun editorUiOf(subagent: CustomSubagent, snapshot: SettingsSnapshot) = CustomSubagentEditorUi(
    name = subagent.name,
    description = subagent.description,
    instructions = subagent.instructions,
    tools = subagent.tools,
    modelKey = subagent.modelKey?.takeIf { key -> key in snapshot.chatModels.allModelKeys },
)
