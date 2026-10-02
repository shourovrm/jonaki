package app.jonaki.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [ThreadEntity::class, MessageEntity::class, StepEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class JonakiDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao

    abstract fun messageDao(): MessageDao

    abstract fun stepDao(): StepDao

    companion object {
        /**
         * The bundled driver ships its own SQLite 3.46 with FTS5 trigram search,
         * which Android 8's built-in SQLite lacks (D-023, spike S-1).
         */
        fun open(context: Context): JonakiDatabase =
            Room.databaseBuilder<JonakiDatabase>(context.applicationContext, "jonaki.db")
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
