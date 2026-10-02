package app.jonaki.settings

import app.jonaki.core.agent.AnswerStyle
import app.jonaki.core.providerapi.ThinkingLevel
import android.content.Context
import app.jonaki.providers.openaicompatible.OpenRouterRouting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeChoice {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Search services the user can order; each needs its own key. */
enum class SearchService(val secret: SecretName) {
    TAVILY(SecretName.TAVILY),
    OLLAMA(SecretName.OLLAMA),
    EXA(SecretName.EXA),
}

data class SettingsSnapshot(
    /** Chat services, their models and the starred default (D-028). */
    val chatModels: ChatModels,
    /** OpenRouter endpoint routing (D-030). */
    val routing: RoutingSettings,
    val searchOrder: List<SearchService>,
    val webSearchOffInNewThreads: Boolean,
    val theme: ThemeChoice,
    /** The chat's status strip (D-027). */
    val showStatusStrip: Boolean = true,
    /** Review mode (M4): facts found by background extraction wait for approval on the memory screen. */
    val reviewExtractedMemories: Boolean = false,
    /** Thinking level per model key; a model without an entry keeps its own default (D-057). */
    val thinkingLevels: Map<String, ThinkingLevel> = emptyMap(),
    /** The user's general instructions for every thread (D-STY-1). */
    val customInstructions: String = "",
    /** Answer style of every thread that has not picked its own (D-STY-2). */
    val answerStyle: AnswerStyle = AnswerStyle.NORMAL,
)

/** Plain settings in app preferences; keys live in [SecretStore]. */
class AppSettings(
    context: Context,
    /** The model a 0.1.0 install used when its model field was blank. */
    presetDefaultModel: (ChatService) -> String,
) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read(presetDefaultModel))

    val snapshot: StateFlow<SettingsSnapshot> = state.asStateFlow()

    fun update(change: (SettingsSnapshot) -> SettingsSnapshot) {
        val updated = change(state.value)
        write(updated)
        state.value = updated
    }

    /** Shorthand for the chat-model edits the settings screen makes. */
    fun updateChatModels(change: (ChatModels) -> ChatModels) {
        update { current -> current.copy(chatModels = change(current.chatModels)) }
    }

    private fun read(presetDefaultModel: (ChatService) -> String): SettingsSnapshot {
        val savedOrder = preferences.getString(SEARCH_ORDER, null)
            ?.split(",")
            ?.mapNotNull { name -> SearchService.entries.firstOrNull { it.name == name } }
            .orEmpty()
        // Services added in a later version are appended so they still appear.
        val searchOrder = savedOrder + SearchService.entries.filter { it !in savedOrder }
        return SettingsSnapshot(
            chatModels = readChatModels(presetDefaultModel),
            routing = RoutingSettings(
                openRouter = enumOrDefault(preferences.getString(OPENROUTER_ROUTING, null), OpenRouterRouting.PRIVATE_THEN_CHEAPEST),
                overrides = RoutingSettings.overridesFromText(preferences.getString(ROUTING_OVERRIDES, "").orEmpty()),
            ),
            searchOrder = searchOrder,
            webSearchOffInNewThreads = preferences.getBoolean(WEB_SEARCH_OFF_IN_NEW_THREADS, false),
            theme = enumOrDefault(preferences.getString(THEME, null), ThemeChoice.SYSTEM),
            showStatusStrip = preferences.getBoolean(SHOW_STATUS_STRIP, true),
            reviewExtractedMemories = preferences.getBoolean(REVIEW_EXTRACTED_MEMORIES, false),
            thinkingLevels = ThinkingLevels.fromText(preferences.getString(THINKING_LEVELS, "").orEmpty()),
            customInstructions = preferences.getString(CUSTOM_INSTRUCTIONS, "").orEmpty(),
            answerStyle = AnswerStyles.fromName(preferences.getString(ANSWER_STYLE, null)) ?: AnswerStyle.NORMAL,
        )
    }

    private fun readChatModels(presetDefaultModel: (ChatService) -> String): ChatModels {
        val addedNames = preferences.getString(ADDED_SERVICES, null)
            ?: return ChatModels.fromLegacy(
                chatServiceName = preferences.getString(LEGACY_CHAT_SERVICE, null),
                savedModels = ChatService.entries.associate { service ->
                    service.name to preferences.getString(LEGACY_MODEL_PREFIX + service.name, "").orEmpty()
                },
                presetDefaultModel = presetDefaultModel,
            )
        val addedServices = addedNames.split(",").mapNotNull { name -> ChatService.entries.firstOrNull { it.name == name } }
        val modelsByService = addedServices.associateWith { service ->
            // Model ids can hold commas or colons, so one id per line.
            preferences.getString(MODELS_PREFIX + service.name, "").orEmpty().lines().filter { it.isNotBlank() }
        }
        return ChatModels(
            addedServices = addedServices,
            modelsByService = modelsByService,
            defaultModelKey = preferences.getString(DEFAULT_MODEL_KEY, null),
        )
    }

    private fun write(snapshot: SettingsSnapshot) {
        val editor = preferences.edit()
        val chatModels = snapshot.chatModels
        editor.putString(ADDED_SERVICES, chatModels.addedServices.joinToString(",") { it.name })
        for (service in ChatService.entries) {
            val modelIds = chatModels.modelsByService[service]
            if (modelIds == null) {
                editor.remove(MODELS_PREFIX + service.name)
            } else {
                editor.putString(MODELS_PREFIX + service.name, modelIds.joinToString("\n"))
            }
        }
        editor.putString(DEFAULT_MODEL_KEY, chatModels.defaultModelKey)
        editor.putString(OPENROUTER_ROUTING, snapshot.routing.openRouter.name)
        editor.putString(ROUTING_OVERRIDES, RoutingSettings.overridesToText(snapshot.routing.overrides))
        editor.putString(SEARCH_ORDER, snapshot.searchOrder.joinToString(",") { it.name })
        editor.putBoolean(WEB_SEARCH_OFF_IN_NEW_THREADS, snapshot.webSearchOffInNewThreads)
        editor.putString(THEME, snapshot.theme.name)
        editor.putBoolean(SHOW_STATUS_STRIP, snapshot.showStatusStrip)
        editor.putBoolean(REVIEW_EXTRACTED_MEMORIES, snapshot.reviewExtractedMemories)
        editor.putString(THINKING_LEVELS, ThinkingLevels.toText(snapshot.thinkingLevels))
        editor.putString(CUSTOM_INSTRUCTIONS, snapshot.customInstructions)
        editor.putString(ANSWER_STYLE, snapshot.answerStyle.name)
        editor.apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val ADDED_SERVICES = "chat_services"
        const val MODELS_PREFIX = "chat_models_"
        const val DEFAULT_MODEL_KEY = "default_model_key"
        const val OPENROUTER_ROUTING = "openrouter_routing"
        const val ROUTING_OVERRIDES = "routing_overrides"

        // Settings of version 0.1.0, read once to migrate.
        const val LEGACY_CHAT_SERVICE = "chat_service"
        const val LEGACY_MODEL_PREFIX = "model_"

        const val SEARCH_ORDER = "search_order"
        const val WEB_SEARCH_OFF_IN_NEW_THREADS = "web_search_off_in_new_threads"
        const val THEME = "theme"
        const val SHOW_STATUS_STRIP = "show_status_strip"
        const val REVIEW_EXTRACTED_MEMORIES = "review_extracted_memories"
        const val THINKING_LEVELS = "thinking_levels"
        const val CUSTOM_INSTRUCTIONS = "custom_instructions"
        const val ANSWER_STYLE = "answer_style"
    }
}
