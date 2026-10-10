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
     * that reading the calendar runs at once while adding to it asks, and
     * the mcp tool's search runs at once while its call asks (D-096, D-117);
     * [sideEffect] stays the tool's highest cost.
     */
    fun sideEffectOf(arguments: JsonObject): SideEffect = sideEffect

    /**
     * True when this call is very risky: it deletes or overwrites something
     * outside the thread folder, or sends a file or data to another app or
     * server. "Allow all in this thread" never covers such a call, so its
     * card offers only Allow once and Deny. Answered per call from the
     * arguments, like [sideEffectOf].
     */
    fun isVeryRiskyOf(arguments: JsonObject): Boolean = false

    /**
     * True when this call sends data out of the app. After the thread has
     * read outside content such a call always asks, in every approval mode
     * (the fixed rule against prompt injection). The default is any call
     * that [SideEffect.CHANGES] something outside Jonaki, so a new tool is
     * covered until its author says otherwise.
     */
    fun sendsOutOf(arguments: JsonObject): Boolean = sideEffectOf(arguments) == SideEffect.CHANGES

    /**
     * The web address this call contacts, or null for a call that contacts
     * none. An address can carry data out in its path and query, so after the
     * thread has read outside content such a call asks unless the address
     * already appeared in the thread (see KnownAddresses in core/agent).
     */
    fun contactedAddressOf(arguments: JsonObject): String? = null

    /**
     * Null when the result is Jonaki's own text. Otherwise the result is
     * outside content: text written by someone else (a web page, a document,
     * another service, a subagent), which reaches the model wrapped as data.
     * The value is a short source for the wrapper, such as a host or a file
     * name; an empty string means the result has no single source.
     */
    fun outsideContentSourceOf(arguments: JsonObject): String? = null

    /**
     * The action of this call as a Settings rule names it, for example
     * "reminder" for the phone tool; null for a tool with one action.
     */
    fun actionOf(arguments: JsonObject): String? = null

    /**
     * Actions that Settings lists for "always allow" rules, as [actionOf]
     * returns them. Empty for a tool with one action.
     */
    val ruleActions: List<String>
        get() = emptyList()

    /**
     * What a rule for an action of this tool must name besides the action,
     * for example "server/tool" for the mcp tool's call; null when the action
     * alone is enough.
     */
    val ruleDetailName: String?
        get() = null

    /** The detail of this call that a rule names, as [ruleDetailName] describes it; null without one. */
    fun ruleDetailOf(arguments: JsonObject): String? = null

    val requiredCapabilities: Set<Capability>

    val timeLimit: Duration

    suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput
}

/** Whether a tool only reads, or changes something and so needs approval. */
enum class SideEffect {
    READ_ONLY,

    /** Changes something outside Jonaki: Downloads, the linked folder, the phone, a server. */
    CHANGES,

    /**
     * Changes something outside Jonaki that the user can undo easily and
     * that sends nothing to another app: a reminder, a copy saved to
     * Downloads/Jonaki. Asks in the Ask mode; the Auto mode runs it without
     * asking.
     */
    CHANGES_REVERSIBLE,

    /**
     * Changes only files inside the thread's own folder. Needs approval like
     * [CHANGES] in the Ask mode; the Auto mode runs it without asking,
     * because nothing leaves the app.
     */
    CHANGES_THREAD_FOLDER,

    /**
     * Changes only Jonaki's own records, which the user sees in the step
     * track and can edit or undo in the app (memory facts). Runs without an
     * approval card, so that remembering the user's facts does not interrupt
     * every answer (D-034, proposed).
     */
    CHANGES_APP_DATA,

    /**
     * Always asks, in every approval mode, and "Allow all in this thread" never covers
     * it: the user decides each time. For costs the user wants to see before
     * they happen, such as subagents beyond the automatic limit (D-137).
     */
    NEEDS_USER,
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
    val skillLibraryFolder: File? = null,
    /**
     * The id of the call being run, set by the agent loop for each call; the
     * delegate tool links its subagents' steps to it (M7). Null outside a call.
     */
    val toolCallId: String? = null,
    /** The folder the threads of this thread's project share, readable and writable as /project/ (D-135); null without a project. */
    val projectFolder: File? = null,
) {
    val paths: ThreadPaths = ThreadPaths(threadFolder, projectFolder)
    val skillPaths: SkillLibraryPaths? = skillLibraryFolder?.let(::SkillLibraryPaths)
    val outputLimiter: OutputLimiter = OutputLimiter(threadFolder)

    /** The same context for one call. */
    fun forCall(callId: String): ToolContext = ToolContext(threadFolder, httpClient, skillLibraryFolder, callId, projectFolder)
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
