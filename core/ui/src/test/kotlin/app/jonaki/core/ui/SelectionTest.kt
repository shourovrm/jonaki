package app.jonaki.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {
    @Test
    fun anEmptySelectionHasNoMode() {
        val selection = Selection<String>()
        assertTrue(selection.isEmpty)
        assertEquals(SelectionMode.NONE, selection.mode)
    }

    @Test
    fun oneSelectedItemIsModeOne() {
        val selection = Selection<String>().toggle("a")
        assertEquals(1, selection.count)
        assertEquals(SelectionMode.ONE, selection.mode)
    }

    @Test
    fun twoSelectedItemsAreModeMany() {
        val selection = Selection<String>().toggle("a").toggle("b")
        assertEquals(2, selection.count)
        assertEquals(SelectionMode.MANY, selection.mode)
    }

    @Test
    fun toggleSelectsAndDeselects() {
        val selected = Selection<String>().toggle("a")
        assertTrue("a" in selected)
        val deselected = selected.toggle("a")
        assertFalse("a" in deselected)
        assertTrue(deselected.isEmpty)
    }

    @Test
    fun selectAllTakesTheVisibleItemsAndKeepsEarlierChoices() {
        val selection = Selection<String>().toggle("earlier").selectAll(listOf("a", "b"))
        assertEquals(setOf("earlier", "a", "b"), selection.ids)
    }

    @Test
    fun everythingVisibleIsSelectedOnlyWhenAllVisibleItemsAre() {
        val visible = listOf("a", "b")
        assertFalse(Selection<String>().toggle("a").coversAll(visible))
        assertTrue(Selection<String>().toggle("a").toggle("b").coversAll(visible))
        assertTrue(Selection<String>().toggle("a").toggle("b").toggle("c").coversAll(visible))
    }

    @Test
    fun nothingIsCoveredWhenNothingIsVisible() {
        assertFalse(Selection<String>().coversAll(emptyList()))
    }

    @Test
    fun clearDropsEverything() {
        assertTrue(Selection<String>().selectAll(listOf("a", "b")).clear().isEmpty)
    }

    @Test
    fun prunedDropsItemsThatNoLongerExist() {
        val selection = Selection<String>().selectAll(listOf("a", "b", "c"))
        assertEquals(setOf("a", "c"), selection.pruned(listOf("a", "c", "d")).ids)
    }

    @Test
    fun prunedReturnsTheSameInstanceWhenNothingIsMissing() {
        val selection = Selection<String>().selectAll(listOf("a", "b"))
        assertSame(selection, selection.pruned(listOf("a", "b", "c")))
    }

    @Test
    fun prunedWorksForLongIds() {
        val selection = Selection<Long>().toggle(1L).toggle(2L)
        assertEquals(setOf(2L), selection.pruned(listOf(2L)).ids)
    }
}
