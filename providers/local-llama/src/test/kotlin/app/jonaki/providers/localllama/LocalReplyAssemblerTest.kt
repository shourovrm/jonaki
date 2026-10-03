package app.jonaki.providers.localllama

import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReplyAssemblerTest {
    private val assembler = LocalReplyAssembler(newToolCallId = { "call_generated" })

    @Test
    fun snapshotsBecomeDeltasOfWhatIsNew() {
        val first = assembler.snapshot(content = "", reasoning = "The user")
        val second = assembler.snapshot(content = "", reasoning = "The user asks")
        val third = assembler.snapshot(content = "Hello", reasoning = "The user asks")
        assertEquals(listOf(StreamEvent.ReasoningDelta("The user")), first)
        assertEquals(listOf(StreamEvent.ReasoningDelta(" asks")), second)
        assertEquals(listOf(StreamEvent.TextDelta("Hello")), third)
    }

    @Test
    fun anUnchangedSnapshotGivesNothing() {
        assembler.snapshot(content = "Hi", reasoning = "")
        assertEquals(emptyList<StreamEvent>(), assembler.snapshot(content = "Hi", reasoning = ""))
    }

    @Test
    fun aRevisedSnapshotWaitsUntilItGrowsPastWhatWasShown() {
        assembler.snapshot(content = "Answer: <", reasoning = "")
        // The parser took "<" back as the start of a tool call.
        assertEquals(emptyList<StreamEvent>(), assembler.snapshot(content = "Answer: ", reasoning = ""))
        assertEquals(listOf(StreamEvent.TextDelta("b")), assembler.snapshot(content = "Answer: <b", reasoning = ""))
    }

    @Test
    fun finishedTextTurnSendsTheRestAndStopWithFreeUsage() {
        assembler.snapshot(content = "It is ", reasoning = "")
        val events = assembler.finish(
            """{"finish":"stop","content":"It is 31 °C.","reasoning":"","tool_calls":[],""" +
                """"prompt_tokens":1450,"cached_tokens":1200,"completion_tokens":9}""",
        )
        assertEquals(
            listOf(
                StreamEvent.TextDelta("31 °C."),
                StreamEvent.Finished(FinishReason.STOP, Usage(1450, 9, cachedInputTokens = 1200, costUsd = 0.0)),
            ),
            events,
        )
    }

    @Test
    fun toolCallsAreReadyAtTheEndWithTheirIdsOrNewOnes() {
        val events = assembler.finish(
            """{"finish":"stop","content":"","reasoning":"","tool_calls":[""" +
                """{"name":"web_search","arguments":"{\"query\":\"Dhaka\"}","id":"call_7"},""" +
                """{"name":"phone","arguments":"","id":""}],""" +
                """"prompt_tokens":10,"cached_tokens":0,"completion_tokens":20}""",
        )
        assertEquals(
            listOf(
                StreamEvent.ToolCallReady(ToolCall("call_7", "web_search", """{"query":"Dhaka"}""")),
                StreamEvent.ToolCallReady(ToolCall("call_generated", "phone", "{}")),
                StreamEvent.Finished(FinishReason.TOOL_CALLS, Usage(10, 20, cachedInputTokens = 0, costUsd = 0.0)),
            ),
            events,
        )
    }

    @Test
    fun aTurnCutByTheLimitRunsNoToolCall() {
        val events = assembler.finish(
            """{"finish":"length","content":"","reasoning":"","tool_calls":[""" +
                """{"name":"web_search","arguments":"{\"query\":\"Dh","id":""}],""" +
                """"prompt_tokens":10,"cached_tokens":0,"completion_tokens":400}""",
        )
        assertEquals(
            listOf(StreamEvent.Finished(FinishReason.LENGTH, Usage(10, 400, cachedInputTokens = 0, costUsd = 0.0))),
            events,
        )
    }

    @Test
    fun anErrorFailsWithoutRetry() {
        val events = assembler.finish("""{"finish":"error","error":"The conversation is 9000 tokens; the local model holds 8192."}""")
        assertEquals(
            listOf(StreamEvent.Failed("The conversation is 9000 tokens; the local model holds 8192.", retryable = false)),
            events,
        )
    }

    @Test
    fun aCancelledTurnEndsWithoutEvents() {
        assertTrue(assembler.finish("""{"finish":"cancelled","content":"half","tool_calls":[]}""").isEmpty())
    }
}
