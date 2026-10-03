package app.jonaki.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check: a 360 dp wide phone at font scale 1.3, in light and dark.

private val sampleRows = listOf(
    PermissionRowUi(PermissionRow.NOTIFICATIONS, PermissionStatus.ALLOWED),
    PermissionRowUi(PermissionRow.CALENDAR, PermissionStatus.BLOCKED),
    PermissionRowUi(PermissionRow.PHOTOS, PermissionStatus.SELECTED_PHOTOS),
    PermissionRowUi(PermissionRow.ALARMS, PermissionStatus.OFF),
)

@Composable
private fun InfoSectionsSample(themeMode: ThemeMode) {
    JonakiTheme(themeMode) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            PermissionsSection(sampleRows, onTap = {})
            AboutSection(appVersion = "1.0.0", onOpenGitHub = {})
        }
    }
}

@Preview(name = "Permissions and About, light", widthDp = 360, heightDp = 1100, fontScale = 1.3f)
@Composable
private fun InfoSectionsLightPreview() {
    InfoSectionsSample(ThemeMode.LIGHT)
}

@Preview(name = "Permissions and About, dark", widthDp = 360, heightDp = 1100, fontScale = 1.3f)
@Composable
private fun InfoSectionsDarkPreview() {
    InfoSectionsSample(ThemeMode.DARK)
}
