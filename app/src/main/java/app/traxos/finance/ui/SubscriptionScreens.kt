package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import java.util.UUID
import kotlinx.coroutines.flow.map

@Composable
internal fun SubscriptionsScreen(
    navigate: (String) -> Unit,
    hidden: Boolean,
    vm: MoneyToolsViewModel = viewModel(),
) {
    val repo = vm.app.obligations
    val snapshots by repo.dao.planSnapshots().collectAsStateWithLifecycle(null)
    val plans = snapshots?.map { it.plan }
    val history = snapshots.orEmpty().flatMap { it.payments }.sortedByDescending { it.createdAt }
    val accounts by repo.db.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    val records by repo.dao.records().collectAsStateWithLifecycle(emptyList())
    val ignored by repo.dao.ignored().collectAsStateWithLifecycle(emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val done by vm.completed.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("ACTIVE") }
    var detail by rememberSaveable { mutableStateOf("") }
    var payment by rememberSaveable { mutableStateOf("") }
    var detected by rememberSaveable { mutableStateOf(false) }
    var undo by rememberSaveable { mutableStateOf("") }
    var observed by rememberSaveable { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done > observed) {
            observed = done
            payment = ""
            undo = ""
        }
    }
    val selected = plans.orEmpty().firstOrNull { it.id == payment }
    if (selected != null)
        key(selected.id) {
            val completed = history.count { it.planId == selected.id && !it.voided }
            val emi = selected.kind == "EMI"
            var amount by rememberSaveable {
                mutableStateOf(
                    if (emi)
                        Money.editable(
                            PlanDates.installment(selected, completed),
                            selected.currency,
                        )
                    else
                        selected.amountMinor
                            ?.let { Money.editable(it, selected.currency) }
                            .orEmpty()
                )
            }
            var fee by rememberSaveable { mutableStateOf("0") }
            var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
            var match by rememberSaveable { mutableStateOf("") }
            var mode by rememberSaveable { mutableStateOf("NEW") }
            val operation = rememberSaveable { UUID.randomUUID().toString() }
            val candidates =
                records.filter {
                    it.accountId == selected.accountId &&
                        it.type == "EXPENSE" &&
                        it.linkId == null &&
                        history.none { p -> p.recordId == it.id && !p.voided }
                }
            TraxosSheet(
                if (emi) "Record installment" else "Confirm payment",
                { if (!busy) payment = "" },
                true,
            ) {
                Text(selected.name, style = MaterialTheme.typography.titleMedium)
                TraxosNotice(
                    if (emi)
                        "Principal was already booked with the original purchase. Recording this installment does not add the principal as another expense or pay your card bill. Only an actual fee creates a new expense."
                    else
                        "Confirm an actual payment, or match an existing expense to avoid duplication. Upcoming dates alone never create transactions.",
                    NoticeKind.INFO,
                )
                if (!emi)
                    TraxosSegment(
                        listOf("NEW" to "New expense", "MATCH" to "Match existing"),
                        mode,
                        !busy,
                    ) {
                        mode = it
                    }
                if (mode == "MATCH" && !emi)
                    TraxosSelector(
                        "Expense",
                        match,
                        candidates.map {
                            it.id to
                                "${it.note} · ${displayMoney(it.amountMinor,selected.currency)} · ${displayDate(it.day)}"
                        },
                        !busy,
                    ) {
                        match = it
                        candidates
                            .firstOrNull { r -> r.id == it }
                            ?.let { r ->
                                amount = Money.editable(r.amountMinor, selected.currency)
                                date = LocalDate.ofEpochDay(r.day).toString()
                            }
                    }
                TraxosInput(
                    if (emi) "Scheduled principal · ${selected.currency}"
                    else "Actual amount · ${selected.currency}",
                    amount,
                    { amount = it },
                    enabled = !busy && !emi && mode != "MATCH",
                    number = true,
                    id = "plan_paid",
                )
                if (emi)
                    TraxosInput(
                        "Actual installment fee · optional",
                        fee,
                        { fee = it },
                        enabled = !busy,
                        number = true,
                    )
                TraxosDateField("Paid date", date, !busy && mode != "MATCH") { date = it }
                error?.let { TraxosNotice(it, NoticeKind.ERROR) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosTextButton({ payment = "" }, Modifier.weight(1f), !busy) {
                        Text("Cancel")
                    }
                    TraxosButton(
                        {
                            vm.run {
                                repo.confirm(
                                    selected.id,
                                    selected.revision,
                                    Money.parse(amount, selected.currency),
                                    LocalDate.parse(date).toEpochDay(),
                                    match.takeIf { mode == "MATCH" && !emi },
                                    if (emi) Money.parse(fee, selected.currency, true) else 0,
                                    operation,
                                )
                            }
                        },
                        Modifier.weight(1f),
                        !busy,
                        busy = busy,
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    if (undo.isNotBlank()) {
        val row = history.firstOrNull { it.id == undo }
        val plan = plans.orEmpty().firstOrNull { it.id == row?.planId }
        if (row != null && plan != null)
            TraxosDialog(
                "Undo payment?",
                "Restore the due occurrence. An expense created by this plan is tombstoned; a matched pre-existing transaction is kept. History remains visible.",
                "Undo",
                { vm.run { repo.undoPayment(row.id, plan.revision) } },
                { undo = "" },
                destructive = true,
                enabled = !busy,
            )
    }
    val item = plans.orEmpty().firstOrNull { it.id == detail }
    if (item != null)
        TraxosSheet(item.name, { detail = "" }, true) {
            Text(
                "${item.kind.lowercase()} · ${item.frequency.lowercase()} · ${item.status.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Next ${displayDate(item.nextDay)}${item.trialEnd?.let{" · Trial ends ${displayDate(it)}"}.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                accounts.firstOrNull { it.account.id == item.accountId }?.account?.name.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
            )
            item.amountMinor?.let { MoneyText(it, item.currency, hidden = hidden) }
                ?: Text("Variable · confirm actual amount")
            val past = history.filter { it.planId == item.id }
            val latest = past.filter { !it.voided }.maxByOrNull { it.dueDay }
            if (item.kind == "EMI")
                Text(
                    "${past.count{!it.voided}} / ${item.installments} installments · original principal ${if(hidden)"••••" else displayMoney(item.principalMinor,item.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosTextButton(
                    {
                        detail = ""
                        navigate("money_edit/PLAN/${item.id}")
                    },
                    Modifier.weight(1f),
                    !busy,
                ) {
                    Text("Edit")
                }
                TraxosTextButton(
                    {
                        vm.run {
                            repo.status(
                                item.id,
                                item.revision,
                                if (item.status == "ACTIVE") "PAUSED" else "ACTIVE",
                            )
                        }
                    },
                    Modifier.weight(1f),
                    !busy && item.status != "ENDED",
                ) {
                    Text(if (item.status == "ACTIVE") "Pause" else "Resume")
                }
                TraxosTextButton(
                    { vm.run { repo.status(item.id, item.revision, "CANCELLED") } },
                    Modifier.weight(1f),
                    !busy && item.status !in listOf("CANCELLED", "ENDED"),
                ) {
                    Text("Cancel")
                }
            }
            error?.let { TraxosNotice(it, NoticeKind.ERROR) }
            Text("PAYMENT HISTORY", style = MaterialTheme.typography.labelSmall)
            if (past.isEmpty())
                Text("No confirmed payments.", style = MaterialTheme.typography.bodySmall)
            past.take(100).forEach { p ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${displayDate(p.paidDay)} · due ${displayDate(p.dueDay)}${if(p.voided)" · undone" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    MoneyText(p.amountMinor, item.currency, hidden = hidden)
                    if (p.feeMinor > 0)
                        Text(
                            if (hidden) "Fee ••••"
                            else "Fee ${displayMoney(p.feeMinor,item.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    if (latest?.id == p.id)
                        TraxosTextButton(
                            {
                                detail = ""
                                undo = p.id
                            },
                            Modifier.fillMaxWidth(),
                            !busy,
                        ) {
                            Text("Undo")
                        }
                }
            }
        }
    val suggestions =
        remember(records, accounts, ignored, plans) {
            PlanDates.detect(records, accounts).filter { suggestion ->
                ignored.none { it.key == suggestion.key } &&
                    plans.orEmpty().none { p ->
                        p.accountId == suggestion.accountId && p.name.equals(suggestion.name, true)
                    }
            }
        }
    LazyColumn(
        Modifier.fillMaxSize().testTag("subscription_wallet"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader("Subscriptions & bills", "Add") {
                navigate("money_edit/SUBSCRIPTION/new")
            }
        }
        item {
            TraxosNotice(
                "Upcoming commitments are not paid expenses. Confirm or match actual payments; pause the old auto-posting recurring rule if it covers the same bill.",
                NoticeKind.INFO,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosTextButton({ navigate("money_edit/BILL/new") }, Modifier.weight(1f)) {
                    Text("Bill")
                }
                TraxosTextButton({ navigate("money_edit/EMI/new") }, Modifier.weight(1f)) {
                    Text("EMI")
                }
                TraxosTextButton({ detected = !detected }, Modifier.weight(1f)) { Text("Detect") }
                TraxosTextButton({ navigate("finance_reminders") }, Modifier.weight(1f)) {
                    Text("Alerts")
                }
            }
        }
        if (detected) {
            item {
                Text(
                    "SUGGESTIONS · ON-DEVICE LEDGER HISTORY",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (suggestions.isEmpty())
                item {
                    Text(
                        "No repeated monthly, quarterly or yearly patterns found. At least three approved ledger payments on different dates are required.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            items(suggestions, key = { "suggestion_${it.key}" }) { s ->
                TraxosCard {
                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${s.evidence} payments · ${s.frequency.lowercase()} · ${if(s.amount==null)"variable amount" else if(hidden)"••••" else displayMoney(s.amount,s.currency)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TraxosButton(
                            { navigate("money_edit/DETECTED/${s.key}") },
                            Modifier.weight(1f),
                            !busy,
                        ) {
                            Text("Review")
                        }
                        TraxosTextButton(
                            { vm.run { repo.ignore(s.key) } },
                            Modifier.weight(1f),
                            !busy,
                        ) {
                            Text("Ignore")
                        }
                    }
                }
            }
        }
        item {
            TraxosSegment(
                listOf("ACTIVE" to "Active", "PAUSED" to "Paused", "HISTORY" to "History"),
                filter,
                true,
            ) {
                filter = it
            }
        }
        item { error?.let { TraxosNotice(it, NoticeKind.ERROR) } }
        if (plans == null) item { SkeletonBlock(Modifier.fillMaxWidth().height(64.dp)) }
        val visible =
            plans.orEmpty().filter {
                if (filter == "HISTORY") it.status in listOf("CANCELLED", "ENDED")
                else it.status == filter
            }
        if (plans != null && visible.isEmpty())
            item {
                Text("No ${filter.lowercase()} plans.", style = MaterialTheme.typography.bodyMedium)
            }
        items(visible, key = { it.id }) { p ->
            TraxosCard {
                SettingsRow(
                    p.name,
                    "${p.kind.lowercase()} · ${p.frequency.lowercase()} · ${p.currency}",
                ) {
                    detail = p.id
                }
                if (p.kind == "EMI") {
                    val count = history.count { it.planId == p.id && !it.voided }
                    if (count < p.installments)
                        MoneyText(PlanDates.installment(p, count), p.currency, hidden = hidden)
                } else
                    p.amountMinor?.let { MoneyText(it, p.currency, hidden = hidden) }
                        ?: Text("Variable amount", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${if(p.trialEnd!=null)"Trial · " else ""}Next ${displayDate(p.nextDay)}${if(p.nextDay<LocalDate.now().toEpochDay() && p.status=="ACTIVE")" · Overdue" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (p.status == "ACTIVE")
                    TraxosButton(
                        {
                            vm.error.value = null
                            payment = p.id
                        },
                        Modifier.fillMaxWidth(),
                        !busy,
                    ) {
                        Text(if (p.kind == "EMI") "Record" else "Confirm")
                    }
            }
        }
    }
}

@Composable
internal fun SubscriptionEditor(
    kind: String,
    id: String,
    close: () -> Unit,
    vm: MoneyToolsViewModel = viewModel(),
) {
    val repo = vm.app.obligations
    val plans by repo.dao.plans().collectAsStateWithLifecycle(null)
    val accounts by repo.db.ledger().accounts().collectAsStateWithLifecycle(null)
    val categories by repo.db.ledger().categories().collectAsStateWithLifecycle(emptyList())
    val records by repo.dao.records().collectAsStateWithLifecycle(emptyList())
    val preferences by repo.db.ledger().preferences().collectAsStateWithLifecycle(null)
    val defaults by
        remember(repo) { repo.dao.reminderSettings().map { it ?: FinanceReminderSettings() } }
            .collectAsStateWithLifecycle(null)
    val old = plans?.firstOrNull { it.id == id }
    if (
        defaults == null ||
            plans == null ||
            accounts == null ||
            preferences == null ||
            (kind == "PLAN" && old == null)
    ) {
        Text("Loading plan…", Modifier.padding(16.dp))
        return
    }
    val suggestion =
        if (kind == "DETECTED")
            PlanDates.detect(records, accounts.orEmpty()).firstOrNull { it.key == id }
        else null
    key(id, kind) {
        val entity = rememberSaveable { old?.id ?: UUID.randomUUID().toString() }
        val operation = rememberSaveable { UUID.randomUUID().toString() }
        val busy by vm.busy.collectAsStateWithLifecycle()
        var type by rememberSaveable {
            mutableStateOf(old?.kind ?: if (kind == "DETECTED") "SUBSCRIPTION" else kind)
        }
        var name by rememberSaveable { mutableStateOf(old?.name ?: suggestion?.name.orEmpty()) }
        var account by rememberSaveable {
            mutableStateOf(
                old?.accountId
                    ?: suggestion?.accountId
                    ?: if (kind == "EMI")
                        accounts
                            .orEmpty()
                            .firstOrNull {
                                it.account.type == "CREDIT_CARD" && !it.account.archived
                            }
                            ?.account
                            ?.id
                            .orEmpty()
                    else preferences?.defaultAccountId.orEmpty()
            )
        }
        var category by rememberSaveable {
            mutableStateOf(
                old?.categoryId
                    ?: suggestion?.categoryId
                    ?: preferences?.defaultExpenseCategoryId.orEmpty()
            )
        }
        var amount by rememberSaveable {
            mutableStateOf(
                old?.amountMinor?.let { Money.editable(it, old.currency) }
                    ?: suggestion?.amount?.let { Money.editable(it, suggestion.currency) }.orEmpty()
            )
        }
        var variable by rememberSaveable {
            mutableStateOf(
                if (old != null) old.amountMinor == null
                else suggestion?.amount == null && suggestion != null
            )
        }
        var frequency by rememberSaveable {
            mutableStateOf(old?.frequency ?: suggestion?.frequency ?: "MONTHLY")
        }
        var custom by rememberSaveable { mutableStateOf((old?.customDays ?: 30).toString()) }
        var date by rememberSaveable {
            mutableStateOf(
                LocalDate.ofEpochDay(
                        old?.nextDay ?: suggestion?.nextDay ?: LocalDate.now().toEpochDay()
                    )
                    .toString()
            )
        }
        var trial by rememberSaveable {
            mutableStateOf(old?.trialEnd?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty())
        }
        var end by rememberSaveable {
            mutableStateOf(old?.endDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty())
        }
        var reminders by rememberSaveable { mutableStateOf(old?.reminders ?: true) }
        var offsets by rememberSaveable { mutableStateOf(old?.offsets ?: defaults!!.offsets) }
        var purchase by rememberSaveable { mutableStateOf(old?.purchaseId.orEmpty()) }
        var installments by rememberSaveable {
            mutableStateOf((old?.installments?.takeIf { it > 0 } ?: 12).toString())
        }
        var more by rememberSaveable { mutableStateOf(false) }
        val funding = accounts.orEmpty().firstOrNull { it.account.id == account }?.account
        val unit = funding?.currency ?: preferences!!.defaultCurrency
        MoneyEditor(
            if (old == null) "New ${type.lowercase()}" else "Edit payment plan",
            vm,
            close,
            {
                vm.run {
                    val bought = records.firstOrNull { it.id == purchase }
                    val isEmi = type == "EMI"
                    val next = LocalDate.parse(date)
                    repo.plan(
                        PaymentPlan(
                            entity,
                            type,
                            name,
                            account,
                            category,
                            unit,
                            if (isEmi)
                                bought?.amountMinor?.let {
                                    it / (installments.toIntOrNull()?.takeIf { n -> n > 0 } ?: 1)
                                }
                            else if (variable) null else Money.parse(amount, unit),
                            if (isEmi) "MONTHLY" else frequency,
                            custom.toIntOrNull() ?: 0,
                            if (old != null && old.nextDay == next.toEpochDay()) old.anchorDay
                            else next.dayOfMonth,
                            next.toEpochDay(),
                            trial
                                .takeIf { it.isNotBlank() }
                                ?.let { LocalDate.parse(it).toEpochDay() },
                            end.takeIf { it.isNotBlank() }
                                ?.let { LocalDate.parse(it).toEpochDay() },
                            old?.status ?: "ACTIVE",
                            reminders,
                            offsets,
                            purchase.takeIf { isEmi && it.isNotBlank() },
                            if (isEmi) installments.toIntOrNull() ?: 0 else 0,
                            if (isEmi) bought?.amountMinor ?: 0 else 0,
                            operationId = operation,
                        ),
                        old?.revision ?: 0,
                    )
                }
            },
        ) {
            TraxosNotice(
                if (type == "EMI")
                    "Select an already-booked card purchase. Principal is scheduled, never charged as an expense a second time. Confirm only actual fees; card bill payment stays separate."
                else
                    "This is a reminder and payment plan. Nothing is posted until you confirm a real payment or match an existing expense.",
                NoticeKind.INFO,
            )
            if (old == null && type != "EMI")
                TraxosSegment(
                    listOf("SUBSCRIPTION" to "Subscription", "BILL" to "Bill"),
                    type,
                    !busy,
                ) {
                    type = it
                }
            TraxosInput(
                "Name / merchant",
                name,
                { name = it.take(80) },
                enabled = !busy,
                id = "payment_plan_name",
            )
            AccountPicker(vm.app.repository, account, accounts.orEmpty(), !busy) {
                account = it
                purchase = ""
            }
            CategoryPicker(vm.app.repository, category, categories, "EXPENSE", !busy) {
                category = it
            }
            if (type == "EMI") {
                val options =
                    records.filter {
                        it.type == "EXPENSE" &&
                            it.accountId == account &&
                            accounts.orEmpty().any { a ->
                                a.account.id == it.accountId && a.account.type == "CREDIT_CARD"
                            }
                    }
                TraxosSelector(
                    "Original purchase",
                    purchase,
                    options.map {
                        it.id to
                            "${it.note} · ${displayMoney(it.amountMinor,unit)} · ${displayDate(it.day)}"
                    },
                    !busy && old == null,
                ) {
                    purchase = it
                }
                TraxosInput(
                    "Installments · 2–120",
                    installments,
                    { installments = it.take(3) },
                    enabled = !busy && old == null,
                    number = true,
                )
            } else {
                TraxosCheckbox(variable, { variable = it }, "Variable amount", enabled = !busy)
                if (!variable)
                    TraxosInput(
                        "Amount · $unit",
                        amount,
                        { amount = it },
                        enabled = !busy,
                        number = true,
                        id = "payment_plan_amount",
                    )
                TraxosSelector(
                    "Repeat",
                    frequency,
                    PlanDates.frequencies.map {
                        it to it.lowercase().replaceFirstChar { c -> c.uppercase() }
                    },
                    !busy,
                ) {
                    frequency = it
                }
                if (frequency == "CUSTOM")
                    TraxosInput(
                        "Every N days · 1–3650",
                        custom,
                        { custom = it.take(4) },
                        enabled = !busy,
                        number = true,
                    )
            }
            TraxosDateField(
                if (type == "EMI") "First / next installment" else "Next billing date",
                date,
                !busy,
            ) {
                date = it
            }
            TraxosTextButton({ more = !more }, Modifier.fillMaxWidth(), !busy) {
                Text(if (more) "Less" else "Details")
            }
            if (more) {
                if (type != "EMI") {
                    TraxosCheckbox(
                        trial.isBlank(),
                        { trial = if (it) "" else date },
                        "No trial",
                        enabled = !busy,
                    )
                    if (trial.isNotBlank())
                        TraxosDateField("Trial ends", trial, !busy) { trial = it }
                }
                TraxosCheckbox(
                    end.isBlank(),
                    { end = if (it) "" else date },
                    "No end date",
                    enabled = !busy,
                )
                if (end.isNotBlank()) TraxosDateField("Ends on", end, !busy) { end = it }
                TraxosCheckbox(reminders, { reminders = it }, "Renewal reminders", enabled = !busy)
                if (reminders)
                    TraxosInput(
                        "Days before due · 0 means due date",
                        offsets,
                        { offsets = it },
                        enabled = !busy,
                    )
            }
            if (suggestion != null)
                Text(
                    "Suggestion from ${suggestion.evidence} reviewed ledger payments. Review all fields before saving.",
                    style = MaterialTheme.typography.bodySmall,
                )
        }
    }
}

@Composable
internal fun FinanceReminderSettingsScreen(vm: MoneyToolsViewModel = viewModel()) {
    val settings by vm.app.obligations.dao.reminderSettings().collectAsStateWithLifecycle(null)
    val original = settings ?: FinanceReminderSettings()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var enabled by rememberSaveable(original) { mutableStateOf(original.enabled) }
    var offsets by rememberSaveable(original) { mutableStateOf(original.offsets) }
    var start by rememberSaveable(original) { mutableStateOf(original.quietStart.toString()) }
    var end by rememberSaveable(original) { mutableStateOf(original.quietEnd.toString()) }
    Column(
        Modifier.fillMaxSize()
            .testTag("finance_reminders")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bills & card reminders", style = MaterialTheme.typography.titleLarge)
        TraxosNotice(
            "Offline reminders never pay a bill or create an expense. Android may delay background delivery. Notifications hide names, amounts and card details.",
            NoticeKind.INFO,
        )
        TraxosCheckbox(enabled, { enabled = it }, "Enable reminders", enabled = !busy)
        TraxosInput("Default lead days · e.g. 7,1,0", offsets, { offsets = it }, enabled = !busy)
        Text(
            "Defaults apply to new plans and billing cycles. Existing schedules retain their own lead days.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TraxosInput(
                "Quiet from · 0–23",
                start,
                { start = it.take(2) },
                Modifier.weight(1f),
                enabled = !busy,
                number = true,
            )
            TraxosInput(
                "Quiet until · 0–23",
                end,
                { end = it.take(2) },
                Modifier.weight(1f),
                enabled = !busy,
                number = true,
            )
        }
        Text(
            "Use matching hours to disable quiet hours.",
            style = MaterialTheme.typography.bodySmall,
        )
        error?.let { TraxosNotice(it, NoticeKind.ERROR) }
        TraxosButton(
            {
                vm.run {
                    vm.app.obligations.reminders(
                        FinanceReminderSettings(
                            enabled = enabled,
                            offsets = offsets,
                            quietStart = start.toIntOrNull() ?: -1,
                            quietEnd = end.toIntOrNull() ?: -1,
                        )
                    )
                }
            },
            Modifier.fillMaxWidth(),
            !busy,
            busy = busy,
        ) {
            Text("Save")
        }
        if (completed > 0)
            Text(
                "Saved on this phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        NotificationPermissionRow()
    }
}
