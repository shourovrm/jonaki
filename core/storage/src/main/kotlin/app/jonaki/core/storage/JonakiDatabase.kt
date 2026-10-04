package app.jonaki.core.storage

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        ThreadEntity::class,
        MessageEntity::class,
        StepEntity::class,
        MemoryEntity::class,
        CompactionEntity::class,
        SubagentEntity::class,
        PersonaEntity::class,
        ProjectEntity::class,
    ],
    version = 11,
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
        // Version 5 adds the nullable reasoningText column (D-054).
        AutoMigration(from = 4, to = 5),
        // Version 6 adds the nullable threads.thinkingLevel column (D-057).
        AutoMigration(from = 5, to = 6),
        // Version 7 adds the subagents table, the nullable steps.subagentId
        // and threads.approvalMode columns (M7, D-058).
        AutoMigration(from = 6, to = 7),
        // Version 8 adds the personas and projects tables and, on threads, the
        // nullable answerStyle, personaId and projectId, instructions (empty by
        // default) and incognito (0 by default) (D-107 to D-111).
        AutoMigration(from = 7, to = 8),
        // Version 9 adds four nullable request-log time columns to messages (D-132).
        AutoMigration(from = 8, to = 9),
        // Version 10 adds the nullable memories.projectId and its index (D-135).
        AutoMigration(from = 9, to = 10),
        // Version 11 adds threads.allowAllInThread and threads.readOutsideContent (both 0 by
        // default); the spec marks the threads whose history already holds outside content.
        AutoMigration(from = 10, to = 11, spec = JonakiDatabase.MarkThreadsThatReadOutsideContent::class),
    ],
)
abstract class JonakiDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao

    abstract fun messageDao(): MessageDao

    abstract fun stepDao(): StepDao

    abstract fun memoryDao(): MemoryDao

    abstract fun compactionDao(): CompactionDao

    abstract fun subagentDao(): SubagentDao

    abstract fun personaDao(): PersonaDao

    abstract fun projectDao(): ProjectDao

    /** Room cannot describe an FTS5 table, so the 2 to 3 migration creates it after Room's own steps. */
    class AddMemorySearchIndex : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
            MemorySearchIndex.create(connection)
        }
    }

    /**
     * Threads that ran a tool returning outside content before version 11 get
     * the flag, so that the rule about sending data out applies to them too.
     * It reads the saved steps: the tools whose results are outside content,
     * an MCP call, and an image of the inbox.
     */
    class MarkThreadsThatReadOutsideContent : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
            connection.execSQL(
                "UPDATE threads SET readOutsideContent = 1 WHERE id IN (" +
                    "SELECT DISTINCT threadId FROM steps WHERE " +
                    "toolName IN ('web_search', 'web_fetch', 'youtube_summarize', 'read_document', 'delegate') " +
                    "OR (toolName = 'mcp' AND argumentsJson LIKE '%\"call\"%') " +
                    "OR (toolName = 'view_image' AND argumentsJson LIKE '%inbox/%'))",
            )
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
