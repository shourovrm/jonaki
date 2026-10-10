package app.jonaki.run

import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.VideoCheckOutcome
import app.jonaki.core.toolapi.VideoDownloadOutcome
import app.jonaki.core.toolapi.VideoGenerator
import app.jonaki.core.toolapi.VideoRequest
import app.jonaki.core.toolapi.VideoStartOutcome
import app.jonaki.tools.generatevideo.GenerateVideoTool
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoHandOverTest {
    @Test
    fun theJobIdIsReadFromTheRealToolsHandOverText() = runBlocking {
        var clockMillis = 0L
        val neverReady = object : VideoGenerator {
            override suspend fun start(request: VideoRequest) = VideoStartOutcome.Started("job-with.dots_and-dashes")

            override suspend fun check(serviceKey: String, jobId: String) = VideoCheckOutcome.Working("in_progress")

            override suspend fun download(serviceKey: String, contentUrl: String, target: File): VideoDownloadOutcome = error("not reached")
        }
        val tool = GenerateVideoTool(
            generator = neverReady,
            modelKeys = listOf("openrouter:a/b"),
            defaultModelKey = { "openrouter:a/b" },
            wait = { duration: Duration -> clockMillis += duration.inWholeMilliseconds },
            nowMillis = { clockMillis },
        )
        val context = ToolContext(Files.createTempDirectory("thread").toFile(), OkHttpClient())

        val output = tool.run(buildJsonObject { put("prompt", "a boat") }, context)

        assertEquals("job-with.dots_and-dashes", VideoHandOver.jobIdIn(output))
    }

    @Test
    fun aSavedVideoAnErrorAndOtherTextHaveNoJobId() {
        assertNull(VideoHandOver.jobIdIn(ToolOutput.success("Video saved: videos/a.mp4\nLength: 4 s, 720p")))
        assertNull(VideoHandOver.jobIdIn(ToolOutput.success("")))
        assertNull(VideoHandOver.jobIdIn(ToolOutput.success("Call generate_video with job_id=job-1 to collect it.")))
    }

    @Test
    fun anErrorThatMentionsAJobIdIsNotAHandOver() {
        val error = ToolOutput.error(
            "an earlier video job of this thread is still waiting to be collected: job-1 (openrouter:a/b, started 3 min ago)",
            "No new video was started and nothing was charged. Call generate_video with job_id=job-1 to collect it.",
        )

        assertNull(VideoHandOver.jobIdIn(error))
    }

    @Test
    fun aHandOverWithoutTheCollectLineHasNoJobId() {
        assertNull(VideoHandOver.jobIdIn(ToolOutput.success("The video is still being made by openrouter:a/b. Job id: j1.")))
    }
}
