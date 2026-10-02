package app.jonaki.core.agent

import app.jonaki.core.toolapi.SideEffect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionBrokerTest {
    private val writer = FakeTool("write_file", sideEffect = SideEffect.CHANGES)
    private val reader = FakeTool("read_file", sideEffect = SideEffect.READ_ONLY)

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
        assertTrue(broker.toolsAllowedForThread.isEmpty())
    }

    @Test
    fun allowOnceAsksAgainNextTime() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_ONCE)
        val broker = PermissionBroker(approver)

        assertTrue(broker.mayRun(writer, call("1", "write_file", "path" to "a.md")))
        assertTrue(broker.mayRun(writer, call("2", "write_file", "path" to "b.md")))
        assertEquals(2, approver.requests.size)
        assertEquals("write_file", approver.requests.first().toolCall.toolName)
    }

    @Test
    fun allowForThreadIsRememberedForThatTool() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_FOR_THREAD)
        val broker = PermissionBroker(approver)

        assertTrue(broker.mayRun(writer, call("1", "write_file")))
        assertTrue(broker.mayRun(writer, call("2", "write_file")))
        assertEquals(1, approver.requests.size)
        assertEquals(setOf("write_file"), broker.toolsAllowedForThread)
    }

    @Test
    fun denyBlocksTheCall() = runBlocking {
        val broker = PermissionBroker(FixedApprover(ApprovalDecision.DENY))

        assertFalse(broker.mayRun(writer, call("1", "write_file")))
    }

    @Test
    fun allowancesCanBeRestoredForAThread() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver, toolsAllowedForThread = setOf("write_file"))

        assertTrue(broker.mayRun(writer, call("1", "write_file")))
        assertTrue(approver.requests.isEmpty())
    }
}
