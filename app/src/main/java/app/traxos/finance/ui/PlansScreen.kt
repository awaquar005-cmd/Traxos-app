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
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
internal fun PlansScreen(
    repo: PlansRepository,
    defaultCurrency: String,
    recurring: () -> Unit,
    hidden: Boolean = false,
    navigate: (String) -> Unit = {},
    changed: () -> Unit = {},
) {
    val extension = remember(repo.db) { PlanningExtensionsRepository(repo.db) }
    val loadedGoalPreferences by extension.dao.goalPreferences().collectAsStateWithLifecycle(null)
    val goalPreferences = loadedGoalPreferences.orEmpty()
    val goalFunding by extension.dao.goalFunding().collectAsStateWithLifecycle(emptyList())
    val reservations by extension.dao.reservations().collectAsStateWithLifecycle(emptyList())
    val accounts by repo.db.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    val rules by extension.dao.budgetRules().collectAsStateWithLifecycle(emptyList())
    val settings by extension.dao.settings().collectAsStateWithLifecycle(PlanningSettings())
    var historyGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var undoGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    val budgets by
        remember(repo) { repo.budgets.map<List<Budget>, List<Budget>?> { it } }
            .collectAsStateWithLifecycle(null)
    val goals by
        remember(repo) { repo.goals.map<List<SavingsGoal>, List<SavingsGoal>?> { it } }
            .collectAsStateWithLifecycle(null)
    val categories by repo.db.ledger().categories().collectAsStateWithLifecycle(emptyList())
    val ledger = remember(repo) { LedgerRepository(repo.db) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val period = YearMonth.parse(month)
    val totals by
        remember(repo, month) {
                repo.db
                    .ledger()
                    .categoryReport(
                        period.atDay(1).toEpochDay(),
                        period.plusMonths(1).atDay(1).toEpochDay(),
                    )
            }
            .collectAsStateWithLifecycle(emptyList())
    var editor by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf<Budget?>(null) }
    var goal by remember { mutableStateOf<SavingsGoal?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                block()
                changed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "Check the entered values"
            } catch (e: Exception) {
                error = "Could not update plans. Saved records are kept; retry."
            } finally {
                busy = false
            }
        }
    }
    historyGoal?.let { GoalHistorySheet(it, { historyGoal = null }, extension, hidden) }
    undoGoal?.let { g ->
        TraxosDialog(
            "Undo linked progress?",
            "Only undo an accidental record. A transfer created with this progress is reversed in the ledger, not by your bank. Already-spent transferred funds cannot be undone.",
            "Undo",
            {
                undoGoal = null
                act { extension.undoGoal(g.id, g.revision, UUID.randomUUID().toString()) }
            },
            { undoGoal = null },
            destructive = true,
            enabled = !busy,
        )
    }
    if (editor == "budget") {
        val original = budget
        var currency by
            rememberSaveable(original?.id) { mutableStateOf(original?.currency ?: defaultCurrency) }
        var selectedMonth by
            rememberSaveable(original?.id) { mutableStateOf(original?.month ?: month) }
        var all by rememberSaveable(original?.id) { mutableStateOf(original?.categoryId == null) }
        var category by
            rememberSaveable(original?.id) { mutableStateOf(original?.categoryId.orEmpty()) }
        var amount by
            rememberSaveable(original?.id) {
                mutableStateOf(
                    original?.let { Money.editable(it.limitMinor, it.currency) }.orEmpty()
                )
            }
        val operation = rememberSaveable(original?.id) { UUID.randomUUID().toString() }
        val rule = rules.firstOrNull { it.budgetId == original?.id }
        var alerts by rememberSaveable(original?.id) { mutableStateOf(rule?.enabled ?: true) }
        var thresholds by
            rememberSaveable(original?.id) {
                mutableStateOf(rule?.thresholds ?: settings?.thresholds ?: "80,100")
            }
        TraxosSheet(
            if (original == null) "New budget" else "Edit budget",
            {
                if (!busy) {
                    editor = ""
                    error = null
                }
            },
            scrollable = true,
        ) {
            Text(
                "A monthly limit, not an expense. Actual spending includes card purchases and refunds; transfers and repayments are excluded.",
                style = MaterialTheme.typography.bodySmall,
            )
            TraxosInput(
                "Month · YYYY-MM",
                selectedMonth,
                { selectedMonth = it.take(7) },
                enabled = !busy && original == null,
                id = "budget_month",
            )
            TraxosSelector(
                "Currency",
                currency,
                Money.currencies.map { it to it },
                !busy && original == null,
            ) {
                currency = it
            }
            TraxosCheckbox(all, { all = it }, "All spending", enabled = !busy && original == null)
            if (!all)
                CategoryPicker(ledger, category, categories, "EXPENSE", !busy && original == null) {
                    category = it
                }
            TraxosInput(
                "Monthly limit",
                amount,
                { amount = it.take(24) },
                number = true,
                enabled = !busy,
                id = "budget_amount",
            )
            TraxosCheckbox(alerts, { alerts = it }, "Threshold alerts", enabled = !busy)
            if (alerts)
                TraxosInput(
                    "Threshold percentages",
                    thresholds,
                    { thresholds = it },
                    enabled = !busy,
                )
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            TraxosButton(
                {
                    act {
                        require(all || category.isNotBlank()) {
                            "Choose a category or All spending"
                        }
                        extension.monthly(
                            selectedMonth,
                            currency,
                            if (all) null else category,
                            Money.parse(amount, currency),
                            original?.revision ?: 0,
                            operation,
                            alerts,
                            thresholds,
                        )
                        editor = ""
                    }
                },
                Modifier.fillMaxWidth().testTag("budget_save"),
                !busy,
                busy,
            ) {
                Text("Save")
            }
        }
    }
    if (editor == "goal" && loadedGoalPreferences != null) {
        val original = goal
        val id = rememberSaveable(original?.id) { original?.id ?: UUID.randomUUID().toString() }
        val operation = rememberSaveable(original?.id) { UUID.randomUUID().toString() }
        var name by rememberSaveable(original?.id) { mutableStateOf(original?.name.orEmpty()) }
        var currency by
            rememberSaveable(original?.id) { mutableStateOf(original?.currency ?: defaultCurrency) }
        var target by
            rememberSaveable(original?.id) {
                mutableStateOf(
                    original?.let { Money.editable(it.targetMinor, it.currency) }.orEmpty()
                )
            }
        val pref = goalPreferences.firstOrNull { it.goalId == original?.id }
        var deadline by
            rememberSaveable(original?.id, pref?.revision) {
                mutableStateOf(
                    pref
                        ?.deadlineDay
                        ?.let { java.time.LocalDate.ofEpochDay(it).toString() }
                        .orEmpty()
                )
            }
        var reminders by
            rememberSaveable(original?.id, pref?.revision) {
                mutableStateOf(pref?.reminders ?: true)
            }
        var offsets by
            rememberSaveable(original?.id, pref?.revision) {
                mutableStateOf(pref?.offsets ?: "7,1,0")
            }
        TraxosSheet(
            if (original == null) "New goal" else "Edit goal",
            {
                if (!busy) {
                    editor = ""
                    error = null
                }
            },
            scrollable = true,
        ) {
            TraxosInput(
                "Goal name",
                name,
                { name = it.take(80) },
                enabled = !busy,
                id = "goal_name",
            )
            TraxosSelector(
                "Currency",
                currency,
                Money.currencies.map { it to it },
                !busy && original == null,
            ) {
                currency = it
            }
            TraxosInput(
                "Target",
                target,
                { target = it.take(24) },
                number = true,
                enabled = !busy,
                id = "goal_target",
            )
            TraxosCheckbox(
                deadline.isBlank(),
                { deadline = if (it) "" else java.time.LocalDate.now().plusMonths(1).toString() },
                "No deadline",
                enabled = !busy,
            )
            if (deadline.isNotBlank()) {
                TraxosDateField("Target date", deadline, !busy) { deadline = it }
                TraxosCheckbox(reminders, { reminders = it }, "Deadline reminders", enabled = !busy)
                if (reminders)
                    TraxosInput("Days before deadline", offsets, { offsets = it }, enabled = !busy)
            }
            Text(
                "Progress is an earmark you record, not an extra asset or a bank transfer.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            TraxosButton(
                {
                    act {
                        extension.goal(
                            id,
                            name,
                            currency,
                            Money.parse(target, currency),
                            original?.revision ?: 0,
                            operation,
                            deadline
                                .takeIf { it.isNotBlank() }
                                ?.let { java.time.LocalDate.parse(it).toEpochDay() },
                            reminders,
                            offsets,
                        )
                        editor = ""
                    }
                },
                Modifier.fillMaxWidth().testTag("goal_save"),
                !busy,
                busy,
            ) {
                Text("Save")
            }
        }
    }
    if (editor in setOf("add_progress", "remove_progress") && goal != null) {
        val selected = goal!!
        var amount by rememberSaveable(selected.id, editor) { mutableStateOf("") }
        val operation = rememberSaveable(selected.id, editor) { UUID.randomUUID().toString() }
        var mode by rememberSaveable(selected.id, editor) { mutableStateOf("MANUAL") }
        var account by rememberSaveable(selected.id, editor) { mutableStateOf("") }
        var otherAccount by rememberSaveable(selected.id, editor) { mutableStateOf("") }
        var transferDate by
            rememberSaveable(selected.id, editor) {
                mutableStateOf(java.time.LocalDate.now().toString())
            }

        TraxosSheet(
            if (editor == "add_progress") "Add progress" else "Remove progress",
            {
                if (!busy) {
                    editor = ""
                    error = null
                }
            },
            scrollable = true,
        ) {
            Text(
                "${selected.name} · ${selected.currency}. Choose whether progress is unlinked, account-backed or recorded with a real transfer.",
                style = MaterialTheme.typography.bodySmall,
            )
            TraxosInput(
                "Amount",
                amount,
                { amount = it.take(24) },
                number = true,
                enabled = !busy,
                id = "goal_progress",
            )
            GoalFundingFields(
                extension,
                selected.currency,
                editor == "add_progress",
                mode,
                account,
                otherAccount,
                transferDate,
                !busy,
                { mode = it },
                { account = it },
                { otherAccount = it },
                { transferDate = it },
            )
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            TraxosButton(
                {
                    val add = editor == "add_progress"
                    act {
                        require(mode == "MANUAL" || account.isNotBlank()) {
                            "Choose a funding account"
                        }
                        require(mode != "TRANSFER" || otherAccount.isNotBlank()) {
                            "Choose the other transfer account"
                        }
                        extension.contribution(
                            selected.id,
                            selected.revision,
                            Money.parse(amount, selected.currency) * (if (add) 1 else -1),
                            if (mode == "MANUAL") null else account,
                            if (mode == "TRANSFER") otherAccount else null,
                            java.time.LocalDate.parse(transferDate).toEpochDay(),
                            operation,
                        )
                        editor = ""
                    }
                },
                Modifier.fillMaxWidth().testTag("goal_progress_save"),
                !busy,
                busy,
            ) {
                Text("Save")
            }
        }
    }
    if (editor == "delete_budget" && budget != null)
        TraxosDialog(
            "Delete this budget?",
            "Only the limit is removed. Transactions and reports remain unchanged.",
            "Delete",
            {
                val b = budget!!
                act {
                    repo.deleteBudget(b.id, b.revision)
                    editor = ""
                }
            },
            { if (!busy) editor = "" },
            destructive = true,
            enabled = !busy,
        )
    LazyColumn(
        Modifier.fillMaxSize().testTag("plans_page"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Make room for plans.", style = MaterialTheme.typography.titleLarge)
            Text(
                "Budgets, savings progress and recurring entries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosTextButton({ navigate("period_budgets") }, Modifier.weight(1f)) {
                    Text("Cycles")
                }
                TraxosTextButton({ navigate("planning_settings") }, Modifier.weight(1f)) {
                    Text("Alerts")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                CompactAction(
                    "Previous",
                    { month = period.minusMonths(1).toString() },
                    Modifier.weight(1f),
                )
                Text(
                    month,
                    Modifier.weight(1f).padding(top = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                CompactAction(
                    "Next",
                    { month = period.plusMonths(1).toString() },
                    Modifier.weight(1f),
                )
            }
        }
        if (error != null && editor.isBlank()) item { TraxosNotice(error!!, NoticeKind.ERROR) }
        item {
            SectionHeader("Monthly budget", "New") {
                budget = null
                error = null
                editor = "budget"
            }
        }
        if (budgets == null || goals == null) item { ScreenSkeleton("accounts") }
        else {
            val current = budgets.orEmpty().filter { it.month == month }
            if (current.isEmpty())
                item {
                    Text(
                        "No budget for this month. Add a limit to compare it with real spending.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            items(current, key = { "budget:${it.id}" }) { b ->
                val spent =
                    totals
                        .filter {
                            it.currency == b.currency &&
                                (b.categoryId == null || it.categoryId == b.categoryId)
                        }
                        .sumOf { it.expenseMinor - it.refundMinor }
                TraxosCard(Modifier.fillMaxWidth()) {
                    Text(
                        b.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
                            ?: "All spending",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Spent", style = MaterialTheme.typography.labelSmall)
                            MoneyText(spent, b.currency, hidden = hidden)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Limit", style = MaterialTheme.typography.labelSmall)
                            MoneyText(b.limitMinor, b.currency, hidden = hidden)
                        }
                    }
                    Text(
                        if (hidden) "Amounts hidden · ${b.currency}"
                        else
                            "${displayMoney(kotlin.math.abs(b.limitMinor-spent),b.currency)} ${if(spent>b.limitMinor) "over budget" else "left"}",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (spent > b.limitMinor) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        CompactAction(
                            "Edit",
                            {
                                budget = b
                                error = null
                                editor = "budget"
                            },
                            Modifier.weight(1f),
                            !busy,
                        )
                        CompactAction(
                            "Delete",
                            {
                                budget = b
                                error = null
                                editor = "delete_budget"
                            },
                            Modifier.weight(1f),
                            !busy,
                        )
                    }
                }
            }
            item {
                SectionHeader("Savings goals", "New") {
                    goal = null
                    error = null
                    editor = "goal"
                }
                Text(
                    "Tracked earmarks are separate from account balances.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val visible = goals.orEmpty().filter { showArchived || !it.archived }
            if (visible.isEmpty())
                item { Text("No savings goals yet.", style = MaterialTheme.typography.bodySmall) }
            items(visible, key = { "goal:${it.id}" }) { g ->
                TraxosCard(Modifier.fillMaxWidth()) {
                    Text(
                        g.name +
                            (if (g.archived) " · Archived"
                            else if (g.savedMinor >= g.targetMinor) " · Reached" else ""),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (hidden) "•••• of •••• · ${g.currency}"
                        else
                            "${displayMoney(g.savedMinor,g.currency)} of ${displayMoney(g.targetMinor,g.currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    goalPreferences
                        .firstOrNull { it.goalId == g.id }
                        ?.deadlineDay
                        ?.let { day ->
                            Text(
                                "Target ${java.time.LocalDate.ofEpochDay(day)}${if(day<java.time.LocalDate.now().toEpochDay() && g.savedMinor<g.targetMinor)" · Overdue" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    val backed = reservations.filter { it.goalId == g.id }.sumOf { it.amountMinor }
                    if (backed != 0L)
                        Text(
                            if (hidden) "Account-backed ••••"
                            else "Account-backed ${displayMoney(backed,g.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    val underfunded =
                        reservations
                            .filter { it.goalId == g.id && it.amountMinor > 0 }
                            .any { r ->
                                val total =
                                    reservations
                                        .filter { it.accountId == r.accountId }
                                        .sumOf { it.amountMinor }
                                (accounts.firstOrNull { it.account.id == r.accountId }?.balanceMinor
                                    ?: 0) < total
                            }
                    if (underfunded)
                        TraxosNotice(
                            "Funding account no longer covers all earmarks. Progress is kept; review the allocations.",
                            NoticeKind.WARNING,
                        )
                    TraxosTextButton({ historyGoal = g }, Modifier.fillMaxWidth()) {
                        Text("History")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!g.archived) {
                            CompactAction(
                                "Add",
                                {
                                    goal = g
                                    error = null
                                    editor = "add_progress"
                                },
                                Modifier.weight(1f),
                                !busy,
                            )
                            CompactAction(
                                "Remove",
                                {
                                    goal = g
                                    error = null
                                    editor = "remove_progress"
                                },
                                Modifier.weight(1f),
                                !busy && g.savedMinor > 0,
                            )
                            CompactAction(
                                "Undo",
                                {
                                    if (
                                        reservations.any {
                                            it.goalId == g.id && it.amountMinor != 0L
                                        }
                                    )
                                        undoGoal = g
                                    else
                                        act {
                                            extension.undoGoal(
                                                g.id,
                                                g.revision,
                                                UUID.randomUUID().toString(),
                                            )
                                        }
                                },
                                Modifier.weight(1f),
                                !busy,
                            )
                            CompactAction(
                                "Edit",
                                {
                                    goal = g
                                    error = null
                                    editor = "goal"
                                },
                                Modifier.weight(1f),
                                !busy,
                            )
                        }
                        CompactAction(
                            if (g.archived) "Restore" else "Archive",
                            { act { repo.archive(g.id, g.revision, !g.archived) } },
                            Modifier.weight(1f),
                            !busy,
                        )
                    }
                }
            }
            item {
                TraxosTextButton({ showArchived = !showArchived }) {
                    Text(if (showArchived) "Hide archived" else "Show archived")
                }
            }
        }
        item {
            SectionHeader("Upcoming entries", "Recurring", recurring)
            Text(
                "Schedules and catch-up remain under Accounts → Recurring.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
