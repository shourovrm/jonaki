package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Settings > Models > Vector image generation: the SVG models of
 * generate_vector_image, in one card per service and then "Add service",
 * like Image generation (D-182, D-183). A card shows the service's key, which
 * is the same key as under Image generation; the star marks the model used
 * when a call names none.
 */
@Composable
internal fun VectorImageGenerationSection(images: ImageGenerationUi, actions: SettingsActions) {
    SectionLabel(stringResource(R.string.settings_section_vector_images))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (card in images.vectorServices) {
            ImageServiceCard(
                card,
                stateKey = "vector:${card.serviceKey}",
                actions = actions,
                onAddModels = { actions.onAddVectorModels(card.serviceKey) },
                onRemoveService = { actions.onVectorServiceRemove(card.serviceKey) },
                onMakeDefault = actions.onVectorModelSetDefault,
            )
        }
        if (images.addableVectorServices.isNotEmpty()) {
            AddServiceDropdown(images.addableVectorServices, actions.onAddVectorService)
        }
    }
}
