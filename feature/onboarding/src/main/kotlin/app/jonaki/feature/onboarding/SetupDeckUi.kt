package app.jonaki.feature.onboarding

import androidx.compose.runtime.Immutable

/** The four first-run cards, in the order they are shown (D-184). */
enum class SetupCard {
    MODEL,
    SEARCH,
    APPROVALS,
    NOTIFICATIONS,
}

/** The three ways card 1 offers to start. */
enum class SetupStart {
    JONAKI_FREE,
    ON_PHONE,
    OWN_KEY,
}

/** Whether a model fits the phone's memory, in the words of Settings > Local models. */
enum class SetupFit {
    FITS,
    TIGHT,
    TOO_BIG,
}

@Immutable
data class SetupPhoneModelUi(
    /** The file name, which is also the model's id on the phone. */
    val fileName: String,
    val name: String,
    /** The download size, for example "0.5 GB". */
    val sizeText: String,
    val fit: SetupFit,
)

@Immutable
data class SetupServiceUi(
    /** Stable id, for example "openrouter" or "TAVILY". */
    val key: String,
    val name: String,
)

/** The model card: first the service, then its key and one model. */
@Immutable
data class SetupModelCardUi(
    /** The first [SetupDeckUi.SHORT_LIST_SIZE] are listed; the rest open with "More services". */
    val services: List<SetupServiceUi> = emptyList(),
    val selectedServiceKey: String? = null,
    /** True shows the service list of "My own key"; false shows the three ways to start. */
    val showsServiceList: Boolean = false,
    /** True shows the key and the model of the selected service. */
    val showsKeyStep: Boolean = false,
    val start: SetupStart = SetupStart.JONAKI_FREE,
    /** Calls a day that Jonaki Free allows on one phone. */
    val freeDailyLimit: Int = 0,
    /** The two smallest recommended local models. */
    val phoneModels: List<SetupPhoneModelUi> = emptyList(),
    val selectedPhoneModelFileName: String? = null,
    /** True once Download was pressed; the card then says where the model will appear. */
    val phoneDownloadStarted: Boolean = false,
    /** True when the phone had too little free storage for the download. */
    val phoneDownloadRefused: Boolean = false,
    val selectedServiceName: String = "",
    /** Where a key for the selected service is made, for example "openrouter.ai"; null shows no line. */
    val keySite: String? = null,
    /** The saved key with its middle hidden; null while none is saved. */
    val savedKeyPreview: String? = null,
    /** The name of the selected service's starred model; null until one is chosen. */
    val chosenModelName: String? = null,
    /** The name of the selected service's default model, preselected once a key is typed or saved. */
    val defaultModelName: String? = null,
)

@Immutable
data class SetupSearchCardUi(
    val services: List<SetupServiceUi> = emptyList(),
    val selectedServiceKey: String = "",
    val selectedServiceName: String = "",
    val savedKeyPreview: String? = null,
)

@Immutable
data class SetupDeckUi(
    val card: SetupCard = SetupCard.MODEL,
    val model: SetupModelCardUi = SetupModelCardUi(),
    val search: SetupSearchCardUi = SetupSearchCardUi(),
    /** True is "only outside the thread's folder" (Auto); false is "every time" (Ask). */
    val asksOnlyOutsideThreadFolder: Boolean = false,
) {
    companion object {
        const val SHORT_LIST_SIZE = 4
    }
}

data class SetupDeckActions(
    val onSkipAll: () -> Unit = {},
    /** Leaves the card as it is and shows the next one; the last card ends the deck. */
    val onSkip: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onStartSelect: (start: SetupStart) -> Unit = {},
    /** "Next" on the three ways to start. Jonaki Free is added here; "My own key" shows the service list. */
    val onStartConfirm: () -> Unit = {},
    val onPhoneModelSelect: (fileName: String) -> Unit = {},
    val onPhoneDownload: (fileName: String) -> Unit = {},
    val onModelServiceSelect: (serviceKey: String) -> Unit = {},
    /** "Next" on the service list: shows the key step. */
    val onModelServiceConfirm: () -> Unit = {},
    /** Saves [typedKey] when it is not blank, then opens the model picker of the selected service. */
    val onChooseModel: (typedKey: String) -> Unit = {},
    /** Saves [typedKey] when it is not blank and adds the service's default model if none is chosen. */
    val onModelDone: (typedKey: String) -> Unit = {},
    val onSearchServiceSelect: (serviceKey: String) -> Unit = {},
    /** Saves [typedKey] when it is not blank and puts the service first in the search order. */
    val onSearchDone: (typedKey: String) -> Unit = {},
    val onApprovalChange: (onlyOutsideThreadFolder: Boolean) -> Unit = {},
    val onApprovalDone: () -> Unit = {},
    /** Opens Android's permission question; the deck ends after the answer. */
    val onAllowNotifications: () -> Unit = {},
)
