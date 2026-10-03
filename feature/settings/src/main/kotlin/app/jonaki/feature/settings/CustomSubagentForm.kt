package app.jonaki.feature.settings

/** Checks a custom subagent before it is saved (D-138). */
object CustomSubagentForm {
    enum class Problem {
        NAME_MISSING,
        NAME_INVALID,
        NAME_TAKEN,
        DESCRIPTION_MISSING,
    }

    const val MAX_NAME_LENGTH = 30

    /** The description is one line of delegate's guidelines, sent with every request of a thread. */
    const val MAX_DESCRIPTION_LENGTH = 200

    // A letter first, then letters and digits in parts joined by single hyphens: a name a model writes back exactly.
    private val NAME_PATTERN = Regex("[a-z][a-z0-9]*(-[a-z0-9]+)*")

    fun isValidName(name: String): Boolean = name.length <= MAX_NAME_LENGTH && NAME_PATTERN.matches(name)

    /**
     * Null when the subagent can be saved. [takenNames] holds the built-in
     * types and the other custom ones; [editingName] is the name it had
     * before this edit, which it may keep.
     */
    fun problem(name: String, description: String, takenNames: Set<String>, editingName: String?): Problem? {
        if (name.isBlank()) {
            return Problem.NAME_MISSING
        }
        if (!isValidName(name)) {
            return Problem.NAME_INVALID
        }
        if (name != editingName && name in takenNames) {
            return Problem.NAME_TAKEN
        }
        if (description.isBlank()) {
            return Problem.DESCRIPTION_MISSING
        }
        return null
    }
}
