package app.jonaki.tools.mcp

import java.io.BufferedReader
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

class EventStreamTest {
    private fun payloadsOf(body: String): List<String> {
        val stream = EventStream(BufferedReader(StringReader(body)))
        return generateSequence { stream.nextData() }.toList()
    }

    @Test
    fun readsEachEventsDataAndSkipsTheOtherFields() {
        val body = "event: message\nid: 7\ndata: {\"a\":1}\n\n: keep-alive\n\nevent: message\ndata: {\"b\":2}\n\n"
        assertEquals(listOf("{\"a\":1}", "{\"b\":2}"), payloadsOf(body))
    }

    @Test
    fun joinsDataLinesOfOneEventWithNewlines() {
        assertEquals(listOf("{\n\"a\":1}"), payloadsOf("data: {\ndata: \"a\":1}\n\n"))
    }

    @Test
    fun acceptsCarriageReturnsAndALastEventWithoutBlankLine() {
        assertEquals(listOf("one", "two"), payloadsOf("data: one\r\n\r\ndata:two"))
    }

    @Test
    fun anEmptyStreamHasNoEvents() {
        assertEquals(emptyList<String>(), payloadsOf(""))
    }
}
