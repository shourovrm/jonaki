package app.jonaki.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight

/** An entry of the selection top bar's menu, after Select all. */
class SelectionAction(val label: String, val onClick: () -> Unit)

/**
 * System Back leaves selection mode before it does anything else. It is
 * registered after the screen's own handlers, so it wins while it is enabled.
 */
@Composable
fun SelectionBackHandler(selecting: Boolean, onLeave: () -> Unit) {
    BackHandler(enabled = selecting, onBack = onLeave)
}

/**
 * The top bar of a list in selection mode: Close, the count, Delete, and a
 * menu with Select all (Deselect all when [allSelected]) and [extraActions].
 * The menu keeps the bar within 360 dp at large font sizes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
    extraActions: List<SelectionAction> = emptyList(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selectAllLabel = stringResource(if (allSelected) R.string.selection_deselect_all else R.string.selection_select_all)
    TopAppBar(
        title = {
            Text(
                pluralStringResource(R.plurals.selection_count, selectedCount, selectedCount),
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.selection_close))
            }
        },
        actions = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.selection_delete),
                    tint = JonakiTheme.colors.deny,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.selection_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(selectAllLabel) },
                        onClick = {
                            menuOpen = false
                            onToggleSelectAll()
                        },
                    )
                    for (action in extraActions) {
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                menuOpen = false
                                action.onClick()
                            },
                        )
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
