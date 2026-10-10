package app.jonaki.run

import app.jonaki.core.agent.AgentEvent
import app.jonaki.core.agent.DirectToolRun
import app.jonaki.core.agent.InMemoryStepRecorder
import app.jonaki.core.agent.RunOutcome
import app.jonaki.core.model.Role
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.toolapi.GeneratedVideos
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.VideoCheckOutcome
import app.jonaki.core.toolapi.VideoDownloadOutcome
import app.jonaki.core.toolapi.VideoFailure
import app.jonaki.core.toolapi.VideoGenerator
import app.jonaki.core.toolapi.VideoRequest
import app.jonaki.core.toolapi.VideoStartOutcome
import app.jonaki.feature.chat.MediaKind
import app.jonaki.tools.generatevideo.GenerateVideoTool
import app.jonaki.tools.generatevideo.PendingVideoJobs
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A send in video mode runs the real generate_video tool through [DirectToolRun]. A job can outlive
 * one call of the tool, so the run asks again for the same job (no new charge), up to three calls.
 */
class MediaModeVideoRunTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val toolContext = ToolContext(threadFolder = threadFolder, httpClient = OkHttpClient())
    private val recorder = InMemoryStepRecorder()
    private val modelKey = "openrouter:google/veo-3.1-lite"

    /** The fake clock moves only when the tool waits, so a whole run takes no real time. */
    private var clockMillis = 1_000_000L
    private val firstClockMillis = clockMillis
    private val recordedCosts = mutableListOf<Pair<String, Double?>>()

    /** The service: the job is ready once the fake clock has passed [readyAfterMillis] since the first call began. */
    private inner class FakeService(val readyAfterMillis: Long?) : VideoGenerator {
        val starts = mutableListOf<VideoRequest>()
        val checkedJobIds = mutableListOf<String>()
        var endJob = false

        override suspend fun start(request: VideoRequest): VideoStartOutcome {
            starts += request
            return VideoStartOutcome.Started("job-1")
        }

        override suspend fun check(serviceKey: String, jobId: String): VideoCheckOutcome {
            checkedJobIds += jobId
            if (endJob) {
                return VideoCheckOutcome.JobEnded("failed", "the service could not make it")
            }
            val isReady = readyAfterMillis != null && clockMillis - firstClockMillis >= readyAfterMillis
            return if (isReady) VideoCheckOutcome.Completed(listOf("/content"), costUsd = 0.12) else VideoCheckOutcome.Working("in_progress")
        }

        override suspend fun download(serviceKey: String, contentUrl: String, target: File): VideoDownloadOutcome {
            val mp4 = byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray() + ByteArray(12)
            target.writeBytes(mp4)
            return VideoDownloadOutcome.Saved("video/mp4", mp4.size.toLong())
        }
    }

    private fun toolWith(service: VideoGenerator, wait: suspend (kotlin.time.Duration) -> Unit = ::advanceClock) = GenerateVideoTool(
        generator = service,
        modelKeys = listOf(modelKey),
        defaultModelKey = { modelKey },
        onJobCompleted = { key, cost -> recordedCosts += key to cost },
        wait = wait,
        nowMillis = { clockMillis },
    )

    private suspend fun advanceClock(duration: kotlin.time.Duration) {
        clockMillis += duration.inWholeMilliseconds
    }

    private suspend fun sendVideo(tool: GenerateVideoTool, typed: String = "a boat at dawn"): RunOutcome {
        var nextId = 1
        return DirectToolRun(recorder).run(
            tool = tool,
            arguments = MediaModeCall.arguments(typed),
            toolContext = toolContext,
            callId = "call-1",
            nextCall = { output -> MediaModeCall.nextCall(MediaKind.VIDEO, output) },
            maxCalls = MediaModeCall.maxCalls(MediaKind.VIDEO),
            newCallId = { "call-${++nextId}" },
        )
    }

    private val savedCalls get() = recorder.events.filterIsInstance<AgentEvent.AssistantMessage>().map { event -> event.message.toolCalls.single() }
    private val results get() = recorder.events.filterIsInstance<AgentEvent.ToolFinished>().map { event -> event.message.text }

    @Test
    fun aVideoDoneInTheFirstCallSavesOneCallAndOneCost() = runBlocking {
        val service = FakeService(readyAfterMillis = 0)

        val outcome = sendVideo(toolWith(service))

        assertEquals(RunOutcome.Completed(""), outcome)
        assertEquals(1, savedCalls.size)
        assertEquals("""{"prompt":"a boat at dawn"}""", savedCalls.single().argumentsJson)
        assertEquals("generate_video", savedCalls.single().toolName)
        assertNotNull(GeneratedVideos.pathIn(results.single()))
        assertEquals(listOf(modelKey to 0.12), recordedCosts)
        assertEquals(1, service.starts.size)
        // The tool's low-cost defaults apply: the call names no length and no resolution.
        assertEquals("a boat at dawn", service.starts.single().prompt)
        assertFalse(recorder.events.any { event -> event is AgentEvent.RequestSent || event is AgentEvent.TextDelta })
    }

    @Test
    fun aVideoHandedOverOnceIsCollectedByASecondCallWithTheJobId() = runBlocking {
        // Ready 9 minutes after the first call began: after the first hand-over at 7.5 minutes, before the second.
        val service = FakeService(readyAfterMillis = 9.minutes.inWholeMilliseconds)

        val outcome = sendVideo(toolWith(service))

        assertEquals(RunOutcome.Completed(""), outcome)
        assertEquals(2, savedCalls.size)
        assertEquals("""{"prompt":"a boat at dawn"}""", savedCalls[0].argumentsJson)
        assertEquals("""{"job_id":"job-1"}""", savedCalls[1].argumentsJson)
        assertEquals(listOf("call-1", "call-2"), savedCalls.map { call -> call.id })
        assertTrue(results[0], results[0].startsWith("The video is still being made"))
        assertNotNull(GeneratedVideos.pathIn(results[1]))
        // The service was asked to start once, and the cost was saved once.
        assertEquals(1, service.starts.size)
        assertEquals(1, recordedCosts.size)
        assertEquals(1, recorder.events.count { event -> event is AgentEvent.RunFinished })
    }

    @Test
    fun aVideoHandedOverThreeTimesStopsAfterTheThirdCallAndKeepsTheJob() = runBlocking {
        val service = FakeService(readyAfterMillis = null)

        val outcome = sendVideo(toolWith(service))

        assertEquals(RunOutcome.Completed(""), outcome)
        assertEquals(3, savedCalls.size)
        assertEquals(listOf("call-1", "call-2", "call-3"), savedCalls.map { call -> call.id })
        assertEquals("""{"job_id":"job-1"}""", savedCalls[1].argumentsJson)
        assertEquals("""{"job_id":"job-1"}""", savedCalls[2].argumentsJson)
        assertTrue(results.all { text -> text.startsWith("The video is still being made") })
        assertEquals(1, service.starts.size)
        assertTrue(recordedCosts.isEmpty())
        // The job stays in the pending file, so the next ordinary message lets the agent collect it.
        assertNotNull(PendingVideoJobs(threadFolder).find("job-1"))
        // Three calls of about 7.5 minutes: the run covers about 22.5 minutes of the fake clock.
        assertTrue(clockMillis - firstClockMillis >= 3 * 7.minutes.inWholeMilliseconds)
    }

    @Test
    fun aFailedJobIsSavedAsAnErrorResultWithNoFollowUp() = runBlocking {
        val service = FakeService(readyAfterMillis = null).apply { endJob = true }

        val outcome = sendVideo(toolWith(service))

        assertEquals(RunOutcome.Completed(""), outcome)
        assertEquals(1, savedCalls.size)
        assertTrue(results.single(), results.single().startsWith("Error: "))
        assertNull(GeneratedVideos.pathIn(results.single()))
        assertTrue(recordedCosts.isEmpty())
    }

    @Test
    fun aRefusalBecauseAnEarlierJobIsPendingIsShownAsItIsWithoutNewVideo() = runBlocking {
        val first = FakeService(readyAfterMillis = null)
        sendVideo(toolWith(first))
        val recorderAfterFirst = recorder.events.size
        // A new send in the same thread while job-1 is still pending (younger than 60 minutes).
        clockMillis = firstClockMillis + 25.minutes.inWholeMilliseconds
        val second = FakeService(readyAfterMillis = 0)

        sendVideo(toolWith(second))

        val newEvents = recorder.events.drop(recorderAfterFirst)
        val newCalls = newEvents.filterIsInstance<AgentEvent.AssistantMessage>().map { event -> event.message.toolCalls.single() }
        assertEquals(1, newCalls.size)
        assertFalse(newCalls.single().argumentsJson.contains("new_video"))
        val refusal = newEvents.filterIsInstance<AgentEvent.ToolFinished>().single().message.text
        assertTrue(refusal, refusal.startsWith("Error: an earlier video job of this thread is still waiting"))
        assertTrue(second.starts.isEmpty())
    }

    @Test
    fun aKeyProblemAtStartIsSavedAsAnErrorResult() = runBlocking {
        val refusing = object : VideoGenerator {
            override suspend fun start(request: VideoRequest) = VideoStartOutcome.Failed(VideoFailure.KEY_PROBLEM, "bad key")

            override suspend fun check(serviceKey: String, jobId: String): VideoCheckOutcome = error("not reached")

            override suspend fun download(serviceKey: String, contentUrl: String, target: File): VideoDownloadOutcome = error("not reached")
        }

        sendVideo(toolWith(refusing))

        assertEquals(1, savedCalls.size)
        assertTrue(results.single().startsWith("Error: "))
    }

    @Test
    fun stopDuringPollingEndsTheRunAsStoppedAndKeepsTheJobInThePendingFile() = runBlocking {
        val service = FakeService(readyAfterMillis = null)
        val polling = CompletableDeferred<Unit>()
        val tool = toolWith(service, wait = {
            polling.complete(Unit)
            awaitCancellation()
        })

        val job = launch { sendVideo(tool) }
        polling.await()
        job.cancel(CancellationException("Stop"))
        job.join()

        assertEquals(listOf("AssistantMessage", "ToolStarted", "RunFinished"), recorder.events.map { event -> event::class.simpleName })
        assertEquals(RunOutcome.Stopped(""), (recorder.events.last() as AgentEvent.RunFinished).outcome)
        // A Stop cannot return text, but the tool wrote the job id before its first wait.
        assertNotNull(PendingVideoJobs(threadFolder).find("job-1"))
    }

    @Test
    fun theRowsOfATwoCallRunMapToAValidHistoryFollowedByAnOrdinaryMessage() = runBlocking {
        val service = FakeService(readyAfterMillis = 9.minutes.inWholeMilliseconds)
        sendVideo(toolWith(service))

        val rows = mutableListOf<MessageEntity>()
        fun add(role: Role, text: String, calls: List<app.jonaki.core.model.ToolCall> = emptyList(), callId: String? = null) {
            rows += MessageEntity(
                id = "m${rows.size}",
                threadId = "t",
                position = rows.size.toLong(),
                role = role.name,
                text = text,
                toolCallsJson = HistoryMapper.toolCallsToJson(calls),
                toolCallId = callId,
                isComplete = true,
                createdAtMillis = 0,
            )
        }
        add(Role.USER, "a boat at dawn")
        // Rows in the order RunSession saves them: each assistant call row, then its tool result row.
        for (event in recorder.events) {
            when (event) {
                is AgentEvent.AssistantMessage -> add(Role.ASSISTANT, event.message.text, event.message.toolCalls)
                is AgentEvent.ToolFinished -> add(Role.TOOL, event.message.text, callId = event.message.toolCallId)
                else -> Unit
            }
        }
        add(Role.USER, "thanks, make it longer")

        val history = HistoryMapper.toHistory(rows)

        assertEquals(
            listOf(Role.USER, Role.ASSISTANT, Role.TOOL, Role.ASSISTANT, Role.TOOL, Role.USER),
            history.map { message -> message.role },
        )
        assertEquals("call-1", history[2].toolCallId)
        assertEquals("call-2", history[4].toolCallId)
        assertEquals(history[1].toolCalls.single().id, history[2].toolCallId)
        assertEquals(history[3].toolCalls.single().id, history[4].toolCallId)
    }
}
