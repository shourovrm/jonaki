package app.jonaki.tools.youtubesummarize

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Asks [primary] again when it says the service is busy, waiting 2, 4 and
 * 8 seconds between tries, and then once on [fallback], a smaller model with
 * its own quota (D-190). A failure that is not retryable, such as a private
 * video, comes back at once. The waits add up to 14 s, under the tool's
 * 180 s limit even with four slow calls.
 */
class RetryingVideoSummarizer(
    private val primary: VideoSummarizer,
    private val fallback: VideoSummarizer?,
    /** Put before the summary when [fallback] answered, so the model and the user know. */
    private val fallbackNote: String,
    private val wait: suspend (millis: Long) -> Unit = { millis -> delay(millis) },
    /** Spreads the waits by up to a quarter either way so many phones do not retry in the same second. */
    private val jitter: (millis: Long) -> Long = { millis -> millis + Random.nextLong(-millis / 4, millis / 4 + 1) },
) : VideoSummarizer {

    override suspend fun summarize(request: VideoRequest): VideoAnswer {
        var answer = primary.summarize(request)
        for (waitMillis in WAITS_MILLIS) {
            if (!isBusy(answer)) {
                return answer
            }
            wait(jitter(waitMillis))
            answer = primary.summarize(request)
        }
        if (!isBusy(answer) || fallback == null) {
            return answer
        }
        return when (val liteAnswer = fallback.summarize(request)) {
            is VideoAnswer.Success -> liteAnswer.copy(text = "$fallbackNote\n\n${liteAnswer.text}")
            is VideoAnswer.Failed -> liteAnswer
        }
    }

    private fun isBusy(answer: VideoAnswer): Boolean = answer is VideoAnswer.Failed && answer.retryable

    companion object {
        val WAITS_MILLIS: List<Long> = listOf(2_000L, 4_000L, 8_000L)
    }
}
