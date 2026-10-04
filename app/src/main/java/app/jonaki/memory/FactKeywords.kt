package app.jonaki.memory

/** Cleans the search words a model gives with a fact; they are indexed for recall and never shown. */
object FactKeywords {
    /** Keeps the index small and stops a model from dumping a paragraph here. */
    const val MAX_LENGTH = 120

    private val separators = Regex("[\\s,;]+")

    /** Words separated by single spaces, at most [MAX_LENGTH] characters, cut at a word end when it can be. */
    fun cleaned(raw: String?): String {
        val joined = raw.orEmpty().split(separators).filter { word -> word.isNotEmpty() }.joinToString(" ")
        if (joined.length <= MAX_LENGTH) {
            return joined
        }
        val cut = joined.take(MAX_LENGTH)
        // A cut inside a word would index a half word; drop it unless the whole text is one long word.
        val lastSpace = cut.lastIndexOf(' ')
        val endsAtWordEnd = joined[MAX_LENGTH] == ' '
        if (endsAtWordEnd || lastSpace < 0) {
            return cut.trimEnd()
        }
        return cut.substring(0, lastSpace)
    }
}
