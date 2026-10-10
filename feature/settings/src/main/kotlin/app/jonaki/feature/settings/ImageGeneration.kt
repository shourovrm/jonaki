package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ModelTag
import app.jonaki.core.ui.MonospaceFamily

/** Settings > Models > Image generation: one card per added image service, then "Add service". */
@Immutable
data class ImageGenerationUi(
    val services: List<ImageServiceCardUi> = emptyList(),
    /** Services offered by "Add service": the ones not added yet. */
    val addableServices: List<AddableServiceUi> = emptyList(),
    /** True when the quality default is High; false is Standard. */
    val isHighQuality: Boolean = false,
    /** The cards of Vector image generation: the services added there, each with its vector (SVG) models. */
    val vectorServices: List<ImageServiceCardUi> = emptyList(),
    /** Services offered by "Add service" under Vector image generation. */
    val addableVectorServices: List<AddableServiceUi> = emptyList(),
)

/** One added image service. Its key is the same secret as a chat service of the same account. */
@Immutable
data class ImageServiceCardUi(
    /** Stable id, for example "openrouter". */
    val serviceKey: String,
    val displayName: String,
    val apiKey: KeySlot,
    val models: List<ImageModelRowUi> = emptyList(),
)

@Immutable
data class ImageModelRowUi(
    /** "service:modelId", for example "openrouter:black-forest-labs/flux.2-klein-4b". */
    val key: String,
    /** The service's id, for example "black-forest-labs/flux.2-klein-4b". */
    val id: String,
    /** The list's name, or the id while the list is not loaded. */
    val name: String,
    /** "$0.014 per megapixel"; null while it is not loaded or the model has none. */
    val priceText: String? = null,
    val isDefault: Boolean = false,
    /** A vector model (makes SVG files); its row carries an "SVG" tag. */
    val isVector: Boolean = false,
)

/** What the image model picker shows. */
sealed interface ImagePickerState {
    data object Loading : ImagePickerState

    /** [reason] is short, for example "HTTP 503". */
    data class Failed(val reason: String) : ImagePickerState

    /** [allowsTypedId] is true for a service whose list is only suggestions, so any id can be added by hand. */
    data class Loaded(val models: List<AddableModelUi>, val allowsTypedId: Boolean = false) : ImagePickerState
}

@Composable
internal fun ImageGenerationSection(images: ImageGenerationUi, actions: SettingsActions) {
    SectionLabel(stringResource(R.string.settings_section_images))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (card in images.services) {
            ImageServiceCard(
                card,
                stateKey = "image:${card.serviceKey}",
                actions = actions,
                onAddModels = { actions.onAddImageModels(card.serviceKey) },
                onRemoveService = { actions.onImageServiceRemove(card.serviceKey) },
                onMakeDefault = actions.onImageModelSetDefault,
            )
        }
        if (images.addableServices.isNotEmpty()) {
            AddServiceDropdown(images.addableServices, actions.onAddImageService)
        }
        if (images.services.isNotEmpty()) {
            ImageQualityRow(images.isHighQuality, actions.onImageQualityChange)
        }
    }
}

/** The quality generate_image uses when the chat model names none: Standard or High. */
@Composable
private fun ImageQualityRow(isHigh: Boolean, onChange: (isHigh: Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.settings_image_quality), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.size(8.dp))
        val choices = listOf(false to R.string.settings_image_quality_standard, true to R.string.settings_image_quality_high)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, (choiceIsHigh, labelResource) ->
                SegmentedButton(
                    selected = choiceIsHigh == isHigh,
                    onClick = { onChange(choiceIsHigh) },
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                    icon = {},
                    label = { Text(stringResource(labelResource), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

/** One service of Image generation or of Vector image generation; [stateKey] differs so each card opens on its own. */
@Composable
internal fun ImageServiceCard(
    card: ImageServiceCardUi,
    stateKey: String,
    actions: SettingsActions,
    onAddModels: () -> Unit,
    onRemoveService: () -> Unit,
    onMakeDefault: (modelKey: String) -> Unit,
) {
    val summary = collapsedSummary(card.apiKey, card.models.size)
    ServiceCardFrame(stateKey, startsOpen = !card.apiKey.isSet, card.displayName, summary, account = null) {
        KeyField(stringResource(R.string.settings_api_key), card.apiKey, actions)
        if (card.models.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 12.dp))
        }
        for (model in card.models) {
            ImageModelRow(
                model,
                onMakeDefault = { onMakeDefault(model.key) },
                onRemove = { actions.onImageModelRemove(model.key) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            TextButton(onClick = onAddModels) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_add_model))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRemoveService) {
                Text(stringResource(R.string.settings_remove_service), color = JonakiTheme.colors.deny)
            }
        }
    }
}

/** The name keeps one line and ends in "…", the id and the price get lines of their own (D-029). */
@Composable
private fun ImageModelRow(model: ImageModelRowUi, onMakeDefault: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        ImageModelStar(model, onMakeDefault)
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            NameWithTag(model.name, isVector = model.isVector)
            if (model.name != model.id) {
                Text(model.id, style = idStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            model.priceText?.let { price ->
                Text(price, style = idStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ImageModelMenu(model, onMakeDefault, onRemove)
    }
}

/** The name ends in "…" when long; the "SVG" tag of a vector model keeps its place and its width (D-029). */
@Composable
private fun NameWithTag(name: String, isVector: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isVector) {
            ModelTag(stringResource(R.string.settings_image_svg_tag), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun idStyle() =MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily)

@Composable
private fun ImageModelStar(model: ImageModelRowUi, onMakeDefault: () -> Unit) {
    if (model.isDefault) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.settings_model_is_default), tint = JonakiTheme.colors.live)
        }
        return
    }
    IconButton(onClick = onMakeDefault) {
        Icon(
            JonakiIcons.StarBorder,
            contentDescription = stringResource(R.string.settings_make_default, model.name),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun ImageModelMenu(model: ImageModelRowUi, onMakeDefault: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.settings_model_options, model.name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!model.isDefault) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_set_default)) },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    onClick = {
                        open = false
                        onMakeDefault()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_remove_model), color = JonakiTheme.colors.deny) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

/**
 * Picks image models of one service by search. The list loads when the
 * screen opens, so it has loading, failed and empty-search states. A row
 * that has an [AddableModelUi.imagePrice] shows it on a line of its own
 * under the id; [onDone] receives the ids ticked, including one typed by
 * hand when the service allows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddImageModelsScreen(
    serviceName: String,
    state: ImagePickerState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onDone: (modelIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.settings_close))
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.settings_add_models_title, serviceName),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    TextButton(onClick = { onDone(picked) }, enabled = picked.isNotEmpty()) {
                        Text(stringResource(R.string.settings_done))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ImagePickerState.Loading -> CenteredMessage {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.settings_images_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is ImagePickerState.Failed -> CenteredMessage {
                    Text(
                        stringResource(R.string.settings_images_failed, state.reason),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.settings_images_retry)) }
                }
                is ImagePickerState.Loaded -> {
                    SearchField(query, onQueryChange = { query = it })
                    val visible = remember(state.models, query) { filterModels(state.models, query) }
                    val typedId = if (state.allowsTypedId) freeTextModelId(state.models, query) else null
                    if (visible.isEmpty() && typedId != null) {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            item(key = "typed") {
                                TypedIdRow(typedId, isPicked = typedId in picked, onToggle = { picked = toggled(picked, typedId) })
                            }
                        }
                    } else if (visible.isEmpty()) {
                        CenteredMessage {
                            Text(stringResource(R.string.settings_images_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(visible, key = { model -> model.id }) { model ->
                                ImagePickRow(model, isPicked = model.id in picked, onToggle = { picked = toggled(picked, model.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) { content() }
}

/**
 * Two lines per row (D-029), three with a price: the name ends in "…" when
 * long, the id and the price each have their own line. The price line keeps
 * its height while the price loads, so the row does not jump when it arrives.
 */
@Composable
private fun ImagePickRow(model: AddableModelUi, isPicked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !model.isAdded, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Checkbox(
            checked = model.isAdded || isPicked,
            onCheckedChange = null,
            enabled = !model.isAdded,
            modifier = Modifier.width(48.dp).padding(top = 2.dp),
        )
        Column(Modifier.weight(1f)) {
            NameWithTag(model.name, isVector = model.isVector)
            Text(model.id, style = idStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            model.imagePrice?.let { price ->
                Text(priceLineText(price), style = idStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A no-break space while loading, so the line is as tall as one with text. */
private fun priceLineText(price: ImagePriceUi): String = when (price) {
    ImagePriceUi.Loading -> "\u00A0"
    is ImagePriceUi.Known -> price.text
    ImagePriceUi.Unknown -> "–"
}
