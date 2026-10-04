package app.jonaki.tools.memory

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.intArgument
import app.jonaki.core.toolapi.stringArgument
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Remembers, forgets and recalls facts (D-009). A thread fact stays with its
 * thread; a project fact reaches the threads of the thread's project
 * (D-135); a global fact reaches every thread. Facts already in the prompt's
 * memory section need no recall. [projectName] is the thread's project, or
 * null when it has none, in which case the project scope is not offered.
 */
class MemoryTool(private val store: MemoryStore, private val projectName: String? = null) : Tool {
    override val name: String = "memory"

    override val promptLine: String =
        "memory: remember, forget or recall lasting facts about the user and this thread"

    override val guidelines: List<String> = listOfNotNull(
        "Remember lasting facts the user states (names, preferences, deadlines, decisions), one short sentence each that makes sense alone.",
        "Use scope global for facts true in every thread (the user's name, language, habits); otherwise thread.",
        projectName?.let { name ->
            "This thread is in the project \"$name\": use scope project for facts about the project's work that its other threads need."
        },
        "When you remember a fact, give keywords: up to 6 search words that the fact's text lacks, mainly its key words in the other script " +
            "(English words for a Bangla fact, Bangla words for an English one) and Bangla in Latin letters when the user writes that way.",
        "Facts under Memory in this prompt are already known. Recall takes several words, finds facts holding any of the words, best match first; " +
            "it matches letters, not meaning. When it finds nothing, try a second query with other words or the same words in the other script.",
        "Forget a fact by its id when the user asks or it turned out wrong. Never remember keys, passwords or card numbers.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    add(ACTION_REMEMBER)
                    add(ACTION_FORGET)
                    add(ACTION_RECALL)
                }
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "remember: the fact, one short sentence")
            }
            putJsonObject("keywords") {
                put("type", "string")
                put("description", "remember: up to 6 search words the text lacks, mainly its key words in the other script, separated by spaces")
            }
            putJsonObject("scope") {
                put("type", "string")
                putJsonArray("enum") {
                    add(SCOPE_THREAD)
                    if (projectName != null) {
                        add(SCOPE_PROJECT)
                    }
                    add(SCOPE_GLOBAL)
                }
                put("description", scopeDescription())
            }
            putJsonObject("id") {
                put("type", "integer")
                put("description", "forget: the fact's id, shown as [id]")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "recall: several words; finds facts holding any of them, best match first")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "recall: most facts to return, default $DEFAULT_RECALL_LIMIT")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override val sideEffect: SideEffect = SideEffect.CHANGES_APP_DATA
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 10.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput =
        when (val action = arguments.stringArgument("action")?.trim()?.lowercase()) {
            ACTION_REMEMBER -> remember(arguments)
            ACTION_FORGET -> forget(arguments)
            ACTION_RECALL -> recall(arguments)
            else -> ToolOutput.error(
                if (action.isNullOrEmpty()) "argument action is missing" else "unknown action \"$action\"",
                "Use action remember, forget or recall.",
            )
        }

    private suspend fun remember(arguments: JsonObject): ToolOutput {
        val text = arguments.stringArgument("text")?.trim().orEmpty()
        if (text.isEmpty()) {
            return ToolOutput.error("argument text is missing", "Call memory again with the fact as text.")
        }
        if (text.length > MAX_FACT_LENGTH) {
            return ToolOutput.error(
                "the fact is ${text.length} characters",
                "Keep a fact under $MAX_FACT_LENGTH characters; split it into shorter facts.",
            )
        }
        val scopeText = arguments.stringArgument("scope")?.trim()?.lowercase()
        val scope = when (scopeText) {
            null, "", SCOPE_THREAD -> FactScope.THREAD
            SCOPE_GLOBAL -> FactScope.GLOBAL
            SCOPE_PROJECT -> {
                if (projectName == null) {
                    return ToolOutput.error("this thread is in no project", "Use scope thread or global.")
                }
                FactScope.PROJECT
            }
            else -> return ToolOutput.error("unknown scope \"$scopeText\"", "Use scope ${scopeNames()}.")
        }
        val keywords = arguments.stringArgument("keywords")?.trim().orEmpty()
        return when (val result = store.remember(scope, text, keywords)) {
            is RememberResult.Saved -> {
                val saved = "Remembered fact ${result.fact.id} for ${scopeWords(result.fact.scope)}: ${result.fact.text}"
                ToolOutput.success(if (result.waitsForReview) saved + "\n" + WAITS_FOR_REVIEW_NOTICE else saved)
            }
            is RememberResult.AlreadyKnown ->
                ToolOutput.success("Already remembered as fact ${result.fact.id}: ${result.fact.text}")
        }
    }

    private suspend fun forget(arguments: JsonObject): ToolOutput {
        val factId = arguments.intArgument("id")?.toLong()
            ?: return ToolOutput.error("argument id is missing or not a number", "Give the fact's id, shown as [id] under Memory.")
        return when (val result = store.forget(factId)) {
            is ForgetResult.Forgotten -> ToolOutput.success("Forgot fact $factId: ${result.fact.text}")
            ForgetResult.NotFound -> ToolOutput.error(
                "there is no fact $factId among the facts this thread can see",
                "Use action recall to find the fact's id.",
            )
            is ForgetResult.Pinned -> ToolOutput.error(
                "fact $factId is pinned by the user",
                "Tell the user to unpin or delete it in the memory screen.",
            )
        }
    }

    private suspend fun recall(arguments: JsonObject): ToolOutput {
        val query = arguments.stringArgument("query")?.trim().orEmpty()
        if (query.isEmpty()) {
            return ToolOutput.error("argument query is missing", "Give several words the fact may hold, for example \"thesis deadline\".")
        }
        val limit = (arguments.intArgument("limit") ?: DEFAULT_RECALL_LIMIT).coerceIn(1, MAX_RECALL_LIMIT)
        val facts = store.recall(query, limit)
        if (facts.isEmpty()) {
            return ToolOutput.success(
                "No fact matches \"$query\". Search matches letters, not meaning: try a second query with other words, " +
                    "or the same words in the other script (English or Bangla).",
            )
        }
        val header = if (facts.size == 1) "1 fact matches \"$query\":" else "${facts.size} facts match \"$query\", best first:"
        val lines = facts.map { fact -> "[${fact.id}] (${labelOf(fact)}) ${fact.text}" }
        return ToolOutput.success((listOf(header) + lines).joinToString("\n"))
    }

    private fun scopeWords(scope: FactScope): String = when (scope) {
        FactScope.GLOBAL -> "all threads"
        FactScope.PROJECT -> "the project's threads"
        FactScope.THREAD -> "this thread"
    }

    private fun scopeDescription(): String =
        if (projectName == null) {
            "remember: thread (default) or global for all threads"
        } else {
            "remember: thread (default), project for the project's threads, or global for all threads"
        }

    private fun scopeNames(): String = if (projectName == null) "thread or global" else "thread, project or global"

    private fun labelOf(fact: Fact): String {
        val scope = scopeWords(fact.scope)
        return if (fact.pinned) "$scope, pinned" else scope
    }

    private companion object {
        /** Said when the thread read outside content, whose instructions could have steered what the model saves. */
        const val WAITS_FOR_REVIEW_NOTICE =
            "This fact waits for the user's approval on the Memory screen because this thread read outside content. " +
                "It is not in memory yet; do not rely on it."
        const val ACTION_REMEMBER = "remember"
        const val ACTION_FORGET = "forget"
        const val ACTION_RECALL = "recall"
        const val SCOPE_THREAD = "thread"
        const val SCOPE_GLOBAL = "global"
        const val SCOPE_PROJECT = "project"

        /** A fact is one sentence; a longer text belongs in a file in the thread folder. */
        const val MAX_FACT_LENGTH = 500
        const val DEFAULT_RECALL_LIMIT = 20
        const val MAX_RECALL_LIMIT = 50
    }
}
