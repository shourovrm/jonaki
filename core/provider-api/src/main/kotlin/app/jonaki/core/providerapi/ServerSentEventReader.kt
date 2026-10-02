package app.jonaki.core.providerapi

import java.io.BufferedReader

/**
 * Reads the `data` payloads of a server-sent event stream, the format every
 * provider uses to stream replies. Lives in core because both provider
 * modules need it and providers may not depend on each other (D-007).
 */
class ServerSentEventReader(private val reader: BufferedReader) {
    /**
     * Returns the next event's data, its lines joined by newlines, or null at
     * the end of the stream. Blocks while waiting for the network.
     */
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
            // Comment lines (":") and the event, id and retry fields carry nothing we use.
        }
    }
}
