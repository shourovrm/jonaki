package app.jonaki.core.agent

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.stringArgument
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * request_tool: the subagent asks for a tool of the thread it was not given.
 * [SubagentLoop] answers the call itself through the [SubagentGate], because
 * the answer changes the subagent's own tool list; this object only gives
 * the model the tool's description.
 */
internal class RequestTool : Tool {
    override val name: String = NAME
    override val promptLine: String = "request_tool: ask for one of the thread's tools you were not given, with a reason"
    override val guidelines: List<String> = listOf(
        "A tool that changes something may need the user's approval; if nobody answers within 3 minutes the request " +
            "is skipped. Then continue with the other parts of the task, or stop and report what you have.",
    )
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("name") {
                put("type", "string")
                put("description", "The tool's name, for example write_file.")
            }
            putJsonObject("reason") {
                put("type", "string")
                put("description", "One sentence the user sees on the approval card.")
            }
        }
        putJsonArray("required") {
            add("name")
            add("reason")
        }
    }
    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 4.minutes

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput =
        ToolOutput.error("request_tool runs only inside a subagent", "Call the tool you need directly.")

    companion object {
        const val NAME = "request_tool"
    }
}

/** ask_parent: one question to the thread's agent, at most [maxQuestions] per subagent (D-015). */
internal class AskParentTool(
    private val asker: ParentAsker,
    private val agentLabel: String,
    private val delegateToolCallId: String,
    /** Adds the answer's cost to the subagent's budget and card. */
    private val onCost: suspend (Double) -> Unit,
    private val maxQuestions: Int = MAX_QUESTIONS,
) : Tool {
    private var questionsAsked = 0

    override val name: String = "ask_parent"
    override val promptLine: String = "ask_parent: ask the agent that gave you the task one question about it (at most twice)"
    override val guidelines: List<String> = listOf(
        "Use ask_parent only for a real ambiguity in the task that you cannot settle yourself; otherwise decide and say what you assumed.",
    )
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("question") {
                put("type", "string")
                put("description", "One short, specific question.")
            }
        }
        putJsonArray("required") { add("question") }
    }
    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 2.minutes

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val question = arguments.stringArgument("question")?.trim()
        if (question.isNullOrEmpty()) {
            return ToolOutput.error("ask_parent needs a question", "Call it again with question set.")
        }
        if (questionsAsked >= maxQuestions) {
            return ToolOutput.error(
                "you have asked $maxQuestions questions already",
                "Decide yourself and say in your answer what you assumed.",
            )
        }
        questionsAsked += 1
        val answer = asker.ask(question, agentLabel, delegateToolCallId)
        answer.costUsd?.let { cost -> onCost(cost) }
        return when (answer) {
            is ParentAnswer.Answered -> ToolOutput.success(answer.text)
            is ParentAnswer.Failed -> ToolOutput.error(
                "the question could not be answered (${answer.message})",
                "Decide yourself and say in your answer what you assumed.",
            )
        }
    }

    companion object {
        const val MAX_QUESTIONS = 2
    }
}

/**
 * notes: a board that the subagents of one delegate call share (D-015). It
 * is a file in the thread folder, so the tool keeps no state; nobody waits
 * on it.
 */
internal class NotesTool(
    private val threadFolder: File,
    /** Relative to the thread folder, for example work/delegations/<call id>/notes.md. */
    private val notesPath: String,
    private val authorLabel: String,
    /** Shared by the subagents of one call, so that two posts never mix. */
    private val lock: Mutex,
) : Tool {
    override val name: String = "notes"
    override val promptLine: String = "notes: post a finding for the other subagents of this task, or read theirs"
    override val guidelines: List<String> = listOf(
        "Post a source or result that the other subagents can reuse, and read the board before you repeat a search.",
    )
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    add("post")
                    add("read")
                }
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "For post: the note, at most $MAX_NOTE_CHARACTERS characters.")
            }
        }
        putJsonArray("required") { add("action") }
    }
    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 10.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val notesFile = File(threadFolder, notesPath)
        return when (arguments.stringArgument("action")?.trim()) {
            "post" -> post(notesFile, arguments.stringArgument("text")?.trim().orEmpty())
            "read" -> read(notesFile)
            else -> ToolOutput.error("notes needs action post or read", "Call it again with action set.")
        }
    }

    private suspend fun post(notesFile: File, text: String): ToolOutput {
        if (text.isEmpty()) {
            return ToolOutput.error("a note needs text", "Call notes again with action post and text.")
        }
        val note = text.take(MAX_NOTE_CHARACTERS)
        lock.withLock {
            withContext(Dispatchers.IO) {
                notesFile.parentFile?.mkdirs()
                notesFile.appendText("**$authorLabel**: $note\n\n")
            }
        }
        return ToolOutput.success("Posted to $notesPath.")
    }

    private suspend fun read(notesFile: File): ToolOutput {
        val text = lock.withLock {
            withContext(Dispatchers.IO) { if (notesFile.exists()) notesFile.readText() else "" }
        }
        return ToolOutput.success(text.ifBlank { "No notes yet." })
    }

    private companion object {
        const val MAX_NOTE_CHARACTERS = 2_000
    }
}
