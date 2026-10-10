package app.jonaki.ui

import android.content.Context

/**
 * Remembers which thread the user left last and when, in a small preferences
 * file of its own, for [StartDestination]. Leaving a new empty thread clears
 * the record, because the user's last place is then that empty thread and not
 * the one before it. An incognito thread is recorded like any other, and
 * [StartDestination] refuses to reopen it.
 */
class LeftThreadStore(context: Context) {
    private val preferences = context.getSharedPreferences("left-thread", Context.MODE_PRIVATE)

    fun read(): LeftThread? {
        val threadId = preferences.getString(THREAD_ID, null) ?: return null
        val leftAtMillis = preferences.getLong(LEFT_AT_MILLIS, -1L)
        return LeftThread(threadId, leftAtMillis)
    }

    fun record(threadId: String, nowMillis: Long) {
        preferences.edit().putString(THREAD_ID, threadId).putLong(LEFT_AT_MILLIS, nowMillis).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val THREAD_ID = "thread_id"
        const val LEFT_AT_MILLIS = "left_at_millis"
    }
}
