package app.jonaki.feature.settings

import app.jonaki.core.ui.AnswerStyleChoice
import app.jonaki.core.ui.ApprovalModeChoice
import app.jonaki.core.ui.ThinkingChoice

import androidx.compose.runtime.Immutable
import app.jonaki.core.ui.ThemeMode

/**
 * Everything the settings screen shows. Saved keys are never part of the
 * state: only whether one is set and its masked first characters ([KeySlot]).
 */
@Immutable
data class SettingsUiState(
    /** Chat services the user added, each shown as a card (D-028). */
    val chatServices: List<ChatServiceCardUi> = emptyList(),
    /** Services offered by "Add service": the ones not added yet. */
    val addableServices: List<AddableServiceUi> = emptyList(),
    val geminiKey: KeySlot,
    /** In the order web_search tries them (D-011). */
    val searchServices: List<SearchServiceRow>,
    val webSearchOffInNewThreads: Boolean,
    val themeMode: ThemeMode,
    /** The chat's status strip (D-027); its switch lives on the Status icons page. */
    val showStatusStrip: Boolean = true,
    /** The folder linked for share_file and imports (D-043); null while none is linked. */
    val linkedFolderName: String? = null,
    /** Reminders and scheduled tasks, soonest first (plan M9). */
    val scheduledItems: List<ScheduledItemUi> = emptyList(),
    /** Remote MCP servers the mcp tool reaches (D-104). */
    val mcpServers: List<McpServerUi> = emptyList(),
    /** Answer style of every thread that has not picked its own (D-108); never DEFAULT here. */
    val answerStyle: AnswerStyleChoice = AnswerStyleChoice.NORMAL,
    /** The general custom instructions (D-107); the row shows their first line. */
    val customInstructions: String = "",
    /** Saved personas, sorted by name (D-109). */
    val personas: List<PersonaRowUi> = emptyList(),
    /** When tools ask first, in every thread without its own mode (D-058). */
    val approvalMode: ApprovalModeChoice = ApprovalModeChoice.ASK,
    /** The model each subagent type runs on (D-065). */
    val subagentModels: List<SubagentModelRowUi> = emptyList(),
    /** The user's scoped models, offered for each type. */
    val subagentModelOptions: List<ModelOptionUi> = emptyList(),
    /** Settings > Subagents' limits, in the order the page shows them (D-138). */
    val subagentLimits: List<SubagentLimitUi> = emptyList(),
    /** Subagent types the user made, in the order they were added (D-138). */
    val customSubagents: List<CustomSubagentRowUi> = emptyList(),
    /** How an unanswered reminder rings again, on Settings > Files and schedule. */
    val reminders: ReminderSettingsUi = ReminderSettingsUi(),
    /** Settings > Permissions, read again each time the screen resumes (D-124). */
    val permissions: List<PermissionRowUi> = emptyList(),
    /** The installed version name, for example "0.8.0". */
    val appVersion: String = "",
    /** Tool groups switched on, and all of them, for the first page's "12 of 12 tools on" (D-128). */
    val toolGroupsOn: Int = 0,
    val toolGroupCount: Int = 0,
    /** Global saved facts, for the first page's summary. */
    val factCount: Int = 0,
    /** Skills in the library, for the first page's summary. */
    val skillCount: Int = 0,
    /** Settings > Local models' row on the first page (D-133). */
    val localModels: LocalModelsSummaryUi = LocalModelsSummaryUi(),
    // The three fields below belong to the single-provider block that the cards
    // replace. They stay only until the app moves to [chatServices]; the screen
    // no longer reads them.
    val providers: List<ProviderChoice> = emptyList(),
    val selectedProviderKey: String = "",
    val model: String = "",
)

/** Kept for the app's current wiring; see [SettingsUiState.providers]. */
@Immutable
data class ProviderChoice(
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

/**
 * One stored secret. [id] is what the save and clear callbacks receive.
 * [maskedKey] is the first three characters and dots ("sk-o••••"), made by
 * the app; the full key never reaches the UI (D-028).
 */
@Immutable
data class KeySlot(
    val id: String,
    val isSet: Boolean,
    val maskedKey: String? = null,
    /** Credit left or used, already formatted ("$12.87 left"); null shows nothing (D-031). */
    val balance: String? = null,
)

/** How OpenRouter picks the provider that serves a model (D-030). */
enum class RoutingUi {
    PRIVATE_THEN_CHEAPEST,
    CHEAPEST,
    AUTOMATIC,
}

/** One added chat service, drawn as a card that opens (D-028). */
@Immutable
data class ChatServiceCardUi(
    /** Stable id, for example "openrouter". */
    val serviceKey: String,
    val displayName: String,
    /** Null for a service that needs no key, such as Ollama on this network. */
    val apiKey: KeySlot?,
    /** OpenRouter only: the routing every model uses unless it overrides it. */
    val routing: RoutingUi? = null,
    val models: List<ServiceModelUi> = emptyList(),
    /** Money left and this month's spend for the closed card (D-032); null shows no line. */
    val account: AccountLineUi? = null,
)

/** "$12.87 left · $0.08 this month", already formatted by the app. */
@Immutable
data class AccountLineUi(
    val text: String,
    /** Under one dollar left: drawn in the error colour and read out as low. */
    val isLow: Boolean,
)

@Immutable
data class ServiceModelUi(
    /** "service:modelId", the key the callbacks receive. */
    val key: String,
    val name: String,
    val contextWindowTokens: Int? = null,
    val inputPricePerMillion: Double? = null,
    val outputPricePerMillion: Double? = null,
    val cachedInputPricePerMillion: Double? = null,
    /** The one starred model, default for new threads across all services. */
    val isDefault: Boolean = false,
    /** Null means the same as the card's routing. */
    val routingOverride: RoutingUi? = null,
    /** Null when the model takes no thinking level (D-057). */
    val thinking: ThinkingChoice? = null,
)

/** A service "Add service" can add. */
@Immutable
data class AddableServiceUi(
    val key: String,
    val displayName: String,
    /** A short second line, for example "Google" or "No key". */
    val hint: String,
)

/** Settings screen callbacks, grouped so the composable signature stays readable. */
class SettingsActions(
    val onBack: () -> Unit,
    val onKeySave: (slotId: String, value: String) -> Unit,
    val onKeyClear: (slotId: String) -> Unit,
    /** [offset] is -1 to move up, +1 to move down. */
    val onSearchServiceMove: (serviceKey: String, offset: Int) -> Unit,
    val onWebSearchOffInNewThreadsChange: (Boolean) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    /** Opens one Settings sub-page from the first page's rows or its search (D-128). */
    val onOpenPage: (SettingsPage) -> Unit = {},
    /** Opens the Status icons page; the app shows [StatusIconsScreen]. */
    val onOpenStatusIcons: () -> Unit = {},
    /** Opens the memory screen with the global facts (M4). */
    val onOpenMemory: () -> Unit = {},
    /** Opens the skill library (M5). */
    val onOpenSkills: () -> Unit = {},
    /** Opens Settings > Tools, the tool group switches (plan M8 step 3). */
    val onOpenTools: () -> Unit = {},
    /** Opens Settings > Python (plan M8 step 4). */
    val onOpenPython: () -> Unit = {},
    /** Opens Android's folder picker; the app keeps the picked folder. */
    val onLinkFolder: () -> Unit = {},
    val onUnlinkFolder: () -> Unit = {},
    /** Cancels a reminder or a scheduled task by its [ScheduledItemUi.id]. */
    val onCancelScheduled: (id: String) -> Unit = {},
    val onAddService: (serviceKey: String) -> Unit = {},
    /** Removes the service, its key and its models. */
    val onRemoveService: (serviceKey: String) -> Unit = {},
    val onServiceRoutingChange: (serviceKey: String, routing: RoutingUi) -> Unit = { _, _ -> },
    /** Opens [AddModelsScreen] for the service. */
    val onAddModels: (serviceKey: String) -> Unit = {},
    val onModelSetDefault: (modelKey: String) -> Unit = {},
    /** [routing] null means "same as the service". */
    val onModelRoutingChange: (modelKey: String, routing: RoutingUi?) -> Unit = { _, _ -> },
    val onModelRemove: (modelKey: String) -> Unit = {},
    /** A thinking level picked in a model's menu (D-057). */
    val onModelThinkingChange: (modelKey: String, choice: ThinkingChoice) -> Unit = { _, _ -> },
    /** Adds or edits an MCP server; the dialog has already checked the input. */
    val onMcpServerSave: (McpServerInput) -> Unit = {},
    val onMcpServerRemove: (id: String) -> Unit = {},
    val onAnswerStyleChange: (AnswerStyleChoice) -> Unit = {},
    /** Opens the editor for the general custom instructions. */
    val onOpenCustomInstructions: () -> Unit = {},
    /** Opens a persona's editor; null opens an empty one for a new persona. */
    val onOpenPersona: (personaId: String?) -> Unit = {},
    val onApprovalModeChange: (ApprovalModeChoice) -> Unit = {},
    /** [modelKey] null returns the type to its default. */
    val onSubagentModelChange: (agentType: String, modelKey: String?) -> Unit = { _, _ -> },
    /** A limit's new value, one step from the old; the app keeps it in range (D-138). */
    val onSubagentLimitChange: (limit: SubagentLimit, value: Int) -> Unit = { _, _ -> },
    /** The new minutes between repeats, one of [ReminderSettingsUi.intervalOptions]. */
    val onReminderIntervalChange: (minutes: Int) -> Unit = {},
    val onReminderMaxRepeatsChange: (repeats: Int) -> Unit = {},
    /** Opens a custom subagent's editor; null opens an empty one for a new subagent. */
    val onOpenCustomSubagent: (name: String?) -> Unit = {},
    /** A tap on a permission row or its button; the app asks or opens system settings by [PermissionRowUi.status]. */
    val onPermissionTap: (PermissionRow) -> Unit = {},
    /** Opens the project's GitHub page in the browser. */
    val onOpenGitHub: () -> Unit = {},
    // Kept for the app's current wiring; the screen no longer calls them.
    val onProviderSelect: (providerKey: String) -> Unit = {},
    val onModelChange: (model: String) -> Unit = {},
)

/** The routing to show under a model in the list: only when it differs from its card's. */
fun routingLabelFor(model: ServiceModelUi, cardRouting: RoutingUi?): RoutingUi? {
    val override = model.routingOverride ?: return null
    if (override == cardRouting) {
        return null
    }
    return override
}

object SettingsSample {
    val state: SettingsUiState = SettingsUiState(
        chatServices = listOf(
            ChatServiceCardUi(
                serviceKey = "openrouter",
                displayName = "OpenRouter",
                apiKey = KeySlot("OPENROUTER", isSet = true, maskedKey = "sk-o••••", balance = "$12.87 left"),
                account = AccountLineUi("$12.87 left · $0.08 this month", isLow = false),
                routing = RoutingUi.PRIVATE_THEN_CHEAPEST,
                models = listOf(
                    ServiceModelUi("openrouter:z-ai/glm-5.3-flash", "GLM 5.3 Flash", 200_000, 0.10, 0.40, 0.03, isDefault = true),
                    ServiceModelUi(
                        "openrouter:qwen/qwen-4-coder-480b-a35b-instruct-turbo",
                        "Qwen 4 Coder 480B A35B Instruct Turbo",
                        256_000,
                        0.40,
                        1.60,
                        null,
                    ),
                    ServiceModelUi(
                        "openrouter:anthropic/claude-sonnet-5.5",
                        "Claude Sonnet 5.5",
                        1_000_000,
                        3.00,
                        15.00,
                        0.30,
                        routingOverride = RoutingUi.AUTOMATIC,
                    ),
                ),
            ),
            ChatServiceCardUi(
                serviceKey = "deepseek",
                displayName = "DeepSeek",
                apiKey = KeySlot("DEEPSEEK", isSet = true, maskedKey = "sk-1••••"),
                models = listOf(ServiceModelUi("deepseek:deepseek-v4", "DeepSeek V4", 128_000, 0.27, 1.10, 0.07)),
            ),
        ),
        addableServices = listOf(
            AddableServiceUi("gemini", "Gemini", "Google"),
            AddableServiceUi("ollama-local", "Ollama on this network", "No key"),
        ),
        geminiKey = KeySlot("GEMINI", isSet = true, maskedKey = "AIz••••"),
        searchServices = listOf(
            SearchServiceRow("TAVILY", "Tavily", KeySlot("TAVILY", isSet = true, maskedKey = "tvl••••", balance = "3 / 1,000 credits this month")),
            SearchServiceRow("OLLAMA", "Ollama", KeySlot("OLLAMA", isSet = true, maskedKey = "6c4••••")),
            SearchServiceRow("EXA", "Exa", KeySlot("EXA", isSet = false)),
        ),
        webSearchOffInNewThreads = false,
        themeMode = ThemeMode.SYSTEM,
        // A 40-character folder name checks that the summary keeps one line (D-029).
        linkedFolderName = "Thesis drafts and supervisor notes 2026",
        scheduledItems = listOf(ScheduledItemUi("reminder:1", "Call Abba", "Daily · Sun 4 Oct, 08:00")),
        personas = listOf(PersonaRowUi("1", "Thesis supervisor who asks for sources")),
        subagentModels = listOf(
            SubagentModelRowUi("researcher", selectedKey = null, defaultIsCheapest = false),
            SubagentModelRowUi("scout", selectedKey = null, defaultIsCheapest = true),
        ),
        subagentLimits = SubagentLimitUi.SAMPLE,
        // A 30-character name and a long description check that each keeps one line (D-029).
        customSubagents = listOf(
            CustomSubagentRowUi("price-checker", "Checks laptop prices in Dhaka shops and lists them by price."),
            CustomSubagentRowUi("bangla-legal-summary-writer-02", "Summarises court papers in plain Bangla."),
        ),
        permissions = listOf(
            PermissionRowUi(PermissionRow.NOTIFICATIONS, PermissionStatus.ALLOWED),
            PermissionRowUi(PermissionRow.CALENDAR, PermissionStatus.BLOCKED),
            PermissionRowUi(PermissionRow.PHOTOS, PermissionStatus.SELECTED_PHOTOS),
            PermissionRowUi(PermissionRow.ALARMS, PermissionStatus.ALLOWED),
        ),
        appVersion = "1.0.0",
        toolGroupsOn = 12,
        toolGroupCount = 12,
        factCount = 3,
        skillCount = 5,
        localModels = LocalModelsSummaryUi(downloadedCount = 2, largestFittingName = "Qwen3.5-2B"),
    )
}
