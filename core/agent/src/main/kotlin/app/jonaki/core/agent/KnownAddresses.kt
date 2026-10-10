package app.jonaki.core.agent

/**
 * Tells whether a web address already appeared in the thread: verbatim in a
 * message of the user or in an earlier tool result. A thread that has read
 * outside content asks before a tool contacts an address that appeared
 * nowhere, because the model composed such an address and may have put the
 * user's data into its path or query. An attacker who never saw the data
 * cannot have written an address that contains it.
 */
fun interface KnownAddresses {
    suspend fun isKnown(address: String): Boolean

    companion object {
        /** Knows nothing, so every address asks; the safe default. */
        val NONE = KnownAddresses { false }
    }
}

/** How addresses are compared and found in text. Pure, so it is tested without a database. */
object WebAddresses {
    private val addressPattern = Regex("""^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#\s]*)([^#\s]*)""")

    // Where an address ends in running text: whitespace, tags, quotes and square brackets (markdown links) never belong to it.
    private val addressInTextPattern = Regex("""[A-Za-z][A-Za-z0-9+.-]*://[^\s<>"'`\\\[\]]+""")

    // Dotted names that end in letters: "github.com", not "1.4.4". The look-behind keeps a match from starting mid-name.
    private val hostInTextPattern = Regex("""(?<![A-Za-z0-9.@-])[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}(?![A-Za-z0-9-])""")

    private const val TRAILING_PUNCTUATION = ".,;:!?"

    /**
     * [address] reduced to what decides which page a server sends: scheme and
     * host in lower case, no fragment (a browser never sends it), no single
     * trailing slash. The path and the query keep their case and every
     * character, because that is where data hides. Null when [address] is not
     * a scheme-and-host address.
     */
    fun normalise(address: String): String? {
        val match = addressPattern.find(address.trim()) ?: return null
        val scheme = match.groupValues[1].lowercase()
        val host = match.groupValues[2].lowercase()
        val pathAndQuery = match.groupValues[3].removeSuffix("/")
        if (host.isEmpty()) {
            return null
        }
        return "$scheme://$host$pathAndQuery"
    }

    /**
     * True when [text] contains [address] as a whole address: after
     * [normalise] it equals one address found in the text. An address that is
     * only the beginning of a longer one in the text does not count.
     */
    fun appearsIn(address: String, text: String): Boolean {
        val wanted = normalise(address) ?: return false
        return addressesIn(text).any { found -> normalise(found) == wanted }
    }

    /**
     * The host [address] is sent to, in lower case, without a user name or a
     * port; null when [address] is not a scheme-and-host address.
     */
    fun hostOf(address: String): String? {
        val match = addressPattern.find(address.trim()) ?: return null
        val host = match.groupValues[2].substringAfterLast('@').substringBefore(':').lowercase()
        return host.ifEmpty { null }
    }

    /**
     * The host names written in [text], with or without a scheme, so that
     * "github.com/x" in a user's message names the host as "https://github.com/x" does.
     */
    fun hostsIn(text: String): Set<String> =
        hostInTextPattern.findAll(text).map { match -> match.value.lowercase() }.toSet()

    /** True when [host] is one of [namedHosts] or a subdomain of one: "api.github.com" for "github.com". */
    fun isOnNamedHost(host: String, namedHosts: Set<String>): Boolean =
        namedHosts.any { named -> host == named || host.endsWith(".$named") }

    /** The addresses in [text], without the punctuation of the sentence or markdown around them. */
    fun addressesIn(text: String): List<String> =
        addressInTextPattern.findAll(text).map { match -> withoutSurroundingPunctuation(match.value) }.toList()

    private fun withoutSurroundingPunctuation(found: String): String {
        var address = found
        while (address.isNotEmpty()) {
            val last = address.last()
            val endsInPunctuation = last in TRAILING_PUNCTUATION
            val endsInUnopenedBracket = last in ")]}" && !bracketIsOpenedInside(address, last)
            if (!endsInPunctuation && !endsInUnopenedBracket) {
                break
            }
            address = address.dropLast(1)
        }
        return address
    }

    /** "https://x.org/a_(b)" keeps its bracket; "(see https://x.org/a)" loses the one that closes the sentence. */
    private fun bracketIsOpenedInside(address: String, closing: Char): Boolean {
        val opening = when (closing) {
            ')' -> '('
            ']' -> '['
            else -> '{'
        }
        return address.count { character -> character == opening } >= address.count { character -> character == closing }
    }
}
