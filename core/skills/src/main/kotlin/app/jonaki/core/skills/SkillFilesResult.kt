package app.jonaki.core.skills

/** A skill's files from a download or a picked file, before they go into the library. */
sealed interface SkillFilesResult {
    /** Every file of the skill by its path relative to the skill's folder. */
    data class Files(val files: Map<String, ByteArray>) : SkillFilesResult

    /** [reason] is shown to the user as it is. */
    data class Failed(val reason: String) : SkillFilesResult
}
