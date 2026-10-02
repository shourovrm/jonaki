package app.jonaki

import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import app.jonaki.tools.youtubesummarize.VideoAnswer
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolRegistryTest {
    private val searchBackend = object : SearchBackend {
        override val name = "Fake"

        override suspend fun search(query: SearchQuery) = SearchOutcome.Success(emptyList())
    }

    private val everything = ToolServices(
        searchBackends = listOf(searchBackend),
        videoSummarizer = { VideoAnswer.Success("summary", inputTokens = null) },
        webAccessEnabled = true,
    )

    private fun namesFor(services: ToolServices) = ToolRegistry.tools(services).map { tool -> tool.name }

    @Test
    fun toolNamesAreUnique() {
        val names = namesFor(everything)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun everyToolIsOfferedWhenAllServicesExist() {
        assertEquals(
            setOf(
                "read_file", "write_file", "edit_file", "find_files", "search_files",
                "web_search", "web_fetch", "youtube_summarize",
            ),
            namesFor(everything).toSet(),
        )
    }

    @Test
    fun webAccessOffRemovesSearchAndFetchButKeepsYouTube() {
        val names = namesFor(everything.copy(webAccessEnabled = false)).toSet()
        assertEquals(false, "web_search" in names)
        assertEquals(false, "web_fetch" in names)
        assertEquals(true, "youtube_summarize" in names)
    }

    @Test
    fun webSearchNeedsAtLeastOneBackendWithAKey() {
        val names = namesFor(everything.copy(searchBackends = emptyList())).toSet()
        assertEquals(false, "web_search" in names)
        assertEquals(true, "web_fetch" in names)
    }

    @Test
    fun youTubeNeedsAGeminiKey() {
        val names = namesFor(everything.copy(videoSummarizer = null)).toSet()
        assertEquals(false, "youtube_summarize" in names)
    }
}
