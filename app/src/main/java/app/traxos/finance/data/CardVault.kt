package app.traxos.finance.data

import androidx.room.*
import app.traxos.finance.gifts.GiftCipher
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Entity(
    tableName = "account_card_secrets",
    foreignKeys =
        [
            ForeignKey(
                entity = Account::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
)
data class CardReference(
    @PrimaryKey val accountId: String,
    val payload: ByteArray,
    val revision: Long,
    val operationId: String,
)

@Dao
interface CardReferenceDao {
    @Query("SELECT * FROM account_card_secrets WHERE accountId=:id")
    suspend fun get(id: String): CardReference?

    @Upsert suspend fun put(row: CardReference)

    @Query("DELETE FROM account_card_secrets WHERE accountId=:id AND revision=:revision")
    suspend fun remove(id: String, revision: Long): Int
}

data class CardSecret(val number: String = "", val cvv: String = "", val expiry: String = "") {
    fun validated(): CardSecret {
        val pan = number.filterNot { it == ' ' || it == '-' }
        require(pan.length in 13..19 && pan.all { it in '0'..'9' }) {
            "Enter a valid card number (13–19 digits)"
        }
        var sum = 0
        pan.reversed().forEachIndexed { index, c ->
            var n = c - '0'
            if (index % 2 == 1) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
        }
        require(sum % 10 == 0 && pan.any { it != '0' }) { "Card number check failed" }
        require(cvv.isBlank() || (cvv.length in 3..4 && cvv.all { it in '0'..'9' })) {
            "CVV must be 3 or 4 digits, or left blank"
        }
        val parts = expiry.trim().split('/')
        require(parts.size == 2 && parts[0].length in 1..2 && parts[1].length in listOf(2, 4)) {
            "Use MM/YY for expiry"
        }
        val month = parts[0].toIntOrNull()
        val rawYear = parts[1].toIntOrNull()
        require(month != null && month in 1..12 && rawYear != null) { "Use MM/YY for expiry" }
        val year = if (parts[1].length == 2) 2000 + rawYear!! else rawYear!!
        require(year in 2000..2099) { "Expiry year must be between 2000 and 2099" }
        return copy(
            number = pan,
            cvv = cvv.trim(),
            expiry = "${month.toString().padStart(2,'0')}/${(year%100).toString().padStart(2,'0')}",
        )
    }

    fun expired(): Boolean {
        val p = expiry.split('/')
        return YearMonth.of(2000 + p[1].toInt(), p[0].toInt()) < YearMonth.now()
    }

    fun bytes() =
        JSONObject()
            .put("v", 1)
            .put("pan", number)
            .put("cvv", cvv)
            .put("expiry", expiry)
            .toString()
            .toByteArray(Charsets.UTF_8)

    companion object {
        fun read(bytes: ByteArray) =
            JSONObject(bytes.toString(Charsets.UTF_8)).let {
                require(it.getInt("v") == 1)
                CardSecret(it.getString("pan"), it.getString("cvv"), it.getString("expiry"))
            }
    }
}

class CardVaultRepository(val db: TraxosDatabase, private val cipher: GiftCipher) {
    suspend fun read(id: String): Pair<CardSecret, Long> =
        withContext(Dispatchers.IO) {
            val row = db.cardReferences().get(id)
            if (row == null) CardSecret() to 0L
            else CardSecret.read(cipher.open("card:$id", row.payload)) to row.revision
        }

    suspend fun save(id: String, secret: CardSecret, revision: Long, operation: String) =
        withContext(Dispatchers.IO) {
            UUID.fromString(operation)
            val valid = secret.validated()
            db.withTransaction {
                require(db.ledger().account(id)?.type in setOf("CREDIT_CARD", "BANK")) {
                    "Choose a bank or credit card account"
                }
                val old = db.cardReferences().get(id)
                if (old?.operationId == operation) return@withTransaction
                require((old?.revision ?: 0) == revision) {
                    "Card details changed; reopen before saving"
                }
                if (old != null)
                    cipher.open(
                        "card:$id",
                        old.payload,
                    ) // Lost keys fail closed; never replace around existing secrets.
                db.cardReferences()
                    .put(
                        CardReference(
                            id,
                            cipher.seal("card:$id", valid.bytes()),
                            revision + 1,
                            operation,
                        )
                    )
            }
        }

    suspend fun remove(id: String, revision: Long) =
        withContext(Dispatchers.IO) {
            require(db.cardReferences().remove(id, revision) == 1) {
                "Card details changed; reopen before removing"
            }
        }
}
