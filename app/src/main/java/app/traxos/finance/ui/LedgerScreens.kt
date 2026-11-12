package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.traxos.finance.data.*
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
                            if (row.record.type == "INCOME") TraxosIcons.income
                            else TraxosIcons.expense,
                            null,
                            Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        row.record.note.ifBlank { row.categoryName },
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
                            prefix = if (row.record.type == "INCOME") "+ " else "− ",
                            hidden = hidden,
                        )
                    }
            }
            if (largeFont)
                MoneyText(
                    row.record.amountMinor,
                    row.currency,
                    prefix = if (row.record.type == "INCOME") "+ " else "− ",
                    hidden = hidden,
                )
            Text(
                LocalDate.ofEpochDay(row.record.day)
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy")) +
                    " · " +
                    if (row.record.type == "INCOME") "Income" else "Expense",
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
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your activity", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TraxosIconButton(onClick = add) { Icon(TraxosIcons.plus, "Add transaction") }
        }
        LedgerSearch(search, vm::search, requester)
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
                        if (search.isBlank()) "Your ledger starts here"
                        else "No matching transactions",
                        if (search.isBlank())
                            "Record an expense or income. All changes stay on this phone."
                        else "Try a different note, account or category.",
                        if (search.isBlank()) "Add transaction" else "Clear search",
                        if (search.isBlank()) add
                        else {
                            { vm.search("") }
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
                            TraxosIcons.wallet,
                            null,
                            Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
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
                    MoneyText(a.balanceMinor, a.account.currency, hidden = hidden)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (a.account.type == "CASH") "Cash" else "Bank") +
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
