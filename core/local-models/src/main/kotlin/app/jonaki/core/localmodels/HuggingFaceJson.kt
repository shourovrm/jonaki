package app.jonaki.core.localmodels

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Reads the two Hugging Face responses the Local models page uses. Entries
 * without the fields a row needs are skipped instead of failing the list.
 */
object HuggingFaceJson {
    /** `GET /api/models?search=…&expand[]=gguf&expand[]=gated&expand[]=downloads&expand[]=cardData`. */
    fun parseSearch(json: String): List<HubRepo> {
        val models = Json.parseToJsonElement(json) as? JsonArray ?: return emptyList()
        return models.mapNotNull { element -> repoFrom(element as? JsonObject) }
    }

    /** `GET /api/models/<repo>/tree/main`: files only, folders are left out. */
    fun parseTree(json: String): List<HubFile> {
        val entries = Json.parseToJsonElement(json) as? JsonArray ?: return emptyList()
        return entries.mapNotNull { element -> fileFrom(element as? JsonObject) }
    }

    private fun repoFrom(model: JsonObject?): HubRepo? {
        if (model == null) return null
        val id = model.text("id") ?: return null
        val gguf = model["gguf"] as? JsonObject
        val cardData = model["cardData"] as? JsonObject
        return HubRepo(
            id = id,
            downloads = model.number("downloads") ?: 0,
            isGated = isGated(model["gated"]),
            architecture = gguf?.text("architecture"),
            totalParameters = gguf?.number("total"),
            contextLength = gguf?.number("context_length"),
            license = cardData?.text("license"),
        )
    }

    /** `false` when open; otherwise the text "auto" or "manual", both of which need the user's consent on the website. */
    private fun isGated(gated: JsonElement?): Boolean {
        val primitive = gated as? JsonPrimitive ?: return false
        val flag = primitive.booleanOrNull
        if (flag != null) {
            return flag
        }
        return primitive.contentOrNull != null
    }

    private fun fileFrom(entry: JsonObject?): HubFile? {
        if (entry == null || entry.text("type") != "file") return null
        val path = entry.text("path") ?: return null
        val lfs = entry["lfs"] as? JsonObject
        return HubFile(
            path = path,
            sizeBytes = entry.number("size") ?: 0,
            sha256 = lfs?.text("oid")?.lowercase(),
        )
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
