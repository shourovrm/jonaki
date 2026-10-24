package app.jonaki.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.ZoneId

/**
 * How many calls went to Jonaki Free today from this phone, for the Settings
 * card and the "Today: 12 of 60" line (D-186). The Worker enforces the limit;
 * this count only tells the user where they stand and resets at local midnight.
 */
class FreeTierUsage(
    private val store: Store,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: String = ZoneId.systemDefault().id,
) {
    /** Where the day and count live between launches. */
    interface Store {
        /** The day the count belongs to ("2026-10-25"), or null before the first call, with the count. */
        fun read(): Pair<String?, Int>

        fun write(day: String, count: Int)

        /** The limit the Worker last reported and the local day it was fetched, or nulls before the first fetch. */
        fun readLimit(): Pair<String?, Int?>

        fun writeLimit(day: String, limit: Int)
    }

    @Synchronized
    fun todayCount(): Int {
        val (day, count) = store.read()
        return if (day == today()) count else 0
    }

    /** Adds one call for today. */
    @Synchronized
    fun record() {
        store.write(today(), todayCount() + 1)
    }

    /** The limit the Worker reported, or null until the first successful fetch. */
    @Synchronized
    fun serverLimit(): Int? = store.readLimit().second

    /** The server's limit when known, else the built-in [DAILY_CALL_LIMIT]. */
    fun effectiveLimit(): Int = serverLimit() ?: DAILY_CALL_LIMIT

    /**
     * Asks the Worker for the limit and today's count, at most once per local
     * day. [baseUrl] is the Worker's /v1 address. A failure of any kind leaves
     * the stored values as they were.
     */
    suspend fun refreshLimit(httpClient: OkHttpClient, baseUrl: String, deviceId: String) {
        if (store.readLimit().first == today()) {
            return
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/limit")
            .header("X-Jonaki-Device", deviceId)
            .build()
        val reported = withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use null
                    }
                    val fields = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                    fields.getValue("limit").jsonPrimitive.int to fields.getValue("used").jsonPrimitive.int
                }
            } catch (failure: java.io.IOException) {
                null
            } catch (failure: kotlinx.serialization.SerializationException) {
                null
            } catch (failure: NoSuchElementException) {
                null
            } catch (failure: IllegalArgumentException) {
                null
            }
        } ?: return
        synchronized(this) {
            store.writeLimit(today(), reported.first)
            store.write(today(), reported.second)
        }
    }

    private fun today(): String = Instant.ofEpochMilli(clock()).atZone(ZoneId.of(zoneId)).toLocalDate().toString()

    companion object {
        /** Calls a phone may make in one day; the Worker in server/jonaki-free holds the same number. */
        const val DAILY_CALL_LIMIT = 60

        /** A store in the app's shared preferences. */
        fun inPreferences(context: Context): Store {
            val preferences = context.getSharedPreferences("jonaki-free", Context.MODE_PRIVATE)
            return object : Store {
                override fun read(): Pair<String?, Int> =
                    preferences.getString("day", null) to preferences.getInt("count", 0)

                override fun write(day: String, count: Int) {
                    preferences.edit().putString("day", day).putInt("count", count).apply()
                }

                override fun readLimit(): Pair<String?, Int?> {
                    val limit = if (preferences.contains("limit")) preferences.getInt("limit", 0) else null
                    return preferences.getString("limitFetchedDay", null) to limit
                }

                override fun writeLimit(day: String, limit: Int) {
                    preferences.edit().putString("limitFetchedDay", day).putInt("limit", limit).apply()
                }
            }
        }
    }
}
