package app.jonaki.tools.searchchats

/**
 * What the search_chats tool needs from the app's chat history. The app wires
 * this to the database for one thread, so this module never depends on
 * storage (D-007). An implementation never returns messages of incognito
 * threads.
 */
interface ChatSearchStore {
    /**
     * Complete user and assistant messages that contain any word of
     * [words], best match first. [thisThreadOnly] limits the search to the
     * thread the store was made for. [totalMatches] counts every match,
     * also those beyond [limit].
     */
    suspend fun search(words: String, thisThreadOnly: Boolean, limit: Int): ChatSearchResult
}

data class ChatSearchResult(
    val hits: List<ChatHit>,
    val totalMatches: Int,
)

enum class Speaker {
    USER,
    ASSISTANT,
}

data class ChatHit(
    val threadTitle: String,
    val isThisThread: Boolean,
    val createdAtMillis: Long,
    val speaker: Speaker,
    /** The whole text of the matching message; the tool cuts the excerpt. */
    val text: String,
    /** The message just before the hit in its thread, if any. */
    val before: ChatNeighbour?,
    val after: ChatNeighbour?,
)

data class ChatNeighbour(
    val speaker: Speaker,
    val text: String,
)
