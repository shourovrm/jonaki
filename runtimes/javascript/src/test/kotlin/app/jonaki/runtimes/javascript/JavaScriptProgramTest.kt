package app.jonaki.runtimes.javascript

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaScriptProgramTest {
    @Test
    fun theScriptStartsWithTheJobAsJson() {
        val script = JavaScriptProgram.script(
            code = "console.log(\"quote \\\" and </script>\")",
            inputTexts = mapOf("inbox/a.csv" to "x,y\nবাংলা"),
        )

        val firstLine = script.lineSequence().first()
        assertTrue(firstLine, firstLine.startsWith("const jonakiJob = "))
        val job = Json.parseToJsonElement(firstLine.removePrefix("const jonakiJob = ").removeSuffix(";")).jsonObject
        assertEquals("console.log(\"quote \\\" and </script>\")", job["code"]!!.jsonPrimitive.content)
        assertEquals("x,y\nবাংলা", job["files"]!!.jsonObject["inbox/a.csv"]!!.jsonPrimitive.content)
    }

    @Test
    fun theScriptEndsWithTheProgramRunner() {
        val script = JavaScriptProgram.script(code = "1", inputTexts = emptyMap())

        assertTrue(script.contains("globalThis.files"))
        assertTrue(script.trimEnd().endsWith("})()"))
    }

    @Test
    fun readsAFinishedReply() {
        val reply = """{"stdout":"hi\n","stderr":"warn\n","droppedCharacters":0,"result":"42","error":null,
            |"written":{"work/o.txt":"X,Y"}}""".trimMargin()

        val outcome = JavaScriptProgram.outcomeFrom(reply)

        assertEquals("hi\n", outcome.stdout)
        assertEquals("warn\n", outcome.stderr)
        assertEquals("42", outcome.resultValue)
        assertNull(outcome.errorText)
        assertEquals("work/o.txt", outcome.outputFiles.single().relativePath)
        assertEquals("X,Y", outcome.outputFiles.single().content.decodeToString())
    }

    @Test
    fun readsAnErrorAndDroppedOutput() {
        val reply = """{"stdout":"a","stderr":"","droppedCharacters":12,"result":null,
            |"error":"Error: boom","written":{}}""".trimMargin()

        val outcome = JavaScriptProgram.outcomeFrom(reply)

        assertEquals("Error: boom", outcome.errorText)
        assertNull(outcome.resultValue)
        assertTrue(outcome.stdout, outcome.stdout.contains("12 more characters were dropped"))
    }

    @Test
    fun printedTextThatStartsWithANumberIsKeptWhole() {
        // fib.join(', ') printed only "0" on the phone in 0.8.0.
        val reply = """{"stdout":"0, 1, 1, 2, 3, 5, 8, 13, 21, 34\n","stderr":"7 warnings\n","droppedCharacters":0,
            |"result":"42 apples","error":null,"written":{}}""".trimMargin()

        val outcome = JavaScriptProgram.outcomeFrom(reply)

        assertEquals("0, 1, 1, 2, 3, 5, 8, 13, 21, 34\n", outcome.stdout)
        assertEquals("7 warnings\n", outcome.stderr)
        assertEquals("42 apples", outcome.resultValue)
    }
}
