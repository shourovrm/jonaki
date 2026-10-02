package app.jonaki.files

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * A list of JSON objects kept in one file in the app's storage, for small
 * records such as reminders and scheduled tasks. Each write replaces the
 * whole file through a temporary file, so a crash never leaves half a list.
 */
class JsonListFile(private val file: File) {
    /** An empty list when the file is missing or cannot be read. */
    fun read(): List<JsonObject> {
        if (!file.isFile) {
            return emptyList()
        }
        return try {
            val array = Json.parseToJsonElement(file.readText()) as? JsonArray ?: return emptyList()
            array.filterIsInstance<JsonObject>()
        } catch (unreadable: SerializationException) {
            emptyList()
        } catch (unreadable: IllegalArgumentException) {
            emptyList()
        }
    }

    fun write(items: List<JsonObject>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(JsonArray(items).toString())
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
