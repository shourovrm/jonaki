package app.jonaki.run

import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageRequest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Saves the cost of every finished picture as a hidden row of the thread
 * (like the Jev guard's cost), so that the thread's and the month's cost
 * include it. The row is saved here, not in the tool, so that a picture that
 * was paid for is counted even when saving the file or the tool call fails.
 */
class RecordingImageGenerator(
    private val generator: ImageGenerator,
    private val threadId: String,
    /** The hidden row that carries a call's usage; `BackgroundModel.saveUsage`. */
    private val saveUsage: suspend (threadId: String, modelKey: String, usage: Usage, costUsd: Double?) -> Unit,
) : ImageGenerator {
    override suspend fun generate(request: ImageRequest): ImageOutcome {
        val outcome = generator.generate(request)
        if (outcome is ImageOutcome.Success && outcome.costUsd != null) {
            // The picture is already paid for, so a Stop that arrives now must not lose its cost.
            withContext(NonCancellable) {
                val usage = Usage(inputTokens = outcome.inputTokens, outputTokens = outcome.outputTokens)
                saveUsage(threadId, modelKeyOf(request.modelId), usage, outcome.costUsd)
            }
        }
        return outcome
    }

    companion object {
        /** In the usage sheet's "service:model" form. */
        fun modelKeyOf(imageModelId: String): String = "openrouter:$imageModelId"
    }
}
