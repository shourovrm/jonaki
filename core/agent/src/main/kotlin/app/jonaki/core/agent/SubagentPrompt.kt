package app.jonaki.core.agent

import app.jonaki.core.toolapi.Tool
import java.util.Locale

/** A subagent's system prompt and the text of its result (M7). */
internal object SubagentPrompt {
    /**
     * Built once per subagent from its starting tools, so it stays the same
     * for all its requests (D-005). Tools granted later reach the model only
     * through the request's tool list.
     */
    fun systemPrompt(
        type: AgentType,
        startTools: List<Tool>,
        limits: SubagentLimits,
        hasKnownPrice: Boolean,
        memorySection: String,
        skillSection: String,
    ): String {
        val budget = if (hasKnownPrice) {
            "${limits.maxToolSteps} tool steps and ${dollars(limits.costCapUsd)} of model cost"
        } else {
            "${limits.maxToolSteps} tool steps"
        }
        val base = """You are a ${type.name} subagent of Jonaki, an assistant in an Android app. Another agent gave you one task. You see nothing of its conversation with the user, and only your final answer goes back to it, so put everything it needs there.
Paths are relative to the thread's folder: inbox/ holds the user's files, work/ is for notes and drafts, artifacts/ is for finished results.
Your budget is $budget. When it runs out you must stop, so plan few steps.
You can read the memory below but not save facts; report new facts in your answer.

${type.instructions}"""
        val skills = if (type.seesSkills) skillSection else ""
        return PromptBuilder(base).systemPrompt(startTools, memorySection = memorySection, skillSection = skills)
    }

    /** What goes back to the thread's agent: the answer, how it ended, and the skipped parts. */
    fun resultText(outcome: SubagentOutcome, limits: SubagentLimits): String {
        val parts = mutableListOf<String>()
        val endedEarly = outcome.stop != SubagentStop.COMPLETED && outcome.stop != SubagentStop.STEP_LIMIT
        if (endedEarly && outcome.answer.isNotBlank()) {
            parts += "It has no final answer. What it wrote and found so far:"
        }
        parts += outcome.answer.ifBlank { "(No answer.)" }
        val ending = endingOf(outcome, limits)
        if (ending != null) {
            parts += ending
        }
        if (outcome.skipped.isNotEmpty()) {
            parts += "Skipped (nobody answered the approval within 3 minutes):\n" +
                outcome.skipped.joinToString("\n") { part -> "- $part" }
        }
        return parts.joinToString("\n\n")
    }

    private fun endingOf(outcome: SubagentOutcome, limits: SubagentLimits): String? = when (outcome.stop) {
        SubagentStop.COMPLETED -> null
        SubagentStop.STEP_LIMIT -> "Stopped by the step limit of ${limits.maxToolSteps} tool steps."
        SubagentStop.COST_LIMIT -> "Stopped by the cost limit of ${dollars(limits.costCapUsd)}."
        SubagentStop.TIME_LIMIT -> "Stopped by the time limit of ${limits.timeLimit.inWholeMinutes} minutes."
        SubagentStop.FAILED -> "Stopped because the model call failed: ${outcome.failure.orEmpty()}"
        SubagentStop.STOPPED -> "Stopped by the user."
    }

    private fun dollars(amount: Double): String = String.format(Locale.ENGLISH, "$%.2f", amount)
}
