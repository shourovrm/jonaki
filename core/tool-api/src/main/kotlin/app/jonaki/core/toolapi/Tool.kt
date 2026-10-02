package app.jonaki.core.toolapi

import java.io.File
import kotlin.time.Duration
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/**
 * One capability the model can call. Each tool lives in its own module under
 * tools/ and depends only on this module (D-007). First sketch from the
 * version 1 plan; M2 fixes it with tests.
 */
interface Tool {
    /** Name the model calls, for example "web_search". */
    val name: String

    /** One line in the system prompt. */
    val promptLine: String

    /** Extra guidance, added to the prompt only while the tool is active. */
    val guidelines: List<String>

    /** JSON schema of the arguments, sent only while the tool is active. */
    val parameterSchema: JsonObject

    val sideEffect: SideEffect

    /**
     * The cost of one call. A tool whose actions differ overrides this, so
     * that reading the calendar runs at once while adding to it asks
     * (D-M9-1, proposed); [sideEffect] stays the tool's highest cost.
     */
    fun sideEffectOf(arguments: JsonObject): SideEffect = sideEffect

    val requiredCapabilities: Set<Capability>

    val timeLimit: Duration

    suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput
}

/** Whether a tool only reads, or changes something and so needs approval. */
enum class SideEffect {
    READ_ONLY,
    CHANGES,

    /**
     * Changes only Jonaki's own records, which the user sees in the step
     * track and can edit or undo in the app (memory facts). Runs without an
     * approval card, so that remembering the user's facts does not interrupt
     * every answer (D-034, proposed).
     */
    CHANGES_APP_DATA,
}

/** Optional parts of the app a tool needs before it can run. */
enum class Capability {
    PYTHON,
}

/**
 * What a tool may use while it runs, and nothing else. Cancellation reaches a
 * tool through its coroutine; HTTP calls made with [await] stop with it.
 */
class ToolContext(
    val threadFolder: File,
    val httpClient: OkHttpClient,
    /** The skill library, readable as /skills/ (D-037); null where a run has no skills. */
    skillLibraryFolder: File? = null,
) {
    val paths: ThreadPaths = ThreadPaths(threadFolder)
    val skillPaths: SkillLibraryPaths? = skillLibraryFolder?.let(::SkillLibraryPaths)
    val outputLimiter: OutputLimiter = OutputLimiter(threadFolder)
}

/** Plain text for the model. An error says what failed and what to try next. */
data class ToolOutput(
    val text: String,
    val isError: Boolean,
) {
    companion object {
        fun success(text: String): ToolOutput = ToolOutput(text = text, isError = false)

        fun error(whatFailed: String, whatToTryNext: String): ToolOutput =
            ToolOutput(text = "Error: $whatFailed. $whatToTryNext", isError = true)
    }
}
