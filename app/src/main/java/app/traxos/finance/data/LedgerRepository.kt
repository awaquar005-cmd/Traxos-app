package app.traxos.finance.data

import androidx.room.withTransaction
import app.traxos.finance.domain.Money
import app.traxos.finance.domain.NavigationLayout
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
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
    ) =
        mutate(operationId) {
            require(name.isNotBlank() && name.trim().length <= 80) {
                "Account name must contain 1–80 characters"
            }
            require(type in listOf("CASH", "BANK")) { "Choose cash or bank" }
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
                if (old == null) Account(id, name.trim(), type, currency, opening, openingDate)
                else old.copy(name = name.trim(), archived = archived, revision = old.revision + 1)
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
                    .put("archived", account.archived),
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
    ) =
        mutate(operationId) {
            Money.effect(type, amount)
            LocalDate.ofEpochDay(day)
            require(note.length <= 500) { "Note must contain at most 500 characters" }
            val old = dao.record(id)
            require(old?.revision == expectedRevision && old?.deleted != true) {
                "Transaction changed. Reopen it before saving."
            }
            val account = requireNotNull(dao.account(accountId)) { "Choose an account" }
            require(!account.archived || old?.accountId == accountId) { "This account is archived" }
            require(day >= account.openingDate) {
                "Date must be on or after the account opening date"
            }
            require(dao.category(categoryId)?.type == type) {
                "Choose a category for this transaction type"
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
            val changed = old.copy(deleted = deleted, revision = old.revision + 1)
            dao.updateRecord(changed)
            recordJournal(operationId, changed, if (deleted) "DELETE" else "RESTORE", old.revision)
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
        require(category.isBlank() || dao.category(category)?.type == "EXPENSE") {
            "Choose an expense category"
        }
        dao.defaults(account, category)
    }

    suspend fun setDefaultAccount(value: String) {
        require(value.isBlank() || dao.account(value)?.archived == false)
        dao.defaultAccount(value)
    }

    suspend fun setDefaultCategory(value: String) {
        require(value.isBlank() || dao.category(value)?.type == "EXPENSE")
        dao.defaultCategory(value)
    }

    suspend fun setSmartEnabled(value: Boolean) = dao.smartEnabled(value)

    suspend fun setSmsOptIn(value: Boolean) {
        require(!value || app.traxos.finance.BuildConfig.SMS_POLICY_APPROVED)
        dao.smsOptIn(value)
    }

    suspend fun setSuggestionNotices(value: Boolean) = dao.suggestionNotices(value)

    suspend fun setSmartSettings(enabled: Boolean, sms: Boolean, notices: Boolean) {
        require(!sms || app.traxos.finance.BuildConfig.SMS_POLICY_APPROVED) {
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
