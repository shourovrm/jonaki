package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.ThemeMode
import androidx.compose.ui.text.style.TextOverflow
import app.jonaki.core.ui.MonospaceFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionLabel(stringResource(R.string.settings_section_chat))
            ChatServicesSection(state, actions)
            SectionLabel(stringResource(R.string.settings_section_youtube))
            Group {
                KeyField(stringResource(R.string.settings_gemini_key), state.geminiKey, actions)
            }
            SearchSection(state, actions)
            SectionLabel(stringResource(R.string.settings_section_memory))
            Group {
                NavigationRow(stringResource(R.string.settings_memory_open), onClick = actions.onOpenMemory)
            }
            SectionLabel(stringResource(R.string.settings_section_skills))
            Group {
                NavigationRow(stringResource(R.string.settings_skills_open), onClick = actions.onOpenSkills)
            }
            SectionLabel(stringResource(R.string.settings_section_files))
            Group {
                LinkedFolderRow(state.linkedFolderName, actions)
            }
            ScheduledSection(state.scheduledItems, actions.onCancelScheduled)
            SectionLabel(stringResource(R.string.settings_section_appearance))
            ThemeChooser(state.themeMode, actions.onThemeModeChange)
            Group {
                NavigationRow(stringResource(R.string.settings_status_icons), onClick = actions.onOpenStatusIcons)
            }
        }
    }
}

@Composable
private fun SearchSection(state: SettingsUiState, actions: SettingsActions) {
    SectionLabel(stringResource(R.string.settings_section_search))
    Text(
        stringResource(R.string.settings_search_order),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp),
    )
    Group {
        state.searchServices.forEachIndexed { index, service ->
            if (index > 0) GroupDivider()
            SearchServiceItem(
                rank = index + 1,
                service = service,
                canMoveUp = index > 0,
                canMoveDown = index < state.searchServices.lastIndex,
                actions = actions,
            )
        }
    }
    Spacer(Modifier.size(12.dp))
    Group {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { actions.onWebSearchOffInNewThreadsChange(!state.webSearchOffInNewThreads) }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.settings_search_off_new), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = state.webSearchOffInNewThreads, onCheckedChange = actions.onWebSearchOffInNewThreadsChange)
        }
    }
}

@Composable
private fun SearchServiceItem(
    rank: Int,
    service: SearchServiceRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: SettingsActions,
) {
    var editing by rememberSaveable(service.key) { mutableStateOf(false) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { editing = !editing }
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$rank", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(service.displayName, style = MaterialTheme.typography.titleSmall)
                if (service.apiKey.isSet) {
                    Text(
                        service.apiKey.maskedKey.orEmpty(),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BalanceLine(service.apiKey.balance)
                } else {
                    KeyStatus(isSet = false)
                }
            }
            IconButton(onClick = { actions.onSearchServiceMove(service.key, -1) }, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.settings_move_up, service.displayName))
            }
            IconButton(onClick = { actions.onSearchServiceMove(service.key, 1) }, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.settings_move_down, service.displayName))
            }
        }
        if (editing) {
            KeyField(
                label = "${service.displayName} · ${stringResource(R.string.settings_api_key)}",
                slot = service.apiKey,
                actions = actions,
            )
        }
    }
}

@Composable
private fun KeyStatus(isSet: Boolean) {
    Text(
        stringResource(if (isSet) R.string.settings_key_set else R.string.settings_key_not_set),
        style = MaterialTheme.typography.labelMedium,
        color = if (isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChooser(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.settings_theme_system,
        ThemeMode.LIGHT to R.string.settings_theme_light,
        ThemeMode.DARK to R.string.settings_theme_dark,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
}

/** The linked folder's name wraps in full, like a path in an opened view (D-029). */
@Composable
private fun LinkedFolderRow(folderName: String?, actions: SettingsActions) {
    if (folderName == null) {
        NavigationRow(stringResource(R.string.settings_link_folder), onClick = actions.onLinkFolder)
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = actions.onLinkFolder)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_linked_folder), style = MaterialTheme.typography.titleSmall)
            Text(
                folderName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = actions.onUnlinkFolder) {
            Text(stringResource(R.string.settings_unlink_folder))
        }
    }
}

@Composable
private fun NavigationRow(text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
