package app.jonaki.settings

import app.jonaki.core.providerapi.ThinkingLevel

/** Thinking levels per model in settings, and which one a run uses (D-057). */
object ThinkingLevels {
    fun toText(levels: Map<String, ThinkingLevel>): String =
        levels.entries.joinToString("\n") { (modelKey, level) -> "$modelKey\t${level.name}" }

    fun fromText(text: String): Map<String, ThinkingLevel> =
        text.lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 2) return@mapNotNull null
            val level = ThinkingLevel.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
            parts[0] to level
        }.toMap()

    /**
     * The thread's own choice wins, then the model's setting; null leaves
     * the model's default. A model without the setting never gets a level,
     * because some services reject a field they do not know.
     */
    fun effective(threadLevel: String?, modelLevel: ThinkingLevel?, isSupported: Boolean): ThinkingLevel? {
        if (!isSupported) {
            return null
        }
        val fromThread = ThinkingLevel.entries.firstOrNull { level -> level.name == threadLevel }
        return fromThread ?: modelLevel
    }
}
