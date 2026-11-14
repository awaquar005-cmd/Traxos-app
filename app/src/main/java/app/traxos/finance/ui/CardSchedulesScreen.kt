package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
internal fun CardSchedulesScreen(
    navigate: (String) -> Unit,
    hidden: Boolean,
    vm: MoneyToolsViewModel = viewModel(),
) {
    val repo = vm.app.obligations
    val accounts by repo.db.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    val cycles by repo.dao.cycles().collectAsStateWithLifecycle(emptyList())
    val revisions by repo.dao.revisions().collectAsStateWithLifecycle(emptyList())
    val allocations by repo.dao.allocations().collectAsStateWithLifecycle(emptyList())
    var allocationHistory by rememberSaveable { mutableStateOf("") }
    var unmatch by rememberSaveable { mutableStateOf("") }
    val statements by repo.dao.statements().collectAsStateWithLifecycle(emptyList())
    val paid by repo.dao.statementPaid().collectAsStateWithLifecycle(emptyList())
    val records by repo.dao.records().collectAsStateWithLifecycle(emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val done by vm.completed.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf("") }
    var payment by rememberSaveable { mutableStateOf("") }
    var void by rememberSaveable { mutableStateOf("") }
    var showVoid by rememberSaveable { mutableStateOf(false) }
    val cards = accounts.filter { it.account.type == "CREDIT_CARD" && !it.account.archived }
    val card = cards.firstOrNull { it.account.id == selected }
    LaunchedEffect(cards) {
        if (card == null) selected = cards.firstOrNull()?.account?.id.orEmpty()
    }
    val cycle = cycles.firstOrNull { it.accountId == selected }
    var observed by rememberSaveable { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done > observed) {
            observed = done
            payment = ""
            void = ""
        }
    }
    if (void.isNotBlank())
        TraxosDialog(
            "Void statement?",
            "Keep its history but stop reminders. Statements with allocated payments cannot be voided.",
            "Void",
            {
                val s = statements.first { it.id == void }
                vm.run { repo.voidStatement(s.id, s.revision) }
            },
            { void = "" },
            destructive = true,
            enabled = !busy,
        )
    if (unmatch.isNotBlank())
        TraxosDialog(
            "Unmatch payment?",
            "Keep the actual bank/card payment. Only its allocation to this statement is removed.",
            "Unmatch",
            {
                val target = unmatch
                unmatch = ""
                vm.run { repo.unmatch(target) }
            },
            { unmatch = "" },
            enabled = !busy,
        )
    val historyStatement = statements.firstOrNull { it.id == allocationHistory }
    if (historyStatement != null)
        TraxosSheet("Statement payments", { allocationHistory = "" }, true) {
            val unit =
                accounts
                    .firstOrNull { it.account.id == historyStatement.accountId }
                    ?.account
                    ?.currency ?: "INR"
            val rows = allocations.filter { it.statementId == historyStatement.id }
            TraxosNotice(
                "Remaining due is based on actual recorded card payments after closing. Historical statements overlap; never add their balances together. Matching below is an audit reference.",
                NoticeKind.INFO,
            )
            TraxosButton(
                {
                    allocationHistory = ""
                    payment = historyStatement.id
                },
                Modifier.fillMaxWidth(),
            ) {
                Text("Match")
            }
            if (rows.isEmpty())
                Text("No payments allocated.", style = MaterialTheme.typography.bodyMedium)
            TraxosTextButton(
                {
                    val target = historyStatement.id
                    allocationHistory = ""
                    navigate("money_edit/STATEMENT_EDIT/$target")
                },
                Modifier.fillMaxWidth(),
                !historyStatement.voided,
            ) {
                Text("Adjust")
            }
            revisions
                .filter { it.statementId == historyStatement.id }
                .forEach { r ->
                    Text(
                        "Previous ${if(hidden)"••••" else Money.format(r.beforeAmount,unit)} · due ${LocalDate.ofEpochDay(r.beforeDue)} · ${r.reason}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            rows.forEach { row ->
                MoneyText(row.amountMinor, unit, hidden = hidden)
                Text(
                    if (row.active) "Linked payment" else "Unmatched · history kept",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (row.active)
                    TraxosTextButton(
                        {
                            allocationHistory = ""
                            unmatch = row.id
                        },
                        Modifier.fillMaxWidth(),
                        !busy,
                    ) {
                        Text("Unmatch")
                    }
            }
        }
    val statement = statements.firstOrNull { it.id == payment }
    if (statement != null && card != null)
        key(statement.id) {
            val remaining =
                PlanDates.remaining(
                    statement,
                    paid.firstOrNull { it.id == statement.id }?.paidMinor ?: 0,
                )
            var amount by rememberSaveable {
                mutableStateOf(Money.editable(remaining, card.account.currency))
            }
            var source by rememberSaveable { mutableStateOf(cycle?.fundingId.orEmpty()) }
            var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
            var mode by rememberSaveable { mutableStateOf(if (remaining > 0) "PAY" else "MATCH") }
            var record by rememberSaveable { mutableStateOf("") }
            val operation = rememberSaveable { UUID.randomUUID().toString() }
            val options =
                records.filter {
                    it.type == "PAYMENT_IN" &&
                        it.accountId == selected &&
                        it.day > statement.closingDay
                }
            TraxosSheet("Statement payment", { if (!busy) payment = "" }, true) {
                Text(
                    "Paying a card is a liability settlement, never another expense.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TraxosSegment(
                    listOf("PAY" to "Pay now", "MATCH" to "Match existing"),
                    mode,
                    !busy,
                ) {
                    mode = it
                }
                if (mode == "PAY")
                    TraxosSelector(
                        "From account",
                        source,
                        accounts
                            .filter {
                                !it.account.archived &&
                                    it.account.type != "CREDIT_CARD" &&
                                    it.account.currency == card.account.currency
                            }
                            .map { it.account.id to it.account.name },
                        !busy,
                    ) {
                        source = it
                    }
                else
                    TraxosSelector(
                        "Card payment",
                        record,
                        options.map {
                            it.id to
                                "${Money.format(it.amountMinor,card.account.currency)} · ${LocalDate.ofEpochDay(it.day)}"
                        },
                        !busy,
                    ) {
                        record = it
                        options
                            .firstOrNull { r -> r.id == it }
                            ?.let { r ->
                                amount =
                                    Money.editable(
                                        minOf(r.amountMinor, statement.amountMinor),
                                        card.account.currency,
                                    )
                                date = LocalDate.ofEpochDay(r.day).toString()
                            }
                    }
                TraxosInput(
                    "Amount · ${card.account.currency}",
                    amount,
                    { amount = it },
                    number = true,
                    enabled = !busy,
                    id = "statement_payment",
                )
                if (mode == "PAY") TraxosDateField("Date", date, !busy) { date = it }
                error?.let { TraxosNotice(it, NoticeKind.ERROR) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosTextButton({ payment = "" }, Modifier.weight(1f), !busy) {
                        Text("Cancel")
                    }
                    TraxosButton(
                        {
                            vm.run {
                                if (mode == "PAY")
                                    repo.pay(
                                        statement.id,
                                        source,
                                        Money.parse(amount, card.account.currency),
                                        LocalDate.parse(date).toEpochDay(),
                                        operation,
                                    )
                                else
                                    repo.allocate(
                                        statement.id,
                                        record,
                                        Money.parse(amount, card.account.currency),
                                        operation,
                                    )
                            }
                        },
                        Modifier.weight(1f),
                        !busy,
                        busy = busy,
                    ) {
                        Text(if (mode == "PAY") "Pay" else "Match")
                    }
                }
            }
        }
    LazyColumn(
        Modifier.fillMaxSize().testTag("card_schedules"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Card statements", style = MaterialTheme.typography.titleLarge)
            Text(
                "Recorded debt, statement dues and upcoming installments.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (cards.isEmpty())
            item {
                TraxosNotice(
                    "Add a credit-card account first. Statements never create available credit as money.",
                    NoticeKind.INFO,
                )
                TraxosButton({ navigate("account/new") }, Modifier.fillMaxWidth()) { Text("Add") }
            }
        if (card != null) {
            item {
                TraxosSelector("Card", selected, cards.map { it.account.id to it.account.name }) {
                    selected = it
                }
            }
            item {
                TraxosCard {
                    Text(
                        "Current recorded outstanding",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    MoneyText(
                        maxOf(0, -card.balanceMinor),
                        card.account.currency,
                        true,
                        hidden = hidden,
                    )
                    if (card.balanceMinor > 0) {
                        Text("Credit balance", style = MaterialTheme.typography.labelSmall)
                        MoneyText(card.balanceMinor, card.account.currency, hidden = hidden)
                    }
                    cycle?.limitMinor?.let { limit ->
                        Text(
                            "Available limit · estimate",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        MoneyText(
                            maxOf(0, limit + card.balanceMinor),
                            card.account.currency,
                            hidden = hidden,
                        )
                        if (-card.balanceMinor > limit)
                            Text(
                                "Recorded debt exceeds the configured limit",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                    }
                    Text(
                        cycle?.let {
                            "Closes on day ${it.closingDay} · due ${it.dueDays} days later"
                        } ?: "Set your bank’s billing cycle before generating statements.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TraxosTextButton(
                            { navigate("money_edit/CYCLE/$selected") },
                            Modifier.weight(1f),
                        ) {
                            Text("Cycle")
                        }
                        TraxosButton(
                            { navigate("money_edit/STATEMENT/$selected") },
                            Modifier.weight(1f),
                        ) {
                            Text("Statement")
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosTextButton({ navigate("money_edit/EMI/new") }, Modifier.weight(1f)) {
                        Text("EMI")
                    }
                    TraxosTextButton({ navigate("subscriptions") }, Modifier.weight(1f)) {
                        Text("Plans")
                    }
                    TraxosTextButton({ navigate("finance_reminders") }, Modifier.weight(1f)) {
                        Text("Reminders")
                    }
                }
            }
            item { TraxosCheckbox(showVoid, { showVoid = it }, "Show voided statements") }
            item { error?.let { TraxosNotice(it, NoticeKind.ERROR) } }
            val filtered =
                statements.filter { it.accountId == selected && (showVoid || !it.voided) }
            if (filtered.isEmpty())
                item {
                    Text(
                        "No statements recorded. Enter the bank’s statement or explicitly generate an estimate.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            items(filtered, key = { it.id }) { s ->
                val allocated = paid.firstOrNull { it.id == s.id }?.paidMinor ?: 0
                val remaining = PlanDates.remaining(s, allocated)
                TraxosCard {
                    Text(
                        "${if(s.estimated)"Estimated" else "Manually entered"} · closes ${LocalDate.ofEpochDay(s.closingDay)}${if(s.voided)" · voided" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Remaining", style = MaterialTheme.typography.labelSmall)
                            MoneyText(remaining, card.account.currency, hidden = hidden)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Minimum left", style = MaterialTheme.typography.labelSmall)
                            MoneyText(
                                PlanDates.minimumRemaining(s, allocated),
                                card.account.currency,
                                hidden = hidden,
                            )
                        }
                    }
                    Text(
                        "Due ${LocalDate.ofEpochDay(s.dueDay)}${if(s.dueDay<LocalDate.now().toEpochDay() && remaining>0)" · Overdue" else if(remaining==0L)" · Paid" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (hidden) "Statement •••• · Paid ••••"
                        else
                            "Statement ${Money.format(s.amountMinor,card.account.currency)} · Payments after close ${Money.format(allocated,card.account.currency)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TraxosTextButton({ allocationHistory = s.id }, Modifier.weight(1f)) {
                            Text("History")
                        }
                        if (!s.voided) {
                            TraxosButton(
                                {
                                    vm.error.value = null
                                    payment = s.id
                                },
                                Modifier.weight(1f),
                                remaining > 0,
                            ) {
                                Text("Pay")
                            }
                            TraxosTextButton(
                                { void = s.id },
                                Modifier.weight(1f),
                                allocated == 0L,
                            ) {
                                Text("Void")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CardScheduleEditor(
    kind: String,
    accountId: String,
    close: () -> Unit,
    vm: MoneyToolsViewModel = viewModel(),
) {
    val repo = vm.app.obligations
    val accounts by repo.db.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    val cycles by repo.dao.cycles().collectAsStateWithLifecycle(null)
    val defaults by
        remember(repo) { repo.dao.reminderSettings().map { it ?: FinanceReminderSettings() } }
            .collectAsStateWithLifecycle(null)
    val a = accounts.firstOrNull { it.account.id == accountId }?.account
    val old = cycles?.firstOrNull { it.accountId == accountId }
    if (a == null || cycles == null || defaults == null) {
        Text("Loading card…", Modifier.padding(16.dp))
        return
    }
    key(accountId) {
        val operation = rememberSaveable { UUID.randomUUID().toString() }
        val entity = rememberSaveable { UUID.randomUUID().toString() }
        val busy by vm.busy.collectAsStateWithLifecycle()
        var closing by rememberSaveable { mutableStateOf((old?.closingDay ?: 1).toString()) }
        var due by rememberSaveable { mutableStateOf((old?.dueDays ?: 20).toString()) }
        var limit by rememberSaveable {
            mutableStateOf(old?.limitMinor?.let { Money.editable(it, a.currency) }.orEmpty())
        }
        var minimum by rememberSaveable {
            mutableStateOf(Money.editable(old?.minimumMinor ?: 0, a.currency))
        }
        var funding by rememberSaveable { mutableStateOf(old?.fundingId.orEmpty()) }
        var reminders by rememberSaveable { mutableStateOf(old?.reminders ?: true) }
        var offsets by rememberSaveable { mutableStateOf(old?.offsets ?: defaults!!.offsets) }
        var date by rememberSaveable {
            mutableStateOf(
                LocalDate.ofEpochDay(
                        PlanDates.closing(YearMonth.now().minusMonths(1), old?.closingDay ?: 1)
                    )
                    .toString()
            )
        }
        var dueDate by rememberSaveable {
            mutableStateOf(LocalDate.parse(date).plusDays((old?.dueDays ?: 20).toLong()).toString())
        }
        var amount by rememberSaveable { mutableStateOf("") }
        var estimated by rememberSaveable { mutableStateOf(false) }
        var estimateBusy by remember { mutableStateOf(false) }
        var estimateError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        MoneyEditor(
            if (kind == "CYCLE") "Card billing cycle" else "New statement",
            vm,
            close,
            {
                vm.run {
                    if (kind == "CYCLE")
                        repo.cycle(
                            CardCycle(
                                accountId,
                                closing.toIntOrNull() ?: 0,
                                due.toIntOrNull() ?: 0,
                                limit
                                    .takeIf { it.isNotBlank() }
                                    ?.let { Money.parse(it, a.currency) },
                                Money.parse(minimum, a.currency, true),
                                funding.takeIf { it.isNotBlank() },
                                reminders,
                                offsets,
                                operationId = operation,
                            ),
                            old?.revision ?: 0,
                        )
                    else
                        repo.statement(
                            CardStatement(
                                entity,
                                accountId,
                                LocalDate.parse(date).toEpochDay(),
                                LocalDate.parse(dueDate).toEpochDay(),
                                Money.parse(amount, a.currency, true),
                                Money.parse(minimum, a.currency, true),
                                estimated,
                                operationId = operation,
                            )
                        )
                }
            },
        ) {
            Text(a.name, style = MaterialTheme.typography.titleMedium)
            if (kind == "CYCLE") {
                TraxosNotice(
                    "Use your bank’s actual terms. Day 29–31 is clamped to month-end. Changing this affects future estimates, never saved statements.",
                    NoticeKind.INFO,
                )
                TraxosInput(
                    "Closing day · 1–31",
                    closing,
                    { closing = it.take(2) },
                    enabled = !busy,
                    number = true,
                    id = "cycle_close",
                )
                TraxosInput(
                    "Due days after close · 1–60",
                    due,
                    { due = it.take(2) },
                    enabled = !busy,
                    number = true,
                )
                TraxosInput(
                    "Credit limit · optional",
                    limit,
                    { limit = it },
                    enabled = !busy,
                    number = true,
                )
                TraxosInput(
                    "Default minimum due · ${a.currency}",
                    minimum,
                    { minimum = it },
                    enabled = !busy,
                    number = true,
                )
                TraxosSelector(
                    "Payment account",
                    funding,
                    listOf("" to "Choose each time") +
                        accounts
                            .filter {
                                !it.account.archived &&
                                    it.account.type != "CREDIT_CARD" &&
                                    it.account.currency == a.currency
                            }
                            .map { it.account.id to it.account.name },
                    !busy,
                ) {
                    funding = it
                }
                TraxosCheckbox(reminders, { reminders = it }, "Bill reminders", enabled = !busy)
                if (reminders)
                    TraxosInput(
                        "Days before due · 0 means due date",
                        offsets,
                        { offsets = it },
                        enabled = !busy,
                    )
            } else {
                TraxosNotice(
                    "Manually enter your bank’s statement, or generate a clearly labelled estimate from recorded transactions. No interest or minimum payment is invented.",
                    NoticeKind.INFO,
                )
                TraxosDateField("Closing date", date, !busy && !estimateBusy) {
                    date = it
                    dueDate = LocalDate.parse(it).plusDays((old?.dueDays ?: 20).toLong()).toString()
                    estimated = false
                }
                TraxosDateField("Due date", dueDate, !busy) { dueDate = it }
                TraxosInput(
                    "Statement amount · ${a.currency}",
                    amount,
                    {
                        amount = it
                        estimated = false
                    },
                    enabled = !busy && !estimateBusy,
                    number = true,
                    id = "statement_amount",
                )
                TraxosInput(
                    "Minimum due · ${a.currency}",
                    minimum,
                    { minimum = it },
                    enabled = !busy,
                    number = true,
                )
                TraxosTextButton(
                    {
                        if (!estimateBusy) {
                            estimateBusy = true
                            estimateError = null
                            scope.launch {
                                try {
                                    amount =
                                        Money.editable(
                                            repo.estimate(
                                                accountId,
                                                LocalDate.parse(date).toEpochDay(),
                                            ),
                                            a.currency,
                                        )
                                    estimated = true
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    estimateError =
                                        "Could not estimate. Check closing date and account opening date."
                                } finally {
                                    estimateBusy = false
                                }
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    !busy && !estimateBusy,
                ) {
                    Text(if (estimateBusy) "Calculating…" else "Estimate")
                }
                estimateError?.let { TraxosNotice(it, NoticeKind.ERROR) }
                if (estimated)
                    Text(
                        "Estimate from your records, not a bank-confirmed statement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
            }
        }
    }
}

@Composable
internal fun CardStatementAdjustmentEditor(
    id: String,
    close: () -> Unit,
    vm: MoneyToolsViewModel = viewModel(),
) {
    val statements by vm.app.obligations.dao.statements().collectAsStateWithLifecycle(emptyList())
    val accounts by vm.app.repository.dao.accounts().collectAsStateWithLifecycle(emptyList())
    val statement = statements.firstOrNull { it.id == id }
    if (statement == null) {
        Text("Loading statement…", Modifier.padding(16.dp))
        return
    }
    val unit =
        accounts.firstOrNull { it.account.id == statement.accountId }?.account?.currency ?: "INR"
    key(statement.id) {
        var amount by rememberSaveable {
            mutableStateOf(Money.editable(statement.amountMinor, unit))
        }
        var minimum by rememberSaveable {
            mutableStateOf(Money.editable(statement.minimumMinor, unit))
        }
        var due by rememberSaveable {
            mutableStateOf(LocalDate.ofEpochDay(statement.dueDay).toString())
        }
        var reason by rememberSaveable { mutableStateOf("") }
        val operation = rememberSaveable { UUID.randomUUID().toString() }
        val busy by vm.busy.collectAsStateWithLifecycle()
        MoneyEditor(
            "Adjust bank statement",
            vm,
            close,
            {
                vm.run {
                    vm.app.obligations.adjustStatement(
                        id,
                        statement.revision,
                        Money.parse(amount, unit, true),
                        Money.parse(minimum, unit, true),
                        LocalDate.parse(due).toEpochDay(),
                        reason,
                        operation,
                    )
                }
            },
        ) {
            TraxosNotice(
                "Use bank-confirmed figures, including any refund adjustment. The previous figures and your reason stay in history. This does not alter card transactions or invent interest.",
                NoticeKind.INFO,
            )
            TraxosInput(
                "Revised statement amount · $unit",
                amount,
                { amount = it },
                enabled = !busy,
                number = true,
            )
            TraxosInput(
                "Revised minimum due · $unit",
                minimum,
                { minimum = it },
                enabled = !busy,
                number = true,
            )
            TraxosDateField("Due date", due, !busy) { due = it }
            TraxosInput("Reason", reason, { reason = it.take(200) }, enabled = !busy)
        }
    }
}
