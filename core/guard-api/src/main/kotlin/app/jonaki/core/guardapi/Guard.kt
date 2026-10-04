package app.jonaki.core.guardapi

import kotlinx.serialization.json.JsonObject

/**
 * The optional second layer on top of the fixed approval rules. It never
 * denies anything: an action either may run without a card or gets the card,
 * and a result is either flagged with a warning or passed on unchanged.
 */
interface Guard {
    /**
     * Call this before a card would be shown. [ActionVerdict.MayRunWithoutCard]
     * lets the action run; [ActionVerdict.ShowCard] means show the card as usual.
     * Never throws, except for coroutine cancellation.
     */
    suspend fun judgeAction(userRequest: String, toolName: String, arguments: JsonObject): ActionVerdict

    /**
     * Call this on text a tool returned from outside (web page, document,
     * transcript, MCP result), with exactly the text the model will see.
     * The text is never withheld; a flagged result gets a warning.
     * Never throws, except for coroutine cancellation.
     */
    suspend fun screenResult(source: String, text: String): ResultVerdict
}

/** The only two outcomes of [Guard.judgeAction]; there is deliberately no "deny". */
sealed interface ActionVerdict {
    /** Short text for logs: why this answer, or what failed. */
    val reason: String

    /** The cost of the call in US dollars; null when unknown or when no call was made. */
    val costUsd: Double?

    data class MayRunWithoutCard(override val reason: String, override val costUsd: Double? = null) : ActionVerdict

    data class ShowCard(override val reason: String, override val costUsd: Double? = null) : ActionVerdict
}

/**
 * The answer of [Guard.screenResult]. [injectionProbability] is the highest
 * probability over all parts of the text, or null when no answer was got.
 */
data class ResultVerdict(
    val isFlagged: Boolean,
    val injectionProbability: Double?,
    val reason: String,
    val costUsd: Double? = null,
)

/** Used while the guard is off: every action gets its card and no result is flagged. */
object NoGuard : Guard {
    private const val OFF_REASON = "Jev guard is off"

    override suspend fun judgeAction(userRequest: String, toolName: String, arguments: JsonObject): ActionVerdict =
        ActionVerdict.ShowCard(OFF_REASON)

    override suspend fun screenResult(source: String, text: String): ResultVerdict =
        ResultVerdict(isFlagged = false, injectionProbability = null, reason = OFF_REASON)
}
