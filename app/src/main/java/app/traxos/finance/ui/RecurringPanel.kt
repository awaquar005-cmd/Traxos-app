package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun RecurringPanel(
    repository: RecurringRepository,
    dismiss: () -> Unit,
    changed: () -> Unit,
) {
    val ledger = remember(repository) { LedgerRepository(repository.db) }
    var visibleLimit by rememberSaveable { mutableStateOf(50) }
    val rules by repository.rules.collectAsStateWithLifecycle(emptyList())
    val accounts by repository.db.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    val categories by repository.db.ledger().categories().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<RecurringRule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RecurringRule?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var discardEditor by remember { mutableStateOf(false) }
    var closeAfterDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        message = null
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (e: Exception) {
                error = "Could not update schedules. Your saved entries are kept; retry."
            } finally {
                busy = false
            }
        }
    }
    if (deleting != null)
        TraxosDialog(
            "Delete recurring schedule?",
            "Posted transactions stay in your ledger. No future entries will be generated.",
            "Delete",
            {
                val rule = deleting!!
                deleting = null
                act { repository.state(rule.id, rule.revision, false, true) }
            },
            { deleting = null },
            destructive = true,
            enabled = !busy,
        )
    if (discardEditor)
        TraxosDialog(
            "Discard schedule changes?",
            "Saved schedules and posted entries stay unchanged.",
            "Discard",
            {
                creating = false
                editing = null
                discardEditor = false
                if (closeAfterDiscard) dismiss()
            },
            { discardEditor = false },
            cancelLabel = "Keep editing",
            destructive = true,
        )
    TraxosSheet(
        "Recurring transactions",
        {
            if (!busy) {
                if (creating || editing != null) {
                    closeAfterDiscard = true
                    discardEditor = true
                } else dismiss()
            }
        },
        scrollable = true,
    ) {
        if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
        if (message != null) TraxosNotice(message!!, NoticeKind.SUCCESS)
        if (creating || editing != null) {
            val original = editing
            val editKey = original?.id ?: "new"
            key(editKey) {
                val id = rememberSaveable { original?.id ?: UUID.randomUUID().toString() }
                val operation = rememberSaveable { UUID.randomUUID().toString() }
                var account by rememberSaveable { mutableStateOf(original?.accountId.orEmpty()) }
                var category by rememberSaveable { mutableStateOf(original?.categoryId.orEmpty()) }
                var type by rememberSaveable { mutableStateOf(original?.type ?: "EXPENSE") }
                val currency = accounts.firstOrNull { it.account.id == account }?.account?.currency
                var amount by rememberSaveable {
                    mutableStateOf(
                        original
                            ?.let { r ->
                                accounts
                                    .firstOrNull { it.account.id == r.accountId }
                                    ?.account
                                    ?.currency
                                    ?.let { Money.editable(r.amountMinor, it) }
                            }
                            .orEmpty()
                    )
                }
                var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
                var frequency by rememberSaveable {
                    mutableStateOf(original?.frequency ?: "MONTHLY")
                }
                var date by rememberSaveable {
                    mutableStateOf(
                        original?.nextDay?.let { LocalDate.ofEpochDay(it).toString() }
                            ?: LocalDate.now().toString()
                    )
                }
                var automatic by rememberSaveable { mutableStateOf(original?.enabled ?: false) }
                TraxosNotice(
                    "Automatic entries are estimates you authorize, not proof a bank payment occurred. Due entries catch up offline; background timing is approximate. Pausing or deleting keeps posted history. Avoid importing the same payment again from SMS.",
                    NoticeKind.INFO,
                )
                TraxosSegment(listOf("EXPENSE" to "Expense", "INCOME" to "Income"), type, !busy) {
                    type = it
                    category = ""
                }
                AccountPicker(
                    ledger,
                    account,
                    accounts.filter { type == "EXPENSE" || it.account.type != "CREDIT_CARD" },
                    !busy,
                ) {
                    account = it
                }
                CategoryPicker(ledger, category, categories, type, !busy) { category = it }
                TraxosInput(
                    "Description",
                    note,
                    { note = it.take(120) },
                    enabled = !busy,
                    id = "recurring_description",
                )
                TraxosInput(
                    "Amount · ${currency ?: "choose account"}",
                    amount,
                    { amount = it.take(24) },
                    enabled = !busy,
                    number = true,
                    id = "recurring_amount",
                )
                TraxosSelector(
                    "Repeat",
                    frequency,
                    listOf("DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly"),
                    !busy,
                ) {
                    frequency = it
                }
                TraxosDateField("Next entry date", date, !busy) { date = it }
                TraxosCheckbox(
                    automatic,
                    { automatic = it },
                    "Automatically post due entries",
                    enabled = !busy,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosTextButton(
                        {
                            closeAfterDiscard = false
                            discardEditor = true
                        },
                        Modifier.weight(1f),
                        !busy,
                    ) {
                        Text("Back to schedules")
                    }
                    TraxosButton(
                        {
                            act {
                                val day =
                                    runCatching { LocalDate.parse(date) }
                                        .getOrElse {
                                            throw IllegalArgumentException(
                                                "Use YYYY-MM-DD for next entry"
                                            )
                                        }
                                val anchor =
                                    if (original != null && original.nextDay == day.toEpochDay())
                                        original.anchorDay
                                    else day.dayOfMonth
                                repository.save(
                                    RecurringRule(
                                        id,
                                        account,
                                        category,
                                        type,
                                        Money.parse(amount, currency.orEmpty()),
                                        note,
                                        frequency,
                                        anchor,
                                        day.toEpochDay(),
                                        enabled = automatic,
                                        operationId = operation,
                                    ),
                                    original?.revision ?: 0,
                                )
                                creating = false
                                editing = null
                                message = "Recurring schedule saved"
                                changed()
                            }
                        },
                        Modifier.weight(1f),
                        !busy,
                        busy,
                    ) {
                        Text("Save schedule")
                    }
                }
            }
        } else {
            TraxosNotice(
                "Recurring entries belong to an account. Daily, weekly or monthly; month-end dates keep their original anchor. Card repayments and transfers must use their dedicated accounting actions.",
                NoticeKind.INFO,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactAction(
                    "Add schedule",
                    {
                        creating = true
                        error = null
                    },
                    Modifier.weight(1f),
                    !busy,
                )
                CompactAction(
                    "Post due now",
                    {
                        act {
                            val remaining = repository.runDue()
                            message =
                                if (remaining) "Batch posted; remaining entries are queued"
                                else "Due-entry check complete; review any schedule errors below"
                            if (remaining) changed()
                        }
                    },
                    Modifier.weight(1f),
                    !busy,
                )
            }
            if (rules.isEmpty())
                Text(
                    "No recurring transactions. Add a schedule to get started.",
                    style = MaterialTheme.typography.bodySmall,
                )
            rules.take(visibleLimit).forEach { rule ->
                TraxosCard(Modifier.fillMaxWidth()) {
                    val account = accounts.firstOrNull { it.account.id == rule.accountId }?.account
                    Text(rule.note, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${account?.name ?: "Account unavailable"} · ${rule.type.lowercase()} · ${rule.frequency.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${account?.currency?.let { Money.format(rule.amountMinor,it) }.orEmpty()} · ${if(rule.enabled) "Next" else "Paused · next"} ${LocalDate.ofEpochDay(rule.nextDay)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (rule.lastError.isNotBlank()) TraxosNotice(rule.lastError, NoticeKind.ERROR)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactAction(
                            "Edit",
                            {
                                editing = rule
                                error = null
                            },
                            Modifier.weight(1f),
                            !busy,
                        )
                        CompactAction(
                            if (rule.enabled) "Pause" else "Resume",
                            {
                                act {
                                    repository.state(rule.id, rule.revision, !rule.enabled)
                                    changed()
                                }
                            },
                            Modifier.weight(1f),
                            !busy,
                        )
                        CompactAction("Delete", { deleting = rule }, Modifier.weight(1f), !busy)
                    }
                }
            }
            if (rules.size > visibleLimit)
                TraxosTextButton({ visibleLimit += 50 }) { Text("Load more schedules") }
        }
    }
}
