package app.jonaki.guard

import app.jonaki.core.guardapi.Guard
import app.jonaki.core.guardapi.NoGuard
import app.jonaki.guards.jev.JevGuard
import app.jonaki.settings.AppSettings
import app.jonaki.settings.SecretName
import app.jonaki.settings.SecretStore
import okhttp3.OkHttpClient

/**
 * Chooses the guard for one agent run. Call [create] at the start of a run,
 * so that switching the setting or removing the key takes effect on the next
 * message without restarting the app.
 */
class GuardFactory(
    private val isJevGuardOn: () -> Boolean,
    private val openRouterKey: () -> String?,
    private val httpClient: OkHttpClient,
) {
    constructor(settings: AppSettings, secrets: SecretStore, httpClient: OkHttpClient) : this(
        isJevGuardOn = { settings.snapshot.value.jevGuardOn },
        openRouterKey = { readOpenRouterKey(secrets) },
        httpClient = httpClient,
    )

    fun create(): Guard {
        if (!isJevGuardOn()) {
            return NoGuard
        }
        val key = openRouterKey()
        if (key.isNullOrBlank()) {
            return NoGuard
        }
        return JevGuard(key, httpClient)
    }
}

/** A key that no longer decrypts (for example after a Keystore reset) counts as no key. */
private fun readOpenRouterKey(secrets: SecretStore): String? =
    runCatching { secrets.read(SecretName.OPENROUTER) }.getOrNull()
