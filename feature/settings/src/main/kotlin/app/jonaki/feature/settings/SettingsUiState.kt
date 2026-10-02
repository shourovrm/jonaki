package app.jonaki.feature.settings

import androidx.compose.runtime.Immutable
import app.jonaki.core.ui.ThemeMode

/**
 * Everything the settings screen shows. Saved keys are never part of the
 * state; only whether one is set ([KeySlot.isSet]).
 */
@Immutable
data class SettingsUiState(
    val providers: List<ProviderChoice>,
    val selectedProviderKey: String,
    val model: String,
    val geminiKey: KeySlot,
    /** In the order web_search tries them (D-011). */
    val searchServices: List<SearchServiceRow>,
    val webSearchOffInNewThreads: Boolean,
    val themeMode: ThemeMode,
    /** The chat's status strip (D-027); its switch lives on the Status icons page. */
    val showStatusStrip: Boolean = true,
)

@Immutable
data class ProviderChoice(
    /** Stable id, for example "openrouter". */
    val key: String,
    val displayName: String,
    val apiKey: KeySlot,
)

@Immutable
data class SearchServiceRow(
    val key: String,
    val displayName: String,
    val apiKey: KeySlot,
)

/** One stored secret. [id] is what the save and clear callbacks receive. */
@Immutable
data class KeySlot(
    val id: String,
    val isSet: Boolean,
)

/** Settings screen callbacks, grouped so the composable signature stays readable. */
class SettingsActions(
    val onBack: () -> Unit,
    val onProviderSelect: (providerKey: String) -> Unit,
    val onModelChange: (model: String) -> Unit,
    val onKeySave: (slotId: String, value: String) -> Unit,
    val onKeyClear: (slotId: String) -> Unit,
    /** [offset] is -1 to move up, +1 to move down. */
    val onSearchServiceMove: (serviceKey: String, offset: Int) -> Unit,
    val onWebSearchOffInNewThreadsChange: (Boolean) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    /** Opens the Status icons page; the app shows [StatusIconsScreen]. */
    val onOpenStatusIcons: () -> Unit = {},
)

object SettingsSample {
    val state: SettingsUiState = SettingsUiState(
        providers = listOf(
            ProviderChoice("openrouter", "OpenRouter", KeySlot("provider:openrouter", isSet = true)),
            ProviderChoice("deepseek", "DeepSeek", KeySlot("provider:deepseek", isSet = false)),
        ),
        selectedProviderKey = "openrouter",
        model = "z-ai/glm-5.3-flash",
        geminiKey = KeySlot("gemini", isSet = true),
        searchServices = listOf(
            SearchServiceRow("tavily", "Tavily", KeySlot("search:tavily", isSet = true)),
            SearchServiceRow("ollama", "Ollama", KeySlot("search:ollama", isSet = true)),
            SearchServiceRow("exa", "Exa", KeySlot("search:exa", isSet = false)),
        ),
        webSearchOffInNewThreads = false,
        themeMode = ThemeMode.SYSTEM,
    )
}
