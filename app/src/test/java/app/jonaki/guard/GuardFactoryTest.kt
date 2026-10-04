package app.jonaki.guard

import app.jonaki.core.guardapi.NoGuard
import app.jonaki.guards.jev.JevGuard
import okhttp3.OkHttpClient
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardFactoryTest {
    private fun factory(isOn: Boolean, key: String?) = GuardFactory({ isOn }, { key }, OkHttpClient())

    @Test
    fun settingOnAndKeyPresentGivesTheJevGuard() {
        assertTrue(factory(isOn = true, key = "sk-or-test").create() is JevGuard)
    }

    @Test
    fun settingOffGivesNoGuardEvenWithAKey() {
        assertSame(NoGuard, factory(isOn = false, key = "sk-or-test").create())
    }

    @Test
    fun missingOrBlankKeyGivesNoGuardEvenWithSettingOn() {
        assertSame(NoGuard, factory(isOn = true, key = null).create())
        assertSame(NoGuard, factory(isOn = true, key = "  ").create())
    }
}
