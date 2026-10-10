package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import app.jonaki.core.ui.MonospaceFamily

/** Settings > Models > Video generation: one card per added video service, then "Add service". */
@Immutable
data class VideoGenerationUi(
    val services: List<VideoServiceCardUi> = emptyList(),
    /** Services offered by "Add service": the ones not added yet. */
    val addableServices: List<AddableServiceUi> = emptyList(),
)

/** One added video service. Its key is the same secret as a chat service of the same account. */
@Immutable
data class VideoServiceCardUi(
    /** Stable id, for example "openrouter". */
    val serviceKey: String,
    val displayName: String,
    val apiKey: KeySlot,
    val models: List<VideoModelRowUi> = emptyList(),
)

@Immutable
data class VideoModelRowUi(
    /** "service:modelId", for example "openrouter:google/veo-3.1-lite". */
    val key: String,
    /** The service's id, for example "google/veo-3.1-lite". */
    val id: String,
    /** The list's name, or the id while the list is not loaded. */
    val name: String,
    /** "$0.03 to $0.14 per second", "$0.08 per second" or "per token"; null while the list is not loaded. */
    val priceText: String? = null,
    /** "4, 6, 8 s" or "1 to 15 s"; null while the list is not loaded or names none. */
    val lengthsText: String? = null,
    val isDefault: Boolean = false,
)

/** A model of the video picker; the price and lengths come with the list, so no row ever waits for them. */
@Immutable
data class VideoPickModelUi(
    val id: String,
    val name: String,
    val isAdded: Boolean = false,
    val priceText: String? = null,
    val lengthsText: String? = null,
)

/** What the video model picker shows. */
sealed interface VideoPickerState {
    data object Loading : VideoPickerState

    /** [reason] is short, for example "HTTP 503". */
    data class Failed(val reason: String) : VideoPickerState

    data class Loaded(val models: List<VideoPickModelUi>) : VideoPickerState
}

@Composable
internal fun VideoGenerationSection(video: VideoGenerationUi, actions: SettingsActions) {
    SectionLabel(stringResource(R.string.settings_section_videos))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (card in video.services) {
            VideoServiceCard(card, actions)
        }
        if (video.addableServices.isNotEmpty()) {
            AddServiceDropdown(video.addableServices, actions.onAddVideoService)
        }
    }
}

@Composable
private fun VideoServiceCard(card: VideoServiceCardUi, actions: SettingsActions) {
    val summary = collapsedSummary(card.apiKey, card.models.size)
    ServiceCardFrame("video:${card.serviceKey}", startsOpen = !card.apiKey.isSet, card.displayName, summary, account = null) {
        KeyField(stringResource(R.string.settings_api_key), card.apiKey, actions)
        if (card.models.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 12.dp))
        }
        for (model in card.models) {
            VideoModelRow(model, actions)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            TextButton(onClick = { actions.onAddVideoModels(card.serviceKey) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_add_model))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { actions.onVideoServiceRemove(card.serviceKey) }) {
                Text(stringResource(R.string.settings_remove_service), color = JonakiTheme.colors.deny)
            }
        }
    }
}

@Composable
private fun videoLineStyle() = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily)

/** The name keeps one line and ends in "…"; the id, the price and the lengths get lines of their own (D-029). */
@Composable
private fun VideoModelRow(model: VideoModelRowUi, actions: SettingsActions) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        VideoModelStar(model, onMakeDefault = { actions.onVideoModelSetDefault(model.key) })
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (model.name != model.id) {
                Text(model.id, style = videoLineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            model.priceText?.let { price ->
                Text(price, style = videoLineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            model.lengthsText?.let { lengths ->
                Text(lengths, style = videoLineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        VideoModelMenu(model, actions)
    }
}

@Composable
private fun VideoModelStar(model: VideoModelRowUi, onMakeDefault: () -> Unit) {
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
private fun VideoModelMenu(model: VideoModelRowUi, actions: SettingsActions) {
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
                        actions.onVideoModelSetDefault(model.key)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_remove_model), color = JonakiTheme.colors.deny) },
                onClick = {
                    open = false
                    actions.onVideoModelRemove(model.key)
                },
            )
        }
    }
}

/**
 * Picks video models by search. The list loads when the screen opens, so it
 * has loading, failed and empty-search states. A row shows the name, the id,
 * the price per second and the supported lengths, each on a line of its own
 * (D-029). [onDone] receives the ids ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVideoModelsScreen(
    serviceName: String,
    state: VideoPickerState,
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
                VideoPickerState.Loading -> VideoCenteredMessage {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.settings_images_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is VideoPickerState.Failed -> VideoCenteredMessage {
                    Text(
                        stringResource(R.string.settings_images_failed, state.reason),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.settings_images_retry)) }
                }
                is VideoPickerState.Loaded -> {
                    SearchField(query, onQueryChange = { query = it })
                    val visible = remember(state.models, query) { filteredVideoModels(state.models, query) }
                    if (visible.isEmpty()) {
                        VideoCenteredMessage {
                            Text(stringResource(R.string.settings_images_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(visible, key = { model -> model.id }) { model ->
                                VideoPickRow(model, isPicked = model.id in picked, onToggle = { picked = toggled(picked, model.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Models whose name or id contains every word of [query], ignoring case. */
internal fun filteredVideoModels(models: List<VideoPickModelUi>, query: String): List<VideoPickModelUi> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) {
        return models
    }
    return models.filter { model ->
        val searchable = "${model.name} ${model.id}".lowercase()
        words.all { word -> word in searchable }
    }
}

@Composable
private fun VideoCenteredMessage(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) { content() }
}

/** Up to four lines per row: the name ends in "…" when long, and the id, the price and the lengths each have their own line. */
@Composable
private fun VideoPickRow(model: VideoPickModelUi, isPicked: Boolean, onToggle: () -> Unit) {
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
            Text(model.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(model.id, style = videoLineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            model.priceText?.let { price ->
                Text(price, style = videoLineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            model.lengthsText?.let { lengths ->
                Text(lengths, style = videoLineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
