package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.ToolContext
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The request-log times of D-132, taken through the agent loop with a scripted provider. */
class RequestTimerTest {
    /** Milliseconds since boot as the fake provider moves them forward; the wall clock is 1,000,000 ms ahead. */
    private var elapsedMillis = 5_000L
    private val timer = RequestTimer(elapsedClock = { elapsedMillis }, wallClock = { elapsedMillis + 1_000_000 })

    /** The times as they stood when each model turn's message was recorded. */
    private val timesPerTurn = mutableListOf<RequestTimes?>()
    private val firstTextEvents = mutableListOf<String>()

    private val recorder = StepRecorder { event ->
        if (timer.observe(event)) {
            firstTextEvents += (event as AgentEvent.TextDelta).text
        }
        if (event is AgentEvent.AssistantMessage) {
            timesPerTurn += timer.current
        }
    }

    private fun run(vararg turns: Flow<StreamEvent>) = runBlocking {
        AgentLoop(
            provider = ScriptedProvider(*turns),
            tools = listOf(FakeTool("lookup")),
            toolContext = ToolContext(Files.createTempDirectory("thread").toFile(), OkHttpClient()),
            permissionBroker = PermissionBroker(FixedApprover(ApprovalDecision.ALLOW_ONCE)),
            recorder = recorder,
            settings = AgentSettings(model = "test-model", systemPrompt = "You are Jonaki."),
        ).run(listOf(Message(Role.USER, "Hi")))
    }

    /** Waits [waitMillis], streams [chunks] [gapMillis] apart, then finishes. */
    private fun slowTextTurn(waitMillis: Long, gapMillis: Long, vararg chunks: String): Flow<StreamEvent> = flow {
        elapsedMillis += waitMillis
        for (chunk in chunks) {
            emit(StreamEvent.TextDelta(chunk))
            elapsedMillis += gapMillis
        }
        emit(StreamEvent.Finished(FinishReason.STOP, Usage(inputTokens = 10, outputTokens = 5)))
    }

    @Test
    fun firstTextIsTimedFromTheMomentTheRequestWasSent() {
        run(slowTextTurn(waitMillis = 800, gapMillis = 30, "Hello", " there"))

        assertEquals(listOf(RequestTimes(sentAtMillis = 1_005_000, sentElapsedMillis = 5_000, firstTextElapsedMillis = 5_800)), timesPerTurn)
        assertEquals(listOf("Hello"), firstTextEvents)
    }

    @Test
    fun whitespaceBeforeTheAnswerDoesNotCountAsFirstText() {
        // The chat shows nothing for blank text, so the timer waits for the first visible character.
        run(slowTextTurn(waitMillis = 400, gapMillis = 50, "\n", " ", "Yes"))

        assertEquals(5_500L, timesPerTurn.single()?.firstTextElapsedMillis)
        assertEquals(listOf("Yes"), firstTextEvents)
    }

    @Test
    fun eachModelTurnGetsItsOwnTimes() {
        val toolTurn: Flow<StreamEvent> = flow {
            elapsedMillis += 300
            emit(StreamEvent.ToolCallReady(call("call-1", "lookup")))
            emit(StreamEvent.Finished(FinishReason.TOOL_CALLS, usage = null))
        }

        run(toolTurn, slowTextTurn(waitMillis = 900, gapMillis = 10, "Done."))

        val (toolRequest, answerRequest) = timesPerTurn
        assertEquals(RequestTimes(1_005_000, 5_000, firstTextElapsedMillis = null), toolRequest)
        assertEquals(RequestTimes(1_005_300, 5_300, firstTextElapsedMillis = 6_200), answerRequest)
    }

    @Test
    fun nothingIsTimedBeforeTheFirstRequest() {
        assertNull(timer.current)
    }
}
