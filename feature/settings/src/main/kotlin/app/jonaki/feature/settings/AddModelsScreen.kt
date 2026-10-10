package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat

/** Width of one price column, so the in, out and cache prices line up from row to row. */
private val PriceColumnWidth = 68.dp

/** Space before a row's text: the checkbox column. */
private val CheckColumnWidth = 48.dp

/**
 * Picks several models of one service from its list, by search (D-028).
 * The query and the ticks are screen-local; [onDone] receives the ids picked,
 * including a model id typed by hand when the list does not have it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddModelsScreen(
    state: AddModelsUiState,
    onClose: () -> Unit,
    onDone: (modelIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    val visible = remember(state.models, query) { filterModels(state.models, query) }
    val typedId = freeTextModelId(state.models, query)
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
                        stringResource(R.string.settings_add_models_title, state.serviceDisplayName),
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
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(
                    footerText(picked.size, state.models.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query, onQueryChange = { query = it })
            PriceHeader()
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.weight(1f)) {
                if (typedId != null && visible.isEmpty()) {
                    item(key = "typed") {
                        TypedIdRow(typedId, isPicked = typedId in picked, onToggle = { picked = toggled(picked, typedId) })
                    }
                }
                items(visible, key = { model -> model.id }) { model ->
                    ModelPickRow(model, isPicked = model.id in picked, onToggle = { picked = toggled(picked, model.id) })
                }
            }
        }
    }
}

internal fun toggled(picked: List<String>, id: String): List<String> =
    if (id in picked) picked - id else picked + id

@Composable
private fun footerText(pickedCount: Int, modelCount: Int): String {
    val selected = pluralStringResource(R.plurals.settings_selected_count, pickedCount, pickedCount)
    val models = pluralStringResource(R.plurals.settings_model_count, modelCount, modelCount)
    return "$selected · $models"
}

@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.settings_search_models)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.settings_clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun PriceHeader() {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(start = 16.dp + CheckColumnWidth, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
        Text(stringResource(R.string.settings_per_million), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        PriceColumn(stringResource(R.string.settings_price_head_in), style, color)
        PriceColumn(stringResource(R.string.settings_price_head_out), style, color)
        PriceColumn(stringResource(R.string.settings_price_head_cache), style, color)
    }
}

@Composable
private fun PriceColumn(text: String, style: TextStyle, color: Color) {
    Text(text, style = style, color = color, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.width(PriceColumnWidth))
}

/** Three lines per row (D-029): name and context, the id, then the prices in columns. */
@Composable
private fun ModelPickRow(model: AddableModelUi, isPicked: Boolean, onToggle: () -> Unit) {
    val checked = model.isAdded || isPicked
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !model.isAdded, onClick = onToggle)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = !model.isAdded, modifier = Modifier.width(CheckColumnWidth).padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val window = model.contextWindowTokens
                if (window != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(UsageFormat.tokenCount(window), style = monoSmall(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(model.id, style = monoSmall(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                Spacer(Modifier.weight(1f))
                PriceValue(model.inputPricePerMillion)
                PriceValue(model.outputPricePerMillion)
                PriceValue(model.cachedInputPricePerMillion)
            }
        }
    }
}

@Composable
private fun PriceValue(usdPerMillion: Double?) {
    val isFree = usdPerMillion == 0.0
    val text = if (isFree) stringResource(R.string.settings_price_free) else UsageFormat.price(usdPerMillion)
    val color = if (isFree) JonakiTheme.colors.live else MaterialTheme.colorScheme.onSurface
    Text(text, style = monoSmall(), color = color, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.width(PriceColumnWidth))
}

@Composable
private fun TypedIdRow(id: String, isPicked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        if (isPicked) {
            Checkbox(checked = true, onCheckedChange = null, modifier = Modifier.width(CheckColumnWidth))
        } else {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(CheckColumnWidth))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_add_by_id), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(id, style = monoSmall(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun monoSmall(): TextStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily)
