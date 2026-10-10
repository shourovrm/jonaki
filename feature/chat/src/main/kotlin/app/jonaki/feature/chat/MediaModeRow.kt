package app.jonaki.feature.chat

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

/**
 * The slim block above the message box. The first line holds one chip for
 * each kind of media the thread can make (picture, vector image, video); at
 * most one is selected. While one is selected, a second line under the chips
 * names the model and, for a video, its length, resolution and estimated
 * cost, because a send in this mode shows no approval card. The details are
 * on their own line, so the chips never move when a kind is selected.
 *
 * Three chips with a short label each take about 290 dp at 360 dp width and
 * font scale 1.3 (labels in the small label style), so all stay labelled.
 */
@Composable
internal fun MediaModeRow(
    mediaMode: MediaModeUi,
    onChange: (selected: MediaKind?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (kind in mediaMode.availableKinds) {
                val isSelected = kind == mediaMode.selected
                FilterChip(
                    selected = isSelected,
                    // A mode that is on can always be switched off.
                    enabled = mediaMode.canChange || isSelected,
                    onClick = { onChange(MediaMode.afterTap(mediaMode.selected, kind, mediaMode.canChange)) },
                    label = { Text(stringResource(kind.labelRes), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    leadingIcon = { Icon(kind.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        val details = MediaMode.detailsLine(mediaMode, stringResource(R.string.chat_media_price_unknown))
        if (details != null) {
            Text(
                details,
                style = MaterialTheme.typography.labelMedium,
                color = JonakiTheme.colors.inkSoft,
                // Two lines: a 40-character model name beside a price may not fit on one (D-029).
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

// The D-029 check: a 360 dp wide phone at font scale 1.3, with a 40-character model name.
private const val LONG_IMAGE_MODEL_NAME = "black-forest-labs/flux.2-klein-4b-preview"
private const val VIDEO_MODEL_NAME = "Grok Imagine Video 1.5 Lite"

private val noneSelected = MediaModeUi(availableKinds = MediaKind.entries.toList(), selected = null, canChange = true)

private val pictureSelected = noneSelected.copy(
    selected = MediaKind.PICTURE,
    modelName = LONG_IMAGE_MODEL_NAME,
    priceText = "\$0.08 per image",
)

private val vectorSelected = noneSelected.copy(
    selected = MediaKind.VECTOR,
    modelName = "Recraft V3 Vector",
    priceText = "\$0.08 per image",
)

private val videoSelected = noneSelected.copy(
    selected = MediaKind.VIDEO,
    modelName = VIDEO_MODEL_NAME,
    videoLengthText = "4 s",
    videoResolution = "720p",
    videoCostText = "about \$0.12",
)

private val videoWithoutEstimate = videoSelected.copy(videoCostText = null)

@Composable
private fun MediaModePreviewScreen(mediaMode: MediaModeUi) {
    ChatScreen(
        state = ChatSample.finished.copy(draft = "", mediaMode = mediaMode),
        onBack = {},
        onDraftChange = {},
        onSend = {},
        onStop = {},
        onApprovalChoice = { _, _ -> },
        onRetry = {},
        onWebSearchChange = {},
    )
}

@Composable
private fun MediaModeRowsPreview(rows: List<MediaModeUi>) {
    JonakiTheme(ThemeMode.LIGHT) {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in rows) {
                    MediaModeRow(row, onChange = {})
                }
            }
        }
    }
}

@Preview(name = "Video on, dark", widthDp = 360, heightDp = 640, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaModeVideoDarkPreview() {
    JonakiTheme(ThemeMode.DARK) { MediaModePreviewScreen(videoSelected) }
}

@Preview(name = "Picture on, light", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun MediaModePictureLightPreview() {
    JonakiTheme(ThemeMode.LIGHT) { MediaModePreviewScreen(pictureSelected) }
}

@Preview(name = "Video on, Bangla", locale = "bn", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun MediaModeVideoBanglaPreview() {
    JonakiTheme(ThemeMode.LIGHT) { MediaModePreviewScreen(videoSelected) }
}

@Preview(name = "None selected and each kind selected", widthDp = 360, fontScale = 1.3f)
@Composable
private fun MediaModeAllKindsPreview() {
    MediaModeRowsPreview(listOf(noneSelected, pictureSelected, vectorSelected, videoSelected, videoWithoutEstimate))
}

@Preview(name = "None selected and each kind selected, Bangla", locale = "bn", widthDp = 360, fontScale = 1.3f)
@Composable
private fun MediaModeAllKindsBanglaPreview() {
    MediaModeRowsPreview(listOf(noneSelected, pictureSelected, vectorSelected, videoSelected, videoWithoutEstimate))
}

@Preview(name = "One kind available", widthDp = 360, fontScale = 1.3f)
@Composable
private fun MediaModeOneKindPreview() {
    MediaModeRowsPreview(
        listOf(
            noneSelected.copy(availableKinds = listOf(MediaKind.VIDEO)),
            videoSelected.copy(availableKinds = listOf(MediaKind.VIDEO)),
        ),
    )
}

@Preview(name = "One kind available, Bangla", locale = "bn", widthDp = 360, fontScale = 1.3f)
@Composable
private fun MediaModeOneKindBanglaPreview() {
    MediaModeRowsPreview(listOf(noneSelected.copy(availableKinds = listOf(MediaKind.PICTURE))))
}

@Preview(name = "Chips disabled", widthDp = 360, fontScale = 1.3f)
@Composable
private fun MediaModeDisabledPreview() {
    MediaModeRowsPreview(listOf(noneSelected.copy(canChange = false), videoSelected.copy(canChange = false)))
}

@Preview(name = "Chips disabled, Bangla", locale = "bn", widthDp = 360, fontScale = 1.3f)
@Composable
private fun MediaModeDisabledBanglaPreview() {
    MediaModeRowsPreview(listOf(noneSelected.copy(canChange = false), pictureSelected.copy(canChange = false)))
}
