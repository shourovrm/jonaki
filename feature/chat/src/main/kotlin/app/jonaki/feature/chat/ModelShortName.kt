package app.jonaki.feature.chat

internal object ModelShortName {
    private const val MAKER_SEPARATOR = ": "

    /**
     * OpenRouter names models "Maker: Model"; the top bar shows the part after the maker
     * because the line under the title is short. A name with nothing after the separator is kept.
     */
    fun of(modelName: String): String {
        val shortName = modelName.substringAfter(MAKER_SEPARATOR, missingDelimiterValue = modelName)
        return shortName.ifBlank { modelName }
    }
}
