package app.jonaki.core.providerapi

/**
 * Whether an HTTP status means "try again later" (overload, rate limit, gateway
 * trouble) rather than a problem a retry cannot fix, such as a bad key.
 */
fun isRetryableHttpStatus(statusCode: Int): Boolean = when (statusCode) {
    408, 409, 425, 429, 500, 502, 503, 504, 529 -> true
    else -> false
}
