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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import app.jonaki.core.ui.MonospaceFamily

/** Settings > Models > Image generation (the models generate_image may use). */
@Immutable
data class ImageGenerationUi(
    /** False shows "Needs an OpenRouter key."; the models stay listed. */
    val hasOpenRouterKey: Boolean = false,
    val models: List<ImageModelRowUi> = emptyList(),
)

@Immutable
data class ImageModelRowUi(
    /** OpenRouter's id, for example "black-forest-labs/flux.2-klein-4b". */
    val id: String,
    /** The list's name, or the id while the list is not loaded. */
    val name: String,
    /** "$0.014 per megapixel"; null while it is not loaded or the model has none. */
    val priceText: String? = null,
    val isDefault: Boolean = false,
)

/** What the image model picker shows. */
sealed interface ImagePickerState {
    data object Loading : ImagePickerState

    /** [reason] is short, for example "HTTP 503". */
    data class Failed(val reason: String) : ImagePickerState

    data class Loaded(val models: List<AddableModelUi>) : ImagePickerState
}

@Composable
internal fun ImageGenerationSection(images: ImageGenerationUi, actions: SettingsActions) {
    SectionLabel(stringResource(R.string.settings_section_images))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column {
            if (!images.hasOpenRouterKey) {
                Text(
                    stringResource(R.string.settings_images_needs_key),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            if (images.models.isEmpty()) {
                Text(
                    stringResource(R.string.settings_images_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            for (model in images.models) {
                ImageModelRow(model, actions)
            }
            TextButton(onClick = actions.onAddImageModels, modifier = Modifier.padding(horizontal = 4.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_images_add))
            }
        }
    }
}

/** The name keeps one line and ends in "…", the id and the price get lines of their own (D-029). */
@Composable
private fun ImageModelRow(model: ImageModelRowUi, actions: SettingsActions) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        ImageModelStar(model, onMakeDefault = { actions.onImageModelSetDefault(model.id) })
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (model.name != model.id) {
                Text(model.id, style = idStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            model.priceText?.let { price ->
                Text(price, style = idStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ImageModelMenu(model, actions)
    }
}

@Composable
private fun idStyle() = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily)

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
private fun ImageModelMenu(model: ImageModelRowUi, actions: SettingsActions) {
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
                        actions.onImageModelSetDefault(model.id)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_remove_model), color = JonakiTheme.colors.deny) },
                onClick = {
                    open = false
                    actions.onImageModelRemove(model.id)
                },
            )
        }
    }
}

/**
 * Picks image models from OpenRouter's list by search. The list loads when
 * the screen opens, so it has loading, failed and empty-search states. The
 * list has no prices, so rows show name and id only; [onDone] receives the
 * ids ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddImageModelsScreen(
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
                        stringResource(R.string.settings_images_picker_title),
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
                    if (visible.isEmpty()) {
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

/** Two lines per row (D-029): the name ends in "…" when long, the id has its own line. */
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
            Text(model.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(model.id, style = idStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
