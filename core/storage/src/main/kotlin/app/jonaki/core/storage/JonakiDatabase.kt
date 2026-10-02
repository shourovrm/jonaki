package app.jonaki.core.storage

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        ThreadEntity::class,
        MessageEntity::class,
        StepEntity::class,
        MemoryEntity::class,
        CompactionEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // Version 2 only adds nullable columns (D-027 usage and the thread's model),
    // so Room generates the migration from the exported schemas in schemas/.
    // Version 3 adds the memories and compactions tables and one nullable
    // thread column; the memory search index is added by the spec (M4).
    // Version 4 adds the thread's switched-off skills, empty by default (M5).
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = JonakiDatabase.AddMemorySearchIndex::class),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class JonakiDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao

    abstract fun messageDao(): MessageDao

    abstract fun stepDao(): StepDao

    abstract fun memoryDao(): MemoryDao

    abstract fun compactionDao(): CompactionDao

    /** Room cannot describe an FTS5 table, so the 2 to 3 migration creates it after Room's own steps. */
    class AddMemorySearchIndex : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
            MemorySearchIndex.create(connection)
        }
    }

    /** A fresh install gets the same FTS5 table that the migration adds to an upgraded one. */
    private class CreateMemorySearchIndex : Callback() {
        override fun onCreate(connection: SQLiteConnection) {
            MemorySearchIndex.create(connection)
        }
    }

    companion object {
        /**
         * The bundled driver ships its own SQLite 3.46 with FTS5 trigram search,
         * which Android 8's built-in SQLite lacks (D-023, spike S-1).
         */
        fun open(context: Context): JonakiDatabase =
            Room.databaseBuilder<JonakiDatabase>(context.applicationContext, "jonaki.db")
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addCallback(CreateMemorySearchIndex())
                .build()
    }
}
