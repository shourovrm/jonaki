package app.jonaki.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.jonaki.JonakiApplication
import app.jonaki.core.agent.ApprovalMode
import app.jonaki.core.localmodels.FitLabel
import app.jonaki.core.localmodels.MemoryFit
import app.jonaki.core.localmodels.RecommendedModel
import app.jonaki.core.localmodels.RecommendedModels
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.feature.onboarding.SetupCard
import app.jonaki.feature.onboarding.SetupDeckActions
import app.jonaki.feature.onboarding.SetupDeckScreen
import app.jonaki.feature.onboarding.SetupDeckUi
import app.jonaki.feature.onboarding.SetupFit
import app.jonaki.feature.onboarding.SetupModelCardUi
import app.jonaki.feature.onboarding.SetupPhoneModelUi
import app.jonaki.feature.onboarding.SetupSearchCardUi
import app.jonaki.feature.onboarding.SetupServiceUi
import app.jonaki.feature.onboarding.SetupStart
import app.jonaki.feature.threads.SetupLeft
import app.jonaki.localmodels.DeviceResources
import app.jonaki.localmodels.DownloadSpec
import app.jonaki.run.ChatProviders
import app.jonaki.settings.ChatModels
import app.jonaki.settings.ChatService
import app.jonaki.settings.FreeTierUsage
import app.jonaki.settings.SearchService
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where the first-run cards are (D-184). JonakiApp keeps it, because the
 * model picker takes the screen in the middle of the first card and the
 * deck must come back to the same place.
 */
@Stable
internal class SetupPlace(
    card: SetupCard = SetupCard.MODEL,
    modelServiceKey: String? = null,
    showsKeyStep: Boolean = false,
    start: SetupStart = SetupStart.JONAKI_FREE,
    showsServiceList: Boolean = false,
) {
    var card by mutableStateOf(card)
    var modelServiceKey by mutableStateOf(modelServiceKey)
    var showsKeyStep by mutableStateOf(showsKeyStep)
    var start by mutableStateOf(start)
    var showsServiceList by mutableStateOf(showsServiceList)

    /** Opens the deck at [card] from the thread list's "Left to set up". */
    fun openAt(card: SetupCard) {
        this.card = card
        showsKeyStep = false
        showsServiceList = false
    }

    companion object {
        val Saver = listSaver<SetupPlace, Any?>(
            save = { place -> listOf(place.card.name, place.modelServiceKey, place.showsKeyStep, place.start.name, place.showsServiceList) },
            restore = { saved ->
                SetupPlace(
                    SetupCard.valueOf(saved[0] as String),
                    saved[1] as String?,
                    saved[2] as Boolean,
                    SetupStart.valueOf(saved[3] as String),
                    saved[4] as Boolean,
                )
            },
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

    /** The model the card shows as chosen: the starred one when it is the service's, else the service's first. */
    fun chosenModelKeyOf(chatModels: ChatModels, service: ChatService): String? {
        val starred = chatModels.defaultModelKey
        if (starred != null && ModelKey.serviceOf(starred) == service.key) {
            return starred
        }
        return chatModels.modelsByService[service].orEmpty().firstOrNull()?.let { modelId -> ModelKey.of(service.key, modelId) }
    }

    /** "0.5 GB" for the button; one decimal in gigabytes, where the Settings page switches to MB below 1 GB. */
    fun gigabytesText(bytes: Long): String = String.format(Locale.ENGLISH, "%.1f GB", bytes / 1_000_000_000.0)

    fun fitOf(model: RecommendedModel, budgetBytes: Long): SetupFit = when (MemoryFit.label(model.memoryNeed, budgetBytes)) {
        FitLabel.FITS -> SetupFit.FITS
        FitLabel.TIGHT -> SetupFit.TIGHT
        FitLabel.TOO_BIG -> SetupFit.TOO_BIG
    }
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
    val localModels = application.localModels
    val phoneModels = RecommendedModels.ALL.take(PHONE_MODEL_COUNT)
    val phoneBudgetBytes = remember {
        val memory = DeviceResources.memory(application)
        MemoryFit.budgetBytes(memory.availableBytes, memory.totalBytes)
    }
    var phoneFileName by rememberSaveable { mutableStateOf(phoneModels.first().fileName) }
    var phoneDownloadStarted by rememberSaveable { mutableStateOf(false) }
    var phoneDownloadRefused by rememberSaveable { mutableStateOf(false) }

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
            place.card == SetupCard.MODEL && place.showsServiceList -> place.showsServiceList = false
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
            showsServiceList = place.showsServiceList,
            showsKeyStep = place.showsKeyStep && modelService != null,
            start = place.start,
            freeDailyLimit = FreeTierUsage.DAILY_CALL_LIMIT,
            phoneModels = phoneModels.map { model ->
                SetupPhoneModelUi(model.fileName, model.name, Setup.gigabytesText(model.sizeBytes), Setup.fitOf(model, phoneBudgetBytes))
            },
            selectedPhoneModelFileName = phoneFileName,
            phoneDownloadStarted = phoneDownloadStarted,
            phoneDownloadRefused = phoneDownloadRefused,
            selectedServiceName = modelService?.displayName.orEmpty(),
            keySite = modelService?.let { service -> Setup.keySiteOf(hintFor(service)) },
            savedKeyPreview = modelService?.secret?.let { secret -> keyPreviews[secret] },
            chosenModelName = modelService?.let { service ->
                Setup.chosenModelKeyOf(snapshot.chatModels, service)?.let { modelKey -> modelNameOf(application, modelKey) }
            },
            defaultModelName = modelService?.let { service ->
                modelNameOf(application, ModelKey.of(service.key, ChatProviders.defaultModel(service)))
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
        onStartSelect = { start -> place.start = start },
        onStartConfirm = {
            if (place.start == SetupStart.OWN_KEY) {
                place.showsServiceList = true
            } else {
                settings.updateChatModels { models ->
                    models.addModel(ChatService.JONAKI_FREE, JONAKI_FREE_MODEL_ID).setDefault(ModelKey.of(ChatService.JONAKI_FREE.key, JONAKI_FREE_MODEL_ID))
                }
                showNextCard()
            }
        },
        onPhoneModelSelect = { fileName -> phoneFileName = fileName },
        onPhoneDownload = { fileName ->
            val model = phoneModels.firstOrNull { candidate -> candidate.fileName == fileName }
            if (model != null) {
                phoneDownloadStarted = true
                scope.launch {
                    val spec = DownloadSpec(model.downloadUrl, model.fileName, model.sizeBytes, model.sha256)
                    val refused = withContext(Dispatchers.IO) { localModels.downloads.start(spec) }
                    phoneDownloadRefused = refused != null
                    phoneDownloadStarted = refused == null
                }
            }
        },
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
        onModelDone = { typedKey ->
            val secret = modelService?.secret
            if (modelService != null && secret != null) {
                if (typedKey.isNotBlank()) {
                    application.secrets.save(secret, typedKey.trim())
                }
                if (Setup.chosenModelKeyOf(settings.snapshot.value.chatModels, modelService) == null) {
                    val defaultModelId = ChatProviders.defaultModel(modelService)
                    settings.updateChatModels { models ->
                        models.addModel(modelService, defaultModelId).setDefault(ModelKey.of(modelService.key, defaultModelId))
                    }
                }
                showNextCard()
            }
        },
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

private const val PHONE_MODEL_COUNT = 2
private const val JONAKI_FREE_MODEL_ID = "free"

private fun modelNameOf(application: JonakiApplication, modelKey: String): String =
    application.catalog.find(modelKey)?.displayName ?: ModelKey.modelOf(modelKey)
