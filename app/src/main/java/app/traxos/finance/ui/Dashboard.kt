@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    addTransaction: () -> Unit = {},
    voiceEntry: () -> Unit = {},
    repeatEntry: () -> Unit = {},
    report: () -> Unit = {},
    pendingReview: @Composable () -> Unit = {},
    backupCaption: String = "On this phone only · Not backed up",
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
        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            backupCaption,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC0CDE5),
                        )
                    }
                }
            }
        }
        item(key = "search") { SearchShortcut(search) }
        item(key = "monthly") {
            TraxosCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "THIS MONTH",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TraxosTextButton(report) { Text("Report ›") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Inflow",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        summaries.forEach {
                            MoneyText(it.incomeMinor, it.currency, hidden = hidden)
                        }
                        if (summaries.isEmpty()) Text("—")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Net outflow",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        summaries.forEach {
                            MoneyText(it.expenseMinor, it.currency, hidden = hidden)
                        }
                        if (summaries.isEmpty()) Text("—")
                    }
                }
            }
        }
        item(key = "quick_actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosButton(addTransaction, Modifier.weight(1f)) { Text("Add") }
                TraxosTextButton(voiceEntry, Modifier.weight(1f).testTag("home_voice")) {
                    Icon(TraxosIcons.mic, "Voice", Modifier.size(18.dp))
                    Text("Voice")
                }
                TraxosTextButton(repeatEntry, Modifier.weight(1f)) { Text("Repeat") }
            }
        }
        item(key = "smart_review") { pendingReview() }
        item(key = "activity_header") { SectionHeader("Recent activity", "View all", search) }
        item(key = "activity_group") {
            Surface(shape = Radius.card, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column {
                    if (state.recent.isEmpty())
                        Message(
                            "Your ledger starts here",
                            "Record your first expense or income using Add.",
                        )
                    state.recent.forEach { row ->
                        RecordItem(row, hidden) { openRecord(row.record.id) }
                    }
                }
            }
        }
        item(key = "account_header") { SectionHeader("Accounts", "Manage", openAccounts) }
        item(key = "account_group") {
            Surface(shape = Radius.card, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column {
                    if (state.accounts.isEmpty())
                        Message(
                            "Start with an account",
                            "Add cash or a bank account with a known opening balance.",
                            "Create",
                            createAccount,
                        )
                    else if (active.isEmpty())
                        Message(
                            "All accounts are archived",
                            "Your history is retained.",
                            "Manage",
                            openAccounts,
                        )
                    active.take(5).forEach { a ->
                        Surface(
                            onClick = { openAccount(a.account.id) },
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    a.account.name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MoneyText(a.balanceMinor, a.account.currency, hidden = hidden)
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)
                        )
                    }
                }
            }
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

@Composable
internal fun HomeSmartReview(vm: app.traxos.finance.smart.SmartViewModel, open: () -> Unit) {
    val first by vm.homeSuggestion.collectAsStateWithLifecycle()
    val count by vm.pending.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    if (count <= 0 || first == null) return
    val (suggestion, draft) = first!!
    val account =
        accounts
            .filter {
                !it.account.archived &&
                    it.account.currency == draft.currency &&
                    (!draft.cardRequired || it.account.type == "CREDIT_CARD") &&
                    (draft.type != "INCOME" || it.account.type != "CREDIT_CARD")
            }
            .let { eligible ->
                eligible.firstOrNull { it.account.id == preferences?.defaultAccountId }
                    ?: eligible.singleOrNull()
            }
    val category =
        categories
            .filter { !it.archived && it.type == draft.type }
            .let { eligible ->
                eligible.firstOrNull { it.id == preferences?.defaultExpenseCategoryId }
                    ?: eligible.singleOrNull()
            }
    TraxosCard(Modifier.fillMaxWidth()) {
        error?.let { TraxosNotice(it, NoticeKind.ERROR) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$count to review",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
            )
            TraxosTextButton(open) { Text("Review ›") }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                draft.merchant.ifBlank {
                    if (draft.kind == "GIFT") "Gift voucher" else "Bank payment"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val amount =
                runCatching { app.traxos.finance.domain.Money.parse(draft.amount, draft.currency) }
                    .getOrNull()
            if (amount != null)
                MoneyText(amount, draft.currency, hidden = preferences?.balancesHidden != false)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (draft.kind == "GIFT") "Gift code stays masked"
                else
                    "${account?.account?.name ?: "Choose account"} · ${category?.name ?: "Choose category"}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TraxosTextButton({ vm.rejectSuggestion(suggestion) }, enabled = !busy) { Text("Skip") }
            TraxosButton(
                {
                    if (draft.kind == "TRANSACTION" && account != null && category != null)
                        vm.approveSuggestion(suggestion, account.account.id, category.id)
                    else open()
                },
                enabled = !busy,
            ) {
                Text(
                    if (draft.kind == "TRANSACTION" && account != null && category != null) "Add"
                    else "Review"
                )
            }
        }
    }
}
