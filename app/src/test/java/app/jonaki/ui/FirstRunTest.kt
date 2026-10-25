package app.jonaki.ui

import app.jonaki.feature.onboarding.SetupCard
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstRunTest {
    @Test
    fun theFirstRunCardsAreModelSearchAndNotificationsOnly() {
        // Tools are all on and the chat asks before risky actions, so neither has a first-run card (D-193).
        assertEquals(listOf(SetupCard.MODEL, SetupCard.SEARCH, SetupCard.NOTIFICATIONS), SetupCard.entries.toList())
    }

    @Test
    fun aSavedCardIsRestoredByName() {
        assertEquals(SetupCard.SEARCH, restoredSetupCard("SEARCH"))
    }

    @Test
    fun aSavedCardThatNoLongerExistsRestoresToTheFirstCard() {
        // A place saved before the update can name the removed Approvals card.
        assertEquals(SetupCard.MODEL, restoredSetupCard("APPROVALS"))
    }
}
