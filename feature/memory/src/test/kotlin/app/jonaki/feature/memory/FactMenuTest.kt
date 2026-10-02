package app.jonaki.feature.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class FactMenuTest {
    private val threadFact = MemoryFactUi(id = 1, text = "Thesis due", pinned = false, isGlobal = false, sourcePreview = "My thesis…")

    @Test
    fun threadFactWithASourceOffersEverything() {
        assertEquals(
            listOf(FactMenuItem.EDIT, FactMenuItem.PIN, FactMenuItem.PROMOTE, FactMenuItem.OPEN_SOURCE, FactMenuItem.DELETE),
            FactMenu.itemsFor(threadFact),
        )
    }

    @Test
    fun pinnedGlobalFactWithoutSourceOffersUnpinButNoMoveOrSource() {
        val fact = threadFact.copy(pinned = true, isGlobal = true, sourcePreview = null)
        assertEquals(listOf(FactMenuItem.EDIT, FactMenuItem.UNPIN, FactMenuItem.DELETE), FactMenu.itemsFor(fact))
    }

    @Test
    fun threadViewIsTheOneWithATitle() {
        val state = MemoryUiState(threadTitle = null, threadFacts = emptyList(), globalFacts = emptyList(), waitingForReview = emptyList(), reviewMode = false)
        assertEquals(false, state.isThreadView)
        assertEquals(true, state.copy(threadTitle = "Plan").isThreadView)
    }
}
