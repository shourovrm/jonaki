package app.jonaki.settings

import app.jonaki.core.agent.ApprovalMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalModesTest {
    @Test
    fun aThreadWithoutItsOwnModeFollowsTheDefault() {
        assertEquals(ApprovalMode.AUTO, ApprovalModes.effective(threadMode = null, defaultMode = ApprovalMode.AUTO))
    }

    @Test
    fun theThreadsOwnModeWins() {
        assertEquals(ApprovalMode.BYPASS, ApprovalModes.effective(threadMode = "BYPASS", defaultMode = ApprovalMode.ASK))
    }

    @Test
    fun anUnknownStoredNameFollowsTheDefault() {
        assertEquals(ApprovalMode.ASK, ApprovalModes.effective(threadMode = "SOMETIMES", defaultMode = ApprovalMode.ASK))
    }

    @Test
    fun aMissingSettingIsAsk() {
        assertEquals(ApprovalMode.ASK, ApprovalModes.fromName(null))
        assertEquals(ApprovalMode.AUTO, ApprovalModes.fromName("AUTO"))
    }
}
