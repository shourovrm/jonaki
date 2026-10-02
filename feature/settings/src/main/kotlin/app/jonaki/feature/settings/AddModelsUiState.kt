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
)

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
