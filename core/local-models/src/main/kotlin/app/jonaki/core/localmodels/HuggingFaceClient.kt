package app.jonaki.core.localmodels

import app.jonaki.core.toolapi.await
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** A Hugging Face request that did not give a usable answer; [message] is short and for the log. */
class HubRequestFailure(message: String) : IOException(message)

/**
 * The public Hugging Face API without a key (D-133): the GGUF model search
 * and one repository's file list. [baseUrl] is replaced in tests.
 */
class HuggingFaceClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    /** Up to [SEARCH_LIMIT] repositories with GGUF files matching [query], most downloaded first. */
    suspend fun search(query: String): List<HubRepo> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/models")
            .addQueryParameter("search", query.trim())
            .addQueryParameter("filter", "gguf")
            .addQueryParameter("sort", "downloads")
            .addQueryParameter("limit", SEARCH_LIMIT.toString())
            .addExpand("gguf")
            .addExpand("gated")
            .addExpand("downloads")
            .addExpand("cardData")
            .build()
        return HuggingFaceJson.parseSearch(fetch(url))
    }

    /** The files at the top of the repository's main branch, with sizes and SHA-256 hashes. */
    suspend fun files(repoId: String): List<HubFile> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/models")
            .addPathSegments(repoId)
            .addPathSegments("tree/main")
            .build()
        return HuggingFaceJson.parseTree(fetch(url))
    }

    private suspend fun fetch(url: HttpUrl): String {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw HubRequestFailure("Hugging Face answered ${response.code}")
            }
            return response.body?.string() ?: throw HubRequestFailure("Hugging Face sent nothing")
        }
    }

    // Hugging Face reads the literal "expand[]" key; HttpUrl encodes the brackets, which it accepts.
    private fun HttpUrl.Builder.addExpand(field: String): HttpUrl.Builder = addQueryParameter("expand[]", field)

    companion object {
        const val DEFAULT_BASE_URL = "https://huggingface.co"
        const val SEARCH_LIMIT = 30

        /**
         * The download address of one file at one commit. It answers with a
         * redirect to a signed CDN address that expires, so a resumed
         * download starts from here again rather than from the old redirect.
         */
        fun downloadUrl(repoId: String, revision: String, path: String, baseUrl: String = DEFAULT_BASE_URL): String =
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegments(repoId)
                .addPathSegment("resolve")
                .addPathSegment(revision)
                .addPathSegments(path)
                .build()
                .toString()
    }
}
