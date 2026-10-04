package app.jonaki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.jonaki.JonakiApplication
import app.jonaki.feature.chat.ChatItem
import app.jonaki.feature.chat.PythonCardAction
import app.jonaki.run.PythonCoreStatus
import app.jonaki.tools.runcode.InstallNeed

/** What the chat needs for its Python install card: how to make it and what its buttons do. */
class PythonCardHooks(
    val cardFor: (toolCallId: String, need: InstallNeed) -> ChatItem?,
    val onAction: (card: ChatItem.PythonInstall, action: PythonCardAction) -> Unit,
)

/**
 * The install card's state for one thread (plan M8 step 4). Not now hides
 * the card until the thread is opened again; Try again sends a short
 * message so the model runs the program once more.
 */
@Composable
fun rememberPythonCardHooks(application: JonakiApplication, threadId: String, onTryAgain: () -> Unit): PythonCardHooks {
    val python = application.python
    val pythonState by python.state.collectAsState()
    var dismissedIds by rememberSaveable(threadId) { mutableStateOf(listOf<String>()) }
    return PythonCardHooks(
        cardFor = { toolCallId, need ->
            if (PythonCards.idFor(toolCallId) in dismissedIds || pythonState.coreStatus == PythonCoreStatus.CHECKING) {
                null
            } else {
                val isWithinDataAddOn = python.isWithinDataAddOn(need.packageNames)
                val isWithinDocumentsAddOn = python.isWithinDocumentsAddOn(need.packageNames)
                PythonCards.of(
                    toolCallId = toolCallId,
                    need = need,
                    python = pythonState,
                    downloadBytes = if (need.packageNames.isEmpty()) python.release.coreDownloadBytes else python.totalBytesFor(need.packageNames),
                    installTarget = PythonCards.installTarget(need, isWithinDataAddOn, isWithinDocumentsAddOn),
                    isDocumentsAddOn = isWithinDocumentsAddOn && !isWithinDataAddOn,
                )
            }
        },
        onAction = { card, action ->
            when (action) {
                PythonCardAction.INSTALL -> {
                    val target = PythonCards.installTarget(
                        InstallNeed(card.packageNames),
                        python.isWithinDataAddOn(card.packageNames),
                        python.isWithinDocumentsAddOn(card.packageNames),
                    )
                    if (target.isEmpty()) python.installCore() else python.installPackages(target)
                }
                PythonCardAction.CANCEL -> python.cancel()
                PythonCardAction.NOT_NOW -> dismissedIds = dismissedIds + card.id
                PythonCardAction.TRY_AGAIN -> {
                    dismissedIds = dismissedIds + card.id
                    onTryAgain()
                }
            }
        },
    )
}
