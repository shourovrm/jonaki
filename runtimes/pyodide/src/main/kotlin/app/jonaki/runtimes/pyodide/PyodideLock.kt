package app.jonaki.runtimes.pyodide

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** The packages a Pyodide release offers, read from its pyodide-lock.json. */
class PyodideLock private constructor(private val packages: Map<String, LockPackage>) {

    val all: Collection<LockPackage> get() = packages.values

    /** Package names compare as pip compares them: case, "_" and "." do not matter. */
    fun find(name: String): LockPackage? = packages[normalName(name)]

    /** The packages and everything they depend on, each once, in breadth-first order. */
    fun withDependencies(names: List<String>): List<LockPackage> {
        val ordered = linkedMapOf<String, LockPackage>()
        val waiting = ArrayDeque(names)
        while (waiting.isNotEmpty()) {
            val lockPackage = find(waiting.removeFirst()) ?: continue
            if (ordered.containsKey(lockPackage.name)) continue
            ordered[lockPackage.name] = lockPackage
            waiting.addAll(lockPackage.depends)
        }
        return ordered.values.toList()
    }

    /** "dateutil" to "python-dateutil": what a program imports, to the package that provides it. */
    fun packageForImport(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (lockPackage in packages.values) {
            for (importName in lockPackage.imports) {
                map.putIfAbsent(importName, lockPackage.name)
            }
        }
        return map
    }

    companion object {
        fun parse(text: String): PyodideLock {
            val packages = Json.parseToJsonElement(text).jsonObject["packages"] as? JsonObject
                ?: error("pyodide-lock.json has no packages")
            val parsed = packages.values.map { entry -> lockPackageFrom(entry.jsonObject) }
            return PyodideLock(parsed.associateBy { lockPackage -> normalName(lockPackage.name) })
        }

        private fun lockPackageFrom(entry: JsonObject): LockPackage = LockPackage(
            name = entry.text("name"),
            fileName = entry.text("file_name"),
            sha256 = entry.text("sha256"),
            imports = entry.texts("imports"),
            depends = entry.texts("depends"),
        )

        private fun JsonObject.text(key: String): String =
            (this[key] as? JsonPrimitive)?.content ?: error("a pyodide-lock.json entry has no $key")

        private fun JsonObject.texts(key: String): List<String> =
            (this[key] as? JsonArray)?.mapNotNull { element -> (element as? JsonPrimitive)?.content } ?: emptyList()

        /** PEP 503 normalisation. */
        private fun normalName(name: String): String = name.trim().lowercase().replace(Regex("[-_.]+"), "-")
    }
}

data class LockPackage(
    val name: String,
    val fileName: String,
    val sha256: String,
    val imports: List<String>,
    val depends: List<String>,
)
