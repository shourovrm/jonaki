package app.jonaki.tools.mcp

/** One search hit: a tool and the server that offers it. */
internal data class ToolMatch(val serverName: String, val tool: McpToolInfo, val score: Int)

/**
 * Keyword search over the servers' tool lists. A word found in a tool's
 * name counts three times as much as one found in its description, so
 * "repository" finds repository_info before a tool that merely mentions
 * repositories.
 */
internal object ToolSearch {
    private const val NAME_WEIGHT = 3
    private const val DESCRIPTION_WEIGHT = 1

    /** [toolsByServer] keeps the user's server order; a blank query lists every tool. */
    fun rank(query: String, toolsByServer: Map<String, List<McpToolInfo>>, limit: Int): List<ToolMatch> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { word -> word.isNotEmpty() }
        val matches = mutableListOf<ToolMatch>()
        for ((serverName, tools) in toolsByServer) {
            for (tool in tools) {
                val score = scoreOf(tool, words)
                if (words.isEmpty() || score > 0) {
                    matches += ToolMatch(serverName, tool, score)
                }
            }
        }
        // sortedByDescending is stable, so equal scores keep server and list order.
        return matches.sortedByDescending { match -> match.score }.take(limit)
    }

    private fun scoreOf(tool: McpToolInfo, words: List<String>): Int {
        val name = tool.name.lowercase()
        val description = tool.description.lowercase()
        var score = 0
        for (word in words) {
            if (name.contains(word)) score += NAME_WEIGHT
            if (description.contains(word)) score += DESCRIPTION_WEIGHT
        }
        return score
    }
}
