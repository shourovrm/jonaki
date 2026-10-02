package app.jonaki.files

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.Lifecycle
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Lets code outside the screen (the share_file tool, which runs in the agent
 * service) open a picker or the share sheet in Jonaki's window and wait for
 * the answer (D-045). Android only lets a visible activity start these, so
 * every call answers [Answer.NotOnScreen] while Jonaki is in the background.
 */
class VisibleActivity {
    /** The activity's answer, or why there was none. */
    sealed interface Answer<out O> {
        data class Result<O>(val value: O) : Answer<O>

        data object NotOnScreen : Answer<Nothing>

        /** No app on the phone handles the intent. */
        data object NoAppToHandle : Answer<Nothing>
    }

    /** A picker that is open. It outlives a rotation, which replaces the activity that opened it. */
    private class OpenRequest(
        val register: (ComponentActivity) -> ActivityResultLauncher<*>,
        val giveUp: () -> Unit,
    ) {
        /** Null after the opening activity was destroyed for a rotation, until the new one registers again. */
        var owner: ComponentActivity? = null
        var launcher: ActivityResultLauncher<*>? = null
    }

    // Touched only on the main thread. A share from another app can start a
    // second MainActivity in that app's task, so more than one can be alive.
    private val activities = mutableListOf<ComponentActivity>()
    private var openRequest: OpenRequest? = null
    private var requestCount = 0

    /** Called from the activity's onCreate. */
    fun attach(activity: ComponentActivity) {
        activities += activity
        val request = openRequest ?: return
        if (request.owner != null) {
            return
        }
        // A rotation recreated the activity while a picker was open; the new
        // registry holds the picker's result under the same key and hands it
        // over as soon as the key is registered again.
        request.owner = activity
        request.launcher = request.register(activity)
    }

    /** Called from the activity's onDestroy. */
    fun detach(activity: ComponentActivity) {
        activities.remove(activity)
        val request = openRequest ?: return
        if (request.owner !== activity) {
            return
        }
        request.launcher?.unregister()
        request.launcher = null
        request.owner = null
        if (!activity.isChangingConfigurations) {
            // The user left that window for good; no result will come.
            request.giveUp()
        }
    }

    /** Starts [intent], for example the share sheet, and does not wait for it to close. */
    suspend fun start(intent: Intent): Answer<Unit> = withContext(Dispatchers.Main) {
        val current = activityOnScreen() ?: return@withContext Answer.NotOnScreen
        try {
            current.startActivity(intent)
            Answer.Result(Unit)
        } catch (missing: ActivityNotFoundException) {
            Answer.NoAppToHandle
        }
    }

    /** Opens [contract]'s screen and suspends until the user answers it. One at a time. */
    suspend fun <I, O> launchForResult(contract: ActivityResultContract<I, O>, input: I): Answer<O> =
        withContext<Answer<O>>(Dispatchers.Main) {
            val current = activityOnScreen()
            if (current == null || openRequest != null) {
                return@withContext Answer.NotOnScreen
            }
            requestCount++
            val key = "visible-activity-$requestCount"
            suspendCancellableCoroutine { continuation ->
                lateinit var request: OpenRequest
                fun finish(answer: Answer<O>) {
                    if (openRequest !== request) {
                        return
                    }
                    openRequest = null
                    request.launcher?.unregister()
                    if (continuation.isActive) {
                        continuation.resume(answer)
                    }
                }
                request = OpenRequest(
                    register = { target ->
                        target.activityResultRegistry.register(key, contract) { output -> finish(Answer.Result(output)) }
                    },
                    giveUp = { finish(Answer.NotOnScreen) },
                )
                openRequest = request
                request.owner = current
                @Suppress("UNCHECKED_CAST")
                val launcher = request.register(current) as ActivityResultLauncher<I>
                request.launcher = launcher
                continuation.invokeOnCancellation {
                    // May run on any thread; the request is cleared on the main thread.
                    current.runOnUiThread { finish(Answer.NotOnScreen) }
                }
                try {
                    launcher.launch(input)
                } catch (missing: ActivityNotFoundException) {
                    finish(Answer.NoAppToHandle)
                }
            }
        }

    /** The newest activity that is at least started, which is the one the user sees. */
    private fun activityOnScreen(): ComponentActivity? =
        activities.lastOrNull { activity -> activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
}
