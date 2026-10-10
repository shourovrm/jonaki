package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Settings > Models > Vector image generation: the SVG models of
 * generate_vector_image in a section of their own, like the video models
 * (D-182). They run on an image service's key, so the service itself stays
 * under Image generation; the star marks the model used when a call names none.
 */
@Composable
internal fun VectorImageGenerationSection(images: ImageGenerationUi, actions: SettingsActions) {
    SectionLabel(stringResource(R.string.settings_section_vector_images))
    Group {
        if (!images.canAddVectorModels) {
            Text(
                stringResource(R.string.settings_vector_images_needs_service),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            return@Group
        }
        for (model in images.vectorModels) {
            ImageModelRow(
                model,
                onMakeDefault = { actions.onVectorModelSetDefault(model.key) },
                onRemove = { actions.onImageModelRemove(model.key) },
            )
        }
        TextButton(onClick = actions.onAddVectorModels, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_add_model))
        }
    }
}
