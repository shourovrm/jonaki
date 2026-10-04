package app.jonaki.core.agent

import app.jonaki.core.guardapi.ActionVerdict
import app.jonaki.core.guardapi.Guard
import app.jonaki.core.guardapi.ResultVerdict
import app.jonaki.core.model.ToolCall
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.stringArgument
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionBrokerTest {
    private val writer = FakeTool("write_file", sideEffect = SideEffect.CHANGES_THREAD_FOLDER)
    private val reader = FakeTool("read_file", sideEffect = SideEffect.READ_ONLY)
    private val sharer = FakeTool("share_file", sideEffect = SideEffect.CHANGES)

    /** Like the phone tool: per-call costs, one very risky action, one reversible action. */
    private class PhoneLikeTool : Tool by FakeTool("phone", sideEffect = SideEffect.CHANGES) {
        override fun sideEffectOf(arguments: JsonObject): SideEffect = when (arguments.stringArgument("action")) {
            "calendar_list" -> SideEffect.READ_ONLY
            "reminder" -> SideEffect.CHANGES_REVERSIBLE
            else -> SideEffect.CHANGES
        }

        override fun isVeryRiskyOf(arguments: JsonObject): Boolean = arguments.stringArgument("action") == "calendar_delete"

        override fun sendsOutOf(arguments: JsonObject): Boolean = arguments.stringArgument("action") == "calendar_add"

        override fun actionOf(arguments: JsonObject): String? = arguments.stringArgument("action")
    }

    /** D-137: what the user decides each time asks in every mode, and no allowance covers it. */
    @Test
    fun aCallThatNeedsTheUserAsksEvenInBypassAndAfterAllowAll() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_ALL_IN_THREAD)
        val broker = PermissionBroker(approver, approvalMode = { ApprovalMode.BYPASS })
        val delegate = FakeTool("delegate", sideEffect = SideEffect.NEEDS_USER)

        assertTrue(broker.mayRun(delegate, call("1", "delegate")))
        assertTrue(broker.mayRun(delegate, call("2", "delegate")))

        assertEquals(2, approver.requests.size)
        assertFalse(approver.requests.first().offersThreadAllowance)
        assertFalse("the answer to a card that offered no allowance grants none", broker.threadState.allowAllInThread)
    }

    /** A guard that answers every action and every text the same way and counts what it was asked. */
    private class FixedGuard(private val letsActionsRun: Boolean) : Guard {
        var actionsJudged = 0

        override suspend fun judgeAction(userRequest: String, toolName: String, arguments: JsonObject): ActionVerdict {
            actionsJudged += 1
            return if (letsActionsRun) ActionVerdict.MayRunWithoutCard("test") else ActionVerdict.ShowCard("test")
        }

        override suspend fun screenResult(source: String, text: String): ResultVerdict = ResultVerdict(false, null, "test")
    }

    @Test
    fun theGuardLetsAnOrdinaryCallRunInsteadOfShowingACard() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val guard = FixedGuard(letsActionsRun = true)
        val broker = PermissionBroker(approver, guard = guard)

        assertTrue(broker.mayRun(writer, call("1", "write_file")))

        assertTrue(approver.requests.isEmpty())
        assertEquals(1, guard.actionsJudged)
    }

    @Test
    fun aGuardThatIsUnsureLeavesTheCardInPlace() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver, guard = FixedGuard(letsActionsRun = false))

        assertFalse(broker.mayRun(writer, call("1", "write_file")))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun theGuardIsNeverAskedAboutACallThatAlwaysAsks() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_ONCE)
        val guard = FixedGuard(letsActionsRun = true)
        val broker = PermissionBroker(approver, approvalMode = { ApprovalMode.BYPASS }, guard = guard)
        val delegate = FakeTool("delegate", sideEffect = SideEffect.NEEDS_USER)

        assertTrue(broker.mayRun(delegate, call("1", "delegate")))

        assertEquals("the card was shown", 1, approver.requests.size)
        assertEquals("the guard had no say", 0, guard.actionsJudged)
    }

    @Test
    fun readOnlyToolRunsWithoutAsking() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver)

        assertTrue(broker.mayRun(reader, call("1", "read_file")))
        assertTrue(approver.requests.isEmpty())
    }

    @Test
    fun toolThatChangesOnlyAppDataRunsWithoutAsking() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver)
        val memory = FakeTool("memory", sideEffect = SideEffect.CHANGES_APP_DATA)

        assertTrue(broker.mayRun(memory, call("1", "memory")))
        assertTrue(approver.requests.isEmpty())
    }

    @Test
    fun aReadOnlyActionOfAChangingToolRunsWithoutAsking() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver)

        assertTrue(broker.mayRun(PhoneLikeTool(), call("1", "phone", "action" to "calendar_list")))
        assertTrue(approver.requests.isEmpty())
        assertFalse(broker.mayRun(PhoneLikeTool(), call("2", "phone", "action" to "calendar_add")))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun allowOnceAsksAgainNextTime() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_ONCE)
        val broker = PermissionBroker(approver)

        assertTrue(broker.mayRun(writer, call("1", "write_file", "path" to "a.md")))
        assertTrue(broker.mayRun(writer, call("2", "write_file", "path" to "b.md")))
        assertEquals(2, approver.requests.size)
        assertEquals("write_file", approver.requests.first().toolCall.toolName)
        assertTrue(approver.requests.first().offersThreadAllowance)
    }

    @Test
    fun denyBlocksTheCall() = runBlocking {
        val broker = PermissionBroker(FixedApprover(ApprovalDecision.DENY))

        assertFalse(broker.mayRun(writer, call("1", "write_file")))
    }

    @Test
    fun theReminderRunsInAutoWithoutACardAndAsksInAsk() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        var mode = ApprovalMode.AUTO
        val broker = PermissionBroker(approver, approvalMode = { mode })
        val reminder = call("1", "phone", "action" to "reminder")

        assertTrue(broker.mayRun(PhoneLikeTool(), reminder))
        assertTrue(approver.requests.isEmpty())
        mode = ApprovalMode.ASK
        assertFalse(broker.mayRun(PhoneLikeTool(), reminder))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun aModeChangeDuringARunAppliesToTheNextCall() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        var mode = ApprovalMode.ASK
        val broker = PermissionBroker(approver, approvalMode = { mode })

        assertFalse(broker.mayRun(writer, call("1", "write_file")))
        mode = ApprovalMode.AUTO
        assertTrue(broker.mayRun(writer, call("2", "write_file")))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun allowAllInThreadAnswersOnceAndThenEveryOrdinaryToolRunsWithoutACard() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_ALL_IN_THREAD)
        val saved = mutableListOf<Pair<Boolean, Boolean>>()
        val state = ThreadApprovalState(save = { allowAll, readOutside -> saved += allowAll to readOutside })
        val broker = PermissionBroker(approver, state)

        assertTrue(broker.mayRun(writer, call("1", "write_file")))
        assertTrue(broker.mayRun(PhoneLikeTool(), call("2", "phone", "action" to "calendar_add")))
        assertTrue(broker.mayRun(writer, call("3", "write_file")))

        assertEquals(1, approver.requests.size)
        assertTrue(state.allowAllInThread)
        assertEquals("saved once, so it survives a restart", listOf(true to false), saved)
    }

    @Test
    fun allowAllRestoredFromStorageSkipsCardsFromTheFirstCall() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver, ThreadApprovalState(allowAllInThread = true))

        assertTrue(broker.mayRun(writer, call("1", "write_file")))
        assertTrue(approver.requests.isEmpty())
    }

    @Test
    fun withdrawingTheAllowanceMakesTheNextCallAskAgain() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val state = ThreadApprovalState(allowAllInThread = true)
        val broker = PermissionBroker(approver, state)

        assertTrue(broker.mayRun(writer, call("1", "write_file")))
        state.withdrawAllowAll()
        assertFalse(broker.mayRun(writer, call("2", "write_file")))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun aVeryRiskyCallAsksEvenWithAllowAllAndItsCardOffersOnlyOnceAndDeny() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_ONCE)
        val broker = PermissionBroker(approver, ThreadApprovalState(allowAllInThread = true))
        val deleteEvents = call("1", "phone", "action" to "calendar_delete")

        assertTrue(broker.mayRun(PhoneLikeTool(), deleteEvents))

        val request = approver.requests.single()
        assertFalse(request.offersThreadAllowance)
        assertFalse(request.afterOutsideContent)
    }

    @Test
    fun anAnswerAllowAllOnAVeryRiskyCardCountsAsOnce() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_ALL_IN_THREAD)
        val broker = PermissionBroker(approver)

        assertTrue(broker.mayRun(PhoneLikeTool(), call("1", "phone", "action" to "calendar_delete")))
        assertFalse(broker.threadState.allowAllInThread)
    }

    @Test
    fun sharingToAnotherAppAsksEvenWithAllowAll() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val share = FakeTool("share_file", sideEffect = SideEffect.CHANGES, veryRisky = true)
        val broker = PermissionBroker(approver, ThreadApprovalState(allowAllInThread = true))

        assertFalse(broker.mayRun(share, call("1", "share_file")))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun aSendOutAsksInBypassOnceTheThreadHasReadOutsideContent() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver, approvalMode = { ApprovalMode.BYPASS })
        val addEvent = call("1", "phone", "action" to "calendar_add")

        assertTrue("before outside content, bypass runs it", broker.mayRun(PhoneLikeTool(), addEvent))
        broker.outsideContentWasRead()
        assertFalse("after outside content it asks", broker.mayRun(PhoneLikeTool(), addEvent))

        val request = approver.requests.single()
        assertTrue(request.afterOutsideContent)
        assertFalse(request.offersThreadAllowance)
    }

    @Test
    fun theOutsideContentFactIsSavedOnceAndRestoredWithTheThread() = runBlocking {
        val saved = mutableListOf<Pair<Boolean, Boolean>>()
        val state = ThreadApprovalState(save = { allowAll, readOutside -> saved += allowAll to readOutside })
        val broker = PermissionBroker(FixedApprover(ApprovalDecision.ALLOW_ONCE), state)

        broker.outsideContentWasRead()
        broker.outsideContentWasRead()

        assertEquals(listOf(false to true), saved)
        val restored = ThreadApprovalState(readOutsideContent = true)
        assertTrue(restored.readOutsideContent)
    }

    @Test
    fun aSettingsRuleLetsOneActionRunButNotTheNextActionOfTheSameTool() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val rules = listOf(ApprovalRule("phone", action = "calendar_add"))
        val broker = PermissionBroker(approver, settingsRules = { rules })

        assertTrue(broker.mayRun(PhoneLikeTool(), call("1", "phone", "action" to "calendar_add")))
        assertFalse(broker.mayRun(PhoneLikeTool(), call("2", "phone", "action" to "notify")))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun aSettingsRuleNamingAnotherToolDoesNotMatch() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver, settingsRules = { listOf(ApprovalRule("schedule", action = "calendar_add")) })

        assertFalse(broker.mayRun(PhoneLikeTool(), call("1", "phone", "action" to "calendar_add")))
    }

    @Test
    fun aSettingsRuleNeverOverridesTheOutsideContentRule() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val rules = listOf(ApprovalRule("phone", action = "calendar_add"))
        val broker = PermissionBroker(approver, ThreadApprovalState(readOutsideContent = true), settingsRules = { rules })

        assertFalse(broker.mayRun(PhoneLikeTool(), call("1", "phone", "action" to "calendar_add")))
        assertTrue(approver.requests.single().afterOutsideContent)
    }

    @Test
    fun aSettingsRuleNeverOverridesTheSubagentCap() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val delegate = FakeTool("delegate", sideEffect = SideEffect.NEEDS_USER)
        val broker = PermissionBroker(approver, settingsRules = { listOf(ApprovalRule("delegate")) })

        assertFalse(broker.mayRun(delegate, call("1", "delegate")))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun rulesAreReadBeforeEveryCallSoSettingsChangesApplyAtOnce() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        var rules = emptyList<ApprovalRule>()
        val broker = PermissionBroker(approver, settingsRules = { rules })

        assertFalse(broker.mayRun(writer, call("1", "write_file")))
        rules = listOf(ApprovalRule("write_file"))
        assertTrue(broker.mayRun(writer, call("2", "write_file")))
    }

    @Test
    fun aRuleForAnMcpCallNamesTheServerAndTheTool() = runBlocking {
        val mcp = object : Tool by FakeTool("mcp", sideEffect = SideEffect.CHANGES) {
            override fun actionOf(arguments: JsonObject): String? = arguments.stringArgument("action")

            override fun ruleDetailOf(arguments: JsonObject): String =
                "${arguments.stringArgument("server")}/${arguments.stringArgument("tool")}"
        }
        val approver = FixedApprover(ApprovalDecision.DENY)
        val rules = listOf(ApprovalRule("mcp", action = "call", detail = "notes/add"))
        val broker = PermissionBroker(approver, settingsRules = { rules })

        assertTrue(broker.mayRun(mcp, call("1", "mcp", "action" to "call", "server" to "notes", "tool" to "add")))
        assertFalse(broker.mayRun(mcp, call("2", "mcp", "action" to "call", "server" to "notes", "tool" to "delete")))
        assertFalse(broker.mayRun(mcp, call("3", "mcp", "action" to "call", "server" to "mail", "tool" to "add")))
    }

    @Test
    fun readOnlyAndAppDataNeverAskInAnyMode() {
        for (mode in ApprovalMode.entries) {
            assertFalse(ApprovalMode.needsApproval(SideEffect.READ_ONLY, mode))
            assertFalse(ApprovalMode.needsApproval(SideEffect.CHANGES_APP_DATA, mode))
        }
    }

    @Test
    fun anUnreadableArgumentStringStillGetsAnAnswer() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver)

        assertFalse(broker.mayRun(sharer, ToolCall("1", "share_file", "not json")))
    }
}
