package app.jonaki.feature.chat

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.ThemeMode

/**
 * The line above the message box while a kind of media is on (D-174). It
 * names the model and the settings of the next send, with the price at the
 * right, because a send in this mode shows no approval card. A tap opens the
 * kind's settings sheet. The kind itself is chosen with the media button in
 * the status strip, so nothing is shown while the mode is off.
 */
@Composable
internal fun MediaDetailsLine(mediaMode: MediaModeUi, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val details = MediaMode.detailsText(mediaMode, stringResource(R.string.chat_media_quality_high)) ?: return
    val price = MediaMode.priceOf(mediaMode, stringResource(R.string.chat_media_price_unknown))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = stringResource(R.string.chat_media_settings_open), role = Role.Button, onClick = onOpenSettings)
            .heightIn(min = 44.dp)
            .padding(start = 18.dp, end = 10.dp, top = 4.dp),
    ) {
        Text(
            details,
            style = MaterialTheme.typography.labelMedium,
            color = JonakiTheme.colors.inkSoft,
            // Two lines: a 40-character model name beside a price may not fit on one (D-029).
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (price != null) {
            // Outside the weighted text, so the price is never the part that is cut.
            Text(
                price,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp).size(20.dp),
        )
    }
}

// The D-029 check: a 360 dp wide phone at font scale 1.3, with a 40-character model name.
private const val LONG_IMAGE_MODEL_NAME = "black-forest-labs/flux.2-klein-4b-preview"
private const val VIDEO_MODEL_NAME = "Grok Imagine Video 1.5 Lite"

private val kindRows = listOf(
    MediaKindRowUi(MediaKind.PICTURE, "Recraft V4.1 Flash", "\$0.007 per image"),
    MediaKindRowUi(MediaKind.VECTOR, "Recraft V4.1 Vector", "\$0.08 per image"),
    MediaKindRowUi(MediaKind.VIDEO, VIDEO_MODEL_NAME, "about \$0.12"),
)

private val noneSelected = MediaModeUi(availableKinds = MediaKind.entries.toList(), selected = null, canChange = true, kindRows = kindRows)

internal val pictureSettingsSample = MediaSettingsUi(
    models = listOf(
        MediaModelRowUi("openrouter:recraft/recraft-v4.1-flash", "Recraft: Recraft V4.1 Flash", "\$0.007 per image"),
        MediaModelRowUi("openrouter:black-forest-labs/flux.2-klein-4b-preview", LONG_IMAGE_MODEL_NAME, "\$0.014 per megapixel"),
        MediaModelRowUi("openrouter:google/gemini-nano-banana-2.1", "Google: Nano Banana 2.1", "\$30 per 1M image tokens"),
    ),
    selectedModelKey = "openrouter:black-forest-labs/flux.2-klein-4b-preview",
    isHighQuality = true,
    shapes = listOf("1:1", "4:3", "3:4", "16:9", "9:16"),
    selectedShape = "16:9",
)

internal val videoSettingsSample = MediaSettingsUi(
    models = listOf(
        MediaModelRowUi("openrouter:x-ai/grok-imagine-video-1.5-lite", "SpaceXAI: $VIDEO_MODEL_NAME", "\$0.02 to \$0.14 per second"),
        MediaModelRowUi("openrouter:minimax/hailuo-3-max", "MiniMax: H3 Max", "\$0.05 to \$0.08 per second"),
    ),
    selectedModelKey = "openrouter:x-ai/grok-imagine-video-1.5-lite",
    lengthsSeconds = listOf(4, 6, 10, 15),
    selectedLengthSeconds = 4,
    sizes = listOf("480p", "720p", "1080p"),
    selectedSize = "720p",
)

internal val pictureSelected = noneSelected.copy(
    selected = MediaKind.PICTURE,
    modelName = LONG_IMAGE_MODEL_NAME,
    priceText = "\$0.014 per megapixel",
    settings = pictureSettingsSample,
)

internal val videoSelected = noneSelected.copy(
    selected = MediaKind.VIDEO,
    modelName = VIDEO_MODEL_NAME,
    videoLengthText = "4 s",
    videoResolution = "720p",
    videoCostText = "about \$0.12",
    settings = videoSettingsSample,
)

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

@Preview(name = "Media off, light", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun MediaOffPreview() {
    JonakiTheme(ThemeMode.LIGHT) { MediaModePreviewScreen(noneSelected) }
}

@Preview(name = "Picture on, 40-character model, light", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun PictureOnPreview() {
    JonakiTheme(ThemeMode.LIGHT) { MediaModePreviewScreen(pictureSelected) }
}

@Preview(name = "Video on, dark", widthDp = 360, heightDp = 640, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoOnDarkPreview() {
    JonakiTheme(ThemeMode.DARK) { MediaModePreviewScreen(videoSelected) }
}

@Preview(name = "Video on, Bangla", locale = "bn", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun VideoOnBanglaPreview() {
    JonakiTheme(ThemeMode.LIGHT) { MediaModePreviewScreen(videoSelected) }
}
