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
     * The user's limits as they were when the run started. They stay fixed
     * for the whole run, so the delegate tool's prompt text, which names
     * them, keeps the provider's prompt cache valid (D-005).
     */
    val limitSettings: SubagentLimitSettings

    /**
     * Runs [tasks] in parallel and returns one report per task, in the same
     * order. [context] is the delegate call's own context.
     */
    suspend fun launch(tasks: List<SubagentTask>, context: ToolContext): List<SubagentReport>
}

/**
 * The subagent limits the user sets in Settings > Subagents. The defaults
 * are the user's rulings: 3 per delegate call (D-060), 10 steps, $0.10 and
 * 10 minutes per subagent (D-061), 2 per message without asking and a
 * warning above 5 (D-137).
 */
data class SubagentLimitSettings(
    /** Subagents a model may start per user message before a call waits for the user. */
    val startedWithoutAsking: Int = 2,
    /** Subagents one delegate call may start at once. */
    val perCall: Int = 3,
    /** Above this many in one message, the approval card warns about the cost. */
    val warnAbove: Int = 5,
    /** Tool steps of one subagent. */
    val toolSteps: Int = 10,
    /** Model cost one subagent may spend, in US cents, so that steps of 5 cents add up exactly. */
    val costCapCents: Int = 10,
    /** How long one subagent may run. */
    val minutes: Int = 10,
) {
    val costCapUsd: Double
        get() = costCapCents / 100.0

    /**
     * Every value moved into its range. A warning below the automatic limit
     * would show on every card, so [warnAbove] is at least
     * [startedWithoutAsking].
     */
    fun withinBounds(): SubagentLimitSettings {
        val automatic = startedWithoutAsking.coerceIn(STARTED_WITHOUT_ASKING_RANGE)
        return SubagentLimitSettings(
            startedWithoutAsking = automatic,
            perCall = perCall.coerceIn(PER_CALL_RANGE),
            warnAbove = warnAbove.coerceIn(WARN_ABOVE_RANGE).coerceAtLeast(automatic),
            toolSteps = toolSteps.coerceIn(TOOL_STEPS_RANGE),
            costCapCents = costCapCents.coerceIn(COST_CAP_CENTS_RANGE),
            minutes = minutes.coerceIn(MINUTES_RANGE),
        )
    }

    companion object {
        val STARTED_WITHOUT_ASKING_RANGE: IntRange = 0..10
        val PER_CALL_RANGE: IntRange = 1..6
        val WARN_ABOVE_RANGE: IntRange = 0..20
        val TOOL_STEPS_RANGE: IntRange = 1..30
        val COST_CAP_CENTS_RANGE: IntRange = 5..100
        val MINUTES_RANGE: IntRange = 1..30

        /** The cost cap moves in steps of 5 cents in Settings. */
        const val COST_CAP_STEP_CENTS = 5
    }
}

data class SubagentTypeInfo(
    /** "researcher", "scout", "writer", "worker", or the name of one the user made. */
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
