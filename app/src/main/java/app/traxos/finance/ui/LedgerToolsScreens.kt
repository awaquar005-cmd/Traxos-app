package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.traxos.finance.data.*
import app.traxos.finance.domain.ActivityScope
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
internal fun ActivityFilterSheet(
    initial: ActivityScope,
    accounts: List<AccountBalance>,
    categories: List<Category>,
    close: () -> Unit,
    apply: (ActivityScope) -> Unit,
) {
    var from by rememberSaveable { mutableStateOf(initial.from) }
    var through by rememberSaveable { mutableStateOf(initial.through) }
    var account by rememberSaveable { mutableStateOf(initial.accountId) }
    var category by rememberSaveable { mutableStateOf(initial.categoryId) }
    var currency by rememberSaveable { mutableStateOf(initial.currency) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    TraxosSheet("Filter activity", close) {
        Column(
            Modifier.weight(1f, false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TraxosButton(
                    {
                        from = LocalDate.now().toString()
                        through = from
                        error = null
                    },
                    Modifier.weight(1f),
                    tone = ButtonTone.SECONDARY,
                ) {
                    Text("Today")
                }
                TraxosButton(
                    {
                        val month = YearMonth.now()
                        from = month.atDay(1).toString()
                        through = month.atEndOfMonth().toString()
                        error = null
                    },
                    Modifier.weight(1f),
                    tone = ButtonTone.SECONDARY,
                ) {
                    Text("This month")
                }
            }
            TraxosInput(
                "From · YYYY-MM-DD (optional)",
                from,
                {
                    from = it.take(10)
                    error = null
                },
                id = "filter_from",
            )
            TraxosInput(
                "Through · YYYY-MM-DD (optional)",
                through,
                {
                    through = it.take(10)
                    error = null
                },
                id = "filter_through",
            )
            Text(
                "Both dates are included. Leave blank for any date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TraxosSelector(
                "Account",
                account,
                listOf("" to "All accounts") +
                    accounts.map {
                        it.account.id to
                            "${it.account.name} · ${it.account.currency}${if(it.account.archived) " · Archived" else ""}"
                    },
                onSelect = { account = it },
            )
            TraxosSelector(
                "Category",
                category,
                listOf("" to "All categories") +
                    categories.map {
                        it.id to
                            "${it.name} · ${if(it.type == "INCOME") "Income" else "Expense"}${if(it.archived) " · Archived" else ""}"
                    },
                onSelect = { category = it },
            )
            TraxosSelector(
                "Currency",
                currency,
                listOf("" to "All currencies") + Money.currencies.map { it to it },
                onSelect = { currency = it },
            )
        }
        if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TraxosButton(
                {
                    from = ""
                    through = ""
                    account = ""
                    category = ""
                    currency = ""
                    error = null
                },
                Modifier.weight(1f).testTag("filter_reset"),
                tone = ButtonTone.SECONDARY,
            ) {
                Text("Reset")
            }
            TraxosButton(
                {
                    try {
                        apply(
                            ActivityScope(from.trim(), through.trim(), account, category, currency)
                                .validate()
                        )
                        keyboard?.hide()
                        focus.clearFocus()
                        close()
                    } catch (_: java.time.DateTimeException) {
                        error = "Use valid dates as YYYY-MM-DD"
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    }
                },
                Modifier.weight(1f).testTag("filter_apply"),
            ) {
                Text("Apply filters")
            }
        }
    }
}

@Composable
internal fun DeletedTransactionsScreen(vm: MainViewModel, hidden: Boolean) {
    val list = vm.deletedPages.collectAsLazyPagingItems()
    val search by vm.deletedSearch.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var restoring by remember { mutableStateOf<RecordRow?>(null) }
    val selected = restoring
    if (selected != null)
        TraxosDialog(
            if (selected.record.linkId == null) "Restore this transaction?"
            else "Restore both sides?",
            if (selected.record.linkId == null)
                "The original amount will affect ${selected.accountName} again. Archived references and transaction dates are preserved."
            else
                "Both sides of this movement will affect their original accounts together. Restore history only if both sides are still correct.",
            "Restore",
            {
                restoring = null
                vm.restore(selected)
            },
            { restoring = null },
            enabled = !busy,
        )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("deleted_list"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Recover your history. Linked movements appear once and restore together. Dates below are transaction dates; nothing is automatically purged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LedgerSearch(search, vm::deletedSearch, remember { FocusRequester() })
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (list.loadState.refresh is LoadState.Loading && list.itemCount == 0)
                items(4) { RowSkeleton() }
            if (list.loadState.refresh is LoadState.Error)
                item {
                    Message(
                        "Could not load deleted entries",
                        "Your records are unchanged.",
                        "Retry",
                        list::retry,
                    )
                }
            if (list.itemCount == 0 && list.loadState.refresh is LoadState.NotLoading)
                item {
                    Message(
                        if (search.isBlank()) "Nothing to recover"
                        else "No matching deleted entries",
                        if (search.isBlank())
                            "Deleted transactions will appear here. Gift cards use their own archive and are not included."
                        else "Try another note, account or category.",
                        if (search.isBlank()) null else "Clear search",
                        { vm.deletedSearch("") },
                    )
                }
            items(list.itemCount, key = list.itemKey { it.record.id }) { index ->
                list[index]?.let { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RecordItem(row, hidden) { if (!busy) restoring = row }
                        TraxosButton(
                            { restoring = row },
                            Modifier.fillMaxWidth().testTag("restore_${row.record.id}"),
                            !busy,
                            tone = ButtonTone.SECONDARY,
                        ) {
                            Text(
                                if (row.record.linkId == null) "Restore transaction"
                                else "Restore linked movement"
                            )
                        }
                    }
                }
            }
            if (list.loadState.append is LoadState.Loading) item { RowSkeleton() }
            if (list.loadState.append is LoadState.Error)
                item {
                    TraxosButton(list::retry, tone = ButtonTone.SECONDARY) {
                        Text("Retry more entries")
                    }
                }
        }
    }
}

@Composable
internal fun MonthlyReportsScreen(vm: MainViewModel, hidden: Boolean, activity: () -> Unit) {
    val state by vm.report.collectAsStateWithLifecycle()
    val monthText by vm.reportMonth.collectAsStateWithLifecycle()
    val type by vm.reportType.collectAsStateWithLifecycle()
    val month = YearMonth.parse(monthText)
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        var draft by rememberSaveable { mutableStateOf(monthText) }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        TraxosSheet("Report month", { picking = false }, scrollable = true) {
            TraxosInput(
                "Month · YYYY-MM",
                draft,
                {
                    draft = it.take(7)
                    error = null
                },
                id = "report_month",
            )
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            TraxosButton(
                {
                    draft = YearMonth.now().toString()
                    error = null
                },
                Modifier.fillMaxWidth(),
                tone = ButtonTone.SECONDARY,
            ) {
                Text("This month")
            }
            TraxosButton(
                {
                    try {
                        vm.reportMonth(draft.trim())
                        keyboard?.hide()
                        focus.clearFocus()
                        picking = false
                    } catch (_: java.time.DateTimeException) {
                        error = "Enter a valid month as YYYY-MM"
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    }
                },
                Modifier.fillMaxWidth().testTag("report_month_apply"),
            ) {
                Text("Show month")
            }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("reports_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TraxosIconButton(
                    { vm.reportMonth(month.minusMonths(1).toString()) },
                    modifier =
                        Modifier.testTag("report_previous").semantics {
                            contentDescription = "Previous month"
                        },
                    enabled = month > YearMonth.of(1, 1),
                ) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
                TraxosButton(
                    { picking = true },
                    Modifier.weight(1f).testTag("report_month_select"),
                    tone = ButtonTone.SECONDARY,
                ) {
                    Text(month.format(DateTimeFormatter.ofPattern("MMM uuuu")))
                }
                TraxosIconButton(
                    { vm.reportMonth(month.plusMonths(1).toString()) },
                    modifier =
                        Modifier.testTag("report_next").semantics {
                            contentDescription = "Next month"
                        },
                    enabled = month < YearMonth.of(9999, 12),
                ) {
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        item {
            TraxosSegment(
                listOf("EXPENSES" to "Spending", "INCOME" to "Income"),
                type,
                onSelect = vm::reportType,
            )
        }
        item {
            Text(
                "Calendar month · currencies stay separate. Net spending is purchases minus refunds, not bank cash flow. Transfers, card payments, gift redemption and deleted entries are excluded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (val result = state) {
            ReportReadState.Loading -> items(4) { RowSkeleton() }
            ReportReadState.Failed ->
                item {
                    Message(
                        "Could not load reports",
                        "Your ledger is unchanged.",
                        "Retry",
                        vm::retry,
                    )
                }
            is ReportReadState.Ready -> {
                if (result.rows.isEmpty())
                    item {
                        Message(
                            "No activity this month",
                            "Select another month or record an expense or income.",
                            "Open activity",
                            activity,
                        )
                    }
                result.rows
                    .groupBy { it.currency }
                    .forEach { (currency, rows) ->
                        item(key = "total:$currency") {
                            SettingsGroup(currency) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(Modifier.weight(1f)) {
                                        ReportTotal(
                                            "Income",
                                            rows.sumOf { it.incomeMinor },
                                            currency,
                                            hidden,
                                        )
                                    }
                                    Box(Modifier.weight(1f)) {
                                        ReportTotal(
                                            "Net spending",
                                            rows.sumOf { it.expenseMinor - it.refundMinor },
                                            currency,
                                            hidden,
                                        )
                                    }
                                }
                            }
                        }
                        val categories =
                            rows
                                .filter {
                                    if (type == "INCOME") it.incomeMinor != 0L
                                    else it.expenseMinor != 0L || it.refundMinor != 0L
                                }
                                .sortedWith(
                                    compareByDescending<CategoryReport> {
                                            if (type == "INCOME") it.incomeMinor
                                            else it.expenseMinor - it.refundMinor
                                        }
                                        .thenBy { it.categoryName }
                                )
                        if (categories.isEmpty())
                            item(key = "empty:$currency") {
                                Text(
                                    "No ${if(type == "INCOME") "income" else "spending"} in $currency this month.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        items(categories, key = { "$currency:${it.categoryId}" }) { row ->
                            TraxosButton(
                                {
                                    vm.search("")
                                    vm.recordFilter(type)
                                    vm.scope(
                                        ActivityScope(
                                            month.atDay(1).toString(),
                                            month.atEndOfMonth().toString(),
                                            categoryId = row.categoryId,
                                            currency = currency,
                                        )
                                    )
                                    activity()
                                },
                                Modifier.fillMaxWidth()
                                    .testTag("report_category_${currency}_${row.categoryId}"),
                                tone = ButtonTone.SECONDARY,
                            ) {
                                Icon(
                                    catalogIcon(row.categoryIcon),
                                    null,
                                    Modifier.size(20.dp),
                                    tint = catalogColor(row.categoryColor),
                                )
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        row.categoryName,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    MoneyText(
                                        if (type == "INCOME") row.incomeMinor
                                        else row.expenseMinor - row.refundMinor,
                                        currency,
                                        hidden = hidden,
                                    )
                                    if (type == "EXPENSES" && row.refundMinor != 0L) {
                                        Text(
                                            if (hidden) "Purchases •••• · Refunds ••••"
                                            else
                                                "Purchases ${displayMoney(row.expenseMinor,currency)} · Refunds ${displayMoney(row.refundMinor,currency)}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Text(
                                        "${row.entries} ${if(row.entries == 1L) "entry" else "entries"} · View activity",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(TraxosIcons.arrow, null, Modifier.size(18.dp))
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun ReportTotal(label: String, amount: Long, currency: String, hidden: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyText(amount, currency, hidden = hidden)
    }
}
