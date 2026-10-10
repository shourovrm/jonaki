package app.jonaki.core.agent

/**
 * Tells a model that a service has withdrawn from a failure of any other
 * kind. Only a withdrawn model justifies running the request on another one;
 * a bad key, a quota or a lost network connection would fail there as well.
 */
internal object ModelUnavailable {
    /** The providers write "<service> answered HTTP <status>: <message>"; see their error mapping. */
    private val statusPattern = Regex("""HTTP (404|400)\b""")

    /** Phrases of the services' "no such model" answers: OpenRouter, OpenAI, DeepSeek, Gemini, Ollama. */
    private val missingModelPhrases = listOf(
        "no endpoints found",
        "model_not_found",
        "unknown model",
        "not a valid model",
        "invalid model",
    )

    fun isUnavailable(failureMessage: String): Boolean {
        if (!statusPattern.containsMatchIn(failureMessage)) {
            return false
        }
        val lowerCaseMessage = failureMessage.lowercase()
        // OpenRouter also answers 404 "No endpoints found" when the user's data policy excludes every
        // provider of an existing model; another model would not be what the user chose.
        if (lowerCaseMessage.contains("data policy")) {
            return false
        }
        if (missingModelPhrases.any { phrase -> lowerCaseMessage.contains(phrase) }) {
            return true
        }
        // "model 'x' not found", "Model Not Exist", "The model `x` does not exist", "models/x is not found".
        val namesModel = lowerCaseMessage.contains("model")
        return namesModel && (lowerCaseMessage.contains("not found") || lowerCaseMessage.contains("not exist"))
    }
}
