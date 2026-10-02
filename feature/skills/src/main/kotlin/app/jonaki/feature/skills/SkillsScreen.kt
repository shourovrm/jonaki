package app.jonaki.feature.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(state: SkillsUiState, actions: SkillsActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.skills_title), fontWeight = FontWeight.SemiBold)
                        if (state.threadTitle != null) {
                            Text(
                                state.threadTitle,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.skills_back))
                    }
                },
                actions = {
                    if (state.canRestoreBuiltIns) {
                        RestoreMenu(actions.onRestoreBuiltIns)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onOpenImport,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.skills_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.skills.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.skills_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                    )
                }
            }
            items(state.skills, key = { skill -> skill.name }) { skill ->
                SkillCard(skill, showSwitch = state.isThreadView, actions = actions)
            }
        }
    }
    if (state.import.isOpen) {
        ImportDialog(state.import, actions)
    }
    val replaceName = state.import.replaceName
    if (replaceName != null) {
        ReplaceDialog(name = replaceName, onConfirm = actions.onConfirmReplace, onDismiss = actions.onCloseImport)
    }
}

@Composable
private fun RestoreMenu(onRestore: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.skills_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.skills_restore)) },
                onClick = {
                    open = false
                    onRestore()
                },
            )
        }
    }
}

@Composable
private fun SkillCard(skill: SkillRowUi, showSwitch: Boolean, actions: SkillsActions) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { actions.onOpenSkill(skill.name) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                // A name keeps one line in a list (D-029); badges get their own line.
                Text(
                    skill.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BadgeLine(SkillBadges.of(skill))
                DescriptionOrProblem(skill)
            }
            if (showSwitch) {
                Switch(
                    checked = skill.enabledInThread && skill.problem == null,
                    onCheckedChange = { enabled -> actions.onEnabledChange(skill.name, enabled) },
                    enabled = skill.problem == null,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun BadgeLine(badges: List<SkillBadge>) {
    if (badges.isEmpty()) {
        return
    }
    val labels = badges.map { badge ->
        when (badge) {
            SkillBadge.BUILT_IN -> stringResource(R.string.skills_built_in)
            SkillBadge.EDITED -> stringResource(R.string.skills_edited)
        }
    }
    Text(
        labels.joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
    )
}

@Composable
private fun DescriptionOrProblem(skill: SkillRowUi) {
    val problem = skill.problem
    if (problem != null) {
        Text(
            problem,
            style = MaterialTheme.typography.bodyMedium,
            color = JonakiTheme.colors.deny,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        return
    }
    Text(
        skill.description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}
