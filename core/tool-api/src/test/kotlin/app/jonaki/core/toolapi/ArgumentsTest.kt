package app.jonaki.core.toolapi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArgumentsTest {
    private val arguments = Json.parseToJsonElement(
        """{"path":"a.md","offset":20,"limit":"50","ignore_case":"true","exact":false,"missing":null}""",
    ).jsonObject

    @Test
    fun readsStrings() {
        assertEquals("a.md", arguments.stringArgument("path"))
        assertNull(arguments.stringArgument("missing"))
        assertNull(arguments.stringArgument("absent"))
    }

    @Test
    fun readsNumbersSentAsNumbersOrStrings() {
        assertEquals(20, arguments.intArgument("offset"))
        assertEquals(50, arguments.intArgument("limit"))
        assertNull(arguments.intArgument("path"))
    }

    @Test
    fun readsBooleansSentAsBooleansOrStrings() {
        assertEquals(true, arguments.booleanArgument("ignore_case"))
        assertEquals(false, arguments.booleanArgument("exact"))
        assertNull(arguments.booleanArgument("path"))
    }
}
