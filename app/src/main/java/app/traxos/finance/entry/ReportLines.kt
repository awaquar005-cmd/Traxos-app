package app.traxos.finance.entry

import androidx.room.*
import kotlinx.coroutines.flow.Flow

const val REPORT_LINES_SQL =
    "SELECT t.id AS recordId,t.accountId,t.merchantId,a.currency,c.id AS categoryId,c.name AS categoryName,c.icon AS categoryIcon,c.color AS categoryColor,t.day,t.type,t.amountMinor AS totalMinor,CASE WHEN t.type='EXPENSE' THEN COALESCE(l.reportMinor,s.amountMinor,t.amountMinor) ELSE 0 END AS expenseMinor,CASE WHEN t.type='REFUND' THEN COALESCE(s.amountMinor,t.amountMinor) ELSE 0 END AS refundMinor,CASE WHEN t.type='INCOME' THEN COALESCE(l.reportMinor,s.amountMinor,t.amountMinor) ELSE 0 END AS incomeMinor,'LEDGER' AS origin FROM transactions t JOIN accounts a ON a.id=t.accountId LEFT JOIN transaction_splits s ON s.recordId=t.id JOIN categories c ON c.id=COALESCE(s.categoryId,t.categoryId) LEFT JOIN group_ledger_links l ON l.recordId=t.id WHERE t.deleted=0 AND t.profileId='local' AND t.type IN ('EXPENSE','INCOME','REFUND') AND (l.recordId IS NULL OR l.reportMinor!=0) AND NOT EXISTS(SELECT 1 FROM debt_funding d WHERE d.recordId=t.id AND d.active=1) UNION ALL SELECT COALESCE(gt.id,e.id) AS recordId,gt.accountId,gt.merchantId,g.currency,c.id AS categoryId,c.name AS categoryName,c.icon AS categoryIcon,c.color AS categoryColor,e.day,'GROUP' AS type,e.amountMinor AS totalMinor,s.amountMinor AS expenseMinor,0 AS refundMinor,0 AS incomeMinor,'GROUP' AS origin FROM group_shares s JOIN group_events e ON e.id=s.eventId JOIN shared_groups g ON g.id=e.groupId JOIN categories c ON c.id=e.categoryId LEFT JOIN group_ledger_links gl ON gl.eventId=e.id LEFT JOIN transactions gt ON gt.id=gl.recordId WHERE e.kind='EXPENSE' AND s.memberId=g.selfId AND NOT EXISTS(SELECT 1 FROM group_events r WHERE r.reversalOf=e.id)"

@DatabaseView(value = REPORT_LINES_SQL, viewName = "personal_report_lines")
data class PersonalReportLine(
    val recordId: String,
    val accountId: String?,
    val merchantId: String?,
    val currency: String,
    val categoryId: String,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val day: Long,
    val type: String,
    val totalMinor: Long,
    val expenseMinor: Long,
    val refundMinor: Long,
    val incomeMinor: Long,
    val origin: String,
)

data class TrendPoint(
    val day: Long,
    val currency: String,
    val expenseMinor: Long,
    val refundMinor: Long,
    val incomeMinor: Long,
    val entries: Long,
)

data class TrendCategory(
    val categoryId: String,
    val categoryName: String,
    val currency: String,
    val expenseMinor: Long,
    val refundMinor: Long,
    val incomeMinor: Long,
    val entries: Long,
)

const val TREND_FILTER =
    " day>=:start AND day<=:end AND (:account='' OR accountId=:account) AND (:category='' OR categoryId=:category) AND (:merchant='' OR merchantId=:merchant) AND (:currency='' OR currency=:currency) AND totalMinor>=:minimum AND totalMinor<=:maximum AND (:kind='ALL' OR (:kind='EXPENSE' AND type IN ('EXPENSE','REFUND','GROUP')) OR (:kind='INCOME' AND type='INCOME')) AND (:origin='ALL' OR origin=:origin) AND (:receipt='ALL' OR (:receipt='HAS' AND EXISTS(SELECT 1 FROM receipt_attachments r WHERE r.recordId=personal_report_lines.recordId AND r.deleted=0)) OR (:receipt='NONE' AND NOT EXISTS(SELECT 1 FROM receipt_attachments r WHERE r.recordId=personal_report_lines.recordId AND r.deleted=0))) AND (:split='ALL' OR (:split='SPLIT' AND EXISTS(SELECT 1 FROM transaction_splits s WHERE s.recordId=personal_report_lines.recordId)) OR (:split='SINGLE' AND NOT EXISTS(SELECT 1 FROM transaction_splits s WHERE s.recordId=personal_report_lines.recordId)))"

@Dao
interface TrendsDao {
    @Query(
        "SELECT day,currency,SUM(expenseMinor) AS expenseMinor,SUM(refundMinor) AS refundMinor,SUM(incomeMinor) AS incomeMinor,COUNT(DISTINCT recordId) AS entries FROM personal_report_lines WHERE " +
            TREND_FILTER +
            " GROUP BY day,currency ORDER BY day,currency"
    )
    fun points(
        start: Long,
        end: Long,
        account: String = "",
        category: String = "",
        merchant: String = "",
        currency: String = "",
        minimum: Long = 0,
        maximum: Long = 99_999_999_999L,
        kind: String = "ALL",
        origin: String = "ALL",
        receipt: String = "ALL",
        split: String = "ALL",
    ): Flow<List<TrendPoint>>

    @Query(
        "SELECT categoryId,categoryName,currency,SUM(expenseMinor) AS expenseMinor,SUM(refundMinor) AS refundMinor,SUM(incomeMinor) AS incomeMinor,COUNT(DISTINCT recordId) AS entries FROM personal_report_lines WHERE " +
            TREND_FILTER +
            " GROUP BY categoryId,currency ORDER BY currency,expenseMinor-refundMinor DESC,categoryName"
    )
    fun categories(
        start: Long,
        end: Long,
        account: String = "",
        category: String = "",
        merchant: String = "",
        currency: String = "",
        minimum: Long = 0,
        maximum: Long = 99_999_999_999L,
        kind: String = "ALL",
        origin: String = "ALL",
        receipt: String = "ALL",
        split: String = "ALL",
    ): Flow<List<TrendCategory>>
}
