package app.jonaki.feature.memory

/**
 * The memory screen (D-008, D-009). Opened from a thread it shows that
 * thread's facts and the global ones; opened from Settings, only the global
 * ones and every thread's facts waiting for review.
 */
data class MemoryUiState(
    /** Null when opened from Settings or from a project. */
    val threadTitle: String?,
    val threadFacts: List<MemoryFactUi>,
    val globalFacts: List<MemoryFactUi>,
    /** Extracted facts waiting for the user's approval (review mode). */
    val waitingForReview: List<MemoryFactUi>,
    val reviewMode: Boolean,
    /**
     * The project of the thread, or of the project view; null when there is
     * none (D-135). Its facts show between the thread's and the global ones.
     */
    val projectName: String? = null,
    val projectFacts: List<MemoryFactUi> = emptyList(),
    /** Facts background extraction replaced or removed, newest first; shown in a folded section at the bottom. */
    val supersededFacts: List<MemoryFactUi> = emptyList(),
) {
    val isThreadView: Boolean
        get() = threadTitle != null

    /** Opened from a project's menu: its facts and the global ones, no thread. */
    val isProjectView: Boolean
        get() = threadTitle == null && projectName != null

    /** Where a new fact can go, the first one chosen at the start. */
    val addScopes: List<FactScopeUi>
        get() = when {
            isThreadView && projectName != null -> listOf(FactScopeUi.THREAD, FactScopeUi.PROJECT, FactScopeUi.GLOBAL)
            isThreadView -> listOf(FactScopeUi.THREAD, FactScopeUi.GLOBAL)
            isProjectView -> listOf(FactScopeUi.PROJECT, FactScopeUi.GLOBAL)
            else -> listOf(FactScopeUi.GLOBAL)
        }
}

/** Who sees a fact: one thread, the threads of one project (D-135), or every thread. */
enum class FactScopeUi {
    THREAD,
    PROJECT,
    GLOBAL,
}

data class MemoryFactUi(
    val id: Long,
    val text: String,
    val pinned: Boolean,
    val scope: FactScopeUi,
    /** First line of the message the fact came from; null when there is none. */
    val sourcePreview: String?,
    /** The thread a fact waiting for review belongs to, shown when opened from Settings. */
    val threadTitle: String? = null,
    /** When extraction replaced or removed the fact; null for a fact in use. */
    val supersededAtMillis: Long? = null,
)

/** What the user can do with one fact; the app writes it to the database. */
class MemoryActions(
    val onBack: () -> Unit,
    val onAdd: (text: String, scope: FactScopeUi) -> Unit,
    val onEdit: (factId: Long, text: String) -> Unit,
    val onDelete: (factId: Long) -> Unit,
    val onPinChange: (factId: Long, pinned: Boolean) -> Unit,
    /** Moves a thread or project fact to all threads. */
    val onPromote: (factId: Long) -> Unit,
    /** Moves a thread fact to its project (D-135). */
    val onMoveToProject: (factId: Long) -> Unit = {},
    val onOpenSource: (factId: Long) -> Unit,
    val onKeep: (factId: Long) -> Unit,
    /** Puts a superseded fact back in use. */
    val onRestore: (factId: Long) -> Unit = {},
    val onReviewModeChange: (enabled: Boolean) -> Unit,
)

/** Entries of a fact's ⋮ menu, in the order shown. */
enum class FactMenuItem {
    EDIT,
    PIN,
    UNPIN,
    MOVE_TO_PROJECT,
    PROMOTE,
    OPEN_SOURCE,
    DELETE,
}

object FactMenu {
    /**
     * A global fact cannot move up, a thread fact can move to its project
     * when [hasProject], and a fact without a source has no message to open.
     */
    fun itemsFor(fact: MemoryFactUi, hasProject: Boolean = false): List<FactMenuItem> {
        val items = mutableListOf(FactMenuItem.EDIT)
        items += if (fact.pinned) FactMenuItem.UNPIN else FactMenuItem.PIN
        if (fact.scope == FactScopeUi.THREAD && hasProject) {
            items += FactMenuItem.MOVE_TO_PROJECT
        }
        if (fact.scope != FactScopeUi.GLOBAL) {
            items += FactMenuItem.PROMOTE
        }
        if (fact.sourcePreview != null) {
            items += FactMenuItem.OPEN_SOURCE
        }
        items += FactMenuItem.DELETE
        return items
    }
}
