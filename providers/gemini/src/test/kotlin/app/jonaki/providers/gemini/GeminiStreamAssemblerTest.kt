package app.jonaki.providers.gemini

import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiStreamAssemblerTest {
    private fun assemble(vararg payloads: String): List<StreamEvent> {
        val assembler = GeminiStreamAssembler()
        val events = mutableListOf<StreamEvent>()
        for (payload in payloads) {
            events += assembler.accept(payload)
        }
        events += assembler.finish()
        return events
    }

    @Test
    fun streamsTextAndReportsUsageWithThoughtTokensAsOutput() {
        val events = assemble(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]}}]}""",
            """{"candidates":[{"content":{"role":"model","parts":[{"text":" world"}]},"finishReason":"STOP"}],
               "usageMetadata":{"promptTokenCount":20,"candidatesTokenCount":5,"thoughtsTokenCount":7,"cachedContentTokenCount":10}}""",
        )
        assertEquals(
            listOf(
                StreamEvent.TextDelta("Hello"),
                StreamEvent.TextDelta(" world"),
                StreamEvent.Finished(FinishReason.STOP, Usage(20, 12, 10)),
            ),
            events,
        )
    }

    @Test
    fun functionCallKeepsItsThoughtSignatureInTheId() {
        val events = assemble(
            """{"candidates":[{"content":{"role":"model","parts":[
                {"functionCall":{"name":"web_search","args":{"query":"rain"}},"thoughtSignature":"c2ln"}
               ]},"finishReason":"STOP"}]}""",
        )
        val ready = events[0] as StreamEvent.ToolCallReady
        assertEquals("web_search", ready.toolCall.toolName)
        assertEquals("""{"query":"rain"}""", ready.toolCall.argumentsJson)
        assertEquals("c2ln", GeminiToolCallId.thoughtSignature(ready.toolCall.id))
        assertEquals(FinishReason.TOOL_CALLS, (events[1] as StreamEvent.Finished).reason)
    }

    @Test
    fun twoCallsInOneTurnGetDistinctIds() {
        val events = assemble(
            """{"candidates":[{"content":{"parts":[
                {"functionCall":{"name":"a","args":{}}},
                {"functionCall":{"name":"b","args":{}}}
               ]},"finishReason":"STOP"}]}""",
        )
        val ids = events.filterIsInstance<StreamEvent.ToolCallReady>().map { it.toolCall.id }
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun usesTheServiceCallIdWhenGiven() {
        val events = assemble(
            """{"candidates":[{"content":{"parts":[{"functionCall":{"id":"fc_9","name":"a","args":{}}}]},"finishReason":"STOP"}]}""",
        )
        assertEquals(StreamEvent.ToolCallReady(ToolCall("fc_9", "a", "{}")), events[0])
    }

    @Test
    fun thoughtPartsBecomeReasoning() {
        val events = assemble(
            """{"candidates":[{"content":{"parts":[{"text":"thinking","thought":true},{"text":"answer"}]},"finishReason":"STOP"}]}""",
        )
        assertEquals(StreamEvent.ReasoningDelta("thinking"), events[0])
        assertEquals(StreamEvent.TextDelta("answer"), events[1])
    }

    @Test
    fun maxTokensIsLength() {
        val events = assemble("""{"candidates":[{"content":{"parts":[{"text":"x"}]},"finishReason":"MAX_TOKENS"}]}""")
        assertEquals(FinishReason.LENGTH, (events.last() as StreamEvent.Finished).reason)
    }

    @Test
    fun blockedPromptFailsWithoutRetry() {
        val events = assemble("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
        val failure = events.single() as StreamEvent.Failed
        assertFalse(failure.retryable)
        assertTrue(failure.message.contains("SAFETY"))
    }

    @Test
    fun errorChunkFailsTheStream() {
        val events = assemble("""{"error":{"code":503,"message":"high demand","status":"UNAVAILABLE"}}""")
        val failure = events.single() as StreamEvent.Failed
        assertTrue(failure.retryable)
    }

    @Test
    fun streamWithoutFinishReasonFailsAsRetryable() {
        val events = assemble("""{"candidates":[{"content":{"parts":[{"text":"cut"}]}}]}""")
        assertTrue((events.last() as StreamEvent.Failed).retryable)
    }
}
