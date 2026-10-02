package app.jonaki.providers.gemini

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

internal fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.stringOrNull(key: String): String? {
    val element = this[key]
    if (element == null || element is JsonNull) return null
    return (element as? JsonPrimitive)?.content
}

internal fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.isTrue(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true
