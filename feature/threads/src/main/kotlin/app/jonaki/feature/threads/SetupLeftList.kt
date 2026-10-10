package app.jonaki.feature.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * What the first-run cards left undone, above the threads (D-184). A row
 * opens its card again and goes when the thing is set; "Hide" removes the
 * list for good, since Settings has all of it.
 */
@Composable
internal fun SetupLeftList(left: List<SetupLeft>, onSetUp: (SetupLeft) -> Unit, onHide: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.threads_setup_left),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onHide) {
                Text(stringResource(R.string.threads_setup_hide), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (item in left) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(end = 12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onSetUp(item) }.padding(end = 12.dp),
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(stringResource(nameOf(item)), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item == SetupLeft.MODEL) {
                        Text(
                            stringResource(R.string.threads_setup_model_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    stringResource(if (item == SetupLeft.NOTIFICATIONS) R.string.threads_setup_allow else R.string.threads_setup_add),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(end = 12.dp))
    }
}

private fun nameOf(item: SetupLeft): Int = when (item) {
    SetupLeft.MODEL -> R.string.threads_setup_model
    SetupLeft.WEB_SEARCH -> R.string.threads_setup_web_search
    SetupLeft.NOTIFICATIONS -> R.string.threads_setup_notifications
}
