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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.ApprovalModeOptions
import app.jonaki.core.ui.ThemeMode
import androidx.compose.ui.text.style.TextOverflow
import app.jonaki.core.ui.MonospaceFamily

/**
 * One Settings sub-page (D-128): today's sections, moved onto the page that
 * the first page's row opens. [SettingsHomeScreen] is the first page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScreen(page: SettingsPage, state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(page.title),
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
            when (page) {
                SettingsPage.MODELS -> ModelsPage(state, actions)
                // The app shows LocalModelsScreen for this page instead; it never reaches here.
                SettingsPage.LOCAL_MODELS -> Unit
                SettingsPage.WEB -> WebPage(state, actions)
                SettingsPage.TOOLS -> ToolsPage(state, actions)
                SettingsPage.GUARDRAILS -> GuardrailsPage(state, actions)
                SettingsPage.SUBAGENTS -> SubagentsPage(state, actions)
                SettingsPage.ANSWERS -> AnswersAndPersonasSections(state, actions)
                SettingsPage.MEMORY_SKILLS -> MemoryAndSkillsPage(state, actions)
                SettingsPage.FILES_SCHEDULE -> FilesAndSchedulePage(state, actions)
                SettingsPage.THEME -> ThemePage(state, actions)
                SettingsPage.PERMISSIONS -> PermissionsSection(state.permissions, actions.onPermissionTap)
                SettingsPage.ABOUT -> AboutSection(
                    state.appVersion,
                    actions.onOpenGitHub,
                    state.update,
                    actions.onCheckForUpdate,
                    actions.onInstallUpdate,
                    actions.onOpenSetup,
                )
            }
        }
    }
}

@Composable
private fun ModelsPage(state: SettingsUiState, actions: SettingsActions) {
    SectionLabel(stringResource(R.string.settings_section_chat))
    ChatServicesSection(state, actions)
    ImageGenerationSection(state.imageGeneration, actions)
    VectorImageGenerationSection(state.imageGeneration, actions)
    VideoGenerationSection(state.videoGeneration, actions)
}

@Composable
private fun WebPage(state: SettingsUiState, actions: SettingsActions) {
    SearchSection(state, actions)
    SectionLabel(stringResource(R.string.settings_section_youtube))
    Group {
        KeyField(stringResource(R.string.settings_gemini_key), state.geminiKey, actions)
    }
}

@Composable
private fun ToolsPage(state: SettingsUiState, actions: SettingsActions) {
    SectionLabel(stringResource(R.string.settings_section_approvals))
    Group {
        ApprovalModeOptions(
            selected = state.approvalMode,
            onSelect = { choice -> if (choice != null) actions.onApprovalModeChange(choice) },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
    SectionLabel(stringResource(R.string.settings_approvals_always))
    Group {
        ApprovalRuleRows(state.approvalRules, state.approvalRuleChoices, actions)
    }
    SectionLabel(stringResource(R.string.settings_section_tools))
    Group {
        NavigationRow(
            text = stringResource(R.string.settings_tools_all),
            summary = pluralStringResource(R.plurals.settings_tools_all_summary, state.toolGroupCount, state.toolGroupCount),
            onClick = actions.onOpenTools,
        )
        GroupDivider()
        NavigationRow(stringResource(R.string.settings_python), onClick = actions.onOpenPython)
    }
    SectionLabel(stringResource(R.string.settings_section_mcp))
    Group {
        McpServerRows(state.mcpServers, actions)
    }
}

@Composable
private fun GuardrailsPage(state: SettingsUiState, actions: SettingsActions) {
    val options = state.guardrails
    SectionLabel(stringResource(R.string.settings_jev_guard))
    JevGuardSection(state.jev, state.jevGuardAvailable, actions.onJevOptionsChange)
    SectionLabel(stringResource(R.string.settings_section_after_outside_content))
    Group {
        OptionSwitchRow(
            text = stringResource(R.string.settings_guardrail_ask_before_sending_out),
            isOn = options.askBeforeSendingOut,
            onChange = { on -> actions.onGuardrailOptionsChange(options.copy(askBeforeSendingOut = on)) },
        )
        GroupDivider()
        OptionSwitchRow(
            text = stringResource(R.string.settings_guardrail_hold_new_facts),
            isOn = options.holdNewFacts,
            onChange = { on -> actions.onGuardrailOptionsChange(options.copy(holdNewFacts = on)) },
        )
    }
}

@Composable
private fun MemoryAndSkillsPage(state: SettingsUiState, actions: SettingsActions) {
    val options = state.memoryOptions
    SectionLabel(stringResource(R.string.settings_section_memory))
    Group {
        NavigationRow(
            text = stringResource(R.string.settings_memory_open),
            summary = pluralStringResource(R.plurals.settings_summary_facts, state.factCount, state.factCount),
            onClick = actions.onOpenMemory,
        )
        GroupDivider()
        OptionSwitchRow(
            text = stringResource(R.string.settings_memory_save_facts),
            isOn = options.saveFactsFromChats,
            onChange = { on -> actions.onMemoryOptionsChange(options.copy(saveFactsFromChats = on)) },
        )
        GroupDivider()
        OptionSwitchRow(
            text = stringResource(R.string.settings_memory_review_facts),
            isOn = options.reviewNewFacts,
            onChange = { on -> actions.onMemoryOptionsChange(options.copy(reviewNewFacts = on)) },
        )
        GroupDivider()
        OptionSwitchRow(
            text = stringResource(R.string.settings_memory_suggest_global),
            isOn = options.suggestFactsForAllThreads,
            onChange = { on -> actions.onMemoryOptionsChange(options.copy(suggestFactsForAllThreads = on)) },
        )
        GroupDivider()
        NavigationRow(stringResource(R.string.settings_memory_export), onClick = actions.onExportMemory)
    }
    SectionLabel(stringResource(R.string.settings_section_skills))
    Group {
        NavigationRow(
            text = stringResource(R.string.settings_skills_open),
            summary = pluralStringResource(R.plurals.settings_summary_skills, state.skillCount, state.skillCount),
            onClick = actions.onOpenSkills,
        )
        GroupDivider()
        OptionSwitchRow(
            text = stringResource(R.string.settings_skills_suggest),
            isOn = options.suggestSkills,
            onChange = { on -> actions.onMemoryOptionsChange(options.copy(suggestSkills = on)) },
        )
    }
}

@Composable
private fun FilesAndSchedulePage(state: SettingsUiState, actions: SettingsActions) {
    SectionLabel(stringResource(R.string.settings_section_files))
    Group {
        LinkedFolderRow(state.linkedFolderName, actions)
    }
    ReminderSettingsSection(state.reminders, actions)
    ScheduledSection(state.scheduledItems, actions.onCancelScheduled)
}

@Composable
private fun ThemePage(state: SettingsUiState, actions: SettingsActions) {
    Spacer(Modifier.size(16.dp))
    ThemeChooser(state.themeMode, actions.onThemeModeChange)
    Spacer(Modifier.size(12.dp))
    Group {
        NavigationRow(stringResource(R.string.settings_status_icons), onClick = actions.onOpenStatusIcons)
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = actions.onUnlinkFolder) {
            Text(stringResource(R.string.settings_unlink_folder))
        }
    }
}

/** A row that opens another page; [summary] is its current state on one line ending in "…" (D-029). */
@Composable
internal fun NavigationRow(text: String, onClick: () -> Unit, summary: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** A section's rows on the page itself, no card (D-123); the label above and the hairlines set it apart. */
@Composable
internal fun Group(content: @Composable () -> Unit) {
    // Rows pad themselves by 16 dp, so 4 dp here lines their text up with the 20 dp section label.
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { content() }
}

@Composable
internal fun GroupDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
