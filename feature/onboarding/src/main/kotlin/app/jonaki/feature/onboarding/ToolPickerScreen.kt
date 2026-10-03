package app.jonaki.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.ToolGroupChoice
import app.jonaki.core.ui.ToolGroupList
import app.jonaki.core.ui.ToolGroupRowUi

/**
 * The first-run tool picker (plan M8 step 3): one switch per tool group.
 * It shows once on a new install and once after the update that added it;
 * the same switches stay in Settings > Tools. A download keeps going after
 * Continue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPickerScreen(
    rows: List<ToolGroupRowUi>,
    onSwitch: (ToolGroupChoice, Boolean) -> Unit,
    onDownload: (ToolGroupChoice) -> Unit,
    onCancelDownload: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.onboarding_tools_title), fontWeight = FontWeight.SemiBold) })
        },
        bottomBar = {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.onboarding_continue))
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.onboarding_tools_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            // On the page itself, no card (D-123).
            ToolGroupList(rows, onSwitch, onDownload, onCancelDownload)
        }
    }
}
