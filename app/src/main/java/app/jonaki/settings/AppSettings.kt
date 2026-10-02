package app.jonaki.settings

import android.content.Context
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

/** Chat services offered in M1 to M3; the other presets arrive in M10. */
enum class ChatService(val presetKey: String, val secret: SecretName) {
    OPENROUTER("openrouter", SecretName.OPENROUTER),
    DEEPSEEK("deepseek", SecretName.DEEPSEEK),
}

data class SettingsSnapshot(
    val chatService: ChatService,
    /** Empty means the preset's default model. */
    val modelByService: Map<ChatService, String>,
    val searchOrder: List<SearchService>,
    val webSearchOffInNewThreads: Boolean,
    val theme: ThemeChoice,
)

/** Plain settings in app preferences; keys live in [SecretStore]. */
class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())

    val snapshot: StateFlow<SettingsSnapshot> = state.asStateFlow()

    fun update(change: (SettingsSnapshot) -> SettingsSnapshot) {
        val updated = change(state.value)
        write(updated)
        state.value = updated
    }

    private fun read(): SettingsSnapshot {
        val savedOrder = preferences.getString(SEARCH_ORDER, null)
            ?.split(",")
            ?.mapNotNull { name -> SearchService.entries.firstOrNull { it.name == name } }
            .orEmpty()
        // Services added in a later version are appended so they still appear.
        val searchOrder = savedOrder + SearchService.entries.filter { it !in savedOrder }
        return SettingsSnapshot(
            chatService = enumOrDefault(preferences.getString(CHAT_SERVICE, null), ChatService.OPENROUTER),
            modelByService = ChatService.entries.associateWith { service ->
                preferences.getString(MODEL_PREFIX + service.name, "").orEmpty()
            },
            searchOrder = searchOrder,
            webSearchOffInNewThreads = preferences.getBoolean(WEB_SEARCH_OFF_IN_NEW_THREADS, false),
            theme = enumOrDefault(preferences.getString(THEME, null), ThemeChoice.SYSTEM),
        )
    }

    private fun write(snapshot: SettingsSnapshot) {
        val editor = preferences.edit()
        editor.putString(CHAT_SERVICE, snapshot.chatService.name)
        for ((service, model) in snapshot.modelByService) {
            editor.putString(MODEL_PREFIX + service.name, model.trim())
        }
        editor.putString(SEARCH_ORDER, snapshot.searchOrder.joinToString(",") { it.name })
        editor.putBoolean(WEB_SEARCH_OFF_IN_NEW_THREADS, snapshot.webSearchOffInNewThreads)
        editor.putString(THEME, snapshot.theme.name)
        editor.apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val CHAT_SERVICE = "chat_service"
        const val MODEL_PREFIX = "model_"
        const val SEARCH_ORDER = "search_order"
        const val WEB_SEARCH_OFF_IN_NEW_THREADS = "web_search_off_in_new_threads"
        const val THEME = "theme"
    }
}
