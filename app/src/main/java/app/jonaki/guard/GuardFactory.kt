package app.jonaki.guard

import app.jonaki.core.guardapi.Guard
import app.jonaki.core.guardapi.NoGuard
import app.jonaki.guards.jev.JevGuard
import app.jonaki.guards.jev.JevThresholds
import app.jonaki.settings.AppSettings
import app.jonaki.settings.JevStrictness
import app.jonaki.settings.SecretName
import app.jonaki.settings.SecretStore
import okhttp3.OkHttpClient

/** The Jev options of Settings > Guardrails, as one run reads them. */
data class JevOptions(
    /** A call that would show an ordinary card may run without it when Jev is sure. */
    val skipsCards: Boolean,
    /** Outside results, and facts saved after them, are screened for planted instructions. */
    val screensOutsideContent: Boolean,
    val strictness: JevStrictness,
) {
    val isOn: Boolean
        get() = skipsCards || screensOutsideContent
}

/**
 * Chooses the guard for one agent run. Call [create] at the start of a run,
 * so that switching an option or removing the key takes effect on the next
 * message without restarting the app.
 */
class GuardFactory(
    private val options: () -> JevOptions,
    private val openRouterKey: () -> String?,
    private val httpClient: OkHttpClient,
) {
    constructor(settings: AppSettings, secrets: SecretStore, httpClient: OkHttpClient) : this(
        options = { settings.snapshot.value.jevOptions },
        openRouterKey = { readOpenRouterKey(secrets) },
        httpClient = httpClient,
    )

    fun create(): Guard {
        val current = options()
        if (!current.isOn) {
            return NoGuard
        }
        val key = openRouterKey()
        if (key.isNullOrBlank()) {
            return NoGuard
        }
        val jev = JevGuard(key, httpClient, thresholds = thresholdsOf(current.strictness))
        if (current.skipsCards && current.screensOutsideContent) {
            return jev
        }
        return OneJobGuard(jev, judgesActions = current.skipsCards, screensResults = current.screensOutsideContent)
    }

    companion object {
        fun thresholdsOf(strictness: JevStrictness): JevThresholds = when (strictness) {
            JevStrictness.CAREFUL -> JevThresholds.CAREFUL
            JevStrictness.BALANCED -> JevThresholds.BALANCED
            JevStrictness.RELAXED -> JevThresholds.RELAXED
        }
    }
}

/** A key that no longer decrypts (for example after a Keystore reset) counts as no key. */
private fun readOpenRouterKey(secrets: SecretStore): String? =
    runCatching { secrets.read(SecretName.OPENROUTER) }.getOrNull()
