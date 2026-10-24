package app.jonaki.providers.gemini

import app.jonaki.core.providerapi.FailureKind

/** Names the Gemini 429 answers the app shows a short text for. */
object GeminiFailures {
    /** A free-tier metric with "limit: 0" means the plan has no quota at all for that model. */
    private val freeTierWithoutQuota = Regex("""free_tier\w*,\s*limit:\s*0\b""")

    fun kindOf(statusCode: Int, bodyText: String): FailureKind? {
        if (statusCode != 429) return null
        if (freeTierWithoutQuota.containsMatchIn(bodyText)) return FailureKind.FREE_PLAN_MODEL
        return FailureKind.RATE_LIMIT
    }

    fun shortText(kind: FailureKind, requestedModel: String): String = when (kind) {
        FailureKind.FREE_PLAN_MODEL -> "Gemini's free plan does not include $requestedModel."
        FailureKind.RATE_LIMIT -> "Gemini is busy; try again in a minute."
        FailureKind.NO_CREDIT -> "Gemini wants billing before it answers again."
    }
}
