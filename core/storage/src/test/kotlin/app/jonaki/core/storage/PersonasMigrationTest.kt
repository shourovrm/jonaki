package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Runs Room's generated 7 to 8 migration: the personas table and the
 * thread's answer style, persona and instructions (D-107 to D-109).
 */
class PersonasMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun openVersion7WithData(): SQLiteConnection {
        val connection = databases.openVersion(7)
        MemorySearchIndex.create(connection)
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "modelKey, memoryExtractedUpToPosition, disabledSkills, thinkingLevel) " +
                "VALUES ('t1', 'Trip', 1, 2, 1, '', 'openrouter:x', 0, 'report', 'HIGH')",
        )
        connection.execSQL(
            "INSERT INTO messages (id, threadId, position, role, text, toolCallsJson, toolCallId, isComplete, createdAtMillis) " +
                "VALUES ('m1', 't1', 0, 'USER', 'hello', '[]', NULL, 1, 1)",
        )
        return connection
    }

    @Test
    fun threadsKeepTheirDataAndStartWithNoStylePersonaOrInstructions() {
        val connection = openVersion7WithData()

        JonakiDatabase_AutoMigration_7_8_Impl().migrate(connection)

        assertEquals(
            listOf("t1|HIGH|report|null|null|"),
            databases.queryStrings(connection, "SELECT id, thinkingLevel, disabledSkills, answerStyle, personaId, instructions FROM threads"),
        )
        assertEquals(listOf("m1|hello"), databases.queryStrings(connection, "SELECT id, text FROM messages"))
    }

    @Test
    fun personasCanBeSavedAfterTheUpgrade() {
        val connection = openVersion7WithData()
        JonakiDatabase_AutoMigration_7_8_Impl().migrate(connection)

        connection.execSQL("INSERT INTO personas (id, name, instructions, createdAtMillis) VALUES ('p1', 'Tutor', 'Be patient.', 5)")
        connection.execSQL("UPDATE threads SET personaId = 'p1', answerStyle = 'CONCISE' WHERE id = 't1'")

        assertEquals(listOf("p1|Tutor|Be patient."), databases.queryStrings(connection, "SELECT id, name, instructions FROM personas"))
        assertEquals(listOf("p1|CONCISE"), databases.queryStrings(connection, "SELECT personaId, answerStyle FROM threads"))
    }

    @Test
    fun upgradedDatabaseHasTheSameTablesAsAFreshOne() {
        val upgraded = openVersion7WithData()
        JonakiDatabase_AutoMigration_7_8_Impl().migrate(upgraded)
        val fresh = databases.openVersion(8)
        MemorySearchIndex.create(fresh)

        assertEquals(databases.describeTables(fresh), databases.describeTables(upgraded))
    }
}
