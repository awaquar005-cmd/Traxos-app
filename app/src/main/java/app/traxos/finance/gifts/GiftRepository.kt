package app.traxos.finance.gifts

import androidx.room.withTransaction
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.domain.Money
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface GiftScheduler {
    fun reconcile()

    fun cancel(cardId: String)
}

object NoGiftScheduler : GiftScheduler {
    override fun reconcile() {}

    override fun cancel(cardId: String) {}
}

class GiftRepository(
    val db: TraxosDatabase,
    private val cipher: GiftCipher,
    private val scheduler: GiftScheduler = NoGiftScheduler,
) {
    val dao = db.gifts()
    private val mutex = Mutex()
    val cards = dao.observe()
    val settings = dao.settings()

    suspend fun secret(card: GiftCard): GiftSecret =
        withContext(Dispatchers.IO) {
            val stored = if (card.secret.isEmpty()) requireNotNull(dao.card(card.id)) else card
            require(!stored.deleted) { "Card is no longer available" }
            GiftSecret.read(cipher.open(stored.id, stored.secret))
        }

    suspend fun save(card: GiftCard, secret: GiftSecret, expectedRevision: Long): GiftCard =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                require(card.merchant.isNotBlank() && card.merchant.length <= 80) {
                    "Enter a merchant name (up to 80 characters)"
                }
                require(card.name.isNotBlank() && card.name.length <= 80) {
                    "Enter a card name (up to 80 characters)"
                }
                require(card.kind in GiftRules.kinds && card.currency in Money.currencies) {
                    "Choose a card type and currency"
                }
                GiftRules.validateCode(secret.code)
                require(
                    secret.pin.length <= 128 &&
                        secret.notes.length <= 4000 &&
                        (secret.image?.size ?: 0) <= 700_000
                ) {
                    "PIN, notes or image exceeds the supported size"
                }
                require(card.source in listOf("PURCHASED", "RECEIVED", "PROMOTIONAL"))
                require(
                    card.receivedDay in
                        LocalDate.of(1900, 1, 1).toEpochDay()..LocalDate.now().toEpochDay()
                ) {
                    "Received date must be between 1900 and today"
                }
                require(card.expiryDay == null || card.expiryDay >= card.receivedDay) {
                    "Expiry must not precede the received date"
                }
                require(
                    card.expiryDay == null ||
                        card.expiryDay <= LocalDate.now().plusYears(100).toEpochDay()
                ) {
                    "Expiry must be within the next 100 years"
                }
                if (card.kind == "DISCOUNT")
                    require(card.originalMinor == null && card.remainingMinor == null) {
                        "Discount vouchers do not have a stored cash balance"
                    }
                else {
                    require(card.originalMinor == null || card.originalMinor in 0..Money.MAX_MINOR)
                    require(
                        card.remainingMinor == null || card.remainingMinor in 0..Money.MAX_MINOR
                    )
                    require(
                        card.originalMinor == null ||
                            card.remainingMinor == null ||
                            card.remainingMinor <= card.originalMinor
                    ) {
                        "Remaining balance exceeds the original value"
                    }
                }
                if (dao.count() > 0) cipher.verifyExisting()
                val fingerprint =
                    cipher.fingerprint(
                        if (card.kind == "DISCOUNT")
                            "discount:${card.merchant.trim().lowercase(java.util.Locale.ROOT)}:${secret.code}"
                        else "value:${secret.code}"
                    )
                val saved =
                    db.withTransaction {
                        val old = dao.card(card.id)
                        require((old?.revision ?: 0) == expectedRevision && old?.deleted != true) {
                            "Card changed; reopen it before saving"
                        }
                        require(dao.duplicate(fingerprint, card.id) == null) {
                            "This code already exists in your wallet, including archived cards"
                        }
                        if (old != null && dao.latest(card.id) != null)
                            require(
                                card.currency == old.currency &&
                                    card.kind == old.kind &&
                                    card.originalMinor == old.originalMinor &&
                                    card.remainingMinor == old.remainingMinor
                            ) {
                                "Use redemption/refund actions to adjust a card with history"
                            }
                        val result =
                            card.copy(
                                secret = cipher.seal(card.id, secret.bytes()),
                                fingerprint = fingerprint,
                                revision = expectedRevision + 1,
                                archived = old?.archived ?: false,
                                manuallyExpired = old?.manuallyExpired ?: false,
                                redeemed =
                                    if (old != null && dao.latest(card.id) != null) old.redeemed
                                    else card.remainingMinor == 0L,
                            )
                        if (old == null) dao.insert(result) else dao.update(result)
                        result
                    }
                scheduler.cancel(saved.id)
                scheduler.reconcile()
                saved
            }
        }

    suspend fun archive(id: String, revision: Long, archived: Boolean) =
        mutate(id, revision) { it.copy(archived = archived) }

    suspend fun markExpired(id: String, revision: Long, expired: Boolean = true) =
        mutate(id, revision) { it.copy(manuallyExpired = expired) }

    suspend fun delete(id: String, revision: Long) =
        mutate(id, revision) { it.copy(deleted = true, fingerprint = "deleted:$id") }

    private suspend fun mutate(id: String, revision: Long, change: (GiftCard) -> GiftCard) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                db.withTransaction {
                    val old = requireNotNull(dao.card(id))
                    require(old.revision == revision && !old.deleted) { "Card changed; reopen it" }
                    dao.update(change(old).copy(revision = revision + 1))
                }
                scheduler.cancel(id)
                scheduler.reconcile()
            }
        }

    /**
     * Stable operation IDs make retries idempotent; Room transaction protects remaining + history.
     */
    suspend fun redeem(
        id: String,
        revision: Long,
        amount: Long?,
        operationId: String,
    ): GiftRedemption =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val result =
                    db.withTransaction {
                        dao.operation(operationId)?.let {
                            require(
                                it.cardId == id && it.kind == "REDEEM" && it.amountMinor == amount
                            ) {
                                "Operation identity does not match this redemption"
                            }
                            return@withTransaction it
                        }
                        val old = requireNotNull(dao.card(id))
                        require(old.revision == revision) { "Card changed; reopen it" }
                        val remaining = GiftRules.redeem(old, amount)
                        dao.update(
                            old.copy(
                                remainingMinor = remaining,
                                redeemed = remaining == 0L || old.kind == "DISCOUNT",
                                revision = revision + 1,
                            )
                        )
                        GiftRedemption(
                                operationId,
                                id,
                                "REDEEM",
                                amount,
                                System.currentTimeMillis(),
                                revision + 1,
                            )
                            .also { dao.addRedemption(it) }
                    }
                scheduler.cancel(id)
                scheduler.reconcile()
                result
            }
        }

    /** Undo or refund the last redemption only; never silently rewrite history or cash ledger. */
    suspend fun reverse(
        id: String,
        revision: Long,
        operationId: String,
        refund: Boolean = false,
    ): Unit =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                db.withTransaction {
                    dao.operation(operationId)?.let {
                        require(it.cardId == id && it.kind == if (refund) "REFUND" else "UNDO") {
                            "Operation identity does not match this reversal"
                        }
                        return@withTransaction
                    }
                    val old = requireNotNull(dao.card(id))
                    require(!old.deleted && old.revision == revision) { "Card changed; reopen it" }
                    val last = requireNotNull(dao.latest(id)) { "No redemption to reverse" }
                    require(last.kind == "REDEEM") {
                        "The latest redemption has already been reversed"
                    }
                    val balance =
                        old.remainingMinor?.let { Math.addExact(it, last.amountMinor ?: 0) }
                    require(balance == null || balance <= Money.MAX_MINOR)
                    dao.update(
                        old.copy(
                            remainingMinor = balance,
                            redeemed = false,
                            revision = revision + 1,
                        )
                    )
                    dao.addRedemption(
                        GiftRedemption(
                            operationId,
                            id,
                            if (refund) "REFUND" else "UNDO",
                            last.amountMinor,
                            System.currentTimeMillis(),
                            revision + 1,
                            last.id,
                        )
                    )
                }
                scheduler.reconcile()
            }
        }

    suspend fun updateSettings(value: GiftSettings) =
        withContext(Dispatchers.IO) {
            GiftRules.intervals(value.intervals)
            require(value.sort in GiftRules.sorts)
            dao.settings(value)
            scheduler.reconcile()
        }
}
