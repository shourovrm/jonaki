package app.jonaki.feature.memory

/**
 * The memory screen (D-008, D-009). Opened from a thread it shows that
 * thread's facts and the global ones; opened from Settings, only the global
 * ones and every thread's facts waiting for review.
 */
data class MemoryUiState(
    /** Null when opened from Settings. */
    val threadTitle: String?,
    val threadFacts: List<MemoryFactUi>,
    val globalFacts: List<MemoryFactUi>,
    /** Extracted facts waiting for the user's approval (review mode). */
    val waitingForReview: List<MemoryFactUi>,
    val reviewMode: Boolean,
) {
    val isThreadView: Boolean
        get() = threadTitle != null
}

data class MemoryFactUi(
    val id: Long,
    val text: String,
    val pinned: Boolean,
    val isGlobal: Boolean,
    /** First line of the message the fact came from; null when there is none. */
    val sourcePreview: String?,
    /** The thread a fact waiting for review belongs to, shown when opened from Settings. */
    val threadTitle: String? = null,
)

/** What the user can do with one fact; the app writes it to the database. */
class MemoryActions(
    val onBack: () -> Unit,
    val onAdd: (text: String, isGlobal: Boolean) -> Unit,
    val onEdit: (factId: Long, text: String) -> Unit,
    val onDelete: (factId: Long) -> Unit,
    val onPinChange: (factId: Long, pinned: Boolean) -> Unit,
    val onPromote: (factId: Long) -> Unit,
    val onOpenSource: (factId: Long) -> Unit,
    val onKeep: (factId: Long) -> Unit,
    val onReviewModeChange: (enabled: Boolean) -> Unit,
)

/** Entries of a fact's ⋮ menu, in the order shown. */
enum class FactMenuItem {
    EDIT,
    PIN,
    UNPIN,
    PROMOTE,
    OPEN_SOURCE,
    DELETE,
}

object FactMenu {
    /** A global fact cannot move up, and a fact without a source has no message to open. */
    fun itemsFor(fact: MemoryFactUi): List<FactMenuItem> {
        val items = mutableListOf(FactMenuItem.EDIT)
        items += if (fact.pinned) FactMenuItem.UNPIN else FactMenuItem.PIN
        if (!fact.isGlobal) {
            items += FactMenuItem.PROMOTE
        }
        if (fact.sourcePreview != null) {
            items += FactMenuItem.OPEN_SOURCE
        }
        items += FactMenuItem.DELETE
        return items
    }
}
