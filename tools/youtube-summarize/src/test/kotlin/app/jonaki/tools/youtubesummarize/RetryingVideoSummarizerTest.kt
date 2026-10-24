package app.jonaki.tools.youtubesummarize

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-190: a busy Gemini is asked again after 2, 4 and 8 seconds, then once on Flash Lite. */
class RetryingVideoSummarizerTest {
    private val request = VideoRequest("https://www.youtube.com/watch?v=abc", "Summarise", null, null, false)
    private val waits = mutableListOf<Long>()
    private val busy = VideoAnswer.Failed("Gemini answered HTTP 503: overloaded", retryable = true)
    private val blocked = VideoAnswer.Failed("Gemini answered HTTP 403: PERMISSION_DENIED", retryable = false)

    private fun scripted(answers: List<VideoAnswer>): Pair<VideoSummarizer, () -> Int> {
        var calls = 0
        val summarizer = VideoSummarizer { answers[minOf(calls++, answers.lastIndex)] }
        return summarizer to { calls }
    }

    private fun retrying(primary: VideoSummarizer, fallback: VideoSummarizer? = null) =
        RetryingVideoSummarizer(primary, fallback, fallbackNote = "Summarised with Gemini 3.5 Flash Lite; Flash was busy.", wait = { millis -> waits += millis }, jitter = { it })

    @Test
    fun `busy twice then success waits 2 and 4 seconds`() = runBlocking {
        val (primary, calls) = scripted(listOf(busy, busy, VideoAnswer.Success("done", 10)))
        val answer = retrying(primary).summarize(request)
        assertEquals(VideoAnswer.Success("done", 10), answer)
        assertEquals(3, calls())
        assertEquals(listOf(2_000L, 4_000L), waits)
    }

    @Test
    fun `four busy answers on Flash make one call on Flash Lite`() = runBlocking {
        val (primary, primaryCalls) = scripted(listOf(busy))
        val (fallback, fallbackCalls) = scripted(listOf(VideoAnswer.Success("lite summary", 5)))
        val answer = retrying(primary, fallback).summarize(request) as VideoAnswer.Success
        assertEquals(4, primaryCalls())
        assertEquals(1, fallbackCalls())
        assertEquals(listOf(2_000L, 4_000L, 8_000L), waits)
        assertTrue(answer.text.startsWith("Summarised with Gemini 3.5 Flash Lite; Flash was busy.\n\n"))
        assertTrue(answer.text.endsWith("lite summary"))
    }

    @Test
    fun `a failure that is not retryable returns at once`() = runBlocking {
        val (primary, calls) = scripted(listOf(blocked))
        val (fallback, fallbackCalls) = scripted(listOf(VideoAnswer.Success("never", null)))
        assertEquals(blocked, retrying(primary, fallback).summarize(request))
        assertEquals(1, calls())
        assertEquals(0, fallbackCalls())
        assertTrue(waits.isEmpty())
    }

    @Test
    fun `when Flash Lite is busy too the last failure comes back`() = runBlocking {
        val (primary, _) = scripted(listOf(busy))
        val liteBusy = VideoAnswer.Failed("Gemini answered HTTP 429: RESOURCE_EXHAUSTED", retryable = true)
        val (fallback, _) = scripted(listOf(liteBusy))
        assertEquals(liteBusy, retrying(primary, fallback).summarize(request))
    }
}
