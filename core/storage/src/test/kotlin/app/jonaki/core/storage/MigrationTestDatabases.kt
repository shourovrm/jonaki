package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * In-memory databases built from the schemas Room exported to schemas/, on
 * the JVM build of the bundled SQLite (spike S-1), for migration tests.
 * Call [closeAll] after each test.
 */
class MigrationTestDatabases {
    private val connections = mutableListOf<SQLiteConnection>()

    fun closeAll() {
        connections.forEach { connection -> connection.close() }
    }

    fun openVersion(version: Int): SQLiteConnection {
        val connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL("PRAGMA foreign_keys = ON")
        connections += connection
        createStatements(version).forEach { statement -> connection.execSQL(statement) }
        return connection
    }

    /** The CREATE statements Room exported for one schema version. */
    private fun createStatements(version: Int): List<String> {
        val schemaFile = File("schemas/app.jonaki.core.storage.JonakiDatabase/$version.json")
        val database = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        val statements = mutableListOf<String>()
        for (entity in database.getValue("entities").jsonArray) {
            val fields = entity.jsonObject
            val tableName = fields.getValue("tableName").jsonPrimitive.content
            statements += fields.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", tableName)
            for (index in fields["indices"]?.jsonArray.orEmpty()) {
                statements += index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", tableName)
            }
        }
        return statements
    }

    fun queryStrings(connection: SQLiteConnection, sql: String, vararg arguments: String): List<String> {
        val rows = mutableListOf<String>()
        connection.prepare(sql).use { statement ->
            arguments.forEachIndexed { index, argument -> statement.bindText(index + 1, argument) }
            while (statement.step()) {
                rows += (0 until statement.getColumnCount()).joinToString("|") { column ->
                    if (statement.isNull(column)) "null" else statement.getText(column)
                }
            }
        }
        return rows
    }

    /** Columns, indices and foreign keys of every table, as Room compares them when it opens a database. */
    fun describeTables(connection: SQLiteConnection): Map<String, List<String>> {
        val tables = queryStrings(
            connection,
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
        )
        return tables.associateWith { table ->
            // ALTER TABLE ADD COLUMN records "DEFAULT NULL"; Room ignores defaults its entities do not declare.
            queryStrings(connection, "PRAGMA table_info(`$table`)").map { column -> column.replace("|NULL|", "|null|") } +
                queryStrings(connection, "PRAGMA index_list(`$table`)").map { "index " + it.substringAfter('|') } +
                queryStrings(connection, "PRAGMA foreign_key_list(`$table`)")
        }
    }
}
