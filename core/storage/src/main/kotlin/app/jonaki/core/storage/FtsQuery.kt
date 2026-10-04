package app.jonaki.core.storage

/**
 * Turns what a model or a user typed into an FTS5 query for a trigram index.
 * One quoted phrase finds only rows that hold the whole text ("thesis
 * deadline" misses "deadline for the thesis"), so each word becomes its own
 * phrase and the words are joined with OR; ordering by rank then puts the
 * rows that hold more of the words first.
 */
object FtsQuery {
    /** A trigram index cannot match a word shorter than this (spike S-1). */
    const val MINIMUM_WORD_LENGTH = 3

    /**
     * The words of [text] as quoted phrases joined with OR, or null when no
     * word is long enough for the index; the caller then falls back to LIKE.
     * Quoting keeps FTS5 from reading operators such as AND, NOT or * from the text.
     */
    fun anyWordOf(text: String): String? {
        val words = searchableWordsOf(text)
        if (words.isEmpty()) {
            return null
        }
        return words.joinToString(" OR ") { word -> quoted(word) }
    }

    /** The distinct words of [text] that the trigram index can match, in the order they appear. */
    fun searchableWordsOf(text: String): List<String> =
        text.split(WHITESPACE)
            .map { word -> word.trim() }
            .filter { word -> word.codePointCount(0, word.length) >= MINIMUM_WORD_LENGTH }
            .distinct()

    /** An FTS5 phrase: the text in double quotes with inner quotes doubled. */
    fun quoted(text: String): String = "\"" + text.replace("\"", "\"\"") + "\""

    private val WHITESPACE = Regex("\\s+")
}
