package app.jonaki.core.agent

import app.jonaki.core.toolapi.SideEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalPolicyTest {
    private fun facts(
        sideEffect: SideEffect,
        isVeryRisky: Boolean = false,
        sendsOut: Boolean = false,
        matchesSettingsRule: Boolean = false,
    ) = CallFacts(sideEffect, isVeryRisky, sendsOut, matchesSettingsRule)

    private fun decide(
        facts: CallFacts,
        mode: ApprovalMode,
        allowAllInThread: Boolean = false,
        readOutsideContent: Boolean = false,
    ): ApprovalVerdict = ApprovalPolicy.decide(facts, mode, allowAllInThread, readOutsideContent)

    private val asksWithAllowAll = ApprovalVerdict.Asks(offersThreadAllowance = true, afterOutsideContent = false)
    private val asksOnlyOnce = ApprovalVerdict.Asks(offersThreadAllowance = false, afterOutsideContent = false)
    private val asksAfterOutsideContent = ApprovalVerdict.Asks(offersThreadAllowance = false, afterOutsideContent = true)

    /** Every side effect in every mode, with no allowance, no rule and no outside content. */
    @Test
    fun theModesDecideEverySideEffect() {
        val expected = mapOf(
            SideEffect.READ_ONLY to listOf(true, true, true),
            SideEffect.CHANGES_APP_DATA to listOf(true, true, true),
            SideEffect.CHANGES_THREAD_FOLDER to listOf(false, true, true),
            SideEffect.CHANGES_REVERSIBLE to listOf(false, true, true),
            SideEffect.CHANGES to listOf(false, false, true),
            SideEffect.NEEDS_USER to listOf(false, false, false),
        )
        assertEquals("every side effect has a row", SideEffect.entries.toSet(), expected.keys)
        for ((sideEffect, runsInAskAutoBypass) in expected) {
            val actual = listOf(ApprovalMode.ASK, ApprovalMode.AUTO, ApprovalMode.BYPASS)
                .map { mode -> decide(facts(sideEffect), mode) == ApprovalVerdict.Runs }
            assertEquals("$sideEffect in Ask, Auto, Bypass", runsInAskAutoBypass, actual)
        }
    }

    @Test
    fun aReversibleChangeRunsInAutoButAsksInAsk() {
        assertEquals(asksWithAllowAll, decide(facts(SideEffect.CHANGES_REVERSIBLE), ApprovalMode.ASK))
        assertEquals(ApprovalVerdict.Runs, decide(facts(SideEffect.CHANGES_REVERSIBLE), ApprovalMode.AUTO))
    }

    @Test
    fun aChangeThatLeavesTheAppAsksInAskAndAutoAndOffersTheAllowance() {
        assertEquals(asksWithAllowAll, decide(facts(SideEffect.CHANGES), ApprovalMode.ASK))
        assertEquals(asksWithAllowAll, decide(facts(SideEffect.CHANGES), ApprovalMode.AUTO))
    }

    @Test
    fun allowAllInThreadLetsEveryOrdinaryChangeRunInEveryMode() {
        val ordinary = listOf(
            SideEffect.CHANGES_THREAD_FOLDER,
            SideEffect.CHANGES_REVERSIBLE,
            SideEffect.CHANGES,
        )
        for (sideEffect in ordinary) {
            for (mode in ApprovalMode.entries) {
                assertEquals("$sideEffect in $mode", ApprovalVerdict.Runs, decide(facts(sideEffect), mode, allowAllInThread = true))
            }
        }
    }

    @Test
    fun allowAllInThreadNeverCoversAVeryRiskyCall() {
        val veryRisky = facts(SideEffect.CHANGES, isVeryRisky = true, sendsOut = true)

        assertEquals(asksOnlyOnce, decide(veryRisky, ApprovalMode.ASK, allowAllInThread = true))
        assertEquals(asksOnlyOnce, decide(veryRisky, ApprovalMode.AUTO, allowAllInThread = true))
    }

    @Test
    fun aVeryRiskyCallOffersOnlyOnceAndDenyBeforeAnyAllowance() {
        assertEquals(asksOnlyOnce, decide(facts(SideEffect.CHANGES, isVeryRisky = true), ApprovalMode.ASK))
    }

    @Test
    fun bypassRunsAVeryRiskyCallThatSendsNothingAfterOutsideContent() {
        val deletesCalendarEvents = facts(SideEffect.CHANGES, isVeryRisky = true, sendsOut = false)

        assertEquals(ApprovalVerdict.Runs, decide(deletesCalendarEvents, ApprovalMode.BYPASS, readOutsideContent = true))
    }

    @Test
    fun aCallThatNeedsTheUserAsksEvenInBypassWithTheAllowanceAndARule() {
        val overTheCap = facts(SideEffect.NEEDS_USER, matchesSettingsRule = true)

        for (mode in ApprovalMode.entries) {
            assertEquals("$mode", asksOnlyOnce, decide(overTheCap, mode, allowAllInThread = true))
        }
    }

    @Test
    fun aSettingsRuleLetsItsActionRunInEveryMode() {
        val ruled = facts(SideEffect.CHANGES, matchesSettingsRule = true)

        for (mode in ApprovalMode.entries) {
            assertEquals("$mode", ApprovalVerdict.Runs, decide(ruled, mode))
        }
    }

    @Test
    fun aSettingsRuleAlsoCoversAVeryRiskyAction() {
        val ruled = facts(SideEffect.CHANGES, isVeryRisky = true, sendsOut = false, matchesSettingsRule = true)

        assertEquals(ApprovalVerdict.Runs, decide(ruled, ApprovalMode.ASK))
    }

    /** The fixed rule: after outside content, a send-out asks in every mode and nothing overrides it. */
    @Test
    fun aSendOutAfterOutsideContentAsksInEveryModeWithAnyAllowanceAndRule() {
        val sendOut = facts(SideEffect.CHANGES, sendsOut = true, matchesSettingsRule = true)

        for (mode in ApprovalMode.entries) {
            assertEquals("$mode", asksAfterOutsideContent, decide(sendOut, mode, allowAllInThread = true, readOutsideContent = true))
        }
    }

    @Test
    fun aSendOutBeforeAnyOutsideContentFollowsTheMode() {
        val sendOut = facts(SideEffect.CHANGES, sendsOut = true)

        assertEquals(asksWithAllowAll, decide(sendOut, ApprovalMode.AUTO, readOutsideContent = false))
        assertEquals(ApprovalVerdict.Runs, decide(sendOut, ApprovalMode.BYPASS, readOutsideContent = false))
    }

    @Test
    fun outsideContentDoesNotMakeACallThatSendsNothingAsk() {
        assertEquals(ApprovalVerdict.Runs, decide(facts(SideEffect.READ_ONLY), ApprovalMode.ASK, readOutsideContent = true))
        assertEquals(ApprovalVerdict.Runs, decide(facts(SideEffect.CHANGES_THREAD_FOLDER), ApprovalMode.AUTO, readOutsideContent = true))
        assertEquals(ApprovalVerdict.Runs, decide(facts(SideEffect.CHANGES_REVERSIBLE, sendsOut = false), ApprovalMode.BYPASS, readOutsideContent = true))
    }

    @Test
    fun theOutsideContentRuleIsPlain() {
        assertTrue(OutsideContent.sendOutNeedsCard(sendsOut = true, threadHasReadOutsideContent = true))
        assertFalse(OutsideContent.sendOutNeedsCard(sendsOut = true, threadHasReadOutsideContent = false))
        assertFalse(OutsideContent.sendOutNeedsCard(sendsOut = false, threadHasReadOutsideContent = true))
        assertFalse(OutsideContent.sendOutNeedsCard(sendsOut = false, threadHasReadOutsideContent = false))
    }
}
