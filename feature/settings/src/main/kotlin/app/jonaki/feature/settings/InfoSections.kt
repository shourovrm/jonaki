package app.jonaki.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.MonospaceFamily

/** One live permission row and the status the app read for it. */
@Immutable
data class PermissionRowUi(
    val row: PermissionRow,
    val status: PermissionStatus,
)

/** Settings > Permissions: the four permissions the user controls, then the ones Android grants at install (D-124). */
@Composable
internal fun PermissionsSection(rows: List<PermissionRowUi>, onTap: (PermissionRow) -> Unit) {
    Spacer(Modifier.height(8.dp))
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
    PermissionStatus.NOT_ALLOWED, PermissionStatus.OFF -> R.string.settings_permissions_status_not_allowed
    PermissionStatus.BLOCKED -> R.string.settings_permissions_status_blocked
}

/** The same colours as a key's "Set" and a low balance elsewhere in Settings. */
@Composable
private fun statusColor(status: PermissionStatus): Color = when (status) {
    PermissionStatus.ALLOWED, PermissionStatus.SELECTED_PHOTOS -> MaterialTheme.colorScheme.primary
    PermissionStatus.NOT_ALLOWED, PermissionStatus.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    PermissionStatus.BLOCKED -> MaterialTheme.colorScheme.error
}

/**
 * Settings > About: the app's name, version and one line on what it does,
 * then who made it and where the source is. A short page of text rather than
 * a list of rows, because nothing here is a setting.
 */
@Composable
internal fun AboutSection(appVersion: String, onOpenGitHub: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JonakiMark()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_about_app_name),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.settings_about_version, appVersion),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MonospaceFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.settings_about_description), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.settings_about_developer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        GitHubLink(onOpenGitHub)
    }
}

/** The launcher icon as the home screen shows it: the lit j on its dark tile, in both themes. */
@Composable
private fun JonakiMark() {
    Image(
        painterResource(R.drawable.ic_jonaki_mark),
        contentDescription = null,
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherTileColor),
    )
}

// ic_launcher_background in the app module.
private val LauncherTileColor = Color(0xFF11140F)

/** The link keeps the 48 dp touch height; the path wraps to two lines, then "…" (D-029). */
@Composable
private fun GitHubLink(onOpenGitHub: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onOpenGitHub)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_github),
            contentDescription = stringResource(R.string.settings_about_github),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.settings_about_github_url),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
