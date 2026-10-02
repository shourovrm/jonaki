package app.jonaki.settings

import app.jonaki.core.providerapi.ThinkingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThinkingLevelsTest {
    @Test
    fun levelsPerModelSurviveSaving() {
        val levels = mapOf("openrouter:z-ai/glm-5.3-flash" to ThinkingLevel.HIGH, "gemini:gemini-3.8-flash" to ThinkingLevel.OFF)

        assertEquals(levels, ThinkingLevels.fromText(ThinkingLevels.toText(levels)))
    }

    @Test
    fun unreadableLinesAreSkipped() {
        assertEquals(mapOf("a:b" to ThinkingLevel.LOW), ThinkingLevels.fromText("a:b\tLOW\nbroken\nc:d\tEXTREME"))
    }

    @Test
    fun theThreadWinsThenTheModelThenTheModelsOwnDefault() {
        assertEquals(ThinkingLevel.LOW, ThinkingLevels.effective(threadLevel = "LOW", modelLevel = ThinkingLevel.HIGH, isSupported = true))
        assertEquals(ThinkingLevel.HIGH, ThinkingLevels.effective(threadLevel = null, modelLevel = ThinkingLevel.HIGH, isSupported = true))
        assertNull(ThinkingLevels.effective(threadLevel = null, modelLevel = null, isSupported = true))
    }

    @Test
    fun aModelWithoutTheSettingGetsNothing() {
        assertNull(ThinkingLevels.effective(threadLevel = "HIGH", modelLevel = ThinkingLevel.HIGH, isSupported = false))
    }
}
