package app.traxos.finance.data

import androidx.room.withTransaction
import app.traxos.finance.domain.Money
import app.traxos.finance.domain.NavigationLayout
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class LedgerRepository(private val db: TraxosDatabase) {
    val dao = db.ledger()
    private val gate = Mutex()

    suspend fun saveAccount(
        operationId: String,
        id: String,
        name: String,
        type: String,
        currency: String,
        opening: Long,
        expectedRevision: Long? = null,
        archived: Boolean = false,
        openingDate: Long = LocalDate.now().toEpochDay(),
        icon: String = "wallet",
        color: String = "BLUE",
    ) =
        mutate(operationId) {
            require(
                name.isNotBlank() && name.trim().length <= 80 && name.none { it.isISOControl() }
            ) {
                "Account name must contain 1–80 characters"
            }
            require(type in accountTypes) { "Choose an account type" }
            require(icon in icons && color in colors) { "Choose a supported icon and color" }
            require(
                dao.accounts().first().none {
                    it.account.id != id && nameKey(it.account.name) == nameKey(name)
                }
            ) {
                "An account with this name already exists (including archived accounts)"
            }
            require(currency in Money.currencies) { "Choose a supported currency" }
            require(opening in 0..Money.MAX_MINOR) {
                "Opening balance is outside the supported range"
            }
            LocalDate.ofEpochDay(openingDate)
            val old = dao.account(id)
            require(old?.revision == expectedRevision) {
                "Account changed. Reopen it before saving."
            }
            if (old == null) require(dao.accountCount() < 1000) { "Account limit reached" }
            val account =
                if (old == null)
                    Account(
                        id,
                        name.trim(),
                        type,
                        currency,
                        if (type == "CREDIT_CARD") -opening else opening,
                        openingDate,
                        icon = icon,
                        color = color,
                    )
                else {
                    require(old.type == type && old.currency == currency) {
                        "Account type and currency cannot change after creation"
                    }
                    old.copy(
                        name = name.trim(),
                        archived = archived,
                        revision = old.revision + 1,
                        icon = icon,
                        color = color,
                    )
                }
            if (old == null) dao.insertAccount(account) else dao.updateAccount(account)
            journal(
                operationId,
                id,
                "ACCOUNT",
                if (old == null) "CREATE" else "UPDATE",
                old?.revision ?: 0,
                account.revision,
                JSONObject()
                    .put("id", id)
                    .put("name", account.name)
                    .put("type", account.type)
                    .put("currency", account.currency)
                    .put("openingMinor", account.openingMinor)
                    .put("openingDate", account.openingDate)
                    .put("archived", account.archived)
                    .put("icon", icon)
                    .put("color", color),
            )
        }

    suspend fun saveRecord(
        operationId: String,
        id: String,
        accountId: String,
        categoryId: String,
        type: String,
        amount: Long,
        day: Long,
        note: String,
        expectedRevision: Long? = null,
        merchantId: String? = null,
    ) =
        mutate(operationId) {
            Money.effect(type, amount)
            LocalDate.ofEpochDay(day)
            require(note.length <= 500) { "Note must contain at most 500 characters" }
            val old = dao.record(id)
            require(old?.linkId == null) { "Use paired payment controls for card payments" }
            require(old?.revision == expectedRevision && old?.deleted != true) {
                "Transaction changed. Reopen it before saving."
            }
            val account = requireNotNull(dao.account(accountId)) { "Choose an account" }
            require(!account.archived || old?.accountId == accountId) { "This account is archived" }
            require(day >= account.openingDate) {
                "Date must be on or after the account opening date"
            }
            require(type in listOf("EXPENSE", "INCOME", "REFUND"))
            require(type != "INCOME" || account.type != "CREDIT_CARD") {
                "Card payments are not income. Use Pay card from the account."
            }
            require(type != "REFUND" || account.type == "CREDIT_CARD") {
                "Card refunds require a credit card account"
            }
            val category = dao.category(categoryId)
            require(category?.type == if (type == "REFUND") "EXPENSE" else type) {
                "Choose a category for this transaction type"
            }
            require(category?.archived == false || old?.categoryId == categoryId) {
                "Choose an active category"
            }
            if (merchantId != null) {
                val merchant = dao.label(merchantId)
                require(
                    merchant?.kind == "MERCHANT" &&
                        (!merchant.archived || old?.merchantId == merchantId)
                ) {
                    "Choose an active merchant"
                }
            }
            if (old != null)
                require(dao.account(old.accountId)?.currency == account.currency) {
                    "Cannot change transaction currency. Create a correcting entry instead."
                }
            if (old == null)
                require(dao.recordCount() < Money.MAX_TRANSACTIONS) {
                    "Device record limit reached; export before continuing"
                }
            val record =
                LedgerRecord(
                    id,
                    accountId,
                    categoryId,
                    type,
                    amount,
                    day,
                    old?.zoneId ?: ZoneId.systemDefault().id,
                    note.trim(),
                    (old?.revision ?: 0) + 1,
                    merchantId = merchantId,
                )
            if (old == null) dao.insertRecord(record) else dao.updateRecord(record)
            recordJournal(
                operationId,
                record,
                if (old == null) "CREATE" else "UPDATE",
                old?.revision ?: 0,
            )
        }

    suspend fun setDeleted(
        operationId: String,
        id: String,
        expectedRevision: Long,
        deleted: Boolean,
    ) =
        mutate(operationId) {
            val old = requireNotNull(dao.record(id)) { "Transaction not found" }
            require(old.revision == expectedRevision) {
                "Transaction changed. Reopen it before continuing."
            }
            require(old.deleted != deleted) { "Transaction already has this status" }
            val records = old.linkId?.let { dao.linked(it) } ?: listOf(old)
            if (old.linkId != null)
                require(
                    records.size == 2 &&
                        records.map { it.type }.toSet() in
                            listOf(
                                setOf("PAYMENT_IN", "PAYMENT_OUT"),
                                setOf("TRANSFER_IN", "TRANSFER_OUT"),
                            ) &&
                        records.map { it.amountMinor }.distinct().size == 1
                ) {
                    "Paired movement is inconsistent; keep data and report this"
                }
            require(records.all { it.deleted == old.deleted }) {
                "Paired movement is inconsistent; keep data and report this"
            }
            for (r in records) {
                val changed = r.copy(deleted = deleted, revision = r.revision + 1)
                dao.updateRecord(changed)
                recordJournal(
                    if (r.id == old.id) operationId else pairedId(operationId),
                    changed,
                    if (deleted) "DELETE" else "RESTORE",
                    r.revision,
                )
            }
        }

    companion object {
        val accountTypes = listOf("CASH", "BANK", "WALLET", "CREDIT_CARD")
        val icons = listOf("tag", "wallet", "food", "travel", "home", "work", "gift", "health")
        val colors = listOf("BLUE", "GREEN", "VIOLET", "AMBER", "ROSE")

        fun nameKey(name: String) = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

        fun pairedId(id: String) = UUID.nameUUIDFromBytes((id + ":paired").toByteArray()).toString()
    }

    suspend fun saveCategory(
        op: String,
        id: String,
        name: String,
        type: String,
        icon: String = "tag",
        color: String = "BLUE",
        archived: Boolean = false,
        revision: Long? = null,
    ) =
        mutate(op) {
            require(name.trim().length in 1..80 && name.none { it.isISOControl() }) {
                "Enter a category name (1–80 characters)"
            }
            require(type in listOf("EXPENSE", "INCOME"))
            require(icon in icons && color in colors)
            val old = dao.category(id)
            require(old?.revision == revision) { "Category changed; reopen before saving" }
            require(old == null || old.type == type) {
                "Category type cannot change; history must keep its meaning"
            }
            require(
                dao.categories().first().none {
                    it.id != id && it.type == type && nameKey(it.name) == nameKey(name)
                }
            ) {
                "This category name already exists (including archived categories)"
            }
            val value =
                Category(id, name.trim(), type, archived, icon, color, (old?.revision ?: 0) + 1)
            if (old == null) dao.insertCategory(value) else dao.updateCategory(value)
            journal(
                op,
                id,
                "CATEGORY",
                if (old == null) "CREATE" else "UPDATE",
                old?.revision ?: 0,
                value.revision,
                JSONObject()
                    .put("name", value.name)
                    .put("type", type)
                    .put("icon", icon)
                    .put("color", color)
                    .put("archived", archived),
            )
        }

    suspend fun saveLabel(
        op: String,
        id: String,
        name: String,
        kind: String,
        archived: Boolean = false,
        revision: Long? = null,
    ) =
        mutate(op) {
            require(name.trim().length in 1..120 && name.none { it.isISOControl() }) {
                "Enter a name (1–120 characters)"
            }
            require(kind in listOf("MERCHANT", "DESCRIPTION"))
            val old = dao.label(id)
            require(old?.revision == revision && (old == null || old.kind == kind)) {
                "Saved option changed; reopen it"
            }
            require(
                dao.labels().first().none {
                    it.id != id && it.kind == kind && it.nameKey == nameKey(name)
                }
            ) {
                "This name already exists (including archived options)"
            }
            val value =
                EntryLabel(id, name.trim(), nameKey(name), kind, archived, (old?.revision ?: 0) + 1)
            if (old == null) dao.insertLabel(value) else dao.updateLabel(value)
            journal(
                op,
                id,
                kind,
                if (old == null) "CREATE" else "UPDATE",
                old?.revision ?: 0,
                value.revision,
                JSONObject().put("name", value.name).put("archived", archived),
            )
        }

    /** A payment reduces bank money and card debt together, without creating expense or income. */
    suspend fun payCard(op: String, cardId: String, sourceId: String, amount: Long, day: Long) =
        mutate(op) {
            require(amount in 1..Money.MAX_MINOR)
            LocalDate.ofEpochDay(day)
            val rows = dao.accounts().first()
            val card =
                requireNotNull(rows.firstOrNull { it.account.id == cardId }) { "Choose a card" }
            val source =
                requireNotNull(rows.firstOrNull { it.account.id == sourceId }) {
                    "Choose a payment account"
                }
            require(card.account.type == "CREDIT_CARD" && source.account.type != "CREDIT_CARD")
            require(!card.account.archived && !source.account.archived) { "Choose active accounts" }
            require(card.account.currency == source.account.currency) {
                "Payment currency must match the card"
            }
            require(day >= maxOf(card.account.openingDate, source.account.openingDate)) {
                "Payment cannot predate account opening"
            }
            require(card.balanceMinor < 0 && amount <= -card.balanceMinor) {
                "Payment cannot exceed recorded card debt"
            }
            require(source.balanceMinor >= amount) { "Payment exceeds the recorded source balance" }
            require(dao.recordCount() + 2 <= Money.MAX_TRANSACTIONS)
            // Existing category reference is metadata only; payment types are excluded from
            // income/expense totals.
            val category =
                requireNotNull(dao.categories().first().firstOrNull { it.type == "EXPENSE" })
            val incoming =
                LedgerRecord(
                    op,
                    cardId,
                    category.id,
                    "PAYMENT_IN",
                    amount,
                    day,
                    ZoneId.systemDefault().id,
                    "Card payment",
                    linkId = op,
                )
            val outgoing =
                incoming.copy(id = pairedId(op), accountId = sourceId, type = "PAYMENT_OUT")
            dao.insertRecord(incoming)
            dao.insertRecord(outgoing)
            recordJournal(op, incoming, "CREATE", 0)
            recordJournal(pairedId(op), outgoing, "CREATE", 0)
        }

    /** Owned-account movement, never income/spending. Both legs and journal commit together. */
    suspend fun transfer(
        op: String,
        sourceId: String,
        destinationId: String,
        amount: Long,
        day: Long,
    ) =
        mutate(op) {
            require(sourceId != destinationId) { "Choose two different accounts" }
            require(amount in 1..Money.MAX_MINOR)
            LocalDate.ofEpochDay(day)
            val rows = dao.accounts().first()
            val source =
                requireNotNull(rows.firstOrNull { it.account.id == sourceId }) {
                    "Choose the source account"
                }
            val destination =
                requireNotNull(rows.firstOrNull { it.account.id == destinationId }) {
                    "Choose the receiving account"
                }
            require(
                listOf(source, destination).all {
                    !it.account.archived && it.account.type != "CREDIT_CARD"
                }
            ) {
                "Use active cash, bank or wallet accounts. Card repayments use Pay card."
            }
            require(source.account.currency == destination.account.currency) {
                "Transfer currency must match; conversion is not supported"
            }
            require(day >= maxOf(source.account.openingDate, destination.account.openingDate)) {
                "Transfer cannot predate account opening"
            }
            require(source.balanceMinor >= amount) { "Transfer exceeds recorded source balance" }
            require(dao.recordCount() + 2 <= Money.MAX_TRANSACTIONS)
            val category =
                requireNotNull(dao.categories().first().firstOrNull { it.type == "EXPENSE" })
            val outgoing =
                LedgerRecord(
                    op,
                    sourceId,
                    category.id,
                    "TRANSFER_OUT",
                    amount,
                    day,
                    ZoneId.systemDefault().id,
                    "Own-account transfer",
                    linkId = op,
                )
            val incoming =
                outgoing.copy(id = pairedId(op), accountId = destinationId, type = "TRANSFER_IN")
            dao.insertRecord(outgoing)
            dao.insertRecord(incoming)
            recordJournal(op, outgoing, "CREATE", 0)
            recordJournal(pairedId(op), incoming, "CREATE", 0)
        }

    suspend fun setAccent(value: String) {
        require(value in listOf("COBALT", "NAVY", "PETROL", "GRAPHITE", "FOREST"))
        dao.accent(value)
    }

    suspend fun setCurrency(value: String) {
        require(value in Money.currencies)
        dao.currency(value)
    }

    suspend fun setDisplayName(value: String) {
        require(value.trim().length <= 80 && value.none { it.isISOControl() })
        dao.displayName(value.trim())
    }

    suspend fun setDefaults(account: String, category: String) {
        require(account.isBlank() || dao.account(account)?.archived == false) {
            "Choose an active account"
        }
        require(
            category.isBlank() ||
                dao.category(category)?.let { it.type == "EXPENSE" && !it.archived } == true
        ) {
            "Choose an expense category"
        }
        dao.defaults(account, category)
    }

    suspend fun setDefaultAccount(value: String) {
        require(value.isBlank() || dao.account(value)?.archived == false)
        dao.defaultAccount(value)
    }

    suspend fun setDefaultCategory(value: String) {
        require(
            value.isBlank() ||
                dao.category(value)?.let { it.type == "EXPENSE" && !it.archived } == true
        )
        dao.defaultCategory(value)
    }

    suspend fun setSmartEnabled(value: Boolean) = dao.smartEnabled(value)

    suspend fun setSmsOptIn(value: Boolean) {
        require(!value || app.traxos.finance.BuildConfig.SMS_ENABLED)
        dao.smsOptIn(value)
    }

    suspend fun setSuggestionNotices(value: Boolean) = dao.suggestionNotices(value)

    suspend fun setSmartSettings(enabled: Boolean, sms: Boolean, notices: Boolean) {
        require(!sms || app.traxos.finance.BuildConfig.SMS_ENABLED) {
            "Automatic SMS access is unavailable in this build"
        }
        dao.smartSettings(enabled, sms, notices)
    }

    private suspend fun mutate(operationId: String, block: suspend () -> Unit) =
        gate.withLock {
            UUID.fromString(operationId)
            db.withTransaction { if (dao.operation(operationId) == null) block() }
        }

    private suspend fun recordJournal(op: String, r: LedgerRecord, kind: String, base: Long) =
        journal(
            op,
            r.id,
            "TRANSACTION",
            kind,
            base,
            r.revision,
            JSONObject()
                .put("id", r.id)
                .put("accountId", r.accountId)
                .put("categoryId", r.categoryId)
                .put("type", r.type)
                .put("amountMinor", r.amountMinor)
                .put("day", r.day)
                .put("zoneId", r.zoneId)
                .put("note", r.note)
                .put("merchantId", r.merchantId)
                .put("linkId", r.linkId)
                .put("deleted", r.deleted),
        )

    private suspend fun journal(
        op: String,
        id: String,
        entity: String,
        kind: String,
        base: Long,
        revision: Long,
        json: JSONObject,
    ) {
        val payload = json.put("revision", revision).put("profileId", "local").toString()
        val hash =
            MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        dao.insertOperation(
            OutboxOperation(
                op,
                id,
                entity,
                kind,
                base,
                revision,
                payload,
                hash,
                System.currentTimeMillis(),
            )
        )
    }

    suspend fun setTheme(theme: String) {
        require(theme in listOf("SYSTEM", "LIGHT", "DARK"))
        dao.setTheme(theme)
    }

    suspend fun setCenterAction(action: String) {
        require(action in listOf("ADD_TRANSACTION", "ACCOUNTS", "TRANSACTIONS", "HOME"))
        dao.setCenterAction(action)
    }

    suspend fun setBalancesHidden(hidden: Boolean) {
        dao.setBalancesHidden(hidden)
    }

    suspend fun setNavigationSlots(slots: List<String>) {
        dao.setNavigationSlots(NavigationLayout.encode(slots))
    }
}
