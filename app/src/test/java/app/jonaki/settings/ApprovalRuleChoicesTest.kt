package app.jonaki.settings

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.feature.settings.ApprovalRuleChoiceUi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalRuleChoicesTest {
    private class DeclaredTool(
        override val name: String,
        override val sideEffect: SideEffect,
        override val ruleActions: List<String> = emptyList(),
        override val ruleDetailName: String? = null,
    ) : Tool {
        override val promptLine: String = name
        override val guidelines: List<String> = emptyList()
        override val parameterSchema: JsonObject = JsonObject(emptyMap())
        override val requiredCapabilities: Set<Capability> = emptySet()
        override val timeLimit: Duration = 1.seconds

        override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput = ToolOutput.success("")
    }

    @Test
    fun onlyToolsThatCanAskAreOfferedOneChoicePerAction() {
        val tools = listOf(
            DeclaredTool("web_fetch", SideEffect.READ_ONLY),
            DeclaredTool("memory", SideEffect.CHANGES_APP_DATA),
            DeclaredTool("delegate", SideEffect.NEEDS_USER),
            DeclaredTool("write_file", SideEffect.CHANGES_THREAD_FOLDER),
            DeclaredTool("phone", SideEffect.CHANGES, ruleActions = listOf("calendar_add", "reminder")),
            DeclaredTool("mcp", SideEffect.CHANGES, ruleActions = listOf("call"), ruleDetailName = "server/tool"),
        )

        assertEquals(
            listOf(
                ApprovalRuleChoiceUi("mcp", "call", "server/tool"),
                ApprovalRuleChoiceUi("phone", "calendar_add", null),
                ApprovalRuleChoiceUi("phone", "reminder", null),
                ApprovalRuleChoiceUi("write_file", null, null),
            ),
            ApprovalRuleChoices.of(tools),
        )
    }
}
