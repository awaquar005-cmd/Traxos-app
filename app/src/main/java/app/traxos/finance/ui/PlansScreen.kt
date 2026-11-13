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
) {
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
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            TraxosButton(
                {
                    act {
                        require(all || category.isNotBlank()) {
                            "Choose a category or All spending"
                        }
                        repo.budget(
                            selectedMonth,
                            currency,
                            if (all) null else category,
                            Money.parse(amount, currency),
                            original?.revision ?: 0,
                            operation,
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
    if (editor == "goal") {
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
            Text(
                "Progress is an earmark you record, not an extra asset or a bank transfer.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            TraxosButton(
                {
                    act {
                        repo.goal(
                            id,
                            name,
                            currency,
                            Money.parse(target, currency),
                            original?.revision ?: 0,
                            operation,
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
                "${selected.name} · ${selected.currency}. This updates tracked progress only. Use Accounts → Transfer for a real movement of money.",
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
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            TraxosButton(
                {
                    val add = editor == "add_progress"
                    act {
                        repo.contribute(
                            selected.id,
                            selected.revision,
                            Money.parse(amount, selected.currency) * (if (add) 1 else -1),
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
                            "${Money.format(kotlin.math.abs(b.limitMinor-spent),b.currency)} ${if(spent>b.limitMinor) "over budget" else "left"}",
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
                            "${Money.format(g.savedMinor,g.currency)} of ${Money.format(g.targetMinor,g.currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
                                    act {
                                        repo.undo(g.id, g.revision, UUID.randomUUID().toString())
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
