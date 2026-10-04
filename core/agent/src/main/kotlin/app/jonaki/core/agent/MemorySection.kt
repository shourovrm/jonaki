package app.jonaki.core.agent

/** Who sees a fact: every thread, the threads of one project (D-135), or one thread. */
enum class PromptFactScope {
    GLOBAL,
    PROJECT,
    THREAD,
}

/** One saved fact as the prompt needs it. */
data class PromptFact(
    val id: Long,
    val text: String,
    val scope: PromptFactScope,
    val pinned: Boolean,
    /** Null when the fact has never been in a prompt or a recall result. */
    val lastUsedAtMillis: Long?,
    /**
     * The day the fact was saved or last changed, as the model reads it
     * ("2026-10-04"), so that it can tell which of two facts is newer. It
     * changes only when the fact does, so the prompt cache holds (D-035).
     */
    val savedOn: String? = null,
)

/** The memory part of the system prompt and the facts it holds. */
data class MemorySectionResult(
    val text: String,
    /** Facts in [text]; the caller marks them as used. */
    val includedIds: List<Long>,
)

/**
 * How many characters of facts each scope may put in the prompt. A project
 * gets less than the other two, because its facts are about one piece of
 * work and the rest stays reachable with recall (D-135).
 */
data class MemoryBudget(
    val globalCharacters: Int,
    val projectCharacters: Int,
    val threadCharacters: Int,
) {
    companion object {
        /** About 1,500, 600 and 1,500 tokens at 4 characters per token (D-009, D-135). */
        val CLOUD = MemoryBudget(globalCharacters = 6_000, projectCharacters = 2_400, threadCharacters = 6_000)

        /**
         * A model on the phone reads about 180 prompt tokens a second (D-133), so
         * it gets about 300, 200 and 300 tokens: roughly 8 facts of each kind.
         */
        val LOCAL = MemoryBudget(globalCharacters = 1_200, projectCharacters = 800, threadCharacters = 1_200)
    }
}

/**
 * Builds the memory part of the system prompt (D-009): global facts, the
 * project's facts (D-135) and the thread's facts, each scope within its own
 * budget, in that order, from the facts that change least to those that
 * change most. Which facts get in is
 * decided by pinned first, then most recently used; the lines are then
 * written in id order. So marking the included facts as used, which makes
 * them the most recent, changes neither the choice nor the bytes, and the
 * provider's prompt cache holds until a fact is added, edited or removed
 * (D-005, D-035).
 */
object MemorySection {
    private const val HEADER = "Memory (facts saved earlier; [id] is for the memory tool; the date is when the fact " +
        "was saved or last changed, and where two facts disagree the newer one holds):"
    private const val GLOBAL_HEADING = "All threads:"
    private const val PROJECT_HEADING = "This project:"
    private const val THREAD_HEADING = "This thread:"
    private const val MORE_NOTICE = "More facts are saved; find them with memory recall."

    fun build(facts: List<PromptFact>, budget: MemoryBudget = MemoryBudget.CLOUD): MemorySectionResult {
        val chosenGlobal = choose(facts.filter { fact -> fact.scope == PromptFactScope.GLOBAL }, budget.globalCharacters)
        val chosenProject = choose(facts.filter { fact -> fact.scope == PromptFactScope.PROJECT }, budget.projectCharacters)
        val chosenThread = choose(facts.filter { fact -> fact.scope == PromptFactScope.THREAD }, budget.threadCharacters)
        val chosen = chosenGlobal + chosenProject + chosenThread
        if (chosen.isEmpty()) {
            return MemorySectionResult(text = "", includedIds = emptyList())
        }
        val lines = mutableListOf(HEADER)
        addGroup(lines, GLOBAL_HEADING, chosenGlobal)
        addGroup(lines, PROJECT_HEADING, chosenProject)
        addGroup(lines, THREAD_HEADING, chosenThread)
        if (facts.size > chosen.size) {
            lines += MORE_NOTICE
        }
        return MemorySectionResult(
            text = lines.joinToString("\n"),
            includedIds = chosen.map { fact -> fact.id },
        )
    }

    private fun addGroup(lines: MutableList<String>, heading: String, facts: List<PromptFact>) {
        if (facts.isEmpty()) {
            return
        }
        lines += heading
        lines += facts.map(::lineOf)
    }

    /** The facts that fit the budget, picked by importance, returned in id order. */
    private fun choose(facts: List<PromptFact>, budgetCharacters: Int): List<PromptFact> {
        val byImportance = facts.sortedWith(
            compareByDescending<PromptFact> { fact -> fact.pinned }
                .thenByDescending { fact -> fact.lastUsedAtMillis ?: Long.MIN_VALUE }
                .thenByDescending { fact -> fact.id },
        )
        val chosen = mutableListOf<PromptFact>()
        var usedCharacters = 0
        for (fact in byImportance) {
            // The line and its line break.
            val cost = lineOf(fact).length + 1
            if (usedCharacters + cost > budgetCharacters) {
                continue
            }
            chosen += fact
            usedCharacters += cost
        }
        return chosen.sortedBy { fact -> fact.id }
    }

    /** One line per fact; line breaks inside a fact would break the list, so they become spaces. */
    private fun lineOf(fact: PromptFact): String {
        val date = if (fact.savedOn == null) "" else "(${fact.savedOn}) "
        return "- [${fact.id}] " + date + fact.text.replace('\n', ' ')
    }

    /** A fact as the context sheet lists it: the prompt's line without the id, which only the model needs. */
    fun displayLineOf(fact: PromptFact): String = lineOf(fact).substringAfter("] ")
}
