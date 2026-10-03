package app.jonaki.core.runtimeapi

/** Size limits shared by run_code and the engines, so both refuse the same files. */
object CodeRunLimits {
    /** Matches the 25 MB cap on files coming into a thread (D-046). */
    const val MAX_FILE_BYTES: Long = 25L * 1024 * 1024

    /** All files offered to one run, or written by one run, together. */
    const val MAX_TOTAL_BYTES: Long = 50L * 1024 * 1024
}
