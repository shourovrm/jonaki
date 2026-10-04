package app.jonaki.memory

import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** One message as background extraction reads it. */
data class ExtractionMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
)

/** What the model asked for, before it is checked against the saved facts. */
sealed interface ExtractionOperation {
    /**
     * [forProject] is true when the model marked the fact "scope":"project" (D-135),
     * [forGlobal] when it marked it "scope":"global". [keywords] are already cleaned.
     */
    data class Add(
        val text: String,
        val source: String?,
        val forProject: Boolean = false,
        val forGlobal: Boolean = false,
        val keywords: String = "",
    ) : ExtractionOperation

    data class Update(val factId: Long, val text: String, val keywords: String = "") : ExtractionOperation

    data class Delete(val factId: Long) : ExtractionOperation
}

sealed interface ParsedExtraction {
    /** [ignored] counts operations too broken to use (no text, no id, unknown op). */
    data class Operations(val operations: List<ExtractionOperation>, val ignored: Int) : ParsedExtraction

    data class Failed(val reason: String) : ParsedExtraction
}

/**
 * [forProject] is true for a fact the thread's project shares (D-135),
 * [forGlobal] for a fact proposed for every thread, which always waits for the user's review.
 */
data class NewFact(
    val text: String,
    val sourceMessageId: String?,
    val forProject: Boolean = false,
    val forGlobal: Boolean = false,
    val keywords: String = "",
)

data class FactUpdate(val factId: Long, val text: String, val keywords: String = "")

/** The changes to make to one thread's facts and its project's. */
data class ExtractionPlan(
    val adds: List<NewFact>,
    val updates: List<FactUpdate>,
    val deletes: List<Long>,
    /** Operations dropped as broken, duplicate, unknown or not allowed. */
    val skipped: Int,
    /** Why nothing could be read from the model's answer; null when it was read. */
    val failure: String? = null,
) {
    val isEmpty: Boolean
        get() = adds.isEmpty() && updates.isEmpty() && deletes.isEmpty()
}

/**
 * Background memory extraction (D-009): the prompt for the cheap model, the
 * parser for its JSON answer and the check of each operation against the
 * thread's saved facts. Pure functions; [MemoryExtractor] does the calls.
 */
object MemoryExtraction {
    /** Same limit as the memory tool's. */
    private const val MAX_FACT_LENGTH = 500
    private const val MAX_USER_MESSAGE_LENGTH = 3_000

    /** Answers are long and rarely hold the user's facts, so less of them is sent. */
    private const val MAX_ASSISTANT_MESSAGE_LENGTH = 1_200

    const val SYSTEM_PROMPT = """You keep the memory of one chat thread: short lasting facts that help in later conversations.
Read the new messages and answer with JSON only, no other text, in this form:
{"operations":[{"op":"add","text":"...","keywords":"...","source":"m1"},{"op":"update","id":12,"text":"...","keywords":"..."},{"op":"delete","id":7}]}
Rules:
- Add only lasting facts: the user's goals, preferences, decisions, names, dates, constraints and plans. Not questions, small talk or what an answer explained.
- Write each fact as one short sentence that makes sense alone, in the language the user wrote in. Turn relative dates into dates using the time in brackets.
- source is the label of the message the fact comes from.
- keywords (optional, for add and update): up to 6 search words that the fact's text lacks, mainly its key words in the other script (English words for a Bangla fact, Bangla words for an English one), and Bangla in Latin letters when the user writes that way. Leave it out when nothing useful comes to mind.
- Update a thread fact when the new messages change it; delete one the user says is wrong or no longer true.
- Never add what the thread facts or global facts already say. Global facts cannot be changed here.
- Never store keys, passwords or card numbers.
- When nothing should change, answer {"operations":[]}."""

    /** Added to [SYSTEM_PROMPT] for a thread in a project (D-135). */
    private const val PROJECT_RULE = """- This thread belongs to a project. Add "scope":"project" to a new fact about the project's work that the project's other threads need (its goals, decisions, names, data, deadlines). Other facts get no scope. Project facts can be updated and deleted like thread facts."""

    /**
     * Added to [SYSTEM_PROMPT] when the user allows global proposals. The fact reaches
     * every thread, so the app holds it for the user's review whatever the review mode is.
     */
    private const val GLOBAL_RULE = """- Add "scope":"global" to a new fact that is a lasting fact about the user and holds in every thread (name, language, where they live, standing preferences). Facts about one thread's work get no scope. The user approves a global fact before it is used."""

    /**
     * The instructions for one thread: [SYSTEM_PROMPT], plus the project rule when the thread
     * has a project and the global rule when [allowGlobalFacts].
     */
    fun systemPrompt(inProject: Boolean, allowGlobalFacts: Boolean = true): String {
        val rules = mutableListOf(SYSTEM_PROMPT)
        if (inProject) {
            rules += PROJECT_RULE
        }
        if (allowGlobalFacts) {
            rules += GLOBAL_RULE
        }
        return rules.joinToString("\n")
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun userPrompt(
        messages: List<ExtractionMessage>,
        threadFacts: List<MemoryEntity>,
        globalFacts: List<MemoryEntity>,
        projectFacts: List<MemoryEntity>? = null,
    ): String {
        val messageBlocks = messages.mapIndexed { index, message ->
            val speaker = if (message.isUser) "User" else "Assistant"
            val limit = if (message.isUser) MAX_USER_MESSAGE_LENGTH else MAX_ASSISTANT_MESSAGE_LENGTH
            "[${labelOf(index)}] $speaker: ${cut(message.text.trim(), limit)}"
        }
        // A thread without a project gets no project block, so its request stays as before.
        val projectBlock = if (projectFacts == null) "" else "Project facts:\n" + factLines(projectFacts) + "\n\n"
        return "Global facts (read only):\n" + factLines(globalFacts) + "\n\n" +
            projectBlock +
            "Thread facts:\n" + factLines(threadFacts) + "\n\n" +
            "New messages:\n" + messageBlocks.joinToString("\n\n")
    }

    fun parse(modelOutput: String): ParsedExtraction {
        val jsonText = jsonPart(modelOutput) ?: return ParsedExtraction.Failed("no JSON in the answer")
        val root = try {
            json.parseToJsonElement(jsonText)
        } catch (error: SerializationException) {
            return ParsedExtraction.Failed("the JSON is broken: ${error.message?.lineSequence()?.firstOrNull()}")
        }
        val operationElements = when (root) {
            is JsonArray -> root
            is JsonObject -> root["operations"] as? JsonArray
                ?: return ParsedExtraction.Failed("the JSON has no operations list")
            else -> return ParsedExtraction.Failed("the JSON is neither an object nor a list")
        }
        val operations = operationElements.mapNotNull(::operationOf)
        return ParsedExtraction.Operations(operations, ignored = operationElements.size - operations.size)
    }

    /**
     * [projectFacts] is null for a thread without a project; then a fact
     * marked for the project stays with the thread. A fact marked global
     * stays with the thread too when [allowGlobalFacts] is false.
     */
    fun plan(
        parsed: ParsedExtraction,
        threadFacts: List<MemoryEntity>,
        globalFacts: List<MemoryEntity>,
        messages: List<ExtractionMessage>,
        projectFacts: List<MemoryEntity>? = null,
        allowGlobalFacts: Boolean = true,
    ): ExtractionPlan {
        if (parsed is ParsedExtraction.Failed) {
            return ExtractionPlan(emptyList(), emptyList(), emptyList(), skipped = 0, failure = parsed.reason)
        }
        val operations = (parsed as ParsedExtraction.Operations).operations
        val inProject = projectFacts != null
        val changeableFactsById = (threadFacts + projectFacts.orEmpty()).associateBy { fact -> fact.id }
        val knownTexts = (threadFacts + globalFacts + projectFacts.orEmpty())
            .map { fact -> FactText.normalized(fact.text) }
            .toMutableSet()
        val touchedIds = mutableSetOf<Long>()
        val adds = mutableListOf<NewFact>()
        val updates = mutableListOf<FactUpdate>()
        val deletes = mutableListOf<Long>()
        var skipped = parsed.ignored

        for (operation in operations) {
            when (operation) {
                is ExtractionOperation.Add -> {
                    val isNew = knownTexts.add(FactText.normalized(operation.text))
                    if (isNew) {
                        adds += NewFact(
                            text = operation.text,
                            sourceMessageId = sourceMessageIdOf(operation.source, messages),
                            forProject = inProject && operation.forProject,
                            forGlobal = allowGlobalFacts && operation.forGlobal,
                            keywords = operation.keywords,
                        )
                    } else {
                        skipped += 1
                    }
                }
                is ExtractionOperation.Update -> {
                    val fact = changeableFact(operation.factId, changeableFactsById, touchedIds)
                    val changesText = fact != null && FactText.normalized(fact.text) != FactText.normalized(operation.text)
                    if (fact != null && changesText) {
                        touchedIds += fact.id
                        knownTexts += FactText.normalized(operation.text)
                        updates += FactUpdate(fact.id, operation.text, operation.keywords)
                    } else {
                        skipped += 1
                    }
                }
                is ExtractionOperation.Delete -> {
                    val fact = changeableFact(operation.factId, changeableFactsById, touchedIds)
                    if (fact != null) {
                        touchedIds += fact.id
                        deletes += fact.id
                    } else {
                        skipped += 1
                    }
                }
            }
        }
        return ExtractionPlan(adds, updates, deletes, skipped)
    }

    /**
     * A thread or project fact extraction may change: not pinned, not
     * written by the user, and not already changed by an earlier operation
     * of this answer.
     */
    private fun changeableFact(factId: Long, changeableFactsById: Map<Long, MemoryEntity>, touchedIds: Set<Long>): MemoryEntity? {
        val fact = changeableFactsById[factId] ?: return null
        val protectedByUser = fact.pinned || fact.origin == MemoryOrigin.USER
        if (protectedByUser || factId in touchedIds) {
            return null
        }
        return fact
    }

    private fun operationOf(element: JsonElement): ExtractionOperation? {
        val fields = element as? JsonObject ?: return null
        val kind = fields.text("op")?.trim()?.lowercase()
        val text = fields.text("text")?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_FACT_LENGTH }
        val factId = fields.number("id")
        val keywords = FactKeywords.cleaned(fields.text("keywords"))
        return when (kind) {
            "add" -> text?.let {
                val scope = fields.text("scope")?.trim()?.lowercase()
                ExtractionOperation.Add(
                    text = it,
                    source = fields.text("source")?.trim(),
                    forProject = scope == "project",
                    forGlobal = scope == "global",
                    keywords = keywords,
                )
            }
            "update" -> if (text != null && factId != null) ExtractionOperation.Update(factId, text, keywords) else null
            "delete" -> factId?.let { ExtractionOperation.Delete(it) }
            else -> null
        }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** Ids arrive as 12 or "12". */
    private fun JsonObject.number(key: String): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.longOrNull ?: primitive.contentOrNull?.trim()?.toLongOrNull()
    }

    /** The JSON inside an answer that may wrap it in a code fence or a sentence. */
    private fun jsonPart(modelOutput: String): String? {
        val objectStart = modelOutput.indexOf('{')
        val arrayStart = modelOutput.indexOf('[')
        val startsWithArray = arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)
        val start = if (startsWithArray) arrayStart else objectStart
        val end = if (startsWithArray) modelOutput.lastIndexOf(']') else modelOutput.lastIndexOf('}')
        if (start < 0 || end <= start) {
            return null
        }
        return modelOutput.substring(start, end + 1)
    }

    private fun labelOf(index: Int): String = "m${index + 1}"

    private fun sourceMessageIdOf(label: String?, messages: List<ExtractionMessage>): String? {
        val index = label?.removePrefix("m")?.toIntOrNull()?.minus(1) ?: return null
        return messages.getOrNull(index)?.id
    }

    private fun factLines(facts: List<MemoryEntity>): String {
        if (facts.isEmpty()) {
            return "(none)"
        }
        return facts.joinToString("\n") { fact -> "- [${fact.id}] ${fact.text}" }
    }

    private fun cut(text: String, limit: Int): String = if (text.length <= limit) text else text.take(limit) + "…"
}
