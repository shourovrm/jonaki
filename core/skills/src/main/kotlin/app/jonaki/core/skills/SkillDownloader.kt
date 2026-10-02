package app.jonaki.core.skills

import app.jonaki.core.toolapi.await
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Fetches a skill's files from a link (D-041): a GitHub folder through the
 * GitHub contents API, which lists a folder and gives each file's raw
 * download link, or any other link as a single SKILL.md. No GitHub key is
 * used, so GitHub allows 60 listings an hour from one address.
 */
class SkillDownloader(
    private val httpClient: OkHttpClient,
    private val gitHubApiBaseUrl: String = "https://api.github.com",
) {
    suspend fun download(source: SkillSource): SkillFilesResult = try {
        when (source) {
            is SkillSource.GitHubFolder -> downloadFolder(source)
            is SkillSource.SingleFile -> downloadSingleFile(source.url)
            SkillSource.NotALink -> SkillFilesResult.Failed("this is not a link")
        }
    } catch (failure: DownloadFailure) {
        SkillFilesResult.Failed(failure.reason)
    } catch (failure: IOException) {
        SkillFilesResult.Failed("could not connect: ${failure.message ?: failure.javaClass.simpleName}")
    }

    private suspend fun downloadSingleFile(url: String): SkillFilesResult {
        val content = fetchBytes(url, budgetBytes = MAX_TOTAL_BYTES)
        return SkillFilesResult.Files(mapOf(SkillLibrary.SKILL_FILE to content))
    }

    private suspend fun downloadFolder(source: SkillSource.GitHubFolder): SkillFilesResult {
        // List the whole tree first, so a folder that is too big costs no file downloads.
        val listed = mutableListOf<ListedFile>()
        listFolder(source, source.path, depth = 0, into = listed)
        val files = mutableMapOf<String, ByteArray>()
        var usedBytes = 0
        for (file in listed) {
            val content = fetchBytes(file.downloadUrl, budgetBytes = MAX_TOTAL_BYTES - usedBytes)
            usedBytes += content.size
            files[file.relativePath] = content
        }
        return SkillFilesResult.Files(files)
    }

    private suspend fun listFolder(source: SkillSource.GitHubFolder, path: String, depth: Int, into: MutableList<ListedFile>) {
        if (depth > MAX_FOLDER_DEPTH) {
            throw DownloadFailure("the folder is nested more than $MAX_FOLDER_DEPTH levels deep")
        }
        val listingText = fetchText(contentsUrl(source, path))
        val listing = try {
            Json.parseToJsonElement(listingText)
        } catch (unreadable: SerializationException) {
            throw DownloadFailure("GitHub sent a folder list that could not be read")
        }
        val entries = listing as? JsonArray ?: throw DownloadFailure("the link is a file, not a folder")
        for (entry in entries) {
            val fields = entry as? JsonObject ?: continue
            val type = fields.text("type")
            val entryPath = fields.text("path") ?: continue
            when (type) {
                "file" -> {
                    val downloadUrl = fields.text("download_url") ?: continue
                    into += ListedFile(relativePath = entryPath.removePrefix(source.path).trimStart('/'), downloadUrl = downloadUrl)
                    if (into.size > MAX_FILES) {
                        throw DownloadFailure("the folder has more than $MAX_FILES files")
                    }
                }
                "dir" -> listFolder(source, entryPath, depth + 1, into)
                // Symbolic links and submodules point outside the folder; a skill does not need them.
                else -> continue
            }
        }
    }

    private fun contentsUrl(source: SkillSource.GitHubFolder, path: String): String {
        val builder = gitHubApiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegments("repos/${source.owner}/${source.repository}/contents/")
        if (path.isNotEmpty()) {
            builder.addPathSegments(path)
        }
        if (source.ref != null) {
            builder.addQueryParameter("ref", source.ref)
        }
        return builder.build().toString()
    }

    private suspend fun fetchText(url: String): String = fetchBytes(url, MAX_TOTAL_BYTES).decodeToString()

    private suspend fun fetchBytes(url: String, budgetBytes: Int): ByteArray {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw DownloadFailure(reasonFor(response))
            }
            val body = response.body ?: throw DownloadFailure("the server sent nothing")
            val source = body.source()
            // Asks for one byte more than allowed: if it arrives, the file is too big.
            source.request(budgetBytes.toLong() + 1)
            if (source.buffer.size > budgetBytes) {
                throw DownloadFailure("the skill is larger than ${MAX_TOTAL_BYTES / (1024 * 1024)} MB")
            }
            return source.buffer.readByteArray()
        }
    }

    private fun reasonFor(response: Response): String {
        val isGitHub = response.request.url.toString().startsWith(gitHubApiBaseUrl)
        return when {
            response.code == 403 && response.header("X-RateLimit-Remaining") == "0" ->
                "GitHub's hourly download limit is used up; try again later"
            response.code == 404 && isGitHub -> "not found on GitHub (404)"
            response.code == 404 -> "not found (404)"
            else -> "the server answered ${response.code}"
        }
    }

    private fun JsonObject.text(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.contentOrNull
    }

    private data class ListedFile(val relativePath: String, val downloadUrl: String)

    private class DownloadFailure(val reason: String) : Exception(reason)

    companion object {
        const val MAX_FILES = 100
        const val MAX_TOTAL_BYTES = 2 * 1024 * 1024
        const val MAX_FOLDER_DEPTH = 5
    }
}
