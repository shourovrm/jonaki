package app.jonaki.run

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptTest {
    @Test
    fun theLanguageRuleNamesItsThreePartsInPriorityOrder() {
        val prompt = SystemPrompt.BASE

        val asked = prompt.indexOf("(1) if the user asked for an answer language earlier in this thread")
        val writes = prompt.indexOf("(2) otherwise answer in the language the user writes in")
        val never = prompt.indexOf("(3) never choose the language from memory facts, the user's location or name")

        assertTrue(asked >= 0)
        assertTrue(writes > asked)
        assertTrue(never > writes)
    }

    @Test
    fun theRuleKeepsAnAskedLanguageWhateverLanguageLaterMessagesUse() {
        assertTrue(SystemPrompt.BASE.contains("whatever language the later messages are in, until the user asks for another language"))
    }

    @Test
    fun thePromptHoldsNoDateOrThreadDetailSoTheProviderCacheKeepsWorking() {
        assertFalse(Regex("""\d{4}-\d{2}-\d{2}""").containsMatchIn(SystemPrompt.BASE))
    }
}
