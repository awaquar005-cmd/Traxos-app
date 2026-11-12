package app.traxos.finance.gifts

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "gift_cards",
    indices =
        [Index(value = ["fingerprint"], unique = true), Index(value = ["deleted", "expiryDay"])],
)
data class GiftCard(
    @PrimaryKey val id: String,
    val merchant: String,
    val name: String,
    val kind: String,
    val secret: ByteArray,
    val fingerprint: String,
    val originalMinor: Long?,
    val remainingMinor: Long?,
    val currency: String,
    val receivedDay: Long,
    val expiryDay: Long?,
    val partialAllowed: Boolean = false,
    val redeemed: Boolean = false,
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val manuallyExpired: Boolean = false,
    val revision: Long = 1,
    val createdAt: Long,
    val source: String,
)

@Entity(
    tableName = "gift_redemptions",
    foreignKeys =
        [
            ForeignKey(
                entity = GiftCard::class,
                parentColumns = ["id"],
                childColumns = ["cardId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index("cardId")],
)
data class GiftRedemption(
    @PrimaryKey val id: String,
    val cardId: String,
    val kind: String,
    val amountMinor: Long?,
    val createdAt: Long,
    val cardRevision: Long,
    val undoesId: String? = null,
)

@Entity(tableName = "gift_settings")
data class GiftSettings(
    @PrimaryKey val id: Int = 1,
    val reminders: Boolean = true,
    val intervals: String = "30,7,1,0",
    val hideCodes: Boolean = true,
    val textDetection: Boolean = true,
    val imageDetection: Boolean = true,
    val sort: String = "EXPIRY",
    // Controls opening the review, never confirmation or saving itself.
    val autoReview: Boolean = true,
)

@Entity(tableName = "gift_notifications")
data class GiftNotification(@PrimaryKey val token: String, val deliveredAt: Long)

// List/scheduler reads never pull encrypted image payloads into memory.
private const val GIFT_METADATA =
    "id,merchant,name,kind,X'' AS secret,fingerprint,originalMinor,remainingMinor,currency,receivedDay,expiryDay,partialAllowed,redeemed,archived,deleted,manuallyExpired,revision,createdAt,source"

@Dao
interface GiftDao {
    @Query("SELECT $GIFT_METADATA FROM gift_cards WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observe(): Flow<List<GiftCard>>

    @Query("SELECT COUNT(*) FROM gift_cards") suspend fun count(): Long

    @Query("SELECT $GIFT_METADATA FROM gift_cards WHERE deleted = 0")
    suspend fun all(): List<GiftCard>

    @Query("SELECT * FROM gift_cards WHERE id = :id") suspend fun card(id: String): GiftCard?

    @Query(
        "SELECT * FROM gift_cards WHERE fingerprint = :fingerprint AND deleted = 0 AND id != :exceptId LIMIT 1"
    )
    suspend fun duplicate(fingerprint: String, exceptId: String): GiftCard?

    @Insert suspend fun insert(card: GiftCard)

    @Update suspend fun update(card: GiftCard)

    @Query("SELECT * FROM gift_redemptions WHERE cardId = :id ORDER BY cardRevision DESC")
    fun history(id: String): Flow<List<GiftRedemption>>

    @Query("SELECT * FROM gift_redemptions WHERE cardId = :id ORDER BY cardRevision DESC LIMIT 1")
    suspend fun latest(id: String): GiftRedemption?

    @Query("SELECT * FROM gift_redemptions WHERE id = :id")
    suspend fun operation(id: String): GiftRedemption?

    @Insert suspend fun addRedemption(redemption: GiftRedemption)

    @Query("SELECT * FROM gift_settings WHERE id = 1") fun settings(): Flow<GiftSettings?>

    @Upsert suspend fun settings(settings: GiftSettings)

    @Query("SELECT COUNT(*) FROM gift_notifications WHERE token = :token")
    suspend fun notified(token: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun notified(item: GiftNotification): Long
}
