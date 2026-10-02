package app.jonaki.providers.gemini

/**
 * Gemini 3 rejects a follow-up request unless each function call it made is
 * sent back with its `thoughtSignature`. The signature is stored inside the
 * tool call's id, so it survives the database and process death without a
 * Gemini-only field in core's ToolCall. Base64 never contains '~'.
 */
object GeminiToolCallId {
    private const val SEPARATOR = "~"

    fun encode(callId: String, thoughtSignature: String?): String =
        if (thoughtSignature.isNullOrEmpty()) callId else callId + SEPARATOR + thoughtSignature

    fun callId(encodedId: String): String = encodedId.substringBefore(SEPARATOR)

    fun thoughtSignature(encodedId: String): String? =
        if (encodedId.contains(SEPARATOR)) encodedId.substringAfter(SEPARATOR) else null
}
