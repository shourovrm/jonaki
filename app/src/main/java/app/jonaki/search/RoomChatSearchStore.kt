package app.jonaki.search

import app.jonaki.core.model.Role
import app.jonaki.core.storage.FtsQuery
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.MessageSearchRow
import app.jonaki.core.storage.NeighbourMessage
import app.jonaki.tools.searchchats.ChatHit
import app.jonaki.tools.searchchats.ChatNeighbour
import app.jonaki.tools.searchchats.ChatSearchResult
import app.jonaki.tools.searchchats.ChatSearchStore
import app.jonaki.tools.searchchats.Speaker

/**
 * The search_chats tool's view of the message index for one thread. The index
 * query itself leaves out incognito threads (see MessageSearchIndex.SEARCH).
 */
class RoomChatSearchStore(
    database: JonakiDatabase,
    private val threadId: String,
) : ChatSearchStore {
    private val messageDao = database.messageDao()

    override suspend fun search(words: String, thisThreadOnly: Boolean, limit: Int): ChatSearchResult {
        val match = FtsQuery.anyWordOf(words) ?: return ChatSearchResult(emptyList(), 0)
        val searchedThreadId = if (thisThreadOnly) threadId else null
        val rows = messageDao.searchMessages(match, searchedThreadId, limit, 0)
        val totalMatches = messageDao.countSearchMatches(match, searchedThreadId)
        return ChatSearchResult(rows.map { row -> hitOf(row) }, totalMatches)
    }

    private suspend fun hitOf(row: MessageSearchRow): ChatHit = ChatHit(
        threadTitle = row.threadTitle,
        isThisThread = row.threadId == threadId,
        createdAtMillis = row.createdAtMillis,
        speaker = speakerOf(row.role),
        text = row.text,
        before = messageDao.messageBefore(row.threadId, row.position)?.let { neighbour -> neighbourOf(neighbour) },
        after = messageDao.messageAfter(row.threadId, row.position)?.let { neighbour -> neighbourOf(neighbour) },
    )

    private fun neighbourOf(message: NeighbourMessage): ChatNeighbour = ChatNeighbour(speakerOf(message.role), message.text)

    private fun speakerOf(role: String): Speaker =
        if (role == Role.USER.name) Speaker.USER else Speaker.ASSISTANT
}
