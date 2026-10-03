package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One live permission row and the status the app read for it. */
@Immutable
data class PermissionRowUi(
    val row: PermissionRow,
    val status: PermissionStatus,
)

/** Settings > Permissions: the four permissions the user controls, then the ones Android grants at install (D-124). */
@Composable
internal fun PermissionsSection(rows: List<PermissionRowUi>, onTap: (PermissionRow) -> Unit) {
    SectionLabel(stringResource(R.string.settings_permissions_header))
    Group {
        rows.forEachIndexed { index, item ->
            if (index > 0) GroupDivider()
            LivePermissionRow(item, onTap)
        }
    }
    Text(
        stringResource(R.string.settings_permissions_always_on_header),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
    )
    Group {
        AlwaysOnRow.entries.forEachIndexed { index, row ->
            if (index > 0) GroupDivider()
            TitleAndLine(
                title = stringResource(row.title),
                line = stringResource(row.purpose),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Name and purpose keep one line each and end in "…" (D-029); the status gets its own line. */
@Composable
private fun LivePermissionRow(item: PermissionRowUi, onTap: (PermissionRow) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap(item.row) }
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            TitleAndLine(title = stringResource(item.row.title), line = stringResource(item.row.purpose))
            Text(
                stringResource(statusText(item.status)),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(item.status),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (PermissionStatuses.buttonFor(item.status)) {
            PermissionButton.ALLOW -> TextButton(onClick = { onTap(item.row) }) {
                Text(stringResource(R.string.settings_permissions_action_ask))
            }
            PermissionButton.OPEN_SETTINGS -> TextButton(onClick = { onTap(item.row) }) {
                Text(stringResource(R.string.settings_permissions_action_open_settings))
            }
            PermissionButton.NONE -> Unit
        }
    }
}

private fun statusText(status: PermissionStatus): Int = when (status) {
    PermissionStatus.ALLOWED -> R.string.settings_permissions_status_allowed
    PermissionStatus.SELECTED_PHOTOS -> R.string.settings_permissions_status_selected_photos
    PermissionStatus.NOT_ASKED -> R.string.settings_permissions_status_not_asked
    PermissionStatus.DENIED, PermissionStatus.BLOCKED -> R.string.settings_permissions_status_denied
    PermissionStatus.OFF -> R.string.settings_permissions_status_off
}

/** The same colours as a key's "Set" and a low balance elsewhere in Settings. */
@Composable
private fun statusColor(status: PermissionStatus): Color = when (status) {
    PermissionStatus.ALLOWED, PermissionStatus.SELECTED_PHOTOS -> MaterialTheme.colorScheme.primary
    PermissionStatus.NOT_ASKED -> MaterialTheme.colorScheme.onSurfaceVariant
    PermissionStatus.DENIED, PermissionStatus.BLOCKED, PermissionStatus.OFF -> MaterialTheme.colorScheme.error
}

/** Settings > About: the version, the developer and the GitHub link. */
@Composable
internal fun AboutSection(appVersion: String, onOpenGitHub: () -> Unit) {
    SectionLabel(stringResource(R.string.settings_about_header))
    Group {
        OneLineRow(stringResource(R.string.settings_about_version, appVersion))
        GroupDivider()
        OneLineRow(stringResource(R.string.settings_about_developer))
        GroupDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenGitHub)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_github), contentDescription = null)
            Spacer(Modifier.width(16.dp))
            TitleAndLine(
                title = stringResource(R.string.settings_about_github),
                line = stringResource(R.string.settings_about_github_url),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OneLineRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TitleAndLine(title: String, line: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
