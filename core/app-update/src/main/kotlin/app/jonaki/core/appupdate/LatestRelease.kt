package app.jonaki.core.appupdate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The newest GitHub release: its version without the "v" and the APK to download. */
data class LatestRelease(
    val versionName: String,
    val apkName: String,
    val apkUrl: String,
)

/**
 * Reads the JSON of GitHub's "latest release" call. Returns null when the text is
 * not a release or has no asset whose name ends in ".apk".
 */
fun parseLatestRelease(jsonText: String): LatestRelease? {
    val release = try {
        Json.parseToJsonElement(jsonText).jsonObject
    } catch (notJson: IllegalArgumentException) {
        return null
    }
    val tagName = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
    val assets = release["assets"]?.jsonArray ?: return null
    for (asset in assets) {
        val apk = apkFrom(asset.jsonObject)
        if (apk != null) {
            return LatestRelease(tagName.removePrefix("v").removePrefix("V"), apk.first, apk.second)
        }
    }
    return null
}

private fun apkFrom(asset: JsonObject): Pair<String, String>? {
    val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return null
    val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null
    if (!name.endsWith(".apk", ignoreCase = true)) {
        return null
    }
    return name to url
}
