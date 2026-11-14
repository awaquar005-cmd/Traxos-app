package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.flow.map

@Composable
internal fun PeriodBudgetsScreen(
    defaultCurrency: String,
    hidden: Boolean,
    navigate: (String) -> Unit,
    vm: MoneyToolsViewModel = viewModel(),
) {
    val repo = vm.app.planning
    val budgets by repo.dao.budgets().collectAsStateWithLifecycle(null)
    val categories by repo.db.ledger().categories().collectAsStateWithLifecycle(emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var asOf by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var archived by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("period_budgets"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader("Budget periods", "Add") { navigate("money_edit/PERIOD/new") }
            Text(
                "Salary cycles or your own exact date range. Limits never create expenses.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { TraxosDateField("Review date", asOf, true) { asOf = it } }
        item {
            TraxosTextButton({ navigate("planning_settings") }, Modifier.fillMaxWidth()) {
                Text("Alerts")
            }
            TraxosCheckbox(archived, { archived = it }, "Show archived")
        }
        item { error?.let { TraxosNotice(it, NoticeKind.ERROR) } }
        if (budgets == null) item { SkeletonBlock(Modifier.fillMaxWidth().height(80.dp)) }
        val visible = budgets.orEmpty().filter { archived || !it.archived }
        if (budgets != null && visible.isEmpty())
            item {
                Text(
                    "No custom or salary-cycle budgets. Your existing monthly budgets remain in Plans.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        items(visible, key = { it.id }) { b ->
            val range = BudgetPeriods.window(b, LocalDate.parse(asOf).toEpochDay())
            val totals by
                remember(b.id, range) {
                        repo.db.ledger().categoryReport(range.start, range.end + 1)
                    }
                    .collectAsStateWithLifecycle(emptyList())
            val spent =
                totals
                    .filter {
                        it.currency == b.currency &&
                            (b.categoryId == null || it.categoryId == b.categoryId)
                    }
                    .sumOf { it.expenseMinor - it.refundMinor }
            TraxosCard {
                if (b.kind == "SALARY")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TraxosTextButton(
                            { asOf = LocalDate.ofEpochDay(range.start - 1).toString() },
                            Modifier.weight(1f),
                            range.start > b.startDay,
                        ) {
                            Text("Previous")
                        }
                        TraxosTextButton(
                            { asOf = LocalDate.ofEpochDay(range.end + 1).toString() },
                            Modifier.weight(1f),
                        ) {
                            Text("Next")
                        }
                    }
                Text(
                    b.name + (if (b.archived) " · Archived" else ""),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${if(b.kind=="SALARY")"Salary day ${b.salaryDay}" else "Custom period"} · ${LocalDate.ofEpochDay(range.start)} – ${LocalDate.ofEpochDay(range.end)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    b.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
                        ?: "All spending",
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Net spending", style = MaterialTheme.typography.labelSmall)
                        MoneyText(spent, b.currency, hidden = hidden)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Limit", style = MaterialTheme.typography.labelSmall)
                        MoneyText(b.limitMinor, b.currency, hidden = hidden)
                    }
                }
                val crossed =
                    BudgetPeriods.thresholds(b.thresholds).filter {
                        BudgetPeriods.reached(spent, b.limitMinor, it)
                    }
                Text(
                    if (hidden) "Amounts hidden · ${b.currency}"
                    else if (spent > b.limitMinor)
                        "${Money.format(spent-b.limitMinor,b.currency)} over budget"
                    else "${Money.format(b.limitMinor-spent,b.currency)} left",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (spent > b.limitMinor) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (crossed.isNotEmpty())
                    Text(
                        "${crossed.last()}% threshold reached",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosTextButton(
                        { navigate("money_edit/PERIOD/${b.id}") },
                        Modifier.weight(1f),
                        !busy,
                    ) {
                        Text("Edit")
                    }
                    TraxosTextButton(
                        { vm.run { repo.archiveBudget(b.id, b.revision, !b.archived) } },
                        Modifier.weight(1f),
                        !busy,
                    ) {
                        Text(if (b.archived) "Restore" else "Archive")
                    }
                }
            }
        }
    }
}

@Composable
internal fun PeriodBudgetEditor(
    id: String,
    currencyDefault: String,
    close: () -> Unit,
    vm: MoneyToolsViewModel = viewModel(),
) {
    val repo = vm.app.planning
    val budgets by repo.dao.budgets().collectAsStateWithLifecycle(null)
    val categories by repo.db.ledger().categories().collectAsStateWithLifecycle(emptyList())
    val defaults by
        remember(repo) { repo.dao.settings().map { it ?: PlanningSettings() } }
            .collectAsStateWithLifecycle(null)
    if (budgets == null || defaults == null) {
        Text("Loading budget…", Modifier.padding(16.dp))
        return
    }
    val old = budgets.orEmpty().firstOrNull { it.id == id }
    if (id != "new" && old == null) {
        Text("Budget not found", Modifier.padding(16.dp))
        return
    }
    key(id) {
        val entity = rememberSaveable { old?.id ?: UUID.randomUUID().toString() }
        val operation = rememberSaveable { UUID.randomUUID().toString() }
        val busy by vm.busy.collectAsStateWithLifecycle()
        var name by rememberSaveable { mutableStateOf(old?.name.orEmpty()) }
        var kind by rememberSaveable { mutableStateOf(old?.kind ?: "SALARY") }
        var currency by rememberSaveable { mutableStateOf(old?.currency ?: currencyDefault) }
        var amount by rememberSaveable {
            mutableStateOf(old?.let { Money.editable(it.limitMinor, it.currency) }.orEmpty())
        }
        var category by rememberSaveable { mutableStateOf(old?.categoryId.orEmpty()) }
        var all by rememberSaveable { mutableStateOf(old?.categoryId == null) }
        var start by rememberSaveable {
            mutableStateOf(
                LocalDate.ofEpochDay(old?.startDay ?: YearMonth.now().atDay(1).toEpochDay())
                    .toString()
            )
        }
        var end by rememberSaveable {
            mutableStateOf(
                LocalDate.ofEpochDay(old?.endDay ?: YearMonth.now().atEndOfMonth().toEpochDay())
                    .toString()
            )
        }
        var salary by rememberSaveable {
            mutableStateOf((old?.salaryDay?.takeIf { it > 0 } ?: 1).toString())
        }
        var alerts by rememberSaveable { mutableStateOf(old?.alerts ?: true) }
        var thresholds by rememberSaveable {
            mutableStateOf(old?.thresholds ?: defaults!!.thresholds)
        }
        MoneyEditor(
            if (old == null) "New budget period" else "Edit budget period",
            vm,
            close,
            {
                vm.run {
                    repo.period(
                        PeriodBudget(
                            entity,
                            name,
                            kind,
                            currency,
                            if (all) null else category,
                            LocalDate.parse(start).toEpochDay(),
                            if (kind == "CUSTOM") LocalDate.parse(end).toEpochDay() else null,
                            if (kind == "SALARY") salary.toIntOrNull() ?: 0 else 0,
                            Money.parse(amount, currency),
                            alerts,
                            thresholds,
                            old?.archived ?: false,
                            "",
                            operationId = operation,
                        ),
                        old?.revision ?: 0,
                    )
                }
            },
        ) {
            TraxosSegment(
                listOf("SALARY" to "Salary cycle", "CUSTOM" to "Custom dates"),
                kind,
                !busy,
            ) {
                kind = it
            }
            TraxosInput("Name", name, { name = it.take(80) }, enabled = !busy, id = "period_name")
            TraxosInput(
                "Limit · $currency",
                amount,
                { amount = it },
                enabled = !busy,
                number = true,
                id = "period_limit",
            )
            TraxosSelector("Currency", currency, Money.currencies.map { it to it }, !busy) {
                currency = it
            }
            TraxosCheckbox(all, { all = it }, "All spending", enabled = !busy)
            if (!all)
                CategoryPicker(vm.app.repository, category, categories, "EXPENSE", !busy) {
                    category = it
                }
            if (kind == "SALARY") {
                TraxosInput(
                    "Salary day · 1–31",
                    salary,
                    {
                        salary = it.take(2)
                        salary
                            .toIntOrNull()
                            ?.takeIf { d -> d in 1..31 }
                            ?.let { d ->
                                var month = YearMonth.now()
                                var date = month.atDay(minOf(d, month.lengthOfMonth()))
                                if (date > LocalDate.now()) {
                                    month = month.minusMonths(1)
                                    date = month.atDay(minOf(d, month.lengthOfMonth()))
                                }
                                start = date.toString()
                            }
                    },
                    enabled = !busy,
                    number = true,
                    id = "salary_day",
                )
                TraxosNotice(
                    "Each cycle starts on your salary day and ends the day before the next one. Short months use their last day; later months return to the chosen day.",
                    NoticeKind.INFO,
                )
            }
            TraxosDateField(
                if (kind == "SALARY") "First salary cycle starts" else "Starts",
                start,
                !busy,
            ) {
                start = it
            }
            if (kind == "CUSTOM") TraxosDateField("Ends · inclusive", end, !busy) { end = it }
            TraxosCheckbox(alerts, { alerts = it }, "Threshold alerts", enabled = !busy)
            if (alerts)
                TraxosInput(
                    "Threshold percentages",
                    thresholds,
                    { thresholds = it },
                    enabled = !busy,
                    id = "period_thresholds",
                )
        }
    }
}

@Composable
internal fun GoalFundingFields(
    repo: PlanningExtensionsRepository,
    currency: String,
    adding: Boolean,
    mode: String,
    account: String,
    other: String,
    date: String,
    enabled: Boolean,
    onMode: (String) -> Unit,
    onAccount: (String) -> Unit,
    onOther: (String) -> Unit,
    onDate: (String) -> Unit,
) {
    val accounts by repo.db.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    val reservations by repo.dao.reservations().collectAsStateWithLifecycle(emptyList())
    TraxosSelector(
        "Progress backing",
        mode,
        listOf(
            "MANUAL" to "Unlinked progress",
            "EARMARK" to "Earmark existing account money",
            "TRANSFER" to "Record transfer and earmark",
        ),
        enabled,
    ) {
        onMode(it)
    }
    if (mode != "MANUAL") {
        AccountPicker(
            LedgerRepository(repo.db),
            account,
            accounts,
            enabled,
            if (adding) "Savings account" else "Funded account",
            currency,
            false,
        ) {
            onAccount(it)
            onOther("")
        }
        accounts
            .firstOrNull { it.account.id == account }
            ?.let { a ->
                val held = reservations.filter { it.accountId == account }.sumOf { it.amountMinor }
                Text(
                    "Available for new allocations: ${Money.format(maxOf(0,a.balanceMinor-held),currency)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        if (mode == "TRANSFER") {
            AccountPicker(
                LedgerRepository(repo.db),
                other,
                accounts.filter { it.account.id != account },
                enabled,
                if (adding) "Transfer from" else "Transfer to",
                currency,
                false,
            ) {
                onOther(it)
            }
            TraxosDateField("Transfer date", date, enabled) { onDate(it) }
        }
    }
    TraxosNotice(
        if (mode == "TRANSFER")
            "Record only an actual move between your own accounts. Both sides are saved together; this is not income or spending. Traxos does not initiate bank payments."
        else if (mode == "EARMARK")
            "Reserve existing money for this goal. Your bank balance is unchanged, and these funds cannot be allocated to another goal again."
        else
            "Unlinked progress is manually tracked. It is not an extra asset or an account transaction.",
        NoticeKind.INFO,
    )
}

@Composable
internal fun GoalHistorySheet(
    goal: SavingsGoal,
    dismiss: () -> Unit,
    repo: PlanningExtensionsRepository,
    hidden: Boolean,
) {
    val history by
        remember(goal.id) { repo.db.plans().movements(goal.id) }
            .collectAsStateWithLifecycle(emptyList())
    val funding by repo.dao.goalFunding().collectAsStateWithLifecycle(emptyList())
    val accounts by repo.db.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    TraxosSheet("Goal history", dismiss, true) {
        Text(goal.name, style = MaterialTheme.typography.titleMedium)
        if (history.isEmpty())
            Text("No progress recorded yet.", style = MaterialTheme.typography.bodySmall)
        history.forEach { m ->
            val link = funding.firstOrNull { it.movementId == m.id }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (m.undoOf != null) "Undo"
                        else if (m.deltaMinor > 0) "Contribution" else "Withdrawal",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        link?.let {
                            accounts
                                .firstOrNull { a -> a.account.id == it.accountId }
                                ?.account
                                ?.name +
                                (if (it.transferId != null) " · transfer" else " · earmark")
                        } ?: "Unlinked progress",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        java.time.Instant.ofEpochMilli(m.createdAt)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .toString(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                MoneyText(m.deltaMinor, goal.currency, hidden = hidden)
            }
        }
        Text(
            "Latest 50 movements shown. Full history stays in your encrypted backup.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun PlanningSettingsScreen(
    navigate: (String) -> Unit,
    vm: MoneyToolsViewModel = viewModel(),
) {
    val settings by
        remember(vm) { vm.app.planning.dao.settings().map { it ?: PlanningSettings() } }
            .collectAsStateWithLifecycle(null)
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val done by vm.completed.collectAsStateWithLifecycle()
    if (settings == null) {
        Text("Loading preferences…", Modifier.padding(16.dp))
        return
    }
    val original = settings!!
    var enabled by rememberSaveable(original) { mutableStateOf(original.budgetAlerts) }
    var thresholds by rememberSaveable(original) { mutableStateOf(original.thresholds) }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Debt & planning alerts", style = MaterialTheme.typography.titleLarge)
        TraxosNotice(
            "Alerts are private and local. They never create repayments, expenses or savings transfers. Android may delay background delivery.",
            NoticeKind.INFO,
        )
        TraxosCheckbox(enabled, { enabled = it }, "Budget threshold alerts", enabled = !busy)
        TraxosInput(
            "Default threshold percentages",
            thresholds,
            { thresholds = it },
            enabled = !busy,
        )
        Text(
            "Defaults apply where a monthly budget has no custom alert rule, and to new budgets. Existing custom rules remain unchanged.",
            style = MaterialTheme.typography.bodySmall,
        )
        error?.let { TraxosNotice(it, NoticeKind.ERROR) }
        TraxosButton(
            {
                vm.run {
                    vm.app.planning.settings(
                        PlanningSettings(budgetAlerts = enabled, thresholds = thresholds)
                    )
                }
            },
            Modifier.fillMaxWidth(),
            !busy,
            busy = busy,
        ) {
            Text("Save")
        }
        if (done > 0)
            Text(
                "Saved on this phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        SettingsRow("Quiet hours & permissions", "Shared with bill, debt and goal reminders") {
            navigate("finance_reminders")
        }
        Text(
            "Debt lead days are set in Debts → Person. Goal lead days are set in Edit goal. Reaching the target or settling the debt stops its reminders.",
            style = MaterialTheme.typography.bodySmall,
        )
        NotificationPermissionRow()
    }
}
