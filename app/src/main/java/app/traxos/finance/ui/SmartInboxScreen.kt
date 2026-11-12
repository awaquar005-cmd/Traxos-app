package app.traxos.finance.ui

import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traxos.finance.domain.Money
import app.traxos.finance.smart.*

@Composable
internal fun SmartInboxScreen(
    vm: SmartViewModel,
    incoming: String? = null,
    consumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val rows by vm.items.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("PENDING") }
    val selected by vm.selected.collectAsStateWithLifecycle()
    val d by vm.draft.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var account by remember(selected?.id) { mutableStateOf("") }
    var category by remember(selected?.id) { mutableStateOf("") }
    LaunchedEffect(incoming) {
        if (incoming != null) {
            text = incoming
            vm.offer(incoming)
            consumed()
        }
    }
    LaunchedEffect(selected?.id, prefs?.defaultAccountId, prefs?.defaultExpenseCategoryId) {
        account = prefs?.defaultAccountId.orEmpty()
        category = prefs?.defaultExpenseCategoryId.orEmpty()
    }
    if (selected != null && d != null) {
        val draft = d!!
        TraxosSheet(
            if (draft.kind == "GIFT") "Review gift card" else "Review transaction",
            vm::close,
            scrollable = true,
        ) {
            var sourceVisible by remember(selected?.id) { mutableStateOf(false) }
            TraxosTextButton({ sourceVisible = !sourceVisible }, enabled = !busy) {
                Text(if (sourceVisible) "Hide message" else "View message · may contain codes")
            }
            if (sourceVisible)
                Text(
                    draft.originalText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            if (draft.warning.isNotBlank()) TraxosNotice(draft.warning, NoticeKind.WARNING)
            Text(
                "Only your approval creates a record. Defaults are suggestions; check every field.",
                style = MaterialTheme.typography.bodySmall,
            )
            TraxosInput(
                "Merchant / description",
                draft.merchant,
                { v -> vm.update { it.copy(merchant = v, note = v) } },
                enabled = !busy,
            )
            SavedLabelPicker(vm.ledger, "MERCHANT") { label ->
                vm.update { it.copy(merchant = label.name, note = label.name) }
            }
            if (draft.accountHint.isNotBlank())
                Text(
                    "Account hint: •••• ${draft.accountHint}",
                    style = MaterialTheme.typography.bodySmall,
                )
            if (draft.cardRequired)
                TraxosNotice(
                    "Credit card purchase: select the matching card account, never a cash account.",
                    NoticeKind.INFO,
                )
            if (draft.kind == "GIFT") {
                TraxosInput(
                    "Gift card code",
                    draft.code,
                    { v -> vm.update { it.copy(code = v) } },
                    enabled = !busy,
                    secret = true,
                )
                TraxosSelector(
                    "Voucher type",
                    draft.giftKind,
                    listOf(
                        "STORED_VALUE" to "Stored value",
                        "SINGLE_USE" to "Single use",
                        "DISCOUNT" to "Discount code",
                    ),
                    !busy,
                ) { v ->
                    vm.update { it.copy(giftKind = v) }
                }
                TraxosInput(
                    "PIN (optional)",
                    draft.pin,
                    { v -> vm.update { it.copy(pin = v) } },
                    enabled = !busy,
                    secret = true,
                )
                TraxosInput(
                    "Expiry (optional · YYYY-MM-DD)",
                    draft.expiry,
                    { v -> vm.update { it.copy(expiry = v) } },
                    enabled = !busy,
                )
                TraxosCheckbox(
                    draft.amountIsBalance,
                    { v -> vm.update { it.copy(amountIsBalance = v) } },
                    "This is the confirmed remaining balance",
                    enabled = !busy,
                )
            }
            if (draft.kind != "GIFT" || draft.giftKind != "DISCOUNT") {
                TraxosInput(
                    "Amount",
                    draft.amount,
                    { v -> vm.update { it.copy(amount = v) } },
                    enabled = !busy,
                    number = true,
                    id = "smart_amount",
                )
                TraxosSelector(
                    "Currency",
                    draft.currency,
                    Money.currencies.map { it to it },
                    !busy,
                ) { v ->
                    vm.update { it.copy(currency = v) }
                }
            }
            if (draft.kind == "TRANSFER") {
                TraxosNotice(
                    "Between your own accounts · not income or an expense. Select both sides; card bills use Pay card.",
                    NoticeKind.INFO,
                )
                AccountPicker(
                    vm.ledger,
                    account,
                    accounts,
                    !busy,
                    "From account",
                    draft.currency,
                    false,
                ) {
                    account = it
                }
                AccountPicker(
                    vm.ledger,
                    category,
                    accounts.filter { it.account.id != account },
                    !busy,
                    "To account",
                    draft.currency,
                    false,
                ) {
                    category = it
                }
                TraxosDateField("Transfer date", draft.day, !busy) { v ->
                    vm.update { it.copy(day = v) }
                }
            }
            if (draft.kind == "TRANSACTION") {
                TraxosSegment(
                    if (draft.cardRequired) listOf("EXPENSE" to "Purchase")
                    else listOf("EXPENSE" to "Expense", "INCOME" to "Income"),
                    draft.type,
                    !busy,
                ) { v ->
                    category = ""
                    vm.update { it.copy(type = v) }
                }
                AccountPicker(
                    vm.ledger,
                    account,
                    accounts.filter { !draft.cardRequired || it.account.type == "CREDIT_CARD" },
                    !busy,
                    currency = draft.currency,
                ) {
                    account = it
                }
                CategoryPicker(vm.ledger, category, categories, draft.type, !busy) { category = it }
                TraxosDateField("Date (today if absent)", draft.day, !busy) { v ->
                    vm.update { it.copy(day = v) }
                }
                TraxosInput(
                    "Note",
                    draft.note,
                    { v -> vm.update { it.copy(note = v) } },
                    enabled = !busy,
                )
            }
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            if (selected!!.status == "PENDING") {
                if (draft.kind != "REVIEW_ONLY")
                    TraxosButton(
                        { vm.approve(account, category) },
                        Modifier.fillMaxWidth().testTag("smart_approve"),
                        !busy,
                        busy,
                    ) {
                        Text(
                            if (draft.kind == "GIFT") "Add gift card"
                            else if (draft.kind == "TRANSFER") "Record transfer"
                            else "Add transaction"
                        )
                    }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosTextButton({ vm.reject(false) }, enabled = !busy) { Text("Reject") }
                    TraxosTextButton({ vm.reject(true) }, enabled = !busy) {
                        Text("Ignore this message")
                    }
                }
            } else
                Text("This suggestion is ${selected!!.status.lowercase()}; it cannot post again.")
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("smart_inbox"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Smart Add", style = MaterialTheme.typography.headlineSmall)
            Text(
                "$pending waiting for review · On this phone only",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            TraxosInput(
                "Share or paste a financial message",
                text,
                { text = it.take(24000) },
                multiline = true,
                secret = true,
                id = "smart_text",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosButton(
                    {
                        text =
                            context
                                .getSystemService(ClipboardManager::class.java)
                                .primaryClip
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                ?.take(24000)
                                .orEmpty()
                    },
                    Modifier.weight(1f),
                    !busy,
                    tone = ButtonTone.SECONDARY,
                ) {
                    Text("Paste")
                }
                TraxosButton(
                    { vm.offer(text) },
                    Modifier.weight(1f),
                    !busy && prefs?.smartEnabled == true && text.isNotBlank(),
                    busy,
                ) {
                    Text("Find suggestion")
                }
            }
        }
        item {
            TraxosSelector(
                "Show",
                filter,
                listOf("PENDING" to "Waiting for approval", "ALL" to "All reviewed statuses"),
                !busy,
            ) {
                filter = it
                vm.filter(it)
            }
        }
        if (error != null && selected == null)
            item {
                TraxosNotice(error!!, NoticeKind.ERROR)
                TraxosTextButton(vm::retry) { Text("Retry inbox") }
            }
        if (rows == null) item { ScreenSkeleton("transactions") }
        else if (rows!!.isEmpty())
            item {
                Text(
                    "No suggestions here. Share or paste a bank debit/credit or gift-card message. OTPs, links and personal conversations are ignored.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        items(rows.orEmpty(), key = { it.first.id }) { (s, p) ->
            TraxosCard(Modifier.fillMaxWidth()) {
                Text(
                    when (s.kind) {
                        "GIFT" -> "Gift card / voucher"
                        "TRANSACTION" -> "Bank transaction"
                        else -> "Needs manual matching"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    p.merchant.ifBlank { "Check merchant" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    s.status.lowercase().replace('_', ' '),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (p.kind != "GIFT") {
                    Text(
                        "${p.type.lowercase()} · ${p.currency} ${p.amount.ifBlank { "Amount missing" }} · ${p.day.ifBlank { "Date missing" }}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${p.bankName.ifBlank { "Bank not identified" }} · Account •••• ${p.accountHint.ifBlank { "Unknown" }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (p.reference.isNotBlank())
                        Text(
                            "Reference: ${p.reference}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                } else
                    Text(
                        "${p.currency} ${p.amount.ifBlank { "Value unknown" }} · ${p.expiry.ifBlank { "No detected expiry" }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                val eligible =
                    accounts.filter {
                        !it.account.archived &&
                            it.account.currency == p.currency &&
                            (!p.cardRequired || it.account.type == "CREDIT_CARD")
                    }
                val suggestedAccount =
                    eligible.firstOrNull { it.account.id == prefs?.defaultAccountId }
                        ?: eligible.singleOrNull()
                val suggestedCategory =
                    categories.firstOrNull {
                        !it.archived &&
                            it.type == p.type &&
                            it.id == prefs?.defaultExpenseCategoryId
                    } ?: categories.filter { !it.archived && it.type == p.type }.singleOrNull()
                if (p.kind == "TRANSACTION")
                    Text(
                        "Save to: ${suggestedAccount?.account?.name ?: "Choose account in Edit"} · ${suggestedCategory?.name ?: "Choose category in Edit"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                if (p.warning.isNotBlank())
                    Text(
                        p.warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                // Secrets remain masked; review/edit is the only action opening a sheet.
                if (s.status == "PENDING")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactAction(
                            "Approve",
                            {
                                vm.approveSuggestion(
                                    s,
                                    suggestedAccount?.account?.id.orEmpty(),
                                    suggestedCategory?.id.orEmpty(),
                                )
                            },
                            Modifier.weight(1f).testTag("approve_${s.id}"),
                            !busy && p.kind in listOf("TRANSACTION", "GIFT"),
                        )
                        CompactAction(
                            "Reject",
                            { vm.rejectSuggestion(s) },
                            Modifier.weight(1f),
                            !busy,
                        )
                        CompactAction("Edit", { vm.select(s) }, Modifier.weight(1f), !busy)
                    }
                else TraxosTextButton({ vm.select(s) }, enabled = !busy) { Text("View status") }
            }
        }
        if (rows.orEmpty().size >= 200)
            item {
                TraxosButton(
                    vm::more,
                    Modifier.fillMaxWidth(),
                    !busy,
                    tone = ButtonTone.SECONDARY,
                ) {
                    Text("Load older suggestions")
                }
            }
        item {
            Text(
                "Automatic SMS access requires a reviewed, eligible build and your separate permission. Sharing/pasting works without SMS access or internet.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
