package app.jonaki.settings

import app.jonaki.core.agent.AgentTypes
import app.jonaki.core.agent.AnswerStyle
import app.jonaki.core.agent.ApprovalMode
import app.jonaki.core.agent.ZoneInMessages
import app.jonaki.core.agent.ApprovalRule
import app.jonaki.core.providerapi.ThinkingLevel
import app.jonaki.core.toolapi.SubagentBudget
import app.jonaki.core.toolapi.SubagentLimitSettings
import app.jonaki.guard.JevOptions
import android.content.Context
import app.jonaki.phone.ReminderPolicy
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
    /** The user's general instructions for every thread (D-107). */
    val customInstructions: String = "",
    /** Answer style of every thread that has not picked its own (D-108). */
    val answerStyle: AnswerStyle = AnswerStyle.NORMAL,
    /** When tools ask before a change, in every thread without its own mode (D-058). */
    val defaultApprovalMode: ApprovalMode = ApprovalMode.ASK,
    /** Model key per subagent type; a type without an entry uses its default (D-065). */
    val subagentModels: Map<String, String> = emptyMap(),
    /** Tool groups switched off in the picker or Settings > Tools; every other group is on (M8). */
    val disabledToolGroups: Set<ToolGroup> = emptySet(),
    /** The [ToolPicker] version the user last finished; 0 before the picker existed. */
    val toolPickerSeenVersion: Int = 0,
    /**
     * Runtime permissions the user refused in Android's dialog; closing it
     * with Back is not a refusal. Android cannot tell "never asked" from
     * "refused for good", so Settings > Permissions reads this record (D-124,
     * D-127).
     */
    val refusedPermissions: Set<String> = emptySet(),
    /** Tool names a model on the phone is offered (D-133). */
    val localModelTools: Set<String> = LocalModelToolList.DEFAULT,
    /** Settings > Subagents: how many start, and each one's budget (D-138). */
    val subagentLimits: SubagentLimitSettings = SubagentLimitSettings(),
    /** How much of the time zone each message's date line tells the model; nothing unless the user chooses more. */
    val zoneInMessages: ZoneInMessages = ZoneInMessages.NONE,
    /** The Jev guard's options (Settings > Guardrails); both jobs are off until switched on. */
    val jevOptions: JevOptions = JevOptions(skipsCards = false, screensOutsideContent = false, strictness = JevStrictness.BALANCED),
    /** Background extraction saves facts from finished chats (D-036); off leaves only the memory tool. */
    val saveFactsFromChats: Boolean = true,
    /** Extraction may propose a fact for every thread; such a fact always waits for approval. */
    val suggestFactsForAllThreads: Boolean = true,
    /** The agent may propose a skill after a hard task; nothing is added without approval. */
    val suggestSkills: Boolean = true,
    /** After a thread read outside content, a call that sends data out always asks (D-143, rule 1). */
    val askBeforeSendingOutAfterOutsideContent: Boolean = true,
    /** After a thread read outside content, new facts wait for approval on the Memory screen. */
    val holdFactsAfterOutsideContent: Boolean = true,
    /** Subagent types the user made, in the order they were added (D-138). */
    val customSubagents: List<CustomSubagent> = emptyList(),
    /** Settings > Files and schedule: how an unanswered reminder rings again. */
    val reminderPolicy: ReminderPolicy = ReminderPolicy(),
    /** "Always allow" rules of Settings > Approvals; each names one action of one tool and applies in every thread. */
    val approvalRules: List<ApprovalRule> = emptyList(),
    /** The video models generate_video may use, and the starred model. */
    val videoModels: VideoModels = VideoModels(),
    /** The image services and models generate_image may use, and the starred model. */
    val imageModels: ImageModels = ImageModels(),
) {
    val enabledToolGroups: Set<ToolGroup> get() = ToolGroups.enabled(disabledToolGroups)
}

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

    /** Adds [permissions] to the record of permissions the user refused (D-127). */
    fun recordPermissionRefusal(permissions: Collection<String>) {
        update { current -> current.copy(refusedPermissions = current.refusedPermissions + permissions) }
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
        val customSubagents = CustomSubagents.fromText(preferences.getString(CUSTOM_SUBAGENTS, "").orEmpty())
        return SettingsSnapshot(
            chatModels = readChatModels(presetDefaultModel),
            routing = RoutingSettings(
                openRouter = enumOrDefault(preferences.getString(OPENROUTER_ROUTING, null), OpenRouterRouting.PRIVATE_THEN_CHEAPEST),
                overrides = RoutingSettings.overridesFromText(preferences.getString(ROUTING_OVERRIDES, "").orEmpty()),
                pinned = RoutingSettings.pinnedFromText(preferences.getString(ROUTING_PINNED_PROVIDERS, "").orEmpty()),
            ),
            searchOrder = searchOrder,
            webSearchOffInNewThreads = preferences.getBoolean(WEB_SEARCH_OFF_IN_NEW_THREADS, false),
            theme = enumOrDefault(preferences.getString(THEME, null), ThemeChoice.SYSTEM),
            showStatusStrip = preferences.getBoolean(SHOW_STATUS_STRIP, true),
            reviewExtractedMemories = preferences.getBoolean(REVIEW_EXTRACTED_MEMORIES, false),
            thinkingLevels = ThinkingLevels.fromText(preferences.getString(THINKING_LEVELS, "").orEmpty()),
            customInstructions = preferences.getString(CUSTOM_INSTRUCTIONS, "").orEmpty(),
            answerStyle = AnswerStyles.fromName(preferences.getString(ANSWER_STYLE, null)) ?: AnswerStyle.NORMAL,
            defaultApprovalMode = ApprovalModes.fromName(preferences.getString(APPROVAL_MODE, null)),
            subagentModels = SubagentModelChoice.fromText(preferences.getString(SUBAGENT_MODELS, "").orEmpty()),
            disabledToolGroups = ToolGroups.disabledFromText(preferences.getString(DISABLED_TOOL_GROUPS, "").orEmpty()),
            toolPickerSeenVersion = preferences.getInt(TOOL_PICKER_SEEN_VERSION, 0),
            refusedPermissions = preferences.getStringSet(REFUSED_PERMISSIONS, emptySet()).orEmpty().toSet(),
            // Missing means never chosen, so the defaults; a saved empty set stays empty.
            localModelTools = preferences.getStringSet(LOCAL_MODEL_TOOLS, null)?.toSet() ?: LocalModelToolList.DEFAULT,
            subagentLimits = readSubagentLimits(customSubagents),
            customSubagents = customSubagents,
            reminderPolicy = ReminderPolicy(
                intervalMinutes = preferences.getInt(REMINDER_INTERVAL_MINUTES, ReminderPolicy.DEFAULT_INTERVAL_MINUTES),
                maxRepeats = preferences.getInt(REMINDER_MAX_REPEATS, ReminderPolicy.DEFAULT_MAX_REPEATS),
            ).withinBounds(),
            jevOptions = JevOptions(
                // Before the two switches there was one; its value is the default of both.
                skipsCards = preferences.getBoolean(JEV_SKIPS_CARDS, preferences.getBoolean(JEV_GUARD_ON, false)),
                screensOutsideContent = preferences.getBoolean(JEV_SCREENS_OUTSIDE_CONTENT, preferences.getBoolean(JEV_GUARD_ON, false)),
                strictness = enumOrDefault(preferences.getString(JEV_STRICTNESS, null), JevStrictness.BALANCED),
            ),
            saveFactsFromChats = preferences.getBoolean(SAVE_FACTS_FROM_CHATS, true),
            suggestFactsForAllThreads = preferences.getBoolean(SUGGEST_FACTS_FOR_ALL_THREADS, true),
            suggestSkills = preferences.getBoolean(SUGGEST_SKILLS, true),
            askBeforeSendingOutAfterOutsideContent = preferences.getBoolean(ASK_BEFORE_SENDING_OUT, true),
            holdFactsAfterOutsideContent = preferences.getBoolean(HOLD_FACTS_AFTER_OUTSIDE_CONTENT, true),
            zoneInMessages = enumOrDefault(preferences.getString(ZONE_IN_MESSAGES, null), ZoneInMessages.NONE),
            approvalRules = ApprovalRules.fromText(preferences.getString(APPROVAL_RULES, "").orEmpty()),
            videoModels = VideoModels.fromStored(
                modelKeysText = preferences.getString(VIDEO_MODEL_KEYS, null),
                defaultModelKey = preferences.getString(VIDEO_DEFAULT_MODEL_KEY, null),
            ),
            imageModels = ImageModels.fromStored(
                servicesText = preferences.getString(IMAGE_SERVICES, null),
                modelKeysText = preferences.getString(IMAGE_MODEL_KEYS, null),
                defaultModelKey = preferences.getString(IMAGE_DEFAULT_MODEL_KEY, null),
                // The first version's values; they are read only until the new ones are first saved.
                legacyModelsText = preferences.getString(IMAGE_MODELS, null),
                legacyDefaultModel = preferences.getString(IMAGE_DEFAULT_MODEL, null),
                // Absent before vector models existed; then no model is a stored vector model.
                vectorModelKeysText = preferences.getString(IMAGE_VECTOR_MODEL_KEYS, null),
            ),
        )
    }

    /**
     * A missing value keeps its default; a saved one is moved into its range in case the ranges changed.
     * Two things come from older versions: the cap per message was "warn above", and every type shared
     * one budget of steps, cost and minutes.
     */
    private fun readSubagentLimits(customSubagents: List<CustomSubagent>): SubagentLimitSettings {
        val defaults = SubagentLimitSettings()
        val oldWarnAbove = preferences.getInt(SUBAGENTS_WARN_ABOVE, defaults.maxPerMessage)
        return SubagentLimitSettings(
            startedWithoutAsking = preferences.getInt(SUBAGENTS_WITHOUT_ASKING, defaults.startedWithoutAsking),
            perCall = preferences.getInt(SUBAGENTS_PER_CALL, defaults.perCall),
            maxPerMessage = preferences.getInt(SUBAGENTS_MAX_PER_MESSAGE, oldWarnAbove),
            budgets = readSubagentBudgets(customSubagents),
        ).withinBounds()
    }

    private fun readSubagentBudgets(customSubagents: List<CustomSubagent>): Map<String, SubagentBudget> {
        val saved = preferences.getString(SUBAGENT_BUDGETS, null)
        if (saved != null) {
            return SubagentBudgets.fromText(saved)
        }
        val typeNames = AgentTypes.ALL.map { type -> type.name } + customSubagents.map { subagent -> subagent.name }
        return SubagentBudgets.migrated(readLegacySubagentBudget(), typeNames)
    }

    /** The one budget of versions before per-type budgets; null when the user never saved any of its three values. */
    private fun readLegacySubagentBudget(): SubagentBudget? {
        val legacyKeys = listOf(SUBAGENT_TOOL_STEPS, SUBAGENT_COST_CENTS, SUBAGENT_MINUTES)
        if (legacyKeys.none { key -> preferences.contains(key) }) {
            return null
        }
        val defaults = SubagentLimitSettings.DEFAULT_BUDGET
        return SubagentBudget(
            toolSteps = preferences.getInt(SUBAGENT_TOOL_STEPS, defaults.toolSteps),
            costCapCents = preferences.getInt(SUBAGENT_COST_CENTS, defaults.costCapCents),
            minutes = preferences.getInt(SUBAGENT_MINUTES, defaults.minutes),
        )
    }

    private fun readLegacyChatModels(presetDefaultModel: (ChatService) -> String): ChatModels {
        val chatServiceName = preferences.getString(LEGACY_CHAT_SERVICE, null)
        val savedModels = ChatService.entries.associate { service ->
            service.name to preferences.getString(LEGACY_MODEL_PREFIX + service.name, "").orEmpty()
        }
        if (!ChatModels.hasLegacySettings(chatServiceName, savedModels)) {
            // A fresh install: the user adds the first service and model themselves.
            return ChatModels(addedServices = emptyList(), modelsByService = emptyMap(), defaultModelKey = null)
        }
        return ChatModels.fromLegacy(chatServiceName, savedModels, presetDefaultModel)
    }

    private fun readChatModels(presetDefaultModel: (ChatService) -> String): ChatModels {
        val addedNames = preferences.getString(ADDED_SERVICES, null)
            ?: return readLegacyChatModels(presetDefaultModel)
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
        editor.putString(ROUTING_PINNED_PROVIDERS, RoutingSettings.pinnedToText(snapshot.routing.pinned))
        editor.putString(SEARCH_ORDER, snapshot.searchOrder.joinToString(",") { it.name })
        editor.putBoolean(WEB_SEARCH_OFF_IN_NEW_THREADS, snapshot.webSearchOffInNewThreads)
        editor.putString(THEME, snapshot.theme.name)
        editor.putBoolean(SHOW_STATUS_STRIP, snapshot.showStatusStrip)
        editor.putBoolean(REVIEW_EXTRACTED_MEMORIES, snapshot.reviewExtractedMemories)
        editor.putString(THINKING_LEVELS, ThinkingLevels.toText(snapshot.thinkingLevels))
        editor.putString(CUSTOM_INSTRUCTIONS, snapshot.customInstructions)
        editor.putString(ANSWER_STYLE, snapshot.answerStyle.name)
        editor.putString(APPROVAL_MODE, snapshot.defaultApprovalMode.name)
        editor.putString(SUBAGENT_MODELS, SubagentModelChoice.toText(snapshot.subagentModels))
        editor.putString(DISABLED_TOOL_GROUPS, ToolGroups.disabledToText(snapshot.disabledToolGroups))
        editor.putInt(TOOL_PICKER_SEEN_VERSION, snapshot.toolPickerSeenVersion)
        editor.putStringSet(REFUSED_PERMISSIONS, snapshot.refusedPermissions)
        editor.putStringSet(LOCAL_MODEL_TOOLS, snapshot.localModelTools)
        val limits = snapshot.subagentLimits
        editor.putInt(SUBAGENTS_WITHOUT_ASKING, limits.startedWithoutAsking)
        editor.putInt(SUBAGENTS_PER_CALL, limits.perCall)
        editor.putInt(SUBAGENTS_MAX_PER_MESSAGE, limits.maxPerMessage)
        editor.putString(SUBAGENT_BUDGETS, SubagentBudgets.toText(limits.budgets))
        editor.putBoolean(JEV_SKIPS_CARDS, snapshot.jevOptions.skipsCards)
        editor.putBoolean(JEV_SCREENS_OUTSIDE_CONTENT, snapshot.jevOptions.screensOutsideContent)
        editor.putString(JEV_STRICTNESS, snapshot.jevOptions.strictness.name)
        editor.putBoolean(SAVE_FACTS_FROM_CHATS, snapshot.saveFactsFromChats)
        editor.putBoolean(SUGGEST_FACTS_FOR_ALL_THREADS, snapshot.suggestFactsForAllThreads)
        editor.putBoolean(SUGGEST_SKILLS, snapshot.suggestSkills)
        editor.putBoolean(ASK_BEFORE_SENDING_OUT, snapshot.askBeforeSendingOutAfterOutsideContent)
        editor.putBoolean(HOLD_FACTS_AFTER_OUTSIDE_CONTENT, snapshot.holdFactsAfterOutsideContent)
        editor.putString(ZONE_IN_MESSAGES, snapshot.zoneInMessages.name)
        editor.putString(CUSTOM_SUBAGENTS, CustomSubagents.toText(snapshot.customSubagents))
        editor.putInt(REMINDER_INTERVAL_MINUTES, snapshot.reminderPolicy.intervalMinutes)
        editor.putInt(REMINDER_MAX_REPEATS, snapshot.reminderPolicy.maxRepeats)
        editor.putString(APPROVAL_RULES, ApprovalRules.toText(snapshot.approvalRules))
        val storedVideoModels = VideoModels.toStored(snapshot.videoModels)
        editor.putString(VIDEO_MODEL_KEYS, storedVideoModels.modelKeysText)
        editor.putString(VIDEO_DEFAULT_MODEL_KEY, storedVideoModels.defaultModelKey)
        val storedImageModels = ImageModels.toStored(snapshot.imageModels)
        editor.putString(IMAGE_SERVICES, storedImageModels.servicesText)
        editor.putString(IMAGE_MODEL_KEYS, storedImageModels.modelKeysText)
        editor.putString(IMAGE_DEFAULT_MODEL_KEY, storedImageModels.defaultModelKey)
        editor.putString(IMAGE_VECTOR_MODEL_KEYS, storedImageModels.vectorModelKeysText)
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
        const val ROUTING_PINNED_PROVIDERS = "routing_pinned_providers"

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
        const val APPROVAL_MODE = "approval_mode"
        const val SUBAGENT_MODELS = "subagent_models"
        const val DISABLED_TOOL_GROUPS = "disabled_tool_groups"
        const val TOOL_PICKER_SEEN_VERSION = "tool_picker_seen_version"
        // A new key: the old "requested_permissions" also counted dialogs closed with Back (D-127).
        const val REFUSED_PERMISSIONS = "refused_permissions"
        const val LOCAL_MODEL_TOOLS = "local_model_tools"
        const val SUBAGENTS_WITHOUT_ASKING = "subagents_without_asking"
        const val SUBAGENTS_PER_CALL = "subagents_per_call"
        const val SUBAGENTS_MAX_PER_MESSAGE = "subagents_max_per_message"

        // Read only, to carry the values of versions before the cap and the per-type budgets over.
        const val SUBAGENTS_WARN_ABOVE = "subagents_warn_above"
        const val SUBAGENT_BUDGETS = "subagent_budgets"
        const val SUBAGENT_TOOL_STEPS = "subagent_tool_steps"
        const val SUBAGENT_COST_CENTS = "subagent_cost_cents"
        const val SUBAGENT_MINUTES = "subagent_minutes"
        const val CUSTOM_SUBAGENTS = "custom_subagents"
        const val REMINDER_INTERVAL_MINUTES = "reminder_interval_minutes"
        const val REMINDER_MAX_REPEATS = "reminder_max_repeats"
        /** The single switch of version 1.2.0; read once as the default of the two that replaced it. */
        const val JEV_GUARD_ON = "jev_guard_on"
        const val JEV_SKIPS_CARDS = "jev_skips_cards"
        const val JEV_SCREENS_OUTSIDE_CONTENT = "jev_screens_outside_content"
        const val JEV_STRICTNESS = "jev_strictness"
        const val SAVE_FACTS_FROM_CHATS = "save_facts_from_chats"
        const val SUGGEST_FACTS_FOR_ALL_THREADS = "suggest_facts_for_all_threads"
        const val SUGGEST_SKILLS = "suggest_skills"
        const val ASK_BEFORE_SENDING_OUT = "ask_before_sending_out_after_outside_content"
        const val HOLD_FACTS_AFTER_OUTSIDE_CONTENT = "hold_facts_after_outside_content"
        const val ZONE_IN_MESSAGES = "zone_in_messages"
        const val APPROVAL_RULES = "approval_rules"
        const val VIDEO_MODEL_KEYS = "video_model_keys"
        const val VIDEO_DEFAULT_MODEL_KEY = "video_default_model_key"
        // The first version of image generation saved only IMAGE_MODELS and IMAGE_DEFAULT_MODEL (OpenRouter ids); they are left in place and read once.
        const val IMAGE_MODELS = "image_models"
        const val IMAGE_DEFAULT_MODEL = "image_default_model"
        const val IMAGE_SERVICES = "image_services"
        const val IMAGE_MODEL_KEYS = "image_model_keys"
        const val IMAGE_DEFAULT_MODEL_KEY = "image_default_model_key"
        const val IMAGE_VECTOR_MODEL_KEYS = "image_vector_model_keys"
    }
}
