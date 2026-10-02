package app.jonaki.tools.phone

/** Finds the app a model names, by its label as the phone shows it or by its package name. */
sealed interface AppMatch {
    data class Found(val app: LaunchableApp) : AppMatch

    data class Several(val apps: List<LaunchableApp>) : AppMatch

    data object None : AppMatch

    companion object {
        /** Lists at most this many names when a query is ambiguous, so the error stays short. */
        private const val MAX_LISTED = 10

        /**
         * An exact label or package name wins ("Maps" over "Google Maps Go");
         * otherwise labels that contain the query count, and one such label
         * is a match.
         */
        fun find(query: String, apps: List<LaunchableApp>): AppMatch {
            val wanted = query.trim()
            val exact = apps.filter { app ->
                app.label.equals(wanted, ignoreCase = true) || app.packageName.equals(wanted, ignoreCase = true)
            }
            val candidates = exact.ifEmpty {
                apps.filter { app -> app.label.contains(wanted, ignoreCase = true) }
            }
            return when (candidates.distinctBy { app -> app.packageName }.size) {
                0 -> None
                1 -> Found(candidates.first())
                else -> Several(candidates.sortedBy { app -> app.label.lowercase() }.take(MAX_LISTED))
            }
        }
    }
}
