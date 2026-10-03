package app.jonaki.core.agent

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
    private val writer = FakeTool("write_file", sideEffect = SideEffect.CHANGES)
    private val reader = FakeTool("read_file", sideEffect = SideEffect.READ_ONLY)
    private val threadFolderWriter = FakeTool("edit_file", sideEffect = SideEffect.CHANGES_THREAD_FOLDER)
    private val sharer = FakeTool("share_file", sideEffect = SideEffect.CHANGES)

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
    fun aReadOnlyCallOfAChangingToolRunsWithoutAsking() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver)
        val proxy = object : Tool by FakeTool("mcp", sideEffect = SideEffect.CHANGES) {
            override fun sideEffectOf(arguments: JsonObject): SideEffect =
                if (arguments.stringArgument("action") == "search") SideEffect.READ_ONLY else SideEffect.CHANGES
        }

        assertTrue(broker.mayRun(proxy, call("1", "mcp", "action" to "search")))
        assertTrue(approver.requests.isEmpty())
        assertFalse(broker.mayRun(proxy, call("2", "mcp", "action" to "call")))
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

    @Test
    fun askModeAsksForThreadFolderAndOutsideChanges() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.ALLOW_ONCE)
        val broker = PermissionBroker(approver, approvalMode = { ApprovalMode.ASK })

        assertTrue(broker.mayRun(threadFolderWriter, call("1", "edit_file")))
        assertTrue(broker.mayRun(sharer, call("2", "share_file")))
        assertEquals(listOf("edit_file", "share_file"), approver.requests.map { it.toolName })
    }

    @Test
    fun autoModeRunsThreadFolderChangesWithoutAsking() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver, approvalMode = { ApprovalMode.AUTO })

        assertTrue(broker.mayRun(threadFolderWriter, call("1", "edit_file")))
        assertTrue(approver.requests.isEmpty())
    }

    @Test
    fun autoModeStillAsksForChangesOutsideTheApp() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver, approvalMode = { ApprovalMode.AUTO })

        assertFalse(broker.mayRun(sharer, call("1", "share_file")))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun bypassModeNeverAsks() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver, approvalMode = { ApprovalMode.BYPASS })

        assertTrue(broker.mayRun(sharer, call("1", "share_file")))
        assertTrue(broker.mayRun(threadFolderWriter, call("2", "edit_file")))
        assertTrue(approver.requests.isEmpty())
        // Bypass is not an allowance: switching back to Ask asks again.
        assertTrue(broker.toolsAllowedForThread.isEmpty())
    }

    @Test
    fun aModeChangeDuringARunAppliesToTheNextCall() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        var mode = ApprovalMode.ASK
        val broker = PermissionBroker(approver, approvalMode = { mode })

        assertFalse(broker.mayRun(threadFolderWriter, call("1", "edit_file")))
        mode = ApprovalMode.AUTO
        assertTrue(broker.mayRun(threadFolderWriter, call("2", "edit_file")))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun allowanceForThreadStillSkipsTheCardInAutoMode() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver, toolsAllowedForThread = setOf("share_file"), approvalMode = { ApprovalMode.AUTO })

        assertTrue(broker.mayRun(sharer, call("1", "share_file")))
        assertTrue(approver.requests.isEmpty())
    }

    @Test
    fun readOnlyAndAppDataNeverAskInAnyMode() {
        for (mode in ApprovalMode.entries) {
            assertFalse(ApprovalMode.needsApproval(SideEffect.READ_ONLY, mode))
            assertFalse(ApprovalMode.needsApproval(SideEffect.CHANGES_APP_DATA, mode))
        }
    }

    /** Like the phone tool: reading the calendar runs at once, adding to it asks (D-M9-1). */
    private class ActionTool : Tool by FakeTool("phone", sideEffect = SideEffect.CHANGES) {
        override fun sideEffectOf(arguments: JsonObject): SideEffect =
            if (arguments.stringArgument("action") == "calendar_list") SideEffect.READ_ONLY else SideEffect.CHANGES
    }

    @Test
    fun aReadOnlyActionOfAToolThatChangesThingsRunsWithoutAsking() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver)

        assertTrue(broker.mayRun(ActionTool(), call("1", "phone", "action" to "calendar_list")))
        assertTrue(approver.requests.isEmpty())
    }

    @Test
    fun aChangingActionOfTheSameToolAsks() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver)

        assertFalse(broker.mayRun(ActionTool(), call("1", "phone", "action" to "calendar_add")))
        assertEquals(1, approver.requests.size)
    }

    @Test
    fun aChangingActionOutsideTheAppStillAsksInAutoMode() = runBlocking {
        val approver = FixedApprover(ApprovalDecision.DENY)
        val broker = PermissionBroker(approver, approvalMode = { ApprovalMode.AUTO })

        assertFalse(broker.mayRun(ActionTool(), call("1", "phone", "action" to "calendar_add")))
        assertEquals(1, approver.requests.size)
    }
}
