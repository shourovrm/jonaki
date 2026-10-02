package app.jonaki.providers.openaicompatible

import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionStreamAssemblerTest {
    private fun assemble(vararg payloads: String): List<StreamEvent> {
        val assembler = ChatCompletionStreamAssembler()
        val events = mutableListOf<StreamEvent>()
        for (payload in payloads) {
            events += assembler.accept(payload)
        }
        events += assembler.finish()
        return events
    }

    @Test
    fun streamsTextThenFinishesWithUsageFromTheLastChunk() {
        val events = assemble(
            """{"choices":[{"delta":{"role":"assistant","content":"Hel"}}]}""",
            """{"choices":[{"delta":{"content":"lo"},"finish_reason":"stop"}]}""",
            """{"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":3,"prompt_tokens_details":{"cached_tokens":8}}}""",
            "[DONE]",
        )
        assertEquals(
            listOf(
                StreamEvent.TextDelta("Hel"),
                StreamEvent.TextDelta("lo"),
                StreamEvent.Finished(FinishReason.STOP, Usage(12, 3, 8)),
            ),
            events,
        )
    }

    @Test
    fun assemblesToolCallArgumentFragmentsByIndex() {
        val events = assemble(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_a","type":"function","function":{"name":"web_search","arguments":"{\"que"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":1,"id":"call_b","function":{"name":"read_file","arguments":""}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ry\":\"rain\"}"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":1,"function":{"arguments":"{\"path\":\"a.md\"}"}}]}}]}""",
            """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            "[DONE]",
        )
        assertEquals(
            listOf(
                StreamEvent.ToolCallReady(ToolCall("call_a", "web_search", """{"query":"rain"}""")),
                StreamEvent.ToolCallReady(ToolCall("call_b", "read_file", """{"path":"a.md"}""")),
                StreamEvent.Finished(FinishReason.TOOL_CALLS, null),
            ),
            events,
        )
    }

    @Test
    fun emptyToolArgumentsBecomeAnEmptyObject() {
        val events = assemble(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"list_threads"}}]},"finish_reason":"tool_calls"}]}""",
        )
        assertEquals(StreamEvent.ToolCallReady(ToolCall("c1", "list_threads", "{}")), events[0])
    }

    @Test
    fun readsReasoningFromOpenRouterAndDeepSeekFields() {
        val events = assemble(
            """{"choices":[{"delta":{"reasoning":"think A"}}]}""",
            """{"choices":[{"delta":{"reasoning_content":"think B"}}]}""",
            """{"choices":[{"delta":{"content":"ok"},"finish_reason":"stop"}]}""",
        )
        assertEquals(StreamEvent.ReasoningDelta("think A"), events[0])
        assertEquals(StreamEvent.ReasoningDelta("think B"), events[1])
    }

    @Test
    fun lengthFinishIsReported() {
        val events = assemble("""{"choices":[{"delta":{"content":"x"},"finish_reason":"length"}]}""")
        assertEquals(StreamEvent.Finished(FinishReason.LENGTH, null), events.last())
    }

    @Test
    fun errorChunkFailsTheStream() {
        val events = assemble(
            """{"choices":[{"delta":{"content":"par"}}]}""",
            """{"error":{"message":"Provider returned error","code":502}}""",
        )
        val failure = events.last() as StreamEvent.Failed
        assertTrue(failure.message.contains("Provider returned error"))
        assertTrue(failure.retryable)
        assertEquals(1, events.count { it is StreamEvent.Failed || it is StreamEvent.Finished })
    }

    @Test
    fun streamThatEndsWithoutFinishReasonFailsAsRetryable() {
        val events = assemble("""{"choices":[{"delta":{"content":"cut"}}]}""")
        val failure = events.last() as StreamEvent.Failed
        assertTrue(failure.retryable)
    }

    @Test
    fun nothingIsAcceptedAfterAnError() {
        val assembler = ChatCompletionStreamAssembler()
        assembler.accept("""{"error":{"message":"bad","code":400}}""")
        assertEquals(emptyList<StreamEvent>(), assembler.accept("""{"choices":[{"delta":{"content":"late"}}]}"""))
        assertEquals(emptyList<StreamEvent>(), assembler.finish())
    }
}
