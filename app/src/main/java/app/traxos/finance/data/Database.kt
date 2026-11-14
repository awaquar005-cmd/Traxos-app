package app.traxos.finance.data

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.traxos.finance.gifts.*
import app.traxos.finance.smart.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "accounts", indices = [Index("profileId")])
data class Account(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val openingMinor: Long,
    val openingDate: Long,
    val archived: Boolean = false,
    val revision: Long = 1,
    val profileId: String = "local",
    @ColumnInfo(defaultValue = "'wallet'") val icon: String = "wallet",
    @ColumnInfo(defaultValue = "'BLUE'") val color: String = "BLUE",
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(defaultValue = "'tag'") val icon: String = "tag",
    @ColumnInfo(defaultValue = "'BLUE'") val color: String = "BLUE",
    @ColumnInfo(defaultValue = "1") val revision: Long = 1,
)

@Entity(tableName = "entry_labels", indices = [Index(value = ["kind", "nameKey"], unique = true)])
data class EntryLabel(
    @PrimaryKey val id: String,
    val name: String,
    val nameKey: String,
    val kind: String,
    val archived: Boolean = false,
    val revision: Long = 1,
)

@Entity(
    tableName = "transactions",
    foreignKeys =
        [
            ForeignKey(
                entity = Account::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Category::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index(value = ["profileId", "day", "id"]),
            Index(value = ["accountId", "day"]),
            Index(value = ["categoryId", "day"]),
            Index("linkId"),
        ],
)
data class LedgerRecord(
    @PrimaryKey val id: String,
    val accountId: String,
    val categoryId: String,
    val type: String,
    val amountMinor: Long,
    val day: Long,
    val zoneId: String,
    val note: String,
    val revision: Long = 1,
    val deleted: Boolean = false,
    val profileId: String = "local",
    @ColumnInfo(defaultValue = "NULL") val merchantId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val linkId: String? = null,
)

@Entity(tableName = "preferences")
data class Preferences(
    @PrimaryKey val id: Int = 1,
    val theme: String = "SYSTEM",
    @ColumnInfo(defaultValue = "'ADD_TRANSACTION'") val centerAction: String = "ADD_TRANSACTION",
    @ColumnInfo(defaultValue = "0") val balancesHidden: Boolean = false,
    @ColumnInfo(defaultValue = "'home,transactions,accounts,settings'")
    val navigationSlots: String = "home,transactions,accounts,settings",
    @ColumnInfo(defaultValue = "'INR'") val defaultCurrency: String = "INR",
    @ColumnInfo(defaultValue = "''") val displayName: String = "",
    @ColumnInfo(defaultValue = "''") val defaultAccountId: String = "",
    @ColumnInfo(defaultValue = "''") val defaultExpenseCategoryId: String = "",
    @ColumnInfo(defaultValue = "1") val smartEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val smsOptIn: Boolean = false,
    @ColumnInfo(defaultValue = "0") val suggestionNotifications: Boolean = false,
    @ColumnInfo(defaultValue = "'COBALT'") val accent: String = "COBALT",
    @ColumnInfo(defaultValue = "'NEWEST'") val activitySort: String = "NEWEST",
)

@Entity(
    tableName = "outbox_operations",
    indices = [Index(value = ["state", "createdAt"]), Index("entityId")],
)
data class OutboxOperation(
    @PrimaryKey val operationId: String,
    val entityId: String,
    val entityType: String,
    val kind: String,
    val baseRevision: Long,
    val localRevision: Long,
    val payload: String,
    val payloadHash: String,
    val createdAt: Long,
    val payloadSchemaVersion: Int = 1,
    val state: String = "LOCAL_ONLY",
    val profileId: String = "local",
)

data class AccountBalance(@Embedded val account: Account, val balanceMinor: Long)

data class RecordRow(
    @Embedded val record: LedgerRecord,
    val accountName: String,
    val categoryName: String,
    val currency: String,
    val merchantName: String? = null,
    val categoryIcon: String = "tag",
    val categoryColor: String = "BLUE",
    val loggedAt: Long? = null,
)

data class CategoryReport(
    val currency: String,
    val categoryId: String,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val expenseMinor: Long,
    val refundMinor: Long,
    val incomeMinor: Long,
    val entries: Long,
)

data class Summary(val currency: String, val expenseMinor: Long, val incomeMinor: Long)

@Dao
interface LedgerDao {
    @Query(
        "SELECT a.*, a.openingMinor + COALESCE(SUM(CASE WHEN t.deleted = 0 THEN CASE WHEN t.type IN ('INCOME','REFUND','PAYMENT_IN','TRANSFER_IN') THEN t.amountMinor ELSE -t.amountMinor END ELSE 0 END),0) AS balanceMinor FROM accounts a LEFT JOIN transactions t ON a.id = t.accountId WHERE a.profileId = 'local' GROUP BY a.id ORDER BY a.archived, a.name COLLATE NOCASE"
    )
    fun accounts(): Flow<List<AccountBalance>>

    @Query("SELECT * FROM entry_labels ORDER BY name COLLATE NOCASE")
    fun labels(): Flow<List<EntryLabel>>

    @Query("SELECT * FROM entry_labels WHERE id=:id") suspend fun label(id: String): EntryLabel?

    @Insert suspend fun insertLabel(value: EntryLabel)

    @Update suspend fun updateLabel(value: EntryLabel)

    @Insert suspend fun insertCategory(value: Category)

    @Update suspend fun updateCategory(value: Category)

    @Query("SELECT * FROM transactions WHERE linkId=:link")
    suspend fun linked(link: String): List<LedgerRecord>

    @Query("SELECT * FROM categories ORDER BY name") fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM preferences WHERE id = 1") fun preferences(): Flow<Preferences?>

    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: String): Account?

    @Query("SELECT * FROM categories WHERE id = :id") suspend fun category(id: String): Category?

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun record(id: String): LedgerRecord?

    @Query("SELECT * FROM outbox_operations WHERE operationId = :id")
    suspend fun operation(id: String): OutboxOperation?

    @Query("SELECT COUNT(*) FROM transactions") suspend fun recordCount(): Long

    @Query("SELECT COUNT(*) FROM accounts") suspend fun accountCount(): Long

    @Query("SELECT COUNT(*) FROM outbox_operations") suspend fun operationCount(): Long

    @Query(
        "SELECT t.*, a.name AS accountName, c.name AS categoryName, a.currency, m.name AS merchantName, c.icon AS categoryIcon, c.color AS categoryColor, (SELECT MIN(o.createdAt) FROM outbox_operations o WHERE o.entityId=t.id AND o.entityType='TRANSACTION') AS loggedAt FROM transactions t JOIN accounts a ON a.id = t.accountId JOIN categories c ON c.id = t.categoryId LEFT JOIN entry_labels m ON m.id = t.merchantId WHERE t.deleted = 0 AND t.profileId = 'local' ORDER BY t.day DESC, t.id DESC LIMIT 6"
    )
    fun recent(): Flow<List<RecordRow>>

    @Query(
        "SELECT t.*, a.name AS accountName, c.name AS categoryName, a.currency, m.name AS merchantName, c.icon AS categoryIcon, c.color AS categoryColor, (SELECT MIN(o.createdAt) FROM outbox_operations o WHERE o.entityId=t.id AND o.entityType='TRANSACTION') AS loggedAt FROM transactions t JOIN accounts a ON a.id = t.accountId JOIN categories c ON c.id = t.categoryId LEFT JOIN entry_labels m ON m.id = t.merchantId WHERE t.deleted = :deleted AND (NOT :deleted OR t.linkId IS NULL OR t.type IN ('TRANSFER_OUT','PAYMENT_OUT')) AND t.profileId = 'local' AND t.day >= :start AND t.day <= :end AND (:account='' OR t.accountId=:account) AND (:category='' OR t.categoryId=:category) AND (:currency='' OR a.currency=:currency) AND (:filter='ALL' OR (:filter='EXPENSES' AND t.type IN ('EXPENSE','REFUND')) OR (:filter='INCOME' AND t.type='INCOME') OR (:filter='TRANSFERS' AND t.type IN ('TRANSFER_IN','TRANSFER_OUT')) OR (:filter='CARD_PAYMENTS' AND t.type IN ('PAYMENT_IN','PAYMENT_OUT'))) AND (t.note LIKE '%' || :search || '%' ESCAPE '\\' OR c.name LIKE '%' || :search || '%' ESCAPE '\\' OR a.name LIKE '%' || :search || '%' ESCAPE '\\' OR m.name LIKE '%' || :search || '%' ESCAPE '\\') ORDER BY CASE WHEN :sort='OLDEST' THEN t.day END ASC, CASE WHEN :sort='OLDEST' THEN t.id END ASC, t.day DESC, t.id DESC"
    )
    fun page(
        search: String,
        filter: String = "ALL",
        start: Long = Long.MIN_VALUE,
        end: Long = Long.MAX_VALUE,
        account: String = "",
        category: String = "",
        currency: String = "",
        sort: String = "NEWEST",
        deleted: Boolean = false,
    ): PagingSource<Int, RecordRow>

    @Query(
        "SELECT currency,SUM(expenseMinor) AS expenseMinor,SUM(incomeMinor) AS incomeMinor FROM (SELECT a.currency, COALESCE(SUM(CASE WHEN t.type='EXPENSE' THEN COALESCE((SELECT reportMinor FROM group_ledger_links WHERE recordId=t.id),t.amountMinor) WHEN t.type='REFUND' THEN -t.amountMinor ELSE 0 END),0) AS expenseMinor,COALESCE(SUM(CASE WHEN t.type='INCOME' THEN COALESCE((SELECT reportMinor FROM group_ledger_links WHERE recordId=t.id),t.amountMinor) ELSE 0 END),0) AS incomeMinor FROM transactions t JOIN accounts a ON a.id=t.accountId WHERE t.deleted=0 AND t.profileId='local' AND t.day>=:start AND t.day<:end GROUP BY a.currency UNION ALL SELECT g.currency,SUM(s.amountMinor) AS expenseMinor,0 AS incomeMinor FROM group_shares s JOIN group_events e ON e.id=s.eventId JOIN shared_groups g ON g.id=e.groupId WHERE e.kind='EXPENSE' AND s.memberId=g.selfId AND e.day>=:start AND e.day<:end AND NOT EXISTS(SELECT 1 FROM group_events r WHERE r.reversalOf=e.id) GROUP BY g.currency) GROUP BY currency"
    )
    fun summary(start: Long, end: Long): Flow<List<Summary>>

    @Query(
        "SELECT currency,categoryId,categoryName,categoryIcon,categoryColor,SUM(expenseMinor) AS expenseMinor,SUM(refundMinor) AS refundMinor,SUM(incomeMinor) AS incomeMinor,SUM(entries) AS entries FROM (SELECT a.currency,c.id AS categoryId,c.name AS categoryName,c.icon AS categoryIcon,c.color AS categoryColor,SUM(CASE WHEN t.type='EXPENSE' THEN COALESCE((SELECT reportMinor FROM group_ledger_links WHERE recordId=t.id),t.amountMinor) ELSE 0 END) AS expenseMinor,SUM(CASE WHEN t.type='REFUND' THEN t.amountMinor ELSE 0 END) AS refundMinor,SUM(CASE WHEN t.type='INCOME' THEN COALESCE((SELECT reportMinor FROM group_ledger_links WHERE recordId=t.id),t.amountMinor) ELSE 0 END) AS incomeMinor,COUNT(*) AS entries FROM transactions t JOIN accounts a ON a.id=t.accountId JOIN categories c ON c.id=t.categoryId WHERE t.profileId='local' AND t.deleted=0 AND t.type IN ('EXPENSE','REFUND','INCOME') AND t.day>=:start AND t.day<:end GROUP BY a.currency,c.id UNION ALL SELECT g.currency,c.id AS categoryId,c.name AS categoryName,c.icon AS categoryIcon,c.color AS categoryColor,SUM(s.amountMinor) AS expenseMinor,0 AS refundMinor,0 AS incomeMinor,COUNT(*) AS entries FROM group_shares s JOIN group_events e ON e.id=s.eventId JOIN shared_groups g ON g.id=e.groupId JOIN categories c ON c.id=e.categoryId WHERE e.kind='EXPENSE' AND s.memberId=g.selfId AND e.day>=:start AND e.day<:end AND NOT EXISTS(SELECT 1 FROM group_events r WHERE r.reversalOf=e.id) GROUP BY g.currency,c.id) GROUP BY currency,categoryId ORDER BY currency,categoryName COLLATE NOCASE,categoryId"
    )
    fun categoryReport(start: Long, end: Long): Flow<List<CategoryReport>>

    @Query("UPDATE preferences SET activitySort=:value WHERE id=1")
    suspend fun activitySort(value: String)

    @Insert suspend fun insertAccount(account: Account)

    @Update suspend fun updateAccount(account: Account)

    @Insert suspend fun insertRecord(record: LedgerRecord)

    @Update suspend fun updateRecord(record: LedgerRecord)

    @Insert suspend fun insertOperation(operation: OutboxOperation)

    @Query("UPDATE preferences SET theme = :theme WHERE id = 1") suspend fun setTheme(theme: String)

    @Query("UPDATE preferences SET centerAction = :action WHERE id = 1")
    suspend fun setCenterAction(action: String)

    @Query("UPDATE preferences SET balancesHidden = :hidden WHERE id = 1")
    suspend fun setBalancesHidden(hidden: Boolean)

    @Query("UPDATE preferences SET navigationSlots = :slots WHERE id = 1")
    suspend fun setNavigationSlots(slots: String)

    @Query("UPDATE preferences SET defaultCurrency=:value WHERE id=1")
    suspend fun currency(value: String)

    @Query("UPDATE preferences SET displayName=:value WHERE id=1")
    suspend fun displayName(value: String)

    @Query(
        "UPDATE preferences SET defaultAccountId=:account,defaultExpenseCategoryId=:category WHERE id=1"
    )
    suspend fun defaults(account: String, category: String)

    @Query(
        "UPDATE preferences SET smartEnabled=:enabled,smsOptIn=:sms,suggestionNotifications=:notices WHERE id=1"
    )
    suspend fun smartSettings(enabled: Boolean, sms: Boolean, notices: Boolean)

    @Query("UPDATE preferences SET defaultAccountId=:value WHERE id=1")
    suspend fun defaultAccount(value: String)

    @Query("UPDATE preferences SET defaultExpenseCategoryId=:value WHERE id=1")
    suspend fun defaultCategory(value: String)

    @Query("UPDATE preferences SET smartEnabled=:value WHERE id=1")
    suspend fun smartEnabled(value: Boolean)

    @Query("UPDATE preferences SET smsOptIn=:value WHERE id=1") suspend fun smsOptIn(value: Boolean)

    @Query("UPDATE preferences SET suggestionNotifications=:value WHERE id=1")
    suspend fun suggestionNotices(value: Boolean)

    @Query("UPDATE preferences SET accent=:value WHERE id=1") suspend fun accent(value: String)

    @Upsert suspend fun preferences(preferences: Preferences)
}

@Database(
    entities =
        [
            Account::class,
            Category::class,
            LedgerRecord::class,
            Preferences::class,
            OutboxOperation::class,
            GiftCard::class,
            GiftRedemption::class,
            GiftSettings::class,
            GiftNotification::class,
            SmartSuggestion::class,
            EntryLabel::class,
            CardReference::class,
            RecurringRule::class,
            Budget::class,
            SavingsGoal::class,
            GoalMovement::class,
            DebtThread::class,
            DebtMovement::class,
            ExpenseGroup::class,
            GroupMember::class,
            GroupEvent::class,
            GroupShare::class,
            GroupLedgerLink::class,
            CardCycle::class,
            CardStatement::class,
            StatementPayment::class,
            StatementRevision::class,
            PaymentPlan::class,
            PlanPayment::class,
            IgnoredPlan::class,
            FinanceReminderSettings::class,
            FinanceReminderReceipt::class,
        ],
    version = 13,
    exportSchema = true,
)
abstract class TraxosDatabase : RoomDatabase() {
    abstract fun groups(): GroupsDao

    abstract fun obligations(): ObligationsDao

    abstract fun debts(): DebtsDao

    abstract fun plans(): PlansDao

    abstract fun recurring(): RecurringDao

    abstract fun cardReferences(): CardReferenceDao

    abstract fun ledger(): LedgerDao

    abstract fun gifts(): GiftDao

    abstract fun smart(): SmartDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN centerAction TEXT NOT NULL DEFAULT 'ADD_TRANSACTION'"
                    )
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN balancesHidden INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN navigationSlots TEXT NOT NULL DEFAULT 'home,transactions,accounts,settings'"
                    )
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS gift_cards (id TEXT NOT NULL, merchant TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL, secret BLOB NOT NULL, fingerprint TEXT NOT NULL, originalMinor INTEGER, remainingMinor INTEGER, currency TEXT NOT NULL, receivedDay INTEGER NOT NULL, expiryDay INTEGER, partialAllowed INTEGER NOT NULL, redeemed INTEGER NOT NULL, archived INTEGER NOT NULL, deleted INTEGER NOT NULL, manuallyExpired INTEGER NOT NULL, revision INTEGER NOT NULL, createdAt INTEGER NOT NULL, source TEXT NOT NULL, PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_gift_cards_fingerprint ON gift_cards(fingerprint)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_gift_cards_deleted_expiryDay ON gift_cards(deleted, expiryDay)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS gift_redemptions (id TEXT NOT NULL, cardId TEXT NOT NULL, kind TEXT NOT NULL, amountMinor INTEGER, createdAt INTEGER NOT NULL, cardRevision INTEGER NOT NULL, undoesId TEXT, PRIMARY KEY(id), FOREIGN KEY(cardId) REFERENCES gift_cards(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_gift_redemptions_cardId ON gift_redemptions(cardId)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS gift_settings (id INTEGER NOT NULL, reminders INTEGER NOT NULL, intervals TEXT NOT NULL, hideCodes INTEGER NOT NULL, textDetection INTEGER NOT NULL, imageDetection INTEGER NOT NULL, sort TEXT NOT NULL, autoReview INTEGER NOT NULL, PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS gift_notifications (token TEXT NOT NULL, deliveredAt INTEGER NOT NULL, PRIMARY KEY(token))"
                    )
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN defaultCurrency TEXT NOT NULL DEFAULT 'INR'"
                    )
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN displayName TEXT NOT NULL DEFAULT ''"
                    )
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN defaultAccountId TEXT NOT NULL DEFAULT ''"
                    )
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN defaultExpenseCategoryId TEXT NOT NULL DEFAULT ''"
                    )
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN smartEnabled INTEGER NOT NULL DEFAULT 1"
                    )
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN smsOptIn INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN suggestionNotifications INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS smart_suggestions (id TEXT NOT NULL, fingerprint TEXT NOT NULL, kind TEXT NOT NULL, secret BLOB NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_smart_suggestions_fingerprint ON smart_suggestions(fingerprint)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_smart_suggestions_status_createdAt ON smart_suggestions(status,createdAt)"
                    )
                }
            }

        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE accounts ADD COLUMN icon TEXT NOT NULL DEFAULT 'wallet'"
                    )
                    db.execSQL("ALTER TABLE accounts ADD COLUMN color TEXT NOT NULL DEFAULT 'BLUE'")
                    db.execSQL(
                        "ALTER TABLE categories ADD COLUMN archived INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL("ALTER TABLE categories ADD COLUMN icon TEXT NOT NULL DEFAULT 'tag'")
                    db.execSQL(
                        "ALTER TABLE categories ADD COLUMN color TEXT NOT NULL DEFAULT 'BLUE'"
                    )
                    db.execSQL(
                        "ALTER TABLE categories ADD COLUMN revision INTEGER NOT NULL DEFAULT 1"
                    )
                    db.execSQL("ALTER TABLE transactions ADD COLUMN merchantId TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE transactions ADD COLUMN linkId TEXT DEFAULT NULL")
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_transactions_linkId ON transactions(linkId)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS entry_labels (id TEXT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL, kind TEXT NOT NULL, archived INTEGER NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_entry_labels_kind_nameKey ON entry_labels(kind,nameKey)"
                    )
                }
            }

        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN accent TEXT NOT NULL DEFAULT 'COBALT'"
                    )
                }
            }

        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE preferences ADD COLUMN activitySort TEXT NOT NULL DEFAULT 'NEWEST'"
                    )
                }
            }

        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS account_card_secrets (accountId TEXT NOT NULL, payload BLOB NOT NULL, revision INTEGER NOT NULL, operationId TEXT NOT NULL, PRIMARY KEY(accountId), FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                }
            }

        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS recurring_rules (id TEXT NOT NULL, accountId TEXT NOT NULL, categoryId TEXT NOT NULL, type TEXT NOT NULL, amountMinor INTEGER NOT NULL, note TEXT NOT NULL, frequency TEXT NOT NULL, anchorDay INTEGER NOT NULL, nextDay INTEGER NOT NULL, enabled INTEGER NOT NULL, deleted INTEGER NOT NULL, revision INTEGER NOT NULL, lastError TEXT NOT NULL, operationId TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_recurring_rules_accountId ON recurring_rules(accountId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_recurring_rules_categoryId ON recurring_rules(categoryId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_recurring_rules_enabled_deleted_nextDay ON recurring_rules(enabled,deleted,nextDay)"
                    )
                }
            }

        val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS budgets (id TEXT NOT NULL, month TEXT NOT NULL, currency TEXT NOT NULL, categoryId TEXT, limitMinor INTEGER NOT NULL, revision INTEGER NOT NULL, operationId TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_budgets_categoryId ON budgets(categoryId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_budgets_month_currency ON budgets(month,currency)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS savings_goals (id TEXT NOT NULL,name TEXT NOT NULL,currency TEXT NOT NULL,targetMinor INTEGER NOT NULL,savedMinor INTEGER NOT NULL,archived INTEGER NOT NULL,revision INTEGER NOT NULL,operationId TEXT NOT NULL,PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS goal_movements (id TEXT NOT NULL,goalId TEXT NOT NULL,deltaMinor INTEGER NOT NULL,createdAt INTEGER NOT NULL,undoOf TEXT,PRIMARY KEY(id),FOREIGN KEY(goalId) REFERENCES savings_goals(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_goal_movements_goalId_createdAt ON goal_movements(goalId,createdAt)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_goal_movements_undoOf ON goal_movements(undoOf)"
                    )
                }
            }

        val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS debt_threads (id TEXT NOT NULL, person TEXT NOT NULL, direction TEXT NOT NULL, currency TEXT NOT NULL, originalMinor INTEGER NOT NULL, outstandingMinor INTEGER NOT NULL, day INTEGER NOT NULL, dueDay INTEGER, note TEXT NOT NULL, archived INTEGER NOT NULL, revision INTEGER NOT NULL, operationId TEXT NOT NULL, PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS debt_movements (id TEXT NOT NULL, debtId TEXT NOT NULL, deltaMinor INTEGER NOT NULL, kind TEXT NOT NULL, day INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL, undoOf TEXT, PRIMARY KEY(id), FOREIGN KEY(debtId) REFERENCES debt_threads(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_debt_movements_debtId_createdAt ON debt_movements(debtId,createdAt)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_debt_movements_undoOf ON debt_movements(undoOf)"
                    )
                }
            }

        val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `shared_groups` (id TEXT NOT NULL,name TEXT NOT NULL,currency TEXT NOT NULL,selfId TEXT NOT NULL,archived INTEGER NOT NULL,revision INTEGER NOT NULL,operationId TEXT NOT NULL,PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `group_members` (id TEXT NOT NULL,groupId TEXT NOT NULL,name TEXT NOT NULL,archived INTEGER NOT NULL,revision INTEGER NOT NULL,operationId TEXT NOT NULL,PRIMARY KEY(id),FOREIGN KEY(groupId) REFERENCES shared_groups(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_group_members_groupId ON `group_members` (groupId)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `group_events` (id TEXT NOT NULL,groupId TEXT NOT NULL,kind TEXT NOT NULL,payerId TEXT NOT NULL,receiverId TEXT,amountMinor INTEGER NOT NULL,day INTEGER NOT NULL,title TEXT NOT NULL,splitMethod TEXT NOT NULL,createdAt INTEGER NOT NULL,reversalOf TEXT,categoryId TEXT,PRIMARY KEY(id),FOREIGN KEY(groupId) REFERENCES shared_groups(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_group_events_day ON group_events(day)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_group_events_groupId_createdAt ON `group_events` (groupId,createdAt)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_group_events_reversalOf ON `group_events` (reversalOf)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `group_shares` (eventId TEXT NOT NULL,memberId TEXT NOT NULL,amountMinor INTEGER NOT NULL,PRIMARY KEY(eventId,memberId),FOREIGN KEY(eventId) REFERENCES group_events(id) ON UPDATE NO ACTION ON DELETE RESTRICT,FOREIGN KEY(memberId) REFERENCES group_members(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_group_shares_memberId ON `group_shares` (memberId)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `group_ledger_links` (recordId TEXT NOT NULL,eventId TEXT NOT NULL,reportMinor INTEGER NOT NULL,PRIMARY KEY(recordId),FOREIGN KEY(recordId) REFERENCES transactions(id) ON UPDATE NO ACTION ON DELETE RESTRICT,FOREIGN KEY(eventId) REFERENCES group_events(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_group_ledger_links_eventId ON `group_ledger_links` (eventId)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `card_cycles` (accountId TEXT NOT NULL,closingDay INTEGER NOT NULL,dueDays INTEGER NOT NULL,limitMinor INTEGER,minimumMinor INTEGER NOT NULL,fundingId TEXT,reminders INTEGER NOT NULL,offsets TEXT NOT NULL,revision INTEGER NOT NULL,operationId TEXT NOT NULL,PRIMARY KEY(accountId),FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `card_statements` (id TEXT NOT NULL,accountId TEXT NOT NULL,closingDay INTEGER NOT NULL,dueDay INTEGER NOT NULL,amountMinor INTEGER NOT NULL,minimumMinor INTEGER NOT NULL,estimated INTEGER NOT NULL,voided INTEGER NOT NULL,revision INTEGER NOT NULL,operationId TEXT NOT NULL,PRIMARY KEY(id),FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_card_statements_accountId_closingDay ON `card_statements` (accountId,closingDay)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS statement_revisions (id TEXT NOT NULL,statementId TEXT NOT NULL,beforeAmount INTEGER NOT NULL,beforeMinimum INTEGER NOT NULL,beforeDue INTEGER NOT NULL,beforeEstimated INTEGER NOT NULL,reason TEXT NOT NULL,createdAt INTEGER NOT NULL,PRIMARY KEY(id),FOREIGN KEY(statementId) REFERENCES card_statements(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_statement_revisions_statementId ON statement_revisions(statementId)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `statement_payments` (id TEXT NOT NULL,statementId TEXT NOT NULL,recordId TEXT NOT NULL,amountMinor INTEGER NOT NULL,createdAt INTEGER NOT NULL,active INTEGER NOT NULL,PRIMARY KEY(id),FOREIGN KEY(statementId) REFERENCES card_statements(id) ON UPDATE NO ACTION ON DELETE RESTRICT,FOREIGN KEY(recordId) REFERENCES transactions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_statement_payments_statementId ON `statement_payments` (statementId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_statement_payments_recordId ON `statement_payments` (recordId)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `payment_plans` (id TEXT NOT NULL,kind TEXT NOT NULL,name TEXT NOT NULL,accountId TEXT NOT NULL,categoryId TEXT NOT NULL,currency TEXT NOT NULL,amountMinor INTEGER,frequency TEXT NOT NULL,customDays INTEGER NOT NULL,anchorDay INTEGER NOT NULL,nextDay INTEGER NOT NULL,trialEnd INTEGER,endDay INTEGER,status TEXT NOT NULL,reminders INTEGER NOT NULL,offsets TEXT NOT NULL,purchaseId TEXT,installments INTEGER NOT NULL,principalMinor INTEGER NOT NULL,revision INTEGER NOT NULL,operationId TEXT NOT NULL,PRIMARY KEY(id),FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_payment_plans_accountId ON `payment_plans` (accountId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_payment_plans_categoryId ON `payment_plans` (categoryId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_payment_plans_status_nextDay ON `payment_plans` (status,nextDay)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `plan_payments` (id TEXT NOT NULL,planId TEXT NOT NULL,dueDay INTEGER NOT NULL,paidDay INTEGER NOT NULL,amountMinor INTEGER NOT NULL,feeMinor INTEGER NOT NULL,recordId TEXT,createdAt INTEGER NOT NULL,occurrenceKey TEXT NOT NULL,createdRecord INTEGER NOT NULL,voided INTEGER NOT NULL,PRIMARY KEY(id),FOREIGN KEY(planId) REFERENCES payment_plans(id) ON UPDATE NO ACTION ON DELETE RESTRICT,FOREIGN KEY(recordId) REFERENCES transactions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_plan_payments_planId_dueDay ON `plan_payments` (planId,dueDay)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_plan_payments_recordId ON `plan_payments` (recordId)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_plan_payments_occurrenceKey ON plan_payments(occurrenceKey)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `plan_ignored` (key TEXT NOT NULL,PRIMARY KEY(`key`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `finance_reminder_settings` (id INTEGER NOT NULL,enabled INTEGER NOT NULL,offsets TEXT NOT NULL,quietStart INTEGER NOT NULL,quietEnd INTEGER NOT NULL,PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `finance_reminder_receipts` (token TEXT NOT NULL,sentAt INTEGER NOT NULL,PRIMARY KEY(token))"
                    )
                }
            }

        // Only one active occurrence per plan/due date; reversed rows retain immutable identity.
        fun open(context: Context, name: String = "traxos.db"): TraxosDatabase =
            Room.databaseBuilder(context, TraxosDatabase::class.java, name)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                )
                .addCallback(
                    object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            val categories =
                                listOf(
                                    Triple("food", "Food & drink", "EXPENSE"),
                                    Triple("groceries", "Groceries", "EXPENSE"),
                                    Triple("travel", "Transport", "EXPENSE"),
                                    Triple("bills", "Bills", "EXPENSE"),
                                    Triple("other-expense", "Other expense", "EXPENSE"),
                                    Triple("salary", "Salary", "INCOME"),
                                    Triple("other-income", "Other income", "INCOME"),
                                )
                            categories.forEach { (id, name, type) ->
                                db.execSQL(
                                    "INSERT INTO categories (id,name,type) VALUES (?,?,?)",
                                    arrayOf(id, name, type),
                                )
                            }
                            db.execSQL("INSERT INTO preferences (id,theme) VALUES (1,'SYSTEM')")
                        }
                    }
                )
                .build()
    }
}
