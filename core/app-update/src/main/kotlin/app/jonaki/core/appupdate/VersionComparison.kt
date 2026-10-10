package app.jonaki.core.appupdate

/**
 * Splits "v1.4.10" into [1, 4, 10]. A suffix after a dash ("-beta1") is dropped.
 * Returns null when any part is not a whole number, so a tag such as "nightly"
 * is never mistaken for a version.
 */
fun parseVersionParts(versionText: String): List<Int>? {
    val withoutPrefix = versionText.trim().removePrefix("v").removePrefix("V")
    val numericPart = withoutPrefix.substringBefore('-')
    if (numericPart.isEmpty()) {
        return null
    }
    val parts = numericPart.split('.').map { part -> part.toIntOrNull() }
    if (parts.any { part -> part == null }) {
        return null
    }
    return parts.filterNotNull()
}

/**
 * True when [candidate] is a higher version than [installed], comparing each dotted
 * part as a number (1.4.10 is newer than 1.4.9). A missing part counts as 0.
 * When either text is not a version the answer is false, so the app never offers
 * an update it cannot justify.
 */
fun isNewerVersion(candidate: String, installed: String): Boolean {
    val candidateParts = parseVersionParts(candidate) ?: return false
    val installedParts = parseVersionParts(installed) ?: return false
    val partCount = maxOf(candidateParts.size, installedParts.size)
    for (index in 0 until partCount) {
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val installedPart = installedParts.getOrElse(index) { 0 }
        if (candidatePart != installedPart) {
            return candidatePart > installedPart
        }
    }
    return false
}
