package app.jonaki.guard

import app.jonaki.core.guardapi.NoGuard
import app.jonaki.guards.jev.JevGuard
import app.jonaki.guards.jev.JevThresholds
import app.jonaki.settings.JevStrictness
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardFactoryTest {
    private fun factory(skipsCards: Boolean, screens: Boolean, key: String?) = GuardFactory(
        options = { JevOptions(skipsCards, screens, JevStrictness.BALANCED) },
        openRouterKey = { key },
        httpClient = OkHttpClient(),
    )

    @Test
    fun bothJobsOnAndKeyPresentGivesTheJevGuardItself() {
        assertTrue(factory(skipsCards = true, screens = true, key = "sk-or-test").create() is JevGuard)
    }

    @Test
    fun oneJobOnGivesAGuardThatDoesOnlyThatJob() {
        assertTrue(factory(skipsCards = true, screens = false, key = "sk-or-test").create() is OneJobGuard)
        assertTrue(factory(skipsCards = false, screens = true, key = "sk-or-test").create() is OneJobGuard)
    }

    @Test
    fun bothJobsOffGivesNoGuardEvenWithAKey() {
        assertSame(NoGuard, factory(skipsCards = false, screens = false, key = "sk-or-test").create())
    }

    @Test
    fun missingOrBlankKeyGivesNoGuardEvenWithBothJobsOn() {
        assertSame(NoGuard, factory(skipsCards = true, screens = true, key = null).create())
        assertSame(NoGuard, factory(skipsCards = true, screens = true, key = "  ").create())
    }

    @Test
    fun eachStrictnessHasItsOwnThresholds() {
        assertEquals(JevThresholds.CAREFUL, GuardFactory.thresholdsOf(JevStrictness.CAREFUL))
        assertEquals(JevThresholds.BALANCED, GuardFactory.thresholdsOf(JevStrictness.BALANCED))
        assertEquals(JevThresholds.RELAXED, GuardFactory.thresholdsOf(JevStrictness.RELAXED))
    }
}
