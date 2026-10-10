package app.jonaki.core.toolapi

/**
 * The choice of an image model by the tools that call an [ImageGenerator]
 * (generate_image and generate_vector_image). Each tool passes only the model
 * keys it may use, so the two tools never pick each other's models.
 */
object ImageModelChoice {
    /**
     * The model a call uses: [requested] as a full "service:model" key, or as
     * just the model id when exactly one of [modelKeys] has that id; when the
     * call names none, [defaultModelKey] if it is listed, else the first key.
     * Null when a requested name matches nothing or is ambiguous.
     */
    fun choose(requested: String?, modelKeys: List<String>, defaultModelKey: String?): String? {
        val wanted = requested?.trim().orEmpty()
        if (wanted.isEmpty()) {
            return defaultModelKey?.takeIf { it in modelKeys } ?: modelKeys.firstOrNull()
        }
        modelKeys.firstOrNull { modelKey -> modelKey.equals(wanted, ignoreCase = true) }?.let { return it }
        val sameId = modelKeys.filter { modelKey -> modelKey.substringAfter(':').equals(wanted, ignoreCase = true) }
        return sameId.singleOrNull()
    }
}

/** The texts the image tools return when the [ImageGenerator] fails; the kinds are the same for both tools. */
object ImageFailureTexts {
    fun toolOutput(failure: ImageOutcome.Failed, serviceKey: String, modelKey: String): ToolOutput {
        val said = failure.message.trim().ifEmpty { "no reason given" }
        return when (failure.kind) {
            ImageFailure.KEY_PROBLEM -> ToolOutput.error(
                "$serviceKey did not accept the request: $said",
                "Tell the user to save a working key for $serviceKey in Settings. Do not retry.",
            )
            ImageFailure.OUT_OF_CREDIT -> ToolOutput.error(
                "$serviceKey has no credit or quota for this picture: $said",
                "Tell the user to add credit or check the quota with $serviceKey. Do not retry.",
            )
            ImageFailure.SERVICE_LIMIT -> ToolOutput.error(
                "$modelKey is over a limit at the service, not at the user's account: $said",
                "No picture was made and nothing was charged. The user's key and credit are fine. " +
                    "Tell the user that, and that they can try again later or pick another image model. Do not retry now.",
            )
            ImageFailure.BLOCKED -> ToolOutput.error(
                "$modelKey refused the prompt: $said",
                "Tell the user. Rephrase the prompt only if it can be done without the refused content; a retry can cost money.",
            )
            ImageFailure.TIMED_OUT -> ToolOutput.error(
                "$modelKey did not finish in time: $said",
                "No picture was saved. Tell the user; try once more, or another image model if they added one.",
            )
            ImageFailure.NO_IMAGE -> ToolOutput.error(
                "the answer of $modelKey held no picture: $said",
                "Try again with a clearer prompt, or another image model; tell the user if it fails twice.",
            )
            ImageFailure.OTHER -> ToolOutput.error(
                "picture generation with $modelKey failed: $said",
                "Tell the user what the service said; try again later or with another image model.",
            )
        }
    }
}
