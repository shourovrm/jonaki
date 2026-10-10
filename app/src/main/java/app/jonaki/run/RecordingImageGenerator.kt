package app.jonaki.run

import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageRequest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Saves every finished picture as a hidden row of the thread (like the Jev
 * guard's cost), so that the thread's and the month's cost include it and the
 * usage sheet lists the call. The row is saved here, not in the tool, so that
 * a picture that was paid for is counted even when saving the file or the
 * tool call fails.
 *
 * A picture whose service reports no cost (Gemini) is saved too, with an
 * unknown cost and the token counts the service gave: the usage sheet then
 * shows the call and its tokens, and the thread's cost total leaves the
 * picture out instead of counting a figure that was made up.
 */
class RecordingImageGenerator(
    private val generator: ImageGenerator,
    private val threadId: String,
    /** The hidden row that carries a call's usage; `BackgroundModel.saveUsage`. */
    private val saveUsage: suspend (threadId: String, modelKey: String, usage: Usage, costUsd: Double?) -> Unit,
) : ImageGenerator {
    override suspend fun generate(request: ImageRequest): ImageOutcome {
        val outcome = generator.generate(request)
        if (outcome is ImageOutcome.Success) {
            // The picture is already paid for, so a Stop that arrives now must not lose its cost.
            withContext(NonCancellable) {
                val usage = Usage(inputTokens = outcome.inputTokens, outputTokens = outcome.outputTokens)
                saveUsage(threadId, modelKeyOf(request), usage, outcome.costUsd)
            }
        }
        return outcome
    }

    companion object {
        /** In the usage sheet's "service:model" form. */
        fun modelKeyOf(request: ImageRequest): String = "${request.serviceKey}:${request.modelId}"
    }
}
