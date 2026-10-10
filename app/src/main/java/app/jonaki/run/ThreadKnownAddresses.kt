package app.jonaki.run

import app.jonaki.core.agent.KnownAddresses
import app.jonaki.core.agent.WebAddresses
import app.jonaki.core.storage.MessageDao

/**
 * The addresses a thread already holds: those in the user's own messages and
 * in tool results, read from the stored messages. The database narrows the
 * rows to those whose text contains the address (one query), and
 * [WebAddresses] then checks that the address stands there whole, so that an
 * address found only inside a longer one does not count. The model's own
 * messages are left out: it may have composed an address there. Any address
 * on a host the user named is known as well.
 */
class ThreadKnownAddresses(
    private val threadId: String,
    private val messageDao: MessageDao,
) : KnownAddresses {
    override suspend fun isKnown(address: String): Boolean {
        val normalised = WebAddresses.normalise(address) ?: return false
        val withoutScheme = normalised.substringAfter("://")
        val candidateTexts = messageDao.textsOfUserAndToolMessagesContaining(threadId, withoutScheme)
        if (candidateTexts.any { text -> WebAddresses.appearsIn(address, text) }) {
            return true
        }
        return isOnAHostTheUserNamed(address)
    }

    /**
     * A site the user named is one they chose to send requests to, so any
     * page of it and of its subdomains is known: the user who gives a GitHub
     * link expects the agent to read the repository's other pages. Hosts
     * named only in tool results do not count; a hostile page names its own.
     */
    private suspend fun isOnAHostTheUserNamed(address: String): Boolean {
        val host = WebAddresses.hostOf(address) ?: return false
        val namedHosts = messageDao.textsOfUserMessages(threadId).flatMap(WebAddresses::hostsIn).toSet()
        return WebAddresses.isOnNamedHost(host, namedHosts)
    }
}
