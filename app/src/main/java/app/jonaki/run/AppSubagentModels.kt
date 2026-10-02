package app.jonaki.run

import app.jonaki.core.agent.AgentType
import app.jonaki.core.agent.ImageMessages
import app.jonaki.core.agent.SubagentModel
import app.jonaki.core.agent.SubagentModels
import app.jonaki.core.modelcatalog.CostCalculator
import app.jonaki.core.modelcatalog.ModelCatalog
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.modelcatalog.ThinkingSupport
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.settings.ChatService
import app.jonaki.settings.SettingsSnapshot
import app.jonaki.settings.SubagentModelChoice
import app.jonaki.settings.ThinkingLevels

/** The models subagents run on, from Settings and the model catalog (D-065). */
class AppSubagentModels(
    private val snapshot: SettingsSnapshot,
    private val catalog: ModelCatalog,
    private val threadModelKey: String,
    /** The cheapest priced model (D-036), the scout's default. */
    private val backgroundModelKey: String?,
    /** Null when the model's service has no saved key. */
    private val providerFor: (modelKey: String) -> ChatProvider?,
    private val imageMessagesFor: (acceptsImages: Boolean) -> ImageMessages,
) : SubagentModels {
    override val scoped: List<SubagentModelInfo> = snapshot.chatModels.allModelKeys.map { key ->
        SubagentModelInfo(key, catalog.find(key)?.displayName ?: ModelKey.modelOf(key))
    }

    /** OpenRouter reports each call's cost, so its limit applies even to a model the catalog has no price for. */
    private fun hasKnownPrice(key: String, info: app.jonaki.core.modelcatalog.ModelInfo?): Boolean {
        val catalogHasPrice = info?.inputUsdPerMillion != null && info.outputUsdPerMillion != null
        return catalogHasPrice || ModelKey.serviceOf(key) == ChatService.OPENROUTER.key
    }

    override fun modelFor(agentType: AgentType, requestedKey: String?): SubagentModel? {
        val key = SubagentModelChoice.choose(
            agentType = agentType.name,
            requestedModelKey = requestedKey,
            savedChoices = snapshot.subagentModels,
            scopedModelKeys = snapshot.chatModels.allModelKeys,
            threadModelKey = threadModelKey,
            backgroundModelKey = backgroundModelKey,
        )
        val provider = providerFor(key) ?: return null
        val info = catalog.find(key)
        // Unknown models count as not taking images (D-049).
        val acceptsImages = info?.acceptsImages == true
        return SubagentModel(
            key = key,
            modelId = ModelKey.modelOf(key),
            provider = provider,
            // The model's own setting; the thread's choice belongs to the thread's model (D-057).
            thinkingLevel = ThinkingLevels.effective(
                threadLevel = null,
                modelLevel = snapshot.thinkingLevels[key],
                isSupported = ThinkingSupport.isSupported(key, info),
            ),
            acceptsImages = acceptsImages,
            hasKnownPrice = hasKnownPrice(key, info),
            priceOf = { usage -> CostCalculator.costUsd(usage, info) },
            imageMessages = imageMessagesFor(acceptsImages),
        )
    }
}
