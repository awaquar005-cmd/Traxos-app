package app.traxos.finance.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun RecordItem(row: RecordRow, hidden: Boolean = false, click: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale > 1.3f
    Surface(
        onClick = click,
        shape = Radius.control,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = Radius.control,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .07f),
                ) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            if (row.record.type.startsWith("TRANSFER")) TraxosIcons.transfer
                            else catalogIcon(row.categoryIcon),
                            null,
                            Modifier.size(19.dp),
                            tint = catalogColor(row.categoryColor),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        row.merchantName ?: row.record.note.ifBlank { row.categoryName },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        row.accountName + " · " + row.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!largeFont)
                    Column(Modifier.weight(.85f), horizontalAlignment = Alignment.End) {
                        MoneyText(
                            row.record.amountMinor,
                            row.currency,
                            prefix =
                                if (
                                    row.record.type in
                                        listOf("INCOME", "REFUND", "PAYMENT_IN", "TRANSFER_IN")
                                )
                                    "+ "
                                else "− ",
                            hidden = hidden,
                        )
                    }
            }
            if (largeFont)
                MoneyText(
                    row.record.amountMinor,
                    row.currency,
                    prefix =
                        if (
                            row.record.type in
                                listOf("INCOME", "REFUND", "PAYMENT_IN", "TRANSFER_IN")
                        )
                            "+ "
                        else "− ",
                    hidden = hidden,
                )
            Text(
                LocalDate.ofEpochDay(row.record.day)
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy")) +
                    " · " +
                    when (row.record.type) {
                        "INCOME" -> "Income"
                        "REFUND" -> "Card refund"
                        "PAYMENT_IN",
                        "PAYMENT_OUT" -> "Card payment"
                        "TRANSFER_IN",
                        "TRANSFER_OUT" -> "Own-account transfer"
                        else -> "Expense"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun TransactionsScreen(
    vm: MainViewModel,
    open: (String) -> Unit,
    hidden: Boolean,
    focusSearch: Boolean,
    onFocusHandled: () -> Unit,
    add: () -> Unit,
) {
    val search by vm.search.collectAsStateWithLifecycle()
    val filter by vm.recordFilter.collectAsStateWithLifecycle()
    val scopeValues by vm.scopeValues.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val reads by vm.reads.collectAsStateWithLifecycle()
    val scope = ActivityScope.read(scopeValues)
    var filtering by rememberSaveable { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    val preferenceBusy by vm.busy.collectAsStateWithLifecycle()
    val sort = preferences?.activitySort ?: "NEWEST"
    if (filtering) {
        val ready = reads as? ReadState.Ready
        if (ready != null)
            ActivityFilterSheet(
                scope,
                ready.accounts,
                ready.categories,
                { filtering = false },
                vm::scope,
            )
    }
    if (sorting)
        TraxosSheet("Activity order", { sorting = false }, scrollable = true) {
            listOf("NEWEST" to "Newest first", "OLDEST" to "Oldest first").forEach { (id, label) ->
                TraxosChoiceRow(label, sort == id, enabled = !preferenceBusy) {
                    vm.activitySort(id)
                    sorting = false
                }
            }
            Text(
                "Saved for Activity. Deleted entries always use newest transaction date first.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    val list = vm.pages.collectAsLazyPagingItems()
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            requester.requestFocus()
            keyboard?.show()
            onFocusHandled()
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val largeFont = LocalDensity.current.fontScale > 1.3f
        if (largeFont) Text("Activity", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!largeFont)
                Text(
                    "Activity",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            CompactAction(
                if (scope.active) "Filters · On" else "Filters",
                { filtering = true },
                (if (largeFont) Modifier.weight(1f) else Modifier).testTag("activity_filters"),
                reads is ReadState.Ready,
                scope.active,
            )
            CompactAction(
                if (sort == "OLDEST") "Oldest first" else "Newest first",
                { sorting = true },
                (if (largeFont) Modifier.weight(1f) else Modifier).testTag("activity_sort"),
                preferences != null && !preferenceBusy,
            )
        }
        LedgerSearch(search, vm::search, requester)
        if (scope.active) {
            val ready = reads as? ReadState.Ready
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    listOfNotNull(
                            if (scope.from.isNotBlank() || scope.through.isNotBlank())
                                "${scope.from.ifBlank { "Any date" }} → ${scope.through.ifBlank { "Any date" }}"
                            else null,
                            ready
                                ?.accounts
                                ?.firstOrNull { it.account.id == scope.accountId }
                                ?.account
                                ?.name,
                            ready?.categories?.firstOrNull { it.id == scope.categoryId }?.name,
                            scope.currency.takeIf { it.isNotBlank() },
                        )
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("activity_scope_summary"),
                )
                TraxosIconButton(
                    vm::clearActivityFilters,
                    Modifier.testTag("activity_clear_filters").semantics {
                        contentDescription = "Clear search and filters"
                    },
                ) {
                    Text("×", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                    "ALL" to "All",
                    "EXPENSES" to "Expenses",
                    "INCOME" to "Income",
                    "TRANSFERS" to "Transfers",
                    "CARD_PAYMENTS" to "Card payments",
                )
                .forEach { (id, label) ->
                    CompactAction(label, { vm.recordFilter(id) }, active = filter == id)
                }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (list.loadState.refresh is LoadState.Loading && list.itemCount == 0)
                items(5) { RowSkeleton() }
            if (list.loadState.refresh is LoadState.Error)
                item {
                    Message(
                        "Could not load transactions",
                        "Your local records are unchanged.",
                        "Retry",
                        list::retry,
                    )
                }
            if (list.itemCount == 0 && list.loadState.refresh is LoadState.NotLoading)
                item {
                    Message(
                        if (search.isBlank() && filter == "ALL" && !scope.active)
                            "Your ledger starts here"
                        else "No matching transactions",
                        if (search.isBlank() && filter == "ALL" && !scope.active)
                            "Record an expense or income. All changes stay on this phone."
                        else "Try a different note, account or category.",
                        if (search.isBlank() && filter == "ALL" && !scope.active) "Add transaction"
                        else "Clear search",
                        if (search.isBlank() && filter == "ALL" && !scope.active) add
                        else {
                            { vm.clearActivityFilters() }
                        },
                    )
                }
            items(list.itemCount, key = list.itemKey { it.record.id }) { i ->
                list[i]?.let { r -> RecordItem(r, hidden) { open(r.record.id) } }
            }
            if (list.loadState.append is LoadState.Loading) item { RowSkeleton() }
            if (list.loadState.append is LoadState.Error)
                item { TraxosTextButton(onClick = list::retry) { Text("Retry loading more") } }
        }
    }
}

@Composable
internal fun AccountsScreen(
    accounts: List<AccountBalance>,
    open: (String) -> Unit,
    hidden: Boolean = false,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionHeader("Your accounts", "Add account") { open("new") } }
        if (accounts.isEmpty())
            item {
                Message(
                    "A place for your money",
                    "Create cash or a bank account. Set your known opening balance to start.",
                    "Create account",
                ) {
                    open("new")
                }
            }
        items(accounts, key = { it.account.id }) { a ->
            Surface(
                onClick = { open(a.account.id) },
                shape = Radius.card,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            catalogIcon(a.account.icon),
                            null,
                            Modifier.size(22.dp),
                            tint = catalogColor(a.account.color),
                        )
                        Text(
                            a.account.name,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            a.account.currency,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (a.account.type == "CREDIT_CARD")
                        Text(
                            if (a.balanceMinor < 0) "Amount owed" else "Credit balance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    MoneyText(
                        if (a.account.type == "CREDIT_CARD") kotlin.math.abs(a.balanceMinor)
                        else a.balanceMinor,
                        a.account.currency,
                        hidden = hidden,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            accountTypeLabel(a.account.type) +
                                if (a.account.archived) " · Archived" else " · Active",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            TraxosIcons.arrow,
                            "Edit account",
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Each currency stays separate. Archived accounts retain their history and balances.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
