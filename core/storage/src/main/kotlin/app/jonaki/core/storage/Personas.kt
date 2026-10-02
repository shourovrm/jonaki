package app.jonaki.core.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A saved persona: a name and the instructions that give it its voice
 * (D-STY-3). A thread points at one through threads.personaId.
 */
@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val instructions: String,
    val createdAtMillis: Long,
)

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY name COLLATE NOCASE, createdAtMillis")
    fun observeAll(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :personaId")
    suspend fun find(personaId: String): PersonaEntity?

    @Upsert
    suspend fun upsert(persona: PersonaEntity)

    /** Threads that used the persona go back to none, so no thread points at a missing row. */
    @Transaction
    suspend fun delete(personaId: String) {
        clearFromThreads(personaId)
        deleteRow(personaId)
    }

    @Query("UPDATE threads SET personaId = NULL WHERE personaId = :personaId")
    suspend fun clearFromThreads(personaId: String)

    @Query("DELETE FROM personas WHERE id = :personaId")
    suspend fun deleteRow(personaId: String)
}
