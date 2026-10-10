package app.jonaki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.jonaki.JonakiApplication
import app.jonaki.settings.ToolPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Works out what a fresh start of the app shows. A start keeps the screen it
 * was given when a share from another app is arriving, when the first-run
 * tool picker is due, or when no model is set up yet (a new thread there
 * could not answer, so the list with its way to Settings stays).
 */
suspend fun chooseStart(application: JonakiApplication, leftThreadStore: LeftThreadStore): StartChoice {
    val settingsSnapshot = application.settings.snapshot.value
    val hasModel = settingsSnapshot.chatModels.allModelKeys.isNotEmpty() ||
        withContext(Dispatchers.IO) { application.localModelRuntime.modelKeys().isNotEmpty() }
    val hasGivenDestination = application.launchedWithShare ||
        ToolPicker.shouldShow(settingsSnapshot.toolPickerSeenVersion) ||
        !hasModel
    val record = leftThreadStore.read()
    val recordedThread = if (record == null) {
        null
    } else {
        withContext(Dispatchers.IO) { application.database.threadDao().find(record.threadId) }
    }
    return StartDestination.decide(
        hasGivenDestination = hasGivenDestination,
        record = record,
        recordedThread = recordedThread?.let { thread -> KnownThread(incognito = thread.incognito) },
        nowMillis = System.currentTimeMillis(),
    )
}

/** Shown for the moment the start is being decided, so the thread list does not flash before a thread opens. */
@Composable
fun StartBlank() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}

/**
 * Records the moment the user leaves the chat screen, or the app goes to the
 * background while the chat is open. A new empty thread has nothing to
 * reopen, so it clears the record instead.
 */
@Composable
fun RecordLeftThread(leftThreadStore: LeftThreadStore, threadId: String, isNewThread: Boolean) {
    val recordLeaving = {
        if (isNewThread) {
            leftThreadStore.clear()
        } else {
            leftThreadStore.record(threadId, System.currentTimeMillis())
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { recordLeaving() }
    DisposableEffect(threadId) {
        onDispose { recordLeaving() }
    }
}
