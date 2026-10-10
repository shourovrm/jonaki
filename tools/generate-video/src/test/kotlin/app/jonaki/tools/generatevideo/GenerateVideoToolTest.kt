package app.jonaki.tools.generatevideo

import app.jonaki.core.toolapi.GeneratedVideos
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.VideoCheckOutcome
import app.jonaki.core.toolapi.VideoDownloadOutcome
import app.jonaki.core.toolapi.VideoFailure
import app.jonaki.core.toolapi.VideoGenerator
import app.jonaki.core.toolapi.VideoModelFacts
import app.jonaki.core.toolapi.VideoRequest
import app.jonaki.core.toolapi.VideoStartOutcome
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GenerateVideoToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())

    private val veoKey = "openrouter:google/veo-3.1-lite"
    private val grokKey = "openrouter:x-ai/grok-imagine-video-1.5-lite"
    private val facts = mapOf(
        veoKey to VideoModelFacts(
            veoKey,
            supportedDurations = listOf(8, 4, 6),
            supportedResolutions = listOf("720p", "1080p"),
            supportedAspectRatios = listOf("16:9", "9:16"),
            generatesAudio = true,
        ),
    )

    /** The fake service: scripted answers, and a record of what the tool asked. */
    private class FakeGenerator : VideoGenerator {
        val starts = mutableListOf<VideoRequest>()
        val checks = mutableListOf<String>()
        val downloads = mutableListOf<String>()
        var startOutcome: VideoStartOutcome = VideoStartOutcome.Started("job-1")

        /** Answers in order; the last one repeats. */
        var checkOutcomes: MutableList<VideoCheckOutcome> = mutableListOf(VideoCheckOutcome.Completed(listOf("/api/v1/videos/job-1/content?index=0"), 0.2))
        var downloadOutcome: VideoDownloadOutcome? = null
        var downloadBytes: ByteArray = mp4Bytes()
        var downloadMediaType = "video/mp4"

        override suspend fun start(request: VideoRequest): VideoStartOutcome {
            starts += request
            return startOutcome
        }

        override suspend fun check(serviceKey: String, jobId: String): VideoCheckOutcome {
            checks += jobId
            return if (checkOutcomes.size > 1) checkOutcomes.removeAt(0) else checkOutcomes.first()
        }

        override suspend fun download(serviceKey: String, contentUrl: String, target: File): VideoDownloadOutcome {
            downloads += contentUrl
            downloadOutcome?.let { return it }
            target.writeBytes(downloadBytes)
            return VideoDownloadOutcome.Saved(downloadMediaType, downloadBytes.size.toLong())
        }
    }

    private val generator = FakeGenerator()
    private var clock = 1_000_000L
    private val waits = mutableListOf<Duration>()
    private val recordedCosts = mutableListOf<Pair<String, Double?>>()

    private fun tool(
        modelKeys: List<String> = listOf(veoKey, grokKey),
        withFacts: Map<String, VideoModelFacts> = facts,
        throwOnWait: Boolean = false,
    ) = GenerateVideoTool(
        generator = generator,
        modelKeys = modelKeys,
        defaultModelKey = { veoKey },
        facts = withFacts,
        onJobCompleted = { modelKey, cost -> recordedCosts += modelKey to cost },
        wait = { duration ->
            if (throwOnWait) throw CancellationException("Stop")
            waits += duration
            clock += duration.inWholeMilliseconds
        },
        nowMillis = { clock },
    )

    private fun run(tool: GenerateVideoTool, json: String) =
        runBlocking { tool.run(Json.parseToJsonElement(json).jsonObject, context) }

    private val working = VideoCheckOutcome.Working("in_progress")

    @Test
    fun savesTheVideoAndAnswersWithPathLengthSizeModelAndCost() {
        generator.checkOutcomes = mutableListOf(working, VideoCheckOutcome.Completed(listOf("/api/v1/videos/job-1/content?index=0"), 0.2))

        val output = run(tool(), """{"prompt":"A boat at dawn","file_name":"boat"}""")

        assertFalse(output.text, output.isError)
        assertEquals("videos/boat.mp4", GeneratedVideos.pathIn(output.text))
        assertTrue(output.text, output.text.contains("Length: 4 s, 720p"))
        assertTrue(output.text.contains("Size: 24 bytes"))
        assertTrue(output.text.contains("Model: $veoKey"))
        assertTrue(output.text.contains("Cost: $0.2000"))
        assertTrue(File(threadFolder, "videos/boat.mp4").isFile)
        assertFalse(File(threadFolder, "videos/.job-1.part").exists())
        assertNull(PendingVideoJobs(threadFolder).find("job-1"))
    }

    @Test
    fun withoutChoicesTheShortestLengthOfFourSecondsAnd720pAreAsked() {
        run(tool(), """{"prompt":"A boat"}""")

        assertEquals(VideoRequest("openrouter", "google/veo-3.1-lite", "A boat", 4, "720p", null, null), generator.starts.single())
    }

    @Test
    fun theAgentsChoicesAreSentWhenSupported() {
        run(tool(), """{"prompt":"A boat","duration_seconds":8,"resolution":"1080p","aspect_ratio":"9:16","with_audio":false}""")

        assertEquals(VideoRequest("openrouter", "google/veo-3.1-lite", "A boat", 8, "1080p", "9:16", false), generator.starts.single())
    }

    @Test
    fun anUnsupportedValueIsRefusedBeforeAnyRequest() {
        val output = run(tool(), """{"prompt":"A boat","duration_seconds":5}""")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("supported lengths: 4, 6, 8 s"))
        assertTrue(generator.starts.isEmpty())
        assertTrue(generator.checks.isEmpty())
    }

    @Test
    fun withoutFactsTheValuesAreSentUnchecked() {
        run(tool(withFacts = emptyMap()), """{"prompt":"A boat","duration_seconds":5,"resolution":"999p"}""")

        assertEquals(VideoRequest("openrouter", "google/veo-3.1-lite", "A boat", 5, "999p", null, null), generator.starts.single())
    }

    @Test
    fun theFirstMinuteIsPolledEveryTenSecondsAndTheRestEveryTwentyThroughTheInjectedWait() {
        generator.checkOutcomes = (List(9) { working } + VideoCheckOutcome.Completed(listOf("/x"), 0.1)).toMutableList()

        run(tool(), """{"prompt":"A boat"}""")

        val expected = List(6) { 10.seconds } + List(4) { 20.seconds }
        assertEquals(expected, waits)
        assertEquals(10, generator.checks.size)
    }

    @Test
    fun aJobStillRunningWhenThirtySecondsRemainIsHandedOverWithItsIdAndTheNextCall() {
        generator.checkOutcomes = mutableListOf(working)
        val startedAt = clock

        val output = run(tool(), """{"prompt":"A boat"}""")

        assertFalse(output.text, output.isError)
        assertNull(GeneratedVideos.pathIn(output.text))
        assertTrue(output.text, output.text.contains("Job id: job-1"))
        assertTrue(output.text, output.text.contains("Call generate_video with job_id=job-1 to collect it."))
        val elapsed = (clock - startedAt).toDouble() / 1000
        assertTrue("handed over after $elapsed s", elapsed >= 450 && elapsed < 480)
        assertEquals(1, generator.starts.size)
        assertTrue(recordedCosts.isEmpty())
        assertEquals("job-1", PendingVideoJobs(threadFolder).all().single().jobId)
    }

    @Test
    fun theJobIdCallCollectsWithoutASecondStartAndTheCostIsRecordedOnce() {
        generator.checkOutcomes = mutableListOf(working)
        run(tool(), """{"prompt":"A boat","file_name":"boat"}""")
        generator.checkOutcomes = mutableListOf(VideoCheckOutcome.Completed(listOf("/api/v1/videos/job-1/content?index=0"), 0.4))

        val output = run(tool(), """{"job_id":"job-1"}""")

        assertFalse(output.text, output.isError)
        assertEquals("videos/boat.mp4", GeneratedVideos.pathIn(output.text))
        assertEquals(1, generator.starts.size)
        assertEquals(listOf(veoKey to 0.4), recordedCosts)
        assertTrue(PendingVideoJobs(threadFolder).all().isEmpty())
    }

    @Test
    fun aDownloadThatFailedAfterCompletionCanBeCollectedAgainAndIsCountedOnce() {
        generator.downloadOutcome = VideoDownloadOutcome.Failed(VideoFailure.OTHER, "the download broke off")
        val failed = run(tool(), """{"prompt":"A boat"}""")

        assertTrue(failed.isError)
        assertTrue(failed.text, failed.text.contains("made and charged but could not be downloaded"))
        assertTrue(failed.text, failed.text.contains("job_id=job-1"))
        assertEquals(1, recordedCosts.size)

        generator.downloadOutcome = null
        val collected = run(tool(), """{"job_id":"job-1"}""")

        assertEquals("videos/a-boat.mp4", GeneratedVideos.pathIn(collected.text))
        assertEquals(1, recordedCosts.size)
        assertEquals(1, generator.starts.size)
    }

    @Test
    fun aStopDuringPollingLeavesTheJobIdInThePendingFileAndLetsTheStopThrough() {
        try {
            run(tool(throwOnWait = true), """{"prompt":"A boat"}""")
            fail("the Stop must reach the agent loop")
        } catch (stop: CancellationException) {
            // expected
        }

        val job = PendingVideoJobs(threadFolder).all().single()
        assertEquals("job-1", job.jobId)
        assertEquals(veoKey, job.modelKey)
        assertEquals(threadFolder.name, job.threadId)
    }

    @Test
    fun aCallWithoutJobIdIsToldAboutAPendingJobAndStartsNothing() {
        PendingVideoJobs(threadFolder).add(PendingVideoJob("t", "job-0", veoKey, clock - 5 * 60_000L, 4, "720p", "old"))

        val output = run(tool(), """{"prompt":"Another boat"}""")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("job-0"))
        assertTrue(output.text, output.text.contains("job_id=job-0"))
        assertTrue(output.text.contains("new_video=true"))
        assertTrue(generator.starts.isEmpty())
    }

    @Test
    fun newVideoTrueStartsAnotherJobDespiteAPendingOne() {
        PendingVideoJobs(threadFolder).add(PendingVideoJob("t", "job-0", veoKey, clock - 5 * 60_000L, 4, "720p", "old"))

        val output = run(tool(), """{"prompt":"Another boat","new_video":true}""")

        assertFalse(output.text, output.isError)
        assertEquals(1, generator.starts.size)
    }

    @Test
    fun aJobOlderThanAnHourDoesNotBlockANewVideo() {
        PendingVideoJobs(threadFolder).add(PendingVideoJob("t", "job-0", veoKey, clock - 61 * 60_000L, 4, "720p", "old"))

        val output = run(tool(), """{"prompt":"Another boat"}""")

        assertFalse(output.text, output.isError)
    }

    @Test
    fun aJobIdThatThisThreadNeverStartedIsRefused() {
        val output = run(tool(), """{"job_id":"someone-elses"}""")

        assertTrue(output.isError)
        assertTrue(generator.checks.isEmpty())
    }

    @Test
    fun aSecondVideoWithTheSameNameGetsANumber() {
        run(tool(), """{"prompt":"x","file_name":"a"}""")
        generator.startOutcome = VideoStartOutcome.Started("job-2")

        val second = run(tool(), """{"prompt":"x","file_name":"a"}""")

        assertEquals("videos/a (2).mp4", GeneratedVideos.pathIn(second.text))
        assertTrue(File(threadFolder, "videos/a.mp4").isFile)
    }

    @Test
    fun aWebmVideoGetsItsOwnEnding() {
        generator.downloadMediaType = "application/octet-stream"
        generator.downloadBytes = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 1, 2, 3, 4, 5, 6, 7, 8)

        val output = run(tool(), """{"prompt":"x","file_name":"clip.mov"}""")

        assertEquals("videos/clip.webm", GeneratedVideos.pathIn(output.text))
    }

    @Test
    fun aDownloadThatIsNotAVideoIsRefusedAndNothingStays() {
        val htmlGenerator = object : VideoGenerator by generator {
            override suspend fun download(serviceKey: String, contentUrl: String, target: File): VideoDownloadOutcome {
                target.writeBytes("<html>no</html>".toByteArray())
                return VideoDownloadOutcome.Saved("text/html", 15)
            }
        }
        val output = run(
            GenerateVideoTool(htmlGenerator, listOf(veoKey), { veoKey }, facts, { _, _ -> }, { duration -> clock += duration.inWholeMilliseconds }, { clock }),
            """{"prompt":"x"}""",
        )

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("text/html"))
        assertFalse(File(threadFolder, "videos").listFiles().orEmpty().any { it.name.endsWith(".part") || it.extension == "mp4" })
    }

    @Test
    fun noKeyNoCreditAndTheServiceLimitAreSaidPlainly() {
        generator.startOutcome = VideoStartOutcome.Failed(VideoFailure.KEY_PROBLEM, "no OpenRouter key is saved")
        assertTrue(run(tool(), """{"prompt":"x"}""").text.contains("Tell the user to save a working key for openrouter"))

        generator.startOutcome = VideoStartOutcome.Failed(VideoFailure.OUT_OF_CREDIT, "HTTP 402: Insufficient credits")
        val credit = run(tool(), """{"prompt":"x"}""")
        assertTrue(credit.text, credit.text.contains("no credit or quota"))
        assertTrue(credit.text.contains("Insufficient credits"))

        generator.startOutcome = VideoStartOutcome.Failed(VideoFailure.SERVICE_LIMIT, "HTTP 429 from Google AI Studio: quota")
        val limit = run(tool(), """{"prompt":"x"}""")
        assertTrue(limit.text, limit.text.contains("not at the user's account"))
        assertTrue(limit.text.contains("The user's key and credit are fine"))
        assertTrue(limit.text.contains("nothing was charged"))
        assertTrue(PendingVideoJobs(threadFolder).all().isEmpty())
    }

    @Test
    fun aRefusedPromptAndAnOtherErrorKeepTheServicesText() {
        generator.startOutcome = VideoStartOutcome.Failed(VideoFailure.BLOCKED, "HTTP 400: flagged by the safety filter")
        val blocked = run(tool(), """{"prompt":"x"}""")
        assertTrue(blocked.text, blocked.text.contains("refused the prompt: HTTP 400: flagged by the safety filter"))

        generator.startOutcome = VideoStartOutcome.Failed(VideoFailure.OTHER, "HTTP 502: <html>")
        assertTrue(run(tool(), """{"prompt":"x"}""").text.contains("HTTP 502: <html>"))
    }

    @Test
    fun aFailedOrExpiredJobSaysWhyAndIsForgotten() {
        generator.checkOutcomes = mutableListOf(VideoCheckOutcome.JobEnded("failed", "Content policy violation"))
        val failed = run(tool(), """{"prompt":"x"}""")
        assertTrue(failed.isError)
        assertTrue(failed.text, failed.text.contains("failed: Content policy violation"))
        assertTrue(PendingVideoJobs(threadFolder).all().isEmpty())

        generator.checkOutcomes = mutableListOf(VideoCheckOutcome.JobEnded("expired", "the service gave no reason"))
        val expired = run(tool(), """{"prompt":"x"}""")
        assertTrue(expired.text, expired.text.contains("expired: the service gave no reason"))
        assertTrue(recordedCosts.isEmpty())
    }

    @Test
    fun threeFailedQuestionsInARowEndTheWaitButKeepTheJob() {
        generator.checkOutcomes = mutableListOf(VideoCheckOutcome.Failed(VideoFailure.OTHER, "no connection"))

        val output = run(tool(), """{"prompt":"x"}""")

        assertTrue(output.isError)
        assertEquals(3, generator.checks.size)
        assertTrue(output.text, output.text.contains("job_id=job-1"))
        assertEquals("job-1", PendingVideoJobs(threadFolder).all().single().jobId)
    }

    @Test
    fun aRefusedKeyDuringPollingEndsTheWaitAtOnce() {
        generator.checkOutcomes = mutableListOf(VideoCheckOutcome.Failed(VideoFailure.KEY_PROBLEM, "HTTP 401"))

        val output = run(tool(), """{"prompt":"x"}""")

        assertTrue(output.isError)
        assertEquals(1, generator.checks.size)
    }

    @Test
    fun missingPromptNoModelAndAWrongModelAreErrors() {
        assertTrue(run(tool(), """{"duration_seconds":4}""").text.contains("argument prompt is missing"))
        assertTrue(run(tool(modelKeys = emptyList()), """{"prompt":"x"}""").text.contains("no video model is added"))
        val wrong = run(tool(), """{"prompt":"x","model":"openrouter:other/model"}""")
        assertTrue(wrong.text, wrong.text.contains("Use one of: $veoKey, $grokKey"))
        assertTrue(generator.starts.isEmpty())
    }

    @Test
    fun aBareModelIdFindsTheAddedModel() {
        run(tool(), """{"prompt":"x","model":"x-ai/grok-imagine-video-1.5-lite","duration_seconds":6}""")

        assertEquals("x-ai/grok-imagine-video-1.5-lite", generator.starts.single().modelId)
        assertEquals(6, generator.starts.single().durationSeconds)
    }

    @Test
    fun theToolAsksEveryTimeAndStopsItselfBeforeItsLimit() {
        val tool = tool()
        assertEquals(SideEffect.CHANGES, tool.sideEffect)
        assertEquals("generate_video", tool.name)
        assertEquals(8 * 60, tool.timeLimit.inWholeSeconds)
        assertTrue(GenerateVideoTool.HAND_OVER_AFTER.inWholeSeconds == tool.timeLimit.inWholeSeconds - 30)
        val properties: JsonObject = tool.parameterSchema["properties"] as JsonObject
        assertTrue(properties.keys.containsAll(listOf("prompt", "duration_seconds", "resolution", "aspect_ratio", "with_audio", "file_name", "model", "job_id")))
    }
}

/** A few bytes that start like an MP4 file: a box of size 24 and the type "ftyp". */
private fun mp4Bytes(): ByteArray = byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray() + ByteArray(12)
