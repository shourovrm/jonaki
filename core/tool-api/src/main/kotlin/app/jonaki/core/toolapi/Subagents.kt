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
 * What one subagent may use: tool steps, minutes and model cost. Each
 * subagent type has its own (D-061).
 */
data class SubagentBudget(
    /** Tool calls; the notes board does not count. */
    val toolSteps: Int,
    /** Model cost one subagent may spend, in US cents, so that steps of 5 cents add up exactly. */
    val costCapCents: Int,
    /** How long one subagent may run. */
    val minutes: Int,
) {
    val costCapUsd: Double
        get() = costCapCents / 100.0

    fun withinBounds(): SubagentBudget = SubagentBudget(
        toolSteps = toolSteps.coerceIn(SubagentLimitSettings.TOOL_STEPS_RANGE),
        costCapCents = costCapCents.coerceIn(SubagentLimitSettings.COST_CAP_CENTS_RANGE),
        minutes = minutes.coerceIn(SubagentLimitSettings.MINUTES_RANGE),
    )
}

/**
 * The subagent limits the user sets in Settings > Subagents. The defaults
 * are the user's rulings: 3 per delegate call (D-060), 2 per message without
 * asking (D-137) and at most 5 per message. The budget of one
 * subagent depends on its type: 10 steps, $0.10 and 10 minutes (D-061), and
 * for the researcher 20 steps, $0.20 and 15 minutes.
 */
data class SubagentLimitSettings(
    /** Subagents a model may start per user message before a call waits for the user. */
    val startedWithoutAsking: Int = 2,
    /** Subagents one delegate call may start at once. */
    val perCall: Int = 3,
    /**
     * The most subagents one user message should start. The thread's agent is
     * told it; a call that goes over waits for the user, who may allow it.
     */
    val maxPerMessage: Int = 5,
    /** The budgets the user changed, by type name; a type without an entry has its default. */
    val budgets: Map<String, SubagentBudget> = emptyMap(),
) {
    /** The budget of the type named [typeName], built-in or custom. */
    fun budgetFor(typeName: String): SubagentBudget = budgets[typeName] ?: defaultBudgetFor(typeName)

    /** The longest time limit among [typeNames], which a call that may start any of them must outlast. */
    fun longestMinutes(typeNames: List<String>): Int =
        typeNames.maxOfOrNull { typeName -> budgetFor(typeName).minutes } ?: DEFAULT_BUDGET.minutes

    /** The highest cost cap among [typeNames], for a warning that cannot know which type will run. */
    fun highestCostCapCents(typeNames: List<String>): Int =
        typeNames.maxOfOrNull { typeName -> budgetFor(typeName).costCapCents } ?: DEFAULT_BUDGET.costCapCents

    /**
     * Every value moved into its range. A cap below the automatic limit
     * would leave calls that wait for no reason, so [maxPerMessage] is at
     * least [startedWithoutAsking].
     */
    fun withinBounds(): SubagentLimitSettings {
        val automatic = startedWithoutAsking.coerceIn(STARTED_WITHOUT_ASKING_RANGE)
        return SubagentLimitSettings(
            startedWithoutAsking = automatic,
            perCall = perCall.coerceIn(PER_CALL_RANGE),
            maxPerMessage = maxPerMessage.coerceIn(MAX_PER_MESSAGE_RANGE).coerceAtLeast(automatic),
            budgets = budgets.mapValues { (_, budget) -> budget.withinBounds() },
        )
    }

    companion object {
        val STARTED_WITHOUT_ASKING_RANGE: IntRange = 0..10
        val PER_CALL_RANGE: IntRange = 1..6
        val MAX_PER_MESSAGE_RANGE: IntRange = 1..20
        val TOOL_STEPS_RANGE: IntRange = 1..30
        val COST_CAP_CENTS_RANGE: IntRange = 5..100
        val MINUTES_RANGE: IntRange = 1..30

        /** The cost cap moves in steps of 5 cents in Settings. */
        const val COST_CAP_STEP_CENTS = 5

        const val RESEARCHER = "researcher"

        val DEFAULT_BUDGET = SubagentBudget(toolSteps = 10, costCapCents = 10, minutes = 10)

        /** A researcher reads many pages, so it gets twice the steps and cost of the others. */
        val RESEARCHER_BUDGET = SubagentBudget(toolSteps = 20, costCapCents = 20, minutes = 15)

        fun defaultBudgetFor(typeName: String): SubagentBudget =
            if (typeName == RESEARCHER) RESEARCHER_BUDGET else DEFAULT_BUDGET
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
