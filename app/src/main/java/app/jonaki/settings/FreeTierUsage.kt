package app.jonaki.settings

import android.content.Context
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
            }
        }
    }
}
