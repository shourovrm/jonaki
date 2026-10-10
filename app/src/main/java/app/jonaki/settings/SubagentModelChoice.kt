package app.jonaki.settings

import app.jonaki.core.modelcatalog.BatchModels

/** Which model each subagent type runs on (D-065). */
object SubagentModelChoice {
    const val SCOUT = "scout"

    /**
     * A model named in the delegate call wins, then the type's setting while
     * that model is still scoped. Without either, a scout uses the
     * background model (D-036), being the fast and cheap type, and every
     * other type the thread's model.
     */
    fun choose(
        agentType: String,
        requestedModelKey: String?,
        savedChoices: Map<String, String>,
        scopedModelKeys: List<String>,
        threadModelKey: String,
        backgroundModelKey: String?,
    ): String {
        // A batch model the user added earlier stays in their list, but no subagent runs on it.
        if (requestedModelKey != null && !BatchModels.isBatchKey(requestedModelKey)) {
            return requestedModelKey
        }
        val saved = savedChoices[agentType]
        if (saved != null && saved in scopedModelKeys && !BatchModels.isBatchKey(saved)) {
            return saved
        }
        if (agentType == SCOUT && backgroundModelKey != null) {
            return backgroundModelKey
        }
        return threadModelKey
    }

    // Model ids may hold ':' (Ollama tags), so type and key are split at a tab.
    fun toText(choices: Map<String, String>): String =
        choices.entries.joinToString("\n") { (agentType, modelKey) -> "$agentType\t$modelKey" }

    fun fromText(text: String): Map<String, String> =
        text.lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return@mapNotNull null
            }
            parts[0] to parts[1]
        }.toMap()
}
