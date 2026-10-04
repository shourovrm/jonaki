package app.jonaki.settings

/**
 * How sure the Jev guard must be (Settings > Guardrails). The names are
 * stored, so they must stay; the numbers behind them are in JevThresholds.
 */
enum class JevStrictness {
    /** More cards and more warnings. */
    CAREFUL,

    /** The values the guard was tested with. */
    BALANCED,

    /** Fewer cards and fewer warnings. */
    RELAXED,
}
