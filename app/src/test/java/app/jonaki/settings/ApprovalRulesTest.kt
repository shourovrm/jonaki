package app.jonaki.settings

import app.jonaki.core.agent.ApprovalRule
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalRulesTest {
    @Test
    fun rulesSurviveTheStoredText() {
        val rules = listOf(
            ApprovalRule("phone", action = "reminder"),
            ApprovalRule("mcp", action = "call", detail = "notes/add"),
            ApprovalRule("write_file"),
        )

        assertEquals(rules, ApprovalRules.fromText(ApprovalRules.toText(rules)))
    }

    @Test
    fun noRulesAreStoredAsAnEmptyList() {
        assertEquals(emptyList<ApprovalRule>(), ApprovalRules.fromText(""))
        assertEquals(emptyList<ApprovalRule>(), ApprovalRules.fromText(ApprovalRules.toText(emptyList())))
    }

    @Test
    fun brokenTextAndEntriesWithoutAToolAreLeftOut() {
        assertEquals(emptyList<ApprovalRule>(), ApprovalRules.fromText("not json"))
        assertEquals(emptyList<ApprovalRule>(), ApprovalRules.fromText("""[{"action":"reminder"},{"tool":" "}]"""))
    }

    @Test
    fun aRepeatedRuleIsKeptOnce() {
        val text = """[{"tool":"phone","action":"reminder"},{"tool":"phone","action":"reminder"}]"""

        assertEquals(listOf(ApprovalRule("phone", "reminder")), ApprovalRules.fromText(text))
    }

    @Test
    fun addingARuleThatExistsChangesNothing() {
        val current = listOf(ApprovalRule("phone", "reminder"))

        assertEquals(current, ApprovalRules.added(current, ApprovalRule("phone", "reminder")))
        assertEquals(2, ApprovalRules.added(current, ApprovalRule("phone", "notify")).size)
    }
}
