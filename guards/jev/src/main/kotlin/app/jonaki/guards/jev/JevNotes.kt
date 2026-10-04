package app.jonaki.guards.jev

import java.util.Locale

/**
 * The one line per guard question that the step's detail view shows. English
 * only, like other stored tool text; the labels around it are app strings.
 */
internal object JevNotes {
    fun ranWithoutCard(effect: String, effectConfidence: Double, servesRequest: Double): String =
        "Jev: ran without a card (${effectLabel(effect)} ${twoDecimals(effectConfidence)}, asked for ${twoDecimals(servesRequest)})"

    /** Lists every condition that stopped the action, because the spike showed two can stop one call at once. */
    fun cardShown(
        effect: String,
        effectConfidence: Double,
        servesRequest: Double,
        effectIsSafe: Boolean,
        effectIsSure: Boolean,
        requestIsServed: Boolean,
    ): String {
        val effectText = "${effectLabel(effect)} ${twoDecimals(effectConfidence)}"
        val reasons = mutableListOf<String>()
        if (!effectIsSafe) {
            reasons.add(effectText)
        } else if (!effectIsSure) {
            reasons.add("not sure of the effect, $effectText")
        }
        if (!requestIsServed) {
            reasons.add("not sure the user asked, ${twoDecimals(servesRequest)}")
        }
        return "Jev: card shown (${reasons.joinToString("; ")})"
    }

    fun noAnswerForAction(): String = "Jev: no answer, card shown"

    fun result(isFlagged: Boolean, probability: Double): String {
        val verdict = if (isFlagged) "flagged" else "clear"
        return "Jev: result $verdict (${twoDecimals(probability)})"
    }

    fun noAnswerForResult(): String = "Jev: no answer, result not checked"

    private fun effectLabel(effect: String): String = when (effect) {
        JevQuestions.READ_ONLY -> "read only"
        JevQuestions.SENDS_OUT -> "sends data out"
        else -> effect
    }

    private fun twoDecimals(number: Double): String = String.format(Locale.ROOT, "%.2f", number)
}
