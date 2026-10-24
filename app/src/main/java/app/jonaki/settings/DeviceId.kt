package app.jonaki.settings

import android.content.Context
import java.util.UUID

/** A random id made once per install; Jonaki Free counts its daily calls per id. */
object DeviceId {
    private const val PREFERENCES_NAME = "device-id"
    private const val KEY = "id"

    fun get(context: Context): String {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = preferences.getString(KEY, null)
        if (stored != null) return stored
        val created = UUID.randomUUID().toString()
        preferences.edit().putString(KEY, created).apply()
        return created
    }
}
