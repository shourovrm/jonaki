package app.jonaki.tools.websearch

import app.jonaki.core.searchapi.Freshness
import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import app.jonaki.core.searchapi.SearchResult
import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Searches the web through the user's search services in order, moving to
 * the next one when a service fails or runs out of quota (D-011).
 */
class WebSearchTool(private val backends: List<SearchBackend>) : Tool {
    override val name: String = "web_search"

    override val promptLine: String =
        "web_search: search the web; returns titles, links and short snippets"

    override val guidelines: List<String> = listOf(
        // D-011 amendment: search services store queries, so they get topic words only.
        "Put only general topic words into a query, never names, personal details or file contents " +
            "from the conversation; the search service stores every query.",
        "To find YouTube videos, set site to youtube.com.",
        "Snippets are short; call web_fetch on a result's link to read the page.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search words, general topic only")
            }
            putJsonObject("site") {
                put("type", "string")
                put("description", "Only results from this domain, for example youtube.com")
            }
            putJsonObject("freshness") {
                put("type", "string")
                putJsonArray("enum") {
                    add("day")
                    add("week")
                    add("month")
                    add("year")
                }
                put("description", "Only results published within this period")
            }
            putJsonObject("count") {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", MAX_COUNT)
                put("description", "Number of results, default $DEFAULT_COUNT")
            }
            putJsonObject("include_content") {
                put("type", "boolean")
                put("description", "Also return page text, at most $MAX_PAGE_CHARACTERS characters per result")
            }
        }
        putJsonArray("required") { add("query") }
    }

    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 30.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val queryText = arguments.text("query")?.trim()
        if (queryText.isNullOrEmpty()) {
            return ToolOutput.error("argument query is missing", "Call web_search again with a query.")
        }
        val freshnessText = arguments.text("freshness")
        val freshness = freshnessText?.let { freshnessFrom(it) }
        if (freshnessText != null && freshness == null) {
            return ToolOutput.error("freshness \"$freshnessText\" is not known", "Use day, week, month or year.")
        }
        if (backends.isEmpty()) {
            return ToolOutput.error("no search service is set up", "Tell the user to add a Tavily or Ollama key in Settings.")
        }

        val query = SearchQuery(
            text = queryText,
            site = arguments.text("site")?.trim()?.ifEmpty { null },
            freshness = freshness,
            count = ((arguments["count"] as? JsonPrimitive)?.intOrNull ?: DEFAULT_COUNT).coerceIn(1, MAX_COUNT),
            includeContent = (arguments["include_content"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
        return searchInOrder(query, context)
    }

    private suspend fun searchInOrder(query: SearchQuery, context: ToolContext): ToolOutput {
        val skippedBackends = mutableListOf<SkippedBackend>()
        for (backend in backends) {
            when (val outcome = backend.search(query)) {
                is SearchOutcome.Success -> {
                    val text = formatResults(backend.name, query, outcome.results, skippedBackends)
                    return ToolOutput.success(context.outputLimiter.limit(text, MAX_OUTPUT_CHARACTERS, name))
                }
                is SearchOutcome.QuotaExceeded -> skippedBackends += SkippedBackend(backend.name, outcome.message)
                // A bad key or network error on one service should not stop the search either.
                is SearchOutcome.Failed -> skippedBackends += SkippedBackend(backend.name, outcome.message)
            }
        }
        return ToolOutput.error(
            "every search service failed (${skippedBackends.joinToString("; ") { "${it.name}: ${it.reason}" }})",
            "Tell the user which service failed; try again later or ask them to check the keys in Settings.",
        )
    }

    private fun formatResults(
        backendName: String,
        query: SearchQuery,
        results: List<SearchResult>,
        skippedBackends: List<SkippedBackend>,
    ): String {
        val builder = StringBuilder()
        builder.append("${results.size} results from $backendName for \"${query.text}\"")
        if (query.site != null) builder.append(" on ${query.site}")
        builder.append(":\n")
        for (skipped in skippedBackends) {
            builder.append("(${skipped.name} skipped: ${skipped.reason})\n")
        }
        if (results.isEmpty()) {
            builder.append("\nNo results. Try fewer or more general words.")
            return builder.toString()
        }
        results.forEachIndexed { index, result ->
            builder.append("\n${index + 1}. ${result.title}\n   ${result.url}\n")
            if (result.publishedDate != null) builder.append("   Published ${result.publishedDate}\n")
            if (result.snippet.isNotBlank()) builder.append("   ${result.snippet.trim()}\n")
            result.content?.let { content -> builder.append(pageText(content)) }
        }
        return builder.toString()
    }

    private fun pageText(content: String): String {
        val trimmed = content.trim()
        if (trimmed.length <= MAX_PAGE_CHARACTERS) return "   Page text:\n$trimmed\n"
        val cut = trimmed.take(MAX_PAGE_CHARACTERS)
        return "   Page text:\n$cut\n   [page text cut at $MAX_PAGE_CHARACTERS of ${trimmed.length} characters; " +
            "use web_fetch for the whole page]\n"
    }

    private fun freshnessFrom(text: String): Freshness? = when (text.lowercase()) {
        "day" -> Freshness.DAY
        "week" -> Freshness.WEEK
        "month" -> Freshness.MONTH
        "year" -> Freshness.YEAR
        else -> null
    }

    private data class SkippedBackend(val name: String, val reason: String)

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val DEFAULT_COUNT = 5
        const val MAX_COUNT = 10
        const val MAX_PAGE_CHARACTERS = 5_000
        const val MAX_OUTPUT_CHARACTERS = 20_000
    }
}
