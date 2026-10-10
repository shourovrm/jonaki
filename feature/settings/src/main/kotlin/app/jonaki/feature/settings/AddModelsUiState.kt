package app.jonaki.feature.settings

import androidx.compose.runtime.Immutable

/** The model list of one service, for [AddModelsScreen]. */
@Immutable
data class AddModelsUiState(
    val serviceDisplayName: String,
    val models: List<AddableModelUi>,
)

@Immutable
data class AddableModelUi(
    /** The id the service expects, for example "deepseek/deepseek-v4". */
    val id: String,
    val name: String,
    val contextWindowTokens: Int? = null,
    val inputPricePerMillion: Double? = null,
    val outputPricePerMillion: Double? = null,
    val cachedInputPricePerMillion: Double? = null,
    /** Already in the user's list: shown ticked and not changeable here. */
    val isAdded: Boolean = false,
    /** Image model picker only: the price line under the id; null for a service that shows no prices. */
    val imagePrice: ImagePriceUi? = null,
    /** Image model picker only: the list says the model makes SVG files; the row carries an "SVG" tag. */
    val isVector: Boolean = false,
)

/** The price line of an image model row in the picker. */
sealed interface ImagePriceUi {
    /** Not loaded yet: the line keeps its height and shows nothing. */
    data object Loading : ImagePriceUi

    /** For example "$0.014 per megapixel". */
    data class Known(val text: String) : ImagePriceUi

    /** The request failed or the service lists no price: the line shows a dash. */
    data object Unknown : ImagePriceUi
}

/** Models whose name or id contains every word of [query], ignoring case. */
fun filterModels(models: List<AddableModelUi>, query: String): List<AddableModelUi> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) {
        return models
    }
    return models.filter { model ->
        val searchable = "${model.name} ${model.id}".lowercase()
        words.all { word -> word in searchable }
    }
}

/**
 * The query as a model id the user can add by hand, for a model the list
 * does not have; null when the query is blank or is already a listed id.
 */
fun freeTextModelId(models: List<AddableModelUi>, query: String): String? {
    val id = query.trim()
    if (id.isEmpty()) {
        return null
    }
    if (models.any { model -> model.id == id }) {
        return null
    }
    return id
}
