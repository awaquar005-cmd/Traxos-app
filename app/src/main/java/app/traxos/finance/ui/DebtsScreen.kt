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
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
internal fun DebtsScreen(
    repo: DebtsRepository,
    currencyDefault: String,
    hidden: Boolean,
    payCard: (String) -> Unit,
    initialDebt: String = "",
    changed: () -> Unit = {},
) {
    val extension = remember(repo.db) { PlanningExtensionsRepository(repo.db) }
    val loadedPreferences by extension.dao.debtPreferences().collectAsStateWithLifecycle(null)
    val debtPreferences = loadedPreferences.orEmpty()
    val funding by extension.dao.debtFunding().collectAsStateWithLifecycle(emptyList())
    val people by extension.dao.people().collectAsStateWithLifecycle(emptyList())
    var extras by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    val threads by
        remember(repo) { repo.threads.map<List<DebtThread>, List<DebtThread>?> { it } }
            .collectAsStateWithLifecycle(null)
    val accounts by repo.db.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    var filter by rememberSaveable { mutableStateOf("ACTIVE") }
    var selected by rememberSaveable { mutableStateOf(initialDebt) }
    var editor by rememberSaveable {
        mutableStateOf(if (initialDebt.isNotBlank()) "detail" else "")
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val debt = threads?.firstOrNull { it.id == selected }
    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                block()
                changed()
                editor = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "Check the entered details"
            } catch (e: Exception) {
                error = "Could not update debts. Saved records are kept; retry."
            } finally {
                busy = false
            }
        }
    }
    if (extras.isNotBlank() && debt != null) DebtExtrasSheet(debt.id, extras, { extras = "" })
    if (sharing && debt != null)
        DebtShareSheet(
            debt,
            people.firstOrNull {
                it.id == debtPreferences.firstOrNull { p -> p.debtId == debt.id }?.personId
            },
            { sharing = false },
        )
    if (confirmUndo && debt != null)
        TraxosDialog(
            "Undo repayment?",
            "An account movement created here is reversed in the ledger. A pre-existing matched transaction is kept. Only undo an accidental record; Traxos cannot reverse an actual bank payment.",
            "Undo",
            {
                confirmUndo = false
                act { extension.undoDebt(debt.id, debt.revision, UUID.randomUUID().toString()) }
            },
            { confirmUndo = false },
            destructive = true,
            enabled = !busy,
        )
    key(editor, selected) {
        if ((editor == "new" || editor == "edit" && debt != null) && loadedPreferences != null) {
            val old = if (editor == "edit") debt else null
            val identity = rememberSaveable { UUID.randomUUID().toString() }
            val operation = rememberSaveable { UUID.randomUUID().toString() }
            var person by rememberSaveable { mutableStateOf(old?.person.orEmpty()) }
            var direction by rememberSaveable { mutableStateOf(old?.direction ?: "LENT") }
            var currency by rememberSaveable { mutableStateOf(old?.currency ?: currencyDefault) }
            var amount by rememberSaveable {
                mutableStateOf(old?.let { Money.editable(it.originalMinor, it.currency) }.orEmpty())
            }
            var date by rememberSaveable {
                mutableStateOf(
                    LocalDate.ofEpochDay(old?.day ?: LocalDate.now().toEpochDay()).toString()
                )
            }
            var due by rememberSaveable {
                mutableStateOf(old?.dueDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty())
            }
            var note by rememberSaveable { mutableStateOf(old?.note.orEmpty()) }
            val oldPreferences = debtPreferences.firstOrNull { it.debtId == old?.id }
            var personId by rememberSaveable { mutableStateOf(oldPreferences?.personId.orEmpty()) }
            var reminders by rememberSaveable { mutableStateOf(oldPreferences?.reminders ?: false) }
            var offsets by rememberSaveable { mutableStateOf(oldPreferences?.offsets ?: "7,1,0") }
            var details by rememberSaveable { mutableStateOf(false) }
            var fundingMode by rememberSaveable { mutableStateOf("NONE") }
            var fundingAccount by rememberSaveable { mutableStateOf("") }
            var existingRecord by rememberSaveable { mutableStateOf("") }
            TraxosSheet(
                if (old == null) "New debt" else "Edit debt",
                {
                    if (!busy) {
                        editor = ""
                        error = null
                    }
                },
                true,
            ) {
                TraxosNotice(
                    "Track an IOU. Account changes are optional and must describe real money paid or received; lending and repayments are not spending or income.",
                    NoticeKind.INFO,
                )
                TraxosSegment(
                    listOf("LENT" to "They owe me", "BORROWED" to "I owe them"),
                    direction,
                    !busy && old == null,
                ) {
                    direction = it
                }
                TraxosInput(
                    "Person",
                    person,
                    { person = it.take(80) },
                    enabled = !busy,
                    id = "debt_person",
                )
                TraxosInput(
                    "Opening amount",
                    amount,
                    { amount = it },
                    enabled = !busy && old == null,
                    number = true,
                    id = "debt_amount",
                )
                TraxosSelector(
                    "Currency",
                    currency,
                    Money.currencies.map { it to it },
                    !busy && old == null,
                ) {
                    currency = it
                }
                TraxosDateField("Opening date", date, !busy && old == null) { date = it }
                TraxosCheckbox(
                    due.isBlank(),
                    { due = if (it) "" else LocalDate.now().toString() },
                    "No due date",
                    enabled = !busy,
                )
                if (due.isNotBlank()) TraxosDateField("Due date", due, !busy) { due = it }
                TraxosInput("Note · optional", note, { note = it.take(500) }, enabled = !busy)
                if (old == null)
                    DebtFundingFields(
                        extension,
                        currency,
                        direction == "BORROWED",
                        amount,
                        date,
                        fundingMode,
                        fundingAccount,
                        existingRecord,
                        !busy,
                        { fundingMode = it },
                        { fundingAccount = it },
                        { existingRecord = it },
                    )
                TraxosTextButton({ details = !details }, Modifier.fillMaxWidth(), !busy) {
                    Text(if (details) "Less" else "Details")
                }
                if (details) {
                    PersonChooser(extension, personId, !busy) {
                        personId = it?.id.orEmpty()
                        if (it != null) person = it.name
                    }
                    TraxosCheckbox(
                        reminders,
                        { reminders = it },
                        "Due reminders",
                        enabled = !busy && due.isNotBlank(),
                    )
                    if (reminders && due.isNotBlank())
                        TraxosInput(
                            "Days before due · 0 means due date",
                            offsets,
                            { offsets = it },
                            enabled = !busy,
                        )
                }

                error?.let { TraxosNotice(it, NoticeKind.ERROR) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosTextButton(
                        {
                            editor = ""
                            error = null
                        },
                        Modifier.weight(1f),
                        !busy,
                    ) {
                        Text("Cancel")
                    }
                    TraxosButton(
                        {
                            act {
                                extension.saveDebt(
                                    DebtThread(
                                        old?.id ?: identity,
                                        person,
                                        direction,
                                        currency,
                                        Money.parse(amount, currency),
                                        0,
                                        LocalDate.parse(date).toEpochDay(),
                                        due.takeIf { it.isNotBlank() }
                                            ?.let { LocalDate.parse(it).toEpochDay() },
                                        note,
                                        operationId = operation,
                                    ),
                                    old?.revision ?: 0,
                                    if (old == null && fundingMode != "NONE") fundingAccount
                                    else null,
                                    if (fundingMode == "MATCH") existingRecord else null,
                                    personId.takeIf { it.isNotBlank() },
                                    reminders && due.isNotBlank(),
                                    offsets,
                                )
                            }
                        },
                        Modifier.weight(1f),
                        !busy,
                        busy = busy,
                    ) {
                        Text("Save")
                    }
                }
            }
        } else if (editor in listOf("REPAYMENT", "INCREASE", "DECREASE") && debt != null) {
            val d = debt
            val kind = editor
            var fundingMode by rememberSaveable { mutableStateOf("NONE") }
            var fundingAccount by rememberSaveable { mutableStateOf("") }
            var existingRecord by rememberSaveable { mutableStateOf("") }
            val operation = rememberSaveable { UUID.randomUUID().toString() }
            var amount by rememberSaveable { mutableStateOf("") }
            var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
            var note by rememberSaveable { mutableStateOf("") }
            TraxosSheet(
                if (kind == "REPAYMENT") "Record repayment" else "Adjust outstanding",
                {
                    if (!busy) {
                        editor = ""
                        error = null
                    }
                },
                true,
            ) {
                Text(d.person, style = MaterialTheme.typography.titleMedium)
                MoneyText(d.outstandingMinor, d.currency, hidden = hidden)
                TraxosNotice(
                    if (kind == "REPAYMENT")
                        "Record an actual repayment. Optionally record or match its account movement below. Principal is never income or spending."
                    else "Correct the IOU balance without creating income or spending.",
                    NoticeKind.INFO,
                )
                TraxosInput(
                    "Amount · ${d.currency}",
                    amount,
                    { amount = it },
                    enabled = !busy,
                    number = true,
                    id = "debt_payment",
                )
                TraxosDateField("Date", date, !busy) { date = it }
                TraxosInput("Note · optional", note, { note = it.take(500) }, enabled = !busy)
                if (kind == "REPAYMENT")
                    DebtFundingFields(
                        extension,
                        d.currency,
                        d.direction == "LENT",
                        amount,
                        date,
                        fundingMode,
                        fundingAccount,
                        existingRecord,
                        !busy,
                        { fundingMode = it },
                        { fundingAccount = it },
                        { existingRecord = it },
                    )
                error?.let { TraxosNotice(it, NoticeKind.ERROR) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosTextButton(
                        {
                            editor = ""
                            error = null
                        },
                        Modifier.weight(1f),
                        !busy,
                    ) {
                        Text("Cancel")
                    }
                    TraxosButton(
                        {
                            act {
                                val minor = Money.parse(amount, d.currency)
                                if (kind == "REPAYMENT")
                                    extension.repay(
                                        d.id,
                                        d.revision,
                                        minor,
                                        LocalDate.parse(date).toEpochDay(),
                                        note,
                                        if (fundingMode != "NONE") fundingAccount else null,
                                        if (fundingMode == "MATCH") existingRecord else null,
                                        operation,
                                    )
                                else
                                    repo.move(
                                        d.id,
                                        d.revision,
                                        if (kind == "INCREASE") minor else -minor,
                                        kind,
                                        LocalDate.parse(date).toEpochDay(),
                                        note,
                                        operation,
                                    )
                            }
                        },
                        Modifier.weight(1f),
                        !busy,
                        busy = busy,
                    ) {
                        Text("Save")
                    }
                }
            }
        } else if (editor == "detail" && debt != null) {
            val d = debt
            val history by
                remember(d.id) { repo.db.debts().history(d.id) }
                    .collectAsStateWithLifecycle(emptyList())
            val undoable by
                remember(d.id) { repo.db.debts().undoable(d.id) }.collectAsStateWithLifecycle(null)
            TraxosSheet(
                d.person,
                {
                    if (!busy) {
                        editor = ""
                        error = null
                    }
                },
                true,
            ) {
                Text(
                    if (d.direction == "LENT") "They owe you" else "You owe them",
                    style = MaterialTheme.typography.bodySmall,
                )
                MoneyText(d.outstandingMinor, d.currency, true, hidden = hidden)
                Text(
                    "Opened ${LocalDate.ofEpochDay(d.day)}${d.dueDay?.let{" · Due ${LocalDate.ofEpochDay(it)}"}.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (d.note.isNotBlank()) Text(d.note, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosTextButton(
                        { extras = "FUNDING" },
                        Modifier.weight(1f),
                        !busy && !d.archived,
                    ) {
                        Text("Account")
                    }
                    TraxosTextButton(
                        { extras = "PERSON" },
                        Modifier.weight(1f),
                        !busy && !d.archived,
                    ) {
                        Text("Person")
                    }
                    TraxosTextButton({ sharing = true }, Modifier.weight(1f), !busy) {
                        Text("Share")
                    }
                }
                error?.let { TraxosNotice(it, NoticeKind.ERROR) }
                if (!d.archived) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TraxosButton(
                            { editor = "REPAYMENT" },
                            Modifier.weight(1f),
                            !busy && d.outstandingMinor > 0,
                        ) {
                            Text("Repay")
                        }
                        TraxosTextButton({ editor = "edit" }, Modifier.weight(1f), !busy) {
                            Text("Edit")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TraxosTextButton({ editor = "INCREASE" }, Modifier.weight(1f), !busy) {
                            Text("Increase")
                        }
                        TraxosTextButton(
                            { editor = "DECREASE" },
                            Modifier.weight(1f),
                            !busy && d.outstandingMinor > 0,
                        ) {
                            Text("Reduce")
                        }
                        TraxosTextButton(
                            {
                                if (
                                    funding.any {
                                        it.debtId == d.id &&
                                            it.movementId == undoable?.id &&
                                            it.active
                                    }
                                )
                                    confirmUndo = true
                                else
                                    act {
                                        extension.undoDebt(
                                            d.id,
                                            d.revision,
                                            UUID.randomUUID().toString(),
                                        )
                                    }
                            },
                            Modifier.weight(1f),
                            !busy && undoable != null,
                        ) {
                            Text("Undo")
                        }
                    }
                }
                TraxosTextButton(
                    { act { repo.archive(d.id, d.revision, !d.archived) } },
                    Modifier.fillMaxWidth(),
                    !busy && (d.archived || d.outstandingMinor == 0L),
                ) {
                    Text(if (d.archived) "Restore" else "Archive")
                }
                Text("RECENT HISTORY · UP TO 100", style = MaterialTheme.typography.labelSmall)
                Text(
                    "Opening · ${Money.format(d.originalMinor,d.currency).takeIf{!hidden} ?: "••••"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                history.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.kind.lowercase().replaceFirstChar { it.uppercase() } +
                                    " · ${LocalDate.ofEpochDay(m.day)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (m.note.isNotBlank())
                                Text(m.note, style = MaterialTheme.typography.bodySmall)
                        }
                        MoneyText(m.deltaMinor, d.currency, hidden = hidden)
                    }
                }
            }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("debt_wallet"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader("Debts & credit", "Add") {
                selected = ""
                editor = "new"
                error = null
            }
            Text(
                "Private IOUs and your recorded card liabilities.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            TraxosNotice(
                "IOUs track what is owed, separately from your cash ledger. Never count a card limit as money.",
                NoticeKind.INFO,
            )
        }
        if (threads == null)
            item { Text("Loading debts…", style = MaterialTheme.typography.bodySmall) }
        val active = threads.orEmpty().filter { !it.archived }
        items(
            active.groupBy { it.currency }.entries.toList(),
            key = { "debt_summary_${it.key}" },
        ) { (currency, rows) ->
            TraxosCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Owed to you · $currency", style = MaterialTheme.typography.labelSmall)
                        MoneyText(
                            rows
                                .filter { it.direction == "LENT" }
                                .fold(0L) { a, b -> Math.addExact(a, b.outstandingMinor) },
                            currency,
                            hidden = hidden,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("You owe · $currency", style = MaterialTheme.typography.labelSmall)
                        MoneyText(
                            rows
                                .filter { it.direction == "BORROWED" }
                                .fold(0L) { a, b -> Math.addExact(a, b.outstandingMinor) },
                            currency,
                            hidden = hidden,
                        )
                    }
                }
            }
        }
        item {
            TraxosSegment(
                listOf("ACTIVE" to "Active", "SETTLED" to "Settled", "ARCHIVED" to "Archived"),
                filter,
                true,
            ) {
                filter = it
            }
        }
        val visible =
            threads.orEmpty().filter {
                when (filter) {
                    "ARCHIVED" -> it.archived
                    "SETTLED" -> !it.archived && it.outstandingMinor == 0L
                    else -> !it.archived && it.outstandingMinor > 0L
                }
            }
        if (threads != null && visible.isEmpty())
            item {
                Text(
                    "No ${filter.lowercase()} IOUs. Add an existing loan or amount owed to get started.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        items(visible, key = { it.id }) { d ->
            TraxosCard {
                SettingsRow(
                    d.person,
                    (if (d.direction == "LENT") "They owe you" else "You owe them") +
                        (d.dueDay
                            ?.let {
                                " · ${if(it<LocalDate.now().toEpochDay() && d.outstandingMinor>0)"Overdue" else "Due"} ${LocalDate.ofEpochDay(it)}"
                            }
                            .orEmpty()),
                ) {
                    selected = d.id
                    editor = "detail"
                    error = null
                }
                MoneyText(d.outstandingMinor, d.currency, hidden = hidden)
            }
        }
        item { Text("CREDIT CARDS", style = MaterialTheme.typography.labelSmall) }
        val cards = accounts.filter { it.account.type == "CREDIT_CARD" && !it.account.archived }
        if (cards.isEmpty())
            item {
                Text(
                    "Your credit-card accounts appear here after you add one in Accounts.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        items(cards, key = { "credit_${it.account.id}" }) { card ->
            TraxosCard {
                SettingsRow(
                    card.account.name,
                    if (card.balanceMinor <= 0) "Recorded amount owed"
                    else "Credit balance · not available credit",
                ) {
                    payCard(card.account.id)
                }
                MoneyText(
                    if (card.balanceMinor <= 0) -card.balanceMinor else card.balanceMinor,
                    card.account.currency,
                    hidden = hidden,
                )
                if (card.balanceMinor < 0)
                    TraxosTextButton({ payCard(card.account.id) }, Modifier.fillMaxWidth()) {
                        Text("Pay")
                    }
            }
        }
    }
}
