@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.traxos.finance.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeScreen(
    state: ReadState.Ready,
    createAccount: () -> Unit,
    openRecord: (String) -> Unit,
    hidden: Boolean = false,
    toggleVisibility: () -> Unit = {},
    search: () -> Unit = {},
    openAccounts: () -> Unit = {},
    openAccount: (String) -> Unit = {},
    visibilityEnabled: Boolean = true,
) {
    val groups = remember(state.accounts) { state.accounts.groupBy { it.account.currency } }
    val summaries =
        remember(groups, state.summary) {
            groups.keys.map { currency ->
                state.summary.firstOrNull { it.currency == currency } ?: Summary(currency, 0, 0)
            }
        }
    val active = remember(state.accounts) { state.accounts.filterNot { it.account.archived } }
    LazyColumn(
        Modifier.fillMaxSize().testTag("dashboard_list"),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "hero") {
            Surface(
                shape = Radius.card,
                color = Ink.navy,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box {
                    BrandWatermark(
                        Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(140.dp)
                    )
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "TOTAL BALANCE",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFC0CDE5),
                            )
                            TraxosIconButton(
                                onClick = toggleVisibility,
                                enabled = visibilityEnabled,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    if (hidden) TraxosIcons.eyeOff else TraxosIcons.eye,
                                    if (hidden) "Show financial amounts"
                                    else "Hide financial amounts",
                                    Modifier.size(21.dp),
                                    tint = Color.White,
                                )
                            }
                        }
                        if (groups.isEmpty()) {
                            Text(
                                "Make room for clarity.",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                "Create your first account to see a balance.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCAD5E9),
                            )
                        } else
                            groups.forEach { (currency, accounts) ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (groups.size > 1)
                                        Text(
                                            currency,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFC0CDE5),
                                        )
                                    MoneyText(
                                        accounts.sumOf { it.balanceMinor },
                                        currency,
                                        large = true,
                                        hidden = hidden,
                                        color = Color.White,
                                    )
                                }
                            }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${state.accounts.size} recorded account${if(state.accounts.size==1) "" else "s"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFCAD5E9),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                LocalDate.now().format(DateTimeFormatter.ofPattern("MMM yyyy")),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFCAD5E9),
                            )
                        }
                        Text(
                            "On this phone only · Not backed up",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC0CDE5),
                        )
                    }
                }
            }
        }
        item(key = "search") { SearchShortcut(search) }
        item(key = "monthly") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "THIS MONTH",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Inflow", summaries, true, hidden, Modifier.weight(1f))
                    MetricCard("Net outflow", summaries, false, hidden, Modifier.weight(1f))
                }
                Surface(
                    shape = Radius.control,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            TraxosIcons.wallet,
                            null,
                            Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Active accounts",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            active.size.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        item(key = "account_header") { SectionHeader("Active accounts", "Manage", openAccounts) }
        if (state.accounts.isEmpty())
            item(key = "account_empty") {
                Message(
                    "Start with an account",
                    "Add cash or a bank account with a known opening balance.",
                    "Create account",
                    createAccount,
                )
            }
        else if (active.isEmpty())
            item(key = "account_archived") {
                Message(
                    "All accounts are archived",
                    "History and balances are retained. Restore an account to add new records.",
                    "Manage accounts",
                    openAccounts,
                )
            }
        else
            item(key = "account_cards") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(active, key = { it.account.id }) { a ->
                        Surface(
                            onClick = { openAccount(a.account.id) },
                            shape = Radius.card,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.width(220.dp),
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        catalogIcon(a.account.icon),
                                        null,
                                        Modifier.size(20.dp),
                                        tint = catalogColor(a.account.color),
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        a.account.currency,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    a.account.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MoneyText(a.balanceMinor, a.account.currency, hidden = hidden)
                                Text(
                                    accountTypeLabel(a.account.type) +
                                        if (a.account.type == "CREDIT_CARD") " · Debt included"
                                        else " account",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        item(key = "activity_header") { SectionHeader("Recent activity", "View all", search) }
        if (state.recent.isEmpty())
            item(key = "activity_empty") {
                Message(
                    "Your ledger starts here",
                    "Record your first expense or income using Add transaction.",
                )
            }
        items(state.recent, key = { "recent:${it.record.id}" }) {
            RecordItem(it, hidden) { openRecord(it.record.id) }
        }
        if (groups.size > 1)
            item(key = "currency_note") {
                Text(
                    "Balances stay separate by currency. No exchange conversion is applied.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun MetricCard(
    title: String,
    summary: List<Summary>,
    income: Boolean,
    hidden: Boolean,
    modifier: Modifier,
) {
    TraxosCard(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (income) TraxosIcons.income else TraxosIcons.expense,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary.isEmpty()) Text("—", style = MaterialTheme.typography.titleLarge)
        summary.forEach { s ->
            if (summary.size > 1)
                Text(
                    s.currency,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            MoneyText(if (income) s.incomeMinor else s.expenseMinor, s.currency, hidden = hidden)
        }
    }
}
