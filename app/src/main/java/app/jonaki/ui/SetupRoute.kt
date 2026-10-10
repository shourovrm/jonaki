package app.jonaki.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.jonaki.JonakiApplication
import app.jonaki.core.agent.ApprovalMode
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.feature.onboarding.SetupCard
import app.jonaki.feature.onboarding.SetupDeckActions
import app.jonaki.feature.onboarding.SetupDeckScreen
import app.jonaki.feature.onboarding.SetupDeckUi
import app.jonaki.feature.onboarding.SetupModelCardUi
import app.jonaki.feature.onboarding.SetupSearchCardUi
import app.jonaki.feature.onboarding.SetupServiceUi
import app.jonaki.feature.threads.SetupLeft
import app.jonaki.settings.ChatService
import app.jonaki.settings.SearchService
import kotlinx.coroutines.launch

/**
 * Where the first-run cards are (D-184). JonakiApp keeps it, because the
 * model picker takes the screen in the middle of the first card and the
 * deck must come back to the same place.
 */
@Stable
internal class SetupPlace(card: SetupCard = SetupCard.MODEL, modelServiceKey: String? = null, showsKeyStep: Boolean = false) {
    var card by mutableStateOf(card)
    var modelServiceKey by mutableStateOf(modelServiceKey)
    var showsKeyStep by mutableStateOf(showsKeyStep)

    /** Opens the deck at [card] from the thread list's "Left to set up". */
    fun openAt(card: SetupCard) {
        this.card = card
        showsKeyStep = false
    }

    companion object {
        val Saver = listSaver<SetupPlace, Any?>(
            save = { place -> listOf(place.card.name, place.modelServiceKey, place.showsKeyStep) },
            restore = { saved -> SetupPlace(SetupCard.valueOf(saved[0] as String), saved[1] as String?, saved[2] as Boolean) },
        )
    }
}

internal object Setup {
    // The services most people have a key for come first; the card lists the rest under "More services".
    private val firstServices = listOf(ChatService.OPENROUTER, ChatService.GEMINI, ChatService.DEEPSEEK, ChatService.OPENAI)

    /** The services the model card offers: the ones that work with a key alone. */
    fun modelServices(): List<ChatService> {
        val withKey = ChatService.entries.filter { service -> service.secret != null }
        return firstServices + (withKey - firstServices.toSet())
    }

    /** What the cards left undone, for the thread list; empty once the list is hidden or the cards were never needed. */
    fun left(deckSeen: Boolean, listHidden: Boolean, hasModel: Boolean, hasSearchKey: Boolean, notificationsOn: Boolean): List<SetupLeft> {
        if (!deckSeen || listHidden) {
            return emptyList()
        }
        val left = mutableListOf<SetupLeft>()
        if (!hasModel) {
            left += SetupLeft.MODEL
        }
        if (!hasSearchKey) {
            left += SetupLeft.WEB_SEARCH
        }
        if (!notificationsOn) {
            left += SetupLeft.NOTIFICATIONS
        }
        return left
    }

    fun cardOf(item: SetupLeft): SetupCard = when (item) {
        SetupLeft.MODEL -> SetupCard.MODEL
        SetupLeft.WEB_SEARCH -> SetupCard.SEARCH
        SetupLeft.NOTIFICATIONS -> SetupCard.NOTIFICATIONS
    }

    /** "openrouter.ai" for a service whose hint is a site; null for hints such as "Google". */
    fun keySiteOf(hint: String): String? = hint.takeIf { text -> '.' in text && ':' !in text && ' ' !in text }
}

@Composable
internal fun SetupRoute(
    application: JonakiApplication,
    place: SetupPlace,
    /** Opens the model picker of the service; it comes back to this route. */
    onChooseModel: (serviceKey: String) -> Unit,
    onFinished: () -> Unit,
) {
    val settings = application.settings
    val snapshot by settings.snapshot.collectAsState()
    val keyPreviews by application.secrets.previews.collectAsState()
    val scope = rememberCoroutineScope()
    var searchServiceName by rememberSaveable { mutableStateOf(snapshot.searchOrder.firstOrNull()?.name ?: SearchService.TAVILY.name) }
    val searchService = SearchService.valueOf(searchServiceName)
    val modelService = place.modelServiceKey?.let(ChatService::byKey)

    val finish = {
        settings.update { current -> current.copy(setupDeckSeen = true) }
        onFinished()
    }
    val showNextCard = {
        val next = SetupCard.entries.getOrNull(place.card.ordinal + 1)
        if (next == null) finish() else place.card = next
    }
    val goBack = {
        when {
            place.card == SetupCard.MODEL && place.showsKeyStep -> place.showsKeyStep = false
            place.card == SetupCard.MODEL -> finish()
            else -> place.card = SetupCard.entries[place.card.ordinal - 1]
        }
    }
    BackHandler { goBack() }

    val state = SetupDeckUi(
        card = place.card,
        model = SetupModelCardUi(
            services = Setup.modelServices().map { service -> SetupServiceUi(service.key, service.displayName) },
            selectedServiceKey = place.modelServiceKey,
            showsKeyStep = place.showsKeyStep && modelService != null,
            selectedServiceName = modelService?.displayName.orEmpty(),
            keySite = modelService?.let { service -> Setup.keySiteOf(hintFor(service)) },
            savedKeyPreview = modelService?.secret?.let { secret -> keyPreviews[secret] },
            chosenModelName = snapshot.chatModels.defaultModelKey?.let { modelKey ->
                application.catalog.find(modelKey)?.displayName ?: ModelKey.modelOf(modelKey)
            },
        ),
        search = SetupSearchCardUi(
            services = SearchService.entries.map { service -> SetupServiceUi(service.name, displayNameOf(service)) },
            selectedServiceKey = searchService.name,
            selectedServiceName = displayNameOf(searchService),
            savedKeyPreview = keyPreviews[searchService.secret],
        ),
        asksOnlyOutsideThreadFolder = snapshot.defaultApprovalMode == ApprovalMode.AUTO,
    )
    val actions = SetupDeckActions(
        onSkipAll = finish,
        onSkip = showNextCard,
        onBack = goBack,
        onModelServiceSelect = { serviceKey -> place.modelServiceKey = serviceKey },
        onModelServiceConfirm = { place.showsKeyStep = true },
        onChooseModel = { typedKey ->
            val secret = modelService?.secret
            if (modelService != null && secret != null) {
                if (typedKey.isNotBlank()) {
                    application.secrets.save(secret, typedKey.trim())
                }
                settings.updateChatModels { models -> models.addService(modelService) }
                onChooseModel(modelService.key)
            }
        },
        onModelDone = showNextCard,
        onSearchServiceSelect = { serviceKey -> searchServiceName = serviceKey },
        onSearchDone = { typedKey ->
            if (typedKey.isNotBlank()) {
                application.secrets.save(searchService.secret, typedKey.trim())
            }
            // The service whose key was just given is tried first.
            settings.update { current -> current.copy(searchOrder = listOf(searchService) + (current.searchOrder - searchService)) }
            showNextCard()
        },
        onApprovalChange = { onlyOutsideThreadFolder ->
            settings.update { current ->
                current.copy(defaultApprovalMode = if (onlyOutsideThreadFolder) ApprovalMode.AUTO else ApprovalMode.ASK)
            }
        },
        onApprovalDone = showNextCard,
        onAllowNotifications = {
            scope.launch {
                application.runtimePermissions.requestNotifications()
                showNextCard()
            }
        },
    )
    SetupDeckScreen(state, actions)
}
