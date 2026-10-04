package app.jonaki.guard

import app.jonaki.core.guardapi.ActionVerdict
import app.jonaki.core.guardapi.Guard
import app.jonaki.core.guardapi.ResultVerdict
import kotlinx.serialization.json.JsonObject

/**
 * A guard with one of its two jobs switched off in Settings > Guardrails. The
 * switched-off job answers as if there were no guard (the card is shown, the
 * result is not flagged) without asking [guard], so it costs nothing and
 * leaves no note.
 */
class OneJobGuard(
    private val guard: Guard,
    private val judgesActions: Boolean,
    private val screensResults: Boolean,
) : Guard {
    override suspend fun judgeAction(userRequest: String, toolName: String, arguments: JsonObject): ActionVerdict {
        if (!judgesActions) {
            return ActionVerdict.ShowCard(ACTIONS_OFF_REASON)
        }
        return guard.judgeAction(userRequest, toolName, arguments)
    }

    override suspend fun screenResult(source: String, text: String): ResultVerdict {
        if (!screensResults) {
            return ResultVerdict(isFlagged = false, injectionProbability = null, reason = SCREENING_OFF_REASON)
        }
        return guard.screenResult(source, text)
    }

    private companion object {
        const val ACTIONS_OFF_REASON = "Skipping cards is off"
        const val SCREENING_OFF_REASON = "Screening outside content is off"
    }
}
