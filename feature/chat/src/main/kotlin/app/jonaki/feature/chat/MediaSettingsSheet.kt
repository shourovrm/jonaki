package app.jonaki.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.ThemeMode

/**
 * Everything the next send of one kind of media uses (D-174, option B): the
 * model, then the settings the kind has (quality and shape for a picture,
 * shape for a vector image, length and size for a video), and the price of
 * the send at the bottom. Each change applies at once; the sheet stays open
 * so that several can be made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaSettingsSheet(
    kind: MediaKind,
    settings: MediaSettingsUi,
    /** The price of the next send as the details line shows it; null when it is not known. */
    priceText: String?,
    onChange: (MediaSettingChange) -> Unit,
    onAddModel: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        MediaSettingsContent(kind, settings, priceText, onChange, onAddModel)
    }
}

@Composable
internal fun MediaSettingsContent(
    kind: MediaKind,
    settings: MediaSettingsUi,
    priceText: String?,
    onChange: (MediaSettingChange) -> Unit,
    onAddModel: () -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        SheetTitle(stringResource(kind.labelRes))
        for (model in settings.models) {
            ChoiceRow(
                name = model.name,
                serviceName = model.priceText ?: stringResource(R.string.chat_media_price_unknown),
                isSelected = model.key == settings.selectedModelKey,
                onClick = { onChange(MediaSettingChange.Model(model.key)) },
                details = {},
            )
        }
        TextButton(onClick = onAddModel) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.chat_media_add_model), modifier = Modifier.padding(start = 8.dp))
        }
        settings.isHighQuality?.let { isHigh ->
            SettingChoices(
                title = stringResource(R.string.chat_media_quality),
                labels = listOf(stringResource(R.string.chat_media_quality_standard), stringResource(R.string.chat_media_quality_high)),
                selectedIndex = if (isHigh) 1 else 0,
                onSelect = { index -> onChange(MediaSettingChange.Quality(isHigh = index == 1)) },
            )
        }
        if (settings.shapes.isNotEmpty()) {
            // The first choice sends no shape, so the model uses its own.
            val shapes: List<String?> = listOf<String?>(null) + settings.shapes
            SettingChoices(
                title = stringResource(R.string.chat_media_shape),
                labels = shapes.map { shape -> shape ?: stringResource(R.string.chat_media_shape_auto) },
                selectedIndex = shapes.indexOf(settings.selectedShape).coerceAtLeast(0),
                onSelect = { index -> onChange(MediaSettingChange.Shape(shapes[index])) },
            )
        }
        if (settings.lengthsSeconds.isNotEmpty()) {
            SettingChoices(
                title = stringResource(R.string.chat_media_length),
                labels = settings.lengthsSeconds.map { seconds -> stringResource(R.string.chat_media_seconds, seconds) },
                selectedIndex = settings.lengthsSeconds.indexOf(settings.selectedLengthSeconds),
                onSelect = { index -> onChange(MediaSettingChange.Length(settings.lengthsSeconds[index])) },
            )
        }
        if (settings.sizes.isNotEmpty()) {
            SettingChoices(
                title = stringResource(R.string.chat_media_size),
                labels = settings.sizes,
                selectedIndex = settings.sizes.indexOf(settings.selectedSize),
                onSelect = { index -> onChange(MediaSettingChange.Size(settings.sizes[index])) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp)) {
            Text(
                stringResource(kind.nextSendRes),
                style = MaterialTheme.typography.bodyMedium,
                color = JonakiTheme.colors.inkSoft,
                modifier = Modifier.weight(1f),
            )
            Text(
                priceText ?: stringResource(R.string.chat_media_price_unknown),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFamily),
                maxLines = 1,
            )
        }
    }
}

/**
 * One setting as a row of choices under its title. More than five choices
 * would be cut at 360 dp, so the callers keep their lists that short; a
 * selected index of -1 (a value the list does not hold) selects nothing.
 */
@Composable
private fun SettingChoices(title: String, labels: List<String>, selectedIndex: Int, onSelect: (index: Int) -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                icon = {},
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false) },
            )
        }
    }
}

@Preview(name = "Picture settings, light", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun PictureSettingsPreview() {
    JonakiTheme(ThemeMode.LIGHT) {
        Surface { MediaSettingsContent(MediaKind.PICTURE, pictureSettingsSample, "\$0.014 per megapixel", onChange = {}, onAddModel = {}) }
    }
}

@Preview(name = "Video settings, dark", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun VideoSettingsDarkPreview() {
    JonakiTheme(ThemeMode.DARK) {
        Surface { MediaSettingsContent(MediaKind.VIDEO, videoSettingsSample, "about \$0.12", onChange = {}, onAddModel = {}) }
    }
}

@Preview(name = "Video settings, Bangla", locale = "bn", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun VideoSettingsBanglaPreview() {
    JonakiTheme(ThemeMode.LIGHT) {
        Surface { MediaSettingsContent(MediaKind.VIDEO, videoSettingsSample, "about \$0.12", onChange = {}, onAddModel = {}) }
    }
}
