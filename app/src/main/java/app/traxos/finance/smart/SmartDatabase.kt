package app.traxos.finance.smart

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "smart_suggestions",
    indices =
        [Index(value = ["fingerprint"], unique = true), Index(value = ["status", "createdAt"])],
)
data class SmartSuggestion(
    @PrimaryKey val id: String,
    val fingerprint: String,
    val kind: String,
    val secret: ByteArray,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface SmartDao {
    @Query(
        "SELECT * FROM smart_suggestions WHERE (:status='ALL' OR status=:status) ORDER BY createdAt DESC LIMIT :limit"
    )
    fun observe(status: String, limit: Int): Flow<List<SmartSuggestion>>

    @Query("SELECT * FROM smart_suggestions WHERE id=:id")
    suspend fun get(id: String): SmartSuggestion?

    @Query("SELECT * FROM smart_suggestions WHERE fingerprint=:value LIMIT 1")
    suspend fun duplicate(value: String): SmartSuggestion?

    @Query("SELECT COUNT(*) FROM smart_suggestions") suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM smart_suggestions WHERE status='PENDING'")
    fun pendingCount(): Flow<Long>

    @Insert suspend fun insert(value: SmartSuggestion)

    @Query("UPDATE smart_suggestions SET status=:status WHERE id=:id AND status='PENDING'")
    suspend fun status(id: String, status: String): Int
}
