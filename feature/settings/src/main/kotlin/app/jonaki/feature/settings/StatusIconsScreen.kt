package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.ContextRing
import app.jonaki.core.ui.DotStyle
import app.jonaki.core.ui.GlowDot
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme

/**
 * Explains the chat's status strip icons in one line each (D-027), and holds
 * the switch that shows or hides the strip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusIconsScreen(
    showStatusStrip: Boolean,
    onShowStatusStripChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_status_icons), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Column {
                    LegendRow(stringResource(R.string.settings_icon_model), stringResource(R.string.settings_icon_model_help)) {
                        GlowDot(JonakiTheme.colors.live, DotStyle.GLOWING, dotSize = 10.dp)
                    }
                    LegendDivider()
                    LegendRow(stringResource(R.string.settings_icon_window), stringResource(R.string.settings_icon_window_help)) {
                        Icon(JonakiIcons.Memory, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    LegendDivider()
                    LegendRow(stringResource(R.string.settings_icon_used), stringResource(R.string.settings_icon_used_help)) {
                        ContextRing(percent = 24, ringSize = 20.dp)
                    }
                    LegendDivider()
                    LegendRow(stringResource(R.string.settings_icon_cost), stringResource(R.string.settings_icon_cost_help)) {
                        Icon(JonakiIcons.Payments, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    LegendDivider()
                    LegendRow(stringResource(R.string.settings_icon_bypass), stringResource(R.string.settings_icon_bypass_help)) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = JonakiTheme.colors.deny,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = showStatusStrip, role = Role.Switch, onValueChange = onShowStatusStripChange)
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_show_status_strip),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = showStatusStrip, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(name: String, help: String, icon: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 66.dp))
}
