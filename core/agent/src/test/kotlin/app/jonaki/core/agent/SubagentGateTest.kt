package app.jonaki.core.agent

import app.jonaki.core.toolapi.SideEffect
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentGateTest {
    private val writer = FakeTool("write_file", sideEffect = SideEffect.CHANGES_THREAD_FOLDER)
    private val sharer = FakeTool("share_file", sideEffect = SideEffect.CHANGES)
    private val reader = FakeTool("read_file")
    private val clock = VirtualClock()

    private fun gate(approver: ApprovalRequester, mode: ApprovalMode = ApprovalMode.ASK, allowed: Set<String> = emptySet()) =
        SubagentGate(PermissionBroker(approver, allowed, approvalMode = { mode }), agentLabel = "researcher 1", timer = clock)

    private suspend fun settle() {
        repeat(20) { yield() }
    }

    @Test
    fun anUnansweredCardCountsAsSkippedAfterThreeMinutes() = runBlocking {
        val approver = WaitingApprover()
        val gate = gate(approver)

        val answer = async { gate.check(writer, call("s1/c1", "write_file", "path" to "work/a.md")) }
        settle()
        clock.advanceBy(2.minutes + 59.seconds)
        settle()
        assertFalse(answer.isCompleted)

        clock.advanceBy(1.seconds)
        assertEquals(GateAnswer.SKIPPED, answer.await())
        assertEquals(listOf(3.minutes), clock.requestedWaits)
        // The card leaves the chat when the wait ends.
        assertEquals(1, approver.withdrawnCards)
        assertEquals(0, clock.pendingWaits)
    }

    @Test
    fun anAnswerBeforeThreeMinutesStopsTheTimer() = runBlocking {
        val approver = WaitingApprover()
        val gate = gate(approver)

        val answer = async { gate.check(writer, call("s1/c1", "write_file")) }
        settle()
        clock.advanceBy(1.minutes)
        approver.answer(ApprovalDecision.ALLOW_ONCE)

        assertEquals(GateAnswer.ALLOWED, answer.await())
        settle()
        assertEquals(0, clock.pendingWaits)
        assertEquals(0, approver.withdrawnCards)
    }

    @Test
    fun theCardNamesTheSubagentAndTheReason() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val gate = gate(approver)

        assertEquals(GateAnswer.DENIED, gate.grant(sharer, call("s1/c2", "request_tool"), reason = "save the report"))
        val request = approver.requests.single()
        assertEquals("share_file", request.toolName)
        assertEquals(SubagentAsk("researcher 1", "save the report"), request.subagent)
    }

    @Test
    fun allowForTaskStopsAskingForThatTool() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_FOR_TASK)
        val gate = gate(approver)

        assertEquals(GateAnswer.ALLOWED, gate.check(writer, call("s1/c1", "write_file")))
        assertEquals(GateAnswer.ALLOWED, gate.check(writer, call("s1/c2", "write_file")))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun allowOnceOnARequestCoversTheNextCallOnly() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_ONCE)
        val gate = gate(approver)

        assertEquals(GateAnswer.ALLOWED, gate.grant(writer, call("s1/c1", "request_tool"), reason = "write notes"))
        assertEquals(GateAnswer.ALLOWED, gate.check(writer, call("s1/c2", "write_file")))
        assertEquals(1, approver.requests.size)
        assertEquals(GateAnswer.ALLOWED, gate.check(writer, call("s1/c3", "write_file")))
        assertEquals(2, approver.requests.size)
    }

    @Test
    fun readOnlyToolsAndThreadAllowancesNeedNoCard() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val gate = gate(approver, allowed = setOf("share_file"))

        assertEquals(GateAnswer.ALLOWED, gate.grant(reader, call("s1/c1", "request_tool"), reason = "read"))
        assertEquals(GateAnswer.ALLOWED, gate.check(sharer, call("s1/c2", "share_file")))
        assertTrue(approver.requests.isEmpty())
    }

    @Test
    fun bypassModeAsksNothingForSubagents() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val gate = gate(approver, mode = ApprovalMode.BYPASS)

        assertEquals(GateAnswer.ALLOWED, gate.grant(sharer, call("s1/c1", "request_tool"), reason = "share"))
        assertEquals(GateAnswer.ALLOWED, gate.check(sharer, call("s1/c2", "share_file")))
        assertTrue(approver.requests.isEmpty())
    }

    @Test
    fun theThreadsAgentWaitsWithoutTimeLimit() = runBlocking {
        val approver = WaitingApprover()
        val broker = PermissionBroker(approver)

        val answer = async { broker.mayRun(writer, call("c1", "write_file")) }
        settle()
        clock.advanceBy(60.minutes)
        settle()
        assertFalse(answer.isCompleted)
        approver.answer(ApprovalDecision.ALLOW_ONCE)
        assertTrue(answer.await())
    }

    @Test
    fun aGrantedToolThatLeavesTheAppStillAsksForEveryCall() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_FOR_TASK)
        val gate = gate(approver, mode = ApprovalMode.AUTO)

        assertEquals(GateAnswer.ALLOWED, gate.grant(sharer, call("s1/c1", "request_tool"), reason = "save"))
        assertEquals(GateAnswer.ALLOWED, gate.check(sharer, call("s1/c2", "share_file", "path" to "work/a.md")))
        // The user saw the request, then the call with its arguments.
        assertEquals(listOf("s1/c1", "s1/c2"), approver.requests.map { it.toolCall.id })
    }

    @Test
    fun anAnswerThatArrivesAsTheTimerEndsWins() = runBlocking {
        val approver = WaitingApprover()
        val gate = gate(approver)

        val answer = async { gate.check(writer, call("s1/c1", "write_file")) }
        settle()
        // Both happen before the gate's coroutine runs again; the timer is released first.
        clock.advanceBy(3.minutes)
        approver.answer(ApprovalDecision.ALLOW_ONCE)

        assertEquals(GateAnswer.ALLOWED, answer.await())
    }
}
