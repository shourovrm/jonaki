package app.jonaki.tools.mcp

import java.io.BufferedReader

/**
 * Reads the `data` payloads of a server-sent event stream, the second answer
 * form of MCP Streamable HTTP. core/provider-api has the same reader, but a
 * tool may depend only on core/tool-api (D-007), so this module keeps its own.
 */
internal class EventStream(private val reader: BufferedReader) {
    /** The next event's data, its lines joined by newlines; null at the end of the stream. */
    fun nextData(): String? {
        val dataLines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine()?.removeSuffix("\r")
            if (line == null) {
                return if (dataLines.isEmpty()) null else dataLines.joinToString("\n")
            }
            if (line.isEmpty()) {
                if (dataLines.isNotEmpty()) return dataLines.joinToString("\n")
                continue
            }
            if (line.startsWith("data:")) {
                dataLines.add(line.removePrefix("data:").removePrefix(" "))
            }
            // Comments (":"), event, id and retry fields carry nothing the client uses.
        }
    }
}
