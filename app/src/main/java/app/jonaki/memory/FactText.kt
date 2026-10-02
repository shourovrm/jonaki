package app.jonaki.memory

import app.jonaki.core.storage.MemoryEntity

/** Compares fact texts the way a reader would: case, extra spaces and a final full stop do not count. */
object FactText {
    private val whitespace = Regex("\\s+")

    fun normalized(text: String): String =
        text.trim().replace(whitespace, " ").trimEnd('.', '।', ' ').lowercase()

    /** The first fact among [facts] with the same text as [text], or null. */
    fun findSame(facts: List<MemoryEntity>, text: String): MemoryEntity? {
        val wanted = normalized(text)
        return facts.firstOrNull { fact -> normalized(fact.text) == wanted }
    }
}
