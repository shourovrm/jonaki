package app.jonaki.core.runtimeapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeLanguageTest {
    @Test
    fun readsTheNamesModelsWrite() {
        assertEquals(CodeLanguage.PYTHON, CodeLanguage.fromArgument("Python "))
        assertEquals(CodeLanguage.PYTHON, CodeLanguage.fromArgument("py"))
        assertEquals(CodeLanguage.JAVASCRIPT, CodeLanguage.fromArgument("javascript"))
        assertEquals(CodeLanguage.JAVASCRIPT, CodeLanguage.fromArgument("JS"))
    }

    @Test
    fun refusesOtherLanguages() {
        assertNull(CodeLanguage.fromArgument("ruby"))
    }
}
