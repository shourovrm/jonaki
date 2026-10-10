package app.jonaki.ui

import app.jonaki.feature.onboarding.SetupCard
import app.jonaki.feature.threads.SetupLeft
import app.jonaki.settings.ChatService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupTest {
    @Test
    fun theListShowsOnlyWhatIsStillUndone() {
        val everything = Setup.left(deckSeen = true, listHidden = false, hasModel = false, hasSearchKey = false, notificationsOn = false)
        val onlySearch = Setup.left(deckSeen = true, listHidden = false, hasModel = true, hasSearchKey = false, notificationsOn = true)

        assertEquals(listOf(SetupLeft.MODEL, SetupLeft.WEB_SEARCH, SetupLeft.NOTIFICATIONS), everything)
        assertEquals(listOf(SetupLeft.WEB_SEARCH), onlySearch)
        assertTrue(Setup.left(deckSeen = true, listHidden = false, hasModel = true, hasSearchKey = true, notificationsOn = true).isEmpty())
    }

    @Test
    fun theListStaysAwayBeforeTheCardsAndAfterHide() {
        assertTrue(Setup.left(deckSeen = false, listHidden = false, hasModel = false, hasSearchKey = false, notificationsOn = false).isEmpty())
        assertTrue(Setup.left(deckSeen = true, listHidden = true, hasModel = false, hasSearchKey = false, notificationsOn = false).isEmpty())
    }

    @Test
    fun theModelCardOffersOnlyServicesThatWorkWithAKeyAndEachOnce() {
        val services = Setup.modelServices()

        assertEquals(ChatService.OPENROUTER, services.first())
        assertEquals(services.distinct(), services)
        assertTrue(services.all { service -> service.secret != null })
        assertTrue(ChatService.OLLAMA_LOCAL !in services)
    }

    @Test
    fun aRowOpensItsOwnCard() {
        assertEquals(SetupCard.MODEL, Setup.cardOf(SetupLeft.MODEL))
        assertEquals(SetupCard.SEARCH, Setup.cardOf(SetupLeft.WEB_SEARCH))
        assertEquals(SetupCard.NOTIFICATIONS, Setup.cardOf(SetupLeft.NOTIFICATIONS))
    }

    @Test
    fun onlyAHintThatIsASiteIsShownAsWhereToGetAKey() {
        assertEquals("openrouter.ai", Setup.keySiteOf("openrouter.ai"))
        assertNull(Setup.keySiteOf("Google"))
        assertNull(Setup.keySiteOf("Alibaba Cloud"))
        assertNull(Setup.keySiteOf("localhost:11434"))
    }
}
