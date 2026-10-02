package app.jonaki.core.agent

/** One saved fact as the prompt needs it. */
data class PromptFact(
    val id: Long,
    val text: String,
    val isGlobal: Boolean,
    val pinned: Boolean,
    /** Null when the fact has never been in a prompt or a recall result. */
    val lastUsedAtMillis: Long?,
)

/** The memory part of the system prompt and the facts it holds. */
data class MemorySectionResult(
    val text: String,
    /** Facts in [text]; the caller marks them as used. */
    val includedIds: List<Long>,
)

/**
 * Builds the memory part of the system prompt (D-009): global facts and the
 * thread's facts, each scope within its own budget. Which facts get in is
 * decided by pinned first, then most recently used; the lines are then
 * written in id order. So marking the included facts as used, which makes
 * them the most recent, changes neither the choice nor the bytes, and the
 * provider's prompt cache holds until a fact is added, edited or removed
 * (D-005, D-035).
 */
object MemorySection {
    /** About 1,500 tokens at 4 characters per token (D-009). */
    const val DEFAULT_BUDGET_CHARACTERS = 6_000

    private const val HEADER = "Memory (facts saved earlier; [id] is for the memory tool):"
    private const val GLOBAL_HEADING = "All threads:"
    private const val THREAD_HEADING = "This thread:"
    private const val MORE_NOTICE = "More facts are saved; find them with memory recall."

    fun build(facts: List<PromptFact>, budgetCharactersPerScope: Int = DEFAULT_BUDGET_CHARACTERS): MemorySectionResult {
        val (globalFacts, threadFacts) = facts.partition { fact -> fact.isGlobal }
        val chosenGlobal = choose(globalFacts, budgetCharactersPerScope)
        val chosenThread = choose(threadFacts, budgetCharactersPerScope)
        if (chosenGlobal.isEmpty() && chosenThread.isEmpty()) {
            return MemorySectionResult(text = "", includedIds = emptyList())
        }
        val lines = mutableListOf(HEADER)
        if (chosenGlobal.isNotEmpty()) {
            lines += GLOBAL_HEADING
            lines += chosenGlobal.map(::lineOf)
        }
        if (chosenThread.isNotEmpty()) {
            lines += THREAD_HEADING
            lines += chosenThread.map(::lineOf)
        }
        val leftOut = facts.size - chosenGlobal.size - chosenThread.size
        if (leftOut > 0) {
            lines += MORE_NOTICE
        }
        return MemorySectionResult(
            text = lines.joinToString("\n"),
            includedIds = (chosenGlobal + chosenThread).map { fact -> fact.id },
        )
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
    private fun lineOf(fact: PromptFact): String = "- [${fact.id}] " + fact.text.replace('\n', ' ')
}
