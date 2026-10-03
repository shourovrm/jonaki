package app.jonaki.providers.localllama

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.ThinkingLevel
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalLlamaProviderTest {
    private lateinit var modelFolder: File
    private lateinit var engine: FakeEngine
    private lateinit var dispatcher: CoroutineDispatcher
    private lateinit var provider: LocalLlamaProvider

    private val textReply = """{"finish":"stop","content":"Hello!","reasoning":"","tool_calls":[],""" +
        """"prompt_tokens":120,"cached_tokens":0,"completion_tokens":3}"""

    @Before
    fun setUp() {
        modelFolder = Files.createTempDirectory("models").toFile()
        File(modelFolder, "small.gguf").writeText("weights")
        File(modelFolder, "large.gguf").writeText("weights")
        engine = FakeEngine()
        dispatcher = LocalLlamaProvider.newEngineDispatcher()
        provider = LocalLlamaProvider(modelFolder, engine, dispatcher, newToolCallId = { "call_new" })
    }

    @After
    fun tearDown() {
        modelFolder.deleteRecursively()
    }

    private fun request(model: String = "small.gguf", thinkingLevel: ThinkingLevel? = null) = ChatRequest(
        model = model,
        systemPrompt = "You are Jonaki.",
        messages = listOf(Message(Role.USER, "Hi")),
        thinkingLevel = thinkingLevel,
    )

    @Test
    fun streamsSnapshotsAsDeltasAndEndsWithTheResult() = runBlocking {
        engine.snapshots = listOf("" to "Greeting", "Hel" to "Greeting", "Hello!" to "Greeting")
        engine.result = textReply
        val events = provider.stream(request()).toList()
        assertEquals(
            listOf(
                StreamEvent.ReasoningDelta("Greeting"),
                StreamEvent.TextDelta("Hel"),
                StreamEvent.TextDelta("lo!"),
            ),
            events.dropLast(1),
        )
        val finished = events.last() as StreamEvent.Finished
        assertEquals(FinishReason.STOP, finished.reason)
        assertEquals(0.0, finished.usage?.costUsd)
    }

    @Test
    fun loadsTheModelOnceWithTheDesignSettings() = runBlocking {
        engine.result = textReply
        provider.stream(request()).toList()
        provider.stream(request()).toList()
        assertEquals(listOf("load small.gguf 8192 4"), engine.calls.filter { call -> call.startsWith("load") })
    }

    @Test
    fun anotherModelReplacesTheLoadedOne() = runBlocking {
        engine.result = textReply
        provider.stream(request("small.gguf")).toList()
        provider.stream(request("large.gguf")).toList()
        assertEquals(listOf("load small.gguf 8192 4", "load large.gguf 8192 4"), engine.calls.filter { call -> call.startsWith("load") })
    }

    @Test
    fun unloadFreesTheModelAndTheNextTurnLoadsItAgain() = runBlocking {
        engine.result = textReply
        provider.stream(request()).toList()
        provider.unload()
        provider.stream(request()).toList()
        assertEquals(listOf("load small.gguf 8192 4", "unload", "load small.gguf 8192 4"), engine.calls.filter { call -> call.startsWith("load") || call == "unload" })
    }

    @Test
    fun thinkingOffReachesTheEngine() = runBlocking {
        engine.result = textReply
        provider.stream(request(thinkingLevel = ThinkingLevel.OFF)).toList()
        assertTrue(engine.lastRequest.contains("\"enable_thinking\":false"))
    }

    @Test
    fun aMissingModelFileFailsWithoutLoading() = runBlocking {
        val events = provider.stream(request("gone.gguf")).toList()
        assertEquals(listOf(StreamEvent.Failed("The model file gone.gguf is not on this phone", retryable = false)), events)
        assertTrue(engine.calls.none { call -> call.startsWith("load") })
    }

    @Test
    fun aModelIdCannotReachOutsideTheFolder() = runBlocking {
        File(modelFolder.parentFile, "outside.gguf").writeText("weights")
        try {
            val events = provider.stream(request("../outside.gguf")).toList()
            assertTrue(events.single() is StreamEvent.Failed)
        } finally {
            File(modelFolder.parentFile, "outside.gguf").delete()
        }
    }

    @Test
    fun aLoadFailureBecomesAFailedEventAndTheNextTurnTriesAgain() = runBlocking {
        engine.loadFailure = "llama.cpp could not load small.gguf"
        val events = provider.stream(request()).toList()
        assertEquals(listOf(StreamEvent.Failed("llama.cpp could not load small.gguf", retryable = false)), events)
        engine.loadFailure = null
        engine.result = textReply
        provider.stream(request()).toList()
        assertEquals(2, engine.calls.count { call -> call.startsWith("load") })
    }

    @Test
    fun cancellingTheFlowCancelsTheNativeTurn() = runBlocking {
        engine.snapshots = listOf("First words" to "")
        engine.blockAfterSnapshots = true
        engine.result = """{"finish":"cancelled","content":"First words","tool_calls":[]}"""
        val firstEvent = withTimeout(5_000) { provider.stream(request()).first() }
        assertEquals(StreamEvent.TextDelta("First words"), firstEvent)
        // first() cancels the flow, which must reach the engine; the blocked turn then returns.
        assertTrue(engine.returnedAfterCancel.await(5, TimeUnit.SECONDS))
        assertTrue("cancel" in engine.calls)
    }

    @Test
    fun aTurnAfterACancelledOneRunsNormally() = runBlocking {
        engine.snapshots = listOf("First words" to "")
        engine.blockAfterSnapshots = true
        engine.result = """{"finish":"cancelled","content":"","tool_calls":[]}"""
        withTimeout(5_000) { provider.stream(request()).first() }
        engine.blockAfterSnapshots = false
        engine.snapshots = emptyList()
        engine.result = textReply
        val events = withTimeout(5_000) { provider.stream(request()).toList() }
        assertEquals(FinishReason.STOP, (events.last() as StreamEvent.Finished).reason)
    }

    @Test
    fun anEngineExceptionBecomesAFailedEvent() = runBlocking {
        engine.result = "not json"
        val events = provider.stream(request()).toList()
        assertTrue((events.single() as StreamEvent.Failed).message.startsWith("The local model failed"))
    }

    /** Replays scripted snapshots; can block until cancel(), as the native turn does. */
    private class FakeEngine : LlamaEngine {
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
        var snapshots: List<Pair<String, String>> = emptyList()
        var result = ""
        var loadFailure: String? = null
        var blockAfterSnapshots = false
        var lastRequest = ""
        val returnedAfterCancel = CountDownLatch(1)

        @Volatile
        private var cancelled = false

        override fun load(modelFile: File, contextTokens: Int, threads: Int) {
            calls += "load ${modelFile.name} $contextTokens $threads"
            loadFailure?.let { message -> throw IllegalStateException(message) }
        }

        override fun unload() {
            calls += "unload"
        }

        override fun generate(requestJson: String, onSnapshot: (content: String, reasoning: String) -> Unit): String {
            calls += "generate"
            lastRequest = requestJson
            for ((content, reasoning) in snapshots) {
                onSnapshot(content, reasoning)
            }
            if (blockAfterSnapshots) {
                val deadline = System.currentTimeMillis() + 5_000
                while (!cancelled && System.currentTimeMillis() < deadline) {
                    Thread.sleep(5)
                }
                if (cancelled) {
                    returnedAfterCancel.countDown()
                }
            }
            return result
        }

        override fun cancel() {
            calls += "cancel"
            cancelled = true
        }

        override fun clearCancel() {
            calls += "clearCancel"
            cancelled = false
        }
    }
}
