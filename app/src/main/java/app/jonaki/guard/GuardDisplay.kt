package app.jonaki.guard

import app.jonaki.guards.jev.JevGuard

/** How the chat names the guard in use: its own name, and the service that runs it. */
data class GuardDisplay(val name: String, val provider: String)

/** The only guard today is Jev, run through OpenRouter. */
fun activeGuardDisplay(): GuardDisplay = GuardDisplay(JevGuard.DISPLAY_NAME, JevGuard.PROVIDER_NAME)
