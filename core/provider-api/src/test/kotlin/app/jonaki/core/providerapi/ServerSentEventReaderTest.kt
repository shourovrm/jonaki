package app.jonaki.core.providerapi

import java.io.BufferedReader
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerSentEventReaderTest {
    private fun readAll(stream: String): List<String> {
        val reader = ServerSentEventReader(BufferedReader(StringReader(stream)))
        val payloads = mutableListOf<String>()
        while (true) {
            val data = reader.nextData() ?: break
            payloads.add(data)
        }
        return payloads
    }

    @Test
    fun returnsEachEventAtTheBlankLine() {
        val payloads = readAll("data: {\"a\":1}\n\ndata: {\"a\":2}\n\n")
        assertEquals(listOf("{\"a\":1}", "{\"a\":2}"), payloads)
    }

    @Test
    fun joinsMultipleDataLinesWithNewlines() {
        assertEquals(listOf("first\nsecond"), readAll("data: first\ndata: second\n\n"))
    }

    @Test
    fun skipsCommentsAndOtherFields() {
        // OpenRouter sends ": OPENROUTER PROCESSING" keep-alive comments.
        val payloads = readAll(": OPENROUTER PROCESSING\n\nevent: message\nid: 7\ndata: x\n\n")
        assertEquals(listOf("x"), payloads)
    }

    @Test
    fun acceptsDataWithoutSpaceAndCarriageReturns() {
        assertEquals(listOf("y"), readAll("data:y\r\n\r\n"))
    }

    @Test
    fun returnsAnUnterminatedFinalEvent() {
        assertEquals(listOf("[DONE]"), readAll("data: [DONE]"))
    }

    @Test
    fun emptyStreamHasNoEvents() {
        assertEquals(emptyList<String>(), readAll(""))
    }
}
