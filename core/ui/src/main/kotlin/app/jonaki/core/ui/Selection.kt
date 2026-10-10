package app.jonaki.core.ui

import androidx.compose.runtime.saveable.Saver

/** How many items are selected, which decides what the top bar offers. */
enum class SelectionMode {
    NONE,
    ONE,
    MANY,
}

/**
 * The items a user has chosen in a list, for deleting several at once. It is
 * immutable: each change returns a new selection. An empty selection means
 * the list is not in selection mode.
 */
data class Selection<T : Any>(val ids: Set<T> = emptySet()) {
    val count: Int
        get() = ids.size

    val isEmpty: Boolean
        get() = ids.isEmpty()

    val mode: SelectionMode
        get() = when (ids.size) {
            0 -> SelectionMode.NONE
            1 -> SelectionMode.ONE
            else -> SelectionMode.MANY
        }

    operator fun contains(id: T): Boolean = id in ids

    fun toggle(id: T): Selection<T> {
        return if (id in ids) Selection(ids - id) else Selection(ids + id)
    }

    /** Adds every visible item; earlier choices stay. */
    fun selectAll(visibleIds: Collection<T>): Selection<T> = Selection(ids + visibleIds)

    fun clear(): Selection<T> = Selection()

    /** True when something is visible and every visible item is selected. */
    fun coversAll(visibleIds: Collection<T>): Boolean {
        return visibleIds.isNotEmpty() && ids.containsAll(visibleIds)
    }

    /**
     * Drops items that are not in [existingIds], such as a thread deleted
     * elsewhere. Returns this same instance when nothing is dropped, so that
     * storing the result does not trigger another update.
     */
    fun pruned(existingIds: Collection<T>): Selection<T> {
        val existing = existingIds.toSet()
        if (existing.containsAll(ids)) {
            return this
        }
        return Selection(ids.intersect(existing))
    }

    companion object {
        /** For `rememberSaveable`, so a selection survives rotation. T must be String or Long. */
        fun <T : Any> saver(): Saver<Selection<T>, ArrayList<T>> = Saver(
            save = { selection -> ArrayList(selection.ids) },
            restore = { saved -> Selection(LinkedHashSet(saved)) },
        )
    }
}
