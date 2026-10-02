package app.jonaki.run

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.modelcatalog.CostCalculator
import app.jonaki.core.modelcatalog.ModelCatalog
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.MessageEntity
import app.jonaki.settings.AppSettings
import app.jonaki.settings.ChatService
import app.jonaki.settings.SecretStore
import java.util.UUID
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

sealed interface BackgroundAnswer {
    data class Success(val text: String, val modelKey: String) : BackgroundAnswer

    data class Failed(val message: String) : BackgroundAnswer
}

/**
 * One-off calls that run beside the chat, such as memory extraction and
 * compaction, on the cheapest model the user has set up (D-009, D-036).
 * The call's usage is saved as a hidden row in the thread, so the thread's
 * and the month's cost include it.
 */
class BackgroundModel(
    private val database: JonakiDatabase,
    private val settings: AppSettings,
    private val secrets: SecretStore,
    private val httpClient: OkHttpClient,
    private val catalog: ModelCatalog,
) {
    /** The model [complete] would use for a thread whose own model is [threadModelKey]. */
    fun modelFor(threadModelKey: String?): String? = choose(
        scopedModelKeys = settings.snapshot.value.chatModels.allModelKeys,
        hasKey = ::hasKey,
        pricePerMillion = ::pricePerMillion,
        threadModelKey = threadModelKey,
    )

    /**
     * Sends [userText] with [systemPrompt] to the background model and
     * returns the whole answer. No tools; the answer is not streamed to the screen.
     */
    suspend fun complete(
        threadId: String,
        threadModelKey: String?,
        systemPrompt: String,
        userText: String,
        maxOutputTokens: Int,
    ): BackgroundAnswer {
        val modelKey = modelFor(threadModelKey) ?: return BackgroundAnswer.Failed("no chat model is set up")
        val service = ChatService.byKey(ModelKey.serviceOf(modelKey))
            ?: return BackgroundAnswer.Failed("unknown service in $modelKey")
        val secret = service.secret
        val apiKey = if (secret == null) null else secrets.read(secret) ?: return BackgroundAnswer.Failed("no key for ${service.displayName}")
        val routing = settings.snapshot.value.routing.effectiveFor(modelKey)
        val provider = ChatProviders.create(service, apiKey, httpClient, routing, onRoutingFallback = {})
        val request = ChatRequest(
            model = ModelKey.modelOf(modelKey),
            systemPrompt = systemPrompt,
            messages = listOf(Message(Role.USER, userText)),
            maxOutputTokens = maxOutputTokens,
        )
        val answer = StringBuilder()
        var usage: Usage? = null
        var failure: String? = null
        val finished = withTimeoutOrNull(TIME_LIMIT_MILLIS) {
            provider.stream(request).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> answer.append(event.text)
                    is StreamEvent.Finished -> usage = event.usage
                    is StreamEvent.Failed -> failure = event.message
                    else -> Unit
                }
            }
        }
        usage?.let { reported -> saveUsage(threadId, modelKey, reported) }
        return when {
            finished == null -> BackgroundAnswer.Failed("$modelKey gave no answer within ${TIME_LIMIT_MILLIS / 1000} s")
            failure != null -> BackgroundAnswer.Failed(failure.orEmpty())
            else -> BackgroundAnswer.Success(answer.toString(), modelKey)
        }
    }

    private fun hasKey(modelKey: String): Boolean {
        val service = ChatService.byKey(ModelKey.serviceOf(modelKey)) ?: return false
        val secret = service.secret ?: return true
        return secrets.read(secret) != null
    }

    private fun pricePerMillion(modelKey: String): Double? {
        val info = catalog.find(modelKey) ?: return null
        val input = info.inputUsdPerMillion ?: return null
        val output = info.outputUsdPerMillion ?: return null
        return input + output
    }

    /** A row the chat never shows and the model never sees; it only carries the call's cost (D-027). */
    private suspend fun saveUsage(threadId: String, modelKey: String, usage: Usage) {
        val messageDao = database.messageDao()
        messageDao.upsert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                threadId = threadId,
                position = messageDao.nextPosition(threadId),
                role = HistoryMapper.BACKGROUND_ROLE,
                text = "",
                toolCallsJson = "[]",
                toolCallId = null,
                isComplete = true,
                createdAtMillis = System.currentTimeMillis(),
                model = modelKey,
                inputTokens = usage.inputTokens,
                cachedInputTokens = usage.cachedInputTokens,
                outputTokens = usage.outputTokens,
                costUsd = CostCalculator.costUsd(usage, catalog.find(modelKey)),
            ),
        )
    }

    companion object {
        private const val TIME_LIMIT_MILLIS = 60_000L

        /**
         * The scoped model with a saved key and the lowest input plus output
         * price per million tokens; the first in scoped order on a tie; the
         * thread's own model when no scoped model has both a key and a price.
         */
        fun choose(
            scopedModelKeys: List<String>,
            hasKey: (String) -> Boolean,
            pricePerMillion: (String) -> Double?,
            threadModelKey: String?,
        ): String? {
            val priced = scopedModelKeys
                .filter(hasKey)
                .mapNotNull { modelKey -> pricePerMillion(modelKey)?.let { price -> modelKey to price } }
            // minByOrNull keeps the first of equal prices, so the user's order breaks ties.
            val cheapest = priced.minByOrNull { (_, price) -> price }
            return cheapest?.first ?: threadModelKey
        }
    }
}
