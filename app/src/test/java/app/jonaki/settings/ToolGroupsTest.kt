package app.jonaki.settings

import app.jonaki.core.runtimeapi.CodeLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolGroupsTest {
    @Test
    fun everyGroupIsOnUntilSwitchedOff() {
        assertEquals(ToolGroup.entries.toSet(), ToolGroups.enabled(emptySet()))
    }

    @Test
    fun filesStaysOnEvenWhenSavedAsOff() {
        assertTrue(ToolGroup.FILES in ToolGroups.enabled(setOf(ToolGroup.FILES, ToolGroup.WEB)))
        assertFalse(ToolGroup.WEB in ToolGroups.enabled(setOf(ToolGroup.FILES, ToolGroup.WEB)))
    }

    @Test
    fun runCodeIsOfferedWhileEitherLanguageIsOn() {
        val pythonOnly = ToolGroups.enabled(setOf(ToolGroup.JAVASCRIPT))

        assertTrue(ToolGroups.isOffered("run_code", pythonOnly))
        assertEquals(setOf(CodeLanguage.PYTHON), ToolGroups.codeLanguages(pythonOnly))
        assertFalse(ToolGroups.isOffered("run_code", ToolGroups.enabled(setOf(ToolGroup.JAVASCRIPT, ToolGroup.PYTHON))))
    }

    @Test
    fun aToolOutsideEveryGroupStaysOffered() {
        assertTrue(ToolGroups.isOffered("phone", ToolGroups.enabled(ToolGroup.entries.toSet())))
    }

    @Test
    fun disabledGroupsSurviveSavingAndUnknownNamesAreDropped() {
        val disabled = setOf(ToolGroup.PYTHON, ToolGroup.WEB)

        val text = ToolGroups.disabledToText(disabled)

        assertEquals("WEB,PYTHON", text)
        assertEquals(disabled, ToolGroups.disabledFromText(text))
        assertEquals(setOf(ToolGroup.WEB), ToolGroups.disabledFromText("WEB,TELEPORT,"))
        assertEquals(emptySet<ToolGroup>(), ToolGroups.disabledFromText(""))
    }

    @Test
    fun thePickerShowsOnANewInstallAndOnceAfterTheUpdate() {
        // A new install and an install from before the picker both read version 0.
        assertTrue(ToolPicker.shouldShow(seenVersion = 0))
        assertFalse(ToolPicker.shouldShow(seenVersion = ToolPicker.CURRENT))
    }
}
