package app.jonaki.phone

/**
 * How a reminder that nobody answered rings again (Settings > Files and
 * schedule). [maxRepeats] counts the rings after the first one; 0 means
 * ring once and wait for Done.
 */
data class ReminderPolicy(
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val maxRepeats: Int = DEFAULT_MAX_REPEATS,
) {
    val intervalMillis: Long
        get() = intervalMinutes * MILLIS_PER_MINUTE

    /** Moves a stored value into what the settings page offers, in case the options changed. */
    fun withinBounds(): ReminderPolicy = ReminderPolicy(
        intervalMinutes = if (intervalMinutes in INTERVAL_OPTIONS) intervalMinutes else DEFAULT_INTERVAL_MINUTES,
        maxRepeats = maxRepeats.coerceIn(0, MAX_REPEATS_LIMIT),
    )

    companion object {
        val INTERVAL_OPTIONS = listOf(5, 10, 15, 30, 60)
        const val DEFAULT_INTERVAL_MINUTES = 10
        const val DEFAULT_MAX_REPEATS = 5
        const val MAX_REPEATS_LIMIT = 10
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
