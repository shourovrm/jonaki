package app.jonaki.feature.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons

/**
 * The first Settings page (D-128): a search field, then one row per
 * sub-page in three groups separated by space. Each row's second line is
 * the page's live state, so most states can be read without opening it.
 * [scrollState] belongs to the caller, so Back from a sub-page lands at the
 * same place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
) {
    val texts = rememberSettingsTexts()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
        ) {
            SettingsSearchField(searchQuery, onSearchQueryChange)
            if (searchQuery.isBlank()) {
                PageRows(state, texts, actions.onOpenPage)
            } else {
                SearchResults(state, texts, searchQuery, actions.onOpenPage)
            }
        }
    }
}

@Composable
private fun PageRows(state: SettingsUiState, texts: SettingsTexts, onOpenPage: (SettingsPage) -> Unit) {
    SettingsPage.GROUPS.forEachIndexed { groupIndex, group ->
        if (groupIndex > 0) {
            Spacer(Modifier.height(14.dp))
        }
        for (page in group) {
            val summary = SettingsSummaries.of(page, state, texts)
            PageRow(
                icon = iconOf(page),
                title = stringResource(page.title),
                summary = summary.text,
                summaryIsAlert = summary.needsAttention,
                onClick = { onOpenPage(page) },
            )
        }
    }
}

/** Each match is a row that opens its page; the second line names that page. */
@Composable
private fun SearchResults(state: SettingsUiState, texts: SettingsTexts, query: String, onOpenPage: (SettingsPage) -> Unit) {
    val entries = remember(state, texts) { SettingsSearch.entries(texts, state) }
    val matches = SettingsSearch.filter(entries, query)
    if (matches.isEmpty()) {
        Text(
            stringResource(R.string.settings_search_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        return
    }
    for (match in matches) {
        PageRow(
            icon = iconOf(match.page),
            title = match.title,
            summary = stringResource(match.page.title),
            summaryIsAlert = false,
            onClick = { onOpenPage(match.page) },
        )
    }
}

/** Title and summary keep one line each and end in "…" (D-029). */
@Composable
private fun PageRow(icon: ImageVector, title: String, summary: String, summaryIsAlert: Boolean, onClick: () -> Unit) {
    val summaryColor = if (summaryIsAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = summaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The same field as the thread list's search. */
@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.settings_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.settings_clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(22.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
    )
}

/** Material equivalents of the mockup's Phosphor icons; the core set and JonakiIcons only, no new dependency. */
private fun iconOf(page: SettingsPage): ImageVector = when (page) {
    SettingsPage.MODELS -> JonakiIcons.Memory
    SettingsPage.WEB -> JonakiIcons.Globe
    SettingsPage.TOOLS -> Icons.Outlined.Build
    SettingsPage.ANSWERS -> JonakiIcons.ChatBubble
    SettingsPage.MEMORY_SKILLS -> JonakiIcons.Lightbulb
    SettingsPage.FILES_SCHEDULE -> JonakiIcons.Folder
    SettingsPage.THEME -> JonakiIcons.DarkMode
    SettingsPage.PERMISSIONS -> JonakiIcons.Shield
    SettingsPage.ABOUT -> Icons.Outlined.Info
}

/** Read again when the language changes, so the summaries follow it. */
@Composable
private fun rememberSettingsTexts(): SettingsTexts {
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { ResourceSettingsTexts(resources) }
}
