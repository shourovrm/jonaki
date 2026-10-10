package app.jonaki.settings

import app.jonaki.core.modelcatalog.ModelKey

/** Chat services the user can add as cards in Settings (D-028). */
enum class ChatService(
    /** Stable id used in model keys and presets, for example "openrouter". */
    val key: String,
    val displayName: String,
    /** Null for a service that needs no key (Ollama on the user's own network). */
    val secret: SecretName?,
) {
    OPENROUTER("openrouter", "OpenRouter", SecretName.OPENROUTER),
    DEEPSEEK("deepseek", "DeepSeek", SecretName.DEEPSEEK),
    GEMINI("gemini", "Gemini", SecretName.GEMINI),
    GLM("glm", "GLM (Z.ai)", SecretName.GLM),
    MIMO("mimo", "Xiaomi MiMo", SecretName.MIMO),

    // Ollama Cloud uses the same account key as Ollama web search.
    OLLAMA_CLOUD("ollama-cloud", "Ollama Cloud", SecretName.OLLAMA),
    OLLAMA_LOCAL("ollama-local", "Ollama on this network", null),
    OPENAI("openai", "OpenAI", SecretName.OPENAI),
    MINIMAX("minimax", "MiniMax", SecretName.MINIMAX),
    QWEN("qwen", "Qwen", SecretName.QWEN),

    // GGUF files on the phone (D-133); its models are the files, not a list the user adds to.
    LOCAL("local", "Local", null),
    ;

    companion object {
        fun byKey(key: String): ChatService? = entries.firstOrNull { service -> service.key == key }
    }
}

/**
 * The user's chat services and models (D-028): services in the order they
 * were added, any number of models per service, and one starred model across
 * all services that new threads use. Every change returns a new value.
 */
data class ChatModels(
    val addedServices: List<ChatService>,
    /** Model ids per service, without the service prefix. */
    val modelsByService: Map<ChatService, List<String>>,
    /** "service:modelId" of the starred model; null when no model is added. */
    val defaultModelKey: String?,
) {
    /** Every model as "service:modelId", in card order. */
    val allModelKeys: List<String>
        get() = addedServices.flatMap { service ->
            modelsByService[service].orEmpty().map { modelId -> ModelKey.of(service.key, modelId) }
        }

    fun addService(service: ChatService): ChatModels {
        if (service in addedServices) {
            return this
        }
        return copy(addedServices = addedServices + service)
    }

    fun removeService(service: ChatService): ChatModels {
        val remaining = copy(addedServices = addedServices - service, modelsByService = modelsByService - service)
        return remaining.withValidDefault()
    }

    /** Adds a model; a blank or already-listed id changes nothing. The first model added is starred. */
    fun addModel(service: ChatService, modelId: String): ChatModels {
        val trimmed = modelId.trim()
        val existing = modelsByService[service].orEmpty()
        if (trimmed.isEmpty() || trimmed in existing) {
            return addService(service)
        }
        val added = addService(service).copy(modelsByService = modelsByService + (service to existing + trimmed))
        return added.withValidDefault()
    }

    fun removeModel(modelKey: String): ChatModels {
        val service = ChatService.byKey(ModelKey.serviceOf(modelKey)) ?: return this
        val remainingIds = modelsByService[service].orEmpty() - ModelKey.modelOf(modelKey)
        return copy(modelsByService = modelsByService + (service to remainingIds)).withValidDefault()
    }

    /** Stars [modelKey]; a key that is not listed changes nothing. */
    fun setDefault(modelKey: String): ChatModels {
        if (modelKey !in allModelKeys) {
            return this
        }
        return copy(defaultModelKey = modelKey)
    }

    /** Keeps the star on a listed model: the current one if still listed, else the first, else none. */
    private fun withValidDefault(): ChatModels {
        val keys = allModelKeys
        if (defaultModelKey in keys) {
            return this
        }
        return copy(defaultModelKey = keys.firstOrNull())
    }

    companion object {
        /**
         * True when version 0.1.0 saved a service name or a model text. A fresh
         * install has neither, and must start with no service and no model.
         */
        fun hasLegacySettings(chatServiceName: String?, savedModels: Map<String, String>): Boolean {
            return !chatServiceName.isNullOrBlank() || savedModels.values.any { it.isNotBlank() }
        }

        /**
         * Settings from version 0.1.0 had one selected service and one model text
         * per service, where a blank text meant the preset's default. They become
         * one card with one starred model, so the blank-means-default rule ends here.
         */
        fun fromLegacy(
            chatServiceName: String?,
            savedModels: Map<String, String>,
            presetDefaultModel: (ChatService) -> String,
        ): ChatModels {
            val service = ChatService.entries.firstOrNull { it.name == chatServiceName } ?: ChatService.OPENROUTER
            val savedModel = savedModels[service.name].orEmpty().trim()
            val modelId = savedModel.ifEmpty { presetDefaultModel(service) }
            val empty = ChatModels(addedServices = emptyList(), modelsByService = emptyMap(), defaultModelKey = null)
            return empty.addModel(service, modelId)
        }
    }
}

/** What Settings shows of a saved key: its first three characters and a mask, never more (D-028). */
fun maskedKeyPreview(key: String): String = key.take(3) + "••••"
