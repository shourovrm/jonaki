package app.jonaki.tools.youtubesummarize

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Summarises a public YouTube video. The video model watches the video
 * itself from its URL, so no download happens on the phone (D-012).
 */
class YouTubeSummarizeTool(private val summarizer: VideoSummarizer) : Tool {
    override val name: String = "youtube_summarize"

    override val promptLine: String =
        "youtube_summarize: watch a public YouTube video and answer a prompt about it"

    override val guidelines: List<String> = listOf(
        "Find a video's link with web_search site=youtube.com first when the user names no link.",
        "Pass the user's actual question as prompt, for example \"list the steps\" or \"what does she say about prices\".",
        "A 20-minute video costs about 100,000 input tokens; use start and end to cover only the part needed.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "YouTube link: watch, youtu.be, shorts or live")
            }
            putJsonObject("prompt") {
                put("type", "string")
                put("description", "What to get from the video; default is a summary with key points")
            }
            putJsonObject("start") {
                put("type", "string")
                put("description", "Start of the part to watch, as seconds, mm:ss or h:mm:ss")
            }
            putJsonObject("end") {
                put("type", "string")
                put("description", "End of the part to watch, as seconds, mm:ss or h:mm:ss")
            }
            putJsonObject("low_resolution") {
                put("type", "boolean")
                put("description", "Fewer tokens per frame; use for videos over 30 minutes")
            }
        }
        putJsonArray("required") { add("url") }
    }

    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()

    /** Spike S-3 measured 14 to 21 seconds for a 19-minute video. */
    override val timeLimit: Duration = 180.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val urlText = arguments.text("url")?.trim()
        if (urlText.isNullOrEmpty()) {
            return ToolOutput.error("argument url is missing", "Call youtube_summarize again with a YouTube link.")
        }
        val videoId = YouTubeLinks.videoId(urlText)
        if (videoId == null) {
            return ToolOutput.error("\"$urlText\" is not a YouTube video link", "For other pages use web_fetch.")
        }

        val startSeconds = parseTime(arguments.text("start"))
        val endSeconds = parseTime(arguments.text("end"))
        if (startSeconds == INVALID_TIME || endSeconds == INVALID_TIME) {
            return ToolOutput.error("start or end is not a time", "Write times as seconds, mm:ss or h:mm:ss, for example 1:30.")
        }
        if (startSeconds != null && endSeconds != null && endSeconds <= startSeconds) {
            return ToolOutput.error("end ($endSeconds s) is not after start ($startSeconds s)", "Give an end time later than the start time.")
        }

        val videoUrl = YouTubeLinks.watchUrl(videoId)
        val request = VideoRequest(
            videoUrl = videoUrl,
            prompt = arguments.text("prompt")?.trim()?.ifEmpty { null } ?: DEFAULT_PROMPT,
            startSeconds = startSeconds,
            endSeconds = endSeconds,
            lowMediaResolution = (arguments["low_resolution"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
        return when (val answer = summarizer.summarize(request)) {
            is VideoAnswer.Success -> {
                val text = "Summary of $videoUrl\n\n${answer.text.trim()}"
                ToolOutput.success(context.outputLimiter.limit(text, MAX_OUTPUT_CHARACTERS, name))
            }
            is VideoAnswer.Failed -> failureOutput(answer)
        }
    }

    private fun failureOutput(answer: VideoAnswer.Failed): ToolOutput {
        val hint = when {
            answer.retryable -> "The video service is busy; try again in a minute."
            answer.message.contains("PERMISSION_DENIED") || answer.message.contains("403") ->
                "The video may be private, age-restricted or removed; tell the user or find another video."
            else -> "Tell the user the video could not be summarised and why."
        }
        return ToolOutput.error(answer.message, hint)
    }

    /** Seconds from "90", "1:30" or "1:02:05"; null when absent; [INVALID_TIME] when unreadable. */
    private fun parseTime(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().split(":")
        if (parts.size > 3) return INVALID_TIME
        var seconds = 0
        for (part in parts) {
            val number = part.toIntOrNull() ?: return INVALID_TIME
            if (number < 0) return INVALID_TIME
            seconds = seconds * 60 + number
        }
        return seconds
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val INVALID_TIME = -1
        const val MAX_OUTPUT_CHARACTERS = 12_000
        const val DEFAULT_PROMPT =
            "Summarise this video. Give the main points as a bullet list, then up to three notable quotes " +
                "with their timestamps. Answer in the language of the video unless asked otherwise."
    }
}
