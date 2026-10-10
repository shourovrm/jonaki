package app.jonaki.core.modelcatalog

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** What a provider says it does with the prompts it receives. */
enum class ProviderPrivacy {
    /** Does not train on prompts and does not keep them. */
    PRIVATE,

    /** Does not train on prompts but keeps them. */
    KEEPS_PROMPTS,

    /** May train on prompts. */
    MAY_TRAIN,
}

/** The two flags of a provider's data policy that decide its [ProviderPrivacy]. */
data class ProviderDataPolicy(val training: Boolean, val retainsPrompts: Boolean) {
    fun privacy(): ProviderPrivacy = when {
        training -> ProviderPrivacy.MAY_TRAIN
        retainsPrompts -> ProviderPrivacy.KEEPS_PROMPTS
        else -> ProviderPrivacy.PRIVATE
    }
}

/**
 * The data policy of every OpenRouter provider, downloaded at most once per process.
 *
 * The list comes from OpenRouter's website, not from a documented API: the endpoints list and the
 * official GET /api/v1/providers carry no data policy. Checked on 2026-10-10 (no key needed); it
 * may change or disappear. A failure only removes the marks from the Providers sheet.
 */
class OpenRouterProviderPolicies(
    private val httpClient: OkHttpClient,
    private val url: String = DEFAULT_URL,
) {
    // Only a successful download is kept, so a failed one is tried again the next time the sheet opens.
    @Volatile
    private var cached: Map<String, ProviderDataPolicy>? = null

    /** The policies by provider slug. Empty when the download or the reading failed. */
    suspend fun load(): Map<String, ProviderDataPolicy> {
        cached?.let { return it }
        val downloaded = try {
            fetch()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            return emptyMap()
        }
        if (downloaded.isNotEmpty()) {
            cached = downloaded
        }
        return downloaded
    }

    /** Downloads and parses the list. Throws [IOException] on a network failure or a non-success answer. */
    suspend fun fetch(): Map<String, ProviderDataPolicy> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("OpenRouter answered HTTP ${response.code}")
            }
            parse(response.body?.string().orEmpty())
        }
    }

    companion object {
        const val DEFAULT_URL = "https://openrouter.ai/api/frontend/v1/all-providers"

        /**
         * Policies by slug. An entry without a readable dataPolicy (both flags as booleans) is left
         * out, so that its provider counts as unknown. Text that is not the expected JSON gives an empty map.
         */
        fun parse(json: String): Map<String, ProviderDataPolicy> {
            val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return emptyMap()
            val entries = root["data"] as? JsonArray ?: return emptyMap()
            val policies = LinkedHashMap<String, ProviderDataPolicy>()
            for (element in entries) {
                val entry = element as? JsonObject ?: continue
                val slug = (entry["slug"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
                val dataPolicy = entry["dataPolicy"] as? JsonObject ?: continue
                val training = (dataPolicy["training"] as? JsonPrimitive)?.booleanOrNull ?: continue
                val retainsPrompts = (dataPolicy["retainsPrompts"] as? JsonPrimitive)?.booleanOrNull ?: continue
                policies[slug] = ProviderDataPolicy(training, retainsPrompts)
            }
            return policies
        }

        /**
         * The privacy of an endpoint's provider, matched by the part of [tag] before the first "/"
         * ("deepinfra/fp4" is "deepinfra"). Null when the provider is not in [policies].
         */
        fun privacyOfTag(policies: Map<String, ProviderDataPolicy>, tag: String): ProviderPrivacy? =
            policies[tag.substringBefore('/')]?.privacy()
    }
}
