package app.jonaki.guards.jev

/**
 * How sure Jev must be before its answer changes anything. Higher action
 * numbers mean fewer calls run without a card; a lower injection number
 * means more outside results get a warning.
 */
data class JevThresholds(
    /** An effect answer (read only, reversible) is trusted only at this confidence or above. */
    val effectConfidence: Double,
    /** The action must serve the user's request with at least this probability. */
    val servesRequest: Double,
    /** A text is flagged at this probability or above. */
    val injectionProbability: Double,
) {
    companion object {
        /**
         * The values spikes/jev-guard tested on 40 labelled cases: all 12 safe
         * actions ran without a card and all 12 risky ones got a card.
         */
        val BALANCED = JevThresholds(effectConfidence = 0.9, servesRequest = 0.65, injectionProbability = 0.65)

        /**
         * More cards and more warnings. The action values are not tested; the
         * injection value 0.5 had no miss and no false alarm in the spike.
         */
        val CAREFUL = JevThresholds(effectConfidence = 0.95, servesRequest = 0.8, injectionProbability = 0.5)

        /**
         * Fewer cards and fewer warnings. The action values are not tested; the
         * injection value 0.8 had no miss and no false alarm in the spike, and
         * the lowest poisoned real page scored 0.81.
         */
        val RELAXED = JevThresholds(effectConfidence = 0.8, servesRequest = 0.5, injectionProbability = 0.8)
    }
}
