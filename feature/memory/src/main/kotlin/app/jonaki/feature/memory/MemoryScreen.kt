package app.jonaki.feature.memory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.jonaki.core.ui.Selection
import app.jonaki.core.ui.SelectionBackHandler
import app.jonaki.core.ui.SelectionTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    state: MemoryUiState,
    actions: MemoryActions,
    modifier: Modifier = Modifier,
    /** Facts selected at the start; only the previews set it. */
    initialSelectedIds: Set<Long> = emptySet(),
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var supersededOpen by rememberSaveable { mutableStateOf(false) }
    val allFacts = state.waitingForReview + state.threadFacts + state.projectFacts + state.globalFacts + state.supersededFacts
    val subtitle = state.threadTitle ?: state.projectName

    var storedSelection by rememberSaveable(stateSaver = Selection.saver<Long>()) {
        mutableStateOf(Selection(initialSelectedIds))
    }
    var confirmingSelectionDelete by rememberSaveable { mutableStateOf(false) }
    // Only facts on screen can stay selected: Select all covers what is listed, and a fact
    // that disappears, or sits in the folded superseded section, never counts.
    val shownFacts = shownFactsOf(state, supersededOpen)
    val shownIds = shownFacts.map { fact -> fact.id }
    val selection = storedSelection.pruned(shownIds)
    LaunchedEffect(selection) {
        if (selection !== storedSelection) {
            storedSelection = selection
        }
    }
    val selecting = !selection.isEmpty
    val factSelection = FactSelection(selection) { changed -> storedSelection = changed }
    SelectionBackHandler(selecting) { storedSelection = selection.clear() }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    selectedCount = selection.count,
                    allSelected = selection.coversAll(shownIds),
                    onClose = { storedSelection = selection.clear() },
                    onToggleSelectAll = {
                        storedSelection = if (selection.coversAll(shownIds)) selection.clear() else selection.selectAll(shownIds)
                    },
                    onDelete = { confirmingSelectionDelete = true },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.memory_title), fontWeight = FontWeight.SemiBold)
                            if (subtitle != null) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.memory_back))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.memory_add)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.waitingForReview.isNotEmpty()) {
                sectionLabel(R.string.memory_section_review)
                items(state.waitingForReview, key = { fact -> "review-${fact.id}" }) { fact ->
                    ReviewCard(fact, actions, factSelection)
                }
            }
            if (state.isThreadView) {
                factSection(
                    labelId = R.string.memory_section_thread,
                    emptyId = R.string.memory_empty_thread,
                    facts = state.threadFacts,
                    hasProject = state.projectName != null,
                    onEdit = { factId -> editingId = factId },
                    onDelete = { factId -> deletingId = factId },
                    actions = actions,
                    factSelection = factSelection,
                )
            }
            if (state.projectName != null) {
                factSection(
                    labelId = R.string.memory_section_project,
                    emptyId = R.string.memory_empty_project,
                    facts = state.projectFacts,
                    hasProject = true,
                    onEdit = { factId -> editingId = factId },
                    onDelete = { factId -> deletingId = factId },
                    actions = actions,
                    factSelection = factSelection,
                )
            }
            factSection(
                labelId = R.string.memory_section_global,
                emptyId = R.string.memory_empty_global,
                facts = state.globalFacts,
                hasProject = state.projectName != null,
                onEdit = { factId -> editingId = factId },
                onDelete = { factId -> deletingId = factId },
                actions = actions,
                factSelection = factSelection,
            )
            item(key = "review-switch") {
                ReviewSwitch(state.reviewMode, actions.onReviewModeChange)
            }
            if (state.supersededFacts.isNotEmpty()) {
                supersededSection(
                    facts = state.supersededFacts,
                    open = supersededOpen,
                    onToggle = { supersededOpen = !supersededOpen },
                    onDelete = { factId -> deletingId = factId },
                    actions = actions,
                    factSelection = factSelection,
                )
            }
        }
    }

    if (adding) {
        FactDialog(
            titleId = R.string.memory_add_title,
            initialText = "",
            scopes = state.addScopes,
            onSave = { text, scope ->
                adding = false
                actions.onAdd(text, scope)
            },
            onDismiss = { adding = false },
        )
    }
    val editing = allFacts.firstOrNull { fact -> fact.id == editingId }
    if (editing != null) {
        FactDialog(
            titleId = R.string.memory_edit_title,
            initialText = editing.text,
            scopes = emptyList(),
            onSave = { text, _ ->
                editingId = null
                actions.onEdit(editing.id, text)
            },
            onDismiss = { editingId = null },
        )
    }
    if (confirmingSelectionDelete && selecting) {
        val selectedFacts = shownFacts.filter { fact -> fact.id in selection }
        val onDismissSelectionDelete = { confirmingSelectionDelete = false }
        val onConfirmSelectionDelete = {
            confirmingSelectionDelete = false
            storedSelection = selection.clear()
            // Each fact goes through the same call as a single delete.
            for (fact in selectedFacts) {
                actions.onDelete(fact.id)
            }
        }
        if (selectedFacts.size == 1) {
            DeleteFactDialog(selectedFacts.first().text, onConfirmSelectionDelete, onDismissSelectionDelete)
        } else {
            DeleteFactsDialog(selectedFacts.size, onConfirmSelectionDelete, onDismissSelectionDelete)
        }
    }
    val deleting = allFacts.firstOrNull { fact -> fact.id == deletingId }
    if (deleting != null) {
        DeleteFactDialog(
            text = deleting.text,
            onConfirm = {
                deletingId = null
                actions.onDelete(deleting.id)
            },
            onDismiss = { deletingId = null },
        )
    }
}

/** The selection and how the cards change it; selecting turns off their own buttons. */
private class FactSelection(val selection: Selection<Long>, val onChange: (Selection<Long>) -> Unit) {
    val selecting: Boolean
        get() = !selection.isEmpty

    fun isSelected(factId: Long): Boolean = factId in selection

    fun toggle(factId: Long) = onChange(selection.toggle(factId))
}

/** The facts the screen lists now, in the order shown; the folded superseded section lists none. */
internal fun shownFactsOf(state: MemoryUiState, supersededOpen: Boolean): List<MemoryFactUi> {
    val shown = mutableListOf<MemoryFactUi>()
    shown += state.waitingForReview
    if (state.isThreadView) {
        shown += state.threadFacts
    }
    if (state.projectName != null) {
        shown += state.projectFacts
    }
    shown += state.globalFacts
    if (supersededOpen) {
        shown += state.supersededFacts
    }
    return shown
}

private const val SELECTED_CARD_TINT = 0.14f

/** A fact row has no card of its own: transparent, or the accent tint when selected. */
@Composable
private fun selectedRowColour(selected: Boolean): Color {
    return if (selected) MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_CARD_TINT) else Color.Transparent
}

/** The colour of the review and superseded cards: their raised surface, with the tint over it when selected. */
@Composable
private fun selectedCardColour(selected: Boolean): Color {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    if (!selected) {
        return base
    }
    return MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_CARD_TINT).compositeOver(base)
}

/** Review and superseded cards have no tap action of their own; a tap only counts while selecting. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.selectableCard(factId: Long, selected: Boolean, factSelection: FactSelection): Modifier {
    return this
        .semantics { this.selected = selected }
        .combinedClickable(
            onClick = { if (factSelection.selecting) factSelection.toggle(factId) },
            onLongClick = { factSelection.toggle(factId) },
        )
}

@Composable
private fun SelectedMark() {
    Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp).size(16.dp),
    )
    Spacer(Modifier.width(6.dp))
}

private fun LazyListScope.sectionLabel(labelId: Int) {
    item(key = "label-$labelId") {
        Text(
            stringResource(labelId),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
        )
    }
}

private fun LazyListScope.factSection(
    labelId: Int,
    emptyId: Int,
    facts: List<MemoryFactUi>,
    hasProject: Boolean,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    actions: MemoryActions,
    factSelection: FactSelection,
) {
    sectionLabel(labelId)
    if (facts.isEmpty()) {
        item(key = "empty-$labelId") {
            Text(
                stringResource(emptyId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        return
    }
    items(facts, key = { fact -> "fact-${fact.id}" }) { fact ->
        FactCard(fact, hasProject, onEdit = { onEdit(fact.id) }, onDelete = { onDelete(fact.id) }, actions = actions, factSelection = factSelection)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FactCard(
    fact: MemoryFactUi,
    hasProject: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    actions: MemoryActions,
    factSelection: FactSelection,
) {
    val selected = factSelection.isSelected(fact.id)
    // A plain row: the list's spacing separates facts without a card (D-123).
    Surface(
        color = selectedRowColour(selected),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .semantics { this.selected = selected }
                .combinedClickable(
                    onClick = { if (factSelection.selecting) factSelection.toggle(fact.id) else onEdit() },
                    // A long press used to do nothing; it now starts selection.
                    onLongClick = { factSelection.toggle(fact.id) },
                )
                .padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    if (selected) {
                        SelectedMark()
                    }
                    if (fact.pinned) {
                        Icon(
                            JonakiIcons.PushPin,
                            contentDescription = stringResource(R.string.memory_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp).size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    // A fact is content, not a name: four lines in the list, all of it in the edit dialog (D-029).
                    Text(fact.text, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                SourceLine(fact, enabled = !factSelection.selecting, onClick = { actions.onOpenSource(fact.id) })
            }
            FactMenuButton(fact, hasProject, onEdit, onDelete, actions, enabled = !factSelection.selecting)
        }
    }
}

@Composable
private fun SourceLine(fact: MemoryFactUi, enabled: Boolean, onClick: () -> Unit) {
    val preview = fact.sourcePreview ?: return
    Text(
        stringResource(R.string.memory_from, preview),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp).clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun FactMenuButton(
    fact: MemoryFactUi,
    hasProject: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    actions: MemoryActions,
    enabled: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.memory_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (item in FactMenu.itemsFor(fact, hasProject)) {
                DropdownMenuItem(
                    text = {
                        val isDelete = item == FactMenuItem.DELETE
                        Text(
                            stringResource(labelOf(item)),
                            color = if (isDelete) JonakiTheme.colors.deny else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        when (item) {
                            FactMenuItem.EDIT -> onEdit()
                            FactMenuItem.PIN -> actions.onPinChange(fact.id, true)
                            FactMenuItem.UNPIN -> actions.onPinChange(fact.id, false)
                            FactMenuItem.MOVE_TO_PROJECT -> actions.onMoveToProject(fact.id)
                            FactMenuItem.PROMOTE -> actions.onPromote(fact.id)
                            FactMenuItem.OPEN_SOURCE -> actions.onOpenSource(fact.id)
                            FactMenuItem.DELETE -> onDelete()
                        }
                    },
                )
            }
        }
    }
}

private fun labelOf(item: FactMenuItem): Int = when (item) {
    FactMenuItem.EDIT -> R.string.memory_menu_edit
    FactMenuItem.PIN -> R.string.memory_menu_pin
    FactMenuItem.UNPIN -> R.string.memory_menu_unpin
    FactMenuItem.MOVE_TO_PROJECT -> R.string.memory_menu_move_to_project
    FactMenuItem.PROMOTE -> R.string.memory_menu_promote
    FactMenuItem.OPEN_SOURCE -> R.string.memory_menu_source
    FactMenuItem.DELETE -> R.string.memory_menu_delete
}

/** Facts extraction replaced or removed: folded, with a count, until the user opens it. */
private fun LazyListScope.supersededSection(
    facts: List<MemoryFactUi>,
    open: Boolean,
    onToggle: () -> Unit,
    onDelete: (Long) -> Unit,
    actions: MemoryActions,
    factSelection: FactSelection,
) {
    item(key = "superseded-header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .heightIn(min = 48.dp)
                .padding(start = 4.dp),
        ) {
            Text(
                stringResource(R.string.memory_section_superseded) + " (" + facts.size + ")",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (open) R.string.memory_superseded_hide else R.string.memory_superseded_show),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
    if (open) {
        items(facts, key = { fact -> "superseded-${fact.id}" }) { fact ->
            SupersededCard(fact, onDelete = { onDelete(fact.id) }, actions = actions, factSelection = factSelection)
        }
    }
}

@Composable
private fun SupersededCard(
    fact: MemoryFactUi,
    onDelete: () -> Unit,
    actions: MemoryActions,
    factSelection: FactSelection,
) {
    val selected = factSelection.isSelected(fact.id)
    Surface(
        color = selectedCardColour(selected),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.selectableCard(fact.id, selected, factSelection).padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (selected) {
                    SelectedMark()
                }
                Text(fact.text, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            val supersededAtMillis = fact.supersededAtMillis
            if (supersededAtMillis != null) {
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(supersededAtMillis))
                Text(
                    stringResource(R.string.memory_superseded_on, date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDelete, enabled = !factSelection.selecting) {
                    Text(stringResource(R.string.memory_delete), color = JonakiTheme.colors.deny)
                }
                TextButton(onClick = { actions.onRestore(fact.id) }, enabled = !factSelection.selecting) {
                    Text(stringResource(R.string.memory_restore))
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(fact: MemoryFactUi, actions: MemoryActions, factSelection: FactSelection) {
    val selected = factSelection.isSelected(fact.id)
    Surface(
        color = selectedCardColour(selected),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.selectableCard(fact.id, selected, factSelection).padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (selected) {
                    SelectedMark()
                }
                Text(fact.text, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            // A global fact reaches every thread, so the card says so before the user approves it.
            val whereLine = when {
                fact.threadTitle != null -> stringResource(R.string.memory_in_thread, fact.threadTitle)
                fact.scope == FactScopeUi.GLOBAL -> stringResource(R.string.memory_scope_global)
                else -> null
            }
            if (whereLine != null) {
                Text(
                    whereLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            SourceLine(fact, enabled = !factSelection.selecting, onClick = { actions.onOpenSource(fact.id) })
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { actions.onDelete(fact.id) }, enabled = !factSelection.selecting) {
                    Text(stringResource(R.string.memory_discard), color = JonakiTheme.colors.deny)
                }
                TextButton(onClick = { actions.onKeep(fact.id) }, enabled = !factSelection.selecting) {
                    Text(stringResource(R.string.memory_keep))
                }
            }
        }
    }
}

@Composable
private fun ReviewSwitch(reviewMode: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { onChange(!reviewMode) }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.memory_review_switch), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = reviewMode, onCheckedChange = onChange)
        }
    }
}
