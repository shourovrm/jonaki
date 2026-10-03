package app.jonaki.feature.memory

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(state: MemoryUiState, actions: MemoryActions, modifier: Modifier = Modifier) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val allFacts = state.waitingForReview + state.threadFacts + state.projectFacts + state.globalFacts
    val subtitle = state.threadTitle ?: state.projectName

    Scaffold(
        modifier = modifier,
        topBar = {
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.memory_add)) },
            )
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
                    ReviewCard(fact, actions)
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
            )
            item(key = "review-switch") {
                ReviewSwitch(state.reviewMode, actions.onReviewModeChange)
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
        FactCard(fact, hasProject, onEdit = { onEdit(fact.id) }, onDelete = { onDelete(fact.id) }, actions = actions)
    }
}

@Composable
private fun FactCard(fact: MemoryFactUi, hasProject: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, actions: MemoryActions) {
    // A plain row: the list's spacing separates facts without a card (D-123).
    Surface(
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.clickable(onClick = onEdit).padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
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
                SourceLine(fact, onClick = { actions.onOpenSource(fact.id) })
            }
            FactMenuButton(fact, hasProject, onEdit, onDelete, actions)
        }
    }
}

@Composable
private fun SourceLine(fact: MemoryFactUi, onClick: () -> Unit) {
    val preview = fact.sourcePreview ?: return
    Text(
        stringResource(R.string.memory_from, preview),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp).clickable(onClick = onClick),
    )
}

@Composable
private fun FactMenuButton(fact: MemoryFactUi, hasProject: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, actions: MemoryActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
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

@Composable
private fun ReviewCard(fact: MemoryFactUi, actions: MemoryActions) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Text(fact.text, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (fact.threadTitle != null) {
                Text(
                    stringResource(R.string.memory_in_thread, fact.threadTitle),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            SourceLine(fact, onClick = { actions.onOpenSource(fact.id) })
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { actions.onDelete(fact.id) }) {
                    Text(stringResource(R.string.memory_discard), color = JonakiTheme.colors.deny)
                }
                TextButton(onClick = { actions.onKeep(fact.id) }) {
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
