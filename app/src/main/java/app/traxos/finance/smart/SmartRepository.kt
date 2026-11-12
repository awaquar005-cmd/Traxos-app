package app.traxos.finance.smart

import androidx.room.withTransaction
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import app.traxos.finance.gifts.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SmartRepository(
    private val db: TraxosDatabase,
    private val cipher: GiftCipher,
    private val ledger: LedgerRepository,
    private val gifts: GiftRepository,
) {
    val dao = db.smart()
    private val gate = Mutex()

    private fun bytes(d: SmartDraft): ByteArray =
        JSONObject()
            .apply {
                put("giftKind", d.giftKind)
                put("cardRequired", d.cardRequired)
                put("v", 1)
                put("kind", d.kind)
                put("merchant", d.merchant)
                put("amount", d.amount)
                put("currency", d.currency)
                put("type", d.type)
                put("day", d.day)
                put("code", d.code)
                put("pin", d.pin)
                put("expiry", d.expiry)
                put("balance", d.amountIsBalance)
                put("reference", d.reference)
                put("account", d.accountHint)
                put("note", d.note)
                put("warning", d.warning)
                put("text", d.originalText)
            }
            .toString()
            .toByteArray(Charsets.UTF_8)

    suspend fun read(s: SmartSuggestion): SmartDraft =
        withContext(Dispatchers.IO) {
            val j = JSONObject(cipher.open("smart:${s.id}", s.secret).toString(Charsets.UTF_8))
            require(j.getInt("v") == 1)
            SmartDraft(
                j.getString("kind"),
                j.getString("merchant"),
                j.getString("amount"),
                j.getString("currency"),
                j.getString("type"),
                j.getString("day"),
                j.getString("code"),
                j.getString("pin"),
                j.getString("expiry"),
                j.getBoolean("balance"),
                j.getString("reference"),
                j.getString("account"),
                j.getString("note"),
                j.getString("warning"),
                j.getString("text"),
                j.optString("giftKind", "STORED_VALUE"),
                j.optBoolean("cardRequired", false),
            )
        }

    suspend fun offer(text: String, receivedDay: String? = null): Pair<SmartSuggestion, Boolean>? =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val d = SmartParser.parse(text, receivedDay) ?: return@withLock null
                if (dao.count() > 0) cipher.verifyExisting()
                val identity =
                    when {
                        d.kind == "GIFT" && d.code.isNotBlank() ->
                            "gift:${if(d.giftKind=="DISCOUNT") GiftRules.normalize(d.merchant)+":" else ""}${GiftRules.normalize(d.code)}"
                        d.reference.isNotBlank() ->
                            "payment:${d.reference}:${d.type}:${d.amount}:${d.currency}"
                        else -> d.originalText.lowercase().replace(Regex("\\s+"), " ").trim()
                    }
                val token = cipher.fingerprint(identity)
                db.withTransaction {
                    if (d.reference.isNotBlank()) {
                        val legacy =
                            cipher.fingerprint(
                                "payment:${d.reference}:${d.accountHint}:${d.type}:${d.amount}:${d.currency}"
                            )
                        dao.duplicate(legacy)?.let {
                            return@withTransaction it to false
                        }
                    }
                    dao.duplicate(token)?.let {
                        return@withTransaction it to false
                    }
                    val id = UUID.randomUUID().toString()
                    SmartSuggestion(
                            id,
                            token,
                            d.kind,
                            cipher.seal("smart:$id", bytes(d)),
                            status =
                                if (
                                    d.kind == "GIFT" &&
                                        d.code.isNotBlank() &&
                                        gifts.hasDuplicate(d.code, "", d.giftKind, d.merchant)
                                )
                                    "ALREADY_IMPORTED"
                                else "PENDING",
                        )
                        .also { dao.insert(it) } to true
                }
            }
        }

    suspend fun reject(id: String, ignore: Boolean) =
        withContext(Dispatchers.IO) {
            require(dao.status(id, if (ignore) "IGNORED" else "REJECTED") == 1) {
                "This suggestion has already been reviewed"
            }
        }

    suspend fun approve(id: String, d: SmartDraft, accountId: String, categoryId: String): String =
        withContext(Dispatchers.IO) {
            gate.withLock {
                require(d.kind in listOf("TRANSACTION", "GIFT")) {
                    "This message needs manual matching; no ordinary expense or income will be created"
                }
                val result =
                    db.withTransaction {
                        val original = requireNotNull(dao.get(id)) { "Suggestion unavailable" }
                        if (original.status == "APPROVED") return@withTransaction id
                        require(original.status == "PENDING") {
                            "This suggestion has already been reviewed"
                        }
                        require(original.kind == d.kind) {
                            "Do not change a transfer or settlement into an ordinary expense"
                        }
                        if (d.kind == "GIFT") {
                            val draft =
                                GiftDraft(
                                    id = id,
                                    kind = d.giftKind,
                                    merchant = d.merchant,
                                    code = d.code,
                                    pin = d.pin,
                                    original = if (!d.amountIsBalance) d.amount else "",
                                    remaining = if (d.amountIsBalance) d.amount else "",
                                    currency = d.currency,
                                    expiry = d.expiry,
                                )
                            gifts.save(draft.card(), draft.secret(), 0)
                        } else {
                            val account =
                                requireNotNull(db.ledger().account(accountId)) {
                                    "Choose an account"
                                }
                            val source = read(original)
                            require(
                                !source.cardRequired ||
                                    (account.type == "CREDIT_CARD" && d.type == "EXPENSE")
                            ) {
                                "This is a credit card purchase. Select its credit card account and Expense."
                            }
                            require(account.currency == d.currency) {
                                "Account currency must match this message"
                            }
                            val date =
                                runCatching { LocalDate.parse(d.day) }
                                    .getOrElse {
                                        throw IllegalArgumentException(
                                            "Choose the transaction date"
                                        )
                                    }
                            require(!date.isAfter(LocalDate.now())) {
                                "Transaction date cannot be in the future"
                            }
                            ledger.saveRecord(
                                id,
                                id,
                                accountId,
                                categoryId,
                                d.type,
                                Money.parse(d.amount, d.currency),
                                date.toEpochDay(),
                                d.note.take(500),
                            )
                        }
                        require(dao.status(id, "APPROVED") == 1)
                        id
                    }
                result
            }
        }
}
