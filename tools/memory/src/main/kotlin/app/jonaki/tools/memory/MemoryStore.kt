package app.jonaki.tools.memory

/**
 * What the memory tool needs from the app's fact storage. The app wires
 * this to the database for one thread, so this module never depends on
 * storage (D-007), and a store cannot reach another thread's facts.
 */
interface MemoryStore {
    /** Saves a fact, or returns the fact already saved with the same text. */
    suspend fun remember(scope: FactScope, text: String): RememberResult

    /** Deletes a global fact, one of this thread's facts or one of its project's. */
    suspend fun forget(factId: Long): ForgetResult

    /** Global, project and this thread's facts whose text contains [query], at most [limit]. */
    suspend fun recall(query: String, limit: Int): List<Fact>
}

enum class FactScope {
    GLOBAL,

    /** Shared by the threads of one project (D-135). */
    PROJECT,
    THREAD,
}

data class Fact(
    val id: Long,
    val scope: FactScope,
    val text: String,
    val pinned: Boolean,
)

sealed interface RememberResult {
    data class Saved(val fact: Fact) : RememberResult

    data class AlreadyKnown(val fact: Fact) : RememberResult
}

sealed interface ForgetResult {
    data class Forgotten(val fact: Fact) : ForgetResult

    /** No such id among the facts this thread can see. */
    data object NotFound : ForgetResult

    /** The user pinned the fact; only the user removes it. */
    data class Pinned(val fact: Fact) : ForgetResult
}
