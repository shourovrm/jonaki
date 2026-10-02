package app.jonaki.core.searchapi

/**
 * One web search service. Each service is one module under search/ (D-007);
 * the web_search tool receives a list of backends and tries them in order,
 * moving on when one reports [SearchOutcome.QuotaExceeded] (D-011).
 */
interface SearchBackend {
    /** Shown to the user and the model, for example "Tavily". */
    val name: String

    suspend fun search(query: SearchQuery): SearchOutcome
}

data class SearchQuery(
    val text: String,
    /** Restrict results to one domain, for example "youtube.com". */
    val site: String? = null,
    val freshness: Freshness? = null,
    val count: Int = 5,
    /** Ask the backend for page content as well as snippets, where it supports it. */
    val includeContent: Boolean = false,
)

enum class Freshness {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val content: String? = null,
    val publishedDate: String? = null,
)

sealed interface SearchOutcome {
    data class Success(val results: List<SearchResult>) : SearchOutcome

    /** Out of credits or rate-limited; the caller tries the next backend. */
    data class QuotaExceeded(val message: String) : SearchOutcome

    /** Any other failure, for example a bad key or a network error. */
    data class Failed(val message: String) : SearchOutcome
}
