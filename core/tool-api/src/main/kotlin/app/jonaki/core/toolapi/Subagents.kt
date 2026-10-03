package app.jonaki.core.toolapi

/**
 * What the delegate tool needs to start subagents (M7, D-015). The tool
 * module sees only this interface; core/agent's subagent runner implements
 * it and the app builds one for each run.
 */
interface SubagentLauncher {
    /** The agent types a task may name, in the order the tool lists them. */
    val agentTypes: List<SubagentTypeInfo>

    /** The user's scoped models, which a task may name to run on. */
    val models: List<SubagentModelInfo>

    /** Tools of the thread that extra_tools may add to a subagent. */
    val extraToolNames: List<String>

    /** Subagents started so far in this run, that is, for the current user message (D-137). */
    val startedThisRun: Int

    /**
     * Runs [tasks] in parallel and returns one report per task, in the same
     * order. [context] is the delegate call's own context.
     */
    suspend fun launch(tasks: List<SubagentTask>, context: ToolContext): List<SubagentReport>

    companion object {
        /** Subagents of one delegate call; the user set 3 on 2026-10-03 (D-060). */
        const val MAX_PARALLEL = 3

        /**
         * Subagents a model may start in one run without asking; the delegate
         * call that would go beyond waits for the user (user ruling, D-137).
         */
        const val STARTED_WITHOUT_ASKING = 2

        /** Above this many in one run, the approval card warns about the cost (D-137). */
        const val WARN_ABOVE = 5
    }
}

data class SubagentTypeInfo(
    /** "researcher", "scout", "writer" or "worker". */
    val name: String,
    val description: String,
)

data class SubagentModelInfo(
    /** "service:modelId", for example "openrouter:z-ai/glm-5.3-flash". */
    val key: String,
    val displayName: String,
)

data class SubagentTask(
    val agentType: String,
    /** The whole task; the subagent sees nothing of the parent's conversation. */
    val task: String,
    val extraTools: List<String> = emptyList(),
    /** A scoped model's key; null uses the model set for the agent type. */
    val modelKey: String? = null,
)

data class SubagentReport(
    /** "researcher", or "researcher 2" when one call starts several. */
    val label: String,
    /** The subagent's answer, with how it ended and any skipped parts. */
    val text: String,
)
