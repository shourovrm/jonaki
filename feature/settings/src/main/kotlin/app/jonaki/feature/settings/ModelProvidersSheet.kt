package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat

/** One provider of an OpenRouter model, ready to draw. */
data class ProviderOptionUi(
    /** Display name, for example "DeepInfra". */
    val name: String,
    /** The slug the request sends, for example "deepinfra/fp4". */
    val tag: String,
    /** The tag again when it names a variant such as "parasail/fast"; null otherwise. */
    val variantTag: String?,
    val inputPricePerMillion: Double?,
    val outputPricePerMillion: Double?,
    /** For example "fp4"; null when unknown. */
    val quantization: String?,
    /** What the provider's data policy says; null when unknown, which shows nothing. */
    val privacy: ProviderPrivacyUi? = null,
    /** The price of input read from this provider's prompt cache; null when it names none, which shows a dash. */
    val cachedInputPricePerMillion: Double? = null,
)

/** What a provider does with prompts, as the row shows it. */
enum class ProviderPrivacyUi {
    /** Neither trains on prompts nor keeps them. */
    PRIVATE,

    /** Does not train, but keeps prompts. */
    KEEPS_PROMPTS,

    /** May train on prompts. */
    MAY_TRAIN,
}

/** What the sheet shows while it loads a model's providers. */
internal sealed interface ProvidersLoadState {
    data object Loading : ProvidersLoadState

    data object Failed : ProvidersLoadState

    data class Loaded(val options: List<ProviderOptionUi>) : ProvidersLoadState
}

/**
 * The ticked tags in the order of [options], which the app lists cheapest first.
 * A tag that is no longer offered is dropped, so the stored order is the price order at saving.
 */
internal fun chosenTagsInListOrder(options: List<ProviderOptionUi>, chosenTags: Set<String>): List<String> =
    options.map { option -> option.tag }.filter { tag -> tag in chosenTags }

/** The provider part of a tag: "deepinfra" for "deepinfra/fp4". */
internal fun providerOfTag(tag: String): String = tag.substringBefore('/')

/** A sheet for one OpenRouter model: tick the providers that may serve it. Loads when composed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelProvidersSheet(model: ServiceModelUi, actions: SettingsActions, onDismiss: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var loadState by remember { mutableStateOf<ProvidersLoadState>(ProvidersLoadState.Loading) }
    var chosenTags by remember { mutableStateOf(model.pinnedProviders.toSet()) }
    var allowFallbacks by remember { mutableStateOf(model.allowFallbacks) }

    LaunchedEffect(model.key, attempt) {
        loadState = ProvidersLoadState.Loading
        loadState = actions.onModelProvidersLoad(model.key).fold(
            onSuccess = { options -> ProvidersLoadState.Loaded(options) },
            onFailure = { ProvidersLoadState.Failed },
        )
    }

    fun save(newChosenTags: Set<String>, newAllowFallbacks: Boolean) {
        val loaded = loadState as? ProvidersLoadState.Loaded ?: return
        chosenTags = newChosenTags
        allowFallbacks = newAllowFallbacks
        actions.onModelProvidersChange(model.key, chosenTagsInListOrder(loaded.options, newChosenTags), newAllowFallbacks)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ModelProvidersContent(
            modelName = model.name,
            loadState = loadState,
            chosenTags = chosenTags,
            allowFallbacks = allowFallbacks,
            onToggle = { tag ->
                val updated = if (tag in chosenTags) chosenTags - tag else chosenTags + tag
                save(updated, allowFallbacks)
            },
            onAllowFallbacksChange = { allowed -> save(chosenTags, allowed) },
            onRetry = { attempt += 1 },
        )
    }
}

/** The sheet's body, without the sheet window, so that previews can draw it. */
@Composable
internal fun ModelProvidersContent(
    modelName: String,
    loadState: ProvidersLoadState,
    chosenTags: Set<String>,
    allowFallbacks: Boolean,
    onToggle: (tag: String) -> Unit,
    onAllowFallbacksChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.settings_model_providers),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        // One item's sheet shows the full name, wrapped (D-029).
        Text(
            modelName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        when (loadState) {
            ProvidersLoadState.Loading -> LoadingNote()
            ProvidersLoadState.Failed -> MessageWithRetry(R.string.settings_providers_failed, onRetry)
            is ProvidersLoadState.Loaded -> {
                if (loadState.options.isEmpty()) {
                    MessageWithRetry(R.string.settings_providers_empty, onRetry)
                } else {
                    ProviderList(loadState.options, chosenTags, allowFallbacks, onToggle, onAllowFallbacksChange)
                }
            }
        }
    }
}

@Composable
private fun LoadingNote() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.settings_providers_loading),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MessageWithRetry(messageRes: Int, onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(stringResource(messageRes), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.settings_providers_retry)) }
    }
}

@Composable
private fun ProviderList(
    options: List<ProviderOptionUi>,
    chosenTags: Set<String>,
    allowFallbacks: Boolean,
    onToggle: (tag: String) -> Unit,
    onAllowFallbacksChange: (Boolean) -> Unit,
) {
    Text(
        stringResource(R.string.settings_providers_order_note),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    )
    PrivacyLegend(legendStates(options))
    for (option in options) {
        ProviderRow(option, isChosen = option.tag in chosenTags, onToggle = { onToggle(option.tag) })
    }
    OptionSwitchRow(
        text = stringResource(R.string.settings_providers_fallbacks),
        isOn = allowFallbacks,
        onChange = onAllowFallbacksChange,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderRow(option: ProviderOptionUi, isChosen: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = isChosen, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        // The row takes the tap, so a screen reader announces one control, not two.
        Checkbox(checked = isChosen, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            // The name gives way first: a fixed-size icon is measured before a weighted text,
            // so a long name ends in "…" and the shield stays whole.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    option.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                option.privacy?.let { privacy ->
                    Icon(
                        privacyIcon(privacy),
                        contentDescription = stringResource(privacyLabel(privacy)),
                        tint = privacyColor(privacy),
                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                    )
                }
            }
            option.variantTag?.let { variantTag ->
                Text(
                    variantTag,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The prices wrap between values, never inside one.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailText(stringResource(R.string.settings_price_in, UsageFormat.price(option.inputPricePerMillion)))
                DetailText(stringResource(R.string.settings_price_out, UsageFormat.price(option.outputPricePerMillion)))
                DetailText(stringResource(R.string.settings_price_cache, UsageFormat.price(option.cachedInputPricePerMillion)))
                option.quantization?.let { quantization -> DetailText(quantization) }
            }
        }
    }
}

private fun privacyIcon(privacy: ProviderPrivacyUi): ImageVector = when (privacy) {
    ProviderPrivacyUi.PRIVATE -> JonakiIcons.Shield
    ProviderPrivacyUi.KEEPS_PROMPTS -> JonakiIcons.Storage
    ProviderPrivacyUi.MAY_TRAIN -> Icons.Filled.Warning
}

private fun privacyLabel(privacy: ProviderPrivacyUi): Int = when (privacy) {
    ProviderPrivacyUi.PRIVATE -> R.string.settings_providers_private
    ProviderPrivacyUi.KEEPS_PROMPTS -> R.string.settings_providers_keeps_prompts
    ProviderPrivacyUi.MAY_TRAIN -> R.string.settings_providers_may_train
}

/** Warning uses the deny colour, as the app does for problems elsewhere so that it stands out; the shape alone also tells the three apart. */
@Composable
private fun privacyColor(privacy: ProviderPrivacyUi): Color = when (privacy) {
    ProviderPrivacyUi.PRIVATE -> MaterialTheme.colorScheme.primary
    ProviderPrivacyUi.KEEPS_PROMPTS -> MaterialTheme.colorScheme.onSurfaceVariant
    ProviderPrivacyUi.MAY_TRAIN -> JonakiTheme.colors.deny
}

/** The states to explain in the legend: those in [options], once each, in the order private, keeps, trains. */
internal fun legendStates(options: List<ProviderOptionUi>): List<ProviderPrivacyUi> {
    val present = options.mapNotNull { option -> option.privacy }.toSet()
    return ProviderPrivacyUi.entries.filter { state -> state in present }
}

/** Icon and label for each state that occurs in the list; nothing when no row has a mark. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PrivacyLegend(states: List<ProviderPrivacyUi>) {
    if (states.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
    ) {
        for (state in states) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The label below says the same, so the icon is not announced twice.
                Icon(privacyIcon(state), contentDescription = null, tint = privacyColor(state), modifier = Modifier.size(16.dp))
                Text(
                    stringResource(privacyLabel(state)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
    )
}
