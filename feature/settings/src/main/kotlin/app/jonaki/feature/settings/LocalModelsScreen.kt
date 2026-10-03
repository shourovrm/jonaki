package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.MonospaceFamily

/**
 * Settings > Local models (D-133): this phone's memory and storage, the
 * downloaded models, a short recommended list and a Hugging Face search.
 * Tapping a search result opens its file list in place of the page.
 */
@Composable
fun LocalModelsScreen(state: LocalModelsUiState, actions: LocalModelsActions, modifier: Modifier = Modifier) {
    val openRepo = state.openRepo
    if (openRepo != null) {
        RepoFilesScreen(openRepo, actions, modifier)
        return
    }
    LocalModelsPage(state, actions, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalModelsPage(state: LocalModelsUiState, actions: LocalModelsActions, modifier: Modifier) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_root_local_models),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionLabel(stringResource(R.string.local_models_section_phone))
            Group {
                DeviceRow(stringResource(R.string.local_models_ram), state.device.ram)
                GroupDivider()
                DeviceRow(stringResource(R.string.local_models_available_memory), state.device.availableMemory)
                GroupDivider()
                DeviceRow(stringResource(R.string.local_models_free_storage), state.device.freeStorage)
            }
            if (state.downloading.isNotEmpty()) {
                SectionLabel(stringResource(R.string.local_models_section_downloading))
                DownloadingRows(state.downloading, onCancel = actions.onCancelDownload)
            }
            SectionLabel(stringResource(R.string.local_models_section_downloaded))
            DownloadedRows(state.downloaded, onDelete = { fileName -> pendingDelete = fileName })
            if (state.tools.isNotEmpty()) {
                SectionLabel(stringResource(R.string.local_models_section_tools))
                ToolRows(state.tools, actions.onToolChange)
            }
            SectionLabel(stringResource(R.string.local_models_section_recommended))
            Group {
                state.recommended.forEachIndexed { index, model ->
                    if (index > 0) GroupDivider()
                    RecommendedRow(model, actions)
                }
            }
            SectionLabel(stringResource(R.string.local_models_section_search))
            HubSearchField(state.query, actions)
            SearchResults(state.search, actions)
        }
    }
    val deleting = state.downloaded.firstOrNull { model -> model.fileName == pendingDelete }
    if (deleting != null) {
        DeleteDialog(
            model = deleting,
            onConfirm = {
                pendingDelete = null
                actions.onDelete(deleting.fileName)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Label on the left, the size on the right in the number font. */
@Composable
private fun DeviceRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = MonospaceFamily, maxLines = 1)
    }
}

@Composable
private fun DownloadedRows(models: List<DownloadedModelUi>, onDelete: (String) -> Unit) {
    if (models.isEmpty()) {
        MutedLine(stringResource(R.string.local_models_none_downloaded))
        return
    }
    Group {
        models.forEachIndexed { index, model ->
            if (index > 0) GroupDivider()
            ModelRow(
                name = model.fileName,
                trailing = {
                    TextButton(
                        onClick = { onDelete(model.fileName) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.local_models_delete))
                    }
                },
            ) {
                SizeLine(model.size)
            }
        }
    }
}

/** Every download in progress, with its bar and Cancel, so one started from a search stays in sight. */
@Composable
private fun DownloadingRows(downloads: List<ActiveDownloadUi>, onCancel: (String) -> Unit) {
    Group {
        downloads.forEachIndexed { index, active ->
            if (index > 0) GroupDivider()
            ModelRow(
                name = active.fileName,
                trailing = {
                    TextButton(onClick = { onCancel(active.fileName) }) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                },
            ) {
                DownloadStatus(active.download)
            }
        }
    }
}

/** A switch per tool; the cost line says what each adds to every request (D-133). */
@Composable
private fun ToolRows(tools: List<LocalToolUi>, onChange: (String, Boolean) -> Unit) {
    Group {
        tools.forEachIndexed { index, tool ->
            if (index > 0) GroupDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChange(tool.name, !tool.enabled) }
                    .heightIn(min = 56.dp)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tool.name, style = MaterialTheme.typography.titleSmall, fontFamily = MonospaceFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val tokens = tool.tokens
                    val costLine = if (tokens == null) null else stringResource(R.string.local_models_tool_tokens, tokens)
                    val filesLine = if (tool.onlyWithFiles) stringResource(R.string.local_models_tool_only_with_files) else null
                    val line = listOfNotNull(costLine, filesLine).joinToString(" · ")
                    if (line.isNotEmpty()) {
                        SecondaryText(line)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = tool.enabled, onCheckedChange = { checked -> onChange(tool.name, checked) })
            }
        }
    }
}

@Composable
private fun RecommendedRow(model: RecommendedModelUi, actions: LocalModelsActions) {
    ModelRow(
        name = model.name,
        trailing = {
            DownloadButton(
                download = model.download,
                onDownload = { actions.onDownloadRecommended(model.fileName) },
                onCancel = { actions.onCancelDownload(model.fileName) },
            )
        },
    ) {
        SizeAndFitLine(model.size, model.fit)
        DownloadStatus(model.download)
    }
}

@Composable
private fun SearchResults(search: HubSearchUi, actions: LocalModelsActions) {
    when (search) {
        HubSearchUi.Idle -> Unit
        HubSearchUi.Loading -> Loading()
        HubSearchUi.Failed -> MutedLine(stringResource(R.string.local_models_search_failed), isError = true)
        is HubSearchUi.Results -> {
            if (search.results.isEmpty()) {
                MutedLine(stringResource(R.string.local_models_search_empty))
                return
            }
            Group {
                search.results.forEachIndexed { index, result ->
                    if (index > 0) GroupDivider()
                    SearchResultRow(result, actions)
                }
            }
        }
    }
}

/** The repository name keeps one line (D-029); its numbers get lines of their own. */
@Composable
private fun SearchResultRow(result: SearchResultUi, actions: LocalModelsActions) {
    ModelRow(
        name = result.repoId,
        onClick = { actions.onOpenRepo(result.repoId) },
        trailing = {
            if (result.block == null) {
                DownloadButton(
                    download = result.download,
                    onDownload = { actions.onDownloadRepo(result.repoId) },
                    onCancel = { actions.onCancelRepoDownload(result.repoId) },
                )
            }
        },
    ) {
        RepoFacts(result)
        BlockOrFit(result.block, result.fit)
        DownloadStatus(result.download)
    }
}

/** "4.2B parameters · apache-2.0", then "1,024,272 downloads". */
@Composable
private fun RepoFacts(result: SearchResultUi) {
    val parameters = result.parameters?.let { count -> stringResource(R.string.local_models_parameters, count) }
    val facts = listOfNotNull(parameters, result.license).joinToString(" · ")
    if (facts.isNotEmpty()) {
        SecondaryText(facts)
    }
    SecondaryText(stringResource(R.string.local_models_downloads, result.downloads))
}

@Composable
private fun BlockOrFit(block: RepoBlockUi?, fit: FitUi?) {
    when {
        block == RepoBlockUi.GATED -> SecondaryText(stringResource(R.string.local_models_gated))
        block == RepoBlockUi.NOT_SUPPORTED -> SecondaryText(stringResource(R.string.local_models_not_supported))
        fit != null -> FitText(fit)
    }
}

/**
 * One repository's .gguf files with sizes and fit labels. The repository
 * name wraps in full here, as in any opened view (D-029).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoFilesScreen(openRepo: RepoFilesUi, actions: LocalModelsActions, modifier: Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.local_models_files_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = actions.onCloseRepo) {
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            val repo = openRepo.repo
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(repo.repoId, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                RepoFacts(repo)
                if (repo.block != null) {
                    BlockOrFit(repo.block, fit = null)
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                openRepo.isLoading -> Loading()
                openRepo.failed -> MutedLine(stringResource(R.string.local_models_files_failed), isError = true)
                openRepo.files.isEmpty() -> MutedLine(stringResource(R.string.local_models_files_empty))
                else -> Group {
                    openRepo.files.forEachIndexed { index, file ->
                        if (index > 0) GroupDivider()
                        RepoFileRow(repo, file, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoFileRow(repo: SearchResultUi, file: RepoFileUi, actions: LocalModelsActions) {
    ModelRow(
        name = file.fileName,
        trailing = {
            if (repo.block == null) {
                DownloadButton(
                    download = file.download,
                    onDownload = { actions.onDownloadFile(repo.repoId, file.fileName) },
                    onCancel = { actions.onCancelDownload(file.fileName) },
                )
            }
        },
    ) {
        SizeAndFitLine(file.size, file.fit)
        if (file.isSuggested) {
            SecondaryText(stringResource(R.string.local_models_suggested))
        }
        DownloadStatus(file.download)
    }
}

/**
 * A model's row: its name on one line ending in "…" (D-029), the lines
 * under it, and a button on the right. [onClick] makes the whole row a
 * tap target.
 */
@Composable
private fun ModelRow(
    name: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    lines: @Composable () -> Unit,
) {
    val tappable = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(tappable)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            lines()
        }
        trailing()
    }
}

/** Download while nothing runs; Cancel while it waits or runs; nothing once downloaded. */
@Composable
private fun DownloadButton(download: DownloadUi, onDownload: () -> Unit, onCancel: () -> Unit) {
    when (download) {
        DownloadUi.Waiting, is DownloadUi.Running -> TextButton(onClick = onCancel) {
            Text(stringResource(R.string.settings_cancel))
        }
        DownloadUi.Downloaded -> Unit
        DownloadUi.Ready, DownloadUi.Failed, DownloadUi.Damaged, is DownloadUi.NoSpace -> TextButton(onClick = onDownload) {
            Text(stringResource(R.string.local_models_download))
        }
    }
}

@Composable
private fun DownloadStatus(download: DownloadUi) {
    when (download) {
        DownloadUi.Ready -> Unit
        DownloadUi.Waiting -> SecondaryText(stringResource(R.string.local_models_waiting))
        is DownloadUi.Running -> {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.local_models_progress, download.downloaded, download.total),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = MonospaceFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DownloadUi.Downloaded -> StatusText(stringResource(R.string.local_models_downloaded), MaterialTheme.colorScheme.primary)
        DownloadUi.Failed -> StatusText(stringResource(R.string.local_models_failed), MaterialTheme.colorScheme.error)
        DownloadUi.Damaged -> StatusText(stringResource(R.string.local_models_damaged), MaterialTheme.colorScheme.error)
        is DownloadUi.NoSpace -> StatusText(stringResource(R.string.local_models_no_space, download.needed), MaterialTheme.colorScheme.error)
    }
}

/** "1.2 GB · Fits": the size in the number font, the label in its colour. */
@Composable
private fun SizeAndFitLine(size: String, fit: FitUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SizeLine(size)
        Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FitText(fit)
    }
}

@Composable
private fun SizeLine(size: String) {
    Text(
        size,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = MonospaceFamily,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** Fits in the accent, Tight muted, Too big in the error colour. */
@Composable
private fun FitText(fit: FitUi) {
    val (text, color) = when (fit) {
        FitUi.FITS -> stringResource(R.string.local_models_fits) to MaterialTheme.colorScheme.primary
        FitUi.TIGHT -> stringResource(R.string.local_models_tight) to MaterialTheme.colorScheme.onSurfaceVariant
        FitUi.TOO_BIG -> stringResource(R.string.local_models_too_big) to MaterialTheme.colorScheme.error
    }
    StatusText(text, color)
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SecondaryText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MutedLine(text: String, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

/** The same field as "Search settings"; the keyboard's search key starts the search at once. */
@Composable
private fun HubSearchField(query: String, actions: LocalModelsActions) {
    TextField(
        value = query,
        onValueChange = actions.onQueryChange,
        placeholder = { Text(stringResource(R.string.local_models_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { actions.onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.settings_clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { actions.onSearch() }),
        shape = RoundedCornerShape(22.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun DeleteDialog(model: DownloadedModelUi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.local_models_delete_title, model.fileName)) },
        // The file itself goes, not just the row (user request, 2026-10-03).
        text = { Text(stringResource(R.string.local_models_delete_clears, model.size), fontFamily = MonospaceFamily) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.local_models_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
