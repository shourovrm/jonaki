package app.jonaki.core.toolapi

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads tool arguments. Models sometimes send numbers and booleans as strings
 * ("200", "true"), so both forms are accepted.
 */
fun JsonObject.stringArgument(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (!primitive.isString && primitive.content == "null") {
        return null
    }
    return primitive.content
}

fun JsonObject.intArgument(name: String): Int? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.content.trim().toIntOrNull()
}

fun JsonObject.booleanArgument(name: String): Boolean? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    return primitive.booleanOrNull ?: primitive.content.trim().lowercase().toBooleanStrictOrNull()
}
